package com.example.service

import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import java.util.Locale

class FloatingBubbleService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: ComposeView
    private lateinit var layoutParams: WindowManager.LayoutParams
    
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false

    // Lifecycle Support for dynamic Compose in Service
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val compositeViewModelStore = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = compositeViewModelStore
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        
        // Restore lifecycle and registry
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Initialize TextToSpeech engine
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale("ar")
                ttsReady = true
            }
        }

        setupOverlay()
    }

    private fun setupOverlay() {
        // Overlay Flags and Type configuration depending on SDK level
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingBubbleService)
            setViewTreeSavedStateRegistryOwner(this@FloatingBubbleService)
            setViewTreeViewModelStoreOwner(this@FloatingBubbleService)
            
            setContent {
                MaterialTheme {
                    FloatingBubbleContent()
                }
            }
        }

        windowManager.addView(overlayView, layoutParams)
    }

    @Composable
    fun FloatingBubbleContent() {
        var isExpanded by remember { mutableStateOf(false) }
        var currentClipboardText by remember { mutableStateOf("انقر لقراءة المنسوخ") }

        // Fetch primary clipboard text dynamically
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        
        LaunchedEffect(isExpanded) {
            if (isExpanded) {
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val txt = clip.getItemAt(0).text?.toString() ?: ""
                    currentClipboardText = if (txt.isBlank()) "الحافظة فارغة حالياً" else txt
                } else {
                    currentClipboardText = "الحافظة فارغة حالياً"
                }
            }
        }

        if (!isExpanded) {
            // Collapsed round bubble state
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F172A))
                    .border(2.dp, Color(0xFF38BDF8), CircleShape)
                    .clickable { isExpanded = true }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            layoutParams.x += dragAmount.x.toInt()
                            layoutParams.y += dragAmount.y.toInt()
                            try {
                                windowManager.updateViewLayout(overlayView, layoutParams)
                            } catch (e: Exception) {
                                // Ignore updates if view detached during animation
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = "Floating Assistant",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(28.dp)
                )
            }
        } else {
            // Expanded dashboard panel
            Card(
                modifier = Modifier
                    .width(280.dp)
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF38BDF8))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Title Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Build, "Assistant Logo", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                            Text(
                                text = "مُساعد المراقب الذكي",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(
                            onClick = { isExpanded = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, "Collapse", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                        }
                    }

                    // Clipboard content
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 80.dp)
                            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .border(0.5.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = currentClipboardText,
                            color = Color(0xFFE2E8F0),
                            fontSize = 10.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Actions Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Speak/Read Clipboard
                        Button(
                            onClick = {
                                if (ttsReady) {
                                    textToSpeech?.speak(
                                        currentClipboardText,
                                        TextToSpeech.QUEUE_FLUSH,
                                        null,
                                        "floating_bubble_tts"
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(38.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("اقرأ المنسوخ", fontSize = 10.sp, color = Color.White)
                        }

                        // Open Main App
                        Button(
                            onClick = {
                                isExpanded = false
                                val startAppIntent = Intent(this@FloatingBubbleService, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                }
                                startActivity(startAppIntent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(38.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.Home, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("فتح التطبيق", fontSize = 10.sp, color = Color.White)
                        }
                    }

                    // Danger Zone / Absolute Dismiss Button
                    Button(
                        onClick = { stopSelf() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.8f)),
                        modifier = Modifier.fillMaxWidth().height(32.dp),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("إغلاق الفقاعة العائمة نهائياً", fontSize = 9.sp, color = Color.White)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        
        try {
            windowManager.removeView(overlayView)
        } catch (e: Exception) {
            // Ignore if view was not added or already removed
        }
        
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
