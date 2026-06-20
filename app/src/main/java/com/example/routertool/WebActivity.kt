package com.example.routertool

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class WebActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private var cursorMode = false
    private var cursorView: TextView? = null
    private var cursorX = 0f
    private var cursorY = 0f
    private val stepSize = 14f

    // JS untuk cursor overlay di dalam WebView
    private val cursorJs = """
        (function(){
            if (document.getElementById('_rt_cursor')) return;
            var d = document.createElement('div');
            d.id = '_rt_cursor';
            d.style.cssText = 'position:fixed;z-index:99999;pointer-events:none;'+
                'width:22px;height:22px;border:2.5px solid #FF5722;border-radius:50%;'+
                'background:rgba(255,87,34,0.15);transform:translate(-50%,-50%);'+
                'transition:none;box-shadow:0 0 10px rgba(255,87,34,0.4);';
            // Crosshair
            var h = document.createElement('div');
            h.style.cssText = 'position:absolute;top:50%;left:3px;right:3px;height:1px;background:#FF5722;';
            var v = document.createElement('div');
            v.style.cssText = 'position:absolute;left:50%;top:3px;bottom:3px;width:1px;background:#FF5722;';
            d.appendChild(h); d.appendChild(v);
            document.body.appendChild(d);
            return true;
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

        if (cursorMode) {
            showCursor()
            Toast.makeText(this, "D-pad: gerak · OK: klik", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            displayZoomControls = false
        }

        webView.isFocusable = false
        webView.isFocusableInTouchMode = false
        webView.settings.builtInZoomControls = false

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url?.contains("/logout") == true) return true
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                supportActionBar?.title = view?.title ?: "Router Tool"
                injekCursor()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }
        }
    }

    private fun injekCursor() {
        webView.post {
            try {
                webView.evaluateJavascript(cursorJs, null)
            } catch (_: Exception) { }
        }
    }

    private fun showCursor() {
        val display = resources.displayMetrics
        cursorX = display.widthPixels / 2f
        cursorY = display.heightPixels / 2f

        cursorView = TextView(this).apply {
            text = "●"
            textSize = 28f
            setTextColor(0xFFFF5722.toInt())
            isFocusable = false
            isClickable = false
            isEnabled = false
            includeFontPadding = false
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val root = findViewById<FrameLayout>(android.R.id.content)
        cursorView?.let {
            root.addView(it)
            posCursor()
        }
    }

    private fun posCursor() {
        cursorView?.let {
            val p = it.layoutParams as FrameLayout.LayoutParams
            p.leftMargin = cursorX.toInt() - 14
            p.topMargin = cursorY.toInt() - 28
            it.layoutParams = p
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (cursorMode && event != null && event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP    -> { geser(0f, -stepSize); return true }
                KeyEvent.KEYCODE_DPAD_DOWN  -> { geser(0f, stepSize); return true }
                KeyEvent.KEYCODE_DPAD_LEFT  -> { geser(-stepSize, 0f); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { geser(stepSize, 0f); return true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { klik(); return true }
                KeyEvent.KEYCODE_BACK -> { finish(); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!cursorMode) return super.onKeyDown(keyCode, event)
        return true // consumed by dispatchKeyEvent
    }

    private fun geser(dx: Float, dy: Float) {
        val display = resources.displayMetrics
        cursorX = (cursorX + dx).coerceIn(0f, display.widthPixels.toFloat())
        cursorY = (cursorY + dy).coerceIn(0f, display.heightPixels.toFloat())
        posCursor()
        // Gerakin cursor JS juga
        webView.evaluateJavascript(
            "(function(){var c=document.getElementById('_rt_cursor');if(c){c.style.left='${cursorX}px';c.style.top='${cursorY}px';}})()",
            null
        )
    }

    private fun klik() {
        // Approach 1: JavaScript click di WebView
        webView.evaluateJavascript("""
            (function(){
                var c=document.getElementById('_rt_cursor');
                if(!c)return;
                var r=c.getBoundingClientRect();
                var x=r.left+r.width/2, y=r.top+r.height/2;
                var el=document.elementFromPoint(x,y);
                if(el){
                    try{el.click();}catch(e){}
                    ['mousedown','mouseup','click'].forEach(function(t){
                        try{el.dispatchEvent(new MouseEvent(t,{bubbles:true,cancelable:true,clientX:x,clientY:y,button:0}));}catch(e){}
                    });
                    try{el.focus();}catch(e){}
                }
            })()
        """.trimIndent(), null)

        // Approach 2: Android MotionEvent (tap real di WebView)
        try {
            val loc = IntArray(2)
            webView.getLocationOnScreen(loc)
            val webX = cursorX - loc[0]
            val webY = cursorY - loc[1]
            val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, webX, webY, 0)
            val up = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, webX, webY, 0)
            webView.dispatchTouchEvent(down)
            webView.dispatchTouchEvent(up)
            down.recycle()
            up.recycle()
        } catch (_: Exception) { }
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
