package com.ysfclc.appobserver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Receives PCAPdroid's localhost UDP exporter while a user-started session is active.
 * It only binds to 127.0.0.1 and writes raw-IP PCAP evidence into this app's private session folder.
 */
class PcapdroidCaptureService : Service() {
    @Volatile private var receiverSocket: DatagramSocket? = null
    @Volatile private var receiverThread: Thread? = null
    private val stopping = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startReceiver()
            ACTION_STOP -> {
                stopReceiver()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_END_SESSION -> {
                stopReceiver()
                ObservationStore.finishSession(this, intent.getStringExtra(EXTRA_FINISH_REASON) ?: "user_stopped")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
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
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Waiting for PCAPdroid packets"))
        receiverThread = Thread({ receivePackets() }, "AppObserver-Pcapdroid-UDP").apply { start() }
    }

    private fun receivePackets() {
        val directory = ObservationStore.activeDirectory(this) ?: return
        val pcapFile = File(directory, "network.pcap")
        var packets = 0L
        var bytes = 0L
        try {
            DatagramSocket(null).use { socket ->
                receiverSocket = socket
                socket.reuseAddress = false
                socket.soTimeout = 1_000
                socket.bind(InetSocketAddress(InetAddress.getByName(LOOPBACK), PORT))
                ObservationStore.appendSessionEvent(this, JSONObject()
                    .put("type", "pcapdroid_udp_receiver_ready")
                    .put("timestampEpochMs", System.currentTimeMillis())
                    .put("collector", "$LOOPBACK:$PORT"))

                FileOutputStream(pcapFile, false).use { output ->
                    output.write(PCAP_GLOBAL_HEADER)
                    output.flush()
                    val buffer = ByteArray(65_535)
                    while (!stopping.get()) {
                        val datagram = DatagramPacket(buffer, buffer.size)
                        try {
                            socket.receive(datagram)
                        } catch (_: SocketTimeoutException) {
                            continue
                        }
                        val length = datagram.length
                        if (PcapdroidPacketParser.isGlobalHeader(buffer, length)) continue
                        val packet = PcapdroidPacketParser.parse(buffer, length, ObservationStore.activePackage(this) ?: "selected-app") ?: continue
                        output.write(packet.recordHeader)
                        output.write(packet.packetData)
                        packets++
                        bytes += packet.packetData.size
                        ObservationStore.appendNetworkEvent(this, packet.summary)
                        if (packets % 24L == 0L) {
                            output.flush()
                            updateNotification("Capturing: $packets packets · " + (bytes / 1024) + " KB")
                        }
                    }
                    output.flush()
                }
            }
        } catch (error: Exception) {
            if (!stopping.get()) {
                ObservationStore.appendSessionEvent(this, JSONObject()
                    .put("type", "pcapdroid_receiver_error")
                    .put("timestampEpochMs", System.currentTimeMillis())
                    .put("error", error.javaClass.simpleName)
                    .put("message", error.message ?: "unknown"))
            }
        } finally {
            receiverSocket = null
            ObservationStore.appendSessionEvent(this, JSONObject()
                .put("type", "pcapdroid_udp_receiver_stopped")
                .put("timestampEpochMs", System.currentTimeMillis())
                .put("packetsWritten", packets)
                .put("packetBytesWritten", bytes))
            if (!stopping.get()) updateNotification("Capture receiver stopped · $packets packets saved")
        }
    }

    private fun stopReceiver() {
        stopping.set(true)
        receiverSocket?.close()
        val thread = receiverThread
        thread?.interrupt()
        if (thread != null && thread !== Thread.currentThread()) {
            runCatching { thread.join(1_500L) }
        }
        receiverThread = null
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
        stopReceiver()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.ysfclc.appobserver.action.PCAP_START"
        const val ACTION_STOP = "com.ysfclc.appobserver.action.PCAP_STOP"
        const val ACTION_END_SESSION = "com.ysfclc.appobserver.action.END_SESSION"
        const val EXTRA_FINISH_REASON = "finish_reason"
        const val PORT = 5123
        const val LOOPBACK = "127.0.0.1"
        private const val CHANNEL_ID = "app_observer_capture"
        private const val NOTIFICATION_ID = 7512
        private val PCAP_GLOBAL_HEADER = byteArrayOf(
            0xd4.toByte(), 0xc3.toByte(), 0xb2.toByte(), 0xa1.toByte(),
            0x02, 0x00, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0xff.toByte(), 0xff.toByte(),
            // DLT_RAW (101): PCAPdroid exports raw IP packets when metadata extensions are disabled.
            0x65, 0x00, 0x00, 0x00
        )
    }
}
