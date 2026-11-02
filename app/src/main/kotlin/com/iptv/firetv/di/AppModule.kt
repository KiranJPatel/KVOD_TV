package com.iptv.firetv.di

import android.content.Context
import androidx.room.Room
import com.iptv.firetv.core.database.AppDatabase
import com.iptv.firetv.core.player.Media3PlayerRepository
import com.iptv.firetv.core.player.PlayerRepository
import com.iptv.firetv.core.security.SecretCodec
import com.iptv.firetv.core.security.SecretStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun db(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, AppDatabase.NAME).addMigrations(*AppDatabase.MIGRATIONS).build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds abstract fun secrets(impl: SecretStore): SecretCodec
    @Binds abstract fun player(impl: Media3PlayerRepository): PlayerRepository
}
