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
            // Chinese to English translation for Tenda router pages
            var cnMap = {
                '登录':'Login','用户名':'Username','密码':'Password','确定':'OK',
                '取消':'Cancel','应用':'Apply','保存':'Save','重置':'Reset',
                '系统状态':'System Status','网络状态':'Network Status','无线设置':'Wireless',
                '无线网络':'WiFi','安全设置':'Security','高级设置':'Advanced',
                '系统工具':'System Tools','重启路由器':'Reboot Router',
                '恢复出厂设置':'Factory Reset','备份配置':'Backup Config',
                '固件升级':'Firmware Upgrade','修改密码':'Change Password',
                '管理':'Admin','设置':'Settings','状态':'Status',
                '连接':'Connect','断开':'Disconnect','已连接':'Connected',
                'WAN口状态':'WAN Status','LAN口状态':'LAN Status',
                'MAC地址':'MAC Address','IP地址':'IP Address',
                '子网掩码':'Subnet Mask','默认网关':'Default Gateway',
                '主DNS':'Primary DNS','备用DNS':'Secondary DNS',
                '上传':'Upload','下载':'Download','速度':'Speed',
                '流量':'Traffic','总流量':'Total Traffic',
                '当前速率':'Current Speed','最大速率':'Max Speed',
                '信号强度':'Signal Strength','信道':'Channel','模式':'Mode',
                '加密方式':'Encryption','SSID':'SSID','频段':'Band',
                '客户端':'Clients','设备':'Devices','在线':'Online',
                '离线':'Offline','启用':'Enable','禁用':'Disable',
                '开启':'On','关闭':'Off','开':'ON','关':'OFF',
                '首页':'Home','帮助':'Help','关于':'About',
                '小时':'h','分钟':'min','秒':'sec','天':'days',
                '192.168.0.1':'192.168.0.1' // keep IP intact
            };
            var all = document.body.querySelectorAll('*');
            for(var i=0;i<all.length;i++){
                var el=all[i];
                if(el.childNodes.length==1&&el.childNodes[0].nodeType==3){
                    var txt=el.childNodes[0].nodeValue.trim();
                    if(txt&&cnMap[txt]!=null){
                        el.childNodes[0].nodeValue=cnMap[txt];
                    }
                }
                if(el.getAttribute&&el.getAttribute('value')&&cnMap[el.getAttribute('value')]!=null){
                    el.setAttribute('value',cnMap[el.getAttribute('value')]);
                }
                if(el.placeholder&&cnMap[el.placeholder]!=null){
                    el.placeholder=cnMap[el.placeholder];
                }
                if(el.title&&cnMap[el.title]!=null){
                    el.title=cnMap[el.title];
                }
            }
            // Also translate any visible text nodes
            function translateNode(n){
                if(n.nodeType==3){
                    var t=n.nodeValue.trim();
                    if(t&&cnMap[t]!=null)n.nodeValue=cnMap[t];
                }else if(n.nodeType==1&&n.childNodes){
                    for(var j=0;j<n.childNodes.length;j++)translateNode(n.childNodes[j]);
                }
            }
            translateNode(document.body);
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
            Toast.makeText(this, "D-pad: move · OK: click", Toast.LENGTH_SHORT).show()
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
