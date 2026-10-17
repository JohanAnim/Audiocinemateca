package com.johang.audiocinemateca.presentation.webview

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.johang.audiocinemateca.data.AuthCatalogRepository
import com.johang.audiocinemateca.presentation.theme.AudiocinematecaTheme
import com.johang.audiocinemateca.util.audiocinematecaAccessibility
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WebViewFragment : Fragment() {

    @Inject
    lateinit var authCatalogRepository: AuthCatalogRepository

    private val args: WebViewFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AudiocinematecaTheme {
                    WebViewScreen(
                        url = args.url,
                        authCatalogRepository = authCatalogRepository,
                        onBack = { findNavController().navigateUp() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewScreen(
    url: String,
    authCatalogRepository: AuthCatalogRepository,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Navegador",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.audiocinematecaAccessibility(
                            label = "Volver atrás",
                            role = Role.Button,
                            onClickAction = onBack
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    coroutineScope.launch {
                        val username = authCatalogRepository.getStoredUsername()
                        val password = authCatalogRepository.getStoredPassword()

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                super.onPageFinished(view, pageUrl)
                                Log.d("WebViewFragment", "Page finished loading: $pageUrl")

                                if (pageUrl != null && pageUrl.contains("/usuario") && username != null && password != null) {
                                    val script = """
                                        (function() {
                                            var nameField = document.getElementById('edit-name');
                                            var passField = document.getElementById('edit-pass');
                                            var loginForm = document.getElementById('user-login');
                                            if (nameField && passField && loginForm) {
                                                nameField.value = '$username';
                                                passField.value = '$password';
                                                loginForm.submit();
                                            }
                                        })();
                                    """.trimIndent()
                                    view?.evaluateJavascript(script, null)
                                }
                            }
                        }

                        loadUrl(url)
                    }
                }
            }
        )
    }
}
