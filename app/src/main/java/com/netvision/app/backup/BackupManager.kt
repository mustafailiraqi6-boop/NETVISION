package com.netvision.app.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.netvision.app.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object BackupManager {
    private const val PREFS_NAME = "netvision_backup"
    private const val KEY_TREE_URI = "backup_tree_uri"

    fun getBackupFolder(context: Context): Uri? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TREE_URI, null) ?: return null
        return runCatching { Uri.parse(raw) }.getOrNull()
    }

    fun setBackupFolder(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_TREE_URI, uri.toString()).apply()
    }

    suspend fun createBackup(context: Context, treeUri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val database = AppDatabase.getDatabase(context)

                // Flush Room's WAL so the main database file is a consistent snapshot.
                database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { }

                val rootDocument = DocumentsContract.buildDocumentUriUsingTree(
                    treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri)
                )
                val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val fileName = "NETVISION_Backup_$stamp.zip"
                val destination = DocumentsContract.createDocument(
                    resolver, rootDocument, "application/zip", fileName
                ) ?: error("تعذر إنشاء ملف النسخة الاحتياطية داخل المجلد المحدد")

                try {
                    val databaseFile = context.getDatabasePath("netvision_database")
                    check(databaseFile.exists()) { "قاعدة بيانات الأجهزة غير موجودة" }
                    val settingsFile = File(context.applicationInfo.dataDir, "shared_prefs/netvision_settings.xml")

                    resolver.openOutputStream(destination, "w")?.use { output ->
                        ZipOutputStream(output).use { zip ->
                            addFile(zip, databaseFile, "netvision_database")
                            if (settingsFile.exists()) {
                                addFile(zip, settingsFile, "netvision_settings.xml")
                            }
                            zip.putNextEntry(ZipEntry("backup_info.txt"))
                            zip.write(
                                ("NETVISION backup\nCreated: $stamp\nDatabase: netvision_database\n").toByteArray(Charsets.UTF_8)
                            )
                            zip.closeEntry()
                        }
                    } ?: error("تعذر فتح ملف النسخة الاحتياطية للكتابة")
                } catch (error: Exception) {
                    runCatching { DocumentsContract.deleteDocument(resolver, destination) }
                    throw error
                }
                fileName
            }
        }

    private fun addFile(zip: ZipOutputStream, file: File, entryName: String) {
        zip.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { input -> input.copyTo(zip) }
        zip.closeEntry()
    }
}
