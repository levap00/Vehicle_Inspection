package com.example.vehicleinspection

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray

class CardsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cards)

        supportActionBar?.title = "Karty"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val rv = findViewById<RecyclerView>(R.id.rvCards)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = CardsAdapter(loadCardsFromAssets())
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadCardsFromAssets(): List<PaymentCardItem> {
        val json = assets.open("cards.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(json)

        val out = ArrayList<PaymentCardItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.getString("name")
            val expiry = o.getString("expiry")
            val imageName = o.optString("image", "")
            val pin = o.optString("pin", "1234")

            val resId = resources.getIdentifier(imageName, "drawable", packageName)
            val safeResId = if (resId != 0) resId else R.drawable.logo_transkap

            out.add(PaymentCardItem(name, expiry, safeResId, pin))
        }
        return out
    }
}
