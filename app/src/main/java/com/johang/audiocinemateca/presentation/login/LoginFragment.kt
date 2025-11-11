package com.johang.audiocinemateca.presentation.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.widget.ProgressBar
import android.app.AlertDialog
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.DialogInterface
import com.johang.audiocinemateca.data.AuthCatalogRepository
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.johang.audiocinemateca.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import android.widget.ScrollView
import com.johang.audiocinemateca.utils.AppUtil
import com.johang.audiocinemateca.utils.AppUtil.showKeyboard
import android.text.Editable
import android.text.TextWatcher
import android.util.Log

import android.view.GestureDetector
import android.view.MotionEvent
import android.content.Context
import android.os.Vibrator

@AndroidEntryPoint
class LoginFragment : Fragment() {

    private val viewModel: LoginViewModel by viewModels()

    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var createAccountLink: TextView
    private lateinit var loginFormScrollView: ScrollView
    private lateinit var betaConsentCheckbox: CheckBox

    private var loadingDialog: androidx.appcompat.app.AlertDialog? = null
    private var progressBar: ProgressBar? = null
    private var progressText: TextView? = null

    private lateinit var gestureDetector: GestureDetector

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        usernameEditText = view.findViewById(R.id.username_edit_text)
        usernameEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Lógica de onTextChanged sin logs ni toasts
            }
            override fun afterTextChanged(s: Editable?) {
                usernameEditText.setSelection(s?.length ?: 0)
            }
        })

        passwordEditText = view.findViewById(R.id.password_edit_text)
        passwordEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Lógica de onTextChanged sin logs ni toasts
            }
            override fun afterTextChanged(s: Editable?) {
                passwordEditText.setSelection(s?.length ?: 0)
            }
        })

        loginButton = view.findViewById(R.id.login_button)
        createAccountLink = view.findViewById(R.id.create_account_link)
        betaConsentCheckbox = view.findViewById(R.id.beta_consent_checkbox)

        loginFormScrollView = view.findViewById<ScrollView>(R.id.login_form_scroll_view)

        loginButton.isEnabled = false

        gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val SWIPE_THRESHOLD = 100
                val SWIPE_VELOCITY_THRESHOLD = 100

                val diffY = e2.y - (e1?.y ?: 0f)
                if (diffY > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    // Deslizamiento hacia abajo detectado
                    viewModel.stopCurrentVoice()

                    // Vibrar
                    val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    vibrator.vibrate(100) // Vibrar por 100 milisegundos

                    return true
                }
                return false
            }
        })

        loginFormScrollView.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            false // Devolver false para que el ScrollView siga manejando el scroll
        }

        setupListeners()
        observeViewModel()

        viewModel.playWelcomeSounds()
    }

    private fun setupListeners() {
        usernameEditText.doAfterTextChanged { text ->
            viewModel.onUsernameChange(text.toString())
        }

        passwordEditText.doAfterTextChanged { text ->
            viewModel.onPasswordChange(text.toString())
        }

        loginButton.setOnClickListener { 
            viewModel.onLoginClick()
        }

        createAccountLink.setOnClickListener { 
            AppUtil.openUrlInBrowser(requireContext(), "https://audiocinemateca.com/")
        }

        betaConsentCheckbox.setOnCheckedChangeListener { _, isChecked ->
            loginButton.isEnabled = isChecked
        }
    }

    private fun showLoadingDialog(title: String, message: String) {
        if (loadingDialog == null) {
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_loading_catalog, null)
            progressBar = dialogView.findViewById(R.id.progress_bar)
            progressText = dialogView.findViewById(R.id.progress_text)

            loadingDialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setView(dialogView)
                .setCancelable(false)
                .create()
        }
        loadingDialog?.show()
        progressText?.text = "${progressBar?.progress ?: 0}%"
    }

    private fun hideLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { 
                    viewModel.username.collect { usernameEditText.setText(it) }
                }
                launch { 
                    viewModel.password.collect { passwordEditText.setText(it) }
                }
                launch { 
                    viewModel.errorMessage.collect { message ->
                        message?.let { Snackbar.make(requireView(), it, Snackbar.LENGTH_LONG).show() }
                    }
                }
                launch { 
                    viewModel.banMessage.collect { message ->
                        message?.let { Snackbar.make(requireView(), it, Snackbar.LENGTH_LONG).show() }
                    }
                }
                launch { 
                    viewModel.loginSuccess.collect { success ->
                        if (success) {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("¡Bienvenido!")
                                .setMessage("Inicio de sesión exitoso. A continuación, se descargará el catálogo. Por favor, espere.")
                                .setPositiveButton("Aceptar") { dialog, _ ->
                                    dialog.dismiss()
                                    viewModel.onCatalogDownloadStart()
                                    showLoadingDialog("Descargando Catálogo", "Por favor, espere mientras se descarga el catálogo.")

                                }
                                .setCancelable(false)
                                .show()
                        }
                    }
                }
                launch {
                    viewModel.catalogDownloadState.collect {
                        when (it) {
                            is AuthCatalogRepository.LoadCatalogResultWithProgress.Progress -> {
                                progressBar?.progress = it.percent
                                progressText?.text = "${it.percent}%"
                            }
                            is AuthCatalogRepository.LoadCatalogResultWithProgress.Success -> {
                                hideLoadingDialog()
                                Snackbar.make(requireView(), "Catálogo descargado con éxito.", Snackbar.LENGTH_LONG).show()
                                startActivity(android.content.Intent(requireActivity(), com.johang.audiocinemateca.MainActivity::class.java))
                                requireActivity().finish()
                            }
                            is AuthCatalogRepository.LoadCatalogResultWithProgress.UpdateAvailable -> {
                                hideLoadingDialog()
                                MaterialAlertDialogBuilder(requireContext())
                                    .setTitle("Actualización de Catálogo Disponible")
                                    .setMessage("Hay una nueva versión del catálogo disponible. ¿Desea descargarla ahora?\n\nVersión actual: ${it.localVersion}\nNueva versión: ${it.serverVersion}\n\nEsta actualización es importante para tener el contenido más reciente.")
                                    .setPositiveButton("Descargar") { dialog, _ ->
                                        dialog.dismiss()
                                        viewModel.onCatalogDownloadStart()
                                    }
                                    .setNegativeButton("Cancelar") { dialog, _ ->
                                        dialog.dismiss()
                                        // No action needed if user cancels update download
                                    }
                                    .setCancelable(false)
                                    .show()
                            }
                            is AuthCatalogRepository.LoadCatalogResultWithProgress.Error -> {
                                hideLoadingDialog()
                                Snackbar.make(requireView(), "Error al descargar el catálogo: ${it.message}", Snackbar.LENGTH_LONG).show()
                            }
                            null -> { /* No-op */ }
                        }
                    }
                }
                
                launch { 
                    viewModel.showErrorAlert.collect { errorEvent ->
                        when (errorEvent) {
                            is LoginErrorEvent.InvalidCredentials -> {
                                MaterialAlertDialogBuilder(requireContext())
                                    .setTitle("Fallo en el inicio de sesión")
                                    .setMessage("Por favor, revise su usuario o contraseña e inténtelo de nuevo.")
                                    .setPositiveButton("Aceptar", null)
                                    .show()
                            }
                            is LoginErrorEvent.ServerError -> {
                                MaterialAlertDialogBuilder(requireContext())
                                    .setTitle("Error del Servidor")
                                    .setMessage("Ocurrió un error en el servidor. Por favor, inténtelo más tarde.")
                                    .setPositiveButton("Aceptar", null)
                                    .show()
                            }
                            is LoginErrorEvent.MissingUsername -> {
                                Toast.makeText(requireContext(), "Por favor, ingrese su nombre de usuario.", Toast.LENGTH_SHORT).show()
                                usernameEditText.requestFocus()
                                usernameEditText.showKeyboard()
                            }
                            is LoginErrorEvent.MissingPassword -> {
                                Toast.makeText(requireContext(), "Por favor, ingrese su contraseña.", Toast.LENGTH_SHORT).show()
                                passwordEditText.requestFocus()
                                passwordEditText.showKeyboard()
                            }
                            is LoginErrorEvent.Unknown -> {
                                MaterialAlertDialogBuilder(requireContext())
                                    .setTitle("Error Desconocido")
                                    .setMessage(errorEvent.message)
                                    .setPositiveButton("Aceptar", null)
                                    .show()
                            }
                        }
                    }
                }
                launch {
                    viewModel.isUserLoggedIn.collect { /* No-op, navigation handled by catalog download success */ }
                }
            }
        }
    }
}