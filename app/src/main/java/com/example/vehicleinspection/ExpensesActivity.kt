package com.example.vehicleinspection

import android.Manifest
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID

class ExpensesActivity : AppCompatActivity() {

    data class Vehicle(val registration: String, val fuel: String)

    private data class ExpenseAction(
        val title: String,
        val subtitle: String,
        val type: String,
        val paymentMethod: String,
        val requiresCard: Boolean,
        val requiresPurpose: Boolean
    )

    private val vehicles = mutableListOf<Vehicle>()
    private val paymentCards = mutableListOf<PaymentCardItem>()
    private val http = OkHttpClient()
    private var pendingPhotoTarget: ImageView? = null

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchCameraPreview() else Toast.makeText(this, "Brak uprawnienia do aparatu", Toast.LENGTH_LONG).show()
        }

    private val cameraPreviewLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
            if (bitmap != null) pendingPhotoTarget?.setImageBitmap(bitmap)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expenses)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarExpenses)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Wydatki"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        loadVehiclesFromCsv()
        loadCardsFromAssets()

        findViewById<View>(R.id.cardFuelPurchase).setOnClickListener {
            showExpenseDialog(
                ExpenseAction(
                    title = "Zakup paliwa",
                    subtitle = "Tankowanie na karte paliwowa",
                    type = "fuel_purchase",
                    paymentMethod = "card",
                    requiresCard = true,
                    requiresPurpose = false
                )
            )
        }
        findViewById<View>(R.id.cardOtherCardExpense).setOnClickListener {
            showExpenseDialog(
                ExpenseAction(
                    title = "Inne wydatki karta",
                    subtitle = "Oplaty, parkingi, autostrady",
                    type = "card_expense",
                    paymentMethod = "card",
                    requiresCard = true,
                    requiresPurpose = true
                )
            )
        }
        findViewById<View>(R.id.cardCashExpense).setOnClickListener {
            showExpenseDialog(
                ExpenseAction(
                    title = "Wydatki gotowka",
                    subtitle = "Platnosc gotowka z zaliczki",
                    type = "cash_expense",
                    paymentMethod = "cash",
                    requiresCard = false,
                    requiresPurpose = true
                )
            )
        }
        findViewById<View>(R.id.cardPrivateExpense).setOnClickListener {
            showExpenseDialog(
                ExpenseAction(
                    title = "Wydatki prywatnymi srodkami",
                    subtitle = "Platnosc z wlasnej kieszeni",
                    type = "private_expense",
                    paymentMethod = "private",
                    requiresCard = false,
                    requiresPurpose = true
                )
            )
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadVehiclesFromCsv() {
        vehicles.clear()
        try {
            assets.open("pojazdy.csv").bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val parts = line.split(",")
                    if (parts.size >= 3) vehicles.add(Vehicle(parts[0].trim(), parts[2].trim().uppercase()))
                }
            }
        } catch (e: Exception) {
            Log.e("VI", "CSV read", e)
        }
    }

    private fun loadCardsFromAssets() {
        paymentCards.clear()
        try {
            val arr = JSONArray(assets.open("cards.json").bufferedReader().use { it.readText() })
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val imageName = o.optString("image", "")
                val resId = resources.getIdentifier(imageName, "drawable", packageName)
                paymentCards.add(
                    PaymentCardItem(
                        name = o.optString("name", ""),
                        expiry = o.optString("expiry", ""),
                        imageRes = if (resId != 0) resId else R.drawable.logo_transkap,
                        pin = o.optString("pin", "1234")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("VI", "cards json read", e)
            Toast.makeText(this, "Blad cards.json: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showExpenseDialog(action: ExpenseAction) {
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(18), dpToPx(8), dpToPx(18), dpToPx(8))
        }
        scroll.addView(content)

        val vehicleSpinner = Spinner(this).apply {
            adapter = simpleTextAdapter(listOf("(brak pojazdu)") + vehicles.map { it.registration })
        }
        content.addView(label("Pojazd opcjonalnie"))
        content.addView(vehicleSpinner, matchWrap())
        selectSavedVehicle(vehicleSpinner)
        vehicleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0) saveSelectedVehicle(vehicles[position - 1].registration)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        var cardSpinner: Spinner? = null
        val cardPreview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundResource(R.drawable.image_placeholder_background)
        }
        if (action.requiresCard) {
            content.addView(label("Karta platnicza / paliwowa"))
            cardSpinner = Spinner(this).apply {
                adapter = CardSpinnerAdapter(paymentCards)
            }
            content.addView(cardSpinner, matchWrap())
            content.addView(cardPreview, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(132)
            ).apply { topMargin = dpToPx(10) })

            if (paymentCards.isNotEmpty()) cardPreview.setImageResource(paymentCards[0].imageRes)
            cardSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    paymentCards.getOrNull(position)?.let { cardPreview.setImageResource(it.imageRes) }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }

        val purposeInput = if (action.requiresPurpose) {
            EditText(this).apply {
                hint = "np. oplata autostradowa, parking"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                setSingleLine(false)
                minLines = 1
                setBackgroundResource(R.drawable.edit_text_background)
                setTextColor(ContextCompat.getColor(this@ExpensesActivity, R.color.ink))
            }.also {
                content.addView(label("Opis / Na co"))
                content.addView(it, matchWrap())
            }
        } else {
            null
        }

        val amountRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dpToPx(12), 0, 0)
        }
        val amountInput = EditText(this).apply {
            hint = "0.00"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setBackgroundResource(R.drawable.edit_text_background)
            setTextColor(ContextCompat.getColor(this@ExpensesActivity, R.color.ink))
        }
        val currencySpinner = Spinner(this).apply {
            adapter = simpleTextAdapter(listOf("PLN", "EUR", "CZK", "USD", "GBP"))
        }
        amountRow.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("Kwota"))
            addView(amountInput, matchWrap())
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        amountRow.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("Waluta"))
            addView(currencySpinner, matchWrap())
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dpToPx(10)
        })
        content.addView(amountRow)

        val photoView = ImageView(this).apply {
            setBackgroundResource(R.drawable.image_placeholder_background)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        content.addView(MaterialButton(this).apply {
            text = "Zrob zdjecie dokumentu"
            isAllCaps = false
            setTextColor(ContextCompat.getColor(this@ExpensesActivity, R.color.white))
            backgroundTintList = ContextCompat.getColorStateList(this@ExpensesActivity, R.color.info)
            setOnClickListener {
                pendingPhotoTarget = photoView
                openCamera()
            }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(52)
        ).apply { topMargin = dpToPx(14) })
        content.addView(photoView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(180)
        ).apply { topMargin = dpToPx(10) })

        val saveButton = MaterialButton(this).apply {
            text = "Zapisz wydatek"
            isAllCaps = false
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(this@ExpensesActivity, R.color.white))
            backgroundTintList = ContextCompat.getColorStateList(this@ExpensesActivity, R.color.accent)
        }
        content.addView(saveButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(56)
        ).apply { topMargin = dpToPx(16) })

        val dialog = AlertDialog.Builder(this)
            .setTitle(action.title)
            .setMessage(action.subtitle)
            .setView(scroll)
            .create()

        saveButton.setOnClickListener {
            val amount = amountInput.text.toString().trim()
            val purpose = purposeInput?.text?.toString()?.trim().orEmpty()
            val card = if (action.requiresCard) {
                paymentCards.getOrNull(cardSpinner?.selectedItemPosition ?: -1)?.name.orEmpty()
            } else {
                ""
            }

            when {
                amount.isBlank() -> Toast.makeText(this, "Podaj kwote", Toast.LENGTH_LONG).show()
                action.requiresPurpose && purpose.isBlank() -> Toast.makeText(this, "Uzupelnij pole: na co", Toast.LENGTH_LONG).show()
                action.requiresCard && card.isBlank() -> Toast.makeText(this, "Wybierz karte", Toast.LENGTH_LONG).show()
                else -> {
                    submitExpense(
                        action = action,
                        registration = selectedRegistration(vehicleSpinner),
                        amount = amount,
                        currency = currencySpinner.selectedItem.toString(),
                        description = purpose.ifBlank { action.title },
                        paymentCard = card,
                        photo = imageToJpeg(photoView)
                    )
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun submitExpense(
        action: ExpenseAction,
        registration: String,
        amount: String,
        currency: String,
        description: String,
        paymentCard: String,
        photo: ByteArray?
    ) {
        val clientId = UUID.randomUUID().toString()
        val driverName = intent.getStringExtra("DRIVER_NAME") ?: ApiConfig.getDriverName(this)
        val payload = JSONObject().apply {
            put("client_id", clientId)
            put("entity", "expense")
            put("type", action.type)
            put("driver_name", driverName)
            put("registration", registration)
            put("amount", amount)
            put("currency", currency)
            put("description", description)
            put("purpose", description)
            put("payment_method", action.paymentMethod)
            put("payment_card", paymentCard)
            put("timestamp_ms", System.currentTimeMillis())
        }.toString()

        val mb = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("payload", payload)

        if (photo != null) {
            mb.addFormDataPart("photo", "photo.jpg", photo.toRequestBody("image/jpeg".toMediaType()))
        }

        Toast.makeText(this, "Wysylam...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val req = Request.Builder()
                    .url("${ApiConfig.API_BASE}/api/expenses")
                    .addHeader("Authorization", ApiConfig.authHeader(this))
                    .post(mb.build())
                    .build()

                http.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    Log.d("VI", "EXPENSE HTTP ${resp.code} $body")
                    runOnUiThread {
                        if (resp.isSuccessful) {
                            Toast.makeText(this, "Zapisano wydatek", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Blad HTTP ${resp.code}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("VI", "expense submit", e)
                OfflineQueue.save(this, clientId, payload, if (photo != null) mapOf("photo" to photo) else emptyMap())
                UploadWorker.enqueueOnce(this)
                runOnUiThread {
                    Toast.makeText(this, "Offline - zapisano w kolejce", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun openCamera() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) launchCameraPreview() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun launchCameraPreview() {
        try {
            cameraPreviewLauncher.launch(null)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Brak aplikacji aparatu", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("VI", "expense camera launch error", e)
            Toast.makeText(this, "Nie mozna uruchomic aparatu: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun imageToJpeg(iv: ImageView): ByteArray? {
        val drawable = iv.drawable as? BitmapDrawable ?: return null
        val out = ByteArrayOutputStream()
        drawable.bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return out.toByteArray()
    }

    private fun selectedRegistration(spinner: Spinner): String {
        val index = spinner.selectedItemPosition - 1
        return if (index >= 0) vehicles.getOrNull(index)?.registration.orEmpty() else ""
    }

    private fun selectSavedVehicle(spinner: Spinner) {
        val saved = getSharedPreferences(FleetSelectionPrefs.PREFS, MODE_PRIVATE)
            .getString(FleetSelectionPrefs.KEY_VEHICLE_REGISTRATION, null)
        val index = vehicles.indexOfFirst { it.registration == saved }
        if (index >= 0) spinner.setSelection(index + 1)
    }

    private fun saveSelectedVehicle(registration: String) {
        getSharedPreferences(FleetSelectionPrefs.PREFS, MODE_PRIVATE)
            .edit()
            .putString(FleetSelectionPrefs.KEY_VEHICLE_REGISTRATION, registration)
            .apply()
    }

    private fun simpleTextAdapter(items: List<String>): ArrayAdapter<String> =
        object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                super.getView(position, convertView, parent).apply { styleSpinnerText(this) }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                super.getDropDownView(position, convertView, parent).apply { styleSpinnerText(this) }
        }.also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

    private inner class CardSpinnerAdapter(items: List<PaymentCardItem>) :
        ArrayAdapter<PaymentCardItem>(this, android.R.layout.simple_spinner_item, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            cardRow(position)

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
            cardRow(position)

        private fun cardRow(position: Int): View {
            val item = getItem(position)
            val row = LinearLayout(this@ExpensesActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
            }
            val image = ImageView(this@ExpensesActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(74), dpToPx(46))
                scaleType = ImageView.ScaleType.CENTER_CROP
                if (item != null) setImageResource(item.imageRes)
            }
            val text = TextView(this@ExpensesActivity).apply {
                text = item?.name.orEmpty()
                setTextColor(ContextCompat.getColor(this@ExpensesActivity, R.color.ink))
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dpToPx(12), 0, 0, 0)
            }
            row.addView(image)
            row.addView(text)
            return row
        }
    }

    private fun styleSpinnerText(view: View) {
        (view as? TextView)?.apply {
            setTextColor(ContextCompat.getColor(this@ExpensesActivity, R.color.ink))
            textSize = 16f
            minHeight = dpToPx(48)
        }
    }

    private fun label(text: String): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(ContextCompat.getColor(this@ExpensesActivity, R.color.muted))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dpToPx(12), 0, dpToPx(6))
        }

    private fun matchWrap(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()
}
