package com.example.orehqmobile.data.models

import com.funkatronics.encoders.Base58
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.generators.SCrypt
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

const val SIGNATURE_LEN = 64
const val PUBLIC_KEY_LEN = 32

data class EncryptedKeypair(
    val salt: ByteArray,
    val iv: ByteArray,
    val encryptedPrivateKey: ByteArray,
    val publicKey: ByteArray
) {
    companion object {
        fun fromKeyPair(keyPair: KeyPair, password: String): EncryptedKeypair {
            val salt = ByteArray(16).apply { java.security.SecureRandom().nextBytes(this) }
            val iv = ByteArray(16).apply { java.security.SecureRandom().nextBytes(this) }
            val key = deriveKey(password, salt)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            val encryptedPrivateKey = cipher.doFinal(keyPair.private.encoded)

            return EncryptedKeypair(salt, iv, encryptedPrivateKey, keyPair.public.encoded)
        }

        fun fromByteArray(bytes: ByteArray): EncryptedKeypair {
            val salt = bytes.copyOfRange(0, 16)
            val iv = bytes.copyOfRange(16, 32)
            val publicKeyLength = bytes[32].toInt() and 0xFF
            val publicKey = bytes.copyOfRange(33, 33 + publicKeyLength)
            val encryptedPrivateKey = bytes.copyOfRange(33 + publicKeyLength, bytes.size)
            return EncryptedKeypair(salt, iv, encryptedPrivateKey, publicKey)
        }

        private fun deriveKey(password: String, salt: ByteArray): ByteArray {
            return SCrypt.generate(password.toByteArray(), salt, 16384, 8, 1, 32)
        }
    }

    fun toKeyPair(password: String): KeyPair {
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val decryptedPrivateKey = cipher.doFinal(encryptedPrivateKey)

        val privateKey = Ed25519PrivateKeyParameters(decryptedPrivateKey, 0)
        val publicKey = Ed25519PublicKeyParameters(this.publicKey, 0)

        return KeyPair(
            Ed25519PublicKey(publicKey.encoded),
            Ed25519PrivateKey(privateKey.encoded)
        )
    }

    fun toByteArray(): ByteArray {
        return salt + iv + byteArrayOf(publicKey.size.toByte()) + publicKey + encryptedPrivateKey
    }
}
class Ed25519PublicKey(private val keyBytes: ByteArray) : PublicKey {
    override fun getAlgorithm(): String = "Ed25519"
    override fun getFormat(): String = "RAW"
    override fun getEncoded(): ByteArray = keyBytes

    override fun toString(): String {
      return Base58.encodeToString(keyBytes)
  }
}

class Ed25519PrivateKey(private val keyBytes: ByteArray) : PrivateKey {
    override fun getAlgorithm(): String = "Ed25519"
    override fun getFormat(): String = "RAW"
    override fun getEncoded(): ByteArray = keyBytes
}

