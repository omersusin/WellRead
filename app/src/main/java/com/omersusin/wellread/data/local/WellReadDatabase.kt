package com.omersusin.wellread.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.omersusin.wellread.data.local.dao.BookDao
import com.omersusin.wellread.data.local.dao.SessionDao
import com.omersusin.wellread.data.local.entities.BookEntity
import com.omersusin.wellread.data.local.entities.SessionEntity

@Database(
    entities = [BookEntity::class, SessionEntity::class],
    version = 2,
    exportSchema = false
)
abstract class WellReadDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun sessionDao(): SessionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE books ADD COLUMN content TEXT NOT NULL DEFAULT ''"
                )
            }
        }
    }
}
