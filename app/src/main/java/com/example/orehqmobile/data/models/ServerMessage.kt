package com.example.orehqmobile.data.models

sealed class ServerMessage {
    data class StartMining(
        val challenge: UByteArray,
        val nonceRange: ULongRange,
        val cutoff: ULong
    ) : ServerMessage() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as StartMining

            if (!challenge.contentEquals(other.challenge)) return false
            if (nonceRange != other.nonceRange) return false
            if (cutoff != other.cutoff) return false

            return true
        }

        override fun hashCode(): Int {
            var result = challenge.contentHashCode()
            result = 31 * result + nonceRange.hashCode()
            result = 31 * result + cutoff.hashCode()
            return result
        }
    }
}