package com.johang.audiocinemateca

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.johang.audiocinemateca.domain.usecase.LoginUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    @Inject
    lateinit var loginUseCase: LoginUseCase

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permiso concedido, continuar con la lógica de inicio
            continueAppFlow()
        } else {
            // Permiso denegado, puedes mostrar un mensaje o continuar sin notificaciones
            continueAppFlow()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                // Permiso ya concedido, continuar con la lógica de inicio
                continueAppFlow()
            } else {
                // Solicitar permiso
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            // Para versiones anteriores a Android 13, el permiso se concede en el manifiesto
            continueAppFlow()
        }
    }

    private fun continueAppFlow() {
        lifecycleScope.launch {
            if (loginUseCase.isUserLoggedIn()) {
                // Usuario logueado, ir a MainActivity
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            } else {
                // Usuario no logueado, ir a LoginActivity
                startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
            }
            finish() // Cerrar SplashActivity
        }
    }
}

