package com.kriptikz.orehqmobile.data.repositories

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.kriptikz.orehqmobile.data.models.EncryptedKeypair
import com.kriptikz.orehqmobile.data.models.Ed25519PrivateKey
import com.kriptikz.orehqmobile.data.models.Ed25519PublicKey
import com.kriptikz.orehqmobile.data.models.SIGNATURE_LEN
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import java.io.File
import java.security.KeyPair
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom

data class GeneratedKeypair (
    val phrase: String,
    val keypair: KeyPair
)

interface IKeypairRepository {
    suspend fun saveEncryptedKeypair(keypair: KeyPair, password: String)
    suspend fun loadEncryptedKeypair(password: String): KeyPair?
    fun saveKeypair(keypair: KeyPair)
    fun loadKeypair(): KeyPair?
    fun loadAcKeypair(): AsymmetricCipherKeyPair?
    fun signMessage(message: ByteArray): SignatureResult?
    fun getPubkey(): Ed25519PublicKey?
    fun generateNewKeypair(): KeyPair
    fun generateNewKeypairWithPhrase(): GeneratedKeypair
    fun encryptedKeypairExists(): Boolean
}

class KeypairRepository(private val context: Context) : IKeypairRepository {
    private val fileName = "data0.bin"

    override suspend fun saveEncryptedKeypair(keypair: KeyPair, password: String) {
        val encryptedKeypair = EncryptedKeypair.fromKeyPair(keypair, password)
        val file = File(context.filesDir, fileName)
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val encryptedFile = EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        encryptedFile.openFileOutput().use { outputStream ->
            outputStream.write(encryptedKeypair.toByteArray())
        }
    }

    override fun saveKeypair(keypair: KeyPair) {
        val file = File(context.filesDir, fileName)
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val encryptedFile = EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        encryptedFile.openFileOutput().use { outputStream ->
            outputStream.write(keypairToBytes(keypair))
        }
    }

    private fun keypairToBytes(keypair: KeyPair): ByteArray {
        val pubkey = keypair.public.encoded
        val privateKey = keypair.private.encoded
        return byteArrayOf(pubkey.size.toByte()) + pubkey + privateKey
    }

    private fun keypairFromBytes(bytes: ByteArray): KeyPair {
        val publicKeyLength = bytes[0].toInt() and 0xFF
        val encodedPublicKey = bytes.copyOfRange(1, 1 + publicKeyLength)
        val encodedPrivateKey = bytes.copyOfRange(1 + publicKeyLength, bytes.size)
        val privateKey = Ed25519PrivateKeyParameters(encodedPrivateKey, 0)
        val publicKey = Ed25519PublicKeyParameters(encodedPublicKey, 0)

        return KeyPair(
            Ed25519PublicKey(publicKey.encoded),
            Ed25519PrivateKey(privateKey.encoded)
        )
    }

    override suspend fun loadEncryptedKeypair(password: String): KeyPair? {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return null

        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val encryptedFile = EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        val bytes = encryptedFile.openFileInput().use { it.readBytes() }
        val encryptedKeypair = EncryptedKeypair.fromByteArray(bytes)
        return encryptedKeypair.toKeyPair(password)
    }

    override fun getPubkey(): Ed25519PublicKey? {
        val kp = loadKeypair()

        return if (kp != null) {
            Ed25519PublicKey(kp.public.encoded)
        } else {
            null
        }
    }

    override fun loadKeypair(): KeyPair? {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return null

        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val encryptedFile = EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        val bytes = encryptedFile.openFileInput().use { it.readBytes() }
        val keypair = keypairFromBytes(bytes)

        return keypair
    }

    override fun loadAcKeypair(): AsymmetricCipherKeyPair? {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return null

        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val encryptedFile = EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        val bytes = encryptedFile.openFileInput().use { it.readBytes() }
        val keypair = keypairFromBytes(bytes)

        val privateKey = Ed25519PrivateKeyParameters(keypair.private.encoded, 0)
        val acKeypair = AsymmetricCipherKeyPair(privateKey.generatePublicKey(), privateKey)
        return acKeypair
    }

    override fun signMessage(
        message: ByteArray,
    ): SignatureResult? {
        var signedMessage = message.clone()
        val kp = loadAcKeypair()
        if (kp != null) {
            val privateKey = kp.private as Ed25519PrivateKeyParameters

            val signer = Ed25519Signer()
            signer.init(true, privateKey)
            signer.update(message, 0, message.size)
            val sig = signer.generateSignature()
            assert(sig.size == SIGNATURE_LEN) { "Unexpected signature length" }

            val offset = signedMessage.size
            signedMessage = signedMessage.copyOf(signedMessage.size + SIGNATURE_LEN)
            sig.copyInto(signedMessage, offset)

            return SignatureResult(signedMessage, signedMessage.sliceArray(message.size until message.size + SIGNATURE_LEN))
        }
        return null
    }

    override fun generateNewKeypair(): KeyPair {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val keyPair = generator.generateKeyPair()
        val privateKey = keyPair.private as Ed25519PrivateKeyParameters
        val publicKey = keyPair.public as Ed25519PublicKeyParameters
        return KeyPair(
            Ed25519PublicKey(publicKey.encoded),
            Ed25519PrivateKey(privateKey.encoded)
        )
    }

    override fun generateNewKeypairWithPhrase(): GeneratedKeypair {
        val generatedKey = uniffi.orehqmobileffi.generateKey()

        val phrase = generatedKey.wordList;
        val privateKeyBytes = generatedKey.keypair.sliceArray(0 until 32)
        val publicKeyBytes = generatedKey.keypair.sliceArray(32 until generatedKey.keypair.size)

        val privateKey = Ed25519PrivateKey(privateKeyBytes)
        val publicKey = Ed25519PublicKey(publicKeyBytes)

        return GeneratedKeypair(phrase, KeyPair(publicKey, privateKey))
    }

    override fun encryptedKeypairExists(): Boolean {
        val file = File(context.filesDir, fileName)
        return file.exists()
    }
}


