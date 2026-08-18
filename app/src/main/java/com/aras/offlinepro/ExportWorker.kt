package com.aras.offlinepro

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExportWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val siteId = inputData.getString("siteId") ?: return Result.failure()
        val store = SiteStore(applicationContext)
        val site = store.get(siteId) ?: return Result.failure()
        val exportDir = File(applicationContext.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val zip = File(exportDir, "${safe(site.code)}_${System.currentTimeMillis()}.zip")
        return runCatching {
            ZipOutputStream(zip.outputStream().buffered()).use { out ->
                val json = buildString {
                    append("{")
                    append("\"siteCode\":").append(quote(site.code)).append(",")
                    append("\"siteName\":").append(quote(site.name)).append(",")
                    append("\"createdAt\":").append(site.createdAt).append(",")
                    append("\"updatedAt\":").append(site.updatedAt).append(",")
                    append("\"values\":{")
                    site.values.entries.forEachIndexed { i, e ->
                        if (i > 0) append(",")
                        append(quote(e.key)).append(":").append(quote(e.value))
                    }
                    append("}}")
                }
                out.putNextEntry(ZipEntry("site.json")); out.write(json.toByteArray(Charsets.UTF_8)); out.closeEntry()
                val photoDir = File(applicationContext.filesDir, "photos/$siteId")
                if (photoDir.exists()) photoDir.listFiles()?.filter { it.isFile }?.forEach { file ->
                    out.putNextEntry(ZipEntry("photos/${file.name}"))
                    file.inputStream().buffered().use { it.copyTo(out) }
                    out.closeEntry()
                }
            }
            Result.success(androidx.work.workDataOf("zipPath" to zip.absolutePath))
        }.getOrElse { Result.failure() }
    }
    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
    private fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9._-]+"), "_")
}
