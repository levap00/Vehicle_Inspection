package com.example.vehicleinspection

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RoadCardActivity : AppCompatActivity() {

    data class Vehicle(val registration: String, val fuel: String)
    data class Trailer(val registration: String, val brand: String)

    private data class LocationTarget(
        val addressView: EditText,
        val pointView: TextView
    )

    private val vehicles = mutableListOf<Vehicle>()
    private val trailers = mutableListOf<Trailer>()
    private val http = OkHttpClient()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val dateOnlyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeOnlyFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private lateinit var spVehicle: Spinner
    private lateinit var spTrailer: Spinner
    private lateinit var pointsContainer: LinearLayout
    private lateinit var btnAddPoint: Button
    private lateinit var btnSubmitRoad: Button
    private lateinit var tvRouteSummary: TextView

    private lateinit var etRoadCardNumber: EditText
    private lateinit var etRoadDriverName: EditText
    private lateinit var etDepartureDateTime: EditText
    private lateinit var etReturnDateTime: EditText
    private lateinit var etHeaderOdometerDeparture: EditText
    private lateinit var etHeaderOdometerReturn: EditText

    private var pendingLocationTarget: LocationTarget? = null
    private var formLocked = false
    private var generatedRoadCardNumber = ""

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.any { it }) {
                fillPendingLocation()
            } else {
                Toast.makeText(this, "Brak uprawnienia do lokalizacji", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_road_card)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarRoadCard)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Karta drogowa"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        spVehicle = findViewById(R.id.spVehicle)
        spTrailer = findViewById(R.id.spTrailer)
        pointsContainer = findViewById(R.id.pointsContainer)
        btnAddPoint = findViewById(R.id.btnAddPoint)
        btnSubmitRoad = findViewById(R.id.btnSubmitRoadCard)
        tvRouteSummary = findViewById(R.id.tvRouteSummary)

        etRoadCardNumber = findViewById(R.id.etRoadCardNumber)
        etRoadDriverName = findViewById(R.id.etRoadDriverName)
        etDepartureDateTime = findViewById(R.id.etDepartureDateTime)
        etReturnDateTime = findViewById(R.id.etReturnDateTime)
        etHeaderOdometerDeparture = findViewById(R.id.etHeaderOdometerDeparture)
        etHeaderOdometerReturn = findViewById(R.id.etHeaderOdometerReturn)

        val driverName = intent.getStringExtra("DRIVER_NAME") ?: ApiConfig.getDriverName(this)
        etRoadDriverName.setText(driverName)
        etDepartureDateTime.setText(dateFormat.format(Date()))
        generatedRoadCardNumber = savedInstanceState?.getString(KEY_CARD_NUMBER) ?: generateRoadCardNumber()
        etRoadCardNumber.setText(generatedRoadCardNumber)
        makeRoadCardNumberReadOnly()

        loadVehiclesFromCsv()
        loadTrailersFromCsv()
        setupVehicleSpinner()
        setupTrailerSpinner()

        addRoadPointRow()
        btnAddPoint.setOnClickListener { addRoadPointRow() }
        btnSubmitRoad.setOnClickListener {
            if (formLocked) {
                confirmUnlock()
            } else {
                submitRoadCard()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_CARD_NUMBER, generatedRoadCardNumber)
        super.onSaveInstanceState(outState)
    }

    private fun loadVehiclesFromCsv() {
        vehicles.clear()
        try {
            assets.open("pojazdy.csv").bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val parts = line.split(",")
                    if (parts.size >= 3) {
                        vehicles.add(Vehicle(parts[0].trim(), parts[2].trim().uppercase()))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VI", "vehicles csv error", e)
            Toast.makeText(this, "Blad CSV: ${e.message}", Toast.LENGTH_LONG).show()
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
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                vehicles.getOrNull(pos)?.let { saveSelectedVehicle(it.registration) }
            }
            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }
        selectSavedVehicle()
    }

    private fun setupTrailerSpinner() {
        spTrailer.adapter = vehicleAdapter(trailers.map { trailerLabel(it) })
        spTrailer.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                trailers.getOrNull(pos)?.let { saveSelectedTrailer(it.registration) }
            }

            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }
        selectSavedTrailer()
    }

    private fun vehicleAdapter(items: List<String>): ArrayAdapter<String> =
        object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
                super.getView(position, convertView, parent).apply {
                    (this as? TextView)?.apply {
                        setTextColor(ContextCompat.getColor(this@RoadCardActivity, R.color.ink))
                        textSize = 18f
                        setPadding(dpToPx(12), 0, dpToPx(12), 0)
                    }
                }

            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
                super.getDropDownView(position, convertView, parent).apply {
                    (this as? TextView)?.apply {
                        setTextColor(ContextCompat.getColor(this@RoadCardActivity, R.color.ink))
                        textSize = 18f
                        minHeight = dpToPx(52)
                    }
                }
        }.also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

    private fun addRoadPointRow() {
        val row = layoutInflater.inflate(R.layout.item_road_point, pointsContainer, false)
        pointsContainer.addView(row)
        configureRoadPointRow(row)
        renumberRows()
        updateSummary()
    }

    private fun configureRoadPointRow(row: View) {
        val departureDate = row.findViewById<EditText>(R.id.etDepartureDate)
        val departureTime = row.findViewById<EditText>(R.id.etDepartureTime)
        val odometerBefore = row.findViewById<EditText>(R.id.etOdometerBefore)
        val odometerAfter = row.findViewById<EditText>(R.id.etOdometerAfter)
        val distanceKm = row.findViewById<EditText>(R.id.etDistanceKm)
        val loadedKm = row.findViewById<EditText>(R.id.etLoadedKm)
        val emptyKm = row.findViewById<EditText>(R.id.etEmptyKm)

        val now = Date()
        departureDate.setText(dateOnlyFormat.format(now))
        departureTime.setText(timeOnlyFormat.format(now))

        row.findViewById<Button>(R.id.btnRemoveLeg).setOnClickListener {
            pointsContainer.removeView(row)
            if (pointsContainer.childCount == 0) addRoadPointRow()
            renumberRows()
            updateSummary()
        }

        row.findViewById<Button>(R.id.btnSaveLeg).setOnClickListener {
            if (isRouteRowBlank(row)) {
                Toast.makeText(this, "Wypelnij odcinek przed zablokowaniem", Toast.LENGTH_LONG).show()
            } else {
                lockRouteRow(row, true)
            }
        }

        row.findViewById<Button>(R.id.btnUnlockLeg).setOnClickListener { confirmUnlockRow(row) }

        row.findViewById<Button>(R.id.btnToggleBorder).setOnClickListener {
            val group = row.findViewById<View>(R.id.groupBorderFields)
            group.visibility = if (group.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        row.findViewById<Button>(R.id.btnStartLocation).setOnClickListener {
            requestLocationFor(
                row.findViewById(R.id.etStartAddress),
                row.findViewById(R.id.tvStartPoint)
            )
        }

        row.findViewById<Button>(R.id.btnEndLocation).setOnClickListener {
            requestLocationFor(
                row.findViewById(R.id.etEndAddress),
                row.findViewById(R.id.tvEndPoint)
            )
        }

        var updatingDistance = false
        fun recalculateDistance() {
            if (updatingDistance) return

            val before = parseNumber(odometerBefore.text.toString())
            val after = parseNumber(odometerAfter.text.toString())
            val loaded = parseNumber(loadedKm.text.toString())
            val empty = parseNumber(emptyKm.text.toString())

            val calculated = when {
                before != null && after != null && after >= before -> after - before
                loaded != null || empty != null -> (loaded ?: 0.0) + (empty ?: 0.0)
                else -> null
            }

            if (calculated != null) {
                updatingDistance = true
                distanceKm.setText(formatNumber(calculated))
                updatingDistance = false
            }
            updateSummary()
        }

        listOf(odometerBefore, odometerAfter, loadedKm, emptyKm).forEach {
            it.afterTextChanged { recalculateDistance() }
        }
        distanceKm.afterTextChanged {
            if (!updatingDistance) updateSummary()
        }

        routeEditTextIds.forEach { id ->
            row.findViewById<EditText>(id).afterTextChanged {
                if (!formLocked && row.findViewById<Button>(R.id.btnSaveLeg).visibility == View.VISIBLE) {
                    row.findViewById<TextView>(R.id.tvLockState).text = "Zmieniono - zapisz odcinek, aby zablokowac pola"
                }
            }
        }
    }

    private fun renumberRows() {
        val count = pointsContainer.childCount
        for (i in 0 until count) {
            val row = pointsContainer.getChildAt(i)
            row.findViewById<TextView>(R.id.tvLegNumber).text = "Odcinek ${i + 1}"
            row.findViewById<Button>(R.id.btnRemoveLeg).visibility =
                if (!formLocked && count > 1) View.VISIBLE else View.GONE
        }
    }

    private fun updateSummary() {
        var filledRows = 0
        var totalKm = 0.0

        for (i in 0 until pointsContainer.childCount) {
            val row = pointsContainer.getChildAt(i)
            if (!isRouteRowBlank(row)) filledRows += 1
            totalKm += parseNumber(textOf(row, R.id.etDistanceKm)) ?: 0.0
        }

        tvRouteSummary.text = "$filledRows odcinkow, ${formatNumber(totalKm)} km"
    }

    private fun lockForm(locked: Boolean) {
        formLocked = locked
        spVehicle.isEnabled = !locked
        spTrailer.isEnabled = !locked
        btnAddPoint.isEnabled = !locked
        btnSubmitRoad.text = if (locked) "Karta zapisana - odblokuj edycje" else "Zapisz karte drogowa"

        headerEditTexts().forEach { it.isEnabled = !locked }

        for (i in 0 until pointsContainer.childCount) {
            lockRouteRow(pointsContainer.getChildAt(i), locked)
        }
        renumberRows()
    }

    private fun lockRouteRow(row: View, locked: Boolean) {
        routeEditTextIds.forEach { row.findViewById<EditText>(it).isEnabled = !locked }
        listOf(R.id.btnStartLocation, R.id.btnEndLocation, R.id.btnRemoveLeg, R.id.btnToggleBorder).forEach {
            row.findViewById<Button>(it).isEnabled = !locked
        }
        row.findViewById<Button>(R.id.btnSaveLeg).visibility = if (locked) View.GONE else View.VISIBLE
        row.findViewById<Button>(R.id.btnUnlockLeg).visibility = if (locked) View.VISIBLE else View.GONE
        row.findViewById<TextView>(R.id.tvLockState).text =
            if (locked) "Odcinek zapisany i zablokowany. Odblokowanie wymaga potwierdzenia." else "Edycja aktywna"
    }

    private fun confirmUnlock() {
        AlertDialog.Builder(this)
            .setTitle("Odblokowac karte?")
            .setMessage("Odblokowanie pozwoli zmienic zapisana karte drogowa. Czy na pewno chcesz kontynuowac?")
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Odblokuj") { _, _ -> lockForm(false) }
            .show()
    }

    private fun confirmUnlockRow(row: View) {
        AlertDialog.Builder(this)
            .setTitle("Odblokowac odcinek?")
            .setMessage("Odblokowanie pozwoli zmienic zapisane dane odcinka. Czy na pewno chcesz kontynuowac?")
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Odblokuj") { _, _ -> lockRouteRow(row, false) }
            .show()
    }

    private fun requestLocationFor(addressView: EditText, pointView: TextView) {
        pendingLocationTarget = LocationTarget(addressView, pointView)
        if (hasLocationPermission()) {
            fillPendingLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun fillPendingLocation() {
        val target = pendingLocationTarget ?: return
        val manager = getSystemService(LocationManager::class.java)

        val lastKnown = bestLastKnownLocation(manager)
        if (lastKnown != null) {
            applyLocation(target, lastKnown)
            return
        }

        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { providerName ->
                try {
                    manager.isProviderEnabled(providerName)
                } catch (e: Exception) {
                    false
                }
            }

        if (provider == null) {
            Toast.makeText(this, "Wlacz lokalizacje w tablecie", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, "Pobieram lokalizacje...", Toast.LENGTH_SHORT).show()
        manager.requestSingleUpdate(
            provider,
            object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    applyLocation(target, location)
                }
            },
            null
        )
    }

    @SuppressLint("MissingPermission")
    private fun bestLastKnownLocation(manager: LocationManager): Location? {
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        return providers.mapNotNull { provider ->
            try {
                manager.getLastKnownLocation(provider)
            } catch (e: Exception) {
                null
            }
        }.maxByOrNull { it.time }
    }

    private fun applyLocation(target: LocationTarget, location: Location) {
        val coords = String.format(Locale.US, "%.6f, %.6f", location.latitude, location.longitude)
        target.pointView.text = coords

        if (!Geocoder.isPresent()) {
            target.addressView.setText(coords)
            return
        }

        Thread {
            val address = try {
                @Suppress("DEPRECATION")
                Geocoder(this, Locale.getDefault())
                    .getFromLocation(location.latitude, location.longitude, 1)
                    ?.firstOrNull()
                    ?.getAddressLine(0)
            } catch (e: Exception) {
                Log.w("VI", "geocode failed", e)
                null
            }

            runOnUiThread {
                target.addressView.setText(address ?: coords)
            }
        }.start()
    }

    private fun buildRoadPayloadJson(): String {
        val points = JSONArray()
        var firstOdometer = etHeaderOdometerDeparture.text.toString().trim()
        var lastOdometer = etHeaderOdometerReturn.text.toString().trim()
        var totalKm = 0.0
        var loadedTotal = 0.0
        var emptyTotal = 0.0

        for (i in 0 until pointsContainer.childCount) {
            val row = pointsContainer.getChildAt(i)
            if (isRouteRowBlank(row)) continue

            val departureDate = textOf(row, R.id.etDepartureDate)
            val departureTime = textOf(row, R.id.etDepartureTime)
            val arrivalDateTime = etReturnDateTime.text.toString().trim()
            val startAddress = textOf(row, R.id.etStartAddress)
            val endAddress = textOf(row, R.id.etEndAddress)
            val startPoint = pointText(row, R.id.tvStartPoint)
            val endPoint = pointText(row, R.id.tvEndPoint)
            val odometerBefore = textOf(row, R.id.etOdometerBefore)
            val odometerAfter = textOf(row, R.id.etOdometerAfter)
            val borderDeparture = textOf(row, R.id.etBorderDeparture)
            val borderReturn = textOf(row, R.id.etBorderReturn)
            val loadedKm = textOf(row, R.id.etLoadedKm)
            val emptyKm = textOf(row, R.id.etEmptyKm)
            val distanceKm = textOf(row, R.id.etDistanceKm)
            val cargoKg = textOf(row, R.id.etCargoKg)

            if (firstOdometer.isBlank() && odometerBefore.isNotBlank()) firstOdometer = odometerBefore
            if (odometerAfter.isNotBlank()) lastOdometer = odometerAfter
            totalKm += parseNumber(distanceKm) ?: 0.0
            loadedTotal += parseNumber(loadedKm) ?: 0.0
            emptyTotal += parseNumber(emptyKm) ?: 0.0

            points.put(JSONObject().apply {
                put("departure_date", departureDate)
                put("departure_time", departureTime)
                put("arrival_datetime", arrivalDateTime)
                put("start_address", startAddress)
                put("start_point", startPoint)
                put("end_address", endAddress)
                put("end_point", endPoint)
                put("odometer_before", odometerBefore)
                put("odometer_after", odometerAfter)
                put("border_departure", borderDeparture)
                put("border_return", borderReturn)
                put("loaded_km", loadedKm)
                put("empty_km", emptyKm)
                put("distance_km", distanceKm)
                put("cargo_kg", cargoKg)

                put("load_place", startAddress)
                put("unload_place", endAddress)
                put("odo_leg_km", distanceKm)
                put("km_load", loadedKm)
                put("km_empty", emptyKm)
                put("time_start", "$departureDate $departureTime".trim())
                put("time_end", arrivalDateTime)
            })
        }

        val driverName = etRoadDriverName.text.toString().trim()
            .ifBlank { intent.getStringExtra("DRIVER_NAME") ?: ApiConfig.getDriverName(this) }

        return JSONObject().apply {
            put("driver_name", driverName)
            put("timestamp_ms", System.currentTimeMillis())
            put("registration", selectedRegistration())
            put("fuel", selectedFuel())
            put("road_card", JSONObject().apply {
                put("schema", "road_card_paper_v3")
                put("card_number", etRoadCardNumber.text.toString().trim())
                put("driver_name", driverName)
                put("tractor_registration", selectedRegistration())
                put("trailer_registration", selectedTrailerRegistration())
                put("departure_datetime", etDepartureDateTime.text.toString().trim())
                put("return_datetime", etReturnDateTime.text.toString().trim())
                put("header_odometer_departure", etHeaderOdometerDeparture.text.toString().trim())
                put("header_odometer_return", etHeaderOdometerReturn.text.toString().trim())
                put("odometer_start", firstOdometer)
                put("odometer_end", lastOdometer)
                put("total_distance_km", formatNumber(totalKm))
                put("loaded_km_total", formatNumber(loadedTotal))
                put("empty_km_total", formatNumber(emptyTotal))
                put("points", points)
            })
        }.toString()
    }

    private fun submitRoadCard() {
        if (filledRouteRowsCount() == 0) {
            Toast.makeText(this, "Wypelnij przynajmniej jeden odcinek", Toast.LENGTH_LONG).show()
            return
        }

        val payload = buildRoadPayloadJson()
        val body = payload.toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url("${ApiConfig.API_BASE}/api/road-cards")
            .addHeader("Authorization", ApiConfig.authHeader(this))
            .post(body)
            .build()

        btnSubmitRoad.isEnabled = false
        Toast.makeText(this, "Wysylam...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                http.newCall(req).execute().use { resp ->
                    val txt = resp.body?.string().orEmpty()
                    Log.d("VI", "ROAD HTTP ${resp.code} $txt")
                    runOnUiThread {
                        btnSubmitRoad.isEnabled = true
                        if (resp.isSuccessful) {
                            Toast.makeText(this, "Karta zapisana i zablokowana", Toast.LENGTH_LONG).show()
                            lockForm(true)
                        } else {
                            Toast.makeText(this, "Blad HTTP ${resp.code}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("VI", "road card submit error", e)
                runOnUiThread {
                    btnSubmitRoad.isEnabled = true
                    Toast.makeText(this, "Wyjatek: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun filledRouteRowsCount(): Int {
        var count = 0
        for (i in 0 until pointsContainer.childCount) {
            if (!isRouteRowBlank(pointsContainer.getChildAt(i))) count += 1
        }
        return count
    }

    private fun isRouteRowBlank(row: View): Boolean {
        val fields = listOf(
            R.id.etStartAddress,
            R.id.etEndAddress,
            R.id.etOdometerBefore,
            R.id.etOdometerAfter,
            R.id.etDistanceKm,
            R.id.etCargoKg,
            R.id.etLoadedKm,
            R.id.etEmptyKm
        )
        return fields.all { textOf(row, it).isBlank() }
    }

    private fun headerEditTexts(): List<EditText> = listOf(
        etRoadDriverName,
        etDepartureDateTime,
        etReturnDateTime,
        etHeaderOdometerDeparture,
        etHeaderOdometerReturn
    )

    private fun selectedRegistration(): String =
        vehicles.getOrNull(spVehicle.selectedItemPosition)?.registration.orEmpty()

    private fun selectedFuel(): String =
        vehicles.getOrNull(spVehicle.selectedItemPosition)?.fuel.orEmpty()

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

    private fun makeRoadCardNumberReadOnly() {
        etRoadCardNumber.keyListener = null
        etRoadCardNumber.isFocusable = false
        etRoadCardNumber.isFocusableInTouchMode = false
        etRoadCardNumber.isCursorVisible = false
    }

    private fun generateRoadCardNumber(): String {
        val monthFormat = SimpleDateFormat("yyyy/MM", Locale.US)
        val keyFormat = SimpleDateFormat("yyyyMM", Locale.US)
        val now = Date()
        val prefs = getSharedPreferences("vi_road_card_numbers", MODE_PRIVATE)
        val key = "counter_${keyFormat.format(now)}"
        val next = prefs.getInt(key, 0) + 1
        prefs.edit().putInt(key, next).apply()
        return "${monthFormat.format(now)}/${String.format(Locale.US, "%03d", next)}"
    }

    private fun textOf(row: View, id: Int): String =
        row.findViewById<EditText>(id).text.toString().trim()

    private fun pointText(row: View, id: Int): String {
        val text = row.findViewById<TextView>(id).text.toString().trim()
        return if (text.startsWith("Brak punktu")) "" else text
    }

    private fun parseNumber(text: String): Double? =
        text.trim().replace(",", ".").toDoubleOrNull()

    private fun formatNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else String.format(Locale.US, "%.1f", value)

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    private fun EditText.afterTextChanged(action: () -> Unit) {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                action()
            }
        })
    }

    private companion object {
        const val KEY_CARD_NUMBER = "road_card_number"

        val routeEditTextIds = listOf(
            R.id.etDepartureDate,
            R.id.etDepartureTime,
            R.id.etStartAddress,
            R.id.etEndAddress,
            R.id.etOdometerBefore,
            R.id.etOdometerAfter,
            R.id.etBorderDeparture,
            R.id.etBorderReturn,
            R.id.etLoadedKm,
            R.id.etEmptyKm,
            R.id.etDistanceKm,
            R.id.etCargoKg
        )
    }
}
