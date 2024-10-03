package com.example.orehqmobile

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.orehqmobile.data.repositories.KeypairRepository
import com.example.orehqmobile.service.OreHQMobileForegroundService
import com.example.orehqmobile.ui.OreHQMobileApp
import com.example.orehqmobile.ui.screens.ForegroundServiceSampleScreen
import com.example.orehqmobile.ui.screens.home_screen.HomeScreenViewModel
import com.example.orehqmobile.ui.theme.OreHQMobileTheme
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.bouncycastle.crypto.AsymmetricCipherKeyPair

class MainActivity : ComponentActivity() {
    private lateinit var homeScreenViewModel: HomeScreenViewModel
    private var oreHQMobileService: OreHQMobileForegroundService? = null

    private var serviceBoundState by mutableStateOf(false)

    private var threadCount by mutableStateOf<Int>(1)
    private var hashpower by mutableStateOf<UInt>(0u)
    private var difficulty by mutableStateOf<UInt>(0u)
    private var lastDifficulty by mutableStateOf<UInt>(0u)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Log.d(TAG, "onServiceConnected")

            val binder = service as OreHQMobileForegroundService.LocalBinder
            oreHQMobileService = binder.getService()
            binder.setSignCallback(homeScreenViewModel.getSignFunction())
            binder.getPubkeyCallback(homeScreenViewModel.getPubkeyFunction())
            serviceBoundState = true

            onServiceConnected()
        }

        override fun onServiceDisconnected(arg0: ComponentName?) {
            Log.d(TAG, "onServiceDisconnected")

            serviceBoundState = false
            oreHQMobileService = null
        }
    }

    // we need notification permission to be able to display a notification for the foreground service
    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            // if permission was denied, the service can still run only the notification won't be visible
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        val keypairRepository = KeypairRepository(this);

        val hasEncryptedKeypair = keypairRepository.encryptedKeypairExists();

        val sender = ActivityResultSender(this)

        homeScreenViewModel = ViewModelProvider(this, HomeScreenViewModel.Factory)[HomeScreenViewModel::class.java]


        setContent {
            OreHQMobileTheme {
                OreHQMobileApp(
                    sender,
                    hasEncryptedKeypair,
                    threadCount = threadCount,
                    hashpower = hashpower,
                    difficulty = difficulty,
                    lastDifficulty =  lastDifficulty,
                    oreHQMobileService,
                    onClickService = ::onStartOrStopForegroundServiceClick,
                    serviceRunning = serviceBoundState,
                    homeScreenViewModel
                )
            }
        }

        checkAndRequestNotificationPermission()
        tryToBindToServiceIfRunning()
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(connection)
    }

    /**
     * Check for notification permission before starting the service so that the notification is visible
     */
    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)) {
                android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                    // permission already granted
                }

                else -> {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    private fun onStartOrStopForegroundServiceClick() {
        if (oreHQMobileService == null) {
            startForegroundService()
        } else {
            // service is already running, stop it
            oreHQMobileService?.stopForegroundService()
        }
    }

    /**
     * Creates and starts the OreHQMobileForgroundService as a foreground service.
     *
     * It also tries to bind to the service to update the UI with location updates.
     */
    private fun startForegroundService() {
        // start the service
        startForegroundService(Intent(this, OreHQMobileForegroundService::class.java))

        // bind to the service to update UI
        tryToBindToServiceIfRunning()
    }

    private fun tryToBindToServiceIfRunning() {
        Intent(this, OreHQMobileForegroundService::class.java).also { intent ->
            bindService(intent, connection, 0)
        }
    }

    private fun onServiceConnected() {
        lifecycleScope.launch {
            oreHQMobileService?.threadCount?.collectLatest { it ->
                threadCount = it
            }
        }
        lifecycleScope.launch {
            oreHQMobileService?.hashpower?.collectLatest { it ->
                hashpower = it
            }
        }
        lifecycleScope.launch {
            oreHQMobileService?.difficulty?.collectLatest { it ->
                difficulty = it
            }
        }
        lifecycleScope.launch {
            oreHQMobileService?.lastDifficulty?.collectLatest { it ->
                lastDifficulty = it
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
