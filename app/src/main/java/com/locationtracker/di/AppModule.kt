package com.locationtracker.di

import android.content.Context
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.locationtracker.data.db.AppDatabase
import com.locationtracker.data.db.LocationDao
import com.locationtracker.security.KeystoreHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun fusedLocation(@ApplicationContext ctx: Context): FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(ctx)

    @Provides @Singleton
    fun activityRecognition(@ApplicationContext ctx: Context) =
        ActivityRecognition.getClient(ctx)

    @Provides @Singleton
    fun database(@ApplicationContext ctx: Context, keystore: KeystoreHelper): AppDatabase =
        AppDatabase.build(ctx, keystore)

    @Provides @Singleton
    fun dao(db: AppDatabase): LocationDao = db.locationDao()
}
