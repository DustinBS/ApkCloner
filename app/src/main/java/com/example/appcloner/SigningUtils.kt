package com.example.appcloner

import java.math.BigInteger
import java.util.Date
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.cert.X509Certificate
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider

object SigningUtils {
    fun generateKeyPair(): KeyPair {
        val kg = KeyPairGenerator.getInstance("RSA")
        kg.initialize(2048)
        return kg.generateKeyPair()
    }

    fun generateSelfSignedCertificate(keyPair: KeyPair): X509Certificate {
        val startDate = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000)
        val endDate = Date(System.currentTimeMillis() + 1000L * 60L * 60L * 24L * 365L * 30L) // 30 Years

        val serialNumber = BigInteger(64, SecureRandom())
        val ownerName = X500Name("CN=AppCloner,O=Android,C=US")

        val contentSigner = JcaContentSignerBuilder("SHA256WithRSAEncryption").build(keyPair.private)
        val builder = JcaX509v3CertificateBuilder(
            ownerName, serialNumber, startDate, endDate, ownerName, keyPair.public
        )

        return JcaX509CertificateConverter().setProvider(BouncyCastleProvider()).getCertificate(builder.build(contentSigner))
    }
}
