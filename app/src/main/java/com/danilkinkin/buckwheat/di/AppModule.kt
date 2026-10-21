package com.danilkinkin.buckwheat.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Singleton
    @Provides
    fun provideYourDatabase(
        @ApplicationContext app: Context
    ) = Room.databaseBuilder(
        app.applicationContext,
        DatabaseModule::class.java,
        "buckwheat-db",
    )
        .fallbackToDestructiveMigration()
        .allowMainThreadQueries()
        .addMigrations(*DatabaseModule.MANUAL_MIGRATIONS)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(INSERT_DEFAULT_BUDGET_SQL)
            }
        })
        .build()

    @Singleton
    @Provides
    fun provideTransactionDao(db: DatabaseModule) = db.transactionDao()

    @Singleton
    @Provides
    fun provideBudgetDao(db: DatabaseModule) = db.budgetDao()

    @Singleton
    @Provides
    fun provideStorageDao(db: DatabaseModule) = db.storageDao()
}