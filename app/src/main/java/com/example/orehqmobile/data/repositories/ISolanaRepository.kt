package com.example.orehqmobile.data.repositories

interface ISolanaRepository {
    fun base58Encode(byteArray: ByteArray): String
}

class SolanaRepository : ISolanaRepository {
    override fun base58Encode(byteArray: ByteArray): String {
        return "todo"
    }
}