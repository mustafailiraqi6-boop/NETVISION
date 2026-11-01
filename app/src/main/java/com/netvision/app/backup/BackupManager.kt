package com.netvision.app.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.netvision.app.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupManager {
    private const val PREFS_NAME = "netvision_backup"
    private const val KEY_TREE_URI = "backup_tree_uri"
    private const val MAX_BACKUPS = 2
    private const val DATABASE_NAME = "netvision_database"

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
                database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { }

                val root = DocumentsContract.buildDocumentUriUsingTree(
                    treeUri, DocumentsContract.getTreeDocumentId(treeUri)
                )
                val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val fileName = "NETVISION_Backup_$stamp.zip"
                val destination = DocumentsContract.createDocument(
                    resolver, root, "application/zip", fileName
                ) ?: error("تعذر إنشاء ملف النسخة الاحتياطية")

                try {
                    val dbFile = context.getDatabasePath(DATABASE_NAME)
                    check(dbFile.exists()) { "قاعدة بيانات الأجهزة غير موجودة" }
                    val settingsFile = File(context.applicationInfo.dataDir, "shared_prefs/netvision_settings.xml")
                    resolver.openOutputStream(destination, "w")?.use { output ->
                        ZipOutputStream(output).use { zip ->
                            addFile(zip, dbFile, DATABASE_NAME)
                            if (settingsFile.exists()) addFile(zip, settingsFile, "netvision_settings.xml")
                            zip.putNextEntry(ZipEntry("backup_info.txt"))
                            zip.write("NETVISION backup\nCreated: $stamp\n".toByteArray(Charsets.UTF_8))
                            zip.closeEntry()
                        }
                    } ?: error("تعذر الكتابة إلى ملف النسخة")
                } catch (error: Exception) {
                    runCatching { DocumentsContract.deleteDocument(resolver, destination) }
                    throw error
                }

                pruneOldBackups(context, treeUri)
                fileName
            }
        }

    suspend fun restoreBackup(context: Context, backupUri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val tempDir = File(context.cacheDir, "restore_staging").apply {
                    deleteRecursively()
                    mkdirs()
                }
                var databaseTemp: File? = null
                var settingsTemp: File? = null
                context.contentResolver.openInputStream(backupUri)?.use { input ->
                    ZipInputStream(input).use { zip ->
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            when (entry.name) {
                                DATABASE_NAME -> {
                                    val out = File(tempDir, DATABASE_NAME)
                                    out.outputStream().use { zip.copyTo(it) }
                                    databaseTemp = out
                                }
                                "netvision_settings.xml" -> {
                                    val out = File(tempDir, "netvision_settings.xml")
                                    out.outputStream().use { zip.copyTo(it) }
                                    settingsTemp = out
                                }
                            }
                            zip.closeEntry()
                        }
                    }
                } ?: error("تعذر قراءة ملف النسخة الاحتياطية")

                val stagedDb = databaseTemp ?: error("الملف لا يحتوي قاعدة بيانات NETVISION")
                check(stagedDb.length() > 0L) { "قاعدة البيانات داخل النسخة فارغة" }

                // Close Room before replacing its database file.
                AppDatabase.closeDatabase()
                val dbFile = context.getDatabasePath(DATABASE_NAME)
                dbFile.parentFile?.mkdirs()
                File(dbFile.path + "-wal").delete()
                File(dbFile.path + "-shm").delete()
                val replacement = File(dbFile.parentFile, "$DATABASE_NAME.restore")
                stagedDb.copyTo(replacement, overwrite = true)
                if (dbFile.exists() && !dbFile.delete()) error("تعذر استبدال قاعدة البيانات الحالية")
                if (!replacement.renameTo(dbFile)) error("تعذر تثبيت قاعدة البيانات المستعادة")

                settingsTemp?.let { staged ->
                    val settingsFile = File(context.applicationInfo.dataDir, "shared_prefs/netvision_settings.xml")
                    settingsFile.parentFile?.mkdirs()
                    staged.copyTo(settingsFile, overwrite = true)
                }
                tempDir.deleteRecursively()
            }
        }

    private fun pruneOldBackups(context: Context, treeUri: Uri) {
        val resolver = context.contentResolver
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri, DocumentsContract.getTreeDocumentId(treeUri)
        )
        val backups = mutableListOf<Pair<Uri, Long>>()
        resolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED),
            null, null,
            "${DocumentsContract.Document.COLUMN_LAST_MODIFIED} DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex) ?: continue
                if (name.startsWith("NETVISION_Backup_") && name.endsWith(".zip")) {
                    val id = cursor.getString(idIndex)
                    backups += DocumentsContract.buildDocumentUriUsingTree(treeUri, id) to
                        if (modifiedIndex >= 0) cursor.getLong(modifiedIndex) else 0L
                }
            }
        }
        backups.sortedByDescending { it.second }.drop(MAX_BACKUPS).forEach { (uri, _) ->
            runCatching { DocumentsContract.deleteDocument(resolver, uri) }
        }
    }

    private fun addFile(zip: ZipOutputStream, file: File, entryName: String) {
        zip.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { it.copyTo(zip) }
        zip.closeEntry()
    }
}
