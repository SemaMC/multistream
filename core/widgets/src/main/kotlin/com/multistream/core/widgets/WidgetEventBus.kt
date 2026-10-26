package com.multistream.core.widgets

import com.multistream.core.common.Platform
import dagger.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Singleton

@Singleton
class WidgetEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<StreamEvent>()
    val events: SharedFlow<StreamEvent> = _events

    suspend fun sendEvent(event: StreamEvent) {
        _events.emit(event)
    }
}

sealed class StreamEvent {
    data class Follow(val platform: Platform, val username: String) : StreamEvent()
    data class Subscription(val platform: Platform, val username: String, val months: Int) : StreamEvent()
    data class Donation(val platform: Platform, val username: String, val amount: Float) : StreamEvent()
    data class ChatMessage(val platform: Platform, val username: String, val message: String) : StreamEvent()
}
