package com.example.orehqmobile.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.ServiceCompat
import androidx.lifecycle.viewModelScope
import com.example.orehqmobile.data.models.ServerMessage
import com.example.orehqmobile.data.models.toLittleEndianByteArray
import com.example.orehqmobile.data.repositories.IPoolRepository
import com.example.orehqmobile.data.repositories.PoolRepository
import com.example.orehqmobile.notification.NotificationsHelper
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Simple foreground service that shows a notification to the user.
 */
class OreHQMobileForegroundService : Service() {
    private var signCallback: ((ByteArray) -> ByteArray)? = null
    private var pubkeyCallback: (() -> String)? = null
    private val binder = LocalBinder()

    private var poolRepository: IPoolRepository = PoolRepository()
    private val runtimeAvailableThreads = Runtime.getRuntime().availableProcessors()

    private val coroutineScope = CoroutineScope(Job())

    private val _threadCount = MutableStateFlow<Int>(1)
    var threadCount: StateFlow<Int> = _threadCount

    private val _hashpower = MutableStateFlow<UInt>(0u)
    var hashpower: StateFlow<UInt> = _hashpower

    private val _difficulty = MutableStateFlow<UInt>(0u)
    var difficulty: StateFlow<UInt> = _difficulty

    private val _lastDifficulty = MutableStateFlow<UInt>(0u)
    var lastDifficulty: StateFlow<UInt> = _lastDifficulty

    private var isWebsocketConnected = false
    private var pubkey: String? = null

    private val NOTIFICATION_ID = 1

    inner class LocalBinder : Binder() {
        fun getService(): OreHQMobileForegroundService = this@OreHQMobileForegroundService
        fun setSignCallback(callback: (ByteArray) -> ByteArray) {
            signCallback = callback
        }
        fun getPubkeyCallback(callback: () -> String) {
            pubkeyCallback = callback
        }
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

        connectToWebsocket()
    }

    /**
     * Stops the foreground service and removes the notification.
     * Can be called from inside or outside the service.
     */
    fun stopForegroundService() {
        stopSelf()
    }

    fun decreaseSelectedThreads() {
      _threadCount.value.let { currentCount ->
          if (currentCount > 1) {
              _threadCount.value = currentCount - 1
          }
      }
  }

  fun increaseSelectedThreads() {
      _threadCount.value.let { currentCount ->
          if (currentCount < runtimeAvailableThreads) {
              _threadCount.value = currentCount + 1
          }
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
                var secondsOfRuntime = startMining.cutoff + 5uL

                var bestDifficulty = 0u

                while(true) {
                    val startTime = System.nanoTime()
                    Log.d("HomeScreenViewModel", "Seconds of run time: $secondsOfRuntime")
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
                                //sendSubmissionMessage(solution)
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

                sendReadyMessage()

                // reset best diff
                _lastDifficulty.value = _difficulty.value
                _difficulty.value = 0u
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@OreHQMobileForegroundService,
                        "Mining Interval Failed!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun connectToWebsocket() {
        if (!isWebsocketConnected) {
            coroutineScope.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) {
                    isWebsocketConnected = true
                }
                val result = poolRepository.fetchTimestamp()
                result.fold(
                    onSuccess = { timestamp ->
                        Log.d(TAG, "Fetched timestamp: $timestamp")
                        val tsBytes = timestamp.toLittleEndianByteArray()

                        val sig = signData(tsBytes)
                        //val signatureResult = solanaRepository.signMessage(tsBytes, listOf(keypair!!))

                        if (pubkey == null) {
                            pubkey = getPubkey()
                        }

                        val pk = pubkey

                        if (pk != null && sig != null) {
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    Log.d(TAG, "Connecting to websocket...")
                                    poolRepository.connectWebSocket(timestamp, Base58.encodeToString(sig), pk, ::sendReadyMessage
                                    ).collect { serverMessage ->
                                        // Handle incoming WebSocket data
                                        Log.d(TAG, "Received WebSocket data: $serverMessage")
                                        // Process the data as needed
                                        when (serverMessage) {
                                            is ServerMessage.StartMining -> handleStartMining(serverMessage)
                                            is ServerMessage.PoolSubmissionResult -> {}//handlePoolSubmissionResult(serverMessage)
                                        }
                                    }

                                    withContext(Dispatchers.Main) {
                                        isWebsocketConnected = false
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isWebsocketConnected = false
                                    }
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


                                withContext(Dispatchers.Main) {
                                    isWebsocketConnected = false
                                }
                            }

                        } else {
                            Log.e(TAG, "pk or sig is null")
                        }
                    },
                    onFailure = { error ->
                        withContext(Dispatchers.Main) {
                            isWebsocketConnected = false
                        }
                        Log.e(TAG, "Error fetching timestamp", error)
                    }
                )

            }
        }
    }

    private fun sendReadyMessage() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis() / 1000 // Current time in seconds
                val msg = now.toLittleEndianByteArray()
                val sig = signData(msg)
                val publicKey = Base58.decode(pubkey!!)

                val binData = ByteArray(1 + 32 + 8 + sig!!.size).apply {
                    this[0] = 0 // Ready message type
                    System.arraycopy(publicKey, 0, this, 1, 32)
                    System.arraycopy(msg, 0, this, 33, 8)
                    System.arraycopy(sig, 0, this, 41, sig!!.size)
                }

                poolRepository.sendWebSocketMessage(binData)
                Log.d(TAG, "Sent Ready message")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending Ready message", e)
            }
        }
    }

    private fun signData(data: ByteArray): ByteArray? {
        return signCallback?.invoke(data)
    }

    private fun getPubkey(): String? {
        return pubkeyCallback?.invoke()
    }

    companion object {
        private const val TAG = "OreHQMobileForegroundService"
    }
}
