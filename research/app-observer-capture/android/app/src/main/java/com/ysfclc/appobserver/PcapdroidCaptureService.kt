package com.ysfclc.appobserver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import org.json.JSONObject
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Receives PCAPdroid's TCP Exporter (pcap-over-IP) on loopback only.
 * The incoming byte stream is a classic-PCAP global header followed by record headers and packet bytes.
 */
class PcapdroidCaptureService : Service() {
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var clientSocket: Socket? = null
    @Volatile private var receiverThread: Thread? = null
    @Volatile private var finishReason: String? = null
    private val stopping = AtomicBoolean(false)
    private val finalized = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startReceiver()
            ACTION_STOP -> {
                requestStop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_END_SESSION -> endSession(
                intent.getStringExtra(EXTRA_FINISH_REASON) ?: "user_stopped"
            )
        }
        return START_NOT_STICKY
    }

    private fun startReceiver() {
        if (!ObservationStore.hasActiveSession(this)) {
            stopSelf()
            return
        }
        if (receiverThread?.isAlive == true) return
        stopping.set(false)
        finalized.set(false)
        finishReason = null
        receiverReady = false
        receiverState = "starting"
        receivedPackets = 0L
        receivedBytes = 0L
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Opening local TCP receiver"))
        receiverThread = Thread({ receivePcapStream() }, "AppObserver-Pcapdroid-TCP").apply { start() }
    }

    private fun receivePcapStream() {
        val directory = ObservationStore.activeDirectory(this) ?: return
        val pcapFile = File(directory, "network.pcap")
        var packets = 0L
        var bytes = 0L
        try {
            ServerSocket().use { server ->
                serverSocket = server
                server.reuseAddress = true
                server.bind(InetSocketAddress(InetAddress.getByName(LOOPBACK), PORT), 1)
                server.soTimeout = 1_000
                receiverReady = true
                receiverState = "listening"
                ObservationStore.appendSessionEvent(this, JSONObject()
                    .put("type", "pcapdroid_tcp_receiver_ready")
                    .put("timestampEpochMs", System.currentTimeMillis())
                    .put("collector", "$LOOPBACK:$PORT"))
                updateNotification("Waiting for PCAPdroid TCP Exporter")

                var accepted: Socket? = null
                while (!stopping.get() && accepted == null) {
                    try {
                        accepted = server.accept()
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                }
                if (accepted == null || stopping.get()) return
                clientSocket = accepted
                receiverState = "connected"
                ObservationStore.appendSessionEvent(this, JSONObject()
                    .put("type", "pcapdroid_tcp_exporter_connected")
                    .put("timestampEpochMs", System.currentTimeMillis())
                    .put("remoteAddress", accepted.remoteSocketAddress.toString()))
                accepted.tcpNoDelay = true
                accepted.keepAlive = true
                accepted.use { client ->
                    val input = client.getInputStream()
                    val globalHeaderBytes = readExact(input, GLOBAL_HEADER_BYTES)
                        ?: throw EOFException("TCP stream closed before PCAP global header")
                    val fileHeader = PcapdroidPacketParser.parseGlobalHeader(globalHeaderBytes)
                        ?: throw IOException("TCP Exporter did not send a supported classic-PCAP header")
                    receiverState = "pcap-header"
                    ObservationStore.appendSessionEvent(this, JSONObject()
                        .put("type", "pcap_global_header_received")
                        .put("timestampEpochMs", System.currentTimeMillis())
                        .put("linkType", fileHeader.linkType)
                        .put("byteOrder", fileHeader.byteOrder.toString())
                        .put("nanoTimestamps", fileHeader.nanoTimestamps))

                    FileOutputStream(pcapFile, false).use { output ->
                        output.write(globalHeaderBytes)
                        FileOutputStream(File(directory, "network-events.jsonl"), false)
                            .bufferedWriter(Charsets.UTF_8).use { networkIndex ->
                                while (!stopping.get()) {
                                    val recordBytes = readExact(input, RECORD_HEADER_BYTES) ?: break
                                    val record = PcapdroidPacketParser.parseRecordHeader(
                                        recordBytes, fileHeader.byteOrder
                                    ) ?: throw IOException("Invalid PCAP record header")
                                    val packetData = readExact(input, record.capturedLength)
                                        ?: throw EOFException("TCP stream ended inside a PCAP packet")

                                    output.write(recordBytes)
                                    output.write(packetData)
                                    val selectedPackage = ObservationStore.activePackage(this)
                                        ?: "selected-app"
                                    val summary = PcapdroidPacketParser.summarizePcapPacket(
                                        packetData, fileHeader, record, selectedPackage
                                    )
                                    networkIndex.append(summary.toString()).append('\n')
                                    packets++
                                    bytes += packetData.size
                                    receivedPackets = packets
                                    receivedBytes = bytes
                                    // Flush every packet so live status and evidence are truthful.
                                    output.flush()
                                    networkIndex.flush()
                                    receiverState = "receiving"
                                    if (packets == 1L || packets % 8L == 0L) {
                                        ObservationStore.appendSessionEvent(this, JSONObject()
                                            .put("type", "pcap_packet_received")
                                            .put("timestampEpochMs", System.currentTimeMillis())
                                            .put("packetCount", packets)
                                            .put("packetBytes", packetData.size)
                                            .put("totalPacketBytes", bytes)
                                            .put("protocol", summary.optString("protocol", "unknown")))
                                        updateNotification("Captured $packets packets · ${bytes / 1024} KB")
                                    }
                                }
                                output.flush()
                                networkIndex.flush()
                            }
                    }
                    ObservationStore.appendSessionEvent(this, JSONObject()
                        .put("type", "pcapdroid_tcp_stream_closed")
                        .put("timestampEpochMs", System.currentTimeMillis())
                        .put("packetsWritten", packets)
                        .put("packetBytesWritten", bytes)
                        .put("linkType", fileHeader.linkType))
                }
            }
        } catch (error: Exception) {
            if (!stopping.get()) {
                ObservationStore.appendSessionEvent(this, JSONObject()
                    .put("type", "pcapdroid_tcp_receiver_error")
                    .put("timestampEpochMs", System.currentTimeMillis())
                    .put("error", error.javaClass.simpleName)
                    .put("message", error.message ?: "unknown"))
            }
        } finally {
            receiverReady = false
            receiverState = if (stopping.get()) "stopped" else "disconnected"
            clientSocket = null
            serverSocket = null
            ObservationStore.appendSessionEvent(this, JSONObject()
                .put("type", "pcapdroid_tcp_receiver_stopped")
                .put("timestampEpochMs", System.currentTimeMillis())
                .put("packetsWritten", packets)
                .put("packetBytesWritten", bytes))
            if (!stopping.get()) updateNotification("TCP receiver stopped · $packets packets saved")
            finishReason?.let(::finalizeSession)
        }
    }

    /**
     * Read exactly the requested number of bytes. A clean EOF before any byte returns null;
     * EOF in the middle of a PCAP structure is an error so a truncated file is never accepted silently.
     */
    private fun readExact(input: java.io.InputStream, size: Int): ByteArray? {
        require(size >= 0 && size <= MAX_RECORD_BYTES) { "Invalid PCAP stream read length: $size" }
        val result = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val count = input.read(result, offset, size - offset)
            if (count < 0) {
                if (offset == 0) return null
                throw EOFException("Unexpected EOF after $offset of $size bytes")
            }
            if (count == 0) continue
            offset += count
        }
        return result
    }

    private fun endSession(reason: String) {
        finishReason = reason
        requestStop()
        val thread = receiverThread
        if (thread == null || !thread.isAlive) finalizeSession(reason)
    }

    private fun requestStop() {
        stopping.set(true)
        receiverReady = false
        runCatching { clientSocket?.close() }
        runCatching { serverSocket?.close() }
        val thread = receiverThread
        if (thread != null && thread !== Thread.currentThread()) {
            runCatching { thread.join(2_500L) }
        }
    }

    private fun finalizeSession(reason: String) {
        if (!finalized.compareAndSet(false, true)) return
        ObservationStore.finishSession(this, reason)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, "App Observer research capture", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shows when App Observer is receiving a local PCAPdroid session" })
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(status: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID)
            else Notification.Builder(this)
        return builder
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("App Observer · Research session")
            .setContentText(status)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(status: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(status))
    }

    override fun onDestroy() {
        requestStop()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.ysfclc.appobserver.capture.action.PCAP_START"
        const val ACTION_STOP = "com.ysfclc.appobserver.capture.action.PCAP_STOP"
        const val ACTION_END_SESSION = "com.ysfclc.appobserver.capture.action.END_SESSION"
        const val EXTRA_FINISH_REASON = "finish_reason"
        const val PORT = 5123
        const val LOOPBACK = "127.0.0.1"
        private const val GLOBAL_HEADER_BYTES = 24
        private const val RECORD_HEADER_BYTES = 16
        private const val MAX_RECORD_BYTES = 16 * 1024 * 1024
        private const val CHANNEL_ID = "app_observer_capture"
        private const val NOTIFICATION_ID = 7512

        @Volatile private var receiverReady = false
        @Volatile private var receiverState = "idle"
        @Volatile private var receivedPackets = 0L
        @Volatile private var receivedBytes = 0L
        fun isReceiverReady(): Boolean = receiverReady
        fun liveStatus(): String = receiverState
        fun livePackets(): Long = receivedPackets
        fun liveBytes(): Long = receivedBytes
    }
}
