package com.example.routertool

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.View
import android.graphics.PixelFormat
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class WebActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private var cursorMode = false
    private var cursorView: ImageView? = null
    private var cursorLabel: TextView? = null
    private var cursorX = 0f
    private var cursorY = 0f
    private val stepSize = 12f  // pixels per d-pad press

    private val cursorJs = """
        (function() {
            if (document.getElementById('_rt_cursor')) return;
            var c = document.createElement('div');
            c.id = '_rt_cursor';
            c.style.cssText = 'position:fixed;z-index:99999;pointer-events:none;'+
                'width:20px;height:20px;border:2px solid #FF5722;border-radius:50%;'+
                'background:rgba(255,87,34,0.2);transform:translate(-50%,-50%);'+
                'box-shadow:0 0 8px rgba(255,87,34,0.5);transition:none;';
            // Crosshair lines
            var h = document.createElement('div');
            h.style.cssText = 'position:absolute;top:50%;left:0;right:0;height:1px;background:#FF5722;';
            var v = document.createElement('div');
            v.style.cssText = 'position:absolute;left:50%;top:0;bottom:0;width:1px;background:#FF5722;';
            c.appendChild(h);
            c.appendChild(v);
            document.body.appendChild(c);
            return true;
        })()
    """.trimIndent()

    private val moveJs = """
        (function(x,y){
            var c = document.getElementById('_rt_cursor');
            if (c) { c.style.left = x+'px'; c.style.top = y+'px'; }
        })(
    """.trimIndent()

    private val clickJs = """
        (function(){
            var c = document.getElementById('_rt_cursor');
            if (!c) return;
            var rect = c.getBoundingClientRect();
            var cx = rect.left + rect.width/2;
            var cy = rect.top + rect.height/2;
            var el = document.elementFromPoint(cx, cy);
            if (el) {
                ['mouseover','mousedown','mouseup','click'].forEach(function(type){
                    el.dispatchEvent(new MouseEvent(type, {bubbles:true,cancelable:true,clientX:cx,clientY:cy,button:0}));
                });
                el.focus();
                // Also try tapping for mobile
                el.dispatchEvent(new TouchEvent('touchstart', {bubbles:true,cancelable:true,clientX:cx,clientY:cy}));
                el.dispatchEvent(new TouchEvent('touchend', {bubbles:true,cancelable:true,clientX:cx,clientY:cy}));
            }
        })()
    """.trimIndent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)

        val url = intent.getStringExtra("url") ?: "http://192.168.0.1/"
        cursorMode = intent.getBooleanExtra("cursorMode", false)

        setupWebView()
        webView.loadUrl(url)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (cursorMode) {
            showCursorOverlay()
            Toast.makeText(this, "🖱️ D-pad: gerak, OK: klik", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onPageReady() {
                injectCursor()
            }
        }, "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (url.contains("/logout")) return true
                view.loadUrl(url)
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                supportActionBar?.title = view?.title ?: "Router Tool"
                if (cursorMode) {
                    injectCursor()
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress == 100) View.GONE else View.VISIBLE
            }
        }
    }

    private fun injectCursor() {
        webView.post {
            try {
                webView.evaluateJavascript(cursorJs, null)
            } catch (_: Exception) { }
        }
    }

    private fun showCursorOverlay() {
        // Position cursor in center of screen
        val displayMetrics = resources.displayMetrics
        cursorX = displayMetrics.widthPixels / 2f
        cursorY = (displayMetrics.heightPixels / 2f).coerceAtMost(800f)

        // Create cursor image view
        val overlay = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_gallery)
            setColorFilter(Color.parseColor("#FF5722"))
            scaleX = 0.8f
            scaleY = 0.8f
            alpha = 0.8f
            layoutParams = FrameLayout.LayoutParams(40, 40)
            isFocusable = false
            isClickable = false
            isEnabled = false
        }
        cursorView = overlay

        // Create label
        val label = TextView(this).apply {
            text = "🖱"
            textSize = 24f
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isFocusable = false
        }
        cursorLabel = label

        // Add to root view
        val root = findViewById<FrameLayout>(android.R.id.content)
        root.addView(label, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).also {
            it.leftMargin = cursorX.toInt()
            it.topMargin = cursorY.toInt()
        })

        updateCursorPosition()
    }

    private fun updateCursorPosition() {
        cursorLabel?.let { label ->
            val params = label.layoutParams as FrameLayout.LayoutParams
            params.leftMargin = cursorX.toInt() - 12
            params.topMargin = cursorY.toInt() - 12
            label.layoutParams = params
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!cursorMode) return super.onKeyDown(keyCode, event)

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> { moveCursor(0f, -stepSize); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { moveCursor(0f, stepSize); true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { moveCursor(-stepSize, 0f); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { moveCursor(stepSize, 0f); true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { cursorClick(); true }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun moveCursor(dx: Float, dy: Float) {
        cursorX = (cursorX + dx).coerceIn(0f, resources.displayMetrics.widthPixels.toFloat())
        cursorY = (cursorY + dy).coerceIn(0f, resources.displayMetrics.heightPixels.toFloat())
        updateCursorPosition()

        // Move JS cursor too
        webView.evaluateJavascript("$moveJs${cursorX.toInt()},${cursorY.toInt()})", null)
    }

    private fun cursorClick() {
        webView.evaluateJavascript(clickJs, null)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}
