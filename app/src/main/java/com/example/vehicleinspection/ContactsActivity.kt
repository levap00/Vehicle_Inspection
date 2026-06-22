package com.example.vehicleinspection

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class ContactsActivity : AppCompatActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var tvEmpty: TextView

    private val http = OkHttpClient()
    private val PREFS_CACHE = "vi_prefs"
    private val CACHE_KEY   = "contacts_cache"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts)

        supportActionBar?.title = "Kontakty"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        rv       = findViewById(R.id.rvContacts)
        progress = findViewById(R.id.progressContacts)
        tvEmpty  = findViewById(R.id.tvContactsEmpty)

        rv.layoutManager = LinearLayoutManager(this)

        fetchContacts()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun fetchContacts() {
        progress.visibility = View.VISIBLE
        tvEmpty.visibility  = View.GONE

        val req = Request.Builder()
            .url("${ApiConfig.API_BASE}/api/contacts")
            .addHeader("Authorization", ApiConfig.authHeader(this))
            .build()

        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    progress.visibility = View.GONE
                    val cached = loadCache()
                    if (cached != null) {
                        showContacts(cached)
                        Toast.makeText(this@ContactsActivity, "Offline – dane z cache", Toast.LENGTH_SHORT).show()
                    } else {
                        tvEmpty.text = "Brak internetu i brak danych lokalnych"
                        tvEmpty.visibility = View.VISIBLE
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                runOnUiThread {
                    progress.visibility = View.GONE
                    if (response.isSuccessful) {
                        saveCache(body)
                        showContacts(body)
                    } else {
                        Toast.makeText(this@ContactsActivity, "Błąd HTTP ${response.code}", Toast.LENGTH_LONG).show()
                        val cached = loadCache()
                        if (cached != null) showContacts(cached)
                    }
                }
            }
        })
    }

    private fun showContacts(json: String) {
        val arr   = JSONArray(json)
        val items = mutableListOf<ContactItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            items.add(ContactItem(
                id    = o.optInt("id", 0),
                name  = o.optString("name", ""),
                role  = o.optString("role", ""),
                phone = o.optString("phone", ""),
                email = o.optString("email", ""),
                photoUrl = o.optString("photo_url", o.optString("photoUrl", ""))
            ))
        }
        rv.adapter = ContactsAdapter(items)
        tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun saveCache(json: String) {
        getSharedPreferences(PREFS_CACHE, MODE_PRIVATE).edit().putString(CACHE_KEY, json).apply()
    }

    private fun loadCache(): String? =
        getSharedPreferences(PREFS_CACHE, MODE_PRIVATE).getString(CACHE_KEY, null)
}
