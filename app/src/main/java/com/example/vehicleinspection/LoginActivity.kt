package com.example.vehicleinspection

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private val http = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Auto-login jeśli token już zapisany
        if (ApiConfig.getToken(this) != null) {
            goToMenu(ApiConfig.getDriverName(this))
            return
        }

        setContentView(R.layout.activity_login)
        etUsername = findViewById(R.id.etDriverName)
        etPassword = findViewById(R.id.etPassword)
        btnLogin   = findViewById(R.id.btnLogin)

        btnLogin.setOnClickListener { doLogin() }
    }

    private fun doLogin() {
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString()

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Podaj login i hasło", Toast.LENGTH_SHORT).show()
            return
        }

        btnLogin.isEnabled = false
        btnLogin.text = "Logowanie…"

        val json = JSONObject().put("username", username).put("password", password).toString()
        val body = json.toRequestBody("application/json".toMediaType())
        val req  = Request.Builder()
            .url("${ApiConfig.API_BASE}/api/auth/login")
            .post(body)
            .build()

        Thread {
            try {
                http.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val obj      = JSONObject(text)
                        val token    = obj.getString("token")
                        val name     = obj.getString("driver_name")
                        val driverId = obj.optInt("driver_id", -1)
                        runOnUiThread {
                            ApiConfig.saveLogin(this, token, name, driverId)
                            goToMenu(name)
                        }
                    } else {
                        val msg = runCatching { JSONObject(text).optString("error", "Błąd logowania") }.getOrDefault("Błąd logowania")
                        runOnUiThread { resetBtn(); Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { resetBtn(); Toast.makeText(this, "Brak połączenia: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun resetBtn() {
        btnLogin.isEnabled = true
        btnLogin.text = "Zaloguj się"
    }

    private fun goToMenu(driverName: String) {
        startActivity(Intent(this, MenuActivity::class.java).putExtra("DRIVER_NAME", driverName))
        finish()
    }
}
