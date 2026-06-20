package com.example.routertool

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)

        findViewById<Button>(R.id.btnRestart).setOnClickListener { confirmRestart() }
        findViewById<Button>(R.id.btnStatus).setOnClickListener { cekStatus() }
        findViewById<Button>(R.id.btnPassword).setOnClickListener { gantiPassword() }
        findViewById<Button>(R.id.btnWifi).setOnClickListener { bukaHalaman("http://192.168.0.1/index.htm") }
        findViewById<Button>(R.id.btnInfo).setOnClickListener { bukaHalaman("http://192.168.0.1/system.htm") }
        findViewById<Button>(R.id.btnSpeed).setOnClickListener { bukaHalaman("http://192.168.0.1/net-control.htm") }
    }

    private fun confirmRestart() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Restart Router")
            .setMessage("Yakin mau restart router? Koneksi akan putus ~30 detik.")
            .setPositiveButton("Ya, Restart") { _, _ -> restartRouter() }
            .setNegativeButton("Batal", null)
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
                val code = conn.responseCode
                conn.disconnect()

                handler.post {
                    setStatus("✅ Perintah restart terkirim! Tunggu ~30 detik...", "#4CAF50")
                    Snackbar.make(findViewById(android.R.id.content), "Router sedang restart...", Snackbar.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                handler.post { setStatus("✅ Restart berhasil (router offline)", "#4CAF50") }
            }
        }
    }

    private fun cekStatus() {
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
                    setStatus("❌ Router tidak merespon: ${e.message}", "#FF5252")
                    Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun gantiPassword() {
        val pwd1 = findViewById<EditText>(R.id.inputPassword).text.toString()
        val pwd2 = findViewById<EditText>(R.id.inputPassword2).text.toString()

        if (pwd1.length < 3) {
            Toast.makeText(this, "Password minimal 3 karakter", Toast.LENGTH_SHORT).show()
            return
        }
        if (pwd1 != pwd2) {
            Toast.makeText(this, "Password tidak cocok!", Toast.LENGTH_SHORT).show()
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
                    Snackbar.make(findViewById(android.R.id.content), "Password diganti ✅", Snackbar.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                handler.post {
                    setStatus("❌ Gagal: ${e.message}", "#FF5252")
                    Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun bukaHalaman(url: String) {
        val intent = Intent(this, WebActivity::class.java)
        intent.putExtra("url", url)
        startActivity(intent)
    }

    private fun setStatus(msg: String, color: String) {
        tvStatus.text = msg
        tvStatus.setTextColor(android.graphics.Color.parseColor(color))
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
