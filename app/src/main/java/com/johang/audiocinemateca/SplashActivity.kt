package com.johang.audiocinemateca

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.domain.usecase.LoginUseCase
import com.johang.audiocinemateca.presentation.theme.AudiocinematecaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SplashActivity : ComponentActivity() {

    @Inject
    lateinit var loginUseCase: LoginUseCase

    @Inject
    lateinit var sharedPreferencesManager: SharedPreferencesManager

    private val splashDurationState = mutableStateOf(2500L)
    private var isFlowStarted = false

    private val requestMultiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        continueAppFlow()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isTaskRoot) {
            finish()
            return
        }
        enableEdgeToEdge()

        setContent {
            AudiocinematecaTheme {
                SplashScreenContent(durationMs = splashDurationState.value)
            }
        }

        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestMultiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            continueAppFlow()
        }
    }

    companion object {
        private var bootMediaPlayer: MediaPlayer? = null
        private const val BOOT_ANIMATION_LAST_TIME_KEY = "last_boot_animation_timestamp"
        private const val BOOT_ANIMATION_FREQUENCY_KEY = "boot_animation_frequency"
    }

    private fun playBootSoundAndVibrate(): Long {
        var duration = 2500L
        try {
            val appCtx = applicationContext
            bootMediaPlayer?.release()

            val mediaPlayer = MediaPlayer()
            bootMediaPlayer = mediaPlayer

            val afd = appCtx.assets.openFd("sonidos/efectos/Boot Animation.mp3")
            mediaPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            mediaPlayer.prepare()
            afd.close()

            val mpDuration = mediaPlayer.duration.toLong()
            if (mpDuration > 0) {
                duration = mpDuration
            }

            mediaPlayer.start()
            triggerVibration(1500L)

            mediaPlayer.setOnCompletionListener { mp ->
                try {
                    mp.release()
                    if (bootMediaPlayer == mp) {
                        bootMediaPlayer = null
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return duration
    }

    private fun triggerVibration(durationMs: Long) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = longArrayOf(
                        0, 30, 20, 30, 20, 30, 20, 30, 20, 30, 20, 30, 20, 30, 20, 30, 20, 30, 20, 30, 20, 30, 20, 40
                    )
                    val amplitudes = intArrayOf(
                        0, 180, 0, 190, 0, 200, 0, 210, 0, 220, 0, 230, 0, 240, 0, 250, 0, 255, 0, 255, 0, 255, 0, 255
                    )
                    if (vibrator.hasAmplitudeControl()) {
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                    } else {
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val pattern = longArrayOf(
                        0, 30, 20, 30, 20, 30, 20, 30, 20, 30, 20, 30, 20, 30, 20, 30, 20, 30, 20, 30
                    )
                    vibrator.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStop() {
        super.onStop()
        cleanupMediaPlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupMediaPlayer()
    }

    private fun cleanupMediaPlayer() {
        try {
            bootMediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            bootMediaPlayer = null
        }
    }

    private fun continueAppFlow() {
        if (isFlowStarted) return
        isFlowStarted = true

        val currentTime = System.currentTimeMillis()
        val frequency = sharedPreferencesManager.getString(BOOT_ANIMATION_FREQUENCY_KEY, "always") ?: "always"
        val lastBootTime = sharedPreferencesManager.getLong(BOOT_ANIMATION_LAST_TIME_KEY, 0L)

        val intervalMs = when (frequency) {
            "6_hours" -> 6 * 60 * 60 * 1000L
            "12_hours" -> 12 * 60 * 60 * 1000L
            "24_hours" -> 24 * 60 * 60 * 1000L
            else -> 0L // "always" (siempre)
        }

        val shouldPlayBoot = (currentTime - lastBootTime) >= intervalMs

        if (shouldPlayBoot) {
            sharedPreferencesManager.saveLong(BOOT_ANIMATION_LAST_TIME_KEY, currentTime)
            val audioDuration = playBootSoundAndVibrate()

            lifecycleScope.launch {
                delay(audioDuration.coerceAtLeast(1500L))
                navigateToNextScreen()
            }
        } else {
            // Si el intervalo de tiempo configurado aún no ha transcurrido, saltear animación y navegar directo
            navigateToNextScreen()
        }
    }

    private fun navigateToNextScreen() {
        if (isFinishing || isDestroyed) return
        cleanupMediaPlayer()
        lifecycleScope.launch {
            try {
                if (loginUseCase.isUserLoggedIn()) {
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                } else {
                    startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                try {
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                } catch (e2: Exception) {
                    e2.printStackTrace()
                }
            } finally {
                finish()
            }
        }
    }
}

@Composable
fun SplashScreenContent(durationMs: Long) {
    val scale = remember { Animatable(0.6f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(
                durationMillis = 1500,
                easing = FastOutSlowInEasing
            )
        )
    }

    LaunchedEffect(Unit) {
        alpha.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(
                durationMillis = 1000
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF020617)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .scale(scale.value)
                .alpha(alpha.value)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "Animación",
                modifier = Modifier.size(120.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Audiocinemateca",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Cargando la pantalla de inicio",
                color = Color(0xFF94A3B8),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}



