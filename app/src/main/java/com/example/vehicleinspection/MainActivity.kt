package com.example.vehicleinspection

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.tabs.TabLayout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID




class MainActivity : AppCompatActivity() {

    private var driverName: String = ""


    // Pojazd z CSV
    data class Vehicle(
        val registration: String,
        val fuel: String // "ON" albo "LNG"
    )

    data class Trailer(
        val registration: String,
        val brand: String
    )

    // --- listy kontrolne ---

    private val CHECKLIST_ON = listOf(
        "DOWÓD REJESTRACYJNY CIĄGNIKA",
        "DOWÓD REJESTRACYJNY NACZEPY",
        "TABLET",
        "MASECZKA FF2 – 2 SZT",
        "KARTA ALIOR",
        "KARTY PALIWOWE: ALIOR, AS 24, AS24 EUROTRAFIC, ON TURTLE, ID-CARD SZWAJCARIA",
        "KOPIA POLISY OCP",
        "LICENCJA",
        "NORMY SPALANIA EURO",
        "UPOWAŻNIENIE DO UŻYWANIA",
        "NIWO – HOLANDIA – ODPADY",
        "CERTYFIKAT NIEMCY – ODPADY",
        "CODE XL – TÜV/DEKRA DO NACZEPY",
        "TÜV AUSTRIA – ŁAŃCUCHY ZIMA",
        "CERTYFIKAT AS24 AUSTRIA REJ. URZĄDZENIA",
        "INSTRUKCJA ADR PL/RU",
        "ZESTAW W RAZIE WYPADKU + FORMULARZ SZKODY",
        "KLUCZ PRZEKŁADNIOWY DO KÓŁ",
        "LISTA NR TELEFONÓW"
    )

    private val CHECKLIST_LNG = listOf(
        "DOWÓD REJESTRACYJNY CIĄGNIKA",
        "DOWÓD REJESTRACYJNY NACZEPY",
        "TABLET",
        "MASECZKA FF2 – 2 SZT",
        "KARTA ALIOR",
        "KARTY PALIWOWE: AS24, AS24 EUROTRAFIC, ON TURTLE, BARMALGAS, BAYWA, ROMAC FUELS, E-LOGIS, BISEK",
        "CZIPY PALIWOWE: LIQUIND, LIQVIS",
        "KOPIA POLISY OCP",
        "TDT – CERTYFIKAT DOZORU ZBIORNIKÓW LNG",
        "LICENCJA",
        "NORMY SPALANIA EURO",
        "UPOWAŻNIENIE DO UŻYWANIA",
        "NIWO – HOLANDIA – ODPADY",
        "CERTYFIKAT NIEMCY – ODPADY",
        "CODE XL – TÜV/DEKRA DO NACZEPY",
        "TÜV AUSTRIA – ŁAŃCUCHY ZIMA",
        "CERTYFIKAT AS24 AUSTRIA REJ. URZĄDZENIA",
        "INSTRUKCJA ADR PL/RU",
        "ZESTAW W RAZIE WYPADKU + FORMULARZ SZKODY",
        "LISTA NR TELEFONÓW"
    )

    // --- UI: zakładki / sekcje ---

    private lateinit var tabModule: TabLayout

    private lateinit var sectionVehicle: View
    private lateinit var sectionInspection: View
    private lateinit var sectionRoadCard: View

    // --- „Przed wyjazdem” ---

    private lateinit var spVehicle: Spinner
    private lateinit var spTrailer: Spinner
    private lateinit var tvChecklistTitle: TextView
    private lateinit var checklistContainer: LinearLayout
    private lateinit var btnChecklistSelectAll: Button
    private lateinit var btnChecklistClear: Button

    // --- Inspekcja / zdjęcia ---

    private lateinit var cbRegistrationDoc: CheckBox
    private lateinit var cbTablet: CheckBox
    private lateinit var cbNoDamage: CheckBox

    private lateinit var btnPhotoLeft: Button
    private lateinit var btnPhotoFront: Button
    private lateinit var btnPhotoRight: Button
    private lateinit var btnPhotoBack: Button
    private lateinit var btnPhotoInside: Button
    private lateinit var btnSubmit: Button

