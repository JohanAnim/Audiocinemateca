package com.johang.audiocinemateca.presentation.webview

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.AuthCatalogRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WebViewFragment : Fragment() {

    @Inject
    lateinit var authCatalogRepository: AuthCatalogRepository

    private lateinit var webView: WebView
    private val args: WebViewFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_webview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        webView = view.findViewById(R.id.web_view)
        webView.settings.javaScriptEnabled = true

        val urlToLoad = args.url

        lifecycleScope.launch {
            val username = authCatalogRepository.getStoredUsername()
            val password = authCatalogRepository.getStoredPassword()

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d("WebViewFragment", "Page finished loading: $url")

                    // Check if we are on the login page and have credentials
                    if (url != null && url.contains("/usuario") && username != null && password != null) {
                        // Inject JavaScript to fill and submit the form
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
                        """
                        view?.evaluateJavascript(script, null)
                    }
                }
            }

            // Initial load
            webView.loadUrl(urlToLoad)
        }
    }
}
