package com.example.routertool

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity

class WebActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    // CSS + JS untuk fokus highlight + navigasi D-pad
    private val focusScript = """
        (function(){
            if (document.getElementById('_rt_style')) return;
            var s = document.createElement('style');
            s.id = '_rt_style';
            s.textContent = [
                '*:focus { outline: 3px solid #FF6F00 !important; outline-offset: 2px !important; }',
                'a:focus, button:focus, input:focus, select:focus, textarea:focus, [tabindex]:focus {',
                '  outline: 3px solid #FF6F00 !important;',
                '  box-shadow: 0 0 0 4px rgba(255,111,0,0.3) !important;',
                '  border-radius: 2px !important;',
                '}',
                'input:focus, select:focus, textarea:focus {',
                '  outline: 3px solid #1565C0 !important;',
                '  box-shadow: 0 0 0 4px rgba(21,101,192,0.3) !important;',
                '}'
            ].join('\\n');
            document.head.appendChild(s);
            
            /* Scroll focused element into view on D-pad nav */
            document.addEventListener('focusin', function(e) {
                setTimeout(function() {
                    e.target.scrollIntoView({block:'center', behavior:'smooth'});
                }, 100);
            });
        })()
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_web)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)

        val url = intent.getStringExtra("url") ?: "http://192.168.0.1/"

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
        }

        webView.setBackgroundColor(Color.WHITE)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url?.contains("/logout") == true) return true
                return false
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                injectFocus()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, p: Int) {
                progressBar.progress = p
                progressBar.visibility = if (p >= 100) View.GONE else View.VISIBLE
            }
        }

        webView.loadUrl(url)
    }

    private fun injectFocus() {
        webView.post {
            try {
                webView.evaluateJavascript(focusScript, null)
            } catch (_: Exception) { }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}
