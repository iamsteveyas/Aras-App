package com.aras.offlinepro

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import java.util.UUID

data class Site(
    val id: String,
    val code: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val values: Map<String, String>,
    val photoCount: Int
)

class SiteStore(context: Context) : SQLiteOpenHelper(context, "aras_pro.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE sites(
                id TEXT PRIMARY KEY,
                code TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                values_json TEXT NOT NULL,
                photo_count INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun createSite(code: String, name: String): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        writableDatabase.execSQL(
            "INSERT INTO sites(id,code,name,created_at,updated_at,values_json) VALUES(?,?,?,?,?,?)",
            arrayOf(id, code.trim(), name.trim(), now, now, "{}")
        )
        return id
    }

    fun findByCode(code: String): Site? = readableDatabase.rawQuery(
        "SELECT * FROM sites WHERE code=? LIMIT 1", arrayOf(code.trim())
    ).use { c -> if (c.moveToFirst()) read(c) else null }

    fun get(id: String): Site? = readableDatabase.rawQuery(
        "SELECT * FROM sites WHERE id=?", arrayOf(id)
    ).use { c -> if (c.moveToFirst()) read(c) else null }

    fun updateValue(id: String, key: String, value: String) {
        val site = get(id) ?: return
        val json = JSONObject(site.values)
        if (value.isBlank()) json.remove(key) else json.put(key, value)
        writableDatabase.execSQL(
            "UPDATE sites SET values_json=?, updated_at=? WHERE id=?",
            arrayOf(json.toString(), System.currentTimeMillis(), id)
        )
    }

    fun updatePhotoCount(id: String, count: Int) {
        writableDatabase.execSQL(
            "UPDATE sites SET photo_count=?, updated_at=? WHERE id=?",
            arrayOf(count, System.currentTimeMillis(), id)
        )
    }

    fun list(): List<Site> = readableDatabase.rawQuery(
        "SELECT * FROM sites ORDER BY updated_at DESC", null
    ).use { c -> buildList { while (c.moveToNext()) add(read(c)) } }

    private fun read(c: android.database.Cursor): Site {
        val values = mutableMapOf<String, String>()
        val json = JSONObject(c.getString(c.getColumnIndexOrThrow("values_json")))
        json.keys().forEach { values[it] = json.getString(it) }
        return Site(
            c.getString(c.getColumnIndexOrThrow("id")),
            c.getString(c.getColumnIndexOrThrow("code")),
            c.getString(c.getColumnIndexOrThrow("name")),
            c.getLong(c.getColumnIndexOrThrow("created_at")),
            c.getLong(c.getColumnIndexOrThrow("updated_at")),
            values,
            c.getInt(c.getColumnIndexOrThrow("photo_count"))
        )
    }
}
