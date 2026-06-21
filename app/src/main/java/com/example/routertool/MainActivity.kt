package com.example.routertool

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val bg = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var statusText: TextView
    private lateinit var statusDot: View
    private lateinit var passwordForm: MaterialCardView
    private var showPassword = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.tvStatus)
        statusDot = findViewById(R.id.dotStatus)
        passwordForm = findViewById(R.id.cardPasswordForm)

        findViewById<MaterialCardView>(R.id.btnRestart).setOnClickListener { confirmRestart() }
        findViewById<MaterialCardView>(R.id.btnStatus).setOnClickListener { checkConnection() }
        findViewById<MaterialCardView>(R.id.btnInfo).setOnClickListener { openInfo() }
        findViewById<MaterialCardView>(R.id.btnDevices).setOnClickListener { showDevices() }
        findViewById<MaterialCardView>(R.id.btnPassword).setOnClickListener { togglePassword() }
        findViewById<MaterialButton>(R.id.btnSavePassword).setOnClickListener { changePassword() }
    }

    // ─── RESTART ────────────────────────────────────────────────

    private fun confirmRestart() {
        AlertDialog.Builder(this)
            .setTitle("Restart Router")
            .setMessage("Restart the router?\nConnection will drop for ~30 sec.")
            .setPositiveButton("Yes") { _, _ -> doRestart() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun doRestart() {
        status("Sending restart...", "#E65100", true)
        bg.execute {
            try {
                post("http://192.168.0.1/goform/SysToolReboot", "GO=system_reboot.asp")
                ui.post {
                    status("Restart sent! Wait ~30s", "#2E7D32", true)
                    toast("Restart sent!")
                }
            } catch (_: Exception) {
                ui.post { status("Router offline (restart OK)", "#2E7D32", true) }
            }
        }
    }

    // ─── CHECK CONNECTION ─────────────────────────────────────

    private fun checkConnection() {
        status("Checking...", "#E65100", true)
        bg.execute {
            try {
                val url = URL("http://192.168.0.1/")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.instanceFollowRedirects = false
                val code = conn.responseCode
                conn.disconnect()
                ui.post {
                    if (code == 302 || code == 200) {
                        status("Router responds (HTTP $code)", "#2E7D32", true)
                        toast("Router is alive")
                    } else {
                        status("Unexpected: HTTP $code", "#E65100", true)
                    }
                }
            } catch (e: Exception) {
                ui.post {
                    status("Router not responding", "#C62828", false)
                    toast("Failed: ${e.message}")
                }
            }
        }
    }

    // ─── INFO ─────────────────────────────────────────────────

    private fun openInfo() {
        startActivity(android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse("http://192.168.0.1/system.htm")
        ))
    }

    // ─── DEVICES ──────────────────────────────────────────────

    private fun showDevices() {
        status("Scanning devices...", "#E65100", true)
        bg.execute {
            try {
                val html = fetch("http://192.168.0.1/ClientStatus.htm")
                val list = parseDevices(html)
                ui.post { showDeviceResult(list) }
            } catch (_: Exception) {
                try {
                    val html = fetch("http://192.168.0.1/wlan_clients.htm")
                    val list = parseDevices(html)
                    ui.post { showDeviceResult(list) }
                } catch (_: Exception) {
                    ui.post {
                        status("Cannot fetch devices", "#C62828", false)
                        toast("Router unreachable")
                    }
                }
            }
        }
    }

    private fun parseDevices(html: String): List<String> {
        val clean = html.replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ")
        val result = mutableListOf<String>()

        // Match IP + MAC in same line
        val ip = Regex("""(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})""")
        val mac = Regex("""([0-9A-Fa-f]{2}[:-][0-9A-Fa-f]{2}[:-][0-9A-Fa-f]{2}[:-][0-9A-Fa-f]{2}[:-][0-9A-Fa-f]{2}[:-][0-9A-Fa-f]{2})""")
        
        for (line in clean.split("\n")) {
            val trimmed = line.trim()
            if (trimmed.length < 10) continue
            
            val ipMatch = ip.find(trimmed)
            val macMatch = mac.find(trimmed)
            
            if (ipMatch != null && macMatch != null) {
                val ipStr = ipMatch.value
                val macStr = macMatch.value
                // Extract hostname (anything between IP and MAC or after MAC)
                val remainder = trimmed
                    .replace(ipStr, "")
                    .replace(macStr, "")
                    .trim()
                    .split(Regex("\\s+"))
                    .filter { it.length > 1 && !it.startsWith("http") && !it.contains("/") }
                val name = if (remainder.isNotEmpty()) remainder.first() else "Unknown"
                result.add("$ipStr  $macStr  $name")
            }
        }

        if (result.isEmpty()) {
            // Fallback: just list unique IPs
            for (m in ip.findAll(clean)) {
                val candidate = m.value
                if (candidate != "192.168.0.1" && candidate != "0.0.0.0" && !result.any { it.startsWith(candidate) }) {
                    result.add("$candidate  -  Unknown")
                }
            }
        }

        return result.take(20)
    }

    private fun showDeviceResult(list: List<String>) {
        if (list.isEmpty()) {
            status("No devices found", "#E65100", true)
            toast("No devices detected")
            return
        }
        status("${list.size} device(s) connected", "#2E7D32", true)
        val items = list.mapIndexed { i, d -> "${i + 1}. $d" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Connected Devices (${list.size})")
            .setItems(items) { _, _ -> }
            .setPositiveButton("Close", null)
            .show()
    }

    // ─── PASSWORD ─────────────────────────────────────────────

    private fun togglePassword() {
        showPassword = !showPassword
        passwordForm.visibility = if (showPassword) View.VISIBLE else View.GONE
    }

    private fun changePassword() {
        val p1 = findViewById<TextInputEditText>(R.id.inputPassword).text.toString()
        val p2 = findViewById<TextInputEditText>(R.id.inputPassword2).text.toString()

        if (p1.length < 3) { toast("Password min 3 characters"); return }
        if (p1 != p2) { toast("Passwords do not match!"); return }

        status("Saving...", "#E65100", true)
        bg.execute {
            try {
                val code = post("http://192.168.0.1/goform/SysToolChangePwd", "newpwd=$p1&pwd2=$p2")
                ui.post {
                    status("Password changed (HTTP $code)", "#2E7D32", true)
                    toast("Password changed!")
                }
            } catch (e: Exception) {
                ui.post {
                    status("Failed: ${e.message}", "#C62828", false)
                    toast("Failed: ${e.message}")
                }
            }
        }
    }

    // ─── HTTP HELPERS ─────────────────────────────────────────

    private fun fetch(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        return conn.inputStream.bufferedReader().use { it.readText() }.also { conn.disconnect() }
    }

    private fun post(url: String, body: String): Int {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        val w = OutputStreamWriter(conn.outputStream)
        w.write(body)
        w.flush()
        w.close()
        val code = conn.responseCode
        conn.disconnect()
        return code
    }

    // ─── UI HELPERS ───────────────────────────────────────────

    private fun status(msg: String, hex: String, ok: Boolean) {
        statusText.text = msg
        statusText.setTextColor(Color.parseColor(hex))
        statusDot.setBackgroundColor(Color.parseColor(if (ok) "#2E7D32" else "#C62828"))
    }

    private fun toast(msg: String) {
        Toast.makeText(this, "  $msg  ", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        bg.shutdown()
    }
}
