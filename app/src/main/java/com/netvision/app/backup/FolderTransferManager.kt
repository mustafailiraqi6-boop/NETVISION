package com.netvision.app.backup

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import android.database.sqlite.SQLiteDatabase
import android.content.ContentValues
import com.netvision.app.database.AppDatabase
import com.netvision.app.security.DeviceCredentialStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import java.security.spec.KeySpec
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Selective tower transfer. The package is independent from the full backup format.
 * Sensitive values are encrypted with a password-derived AES-GCM key.
 */
object FolderTransferManager {
    data class FolderSummary(val id: String, val name: String, val deviceCount: Int)
    data class ImportSummary(val folders: List<String>, val deviceCount: Int)

    private const val FORMAT_VERSION = 1
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val ENTRY_NAME = "netvision_towers.enc"

    fun listFolders(context: Context): List<FolderSummary> {
        val db = AppDatabase.getDatabase(context).openHelper.readableDatabase
        val result = mutableListOf<FolderSummary>()
        db.query("SELECT f.id, f.name, (SELECT COUNT(*) FROM devices d WHERE d.folderId=f.id) FROM device_folders f WHERE f.id != 'uncategorized' ORDER BY f.createdAt").use { c ->
            while (c.moveToNext()) result += FolderSummary(c.getString(0), c.getString(1), c.getInt(2))
        }
        return result
    }

