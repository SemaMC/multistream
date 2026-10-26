package com.multistream.core.streaming

import com.multistream.core.common.EncodedChunk
import com.multistream.core.common.Layout
import com.multistream.core.common.Platform
import com.multistream.core.common.PlatformConfig
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Creates the [RTMPConnection] used for a platform. Split out as a seam so that the
 * real RTMP client (still undecided, see task-10-report.md) and the tests can supply
 * different connections without changing the orchestrator.
 */
fun interface RTMPConnectionFactory {
    fun create(config: PlatformConfig, layout: Layout): RTMPConnection
}

/**
 * Orchestrates several simultaneous RTMP connections.
 *
 * Each platform gets a connection whose layout comes from [layoutRouter]; [send]
 * fans an encoded chunk out only to the connections that are connected and expect
 * that layout. Connections are established in parallel and retried with
 * [RetryPolicy] (exponential backoff), which is what keeps long streams alive across
 * transient network failures.
 *
 * [connections] is a [CopyOnWriteArrayList] because [send] runs on the encoder thread
 * while connect/disconnect run on IO coroutines.
 */
@Singleton
class RTMPMultiStreamer internal constructor(
    private val layoutRouter: LayoutRouter,
    private val retryPolicy: RetryPolicy,
    private val connectionFactory: RTMPConnectionFactory
) {
    /**
     * Hilt entry point. The RTMP client is still undecided (task-10-report.md), so
     * the default factory builds [RTMPConnection]s whose client factory throws until
     * a real library is bound.
     */
    @Inject
    constructor(layoutRouter: LayoutRouter) : this(
        layoutRouter = layoutRouter,
        retryPolicy = RetryPolicy(),
        connectionFactory = RTMPConnectionFactory { config, layout ->
            RTMPConnection(config, layout)
        }
    )

    private val connections = CopyOnWriteArrayList<RTMPConnection>()

    /**
     * Connects every config, in parallel. A platform that fails all retry attempts
     * fails the whole call; successfully connected platforms stay connected.
     */
    suspend fun connect(configs: List<PlatformConfig>): Unit = coroutineScope {
        configs.map { config ->
            async {
                val layout = layoutRouter.getLayout(config.platform)
                val connection = retryPolicy.retry {
                    // A fresh connection per attempt: RTMPConnection.connect() is not
                    // reusable after a failure.
                    connectionFactory.create(config, layout).also { it.connect() }
                }
                replaceConnection(connection)
            }
        }.awaitAll()
        Unit
    }

    /** Sends [chunk] to every connected connection that expects its layout. */
    fun send(chunk: EncodedChunk) {
        connections.forEach { connection ->
            if (connection.isConnected && connection.layout == chunk.layout) {
                connection.sendChunk(chunk)
            }
        }
    }

    fun getConnection(platform: Platform): RTMPConnection? =
        connections.find { it.config.platform == platform }

    /** Connections currently connected with [Layout.HORIZONTAL]. */
    fun getHorizontalConnectionCount(): Int = connectedCount(Layout.HORIZONTAL)

    /** Connections currently connected with [Layout.VERTICAL]. */
    fun getVerticalConnectionCount(): Int = connectedCount(Layout.VERTICAL)

    val connectionCount: Int
        get() = connections.size

    suspend fun disconnectAll() {
        connections.forEach { it.disconnect() }
        connections.clear()
    }

    private fun connectedCount(layout: Layout): Int =
        connections.count { it.isConnected && it.layout == layout }

    private fun replaceConnection(connection: RTMPConnection) {
        val platform = connection.config.platform
        val stale = connections.filter { it.config.platform == platform }
        if (stale.isNotEmpty()) {
            // Disconnect before dropping: otherwise the old client is leaked.
            connections.removeAll(stale)
            stale.forEach { runCatching { it.disconnect() } }
        }
        connections.add(connection)
    }
}
