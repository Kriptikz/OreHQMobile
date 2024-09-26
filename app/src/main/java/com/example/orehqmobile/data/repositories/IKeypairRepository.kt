package com.example.orehqmobile.data.repositories

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.example.orehqmobile.data.models.EncryptedKeypair
import com.example.orehqmobile.data.models.Ed25519PrivateKey
import com.example.orehqmobile.data.models.Ed25519PublicKey
import java.io.File
import java.security.KeyPair
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import java.security.SecureRandom

data class GeneratedKeypair (
    val phrase: String,
    val keypair: KeyPair
)

interface IKeypairRepository {
    suspend fun saveEncryptedKeypair(keypair: KeyPair, password: String)
    suspend fun loadEncryptedKeypair(password: String): KeyPair?
    fun generateNewKeypair(): KeyPair
    fun generateNewKeypairWithPhrase(): GeneratedKeypair
    fun encryptedKeypairExists(): Boolean
}

class KeypairRepository(private val context: Context) : IKeypairRepository {
    private val fileName = "encrypted_test_keypair.bin"

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
        val generatedKey = uniffi.drillxmobile.dxGenerateKey()

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


