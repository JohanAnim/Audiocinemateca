package com.johang.audiocinemateca.presentation.login

import android.content.Context
import android.content.Intent
import android.os.Vibrator
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.johang.audiocinemateca.MainActivity
import com.johang.audiocinemateca.LoginActivity
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.AuthCatalogRepository
import com.johang.audiocinemateca.utils.AppUtil

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val username by viewModel.username.collectAsState()
    val password by viewModel.password.collectAsState()
    val loginSuccess by viewModel.loginSuccess.collectAsState()
    val catalogState by viewModel.catalogDownloadState.collectAsState()
    val banMessage by viewModel.banMessage.collectAsState()

    var showPassword by remember { mutableStateOf(false) }
    var showWelcomeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.playWelcomeSounds()
    }

    LaunchedEffect(Unit) {
        viewModel.showErrorAlert.collect { event ->
            when (event) {
                is LoginErrorEvent.InvalidCredentials -> AppUtil.showAlertDialog(context, "Fallo en el inicio de sesión", "Por favor, revise su usuario o contraseña e inténtelo de nuevo.")
                is LoginErrorEvent.ServerError -> AppUtil.showAlertDialog(context, "Error del Servidor", "Ocurrió un error en el servidor. Por favor, inténtelo más tarde.")
                is LoginErrorEvent.MissingUsername -> Toast.makeText(context, "Por favor, ingrese su nombre de usuario.", Toast.LENGTH_SHORT).show()
                is LoginErrorEvent.MissingPassword -> Toast.makeText(context, "Por favor, ingrese su contraseña.", Toast.LENGTH_SHORT).show()
                is LoginErrorEvent.Unknown -> AppUtil.showAlertDialog(context, "Error Desconocido", event.message)
            }
        }
    }

    LaunchedEffect(loginSuccess) {
        if (loginSuccess) showWelcomeDialog = true
    }

    // Gesto Global: Un solo dedo hacia arriba para detener voz y sonidos
    val gestureModifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull()
                    if (change != null && event.changes.size == 1 && event.type == PointerEventType.Move) {
                        val dragAmountY = change.position.y - change.previousPosition.y
                        if (dragAmountY < -20) { 
                            viewModel.stopCurrentVoice()
                            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                vibrator.vibrate(android.os.VibrationEffect.createOneShot(150, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator.vibrate(150)
                            }
                            change.consume()
                        }
                    }
                }
            }
        }

    Scaffold(modifier = gestureModifier) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Icon(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Bienvenido a la Audiocinemateca",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            Text(
                text = "Para empezar, ingrese su usuario y contraseña:",
                fontSize = 15.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // SUBTÍTULO Y CAMPO USUARIO
            Text(
                text = "Nombre de Usuario:",
                modifier = Modifier.align(Alignment.Start).padding(bottom = 4.dp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedTextField(
                value = username,
                onValueChange = { viewModel.onUsernameChange(it) },
                label = { Text("Usuario") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
                    .semantics { 
                        // Habilitar sugerencias de Google Autofill
                        contentType = ContentType.Username
                    },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                )
            )

            // SUBTÍTULO Y CAMPO CONTRASEÑA
            Text(
                text = "Contraseña de Acceso:",
                modifier = Modifier.align(Alignment.Start).padding(bottom = 4.dp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedTextField(
                value = password,
                onValueChange = { viewModel.onPasswordChange(it) },
                label = { Text("Contraseña") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
                    .semantics { 
                        // Habilitar sugerencias de Google Autofill
                        contentType = ContentType.Password
                    },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(
                        onClick = { showPassword = !showPassword },
                        modifier = Modifier.semantics {
                            role = Role.Button
                            contentDescription = if (showPassword) "Ocultar contraseña" else "Mostrar contraseña"
                        }
                    ) {
                        Icon(
                            imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null
                        )
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                singleLine = true
            )

            // AVISO IMPORTANTE: Limpiamos semánticas en el contenedor para evitar doble lectura
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .clearAndSetSemantics {
                        contentDescription = "AVISO IMPORTANTE: Si acabas de crear tu cuenta, debes iniciar sesión por primera vez en audiocinemateca punto com para validar tu perfil antes de poder acceder desde esta aplicación."
                    }
            ) {
                Text(
                    text = "IMPORTANTE: Si acabas de crear tu cuenta, debes iniciar sesión por primera vez en audiocinemateca.com para validar tu perfil antes de poder acceder desde esta aplicación.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(12.dp)
                )
            }

            if (banMessage != null) {
                Text(
                    text = banMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // BOTÓN INICIAR SESIÓN
            Button(
                onClick = { viewModel.onLoginClick() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(bottom = 16.dp)
                    .semantics { role = Role.Button },
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Iniciar Sesión", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }

            // ENLACE CREALA AQUÍ
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "¿Aún no tienes una cuenta? ", color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp)
                Text(
                    text = "Créala aquí",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable { AppUtil.openUrlInBrowser(context, "https://audiocinemateca.com/") }
                        .semantics { 
                            role = Role.Button
                            contentDescription = "Créala aquí, enlace externo"
                        }
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Diálogos de carga y éxito
    if (showWelcomeDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("¡Bienvenido!") },
            text = { Text("Inicio de sesión exitoso. A continuación, se descargará el catálogo. Por favor, espere.") },
            confirmButton = {
                Button(onClick = {
                    showWelcomeDialog = false
                    viewModel.onCatalogDownloadStart()
                }) { Text("Aceptar") }
            }
        )
    }

    LaunchedEffect(catalogState) {
        if (catalogState is AuthCatalogRepository.LoadCatalogResultWithProgress.Success) {
            context.startActivity(Intent(context, MainActivity::class.java))
            (context as? LoginActivity)?.finish()
        }
    }

    when (val state = catalogState) {
        is AuthCatalogRepository.LoadCatalogResultWithProgress.Progress -> {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Descargando Catálogo") },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
                        Text("${state.percent}%", modifier = Modifier.padding(top = 8.dp))
                    }
                },
                confirmButton = { }
            )
        }
        is AuthCatalogRepository.LoadCatalogResultWithProgress.Success -> {
            // Manejado por LaunchedEffect superior
        }
        is AuthCatalogRepository.LoadCatalogResultWithProgress.Error -> {
            AppUtil.showAlertDialog(context, "Error", "Error al descargar el catálogo: ${state.message}")
        }
        else -> {}
    }
}
