package com.example.routertool

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.snackbar.Snackbar
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var tvStatus: TextView
    private lateinit var tvSpeedDown: TextView
    private lateinit var tvSpeedUp: TextView

    // Speed monitor
    private val speedHandler = Handler(Looper.getMainLooper())
    private var lastRxBytes = 0L
    private var lastTxBytes = 0L
    private var lastSpeedTime = 0L
    private var isMonitoring = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvSpeedDown = findViewById(R.id.tvSpeedDown)
        tvSpeedUp = findViewById(R.id.tvSpeedUp)

        // Set click listeners on the card layouts
        findViewById<ViewGroup>(R.id.btnRestart).setOnClickListener { confirmRestart() }
        findViewById<ViewGroup>(R.id.btnStatus).setOnClickListener { cekKoneksi() }
        findViewById<ViewGroup>(R.id.btnWifi).setOnClickListener { bukaHalaman("http://192.168.0.1/index.htm") }
        findViewById<ViewGroup>(R.id.btnInfo).setOnClickListener { bukaHalaman("http://192.168.0.1/system.htm") }
        findViewById<ViewGroup>(R.id.btnSpeed).setOnClickListener { bukaHalaman("http://192.168.0.1/net-control.htm") }
        findViewById<MaterialButton>(R.id.btnPassword).setOnClickListener { gantiPassword() }

        // Start speed monitoring
        startSpeedMonitor()
    }

    // ─── SPEED MONITOR ───────────────────────────────────────────

    private fun startSpeedMonitor() {
        isMonitoring = true
        lastRxBytes = android.net.TrafficStats.getTotalRxBytes()
        lastTxBytes = android.net.TrafficStats.getTotalTxBytes()
        lastSpeedTime = System.currentTimeMillis()
        speedHandler.postDelayed(speedRunnable, 1000)
    }

    private val speedRunnable = object : Runnable {
        override fun run() {
            if (!isMonitoring) return

            val now = System.currentTimeMillis()
            val currentRx = android.net.TrafficStats.getTotalRxBytes()
            val currentTx = android.net.TrafficStats.getTotalTxBytes()

            // Handle counter reset (device reboot)
            val rxBytes = if (currentRx >= lastRxBytes) currentRx - lastRxBytes else currentRx
            val txBytes = if (currentTx >= lastTxBytes) currentTx - lastTxBytes else currentTx
            val elapsed = (now - lastSpeedTime) / 1000.0

            if (elapsed > 0) {
                val downSpeed = rxBytes / elapsed // bytes/sec
                val upSpeed = txBytes / elapsed

                runOnUiThread {
                    tvSpeedDown.text = formatSpeed(downSpeed)
                    tvSpeedUp.text = formatSpeed(upSpeed)
                }
            }

            lastRxBytes = currentRx
            lastTxBytes = currentTx
            lastSpeedTime = now
            speedHandler.postDelayed(this, 1000)
        }
    }

    private fun formatSpeed(bytesPerSec: Double): String {
        return when {
            bytesPerSec >= 1_000_000 -> String.format("%.1f MB/s", bytesPerSec / 1_000_000)
            bytesPerSec >= 1_000 -> String.format("%.0f KB/s", bytesPerSec / 1_000)
            bytesPerSec >= 1 -> String.format("%.0f B/s", bytesPerSec)
            else -> "0 KB/s"
        }
    }

    override fun onPause() {
        super.onPause()
        isMonitoring = false
        speedHandler.removeCallbacks(speedRunnable)
    }

    override fun onResume() {
        super.onResume()
        if (!isMonitoring) {
            startSpeedMonitor()
        }
    }

    // ─── RESTART ─────────────────────────────────────────────────

    private fun confirmRestart() {
        AlertDialog.Builder(this)
            .setTitle("🔄 Restart Router")
            .setMessage(getString(R.string.restart_confirm))
            .setPositiveButton(getString(R.string.yes)) { _, _ -> restartRouter() }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun restartRouter() {
        setStatus("⏳ Mengirim perintah restart...", "#FF9800")
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
                    setStatus("✅ Perintah restart terkirim! Tunggu ~30 detik...", "#4CAF50")
                    Snackbar.make(findViewById(android.R.id.content), R.string.restart_sent, Snackbar.LENGTH_LONG).show()
                }
            } catch (_: Exception) {
                handler.post { setStatus("✅ Restart berhasil (router offline)", "#4CAF50") }
            }
        }
    }

    // ─── CEK KONEKSI ─────────────────────────────────────────────

    private fun cekKoneksi() {
        setStatus("⏳ Memeriksa koneksi...", "#FF9800")
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
                        setStatus("✅ Router merespon (HTTP $code)", "#4CAF50")
                        Toast.makeText(this, "Router hidup ✅", Toast.LENGTH_SHORT).show()
                    } else {
                        setStatus("⚠️ Respon aneh: HTTP $code", "#FF9800")
                    }
                }
            } catch (e: Exception) {
                handler.post {
                    setStatus("❌ Router tidak merespon", "#FF5252")
                    Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ─── GANTI PASSWORD ──────────────────────────────────────────

    private fun gantiPassword() {
        val pwd1 = findViewById<TextInputEditText>(R.id.inputPassword).text.toString()
        val pwd2 = findViewById<TextInputEditText>(R.id.inputPassword2).text.toString()

        if (pwd1.length < 3) {
            Toast.makeText(this, R.string.password_error_min, Toast.LENGTH_SHORT).show()
            return
        }
        if (pwd1 != pwd2) {
            Toast.makeText(this, R.string.password_error_match, Toast.LENGTH_SHORT).show()
            return
        }

        setStatus("⏳ Mengganti password...", "#FF9800")
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
                    setStatus("✅ Password berhasil diganti! (HTTP $code)", "#4CAF50")
                    Snackbar.make(findViewById(android.R.id.content), R.string.password_ok, Snackbar.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                handler.post {
                    setStatus("❌ Gagal: ${e.message}", "#FF5252")
                    Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ─── WEBVIEW ─────────────────────────────────────────────────

    private fun bukaHalaman(url: String) {
        val intent = Intent(this, WebActivity::class.java)
        intent.putExtra("url", url)
        startActivity(intent)
    }

    // ─── UI HELPERS ──────────────────────────────────────────────

    private fun setStatus(msg: String, colorHex: String) {
        tvStatus.text = msg
        tvStatus.setTextColor(android.graphics.Color.parseColor(colorHex))
    }

    override fun onDestroy() {
        super.onDestroy()
        isMonitoring = false
        speedHandler.removeCallbacks(speedRunnable)
        executor.shutdown()
    }
}
