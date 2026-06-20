package com.example.routertool

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.view.View
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

    // UI
    private lateinit var tvStatus: TextView
    private lateinit var dotStatus: View
    private lateinit var cardPasswordForm: MaterialCardView
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        dotStatus = findViewById(R.id.dotStatus)
        cardPasswordForm = findViewById(R.id.cardPasswordForm)

        // Feature clicks
        findViewById<MaterialCardView>(R.id.btnRestart).setOnClickListener { confirmRestart() }
        findViewById<MaterialCardView>(R.id.btnStatus).setOnClickListener { cekKoneksi() }
        findViewById<MaterialCardView>(R.id.btnWifi).setOnClickListener { bukaHalaman("http://192.168.0.1/index.htm") }
        findViewById<MaterialCardView>(R.id.btnInfo).setOnClickListener { bukaHalaman("http://192.168.0.1/system.htm") }
        findViewById<MaterialCardView>(R.id.btnSpeed).setOnClickListener { bukaHalaman("http://192.168.0.1/net-control.htm") }
        findViewById<MaterialCardView>(R.id.btnGantiPwd).setOnClickListener { togglePasswordForm() }
        findViewById<MaterialButton>(R.id.btnPassword).setOnClickListener { gantiPassword() }
    }

    // ─── RESTART ────────────────────────────────────────────────

    private fun confirmRestart() {
        AlertDialog.Builder(this)
            .setTitle("🔄 Restart Router")
            .setMessage("Restart the router?\nConnection will drop for ~30 sec.")
            .setPositiveButton("Yes, Restart") { _, _ -> restartRouter() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun restartRouter() {
        setStatus("Sending restart command...", "#FF9800", true)
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
                    setStatus("Restart sent! Wait ~30 seconds...", "#4CAF50", true)
                    Snackbar.make(findViewById(android.R.id.content), "✅ Restart sent!", Snackbar.LENGTH_LONG).show()
                }
            } catch (_: Exception) {
                handler.post { setStatus("Restart done (router offline)", "#4CAF50", true) }
            }
        }
    }

    // ─── CEK KONEKSI ─────────────────────────────────────────

    private fun cekKoneksi() {
        setStatus("Checking connection...", "#FF9800", true)
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
                        setStatus("Router responds (HTTP $code)", "#4CAF50", true)
                        Toast.makeText(this, "Router is alive ✅", Toast.LENGTH_SHORT).show()
                    } else {
                        setStatus("Strange response: HTTP $code", "#FF9800", true)
                    }
                }
            } catch (e: Exception) {
                handler.post {
                    setStatus("Router not responding", "#F44336", false)
                    Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ─── GANTI PASSWORD ──────────────────────────────────────

    private fun togglePasswordForm() {
        isPasswordVisible = !isPasswordVisible
        cardPasswordForm.visibility = if (isPasswordVisible) View.VISIBLE else View.GONE
    }

    private fun gantiPassword() {
        val pwd1 = findViewById<TextInputEditText>(R.id.inputPassword).text.toString()
        val pwd2 = findViewById<TextInputEditText>(R.id.inputPassword2).text.toString()

        if (pwd1.length < 3) {
            Toast.makeText(this, "Password min 3 characters", Toast.LENGTH_SHORT).show()
            return
        }
        if (pwd1 != pwd2) {
            Toast.makeText(this, "Passwords do not match!", Toast.LENGTH_SHORT).show()
            return
        }

        setStatus("Changing password...", "#FF9800", true)
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
                    setStatus("Password changed! (HTTP $code)", "#4CAF50", true)
                    Snackbar.make(findViewById(android.R.id.content), "✅ Password changed!", Snackbar.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                handler.post {
                    setStatus("Failed: ${e.message}", "#F44336", false)
                    Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ─── HELPERS ─────────────────────────────────────────────

    private fun bukaHalaman(url: String) {
        val intent = Intent(this, WebActivity::class.java).apply {
            putExtra("url", url)
            // Enable D-pad cursor mode for Android TV / remote users
            putExtra("cursorMode", true)
        }
        startActivity(intent)
    }

    private fun setStatus(msg: String, colorHex: String, ok: Boolean) {
        tvStatus.text = msg
        tvStatus.setTextColor(Color.parseColor(colorHex))
        dotStatus.setBackgroundColor(Color.parseColor(if (ok) "#4CAF50" else "#F44336"))
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
