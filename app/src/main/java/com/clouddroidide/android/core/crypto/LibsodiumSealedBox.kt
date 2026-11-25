package com.clouddroidide.android.core.crypto

import android.util.Base64
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.Box
import java.nio.charset.StandardCharsets

/**
 * Encrypts GitHub Actions secrets using Libsodium crypto_box_seal (X25519 + XSalsa20-Poly1305)
 * as required by PUT /repos/{owner}/{repo}/actions/secrets/{secret_name}.
 */
object LibsodiumSealedBox {
    private val sodium: LazySodiumAndroid by lazy {
        LazySodiumAndroid(SodiumAndroid())
    }

    fun encryptSecret(secretValue: String, base64PublicKey: String): String {
        val publicKeyBytes = Base64.decode(base64PublicKey.trim(), Base64.DEFAULT)
        require(publicKeyBytes.size == Box.PUBLICKEYBYTES) {
            "Invalid GitHub Actions public key length: ${publicKeyBytes.size}"
        }
        val messageBytes = secretValue.toByteArray(StandardCharsets.UTF_8)
        val cipherBytes = ByteArray(messageBytes.size + Box.SEALBYTES)
        val ok = sodium.cryptoBoxSeal(cipherBytes, messageBytes, messageBytes.size.toLong(), publicKeyBytes)
        if (!ok) {
            error("Libsodium cryptoBoxSeal failed")
        }
        return Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
    }
}
