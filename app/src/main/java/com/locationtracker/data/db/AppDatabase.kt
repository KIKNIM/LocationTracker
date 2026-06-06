package com.locationtracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.locationtracker.data.model.LocationRecord
import com.locationtracker.security.KeystoreHelper
import net.sqlcipher.database.SupportFactory

@Database(entities = [LocationRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao

    companion object {
        private const val NAME = "tracker.db"

        fun build(ctx: Context, keyHelper: KeystoreHelper): AppDatabase {
            val passphrase = keyHelper.obtainKey()
            val factory = SupportFactory(passphrase)
            return Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, NAME)
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