    fun export(context: Context, folderIds: List<String>, includeSnmp: Boolean, includeApi: Boolean, password: CharArray, destination: File) {
        require(folderIds.isNotEmpty()) { "اختر برجاً واحداً على الأقل" }
        require(password.size >= 8) { "كلمة مرور الملف يجب أن تكون 8 أحرف على الأقل" }
        val db = AppDatabase.getDatabase(context).openHelper.readableDatabase
        val folders = JSONArray()
        val devices = JSONArray()
        val settings = context.getSharedPreferences("netvision_settings", Context.MODE_PRIVATE)
        val credentialStore = DeviceCredentialStore(context)

        folderIds.distinct().forEach { folderId ->
            db.query("SELECT id,name,createdAt FROM device_folders WHERE id=?", arrayOf(folderId)).use { c ->
                if (c.moveToFirst()) folders.put(JSONObject().put("id", c.getString(0)).put("name", c.getString(1)).put("createdAt", c.getLong(2)))
            }
            db.query("SELECT * FROM devices WHERE folderId=?", arrayOf(folderId)).use { c ->
                while (c.moveToNext()) {
                    val item = JSONObject()
                    for (i in 0 until c.columnCount) {
                        val key = c.getColumnName(i)
                        if (c.isNull(i)) item.put(key, JSONObject.NULL)
                        else when (c.getType(i)) {
                            android.database.Cursor.FIELD_TYPE_INTEGER -> item.put(key, c.getLong(i))
                            android.database.Cursor.FIELD_TYPE_FLOAT -> item.put(key, c.getDouble(i))
                            else -> item.put(key, c.getString(i))
                        }
                    }
                    val ip = item.optString("ipAddress")
                    if (includeSnmp) {
                        item.put("snmpDeviceCommunity", settings.getString("snmp_device_community_$ip", null) ?: JSONObject.NULL)
                    }
                    if (includeApi && item.optString("manufacturer") == "MIKROTIK") {
                        val credentials = credentialStore.get(item.optString("id"))
                        if (credentials != null) {
                            item.put("apiUsername", credentials.username)
                            item.put("apiPassword", credentials.password)
                        }
                    }
                    devices.put(item)
                }
            }
        }
        require(folders.length() > 0) { "لم يتم العثور على الأبراج المحددة" }
        val payload = JSONObject()
            .put("format", "NETVISION_TOWER_TRANSFER")
            .put("version", FORMAT_VERSION)
            .put("createdAt", System.currentTimeMillis())
            .put("includeSnmp", includeSnmp)
            .put("includeApi", includeApi)
            .put("folders", folders)
            .put("devices", devices)
            .toString().toByteArray(Charsets.UTF_8)

        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(payload)

        destination.outputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry(ENTRY_NAME))
                zip.write(byteArrayOf(FORMAT_VERSION.toByte()))
                zip.write(salt)
                zip.write(iv)
                zip.write(encrypted)
                zip.closeEntry()
            }
        }
    }

    fun inspect(context: Context, source: File, password: CharArray): ImportSummary {
        val payload = readPayload(source, password)
        val folders = payload.getJSONArray("folders")
        val devices = payload.getJSONArray("devices")
        return ImportSummary((0 until folders.length()).map { folders.getJSONObject(it).optString("name") }, devices.length())
    }

    /** conflictMode: "replace" or "rename". Existing devices with the same IP are skipped. */
    fun import(context: Context, source: File, password: CharArray, conflictMode: String): ImportSummary {
        require(conflictMode == "replace" || conflictMode == "rename")
        val payload = readPayload(source, password)
        val folders = payload.getJSONArray("folders")
        val devices = payload.getJSONArray("devices")
        val db = AppDatabase.getDatabase(context).openHelper.writableDatabase
        val prefs = context.getSharedPreferences("netvision_settings", Context.MODE_PRIVATE)
        val credentials = DeviceCredentialStore(context)
        val importedNames = mutableListOf<String>()
        var importedDeviceCount = 0
        db.beginTransaction()
        try {
            for (i in 0 until folders.length()) {
                val sourceFolder = folders.getJSONObject(i)
                val sourceId = sourceFolder.getString("id")
                val originalName = sourceFolder.getString("name")
                var targetName = originalName
                val existingId = findFolderIdByName(db, originalName)
                if (existingId != null && conflictMode == "replace") {
                    db.delete("devices", "folderId=?", arrayOf(existingId))
                    db.delete("device_folders", "id=?", arrayOf(existingId))
                } else if (existingId != null) {
                    var suffix = 2
                    while (findFolderIdByName(db, "$originalName ($suffix)") != null) suffix++
                    targetName = "$originalName ($suffix)"
                }
                val newFolderId = "fld-${UUID.randomUUID()}"
                val folderValues = ContentValues().apply {
                    put("id", newFolderId); put("name", targetName); put("createdAt", System.currentTimeMillis())
                }
                db.insert("device_folders", SQLiteDatabase.CONFLICT_ABORT, folderValues)
                importedNames += targetName

                for (j in 0 until devices.length()) {
                    val item = devices.getJSONObject(j)
                    if (item.optString("folderId") != sourceId) continue
                    val ip = item.optString("ipAddress").trim()
                    if (ip.isBlank() || deviceIpExists(db, ip)) continue
                    val oldId = item.optString("id")
                    val newDeviceId = "dev-${UUID.randomUUID()}"
                    val values = ContentValues()
                    val columns = arrayOf("name","ipAddress","macAddress","manufacturer","model","deviceType","status","firmwareVersion","lastSeenTimestamp","signalDbm","txRateMbps","rxRateMbps","uptimeFormatted","cpuUsagePercent","memoryUsagePercent","pingLatencyMs","snmpVersion","lastPingTimestamp","apiPort")
                    columns.forEach { column ->
                        if (!item.isNull(column)) {
                            when (column) {
                                "lastSeenTimestamp","signalDbm","cpuUsagePercent","memoryUsagePercent","pingLatencyMs","lastPingTimestamp","apiPort" -> values.put(column, item.optLong(column))
                                "txRateMbps","rxRateMbps" -> values.put(column, item.optDouble(column))
                                else -> values.put(column, item.optString(column))
                            }
                        } else values.putNull(column)
                    }
                    values.put("id", newDeviceId)
                    values.put("folderId", newFolderId)
                    db.insert("devices", SQLiteDatabase.CONFLICT_ABORT, values)
                    importedDeviceCount++

                    val community = item.optString("snmpDeviceCommunity", "")
                    if (community.isNotBlank() && community != "null") prefs.edit().putString("snmp_device_community_$ip", community).apply()
                    val username = item.optString("apiUsername", "")
                    val apiPassword = item.optString("apiPassword", "")
                    if (username.isNotBlank() && apiPassword.isNotBlank()) credentials.save(newDeviceId, username, apiPassword)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return ImportSummary(importedNames, importedDeviceCount)
    }

    private fun findFolderIdByName(db: SupportSQLiteDatabase, name: String): String? =
        db.query("device_folders", arrayOf("id"), "name = ? COLLATE NOCASE", arrayOf(name), null, null, null).use { if (it.moveToFirst()) it.getString(0) else null }

    private fun deviceIpExists(db: SupportSQLiteDatabase, ip: String): Boolean =
        db.query("devices", arrayOf("id"), "ipAddress = ?", arrayOf(ip), null, null, "1").use { it.moveToFirst() }

    private fun readPayload(source: File, password: CharArray): JSONObject {
        require(password.isNotEmpty()) { "أدخل كلمة مرور الملف" }
        var packed: ByteArray? = null
        source.inputStream().use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == ENTRY_NAME) {
                        val out = ByteArrayOutputStream()
                        zip.copyTo(out)
                        packed = out.toByteArray()
                    }
                    zip.closeEntry()
                }
            }
        }
        val bytes = packed ?: error("ملف التصدير غير صالح")
        require(bytes.size > 1 + SALT_SIZE + IV_SIZE)
        require(bytes[0].toInt() == FORMAT_VERSION) { "إصدار ملف غير مدعوم" }
        val salt = bytes.copyOfRange(1, 1 + SALT_SIZE)
        val iv = bytes.copyOfRange(1 + SALT_SIZE, 1 + SALT_SIZE + IV_SIZE)
        val encrypted = bytes.copyOfRange(1 + SALT_SIZE + IV_SIZE, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
        val json = JSONObject(String(cipher.doFinal(encrypted), Charsets.UTF_8))
        require(json.optString("format") == "NETVISION_TOWER_TRANSFER") { "هذا ليس ملف نقل أبراج NETVISION" }
        require(json.optInt("version") == FORMAT_VERSION) { "إصدار ملف غير مدعوم" }
        return json
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec: KeySpec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(raw, "AES")
    }
}