    private lateinit var ivPhotoLeft: ImageView
    private lateinit var ivPhotoFront: ImageView
    private lateinit var ivPhotoRight: ImageView
    private lateinit var ivPhotoBack: ImageView
    private lateinit var ivPhotoInside: ImageView

    // --- dane ---

    private val vehicles = mutableListOf<Vehicle>()
    private val trailers = mutableListOf<Trailer>()
    private var pendingPhotoTarget: ImageView? = null

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                launchCameraPreview()
            } else {
                Toast.makeText(this, "Brak uprawnienia do aparatu", Toast.LENGTH_LONG).show()
            }
        }

    private val cameraPreviewLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
            if (bitmap != null) {
                pendingPhotoTarget?.setImageBitmap(bitmap)
            } else {
                Toast.makeText(this, "Nie zapisano zdjecia", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        driverName = intent.getStringExtra("DRIVER_NAME") ?: ApiConfig.getDriverName(this)
        setContentView(R.layout.activity_main)
        UploadWorker.ensurePeriodic(this)
        UploadWorker.enqueueOnce(this)



        // zakładki
        tabModule = findViewById(R.id.tabModule)
        sectionVehicle = findViewById(R.id.sectionVehicle)
        sectionInspection = findViewById(R.id.sectionInspection)
        sectionRoadCard = findViewById(R.id.sectionRoadCard)

        // „Przed wyjazdem”
        spVehicle = findViewById(R.id.spVehicle)
        spTrailer = findViewById(R.id.spTrailer)
        tvChecklistTitle = findViewById(R.id.tvChecklistTitle)
        checklistContainer = findViewById(R.id.checklistContainer)
        btnChecklistSelectAll = findViewById(R.id.btnChecklistSelectAll)
        btnChecklistClear = findViewById(R.id.btnChecklistClear)

        // Inspekcja / zdjęcia
        cbRegistrationDoc = findViewById(R.id.cbRegistrationDoc)
        cbTablet = findViewById(R.id.cbTablet)
        cbNoDamage = findViewById(R.id.cbNoDamage)

        btnPhotoLeft = findViewById(R.id.btnPhotoLeft)
        btnPhotoFront = findViewById(R.id.btnPhotoFront)
        btnPhotoRight = findViewById(R.id.btnPhotoRight)
        btnPhotoBack = findViewById(R.id.btnPhotoBack)
        btnPhotoInside = findViewById(R.id.btnPhotoInside)
        btnSubmit = findViewById(R.id.btnSubmit)

        ivPhotoLeft = findViewById(R.id.ivPhotoLeft)
        ivPhotoFront = findViewById(R.id.ivPhotoFront)
        ivPhotoRight = findViewById(R.id.ivPhotoRight)
        ivPhotoBack = findViewById(R.id.ivPhotoBack)
        ivPhotoInside = findViewById(R.id.ivPhotoInside)

        // Karta drogowa
        // zakładki
        tabModule.addTab(tabModule.newTab().setText("Przed wyjazdem"))
        showPreDeparture()
        tabModule.visibility = View.GONE
        sectionRoadCard.visibility = View.GONE




        // pojazdy z CSV + spinner
        loadVehiclesFromCsv()
        loadTrailersFromCsv()
        setupVehicleSpinner()
        setupTrailerSpinner()

        // karta drogowa
        btnChecklistSelectAll.setOnClickListener { setChecklistChecked(true) }
        btnChecklistClear.setOnClickListener { setChecklistChecked(false) }

        // zdjęcia
        btnPhotoLeft.setOnClickListener { openCamera(ivPhotoLeft) }
        btnPhotoFront.setOnClickListener { openCamera(ivPhotoFront) }
        btnPhotoRight.setOnClickListener { openCamera(ivPhotoRight) }
        btnPhotoBack.setOnClickListener { openCamera(ivPhotoBack) }
        btnPhotoInside.setOnClickListener { openCamera(ivPhotoInside) }

        btnSubmit.setOnClickListener { submitInspection() }

    }

    // --- zakładki ---

    private fun showPreDeparture() {
        sectionVehicle.visibility = View.VISIBLE
        sectionInspection.visibility = View.VISIBLE
        sectionRoadCard.visibility = View.GONE
    }

    private fun showRoadCard() {
        sectionVehicle.visibility = View.GONE
        sectionInspection.visibility = View.GONE
        sectionRoadCard.visibility = View.VISIBLE
    }

    // --- CSV: /assets/pojazdy.csv ---

    private fun loadVehiclesFromCsv() {
        vehicles.clear()
        try {
            // format: reg,brand,fuel
            assets.open("pojazdy.csv").bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val parts = line.split(",")
                    if (parts.size >= 3) {
                        val registration = parts[0].trim()
                        val fuel = parts[2].trim().uppercase() // "ON" / "LNG"
                        vehicles.add(Vehicle(registration, fuel))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VI", "submit error: ${e}", e)
            runOnUiThread {
                Toast.makeText(this, "${e.javaClass.simpleName}: ${e.message}", Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    private fun loadTrailersFromCsv() {
        trailers.clear()
        try {
            assets.open("naczepy.csv").bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val parts = line.split(",")
                    val registration = parts.getOrNull(0)?.trim().orEmpty()
                    val brand = parts.getOrNull(1)?.trim().orEmpty()
                    if (registration.isNotBlank()) {
                        trailers.add(Trailer(registration, brand))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VI", "trailers csv error", e)
            Toast.makeText(this, "Blad CSV naczep: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }


        private fun setupVehicleSpinner() {
        spVehicle.adapter = vehicleAdapter(vehicles.map { it.registration })

        spVehicle.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val vehicle = vehicles.getOrNull(position)
                if (vehicle != null) {
                    saveSelectedVehicle(vehicle.registration)
                    updateChecklistForFuel(vehicle.fuel)
                } else {
                    tvChecklistTitle.text = "Lista kontrolna przed wyjazdem"
                    checklistContainer.removeAllViews()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        selectSavedVehicle()
    }

    private fun setupTrailerSpinner() {
        spTrailer.adapter = vehicleAdapter(trailers.map { trailerLabel(it) })
        spTrailer.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                trailers.getOrNull(position)?.let { saveSelectedTrailer(it.registration) }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        selectSavedTrailer()
    }

    private fun vehicleAdapter(items: List<String>): ArrayAdapter<String> =
        object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
                super.getView(position, convertView, parent).apply {
                    (this as? TextView)?.apply {
                        setTextColor(ContextCompat.getColor(this@MainActivity, R.color.ink))
                        textSize = 18f
                        setPadding(dpToPx(12), 0, dpToPx(12), 0)
                    }
                }

            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
                super.getDropDownView(position, convertView, parent).apply {
                    (this as? TextView)?.apply {
                        setTextColor(ContextCompat.getColor(this@MainActivity, R.color.ink))
                        textSize = 18f
                        minHeight = dpToPx(52)
                    }
                }
        }.also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

    // --- generowanie checklisty ON / LNG ---

    private fun updateChecklistForFuel(fuel: String) {
        val items = if (fuel == "LNG") CHECKLIST_LNG else CHECKLIST_ON
        val titleSuffix = if (fuel == "LNG") " (LNG)" else " (ON)"
        tvChecklistTitle.text = "Lista kontrolna przed wyjazdem$titleSuffix"

        checklistContainer.removeAllViews()

        for (text in items) {
            val cb = CheckBox(this).apply {
                this.text = text
                textSize = 16f
                minHeight = dpToPx(52)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.ink))
                setBackgroundResource(R.drawable.bg_check_item)
                setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(4)
            }
            cb.layoutParams = params

            checklistContainer.addView(cb)
        }
    }

    private fun setChecklistChecked(checked: Boolean) {
        for (i in 0 until checklistContainer.childCount) {
            (checklistContainer.getChildAt(i) as? CheckBox)?.isChecked = checked
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    // --- aparat ---

    private fun openCamera(targetView: ImageView) {
        pendingPhotoTarget = targetView
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

        if (granted) {
            launchCameraPreview()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCameraPreview() {
        try {
            cameraPreviewLauncher.launch(null)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Brak aplikacji aparatu", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("VI", "camera launch error", e)
            Toast.makeText(this, "Nie mozna uruchomic aparatu: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun imageViewToJpegBytes(iv: ImageView): ByteArray? {
        val d = iv.drawable as? BitmapDrawable ?: return null
        val bmp = d.bitmap ?: return null
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return out.toByteArray()
    }

    private fun buildPayloadJson(clientId: String): String {
        val checklistArr = JSONArray()
        for (i in 0 until checklistContainer.childCount) {
            val cb = checklistContainer.getChildAt(i) as? CheckBox ?: continue
            checklistArr.put(JSONObject().apply {
                put("text", cb.text.toString())
                put("checked", cb.isChecked)
            })
        }

        val fuel = vehicles.getOrNull(spVehicle.selectedItemPosition)?.fuel ?: ""

        return JSONObject().apply {
            put("client_id", clientId)                 // <- klucz do idempotencji na backendzie
            put("driver_name", driverName)
            put("timestamp_ms", System.currentTimeMillis())
            put("registration", selectedRegistration())
            put("trailer_registration", selectedTrailerRegistration())
            put("fuel", fuel)

            put("inspection", JSONObject().apply {
                put("registration_doc", cbRegistrationDoc.isChecked)
                put("tablet", cbTablet.isChecked)
                put("no_damage", cbNoDamage.isChecked)
            })

            put("checklist", checklistArr)
        }.toString()
    }


    private fun submitInspection() {
        val clientId = UUID.randomUUID().toString()
        val payload = buildPayloadJson(clientId)

        val photos = mutableMapOf<String, ByteArray>()
        imageViewToJpegBytes(ivPhotoLeft)?.let { photos["photo_left"] = it }
        imageViewToJpegBytes(ivPhotoFront)?.let { photos["photo_front"] = it }
        imageViewToJpegBytes(ivPhotoRight)?.let { photos["photo_right"] = it }
        imageViewToJpegBytes(ivPhotoBack)?.let { photos["photo_back"] = it }
        imageViewToJpegBytes(ivPhotoInside)?.let { photos["photo_inside"] = it }

        try {
            // zapis zawsze (offline-first)
            OfflineQueue.save(this, clientId, payload, photos)

            UploadWorker.enqueueOnce(this)
            returnToMenu()

            Toast.makeText(this, "Zapisano. Wysyłka w tle (ID: $clientId)", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("VI", "submit error", e)
            Toast.makeText(this, "Błąd zapisu: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun selectedRegistration(): String =
        vehicles.getOrNull(spVehicle.selectedItemPosition)?.registration.orEmpty()

    private fun selectedTrailerRegistration(): String =
        trailers.getOrNull(spTrailer.selectedItemPosition)?.registration.orEmpty()

    private fun trailerLabel(trailer: Trailer): String =
        if (trailer.brand.isBlank()) trailer.registration else "${trailer.registration} - ${trailer.brand}"

    private fun saveSelectedVehicle(registration: String) {
        getSharedPreferences(FleetSelectionPrefs.PREFS, MODE_PRIVATE)
            .edit()
            .putString(FleetSelectionPrefs.KEY_VEHICLE_REGISTRATION, registration)
            .apply()
    }

    private fun saveSelectedTrailer(registration: String) {
        getSharedPreferences(FleetSelectionPrefs.PREFS, MODE_PRIVATE)
            .edit()
            .putString(FleetSelectionPrefs.KEY_TRAILER_REGISTRATION, registration)
            .apply()
    }

    private fun selectSavedVehicle() {
        val saved = getSharedPreferences(FleetSelectionPrefs.PREFS, MODE_PRIVATE)
            .getString(FleetSelectionPrefs.KEY_VEHICLE_REGISTRATION, null)
        val index = vehicles.indexOfFirst { it.registration == saved }
        if (index >= 0) spVehicle.setSelection(index)
    }

    private fun selectSavedTrailer() {
        val saved = getSharedPreferences(FleetSelectionPrefs.PREFS, MODE_PRIVATE)
            .getString(FleetSelectionPrefs.KEY_TRAILER_REGISTRATION, null)
        val index = trailers.indexOfFirst { it.registration == saved }
        if (index >= 0) spTrailer.setSelection(index)
    }

    private fun returnToMenu() {
        startActivity(
            Intent(this, MenuActivity::class.java)
                .putExtra("DRIVER_NAME", driverName)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }



}
