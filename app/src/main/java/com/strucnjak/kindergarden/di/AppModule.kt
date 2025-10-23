package com.strucnjak.kindergarden.di

import android.content.Context
import com.cloudinary.android.MediaManager
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.strucnjak.kindergarden.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideDatabase(): DatabaseReference = FirebaseDatabase.getInstance().reference

    @Provides
    @Singleton
    fun provideFusedLocationProvider(@ApplicationContext context: Context) =
        LocationServices.getFusedLocationProviderClient(context)

    @Provides
    @Singleton
    fun provideCloudinary(@ApplicationContext context: Context): MediaManager {
        val cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME
        val uploadPreset = BuildConfig.CLOUDINARY_UPLOAD_PRESET
        val config = hashMapOf(
            "cloud_name" to cloudName,
            "unsigned" to "true",
            "upload_preset" to uploadPreset
        )
        try {
            MediaManager.init(context, config)
        } catch (_: Exception) { }
        return MediaManager.get()
    }
}
