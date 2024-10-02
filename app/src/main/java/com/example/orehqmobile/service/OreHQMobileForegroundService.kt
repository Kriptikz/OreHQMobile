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
import com.example.orehqmobile.notification.NotificationsHelper
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Simple foreground service that shows a notification to the user.
 */
class OreHQMobileForegroundService : Service() {
    private val binder = LocalBinder()

    private val coroutineScope = CoroutineScope(Job())
    private var timerJob: Job? = null

    private val _locationFlow = MutableStateFlow<Location?>(null)
    var locationFlow: StateFlow<Location?> = _locationFlow

    private var hashpower = 0u
    private var difficulty = 0u

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

        Toast.makeText(this, "Foreground Service created", Toast.LENGTH_SHORT).show()

        startServiceRunningTicker()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")

        timerJob?.cancel()
        coroutineScope.coroutineContext.cancelChildren()

        Toast.makeText(this, "Foreground Service destroyed", Toast.LENGTH_SHORT).show()
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
            NotificationsHelper.buildNotification(this, 0u, 0u),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        )

        coroutineScope.launch(Dispatchers.IO) {
            val challenge: List<UByte> = List(32) { 0.toUByte() }


            var currentNonce = 0uL
            val lastNonce = 10_000_000uL
            val noncesPerThread = 10_000uL

            while (true) {
                Log.d(TAG, "Start Mining")
                var secondsOfRuntime = 60uL + 5uL

                var bestDifficulty = 0u
                val startTime = System.nanoTime()
                Log.d(TAG, "Seconds of run time: $secondsOfRuntime")
                val maxBatchRuntime = 10uL // 10 seconds
                val currentBatchMaxRuntime = minOf(secondsOfRuntime, maxBatchRuntime)
                val jobs = List(6) {
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

//                    homeUiState = homeUiState.copy(
//                        difficulty = bestResult.difficulty ?: 0u
//                    )
                    }
                }

                withContext(Dispatchers.Main) {
                    hashpower = newHashrate
                    difficulty = bestDifficulty
                    NotificationsHelper.updateNotification(this@OreHQMobileForegroundService, NOTIFICATION_ID, hashpower, difficulty)

                }

                Log.d("HomeScreenViewModel", "Hashpower: $newHashrate")
            }
        }
    }

    /**
     * Stops the foreground service and removes the notification.
     * Can be called from inside or outside the service.
     */
    fun stopForegroundService() {
        stopSelf()
    }

    /**
     * Starts a ticker that shows a toast every [TICKER_PERIOD_SECONDS] seconds to indicate that the service is still running.
     */
    private fun startServiceRunningTicker() {
        timerJob?.cancel()
        timerJob = coroutineScope.launch {
            tickerFlow()
                .collectLatest {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@OreHQMobileForegroundService,
                            "Foreground Service still running!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }
    }

    private fun tickerFlow(
        period: Duration = TICKER_PERIOD_SECONDS,
        initialDelay: Duration = TICKER_PERIOD_SECONDS
    ) = flow {
        delay(initialDelay)
        while (true) {
            emit(Unit)
            delay(period)
        }
    }

    companion object {
        private const val TAG = "OreHQMobileForegroundService"
        private val LOCATION_UPDATES_INTERVAL_MS = 1.seconds.inWholeMilliseconds
        private val TICKER_PERIOD_SECONDS = 5.seconds
    }
}
