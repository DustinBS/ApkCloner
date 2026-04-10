package com.example.appcloner

import java.io.File
import com.android.apksig.ApkSigner

object DebugFallbacks {
    @JvmStatic
    fun attemptFallbackSign(baseFile: File, tempUnsignedApk: File, outputApkPath: String): Boolean {
        try {
            baseFile.copyTo(tempUnsignedApk, overwrite = true)
            val keyPairFallback = SigningUtils.generateKeyPair()
            val certFallback = SigningUtils.generateSelfSignedCertificate(keyPairFallback)
            val signerConfigFallback = ApkSigner.SignerConfig.Builder("appcloner", keyPairFallback.private, listOf(certFallback)).build()
            val signerFallback = ApkSigner.Builder(listOf(signerConfigFallback))
                .setInputApk(tempUnsignedApk)
                .setOutputApk(File(outputApkPath))
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setMinSdkVersion(1)
                .build()
            signerFallback.sign()
            tempUnsignedApk.delete()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
