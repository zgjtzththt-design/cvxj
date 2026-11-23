package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TransferEntity::class, TrustedDeviceEntity::class],
    version = 1,
    exportSchema = false
)
abstract class DropLinkDatabase : RoomDatabase() {
    abstract fun dropLinkDao(): DropLinkDao

    companion object {
        @Volatile
        private var INSTANCE: DropLinkDatabase? = null

        fun getInstance(context: Context): DropLinkDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DropLinkDatabase::class.java,
                    "droplink.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
