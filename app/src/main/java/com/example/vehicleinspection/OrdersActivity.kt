package com.example.vehicleinspection

import android.content.Context
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class OrdersActivity : AppCompatActivity() {

    private val http = OkHttpClient()

    private lateinit var tvVehicle: TextView
    private lateinit var tvRoute: TextView
    private lateinit var btnChoose: MaterialButton
    private lateinit var btnRefresh: MaterialButton
    private lateinit var progress: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var rv: RecyclerView

    private val adapter = RouteEventAdapter()

    private var vehicles: List<VehicleItem> = emptyList()
    private var selectedVehicleId: Int = -1
    private var selectedVehicleLabel: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_orders)

        tvVehicle = findViewById(R.id.tvVehicle)
        tvRoute = findViewById(R.id.tvRoute)
        btnChoose = findViewById(R.id.btnChooseVehicle)
        btnRefresh = findViewById(R.id.btnRefresh)
        progress = findViewById(R.id.progress)
        tvEmpty = findViewById(R.id.tvEmpty)
        rv = findViewById(R.id.rvEvents)

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        val prefs = prefs()
        selectedVehicleId = prefs.getInt(PREF_VEHICLE_ID, -1)
        selectedVehicleLabel = prefs.getString(PREF_VEHICLE_LABEL, "") ?: ""

        renderVehicleHeader()

        btnChoose.setOnClickListener { loadVehiclesAndPick() }
        btnRefresh.setOnClickListener {
            if (selectedVehicleId <= 0) {
                loadVehiclesAndPick()
            } else {
                // opcjonalnie: POST /refresh, a potem GET events
                tryManualRefreshThenLoadEvents(selectedVehicleId)
            }
        }

        // start: jeśli nie ma pojazdu -> wybór, jeśli jest -> ładuj eventy
        if (selectedVehicleId <= 0) {
            loadVehiclesAndPick()
        } else {
            loadEvents(selectedVehicleId, allowCache = true)
        }
    }

    private fun renderVehicleHeader() {
        tvVehicle.text = if (selectedVehicleId > 0) {
            "Pojazd: $selectedVehicleLabel ($selectedVehicleId)"
        } else {
            "Pojazd: —"
        }
        tvRoute.text = "Trasa: —"
    }

    private fun apiUrl(path: String): String =
        ApiConfig.API_BASE.trimEnd('/') + path

    private fun authHeader(builder: Request.Builder): Request.Builder =
        builder.header("Authorization", ApiConfig.authHeader(this))
            .header("Accept", "application/json")

    private fun setLoading(on: Boolean) {
        progress.visibility = if (on) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun loadVehiclesAndPick() {
        setLoading(true)

        val req = authHeader(
            Request.Builder().url(apiUrl("/api/vehicles"))
        ).get().build()

        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    setLoading(false)
                    Toast.makeText(this@OrdersActivity, "Błąd sieci: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    runOnUiThread {
                        setLoading(false)
                        Toast.makeText(this@OrdersActivity, "HTTP ${response.code}: $body", Toast.LENGTH_LONG).show()
                    }
                    return
                }

                val parsed = parseVehicles(body)
                runOnUiThread {
                    setLoading(false)
                    vehicles = parsed
                    if (vehicles.isEmpty()) {
                        Toast.makeText(this@OrdersActivity, "Brak pojazdów z API.", Toast.LENGTH_LONG).show()
                        return@runOnUiThread
                    }
                    showVehiclePicker()
                }
            }
        })
    }

    private fun showVehiclePicker() {
        val items = vehicles.map { "${it.plate}  (id=${it.vehicleId})" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Wybierz pojazd")
            .setItems(items) { _, which ->
                val v = vehicles[which]
                selectedVehicleId = v.vehicleId
                selectedVehicleLabel = v.plate

                prefs().edit()
                    .putInt(PREF_VEHICLE_ID, selectedVehicleId)
                    .putString(PREF_VEHICLE_LABEL, selectedVehicleLabel)
                    .apply()

                renderVehicleHeader()
                loadEvents(selectedVehicleId, allowCache = true)
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    private fun tryManualRefreshThenLoadEvents(vehicleId: Int) {
        // jeśli backend ma endpoint -> super, jeśli nie -> i tak odpalimy GET events
        setLoading(true)

        val req = authHeader(
            Request.Builder().url(apiUrl("/api/vehicles/$vehicleId/refresh"))
        ).post("".toRequestBody(null)).build()

        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    setLoading(false)
                    // brak neta -> spróbuj cache
                    loadEvents(vehicleId, allowCache = true)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
                runOnUiThread {
                    setLoading(false)
                    loadEvents(vehicleId, allowCache = false)
                }
            }
        })
    }

    private fun loadEvents(vehicleId: Int, allowCache: Boolean) {
        setLoading(true)
        tvEmpty.visibility = android.view.View.GONE

        val req = authHeader(
            Request.Builder().url(apiUrl("/api/vehicles/$vehicleId/route/active/events"))
        ).get().build()

        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    setLoading(false)
                    if (allowCache) {
                        val cached = prefs().getString(cacheKey(vehicleId), null)
                        if (!cached.isNullOrBlank()) {
                            applyEventsResponse(cached, fromCache = true)
                            Toast.makeText(this@OrdersActivity, "Offline: pokazuję ostatnie dane", Toast.LENGTH_SHORT).show()
                            return@runOnUiThread
                        }
                    }
                    Toast.makeText(this@OrdersActivity, "Brak internetu: ${e.message}", Toast.LENGTH_LONG).show()
                    adapter.setItems(emptyList())
                    tvEmpty.visibility = android.view.View.VISIBLE
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    runOnUiThread {
                        setLoading(false)
                        Toast.makeText(this@OrdersActivity, "HTTP ${response.code}: $body", Toast.LENGTH_LONG).show()
                    }
                    return
                }

                prefs().edit().putString(cacheKey(vehicleId), body).apply()

                runOnUiThread {
                    setLoading(false)
                    applyEventsResponse(body, fromCache = false)
                }
            }
        })
    }

    private fun applyEventsResponse(raw: String, fromCache: Boolean) {
        val parsed = parseEvents(raw)

        tvRoute.text = "Trasa: ${parsed.routeNumber.ifBlank { "—" }}${if (fromCache) " (cache)" else ""}"
        adapter.setItems(parsed.events)

        tvEmpty.visibility = if (parsed.events.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun parseVehicles(raw: String): List<VehicleItem> {
        val out = mutableListOf<VehicleItem>()
        val trimmed = raw.trim()

        val arr = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed).optJSONArray("data") ?: JSONArray()

        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optInt("vehicle_id", o.optInt("id", -1))
            if (id <= 0) continue

            val plate = firstNonBlank(
                o.optString("plate"),
                o.optString("registration"),
                o.optString("reg"),
                "ID $id"
            )

            out.add(VehicleItem(vehicleId = id, plate = plate))
        }
        return out
    }

    private data class EventsParsed(val routeNumber: String, val events: List<RouteEventItem>)

    private fun parseEvents(raw: String): EventsParsed {
        val trimmed = raw.trim()

        var routeNo = ""
        val eventsArr: JSONArray = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            val obj = JSONObject(trimmed)
            routeNo = obj.optString("route_number",
                obj.optJSONObject("route")?.optString("route_number").orEmpty()
            )
            obj.optJSONArray("events") ?: JSONArray()
        }

        val items = mutableListOf<RouteEventItem>()
        for (i in 0 until eventsArr.length()) {
            val ev = eventsArr.optJSONObject(i) ?: continue

            val orderNo = firstNonBlank(
                ev.optString("order_no"),
                ev.optString("orderNumber"),
                ev.optString("order"),
                ev.optString("zl"),
                "—"
            )

            val customer = firstNonBlank(
                ev.optString("customer"),
                ev.optString("customer_name"),
                ev.optString("client"),
                ev.optString("contractor"),
                ""
            )

            val loadText = when {
                ev.has("load_text") -> ev.optString("load_text")
                ev.opt("load") is String -> ev.optString("load")
                else -> formatStop(ev.optJSONObject("load") ?: ev.optJSONObject("loading"), "ZAŁ")
            }

            val unloadText = when {
                ev.has("unload_text") -> ev.optString("unload_text")
                ev.opt("unload") is String -> ev.optString("unload")
                else -> formatStop(ev.optJSONObject("unload") ?: ev.optJSONObject("unloading"), "ROZ")
            }

            items.add(RouteEventItem(
                header = listOf(orderNo, customer).filter { it.isNotBlank() }.joinToString(" | "),
                load = if (loadText.isBlank()) "ZAŁ: —" else loadText,
                unload = if (unloadText.isBlank()) "ROZ: —" else unloadText
            ))
        }

        return EventsParsed(routeNumber = routeNo, events = items)
    }

    private fun formatStop(o: JSONObject?, prefix: String): String {
        if (o == null) return ""
        val place = firstNonBlank(
            o.optString("place"),
            o.optString("name"),
            o.optString("address"),
            o.optString("addr")
        )

        val from = firstNonBlank(o.optString("from"), o.optString("time_from"), o.optString("window_from"))
        val to = firstNonBlank(o.optString("to"), o.optString("time_to"), o.optString("window_to"))

        val win = listOf(from, to).filter { it.isNotBlank() }.joinToString(" - ")
        return if (win.isNotBlank()) "$prefix: $place ($win)" else "$prefix: $place"
    }

    private fun firstNonBlank(vararg s: String): String =
        s.firstOrNull { it.isNotBlank() } ?: ""

    private fun prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun cacheKey(vehicleId: Int) = "events_cache_$vehicleId"

    private data class VehicleItem(val vehicleId: Int, val plate: String)

    private data class RouteEventItem(val header: String, val load: String, val unload: String)

    private class RouteEventAdapter : RecyclerView.Adapter<RouteEventVH>() {
        private val items = mutableListOf<RouteEventItem>()

        fun setItems(newItems: List<RouteEventItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RouteEventVH {
            val v = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_route_event, parent, false)
            return RouteEventVH(v)
        }

        override fun onBindViewHolder(holder: RouteEventVH, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size
    }

    private class RouteEventVH(v: android.view.View) : RecyclerView.ViewHolder(v) {
        private val tvHeader: TextView = v.findViewById(R.id.tvHeader)
        private val tvLoad: TextView = v.findViewById(R.id.tvLoad)
        private val tvUnload: TextView = v.findViewById(R.id.tvUnload)

        fun bind(item: RouteEventItem) {
            tvHeader.text = item.header
            tvLoad.text = item.load
            tvUnload.text = item.unload
        }
    }

    companion object {
        private const val PREFS = "vi_prefs"
        private const val PREF_VEHICLE_ID = "vehicle_id"
        private const val PREF_VEHICLE_LABEL = "vehicle_label"
    }
}
