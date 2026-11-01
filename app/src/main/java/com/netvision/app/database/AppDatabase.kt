package com.netvision.app.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DeviceEntity::class, NetworkRangeEntity::class, DeviceFolderEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun networkRangeDao(): NetworkRangeDao
    abstract fun deviceFolderDao(): DeviceFolderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Version 1 to 2 schema migration
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `devices` ADD COLUMN `snmpVersion` TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `device_folders` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("ALTER TABLE `devices` ADD COLUMN `folderId` TEXT NOT NULL DEFAULT 'uncategorized'")
                db.execSQL("INSERT OR IGNORE INTO `device_folders` (`id`, `name`, `createdAt`) VALUES ('uncategorized', 'غير مصنف', 0)")
            }
        }

        fun closeDatabase() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "netvision_database"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
