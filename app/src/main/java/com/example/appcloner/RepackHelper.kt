package com.example.appcloner

import java.io.File
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.X509Certificate
import com.android.apksig.ApkSigner
import com.reandroid.apk.ApkModule
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.SecureRandom
import java.util.Date

object RepackHelper {
    
    class RepackException(message: String) : Exception(message)

    fun repack(inputApks: List<String>, outputApkPath: String, newPackage: String?, newAppName: String? = null): Boolean {
        try {
            if (inputApks.isEmpty()) throw RepackException("No input APKs provided.")
            
            val apkFiles = inputApks.map { File(it) }
            apkFiles.forEach { if (!it.exists()) throw RepackException("Target APK does not exist: ${it.name}") }
            
            val baseFile = apkFiles.first()
            val tempUnsignedApk = File(baseFile.parentFile, "temp_unsigned.apk")
            if (tempUnsignedApk.exists()) tempUnsignedApk.delete()

            // 1. Merge Split APKs if needed, then load as unified ApkModule
            val apkModule = if (apkFiles.size == 1) {
                ApkModule.loadApkFile(baseFile)
            } else {
                val base = ApkModule.loadApkFile(baseFile, "base")
                for (i in 1 until apkFiles.size) {
                    val split = ApkModule.loadApkFile(apkFiles[i], "split_$i")
                    base.merge(split)
                }
                base
            }

            val manifestDocument = apkModule.androidManifest
            val oldPackage = manifestDocument?.packageName
            
            if (oldPackage != null && newPackage != null && oldPackage != newPackage) {
                manifestDocument.packageName = newPackage
                
                // Recursively traverse and update explicit references
                val providerCounter = java.util.concurrent.atomic.AtomicInteger(0)

                fun updateElement(element: com.reandroid.arsc.chunk.xml.ResXmlElement) {
                    val authAttr = element.searchAttributeByResourceId(0x01010018) // authorities
                    if (authAttr != null) {
                        val auths = authAttr.valueAsString
                        if (auths != null && auths.contains(oldPackage)) {
                            authAttr.setValueAsString(auths.replace(oldPackage, newPackage))
                        } else if (auths != null) {
                            // Generate a unique authority anchored to the new package to avoid device-wide conflicts
                            val suffix = providerCounter.getAndIncrement()
                            val newAuthority = "$newPackage.provider$suffix"
                            authAttr.setValueAsString(newAuthority)
                        }
                    }
                    
                    val nameAttr = element.searchAttributeByResourceId(0x01010003) // name
                    if (nameAttr != null) {
                        val nameStr = nameAttr.valueAsString
                        if (nameStr != null && nameStr.contains(oldPackage)) {
                            nameAttr.setValueAsString(nameStr.replace(oldPackage, newPackage))
                        }
                    }
                    
                    val iterator = element.elements
                    if (iterator != null) {
                        while (iterator.hasNext()) {
                            updateElement(iterator.next())
                        }
                    }
                }
                
                manifestDocument.manifestElement?.let { updateElement(it) }

                // Remove sharedUserId / sharedUserLabel from manifest if present to avoid install conflicts
                try {
                    val mdClass = manifestDocument::class.java
                    // Try setter method if available
                    mdClass.methods.firstOrNull { it.name.equals("setSharedUserId", ignoreCase = true) }?.let { m ->
                        try { m.invoke(manifestDocument, null) } catch (_: Exception) { }
                    }
                    // Try field access fallback
                    try {
                        val f = mdClass.getDeclaredField("sharedUserId")
                        f.isAccessible = true
                        f.set(manifestDocument, null)
                    } catch (_: Exception) { }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Update resources.arsc packages properly
                try {
                    val tableBlock = apkModule.tableBlock
                    if (tableBlock != null) {
                        for (packageBlock in tableBlock.packageArray.childes) {
                            if (packageBlock.name == oldPackage) {
                                packageBlock.name = newPackage
                            }
                        }
                        tableBlock.refresh()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            if (newAppName != null && newAppName.isNotEmpty()) {
                // Try to set application label directly using getOrCreateAttribute
                val attr = manifestDocument?.applicationElement?.getOrCreateAndroidAttribute("label", 0x01010001)
                attr?.setValueAsString(newAppName)
            }
            
            // Delete old signature manually
            apkModule.removeDir("META-INF")
            
            // Write unsigned APK temporarily
            apkModule.writeApk(tempUnsignedApk)
            
            // 2. Sign APK natively using Apksig
            val keyPair = generateKeyPair()
            val cert = generateSelfSignedCertificate(keyPair)
            
            val signerConfig = ApkSigner.SignerConfig.Builder("appcloner", keyPair.private, listOf(cert)).build()
            
            val signer = ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(tempUnsignedApk)
                .setOutputApk(File(outputApkPath))
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .build()
                
            signer.sign()
            
            tempUnsignedApk.delete()
            return true

        } catch (e: Exception) {
            e.printStackTrace()
            throw RepackException(e.message ?: "Unknown repack error")
        }
    }
    
    private fun generateKeyPair(): KeyPair {
        val kg = KeyPairGenerator.getInstance("RSA")
        kg.initialize(2048)
        return kg.generateKeyPair()
    }
    
    private fun generateSelfSignedCertificate(keyPair: KeyPair): X509Certificate {
        val startDate = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000)
        val endDate = Date(System.currentTimeMillis() + 1000L * 60L * 60L * 24L * 365L * 30L) // 30 Years
        
        val serialNumber = BigInteger(64, SecureRandom())
        val ownerName = X500Name("CN=AppCloner,O=Android,C=US")
        
        val contentSigner = JcaContentSignerBuilder("SHA256WithRSAEncryption").build(keyPair.private)
        val builder = JcaX509v3CertificateBuilder(
            ownerName, serialNumber, startDate, endDate, ownerName, keyPair.public
        )
        
        return org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().setProvider(org.bouncycastle.jce.provider.BouncyCastleProvider()).getCertificate(builder.build(contentSigner))
    }
}
