package com.kriptikz.orehqmobile.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.ServiceCompat
import com.kriptikz.orehqmobile.data.database.AppRoomDatabase
import com.kriptikz.orehqmobile.data.entities.SubmissionResult
import com.kriptikz.orehqmobile.data.models.ServerMessage
import com.kriptikz.orehqmobile.data.models.toLittleEndianByteArray
import com.kriptikz.orehqmobile.data.repositories.IKeypairRepository
import com.kriptikz.orehqmobile.data.repositories.IPoolRepository
import com.kriptikz.orehqmobile.data.repositories.KeypairRepository
import com.kriptikz.orehqmobile.data.repositories.PoolRepository
import com.kriptikz.orehqmobile.data.repositories.SubmissionResultRepository
import com.kriptikz.orehqmobile.notification.NotificationsHelper
import com.funkatronics.encoders.Base58
import com.funkatronics.encoders.Base64
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.orehqmobileffi.DxSolution
import kotlin.math.pow

/**
 * Simple foreground service that shows a notification to the user.
 */
class OreHQMobileForegroundService : Service() {
    private val binder = LocalBinder()

    private var poolRepository: IPoolRepository = PoolRepository()
    private var keypairRepository: IKeypairRepository = KeypairRepository(this)

    private val appDb = AppRoomDatabase.getInstance(this)
    private val submissionResultDao = appDb.submissionResultDao()
    private var submissionResultRepository = SubmissionResultRepository(submissionResultDao)

    private val runtimeAvailableThreads = Runtime.getRuntime().availableProcessors()

    private val coroutineScope = CoroutineScope(Job())

    private val _threadCount = MutableStateFlow<Int>(1)
    var threadCount: StateFlow<Int> = _threadCount

    private val _hashpower = MutableStateFlow<UInt>(0u)
    var hashpower: StateFlow<UInt> = _hashpower

    private val _difficulty = MutableStateFlow<UInt>(0u)
    var difficulty: StateFlow<UInt> = _difficulty

    private val _poolBalance = MutableStateFlow<Double>(0.0)
    var poolBalance: StateFlow<Double> = _poolBalance

    private val _topStake = MutableStateFlow<Double>(0.0)
    var topStake: StateFlow<Double> = _topStake

    private val _poolMultiplier = MutableStateFlow<Double>(0.0)
    var poolMultiplier: StateFlow<Double> = _poolMultiplier

    private val _activeMiners = MutableStateFlow<UInt>(0u)
    var activeMiners: StateFlow<UInt> = _activeMiners

    private var keepMining = true
    private var isWebsocketConnected = false
    private var pubkey: String? = null

    var powerSlider: Float = 0f

    private val NOTIFICATION_ID = 1

    inner class LocalBinder : Binder() {
        fun getService(): OreHQMobileForegroundService = this@OreHQMobileForegroundService
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "onBind")
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")

        startAsForegroundService()

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        Toast.makeText(this, "Mining Started", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")

        coroutineScope.coroutineContext.cancelChildren()
        Toast.makeText(this, "Mining Stopped", Toast.LENGTH_SHORT).show()
    }

    /**
     * Promotes the service to a foreground service, showing a notification to the user.
     *
     * This needs to be called within 10 seconds of starting the service or the system will throw an exception.
     */
    private fun startAsForegroundService() {
        // create the notification channel
        NotificationsHelper.createNotificationChannel(this)

        // promote service to foreground service
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            NotificationsHelper.buildNotification(this, _threadCount.value, 0u, 0u),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        )

