package com.kriptikz.orehqmobile.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.funkatronics.encoders.Base58
import com.funkatronics.encoders.Base64
import com.kriptikz.orehqmobile.data.database.AppRoomDatabase
import com.kriptikz.orehqmobile.data.entities.SubmissionResult
import com.kriptikz.orehqmobile.data.models.ServerMessage
import com.kriptikz.orehqmobile.data.models.toLittleEndianByteArray
import com.kriptikz.orehqmobile.data.repositories.AppAccountRepository
import com.kriptikz.orehqmobile.data.repositories.IKeypairRepository
import com.kriptikz.orehqmobile.data.repositories.IPoolRepository
import com.kriptikz.orehqmobile.data.repositories.KeypairRepository
import com.kriptikz.orehqmobile.data.repositories.PoolRepository
import com.kriptikz.orehqmobile.data.repositories.SubmissionResultRepository
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import uniffi.orehqmobileffi.DxSolution
import java.util.UUID
import kotlin.math.pow

class MiningWorker(
    appContext: Context,
    workerParams: WorkerParameters
): CoroutineWorker(appContext, workerParams) {
    private var poolRepository: IPoolRepository = PoolRepository()
    private var keypairRepository: IKeypairRepository = KeypairRepository(appContext)
    private val appDb = AppRoomDatabase.getInstance(appContext)
    private val submissionResultDao = appDb.submissionResultDao()
    private var submissionResultRepository = SubmissionResultRepository(submissionResultDao)
    private val appAccountDao = appDb.appAccountDao()
    private var appAccountRepository = AppAccountRepository(appAccountDao)

    private val workId = UUID.randomUUID()

    private var lastSubmissionResultAt = System.currentTimeMillis()


    private var isWebsocketConnected = false

    // keepMining: changed to false when websocket message sender fails so connection will be reestablished
    private var keepMining = true

    private var pubkey: String? = null


    override suspend fun doWork(): Result = coroutineScope{
        Log.d(TAG, "$workId - Starting doWork()")
        val appAccount = appAccountRepository.getAppAccount()
        try {
            if (appAccount != null) {
                appAccountRepository.updateIsMining(true, appAccount.id)

                connectToWebsocket()
                appAccountRepository.updateIsMining(false, appAccount.id)
            }
            return@coroutineScope Result.success()
        } catch(e: Exception) {
            if (appAccount != null) {
                appAccountRepository.updateIsMining(false, appAccount.id)
            }
            return@coroutineScope Result.retry()
        }
    }

    private suspend fun connectToWebsocket() = coroutineScope {
        if (!isWebsocketConnected) {
            isWebsocketConnected = true
            launch(Dispatchers.IO) {
                while (true) {
                    ensureActive()
                    val now = System.currentTimeMillis()
                    val diff = (now - lastSubmissionResultAt) / 1000
                    if (diff > 120) {
                        Log.e(TAG, "Last submission result over 120 seconds ago")
                        poolRepository.disconnectWebsocket()
                        break;
                    }
                    delay(5_000)
                }
            }
            async(Dispatchers.IO) {
                while (true) {
                    ensureActive()
                    val appAccount = appAccountRepository.getAppAccount() ?: break

                    if (!appAccount.isMiningSwitchOn) {
                        break
                    }
                    val result = poolRepository.fetchTimestamp()
                    result.fold(
                        onSuccess = { timestamp ->
                            Log.d(TAG, "$workId - Fetched timestamp: $timestamp")
                            val tsBytes = timestamp.toLittleEndianByteArray()

                            val sig = keypairRepository.signMessage(tsBytes)?.signature
                            if (pubkey == null) {
                                pubkey = getPubkey()
                            }

                            val pk = pubkey

                            if (pk != null && sig != null) {
                                try {
                                    isWebsocketConnected = true
                                    Log.d(TAG, "$workId - Connecting to websocket...")
                                    poolRepository.connectWebSocket(
                                        timestamp,
                                        Base58.encodeToString(sig),
                                        pk
                                    ) {
                                        launch {
                                            sendReadyMessage()
                                        }
                                    }.collect { serverMessage ->
                                        // Handle incoming WebSocket data
                                        Log.d(
                                            TAG,
                                            "$workId - Received WebSocket data: $serverMessage"
                                        )
                                        // Process the data as needed
                                        when (serverMessage) {
                                            is ServerMessage.StartMining -> {
                                                launch(Dispatchers.IO) {
                                                    handleStartMining(
                                                        serverMessage
                                                    )
                                                }
                                            }

                                            is ServerMessage.PoolSubmissionResult -> {
                                                handlePoolSubmissionResult(serverMessage)
                                            }

                                            is ServerMessage.Close -> {
                                                return@collect
                                            }
                                        }
                                    }

                                    isWebsocketConnected = false
                                } catch (e: Exception) {
                                    isWebsocketConnected = false
                                    Log.e(TAG, "WebSocket error: ${e.message}")
                                    when (e) {
                                        is io.ktor.client.plugins.ClientRequestException -> {
                                            val errorBody = e.response.bodyAsText()
                                            Log.e(TAG, "Error body: $errorBody")
                                        }

                                        is io.ktor.client.plugins.ServerResponseException -> {
                                            val errorBody = e.response.bodyAsText()
                                            Log.e(TAG, "Error body: $errorBody")
                                        }

                                        else -> {
                                            Log.e(TAG, "Unexpected error", e)
                                        }
                                    }
                                }

                                Log.d(TAG, "$workId - Websocket connection closed")


                                isWebsocketConnected = false
                            } else {
                                Log.e(TAG, "$workId - pk or sig is null")
                            }
                        },
                        onFailure = { error ->
                            isWebsocketConnected = false
                            Log.e(TAG, "$workId - Error fetching timestamp", error)
                        }
                    )

                    Log.d(TAG, "$workId - Websocket disconnected. Reconnecting in 3 seconds...")
                    delay(3000)
                }
            }
        }
    }

    private suspend fun handleStartMining(startMining: ServerMessage.StartMining) = coroutineScope {
        Log.d(
            TAG, "$workId - Received StartMining: hash=${Base64.encodeToString(startMining.challenge.toByteArray())}, " +
                "nonceRange=${startMining.nonceRange}, cutoff=${startMining.cutoff}")

        lastSubmissionResultAt = System.currentTimeMillis()
        launch(Dispatchers.IO) {
            try {
                val challenge: List<UByte> = startMining.challenge.toList()

                var currentNonce = startMining.nonceRange.first
                val lastNonce = startMining.nonceRange.last
                val noncesPerThread = 10_000uL
                var secondsOfRuntime = startMining.cutoff

                var bestDifficulty = 0u

                while(true) {
                    ensureActive()
                    val startTime = System.nanoTime()
                    val appAccount = appAccountRepository.getAppAccount()!!
                    val threads = calculateThreads(appAccount.miningPowerLevel)

                    if (!appAccount.isMiningSwitchOn) {
                        keepMining = false
                        poolRepository.disconnectWebsocket()
                        break
                    }

                    val maxBatchRuntime = 10uL // 10 seconds
                    val currentBatchMaxRuntime = minOf(secondsOfRuntime, maxBatchRuntime)
                    val jobs = List(threads) {
                        val nonceForThisJob = currentNonce
                        currentNonce += noncesPerThread
                        async(Dispatchers.Default) {
                            uniffi.orehqmobileffi.dxHash(challenge, currentBatchMaxRuntime, nonceForThisJob, lastNonce)
                        }
                    }
                    val results = jobs.awaitAll()
                    val endTime = System.nanoTime()

                    val totalNoncesChecked = results.sumOf { it.noncesChecked }
                    val elapsedTimeSeconds = if (endTime >= startTime) {
                        (endTime - startTime) / 1_000_000_000.0
                    } else {
                        0.0
                    }

                    if (secondsOfRuntime >= elapsedTimeSeconds.toUInt()) {
                        secondsOfRuntime -= elapsedTimeSeconds.toUInt()
                    }
                    val newHashrate = (totalNoncesChecked.toDouble() / elapsedTimeSeconds).toInt()

                    val bestResult = results.maxByOrNull { it.difficulty }
                    if (bestResult != null) {
                        if (bestResult.difficulty > bestDifficulty) {
                            bestDifficulty = bestResult.difficulty
                            bestResult.let { solution ->
                                Log.d(TAG, "$workId - Send submission with diff: ${solution.difficulty}")
                                sendSubmissionMessage(solution)
                            }
                        }
                    }

                    val appAccountAfter = appAccountRepository.getAppAccount()!!
                    if (!appAccountAfter.isMiningSwitchOn) {
                        keepMining = false
                        poolRepository.disconnectWebsocket()
                        break
                    }

                    Log.d(TAG, "$workId - Hashpower: $newHashrate")
                    appAccountRepository.updateHashpower(newHashrate, appAccount.id)
                    appAccountRepository.updateLastDifficulty(bestDifficulty.toInt(), appAccount.id)

                    if (secondsOfRuntime <= 2u) {
                        break;
                    }
                }

                if (keepMining) {
                    sendReadyMessage()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handlePoolSubmissionResult(poolSubmissionResult: ServerMessage.PoolSubmissionResult) {
        Log.d(TAG, "$workId - Received pool submission result:")
        Log.d(TAG, "$workId - Difficulty: ${poolSubmissionResult.difficulty}")
        Log.d(TAG, "$workId - Difficulty Int: ${poolSubmissionResult.difficulty.toInt()}")
        Log.d(TAG, "$workId - Total Balance: ${"%.11f".format(poolSubmissionResult.totalBalance)}")
        Log.d(TAG, "$workId - Total Rewards: ${"%.11f".format(poolSubmissionResult.totalRewards)}")
        Log.d(TAG, "$workId - Top Stake: ${"%.11f".format(poolSubmissionResult.topStake)}")
        Log.d(TAG, "$workId - Multiplier: ${"%.11f".format(poolSubmissionResult.multiplier)}")
        Log.d(TAG, "$workId - Active Miners: ${poolSubmissionResult.activeMiners}")
        Log.d(TAG, "$workId - Challenge: ${poolSubmissionResult.challenge.joinToString(", ")}")
        Log.d(TAG, "$workId - Best Nonce: ${poolSubmissionResult.bestNonce}")
        Log.d(TAG, "$workId - Miner Supplied Difficulty: ${poolSubmissionResult.minerSuppliedDifficulty}")
        Log.d(TAG, "$workId - Miner Earned Rewards: ${"%.11f".format(poolSubmissionResult.minerEarnedRewards)}")
        Log.d(TAG, "$workId - Miner Percentage: ${"%.11f".format(poolSubmissionResult.minerPercentage)}")

//        _poolBalance.value = poolSubmissionResult.totalBalance
//        _poolMultiplier.value = poolSubmissionResult.multiplier
//        _topStake.value = poolSubmissionResult.topStake
//        _activeMiners.value = poolSubmissionResult.activeMiners

        val totalRewards = (poolSubmissionResult.totalRewards * 10.0.pow(11.0)).toLong()
        val earnings = (poolSubmissionResult.minerEarnedRewards * 10.0.pow(11.0)).toLong()

        submissionResultRepository.insertSubmissionResult(
            SubmissionResult(
            poolSubmissionResult.difficulty.toInt(),
            totalRewards,
            poolSubmissionResult.minerPercentage,
            poolSubmissionResult.minerSuppliedDifficulty.toInt(),
            earnings,
        ))
    }

    private suspend fun sendReadyMessage() = coroutineScope {
        launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis() / 1000 // Current time in seconds
                val msg = now.toLittleEndianByteArray()
                val sig = keypairRepository.signMessage(msg)?.signature
                val publicKey = Base58.decode(pubkey!!)

                val binData = ByteArray(1 + 32 + 8 + sig!!.size).apply {
                    this[0] = 0 // Ready message type
                    System.arraycopy(publicKey, 0, this, 1, 32)
                    System.arraycopy(msg, 0, this, 33, 8)
                    System.arraycopy(sig, 0, this, 41, sig.size)
                }

                poolRepository.sendWebSocketMessage(binData)
                Log.d(TAG, "$workId - Sent Ready message")
            } catch (e: Exception) {
                Log.e(TAG, "$workId - Error sending Ready message", e)
                keepMining = false
                poolRepository.disconnectWebsocket()
            }
        }
    }

    private suspend fun sendSubmissionMessage(submission: DxSolution) = coroutineScope {
        launch(Dispatchers.IO) {
            try {
                val bestHashBin = submission.digest.toUByteArray()
                val bestNonceBin = submission.nonce.toUByteArray()

                val hashNonceMessage = UByteArray(24)
                bestHashBin.copyInto(hashNonceMessage, 0, 0, 16)
                bestNonceBin.copyInto(hashNonceMessage, 16, 0, 8)

                val sig = keypairRepository.signMessage(hashNonceMessage.toByteArray())?.signature
                val publicKey = Base58.decode(pubkey!!)

                // Convert signature to Base58 string
                val signatureBase58 = Base58.encodeToString(sig!!)

                val binData = ByteArray(57 + signatureBase58.toByteArray().size).apply {
                    this[0] = 2 // BestSolution Message
                    System.arraycopy(bestHashBin.toByteArray(), 0, this, 1, 16)
                    System.arraycopy(bestNonceBin.toByteArray(), 0, this, 17, 8)
                    System.arraycopy(publicKey, 0, this, 25, 32)
                    System.arraycopy(signatureBase58.toByteArray(), 0, this, 57, signatureBase58.toByteArray().size)
                }

                poolRepository.sendWebSocketMessage(binData)
                Log.d(TAG, "$workId - Sent BestSolution message")
            } catch (e: Exception) {
                Log.e(TAG, "$workId - Error sending BestSolution message", e)
                keepMining = false
                poolRepository.disconnectWebsocket()
            }
        }
    }

    private fun getPubkey(): String? {
        return keypairRepository.getPubkey()?.toString()
    }

    companion object {
        private const val TAG = "MiningWorker"
        private val availableThreads = Runtime.getRuntime().availableProcessors()

        fun calculateThreads(powerLevel: Int): Int {
            val newThreadsCount = when (powerLevel) {
                0 -> {
                    1
                }
                1 -> {
                    availableThreads / 3
                }
                2 -> {
                    availableThreads / 2
                }
                3 -> {
                    availableThreads - (availableThreads / 3)
                }
                4 -> {
                    availableThreads
                }
                else -> {
                    availableThreads
                }
            }

            return newThreadsCount
        }
    }
}