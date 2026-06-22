package com.example.vehicleinspection

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CardsAdapter(private val items: List<PaymentCardItem>) :
    RecyclerView.Adapter<CardsAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val iv: ImageView = v.findViewById(R.id.ivCard)
        val tvName: TextView = v.findViewById(R.id.tvName)
        val tvExpiry: TextView = v.findViewById(R.id.tvExpiry)
        val tvPin: TextView = v.findViewById(R.id.tvPin)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_card, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        h.iv.setImageResource(item.imageRes)
        h.tvName.text = item.name
        h.tvExpiry.text = "Wazna do: ${item.expiry}"
        h.tvPin.text = "PIN: ${item.pin}"
    }

    override fun getItemCount() = items.size
}