        //connectToWebsocket()
    }

    /**
     * Stops the foreground service and removes the notification.
     * Can be called from inside or outside the service.
     */
    fun stopForegroundService() {
        stopSelf()
    }

    fun updateSelectedThreads(count: Int) {
        _threadCount.value.let {
            val newThreadsCount = when (count) {
                0 -> {
                    powerSlider = 0f
                    1
                }
                1 -> {
                    powerSlider = 1f
                    runtimeAvailableThreads / 3
                }
                2 -> {
                    powerSlider = 2f
                    runtimeAvailableThreads / 2
                }
                3 -> {
                    powerSlider = 3f
                    runtimeAvailableThreads - (runtimeAvailableThreads / 3)
                }
                4 -> {
                    powerSlider = 4f
                    runtimeAvailableThreads
                }
                else -> {
                    powerSlider = 4f
                    runtimeAvailableThreads
                }
            }
            _threadCount.value = newThreadsCount
            NotificationsHelper.updateNotification(this@OreHQMobileForegroundService, NOTIFICATION_ID, _threadCount.value, _hashpower.value, _difficulty.value)
        }
    }

    private fun handleStartMining(startMining: ServerMessage.StartMining) {
        Log.d(TAG, "Received StartMining: hash=${Base64.encodeToString(startMining.challenge.toByteArray())}, " +
                "nonceRange=${startMining.nonceRange}, cutoff=${startMining.cutoff}")
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val challenge: List<UByte> = startMining.challenge.toList()

                var currentNonce = startMining.nonceRange.first
                val lastNonce = startMining.nonceRange.last
                val noncesPerThread = 10_000uL
                var secondsOfRuntime = startMining.cutoff

                var bestDifficulty = 0u

                while(keepMining) {
                    val startTime = System.nanoTime()
                    Log.d(TAG, "Seconds of run time: $secondsOfRuntime")
                    val maxBatchRuntime = 10uL // 10 seconds
                    val currentBatchMaxRuntime = minOf(secondsOfRuntime, maxBatchRuntime)
                    val jobs = List(_threadCount.value) {
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
                    val newHashrate = (totalNoncesChecked.toDouble() / elapsedTimeSeconds).toUInt()

                    val bestResult = results.maxByOrNull { it.difficulty }
                    if (bestResult != null) {
                        if (bestResult.difficulty > bestDifficulty) {
                            bestDifficulty = bestResult.difficulty
                            bestResult.let { solution ->
                                Log.d(TAG, "Send submission with diff: ${solution.difficulty}")
                                sendSubmissionMessage(solution)
                            }
                        }
                    }

                    Log.d(TAG, "Hashpower: $newHashrate")

                    withContext(Dispatchers.Main) {
                        _hashpower.value = newHashrate
                        _difficulty.value = bestDifficulty
                        NotificationsHelper.updateNotification(this@OreHQMobileForegroundService, NOTIFICATION_ID, _threadCount.value, _hashpower.value, _difficulty.value)
                    }

                    if (secondsOfRuntime <= 2u) {
                        break;
                    }
                }

                if (keepMining) {
                    sendReadyMessage()
                }
            } catch (e: Exception) {
                e.printStackTrace()
//                Toast.makeText(
//                    this@OreHQMobileForegroundService,
//                    "Mining Interval Failed!",
//                    Toast.LENGTH_SHORT
//                ).show()
            }
        }
    }

    private fun connectToWebsocket() {
        if (!isWebsocketConnected) {
            isWebsocketConnected = true
            coroutineScope.launch(Dispatchers.IO) {
                while (true) {
                    val result = poolRepository.fetchTimestamp()
                    result.fold(
                        onSuccess = { timestamp ->
                            Log.d(TAG, "Fetched timestamp: $timestamp")
                            val tsBytes = timestamp.toLittleEndianByteArray()

                            val sig = keypairRepository.signMessage(tsBytes)?.signature
                            if (pubkey == null) {
                                pubkey = getPubkey()
                            }

                            val pk = pubkey

                            if (pk != null && sig != null) {
                                try {
                                    keepMining = true
                                    isWebsocketConnected = true
                                    Log.d(TAG, "Connecting to websocket...")
                                    poolRepository.connectWebSocket(
                                        timestamp,
                                        Base58.encodeToString(sig),
                                        pk,
                                        ::sendReadyMessage
                                    ).collect { serverMessage ->
                                        // Handle incoming WebSocket data
                                        Log.d(
                                            TAG,
                                            "Received WebSocket data: $serverMessage"
                                        )
                                        // Process the data as needed
                                        when (serverMessage) {
                                            is ServerMessage.StartMining -> handleStartMining(
                                                serverMessage
                                            )

                                            is ServerMessage.PoolSubmissionResult -> {
                                                handlePoolSubmissionResult(serverMessage)
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

                                Log.d(TAG, "Websocket connection closed")


                                isWebsocketConnected = false
                            } else {
                                Log.e(TAG, "pk or sig is null")
                            }
                        },
                        onFailure = { error ->
                            isWebsocketConnected = false
                            Log.e(TAG, "Error fetching timestamp", error)
                        }
                    )

                    Log.d(TAG, "Websocket disconnected. Reconnecting in 3 seconds...")
                    delay(3000)
                }
            }
        }
    }

    private fun sendReadyMessage() {
        coroutineScope.launch(Dispatchers.IO) {
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
                Log.d(TAG, "Sent Ready message")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending Ready message", e)
                keepMining = false
                poolRepository.disconnectWebsocket()
            }
        }
    }

    private fun handlePoolSubmissionResult(poolSubmissionResult: ServerMessage.PoolSubmissionResult) {
        Log.d(TAG, "Received pool submission result:")
        Log.d(TAG, "Difficulty: ${poolSubmissionResult.difficulty}")
        Log.d(TAG, "Difficulty Int: ${poolSubmissionResult.difficulty.toInt()}")
        Log.d(TAG, "Total Balance: ${"%.11f".format(poolSubmissionResult.totalBalance)}")
        Log.d(TAG, "Total Rewards: ${"%.11f".format(poolSubmissionResult.totalRewards)}")
        Log.d(TAG, "Top Stake: ${"%.11f".format(poolSubmissionResult.topStake)}")
        Log.d(TAG, "Multiplier: ${"%.11f".format(poolSubmissionResult.multiplier)}")
        Log.d(TAG, "Active Miners: ${poolSubmissionResult.activeMiners}")
        Log.d(TAG, "Challenge: ${poolSubmissionResult.challenge.joinToString(", ")}")
        Log.d(TAG, "Best Nonce: ${poolSubmissionResult.bestNonce}")
        Log.d(TAG, "Miner Supplied Difficulty: ${poolSubmissionResult.minerSuppliedDifficulty}")
        Log.d(TAG, "Miner Earned Rewards: ${"%.11f".format(poolSubmissionResult.minerEarnedRewards)}")
        Log.d(TAG, "Miner Percentage: ${"%.11f".format(poolSubmissionResult.minerPercentage)}")

        _poolBalance.value = poolSubmissionResult.totalBalance
        _poolMultiplier.value = poolSubmissionResult.multiplier
        _topStake.value = poolSubmissionResult.topStake
        _activeMiners.value = poolSubmissionResult.activeMiners

        val totalRewards = (poolSubmissionResult.totalRewards * 10.0.pow(11.0)).toLong()
        val earnings = (poolSubmissionResult.minerEarnedRewards * 10.0.pow(11.0)).toLong()

        submissionResultRepository.insertSubmissionResult(SubmissionResult(
            poolSubmissionResult.difficulty.toInt(),
            totalRewards,
            poolSubmissionResult.minerPercentage,
            poolSubmissionResult.minerSuppliedDifficulty.toInt(),
            earnings,
        ))

        //Toast.makeText(this@OreHQMobileForegroundService, "${"%.11f".format(poolSubmissionResult.minerEarnedRewards)}", Toast.LENGTH_LONG)
    }

    private fun sendSubmissionMessage(submission: DxSolution) {
        coroutineScope.launch(Dispatchers.IO) {
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
                Log.d(TAG, "Sent BestSolution message")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending BestSolution message", e)
                keepMining = false
                poolRepository.disconnectWebsocket()
            }
        }
    }

    private fun getPubkey(): String? {
        return keypairRepository.getPubkey()?.toString()
    }

    companion object {
        private const val TAG = "OreHQMobileForegroundService"
    }
}
