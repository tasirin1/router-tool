package com.example.routertool

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val speedHandler = Handler(Looper.getMainLooper())

    // UI
    private lateinit var tvStatus: TextView
    private lateinit var tvSpeedDown: TextView
    private lateinit var tvSpeedUp: TextView
    private lateinit var barDown: View
    private lateinit var barUp: View
    private lateinit var dotStatus: View
    private lateinit var tvSource: TextView
    private lateinit var cardPasswordForm: MaterialCardView
    private var isPasswordVisible = false

    // Speed monitor
    private var isMonitoring = false
    private var lastDownBytes = 0L
    private var lastUpBytes = 0L
    private var lastPollTime = 0L
    private var maxSpeed = 10.0 * 1_000_000  // 10 MB/s default for bar scale

    // Known Tenda endpoints for bandwidth data
    private val bandwidthEndpoints = listOf(
        "http://192.168.0.1/goform/getStatus",
        "http://192.168.0.1/goform/status",
        "http://192.168.0.1/goform/asp?cmd=status",
        "http://192.168.0.1/status.htm",
        "http://192.168.0.1/index.htm"
    )
    private var workingEndpoint: String? = null
    private var useRouterData = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind UI
        tvStatus = findViewById(R.id.tvStatus)
        tvSpeedDown = findViewById(R.id.tvSpeedDown)
        tvSpeedUp = findViewById(R.id.tvSpeedUp)
        barDown = findViewById(R.id.barDown)
        barUp = findViewById(R.id.barUp)
        dotStatus = findViewById(R.id.dotStatus)
        tvSource = findViewById(R.id.tvLabelSource)
        cardPasswordForm = findViewById(R.id.cardPasswordForm)

        // Feature clicks
        findViewById<LinearLayout>(R.id.btnRestart).setOnClickListener { confirmRestart() }
        findViewById<LinearLayout>(R.id.btnStatus).setOnClickListener { cekKoneksi() }
        findViewById<LinearLayout>(R.id.btnWifi).setOnClickListener { bukaHalaman("http://192.168.0.1/index.htm") }
        findViewById<LinearLayout>(R.id.btnInfo).setOnClickListener { bukaHalaman("http://192.168.0.1/system.htm") }
        findViewById<LinearLayout>(R.id.btnSpeed).setOnClickListener { bukaHalaman("http://192.168.0.1/net-control.htm") }
        findViewById<LinearLayout>(R.id.btnGantiPwd).setOnClickListener { togglePasswordForm() }
        findViewById<MaterialButton>(R.id.btnPassword).setOnClickListener { gantiPassword() }

        // Start polling router for bandwidth
        discoverEndpoint()
    }

    // ═══════════════════════════════════════════════════════════════
    //  BANDWIDTH MONITOR — polls router for real WAN traffic
    // ═══════════════════════════════════════════════════════════════

    private fun discoverEndpoint() {
        executor.execute {
            for (ep in bandwidthEndpoints) {
                try {
                    val url = URL(ep)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    val code = conn.responseCode
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()

                    if (code == 200 && parseBandwidth(body) != null) {
                        workingEndpoint = ep
                        useRouterData = true
                        handler.post {
                            tvSource.text = "ROUTER"
                            tvSource.setTextColor(Color.parseColor("#81D4FA"))
                            startRouterPolling()
                        }
                        return@execute
                    }
                } catch (_: Exception) { }
            }
            // Fallback: monitor just the phone's data via TrafficStats
            useRouterData = false
            handler.post {
                tvSource.text = "PHONE"
                tvSource.setTextColor(Color.parseColor("#A5D6A7"))
                startPhonePolling()
            }
        }
    }

    private fun startRouterPolling() {
        isMonitoring = true
        speedHandler.post(object : Runnable {
            override fun run() {
                if (!isMonitoring) return
                pollRouterBandwidth()
                speedHandler.postDelayed(this, 2000) // poll every 2s
            }
        })
    }

    private fun pollRouterBandwidth() {
        val ep = workingEndpoint ?: return
        executor.execute {
            try {
                val url = URL(ep)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val parsed = parseBandwidth(body)
                if (parsed != null) {
                    val (down, up) = parsed
                    handler.post { updateSpeedDisplay(down, up) }
                }
            } catch (_: Exception) { }
        }
    }

    /** Parse bandwidth (down bytes/sec, up bytes/sec) from HTTP response */
    private fun parseBandwidth(body: String): Pair<Double, Double>? {
        // Try JSON format: {"wanDownRate":"1234","wanUpRate":"567"}
        try {
            val json = org.json.JSONObject(body)
            if (json.has("wanDownRate")) {
                val down = json.optString("wanDownRate", "0").toDoubleOrNull() ?: 0.0
                val up = json.optString("wanUpRate", "0").toDoubleOrNull() ?: 0.0
                return Pair(down, up)
            }
        } catch (_: Exception) { }

        // Try JavaScript var: wanDownRate=1234; wanUpRate=567
        val downRegex = Regex("""wanDownRate\s*[=:]\s*"?(\d+(?:\.\d+))?"?""", RegexOption.IGNORE_CASE)
        val upRegex = Regex("""wanUpRate\s*[=:]\s*"?(\d+(?:\.\d+))?"?""", RegexOption.IGNORE_CASE)

        val downMatch = downRegex.find(body)
        val upMatch = upRegex.find(body)
        if (downMatch != null || upMatch != null) {
            val down = downMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            val up = upMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            return Pair(down, up)
        }

        // Try HTML table with bandwidth values
        val tbDown = Regex("""(?:Download|WAN Rx|rxBytes|wanDown)[^<]*?(\d+(?:\.\d+)?)\s*(?:Mbps|Kbps|bps)""", RegexOption.IGNORE_CASE)
        val tbUp = Regex("""(?:Upload|WAN Tx|txBytes|wanUp)[^<]*?(\d+(?:\.\d+)?)\s*(?:Mbps|Kbps|bps)""", RegexOption.IGNORE_CASE)

        val mDown = tbDown.find(body)
        val mUp = tbUp.find(body)
        if (mDown != null || mUp != null) {
            val down = mDown?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            val up = mUp?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            return Pair(down, up)
        }

        return null
    }

    /** Fallback: use Android TrafficStats (phone-only data) */
    private fun startPhonePolling() {
        isMonitoring = true
        lastDownBytes = android.net.TrafficStats.getTotalRxBytes()
        lastUpBytes = android.net.TrafficStats.getTotalTxBytes()
        lastPollTime = System.currentTimeMillis()

        speedHandler.post(object : Runnable {
            override fun run() {
                if (!isMonitoring) return
                val now = System.currentTimeMillis()
                val curRx = android.net.TrafficStats.getTotalRxBytes()
                val curTx = android.net.TrafficStats.getTotalTxBytes()
                val elapsed = (now - lastPollTime) / 1000.0

                if (elapsed > 0) {
                    val down = (curRx - lastRxBytesSafe(curRx)).coerceAtLeast(0) / elapsed
                    val up = (curTx - lastTxBytesSafe(curTx)).coerceAtLeast(0) / elapsed
                    handler.post { updateSpeedDisplay(down, up) }
                }

                lastDownBytes = curRx
                lastUpBytes = curTx
                lastPollTime = now
                speedHandler.postDelayed(this, 1000)
            }
        })
    }

    private fun lastRxBytesSafe(cur: Long) = if (cur >= lastDownBytes) lastDownBytes else cur
    private fun lastTxBytesSafe(cur: Long) = if (cur >= lastUpBytes) lastUpBytes else cur

    private fun updateSpeedDisplay(downBps: Double, upBps: Double) {
        tvSpeedDown.text = formatSpeed(downBps)
        tvSpeedUp.text = formatSpeed(upBps)

        // Auto-scale bars based on peak speed
        if (downBps > maxSpeed) maxSpeed = downBps * 1.2
        if (upBps > maxSpeed) maxSpeed = upBps * 1.2
        if (maxSpeed < 1_000_000.0) maxSpeed = 1_000_000.0

        setBarWidth(barDown, downBps)
        setBarWidth(barUp, upBps)
    }

    private fun setBarWidth(bar: View, bps: Double) {
        val parent = bar.parent as? View ?: return
        val parentW = parent.width
        if (parentW <= 0) return
        val pct = (bps / maxSpeed).coerceIn(0.0, 1.0)
        bar.layoutParams?.width = (parentW * pct).toInt()
        bar.requestLayout()
    }

    private fun formatSpeed(bps: Double): String {
        return when {
            bps >= 1_000_000 -> String.format("%.1f MB/s", bps / 1_000_000)
            bps >= 1_000 -> String.format("%.0f KB/s", bps / 1_000)
            bps >= 1 -> String.format("%.0f B/s", bps)
            else -> "0 KB/s"
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  RESTART ROUTER
    // ═══════════════════════════════════════════════════════════════

    private fun confirmRestart() {
        AlertDialog.Builder(this)
            .setTitle("🔄 Restart Router")
            .setMessage("Yakin mau restart router?\nKoneksi akan putus ~30 detik.")
            .setPositiveButton("Ya, Restart") { _, _ -> restartRouter() }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun restartRouter() {
        setStatus("Mengirim perintah restart...", "#FF9800", true)
        executor.execute {
            try {
                val url = URL("http://192.168.0.1/goform/SysToolReboot")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val writer = OutputStreamWriter(conn.outputStream)
                writer.write("GO=system_reboot.asp")
                writer.flush()
                writer.close()
                conn.responseCode
                conn.disconnect()
                handler.post {
                    setStatus("Perintah restart terkirim! Tunggu ~30 detik...", "#4CAF50", true)
                    Snackbar.make(findViewById(android.R.id.content), "✅ Restart dikirim!", Snackbar.LENGTH_LONG).show()
                }
            } catch (_: Exception) {
                handler.post { setStatus("Restart berhasil (router offline)", "#4CAF50", true) }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  CEK KONEKSI
    // ═══════════════════════════════════════════════════════════════

    private fun cekKoneksi() {
        setStatus("Memeriksa koneksi...", "#FF9800", true)
        executor.execute {
            try {
                val url = URL("http://192.168.0.1/")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.instanceFollowRedirects = false
                val code = conn.responseCode
                conn.disconnect()

                handler.post {
                    if (code == 302 || code == 200) {
                        setStatus("Router merespon (HTTP $code)", "#4CAF50", true)
                        Toast.makeText(this, "Router hidup ✅", Toast.LENGTH_SHORT).show()
                    } else {
                        setStatus("Respon aneh: HTTP $code", "#FF9800", true)
                    }
                }
            } catch (e: Exception) {
                handler.post {
                    setStatus("Router tidak merespon", "#F44336", false)
                    Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  GANTI PASSWORD
    // ═══════════════════════════════════════════════════════════════

    private fun togglePasswordForm() {
        isPasswordVisible = !isPasswordVisible
        cardPasswordForm.visibility = if (isPasswordVisible) View.VISIBLE else View.GONE
    }

    private fun gantiPassword() {
        val pwd1 = findViewById<TextInputEditText>(R.id.inputPassword).text.toString()
        val pwd2 = findViewById<TextInputEditText>(R.id.inputPassword2).text.toString()

        if (pwd1.length < 3) {
            Toast.makeText(this, "Password minimal 3 karakter", Toast.LENGTH_SHORT).show()
            return
        }
        if (pwd1 != pwd2) {
            Toast.makeText(this, "Password tidak cocok!", Toast.LENGTH_SHORT).show()
            return
        }

        setStatus("Mengganti password...", "#FF9800", true)
        executor.execute {
            try {
                val url = URL("http://192.168.0.1/goform/SysToolChangePwd")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val writer = OutputStreamWriter(conn.outputStream)
                writer.write("newpwd=$pwd1&pwd2=$pwd2")
                writer.flush()
                writer.close()
                val code = conn.responseCode
                conn.disconnect()

                handler.post {
                    setStatus("Password berhasil diganti! (HTTP $code)", "#4CAF50", true)
                    Snackbar.make(findViewById(android.R.id.content), "✅ Password diganti!", Snackbar.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                handler.post {
                    setStatus("Gagal: ${e.message}", "#F44336", false)
                    Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  WEBVIEW HELPER
    // ═══════════════════════════════════════════════════════════════

    private fun bukaHalaman(url: String) {
        val intent = Intent(this, WebActivity::class.java)
        intent.putExtra("url", url)
        startActivity(intent)
    }

    // ═══════════════════════════════════════════════════════════════
    //  UI HELPERS
    // ═══════════════════════════════════════════════════════════════

    private fun setStatus(msg: String, colorHex: String, ok: Boolean) {
        tvStatus.text = msg
        tvStatus.setTextColor(Color.parseColor(colorHex))
        dotStatus.setBackgroundResource(if (ok) android.R.drawable.presence_online else android.R.drawable.presence_offline)
        // Tinted dot
        dotStatus.setBackgroundColor(Color.parseColor(if (ok) "#4CAF50" else "#F44336"))
    }

    // ═══════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    override fun onPause() {
        super.onPause()
        isMonitoring = false
        speedHandler.removeCallbacksAndMessages(null)
    }

    override fun onResume() {
        super.onResume()
        if (!isMonitoring && ::tvSource.isInitialized) {
            if (useRouterData) startRouterPolling() else startPhonePolling()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isMonitoring = false
        speedHandler.removeCallbacksAndMessages(null)
        executor.shutdown()
    }
}
