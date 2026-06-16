package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.database.AppDatabase
import com.example.data.repository.SmartMonitorRepository
import com.example.util.ClipboardParserUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ClipboardWatcherService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var repository: SmartMonitorRepository
    
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        checkClipboard()
    }

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipListener)
        
        val db = AppDatabase.getDatabase(applicationContext)
        repository = SmartMonitorRepository(
            context = applicationContext,
            templateDao = db.templateDao(),
            prefixPathDao = db.prefixPathDao(),
            logDao = db.logDao(),
            clipboardOperationDao = db.clipboardOperationDao()
        )
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        
        scope.launch {
            repository.log("⚙️ تم تشغيل خدمة الخلفية لمراقبة الحافظة بنجاح.", "INFO")
        }
    }

    private fun checkClipboard() {
        val clip = clipboardManager.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString() ?: ""
            if (text.isNotBlank() && text.hashCode() != lastTextHash) {
                lastTextHash = text.hashCode()
                scope.launch {
                    val activePrefixes = listOf("@builder", "@watcher", "@deploy")
                    if (ClipboardParserUtil.containsDirectives(text, activePrefixes)) {
                        repository.log("🛎️ خدمة الخلفية: كشف كُود توجيهي في الحافظة!", "INFO")
                        repository.processText(text, activePrefixes)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        clipboardManager.removePrimaryClipChangedListener(clipListener)
        job.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Clipboard Monitor Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for background clipboard monitoring service"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("المراقب الذكي نشط")
            .setContentText("جاري مراقبة الحافظة في الخلفية عن كثب...")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "clipboard_monitor_channel"
        private var lastTextHash = 0
    }
}
