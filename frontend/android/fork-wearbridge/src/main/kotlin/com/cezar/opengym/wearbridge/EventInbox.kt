package com.cezar.opengym.wearbridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The watch's events, kept on disk the moment they arrive and until the page has applied them
 * (lib/watch-sync.js drain → applyEvents → saveNow → ack). One arriving while the page is asleep
 * (the phone locked in a pocket, the app killed) is still here when the page wakes.
 *
 * In order of arrival, each id once. `uri` is the event's DataItem, deleted on ack: that deletion is
 * what tells the watch the phone has it. Written whole to a temp file and renamed over the old one,
 * as upstream writes its own files, so a crash mid-write leaves the previous copy.
 */
class EventInbox(private val file: File) {
    @Serializable
    data class Item(val id: String, val json: String, val uri: String? = null, val at: Long = 0)

    private val json = Json { ignoreUnknownKeys = true }
    private val ser = ListSerializer(Item.serializer())
    private val lock = Any()

    fun all(): List<Item> = synchronized(lock) { read() }

    /** Adds an event unless its id is here already. Returns whether it was new. */
    fun add(item: Item): Boolean = synchronized(lock) {
        val items = read()
        if (items.any { it.id == item.id }) return false
        write((items + item).takeLast(MAX))
        true
    }

    /** Takes these ids out; returns the items taken, so their DataItems can be deleted. */
    fun remove(ids: Collection<String>): List<Item> = synchronized(lock) {
        val items = read()
        val (gone, left) = items.partition { it.id in ids }
        if (gone.isNotEmpty()) write(left)
        gone
    }

    private fun read(): List<Item> = try {
        if (file.exists()) json.decodeFromString(ser, file.readText()) else emptyList()
    } catch (e: Exception) {
        // An unreadable file is set aside rather than lost, and the queue starts again.
        file.renameTo(File(file.parentFile, file.name + ".bad-" + System.currentTimeMillis()))
        emptyList()
    }

    private fun write(items: List<Item>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(ser, items))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    companion object {
        /** A workout is a few hundred events at most; more than this is a watch gone wrong. */
        const val MAX = 1000
        const val FILE_NAME = "opengym-watch-events.json"

        /** The event's id from its JSON, or null when it has none (and is then not kept). */
        fun idOf(eventJson: String): String? = try {
            val o = Json.parseToJsonElement(eventJson) as? kotlinx.serialization.json.JsonObject
            (o?.get("id") as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
        } catch (e: Exception) { null }
    }
}
