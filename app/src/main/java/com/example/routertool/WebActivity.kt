package com.example.routertool

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class WebActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    // Injeksi CSS highlight + retry untuk frame
    private val highlightJs = """
        (function(){
            function addHighlight() {
                if (document.getElementById('_rt_focus')) return;
                var s = document.createElement('style');
                s.id = '_rt_focus';
                s.textContent = [
                    '*:focus { outline: 3px solid #FF6F00 !important; outline-offset: 2px !important; }',
                    '*:focus-visible { outline: 3px solid #FF6F00 !important; box-shadow: 0 0 0 4px rgba(255,111,0,0.3) !important; }',
                    'a, button, input, select, textarea, [tabindex], [onclick], td, li, span, div, label',
                    '  { -webkit-tap-highlight-color: rgba(255,111,0,0.3) !important; }',
                    '[tabindex]:focus, [onclick]:focus, td:focus, li:focus, div[onclick]:focus',
                    '  { outline: 3px solid #FF6F00 !important; box-shadow: 0 0 0 4px rgba(255,111,0,0.3) !important; }',
                    'input:focus, select:focus, textarea:focus',
                    '  { outline: 3px solid #1565C0 !important; box-shadow: 0 0 0 4px rgba(21,101,192,0.3) !important; }'
                ].join('\n');
                document.head.appendChild(s);
                /* Auto scroll focused element into view */
                document.addEventListener('focusin', function(e){
                    var el = e.target;
                    setTimeout(function(){
                        try { el.scrollIntoView({block:'center', behavior:'smooth'}); } catch(ex) {}
                    }, 80);
                });
                /* Make all clickable divs/td focusable */
                var all = document.querySelectorAll('[onclick], td, .menuItem, .sidebar, .nav, [class*=menu], [class*=nav]');
                for (var i=0; i<all.length; i++) {
                    if (!all[i].getAttribute('tabindex') && all[i].onclick) {
                        all[i].setAttribute('tabindex', '0');
                    }
                }
            }
            addHighlight();
            /* Also inject into frames */
            try {
                var frames = document.getElementsByTagName('frame');
                for (var i=0; i<frames.length; i++) {
                    try {
                        var fDoc = frames[i].contentDocument || frames[i].contentWindow.document;
                        if (fDoc && !fDoc.getElementById('_rt_focus')) {
                            var fs = fDoc.createElement('style');
                            fs.id = '_rt_focus';
                            fs.textContent = document.getElementById('_rt_focus').textContent;
                            fDoc.head.appendChild(fs);
                        }
                    } catch(ex) {}
                }
            } catch(ex) {}
        })();
    """.trimIndent()

    private val retryJs = """
        (function(){
            var tries = 0;
            function retry() {
                if (document.getElementById('_rt_focus')) return;
                tries++;
                if (tries > 20) return;
                var s = document.createElement('style');
                s.id = '_rt_focus';
                s.textContent = [
                    '*:focus { outline: 3px solid #FF6F00 !important; outline-offset: 2px !important; }',
                    '*:focus-visible { outline: 3px solid #FF6F00 !important; box-shadow: 0 0 0 4px rgba(255,111,0,0.3) !important; }',
                    'a, button, input, select, textarea, [tabindex], [onclick], td, li, span, div, label',
                    '  { -webkit-tap-highlight-color: rgba(255,111,0,0.3) !important; }',
                    '[onclick]:focus, td:focus, li:focus, div[onclick]:focus',
                    '  { outline: 3px solid #FF6F00 !important; box-shadow: 0 0 0 4px rgba(255,111,0,0.3) !important; }',
                    'input:focus, select:focus, textarea:focus',
                    '  { outline: 3px solid #1565C0 !important; box-shadow: 0 0 0 4px rgba(21,101,192,0.3) !important; }'
                ].join('\n');
                document.head.appendChild(s);
                /* make clickable elements focusable */
                var el = document.querySelectorAll('[onclick], td, .menuItem, .sidebar, .nav, [class*=menu]');
                for (var i=0; i<el.length; i++) {
                    if (el[i].onclick && !el[i].getAttribute('tabindex')) el[i].setAttribute('tabindex','0');
                }
            }
            /* retry a few times in case of dynamic content */
            retry();
            setInterval(retry, 1500);
        })();
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
                injectHighlight()
                // Retry highlight after frames load
                webView.postDelayed({ injectHighlight() }, 1000)
                webView.postDelayed({ injectHighlight() }, 3000)
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

    private fun injectHighlight() {
        try {
            webView.evaluateJavascript(highlightJs, null)
            webView.evaluateJavascript(retryJs, null)
        } catch (_: Exception) { }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}
