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
import java.net.InetAddress
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
        findViewById<MaterialCardView>(R.id.btnWeb).setOnClickListener { openWeb() }
        findViewById<MaterialCardView>(R.id.btnScan).setOnClickListener { scanNetwork() }
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

    // ─── WEB ADMIN ─────────────────────────────────────────────

    private fun openWeb() {
        startActivity(android.content.Intent(this, WebActivity::class.java).apply {
            putExtra("url", "http://192.168.0.1/")
        })
    }

    // ─── NETWORK SCAN ─────────────────────────────────────────

    private fun scanNetwork() {
        status("Scanning subnet...", "#E65100", true)
        toast("Scanning all IPs, please wait...")
        bg.execute {
            try {
                // Get local IP
                val localIp = InetAddress.getLocalHost()
                val localAddr = localIp.hostAddress ?: "192.168.0.101"
                
                // Determine subnet (assume /24)
                val parts = localAddr.split(".")
                if (parts.size != 4) {
                    ui.post { status("Invalid IP", "#C62828", false) }
                    return@execute
                }
                val prefix = "${parts[0]}.${parts[1]}.${parts[2]}."
                
                val results = mutableListOf<String>()
                val ports = intArrayOf(80, 443, 8080, 8443, 8081, 8000, 3000, 5000, 9090, 8888, 9000, 81, 444, 1883, 22, 23, 8291, 2000, 53)
                val checked = mutableSetOf<String>()
                
                // Scan subnet for open ports + HTTP detection
                for (i in 1..254) {
                    val ip = "$prefix$i"
                    var found = false
                    val ipServices = mutableListOf<String>()
                    
                    for (port in ports) {
                        try {
                            val s = java.net.Socket()
                            s.connect(java.net.InetSocketAddress(ip, port), 300)
                            if (s.isConnected) {
                                s.close()
                                val service = guessService(port)
                                ipServices.add("    Port $port ($service)")
                                found = true
                                
                                // If web port, try to detect HTTP title
                                if (port in intArrayOf(80, 443, 8080, 8443, 8081, 8000, 3000, 5000, 8888, 9000, 81, 444)) {
                                    try {
                                        val testUrl = if (port == 443 || port == 8443) "https://$ip" else "http://$ip"
                                        val conn = java.net.URL(testUrl).openConnection() as java.net.HttpURLConnection
                                        conn.connectTimeout = 1000
                                        conn.readTimeout = 1000
                                        conn.instanceFollowRedirects = false
                                        val code = conn.responseCode
                                        val header = conn.getHeaderField("Server") ?: ""
                                        val loc = conn.getHeaderField("Location") ?: ""
                                        ipServices[ipServices.size - 1] += " (HTTP $code${if (header.isNotEmpty()) " · $header" else ""}${if (loc.isNotEmpty()) " → $loc" else ""})"
                                        conn.disconnect()
                                    } catch (_: Exception) { }
                                }
                            }
                        } catch (_: Exception) { }
                    }
                    
                    if (found && ip !in checked) {
                        checked.add(ip)
                        results.add("$ip")
                        results.addAll(ipServices)
                    }
                }
                
                ui.post { showScanResult(results) }
            } catch (e: Exception) {
                ui.post {
                    status("Scan failed: ${e.message}", "#C62828", false)
                    toast("Failed: ${e.message}")
                }
            }
        }
    }

    private fun guessService(port: Int): String = when (port) {
        80 -> "HTTP"
        443 -> "HTTPS"
        8080 -> "HTTP-Alt"
        8443 -> "HTTPS-Alt"
        8081 -> "HTTP-Alt2"
        8000 -> "HTTP-Alt3"
        3000 -> "Web-Dev"
        5000 -> "Web-Alt"
        8888 -> "Web-Dash"
        9000 -> "Web-Alt4"
        81 -> "HTTP-Alt5"
        444 -> "HTTPS-Alt5"
        22 -> "SSH"
        23 -> "Telnet"
        8291 -> "Winbox"
        2000 -> "Bandwidth"
        53 -> "DNS"
        1883 -> "MQTT"
        else -> "Unknown"
    }

    private fun showScanResult(list: List<String>) {
        if (list.isEmpty()) {
            status("No hosts found", "#E65100", true)
            toast("No active hosts detected")
            return
        }
        
        val hostCount = list.count { !it.startsWith("    ") }
        status("Found $hostCount host(s)", "#2E7D32", true)
        
        val items = list.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Network Scan ($hostCount hosts)")
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
