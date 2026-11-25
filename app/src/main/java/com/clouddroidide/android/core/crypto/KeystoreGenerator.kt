package com.clouddroidide.android.core.crypto

import android.util.Base64
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Security
import java.util.Date

data class KeystoreParams(
    val fileName: String,
    val alias: String,
    val keystorePassword: String,
    val keyPassword: String,
    val validityYears: Int,
    val commonName: String,
    val organizationalUnit: String,
    val organization: String,
    val locality: String,
    val state: String,
    val country: String
)

data class KeystoreInfo(
    val file: File,
    val fileName: String,
    val sizeBytes: Long,
    val alias: String?,
    val sha256Fingerprint: String?
)

/**
 * Generates and inspects real PKCS12/JKS Android release signing keystores using BouncyCastle.
 */
object KeystoreGenerator {
    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    fun generateKeystore(outputDir: File, params: KeystoreParams): KeystoreInfo {
        if (!outputDir.exists()) outputDir.mkdirs()
        val safeName = params.fileName.trim().let {
            if (it.endsWith(".jks") || it.endsWith(".p12") || it.endsWith(".keystore")) it else "$it.jks"
        }
        val outFile = File(outputDir, safeName)

        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(2048, SecureRandom())
        val keyPair = keyPairGen.generateKeyPair()

        val nameBuilder = X500NameBuilder(BCStyle.INSTANCE)
        if (params.commonName.isNotBlank()) nameBuilder.addRDN(BCStyle.CN, params.commonName.trim())
        if (params.organizationalUnit.isNotBlank()) nameBuilder.addRDN(BCStyle.OU, params.organizationalUnit.trim())
        if (params.organization.isNotBlank()) nameBuilder.addRDN(BCStyle.O, params.organization.trim())
        if (params.locality.isNotBlank()) nameBuilder.addRDN(BCStyle.L, params.locality.trim())
        if (params.state.isNotBlank()) nameBuilder.addRDN(BCStyle.ST, params.state.trim())
        if (params.country.isNotBlank()) nameBuilder.addRDN(BCStyle.C, params.country.trim().uppercase().take(2))
        val subject = nameBuilder.build()

        val now = System.currentTimeMillis()
        val notBefore = Date(now - 60_000L)
        val years = params.validityYears.coerceIn(1, 100)
        val notAfter = Date(now + years * 365L * 24L * 60L * 60L * 1000L)
        val serial = BigInteger(64, SecureRandom()).abs().max(BigInteger.ONE)

        val certBuilder = JcaX509v3CertificateBuilder(
            subject,
            serial,
            notBefore,
            notAfter,
            subject,
            keyPair.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val certHolder = certBuilder.build(signer)
        val cert = JcaX509CertificateConverter().getCertificate(certHolder)

        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry(
            params.alias.trim(),
            keyPair.private,
            params.keyPassword.toCharArray(),
            arrayOf(cert)
        )
        FileOutputStream(outFile).use { fos ->
            ks.store(fos, params.keystorePassword.toCharArray())
        }

        val sha256 = MessageDigest.getInstance("SHA-256")
            .digest(cert.encoded)
            .joinToString(":") { "%02X".format(it) }

        return KeystoreInfo(
            file = outFile,
            fileName = safeName,
            sizeBytes = outFile.length(),
            alias = params.alias.trim(),
            sha256Fingerprint = sha256
        )
    }

    fun verifyKeystore(bytes: ByteArray, keystorePassword: String): Pair<String?, String?> {
        val types = listOf("PKCS12", "JKS", "BKS")
        for (type in types) {
            runCatching {
                val ks = KeyStore.getInstance(type)
                ks.load(ByteArrayInputStream(bytes), keystorePassword.toCharArray())
                val aliases = ks.aliases()
                if (aliases.hasMoreElements()) {
                    val alias = aliases.nextElement()
                    val cert = ks.getCertificate(alias)
                    val fp = cert?.encoded?.let { enc ->
                        MessageDigest.getInstance("SHA-256").digest(enc)
                            .joinToString(":") { "%02X".format(it) }
                    }
                    return alias to fp
                }
            }
        }
        return null to null
    }

    fun toBase64(file: File): String =
        Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
}
