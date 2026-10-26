package com.multistream.app.di

import android.content.Context
import com.multistream.core.streaming.DefaultLayoutRouter
import com.multistream.core.streaming.LayoutRouter
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
    fun provideContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun provideLayoutRouter(): LayoutRouter = DefaultLayoutRouter()
}
