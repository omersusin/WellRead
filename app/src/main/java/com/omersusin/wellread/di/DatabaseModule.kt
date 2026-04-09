package com.omersusin.wellread.di

import android.content.Context
import androidx.room.Room
import com.omersusin.wellread.data.local.WellReadDatabase
import com.omersusin.wellread.data.local.dao.BookDao
import com.omersusin.wellread.data.local.dao.SessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WellReadDatabase =
        Room.databaseBuilder(context, WellReadDatabase::class.java, "wellread.db")
            .addMigrations(WellReadDatabase.MIGRATION_1_2)
            .build()

    @Provides fun provideBookDao(db: WellReadDatabase): BookDao = db.bookDao()
    @Provides fun provideSessionDao(db: WellReadDatabase): SessionDao = db.sessionDao()
}
