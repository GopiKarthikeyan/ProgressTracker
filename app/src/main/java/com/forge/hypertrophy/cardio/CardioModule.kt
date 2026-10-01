package com.forge.hypertrophy.cardio

import android.content.Context
import android.location.LocationManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CardioModule {
    @Provides
    @Singleton
    fun fusedClient(@ApplicationContext context: Context): FusedLocationProviderClient {
        return LocationServices.getFusedLocationProviderClient(context)
    }

    @Provides
    @Singleton
    fun fusedSource(client: FusedLocationProviderClient, clock: Clock): FusedGpsLocationSource {
        return FusedGpsLocationSource(client, clock)
    }

    @Provides
    @Singleton
    fun platformSource(@ApplicationContext context: Context, clock: Clock): PlatformGpsLocationSource {
        val manager = context.getSystemService(LocationManager::class.java)
        return PlatformGpsLocationSource(manager, clock)
    }

    @Provides
    @Singleton
    fun locationSource(
        playServices: PlayServicesStatus,
        fused: FusedGpsLocationSource,
        platform: PlatformGpsLocationSource,
    ): LocationSource = SelectingLocationSource(playServices, fused, platform)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CardioBindings {
    @Binds
    @Singleton
    abstract fun bindController(impl: ContextCardioServiceController): CardioServiceController
}
