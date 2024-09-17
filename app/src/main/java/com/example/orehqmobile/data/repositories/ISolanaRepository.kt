package com.example.orehqmobile.data.repositories

import com.example.orehqmobile.data.models.PUBLIC_KEY_LEN
import com.example.orehqmobile.data.models.SIGNATURE_LEN
import com.funkatronics.encoders.Base58
import com.solana.signer.SolanaSigner
import com.solana.transaction.Message
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

interface ISolanaRepository {
    fun base58Encode(byteArray: ByteArray): String
    fun signMessage(message: ByteArray, keypairs: List<AsymmetricCipherKeyPair>): SignatureResult
    fun signTransaction(
        transaction: ByteArray,
        keypairs: List<AsymmetricCipherKeyPair>
    ): SignatureResult
}

data class SignatureResult(
    val signedPayload: ByteArray,
    val signature: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SignatureResult

        if (!signedPayload.contentEquals(other.signedPayload)) return false
        if (!signature.contentEquals(other.signature)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = signedPayload.contentHashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }
}

class SolanaRepository : ISolanaRepository {
    override fun base58Encode(byteArray: ByteArray): String {
        return Base58.encodeToString(byteArray)
    }

    override fun signTransaction(
        transaction: ByteArray,
        keypairs: List<AsymmetricCipherKeyPair>
    ): SignatureResult {
        // Validate the transaction only up through the account addresses array
        val (numSignatures, numSignaturesOffset) = readCompactArrayLen(transaction, 0)
        val prefixOffset = numSignaturesOffset + (SIGNATURE_LEN * numSignatures)
        val prefix = transaction[prefixOffset].toInt()

        // if the highest bit of the prefix is not set, the message is not versioned
        val txnVersionOffset = if (prefix and 0x7f == prefix) 0 else 1
        val headerOffset = prefixOffset + txnVersionOffset

        val accountsArrayOffset = headerOffset + 3
        require(accountsArrayOffset <= transaction.size) { "transaction header extends beyond buffer bounds" }
        val numSignaturesHeader = transaction[headerOffset].toInt()
        require(numSignatures == numSignaturesHeader) { "Signatures array length does not match transaction required number of signatures" }

        val (numAccounts, numAccountsOffset) = readCompactArrayLen(transaction, accountsArrayOffset)
        require(numAccounts >= numSignatures) { "Accounts array is smaller than number of required signatures" }
        val blockhashOffset = accountsArrayOffset + numAccountsOffset + PUBLIC_KEY_LEN * numAccounts
        require(blockhashOffset <= transaction.size) { "Accounts array extends beyond buffer bounds" }

        val partiallySignedTx = transaction.clone()

        keypairs.forEach { keypair ->
            val publicKey = keypair.public as Ed25519PublicKeyParameters
            val privateKey = keypair.private as Ed25519PrivateKeyParameters
            val publicKeyBytes = publicKey.encoded
            var accountIndex = -1
            for (i in 0 until numSignatures) {
                val accountOff = accountsArrayOffset + numAccountsOffset + PUBLIC_KEY_LEN * i
                val accountPublicKey = transaction.copyOfRange(accountOff, accountOff + PUBLIC_KEY_LEN)
                if (publicKeyBytes contentEquals accountPublicKey) {
                    accountIndex = i
                    break
                }
            }
            require(accountIndex != -1) { "Transaction does not require a signature with the requested keypair" }

            val signer = Ed25519Signer()
            signer.init(true, privateKey)
            signer.update(transaction, prefixOffset, transaction.size - prefixOffset)
            val sig = signer.generateSignature()
            assert(sig.size == SIGNATURE_LEN) { "Unexpected signature length" }

            System.arraycopy(sig, 0, partiallySignedTx, numSignaturesOffset + SIGNATURE_LEN * accountIndex, sig.size)
        }

        return SignatureResult(partiallySignedTx, partiallySignedTx.sliceArray(1 until 1 + SIGNATURE_LEN))
    }

    override fun signMessage(
        message: ByteArray,
        keypairs: List<AsymmetricCipherKeyPair>
    ): SignatureResult {
        var signedMessage = message.clone()
        keypairs.forEach { keypair ->
            val privateKey = keypair.private as Ed25519PrivateKeyParameters

            val signer = Ed25519Signer()
            signer.init(true, privateKey)
            signer.update(message, 0, message.size)
            val sig = signer.generateSignature()
            assert(sig.size == SIGNATURE_LEN) { "Unexpected signature length" }

            val offset = signedMessage.size
            signedMessage = signedMessage.copyOf(signedMessage.size + SIGNATURE_LEN)
            sig.copyInto(signedMessage, offset)
        }

        return SignatureResult(signedMessage, signedMessage.sliceArray(message.size until message.size + SIGNATURE_LEN))
    }

    private fun readCompactArrayLen(b: ByteArray, off: Int): Pair<Int, Int> {
        var len: Int

        require(off < b.size) { "compact array length extends beyond buffer bounds" }
        val b0 = b[off].toUByte().toInt()
        len = (b0.and(0x7f))
        if (b0.and(0x80) == 0) {
            return len to 1
        }

        require((off + 1) < b.size) { "compact array length extends beyond buffer bounds" }
        val b1 = b[off + 1].toUByte().toInt()
        len = len.shl(7).or(b1.and(0x7f))
        if (b1.and(0x80) == 0) {
            return len to 2
        }

        require((off + 2) < b.size) { "compact array length extends beyond buffer bounds" }
        val b2 = b[off + 2].toUByte().toInt()
        require(b2.and((0x3).inv()) == 0) { "third byte of compact array length has unexpected bits set" }
        len = len.shl(2).or(b2)
        return len to 3
    }
}