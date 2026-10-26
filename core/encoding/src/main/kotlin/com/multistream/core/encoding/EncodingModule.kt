package com.multistream.core.encoding

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Bindings that let the encoding module be injected without exposing implementation details in
 * consumers (for example, tests can build [DualVideoEncoder] with a fake [VideoEncoderFactory]).
 */
@Module
@InstallIn(SingletonComponent::class)
object EncodingModule {

    @Provides
    @Singleton
    fun provideVideoEncoderFactory(): VideoEncoderFactory = DefaultVideoEncoderFactory()

    @Provides
    @Singleton
    fun provideCompositionBackend(): CompositionBackend = GlCompositionBackend()
}
