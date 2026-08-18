package com.aras.offlinepro

import android.content.Context
import org.json.JSONObject

data class FieldDef(
    val row: Int?,
    val category: String,
    val subCategory: String,
    val label: String,
    val type: String,
    val comboMarker: String?,
    val options: List<String>
)

class FormSchema(private val categories: Map<String, List<FieldDef>>) {
    fun fields(category: String) = categories[category].orEmpty()
    fun categoryNames() = categories.keys.toList()

    companion object {
        fun load(context: Context): FormSchema {
            val text = context.assets.open("form_schema.json").bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val cats = root.getJSONObject("categories")
            val map = linkedMapOf<String, List<FieldDef>>()
            cats.keys().forEach { category ->
                val arr = cats.getJSONArray(category)
                val list = buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val options = mutableListOf<String>()
                        o.optJSONArray("options")?.let { a ->
                            for (j in 0 until a.length()) options += a.getString(j)
                        }
                        add(FieldDef(
                            o.optInt("row", -1).takeIf { it >= 0 },
                            o.getString("category"),
                            o.optString("subCategory", o.getString("label")),
                            o.getString("label"),
                            o.getString("type"),
                            o.optString("comboMarker", null),
                            options
                        ))
                    }
                }
                map[category] = list
            }
            return FormSchema(map)
        }
    }
}
