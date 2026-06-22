package com.example.vehicleinspection

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.net.URL

class ContactsAdapter(private val items: List<ContactItem>) :
    RecyclerView.Adapter<ContactsAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val ivPhoto: ImageView = v.findViewById(R.id.ivContactPhoto)
        val tvName: TextView = v.findViewById(R.id.tvContactName)
        val tvRole: TextView = v.findViewById(R.id.tvContactRole)
        val tvPhone: TextView = v.findViewById(R.id.tvContactPhone)
        val tvEmail: TextView = v.findViewById(R.id.tvContactEmail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val contact = items[position]
        h.tvName.text = contact.name
        h.tvRole.text = contact.role.ifBlank { "-" }
        h.tvPhone.text = contact.phone.ifBlank { "-" }
        h.tvEmail.text = contact.email.ifBlank { "-" }

        bindPhoto(h, contact.photoUrl)

        if (contact.phone.isNotBlank()) {
            h.tvPhone.isClickable = true
            h.tvPhone.setOnClickListener {
                val normalizedPhone = contact.phone.replace(" ", "")
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$normalizedPhone"))
                h.itemView.context.startActivity(intent)
            }
        } else {
            h.tvPhone.isClickable = false
            h.tvPhone.setOnClickListener(null)
        }
    }

    override fun getItemCount() = items.size

    private fun bindPhoto(h: VH, photoUrl: String) {
        h.ivPhoto.setImageResource(R.drawable.logo_transkap)

        if (photoUrl.isBlank()) return

        val resId = h.itemView.resources.getIdentifier(
            photoUrl.removePrefix("@drawable/"),
            "drawable",
            h.itemView.context.packageName
        )
        if (resId != 0) {
            h.ivPhoto.setImageResource(resId)
            return
        }

        val absoluteUrl = if (photoUrl.startsWith("/")) "${ApiConfig.API_BASE}$photoUrl" else photoUrl
        h.ivPhoto.tag = absoluteUrl
        Thread {
            val bitmap = try {
                URL(absoluteUrl).openStream().use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                null
            }

            if (bitmap != null) {
                h.itemView.post {
                    if (h.ivPhoto.tag == absoluteUrl) h.ivPhoto.setImageBitmap(bitmap)
                }
            }
        }.start()
    }
}
