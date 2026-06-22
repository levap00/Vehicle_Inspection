package com.example.vehicleinspection

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)

        val driverName = intent.getStringExtra("DRIVER_NAME") ?: ApiConfig.getDriverName(this)
        findViewById<TextView>(R.id.tvWelcomeTitle).text = "Witaj, $driverName!"

        fun go(cls: Class<*>) = Intent(this, cls).putExtra("DRIVER_NAME", driverName)

        findViewById<View>(R.id.cardPreDeparture).setOnClickListener { startActivity(go(MainActivity::class.java)) }
        findViewById<View>(R.id.cardOrders).setOnClickListener      { startActivity(go(OrdersActivity::class.java)) }
        findViewById<View>(R.id.cardRoadCard).setOnClickListener     { startActivity(go(RoadCardActivity::class.java)) }
        findViewById<View>(R.id.cardPins).setOnClickListener         { startActivity(go(CardsActivity::class.java)) }
        findViewById<View>(R.id.cardDocs).setOnClickListener         { startActivity(go(ExpensesActivity::class.java)) }
        findViewById<View>(R.id.cardPhones).setOnClickListener       { startActivity(go(ContactsActivity::class.java)) }
    }
}
