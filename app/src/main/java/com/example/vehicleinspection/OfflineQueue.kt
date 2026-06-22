package com.example.vehicleinspection

import android.content.Context
import org.json.JSONObject
import java.io.File

object OfflineQueue {
    private const val ROOT_DIR = "vi_queue"

    fun root(context: Context): File = File(context.filesDir, ROOT_DIR).apply { mkdirs() }

    fun dirFor(context: Context, clientId: String): File =
        File(root(context), clientId).apply { mkdirs() }

    fun save(
        context: Context,
        clientId: String,
        payloadJson: String,
        photos: Map<String, ByteArray>
    ) {
        val dir = dirFor(context, clientId)

        File(dir, "payload.json").writeText(payloadJson, Charsets.UTF_8)

        // proste meta (przydatne do debug)
        val meta = JSONObject()
            .put("client_id", clientId)
            .put("created_at_ms", System.currentTimeMillis())
            .toString()
        File(dir, "meta.json").writeText(meta, Charsets.UTF_8)

        for ((field, bytes) in photos) {
            File(dir, "$field.jpg").writeBytes(bytes)
        }
    }

    fun listDirs(context: Context): List<File> =
        root(context).listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()

    fun readPayload(dir: File): String =
        File(dir, "payload.json").readText(Charsets.UTF_8)

    fun readPhotoIfExists(dir: File, field: String): File? {
        val f = File(dir, "$field.jpg")
        return if (f.exists() && f.length() > 0) f else null
    }

    fun delete(dir: File) {
        dir.deleteRecursively()
    }
}
