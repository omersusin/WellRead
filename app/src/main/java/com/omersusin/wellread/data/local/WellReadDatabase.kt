package com.omersusin.wellread.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.omersusin.wellread.data.local.dao.BookDao
import com.omersusin.wellread.data.local.dao.SessionDao
import com.omersusin.wellread.data.local.entities.BookEntity
import com.omersusin.wellread.data.local.entities.SessionEntity

@Database(
    entities = [BookEntity::class, SessionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class WellReadDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun sessionDao(): SessionDao
}
