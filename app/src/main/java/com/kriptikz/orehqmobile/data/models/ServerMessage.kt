package com.kriptikz.orehqmobile.data.models

import android.util.Log
import kotlin.math.pow

sealed class ServerMessage {
    companion object {
        fun fromUByteArray(data: UByteArray): ServerMessage? {
            if (data.isEmpty()) return null

            return when (data[0].toUInt()) {
                0U -> parseStartMining(data)
                1U -> parsePoolSubmissionResult(data)
                else -> {
                    Log.w("ServerMessage", "Unknown message type: ${data[0]}")
                    null
                }
            }
        }

        private fun parseStartMining(data: UByteArray): StartMining? {
            if (data.size < 57) {
                Log.w("ServerMessage", "Invalid data for StartMining message")
                return null
            }

            val challenge = data.slice(1..32).toUByteArray()
            val cutoff = data.slice(33..40).toUByteArray().toULong()
            val nonceStart = data.slice(41..48).toUByteArray().toULong()
            Log.d("ServerMessage", "Nonce Start: $nonceStart")
            val nonceEnd = data.slice(49..56).toUByteArray().toULong()
            Log.d("ServerMessage", "Nonce End: $nonceEnd")

            return StartMining(challenge, nonceStart until nonceEnd, cutoff)
        }

        @OptIn(ExperimentalUnsignedTypes::class)
        private fun parsePoolSubmissionResult(data: UByteArray): PoolSubmissionResult? {
            if (data.size < 101) {
                Log.w("ServerMessage", "Invalid data for PoolSubmissionResult message")
                return null
            }

            val difficulty = data.slice(1..4).toUByteArray().toUInt()
            val totalBalance = data.slice(5..12).toUByteArray().toDouble()
            val totalRewards = data.slice(13..20).toUByteArray().toDouble()
            val topStake = data.slice(21..28).toUByteArray().toDouble()
            val multiplier = data.slice(29..36).toUByteArray().toDouble()
            val activeMiners = data.slice(37..40).toUByteArray().toUInt()
            val challenge = data.slice(41..72).toUByteArray()
            val bestNonce = data.slice(73..80).toUByteArray().toULong()
            val minerSuppliedDifficulty = data.slice(81..84).toUByteArray().toUInt()
            val minerEarnedRewards = data.slice(85..92).toUByteArray().toDouble()
            val minerPercentage = data.slice(93..100).toUByteArray().toDouble()

            return PoolSubmissionResult(
                difficulty,
                totalBalance,
                totalRewards,
                topStake,
                multiplier,
                activeMiners,
                challenge,
                bestNonce,
                minerSuppliedDifficulty,
                minerEarnedRewards,
                minerPercentage
            )
        }
    }

    data class Close(val reason: String) : ServerMessage() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Close

            return reason.contentEquals(other.reason)
        }

        override fun hashCode(): Int {
            var result = reason.toByteArray().contentHashCode()
            return result
        }

    }

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

    data class PoolSubmissionResult(
        val difficulty: UInt,
        val totalBalance: Double,
        val totalRewards: Double,
        val topStake: Double,
        val multiplier: Double,
        val activeMiners: UInt,
        val challenge: UByteArray,
        val bestNonce: ULong,
        val minerSuppliedDifficulty: UInt,
        val minerEarnedRewards: Double,
        val minerPercentage: Double
    ) : ServerMessage() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as PoolSubmissionResult

            return difficulty == other.difficulty &&
                   totalBalance == other.totalBalance &&
                   totalRewards == other.totalRewards &&
                   topStake == other.topStake &&
                   multiplier == other.multiplier &&
                   activeMiners == other.activeMiners &&
                   challenge.contentEquals(other.challenge) &&
                   bestNonce == other.bestNonce &&
                   minerSuppliedDifficulty == other.minerSuppliedDifficulty &&
                   minerEarnedRewards == other.minerEarnedRewards &&
                   minerPercentage == other.minerPercentage
        }

        override fun hashCode(): Int {
            var result = difficulty.hashCode()
            result = 31 * result + totalBalance.hashCode()
            result = 31 * result + totalRewards.hashCode()
            result = 31 * result + topStake.hashCode()
            result = 31 * result + multiplier.hashCode()
            result = 31 * result + activeMiners.hashCode()
            result = 31 * result + challenge.contentHashCode()
            result = 31 * result + bestNonce.hashCode()
            result = 31 * result + minerSuppliedDifficulty.hashCode()
            result = 31 * result + minerEarnedRewards.hashCode()
            result = 31 * result + minerPercentage.hashCode()
            return result
        }
    }
}

// Helper conversion functions
public fun ULong.toLittleEndianByteArray(): ByteArray {
    return ByteArray(8) { i -> (this shr (8 * i)).toByte() }
}

fun Long.toLittleEndianByteArray(): ByteArray {
    return ByteArray(8) { i -> (this shr (8 * i) and 0xFFL).toByte() }
}

fun UByteArray.toULong(): ULong = this.foldIndexed(0UL) { index, acc, byte ->
    acc or (byte.toULong() shl (index * 8))
}

private fun List<UByte>.toUByteArray(): UByteArray {
    return UByteArray(size) { this[it] }
}

fun ULong.toLittleEndianUByteArray(): UByteArray {
    return UByteArray(8) { i -> ((this shr (8 * i)) and 0xFFU).toUByte() }
}

fun UByteArray.toUInt(): UInt = this.foldIndexed(0U) { index, acc, byte ->
    acc or (byte.toUInt() shl (index * 8))
}

fun UByteArray.toDouble(): Double {
  val bits = this.foldIndexed(0L) { index, acc, byte ->
      acc or ((byte.toLong() and 0xFFL) shl (index * 8))
  }
  return Double.fromBits(bits)
}

