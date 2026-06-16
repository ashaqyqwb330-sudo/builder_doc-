package com.example

import android.app.Application
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.database.PrefixPathEntity
import com.example.data.database.TemplateEntity
import com.example.data.database.LogEntity
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.FileItem
import com.example.ui.viewmodel.SmartMonitorViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SmartMonitorApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartMonitorApp(viewModel: SmartMonitorViewModel = viewModel()) {
    val context = LocalContext.current
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val isMonitoring by viewModel.isClipboardMonitoring.collectAsStateWithLifecycle()

    // Periodically poll modern clipboard changes on app active/focus to simulate background daemon on Android
    LaunchedEffect(isMonitoring) {
        while (isMonitoring) {
            viewModel.checkClipboardForDirectives(context)
            delay(2000)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("app_scaffold"),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Daemon Logo",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "المراقب الذكي v5.9",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp)
                            )
                            Text(
                                text = "Smart Workspace Daemon & Parser",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            )
                        }
                    }
                },
                actions = {
                    // Clipboard daemon activity status indicator
                    Row(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .background(
                                color = if (isMonitoring) Color(0xFF15803D) else Color(0xFF334155),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { viewModel.toggleClipboardMonitoring(context) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isMonitoring) Color(0xFF4ADE80) else Color(0xFF94A3B8))
                        )
                        Text(
                            text = if (isMonitoring) "المراقب نشط" else "المراقبة متوقفة",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { viewModel.setTab(0) },
                    icon = { Icon(Icons.Default.Edit, "Compiler") },
                    label = { Text("المترجم") },
                    modifier = Modifier.testTag("tab_compiler")
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { 
                        viewModel.setTab(1)
                        viewModel.refreshWorkspaceFiles()
                    },
                    icon = { Icon(Icons.Default.Folder, "Explorer") },
                    label = { Text("الملفات") },
                    modifier = Modifier.testTag("tab_explorer")
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { viewModel.setTab(2) },
                    icon = { Icon(Icons.Default.Settings, "Config") },
                    label = { Text("قوالب العمل") },
                    modifier = Modifier.testTag("tab_templates")
                )
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { viewModel.setTab(3) },
                    icon = { Icon(Icons.Default.Info, "Web Merger") },
                    label = { Text("دمج الويب") },
                    modifier = Modifier.testTag("tab_web")
                )
                NavigationBarItem(
                    selected = currentTab == 4,
                    onClick = { viewModel.setTab(4) },
                    icon = { Icon(Icons.Default.Share, "Project Companion") },
                    label = { Text("رفيق المشروع") },
                    modifier = Modifier.testTag("tab_companion")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> CompilerTab(viewModel = viewModel)
                1 -> ExplorerTab(viewModel = viewModel)
                2 -> ConfigurationTab(viewModel = viewModel)
                3 -> WebMergerTab(viewModel = viewModel)
                4 -> ProjectCompanionTab(viewModel = viewModel)
            }

            // Global floating in-app Console Overlay
            ConsoleOverlay(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ---------------- TAB 0: COMPILER & MONITOR ----------------
@Composable
fun CompilerTab(viewModel: SmartMonitorViewModel) {
    val context = LocalContext.current
    val editorText by viewModel.editorText.collectAsStateWithLifecycle()
    val isMonitoring by viewModel.isClipboardMonitoring.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Cheering and encouragement card featuring Developer Engineer Idris Yusuf Al-Madani
        IdrisEncouragementCard(viewModel = viewModel)

        // Dashboard status indicators
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "مترجم كود التوجيهات وحزم العمل 5.9",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Start
                    )
                    Text(
                        text = "يدعم المعالجة المتوازية، دمج الروابط المطلقة وإدارتها تلقائياً.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { viewModel.toggleClipboardMonitoring(context) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMonitoring) Color(0xFF15803D) else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (isMonitoring) Icons.Default.Refresh else Icons.Default.PlayArrow,
                        contentDescription = "Toggle"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isMonitoring) "إيقاف المراقبة" else "بدء المراقبة")
                }
            }
        }

        // Action Utilities Panel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ElevatedButton(
                onClick = {
                    viewModel.readAndProcessClipboard(context)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("paste_scan_button"),
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Icon(Icons.Default.Add, "Paste")
                Spacer(modifier = Modifier.width(4.dp))
                Text("جلب ومعالجة الحافظة")
            }

            Button(
                onClick = {
                    // Populate Stress test example resolving Test 10 specifications
                    val sample = """
                    <!-- @builder:file test/file1.py -->
                    <!-- @builder:mode overwrite -->
                    print("Hello World from File 1")
                    <!-- @builder:end -->

                    <!-- @builder:file test/file2.py -->
                    <!-- @builder:mode append -->
                    print("Append statement into File 2")
                    <!-- @builder:end -->

                    <!-- @watcher:file logs/monitor_test.txt -->
                    <!-- @watcher:mode overwrite -->
                    Log record timestamp for Test 10.
                    <!-- @watcher:end -->

                    <!-- @deploy:file config.yaml -->
                    <!-- @deploy:mode overwrite -->
                    environment: production
                    port: 8080
                    <!-- @deploy:end -->
                    """.trimIndent()
                    viewModel.setEditorText(sample)
                    Toast.makeText(context, "تم إدراج كود اختبار الإجهاد (4 كتل متوازية)", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Icon(Icons.Default.Build, "Stress Test")
                Spacer(modifier = Modifier.width(4.dp))
                Text("اختبار الإجهاد (4 توجيهات)")
            }
        }

        // Cumulative Clipboard Accumulator Panel
        val isCumulativeByPrefs by viewModel.isCumulativeClipboardEnabled.collectAsStateWithLifecycle()
        val cumulativeBuffer by viewModel.cumulativeClipboardBuffer.collectAsStateWithLifecycle()

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Checkbox(
                            checked = isCumulativeByPrefs,
                            onCheckedChange = { viewModel.toggleCumulativeClipboard() },
                            modifier = Modifier.testTag("toggle_cumulative_clipboard")
                        )
                        Column {
                            Text(
                                text = "تفعيل المراقبة التراكمية الذكية (Accumulator)",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "تجميع دُفعات الكود المتعاقبة دون الاستبدال (حل لمشاكل حد نسخ النظام)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (isCumulativeByPrefs) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    ) {
                        Column {
                            Text(
                                text = "📥 حوض التراكم النشط:",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (cumulativeBuffer.isEmpty()) "الحوض فارغ حالياً. قم بنسخ فقرات برمجية تلو الأخرى لحشدها هنا تلقائياً..." 
                                       else "الكود المتراكم: ${cumulativeBuffer.length} حرفاً | ${cumulativeBuffer.lines().size} سطراً",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = if (cumulativeBuffer.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.processCumulativeClipboardBuffer() },
                            modifier = Modifier.weight(1.5f).height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            enabled = cumulativeBuffer.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Build, "Process Cumulative", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("عالج الحزمة المتراكمة بالكامل", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { viewModel.clearCumulativeClipboardBuffer() },
                            modifier = Modifier.weight(1f).height(40.dp),
                            enabled = cumulativeBuffer.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, "Clear", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("مسح التراكم", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Custom Rich Text Editor for pasting block code
        OutlinedTextField(
            value = editorText,
            onValueChange = { viewModel.setEditorText(it) },
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .testTag("code_editor"),
            placeholder = { Text("الصق كتل التوجيهات البرمجية هنا للمراقبة والمعالجة اليدوية...\nمثال:\n<!-- @builder:file test/hello.txt -->\nمرحباً بالعالم\n<!-- @builder:end -->") },
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        // Compile Executor Button
        Button(
            onClick = {
                viewModel.compilePastedText()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("execute_compiler_button")
        ) {
            Icon(Icons.Default.Build, "Execute")
            Spacer(modifier = Modifier.width(8.dp))
            Text("تشغيل ومعالجة الكود الحالي", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        // Live Monospaced Terminal Output Console (Resolves feedback issue 1)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                .background(Color(0xFF0F172A))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Info, "Terminal Console", tint = Color(0xFF4ADE80), modifier = Modifier.size(16.dp))
                    Text(
                        text = "محاكي مخرجات المروّس الذكي (Console logs):",
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                TextButton(
                    onClick = { viewModel.clearAllLogs() },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF87171))
                ) {
                    Icon(Icons.Default.Delete, "Clear Console", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تنظيف", fontSize = 11.sp)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(Color(0xFF0B1329))
                    .padding(8.dp)
            ) {
                if (logs.isEmpty()) {
                    Text(
                        text = "الرادار خامل. في انتظار استقبال أي توجيهات برمجية للتحليل...",
                        color = Color(0xFF64748B),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.Center),
                        textAlign = TextAlign.Center
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logs) { log ->
                            LogLineView(log = log)
                        }
                    }
                }
            }
        }
    }
}

// ---------------- TAB 1: WORKSPACE FILES EXPLORER ----------------
@Composable
fun ExplorerTab(viewModel: SmartMonitorViewModel) {
    val context = LocalContext.current
    val files by viewModel.workspaceFiles.collectAsStateWithLifecycle()
    val previewedFile by viewModel.previewedFile.collectAsStateWithLifecycle()
    val fileInputName by viewModel.fileProcessInputPath.collectAsStateWithLifecycle()
    var targetFolderNameInput by remember { mutableStateOf("test_folder") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Upgraded Custom File Browser & Path Navigator Card
        val customPathByVM by viewModel.customWorkspacePath.collectAsStateWithLifecycle()
        var typedPathInput by remember(customPathByVM) { mutableStateOf(customPathByVM) }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder, 
                            contentDescription = "Workspace Track",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "🐾 مسار المشروع النشط المخصص",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    IconButton(
                        onClick = { 
                            viewModel.refreshWorkspaceFiles() 
                            viewModel.refreshPlugins()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Refresh, "Refresh Files", modifier = Modifier.size(18.dp))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = typedPathInput,
                        onValueChange = { typedPathInput = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("📝 اكتب مسار المجلد لتصفحه") },
                        textStyle = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                        singleLine = true,
                        trailingIcon = {
                            if (typedPathInput.isNotBlank()) {
                                IconButton(onClick = { typedPathInput = "" }) {
                                    Icon(Icons.Default.Close, "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    )
                    Button(
                        onClick = { viewModel.updateWorkspaceDirectory(typedPathInput) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("انتقل", fontSize = 11.sp)
                    }
                }

                // Quick Navigation Chips / Buttons
                Text(
                    text = "🧭 روابط انتقال سريعة للمسارات:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val defaultSandboxPath = context.getExternalFilesDir(null)?.absolutePath + "/BuilderOutput"
                    val appFilesPath = context.filesDir.absolutePath
                    val systemRootPath = "/"
                    val sdCardPath = "/storage/emulated/0"

                    listOf(
                        Triple("الافتراضي 🐾", defaultSandboxPath, Color(0xFF10B981)),
                        Triple("النظام 📂", systemRootPath, Color(0xFFEF4444)),
                        Triple("الذاكرة 📱", sdCardPath, Color(0xFFF59E0B)),
                        Triple("الملفات 📦", appFilesPath, Color(0xFF3B82F6))
                    ).forEach { (label, targetPath, chipColor) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(chipColor.copy(alpha = 0.12f))
                                .border(1.dp, if (customPathByVM == targetPath) chipColor else chipColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.updateWorkspaceDirectory(targetPath)
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (customPathByVM == targetPath) FontWeight.Bold else FontWeight.Normal,
                                color = if (customPathByVM == targetPath) chipColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }

        // Test 5 & Test 6 UI testing utility section
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🛠️ معالجة ملف تفتيشي / ملفات مجلد معين:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                // Test 5 Manual file parse trigger
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = fileInputName,
                        onValueChange = { viewModel.fileProcessInputPath.value = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("الملف للاختبار (test_input.txt)") },
                        textStyle = TextStyle(fontSize = 12.sp),
                        singleLine = true
                    )
                    Button(
                        onClick = { viewModel.processManualSingleFile() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("عالج", fontSize = 11.sp)
                    }
                }

                // Test 6 Manual folder batch parse trigger
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = targetFolderNameInput,
                        onValueChange = { targetFolderNameInput = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("معالجة مجلد داخلي (مثل test_folder)") },
                        textStyle = TextStyle(fontSize = 12.sp),
                        singleLine = true
                    )
                    Button(
                        onClick = { viewModel.processSpecificFolder(targetFolderNameInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("عالج المجلد", fontSize = 11.sp)
                    }
                }
            }
        }

        // Workspace files hierarchy view list
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            if (files.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Empty Sandbox",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "مساحة العمل فارغة حالياً.",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "استخدم المترجم أو زر اختبار الإجهاد لإنشاء ملفات ومجلدات تلقائية هنا.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    items(files) { file ->
                        FileNodeItem(
                            fileItem = file,
                            onPreview = { viewModel.previewFile(it) },
                            onDelete = { viewModel.deleteWorkspaceFile(it) },
                            onProcess = { viewModel.processDirectivesFromFile(it.relativePath) },
                            onWebProcess = { viewModel.selectWebPageAndSwitchTab(it.relativePath) }
                        )
                    }
                }
            }
        }
    }

    // Modal Preview Dialog with Edit & Save triggers
    if (previewedFile != null) {
        val (fileName, fileContent) = previewedFile!!
        var draftContent by remember(fileName) { mutableStateOf(fileContent) }
        
        AlertDialog(
            onDismissRequest = { viewModel.closePreview() },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "تعديل: $fileName", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = { viewModel.closePreview() }) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "تعديل محتويات الملف مباشرة لحفظها:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    OutlinedTextField(
                        value = draftContent,
                        onValueChange = { draftContent = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("file_preview_editor"),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        maxLines = Int.MAX_VALUE
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveFileContent(fileName, draftContent)
                        Toast.makeText(context, "تم حفظ تعديلات الملف بنجاح", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("save_file_button")
                ) {
                    Icon(Icons.Default.Done, "Save")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("حفظ التعديلات")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closePreview() }) {
                    Text("إغلاق")
                }
            }
        )
    }
}

@Composable
fun FileNodeItem(
    fileItem: FileItem,
    depth: Int = 0,
    onPreview: (FileItem) -> Unit,
    onDelete: (FileItem) -> Unit,
    onProcess: ((FileItem) -> Unit)? = null,
    onWebProcess: ((FileItem) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (depth * 12).dp)
                .clickable {
                    if (fileItem.isDirectory) {
                        expanded = !expanded
                    } else {
                        onPreview(fileItem)
                    }
                }
                .padding(vertical = 6.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (fileItem.isDirectory) {
                        Icons.Default.Folder
                    } else {
                        Icons.Default.Share
                    },
                    contentDescription = "File Type Icon",
                    tint = if (fileItem.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = fileItem.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!fileItem.isDirectory) {
                        Text(
                            text = "${fileItem.size} بايت",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
            if (!fileItem.isDirectory) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isHtml = fileItem.name.endsWith(".html", true) || fileItem.name.endsWith(".htm", true)
                    if (isHtml) {
                        IconButton(
                            onClick = { onWebProcess?.invoke(fileItem) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share, 
                                contentDescription = "Merge Web Resources",
                                tint = Color(0xFF0EA5E9),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { onProcess?.invoke(fileItem) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow, 
                            contentDescription = "Process Directives",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    IconButton(
                        onClick = { onDelete(fileItem) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Item",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                IconButton(
                    onClick = { onDelete(fileItem) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Item",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        
        if (fileItem.isDirectory && expanded) {
            fileItem.children.forEach { child ->
                FileNodeItem(
                    fileItem = child,
                    depth = depth + 1,
                    onPreview = onPreview,
                    onDelete = onDelete,
                    onProcess = onProcess,
                    onWebProcess = onWebProcess
                )
            }
        }
    }
}

// ---------------- TAB 2: CONFIGURATION & TEMPLATES ----------------
@Composable
fun ConfigurationTab(viewModel: SmartMonitorViewModel) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val prefixPaths by viewModel.prefixPaths.collectAsStateWithLifecycle()
    val rawPrefixString by viewModel.directivePrefixes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    val watchFolder by viewModel.watchFolder.collectAsStateWithLifecycle()
    val watchExtensions by viewModel.watchExtensions.collectAsStateWithLifecycle()
    val watchPrefix by viewModel.watchPrefix.collectAsStateWithLifecycle()
    val watchOutputPath by viewModel.watchOutputPath.collectAsStateWithLifecycle()
    val isFolderWatching by viewModel.isFolderWatching.collectAsStateWithLifecycle()
    
    var newTmplName by remember { mutableStateOf("") }
    var newTmplPath by remember { mutableStateOf("") }
    var newTmplPrefix by remember { mutableStateOf("@builder") }
    var newTmplMode by remember { mutableStateOf("w") } // "w" or "a"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: Directives list configure (Resolves issue 3.1 dynamically changing list with save trigger)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Info, "Prefix Labels", tint = MaterialTheme.colorScheme.secondary)
                    Text(
                        text = "🏷️ بادئات العمل المفعلة (مسافات تفصل بينها):",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                var currentTextValue by remember(rawPrefixString) { mutableStateOf(rawPrefixString.joinToString(" ")) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = currentTextValue,
                        onValueChange = { currentTextValue = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("@builder @watcher @myprefix") }
                    )
                    Button(
                        onClick = { viewModel.updatePrefixesList(currentTextValue) }
                    ) {
                        Text("تحديث")
                    }
                }
            }
        }

        // Section 2: Dynamic Custom Prefixes Directories (Resolves Test 3.1 & Dynamic Update Requirements)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Folder, "Custom Paths", tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "📁 مسارات عمل مخصصة لكل بادئة (Dynamic Mapping):",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Text(
                    text = "يتم توليد حقول الإدخال أدناه تلقائياً لجميع البادئات المدرجة في الإعدادات أعلاه. استخدم صندوق الخيار لتشغيل المسار المخصص أو تعطيله.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                if (rawPrefixString.isEmpty()) {
                    Text(
                        text = "يرجى تحديد بادئة واحدة على الأقل في الإعدادات لإنشاء مسارات عمل مخصصة.",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    )
                } else {
                    rawPrefixString.forEach { prefix ->
                        val existingEntity = prefixPaths.find { it.prefix == prefix }
                        val initialPath = existingEntity?.customPath ?: ""
                        val initialEnabled = existingEntity?.enabled ?: false

                        PrefixPathRow(
                            prefix = prefix,
                            initialPath = initialPath,
                            initialEnabled = initialEnabled,
                            onSave = { newPath, isEnabled ->
                                viewModel.addPrefixPath(prefix, newPath, isEnabled)
                            }
                        )
                    }
                }
            }
        }

        // --- Folder Watcher (Long-term Monitoring) Section (Version 5.7) ---
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically, 
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh, 
                        contentDescription = "Folder Watcher", 
                        tint = if (isFolderWatching) Color(0xFF15803D) else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "🔄 مراقبة مجلد (طويل الأمد):",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isFolderWatching) Color(0xFFDCFCE7) else Color(0xFFF3F4F6),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isFolderWatching) "نشط ومراقب 🟢" else "متوقف ⏸️",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isFolderWatching) Color(0xFF15803D) else Color(0xFF4B5563)
                        )
                    }
                }

                Text(
                    text = "حدد مجلداً فرعياً أو مطلقاً ليقوم التطبيق بمراقبته كل ثانيتين في الخلفية. ستتم قفل ومعالجة أي ملف جديد يُضاف إليه تلقائياً دون تجميد الواجهة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = watchFolder,
                    onValueChange = { viewModel.watchFolder.value = it },
                    label = { Text("📁 مجلد المراقبة الفرعي أو المطلق") },
                    placeholder = { Text("مثال: watcher_inputs") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isFolderWatching
                )

                OutlinedTextField(
                    value = watchOutputPath,
                    onValueChange = { viewModel.watchOutputPath.value = it },
                    label = { Text("📁 مجلد تصدير المخرجات المخصص (اختياري)") },
                    placeholder = { Text("مثال: watcher_outputs (اتركه فارغاً للافتراضي)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isFolderWatching
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = watchExtensions,
                        onValueChange = { viewModel.watchExtensions.value = it },
                        label = { Text("📝 امتدادات المراقبة") },
                        placeholder = { Text("مثال: txt, py, html, md") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = !isFolderWatching
                    )

                    OutlinedTextField(
                        value = watchPrefix,
                        onValueChange = { viewModel.watchPrefix.value = it },
                        label = { Text("🏷️ بادئة تصفية التوجيهات") },
                        placeholder = { Text("مثال: @watcher") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = !isFolderWatching
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (isFolderWatching) {
                                viewModel.stopFolderWatching()
                            } else {
                                viewModel.startFolderWatching()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFolderWatching) Color(0xFFDC2626) else Color(0xFF15803D)
                        )
                    ) {
                        Icon(
                            imageVector = if (isFolderWatching) Icons.Default.Close else Icons.Default.PlayArrow,
                            contentDescription = "Toggle folder watching"
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isFolderWatching) "إيقاف المراقبة ⏹️" else "تشغيل ومراقبة المجلد ▶️",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Section 3: Template Management Card (Resolves Test 4.1 UI to toggle & manage templates)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.List, "Templates", tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "📋 إدارة القوالب النشطة لتوجيه الخرج التلقائي:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Text(
                    text = "ملاحظة: إذا pasted نصاً عادياً (لا يحتوي توجيهات برمجية)، فسيتم كتابة/إلحاق النص تلقائياً إلى كافة القوالب النشطة أدناه.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                // List Templates
                if (templates.isEmpty()) {
                    Text(
                        text = "لا توجد قوالب معرّفة حالياً.",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(8.dp)
                    )
                } else {
                    templates.forEach { t ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(t.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("المسار: ${t.path} | البادئة: ${t.prefix}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                Text(
                                    text = if (t.mode == "a") "الوضع: إضافة (Append)" else "الوضع: كتابة (Overwrite)",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = t.enabled,
                                    onCheckedChange = { viewModel.toggleTemplate(t) },
                                    modifier = Modifier.testTag("template_active_checkbox")
                                )
                                Text("تفعيل", fontSize = 11.sp)
                                IconButton(onClick = { viewModel.removeTemplate(t) }) {
                                    Icon(Icons.Default.Delete, "Delete template", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                // New Template Add Form
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("إضافة قالب جديد لوجهة الحفظ:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = newTmplName,
                        onValueChange = { newTmplName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("اسم القالب") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newTmplPath,
                        onValueChange = { newTmplPath = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("وجهة المسار النسبي (مثل text/hello.txt)") },
                        singleLine = true
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = newTmplPrefix,
                            onValueChange = { newTmplPrefix = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("البادئة") },
                            singleLine = true
                        )
                        Row(
                            modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(
                                onClick = { newTmplMode = "w" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (newTmplMode == "w") MaterialTheme.colorScheme.primary else Color.Gray
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("كتابة", fontSize = 10.sp)
                            }
                            Button(
                                onClick = { newTmplMode = "a" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (newTmplMode == "a") MaterialTheme.colorScheme.primary else Color.Gray
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("إلحاق", fontSize = 10.sp)
                            }
                        }
                    }
                    Button(
                        onClick = {
                            if (newTmplName.isNotBlank() && newTmplPath.isNotBlank()) {
                                viewModel.addTemplate(
                                    newTmplName.trim(),
                                    newTmplPath.trim(),
                                    newTmplPrefix.trim(),
                                    newTmplMode,
                                    true
                                )
                                newTmplName = ""
                                newTmplPath = ""
                                Toast.makeText(context, "تم حفظ قالب العمل وإضافته بنجاح", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("إدراج القالب")
                    }
                }
            }
        }

        // --- SECTION 4: Python Plugins Dynamic Management Panel ---
        val plugins by viewModel.pythonPlugins.collectAsStateWithLifecycle()

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build, 
                            contentDescription = "Python Plugins", 
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = "🐍 نظام إضافات بايثون الذكي (Plugins):",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    IconButton(
                        onClick = { viewModel.refreshPlugins() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Refresh, "Refresh Plugins", modifier = Modifier.size(16.dp))
                    }
                }

                Text(
                    text = "يبحث النظام تلقائياً في مجلد 'plugins' عن ملفات بايثون (.py) ويقوم بتحميلها أو تشغيلها عند بدء التشغيل تلقائياً لتوسيع ومتابعة مهام البناء والترجمة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (plugins.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "لم يعثر على أي إضافات نشطة داخل مجلد 'plugins' حالياً.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { viewModel.refreshPlugins() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                            ) {
                                Text("توليد وتحديث قائمة الإضافات الافتراضية", fontSize = 11.sp)
                            }
                        }
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        plugins.forEach { plugin ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = "🐍 ${plugin.name}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "Path: plugins/${plugin.name}",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.outline,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (plugin.isRunning) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                                Text("جاري التشغيل...", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
                                            } else {
                                                IconButton(
                                                    onClick = { viewModel.runPythonPlugin(plugin.name) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = "Run Plugin",
                                                        tint = Color(0xFF10B981),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Checkbox(
                                            checked = plugin.isAutoRunAtStartup,
                                            onCheckedChange = { viewModel.togglePluginAutoRun(plugin.name) },
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Text(
                                            text = "التشغيل والتحميل التلقائي عند بدء التطبيق",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (plugin.lastOutput.isNotBlank()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
                                                .padding(8.dp)
                                        ) {
                                            Text(
                                                text = "📟 مخرجات طرفية الملحق:",
                                                fontSize = 9.sp,
                                                color = Color(0xFF38BDF8),
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = plugin.lastOutput,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = Color(0xFFF8FAFC)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- TAB 3: SAVED WEB PAGES MERGER ----------------
@Composable
fun WebMergerTab(viewModel: SmartMonitorViewModel) {
    val htmlText by viewModel.htmlInput.collectAsStateWithLifecycle()
    val cssText by viewModel.cssInput.collectAsStateWithLifecycle()
    val jsText by viewModel.jsInput.collectAsStateWithLifecycle()
    val mergeResult by viewModel.webMergeResult.collectAsStateWithLifecycle()
    
    val fileMergeResult by viewModel.webPageFileMergeResult.collectAsStateWithLifecycle()
    val extractedDirectives by viewModel.extractedWebDirectives.collectAsStateWithLifecycle()
    val workspaceFiles by viewModel.workspaceFiles.collectAsStateWithLifecycle()
    
    val context = LocalContext.current
    var selectedFilePathInput by remember { mutableStateOf("") }
    
    // Find all HTML files inside workspace recursive
    val htmlFilesList = remember(workspaceFiles) {
        val list = mutableListOf<com.example.ui.viewmodel.FileItem>()
        fun traverse(item: com.example.ui.viewmodel.FileItem) {
            if (item.isDirectory) {
                item.children.forEach { traverse(it) }
            } else {
                if (item.name.lowercase().endsWith(".html") || item.name.lowercase().endsWith(".htm")) {
                    list.add(item)
                }
            }
        }
        workspaceFiles.forEach { traverse(it) }
        list
    }

    var selectedModeTab by remember { mutableStateOf(0) } // 0: File-based, 1: Text-based manual

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "🌐 دمج موارد صفحة الويب وتنسيقها (Saved Web Page Parser)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "يقوم تلقائياً بدمج كود الـ CSS والـ JS الخارجي بداخل قالب الصفحة للحصول على ملف HTML موحّد غير متصل، ويستخرج أي توجيهات برمجية مسبقة مضافة فيها.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Horizontal toggle selection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { selectedModeTab = 0 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedModeTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (selectedModeTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("🌐 معالجة ملف ويب محفوظ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { selectedModeTab = 1 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedModeTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (selectedModeTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("📝 دمج يدوي بالنسخ واللصق", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (selectedModeTab == 0) {
            // HTML File selector GUI
            Text("اختر ملف HTML من مساحة العمل للمعالجة المتقدمة:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            
            if (htmlFilesList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا توجد ملفات تفاعلية بتنسيق HTML في مساحة العمل حالياً. يرجى إنشاء أو استيراد ملف HTML أولاً.",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp), RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("انقر لتحديد ملف HTML للتحليل الفوري دُفعة واحدة ومكافأة المطور إدريس:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        htmlFilesList.forEach { file ->
                            val isSelected = selectedFilePathInput == file.relativePath
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedFilePathInput = file.relativePath }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Done, "HTML Icon", tint = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF64748B), modifier = Modifier.size(12.dp))
                                    Text(
                                        text = file.name,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = selectedFilePathInput,
                onValueChange = { selectedFilePathInput = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("أدخل مسار ملف HTML في مساحة العمل (مثال: my_page.html)...") },
                label = { Text("مسار ملف HTML المختار") },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            )

            Button(
                onClick = {
                    if (selectedFilePathInput.isBlank()) {
                        Toast.makeText(context, "الرجاء تحديد أو إدخال مسار ملف HTML أولاً!", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.selectAndProcessWebPageFile(selectedFilePathInput)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, "Process Web Page")
                Spacer(modifier = Modifier.width(6.dp))
                Text("دمج وتنسيق صفحة الويب المحددة")
            }

            // Confirmation dialogue interface for version 5.8
            if (fileMergeResult != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                     colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                     border = BorderStroke(1.5.dp, Color(0xFF38BDF8)),
                     modifier = Modifier.fillMaxWidth()
                ) {
                     Column(
                         modifier = Modifier.padding(14.dp),
                         verticalArrangement = Arrangement.spacedBy(10.dp)
                     ) {
                         Row(
                             modifier = Modifier.fillMaxWidth(),
                             horizontalArrangement = Arrangement.SpaceBetween,
                             verticalAlignment = Alignment.CenterVertically
                         ) {
                             Text(
                                 text = "📝 تأكيد استخراج وحفظ الكتل البرمجية المكتشفة",
                                 fontWeight = FontWeight.Bold,
                                 color = Color.White,
                                 fontSize = 13.sp
                             )
                             Text(
                                 text = "(@builder أو غيرها)",
                                 fontSize = 10.sp,
                                 color = Color(0xFF38BDF8)
                             )
                         }

                         Text(
                             text = "تم العثور على ${extractedDirectives.size} كتلة توجيهات برمجية قابلة للاستخراج من الصفحة الموصولة. مجلد الموارد المكتشف: ${fileMergeResult?.resourceFolder?.substringAfterLast('/') ?: "لا يوجد"}",
                             fontSize = 11.sp,
                             color = Color(0xFF94A3B8)
                         )

                         if (extractedDirectives.isEmpty()) {
                             Text(
                                 text = "⚠️ لم يتم اكتشاف أي توجيهات برمجية مسبقة بالملف. سيتم تصدير ملف HTML المدمج الموحد فقط عند الحفظ.",
                                 color = Color(0xFFFBBF24),
                                 fontSize = 11.sp,
                                 fontWeight = FontWeight.Bold
                             )
                         } else {
                             Column(
                                 verticalArrangement = Arrangement.spacedBy(8.dp),
                                 modifier = Modifier.fillMaxWidth()
                             ) {
                                 extractedDirectives.forEachIndexed { index, directive ->
                                     Card(
                                         colors = CardDefaults.cardColors(
                                             containerColor = if (directive.isSelected) Color(0xFF1E293B) else Color(0xFF0F172A)
                                         ),
                                         border = BorderStroke(
                                             1.dp,
                                             if (directive.isSelected) Color(0xFF38BDF8) else Color(0xFF334155)
                                         ),
                                         modifier = Modifier
                                             .fillMaxWidth()
                                             .clickable { viewModel.toggleExtractedWebDirective(index) }
                                     ) {
                                         Row(
                                             modifier = Modifier.padding(8.dp),
                                             verticalAlignment = Alignment.CenterVertically,
                                             horizontalArrangement = Arrangement.spacedBy(8.dp)
                                         ) {
                                             Checkbox(
                                                 checked = directive.isSelected,
                                                 onCheckedChange = { viewModel.toggleExtractedWebDirective(index) }
                                             )
                                             Column(modifier = Modifier.weight(1f)) {
                                                 Text(
                                                     text = "مسار الحفظ المنسق: ${directive.path}",
                                                     color = Color.White,
                                                     fontWeight = FontWeight.Bold,
                                                     fontSize = 12.sp
                                                 )
                                                 Row(
                                                     horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                     modifier = Modifier.fillMaxWidth()
                                                 ) {
                                                     Text("البادئة: ${directive.prefix}", fontSize = 10.sp, color = Color(0xFF64748B))
                                                     Text("الوضع: ${if (directive.mode == "a") "إلحاق (append)" else "كتابة جديدة (overwrite)"}", fontSize = 10.sp, color = Color(0xFF64748B))
                                                     Text("الحجم: ${directive.content.length} حرفاً", fontSize = 10.sp, color = Color(0xFF38BDF8))
                                                 }
                                             }
                                         }
                                     }
                                 }
                             }
                         }

                         Row(
                             modifier = Modifier.fillMaxWidth(),
                             horizontalArrangement = Arrangement.spacedBy(8.dp)
                         ) {
                             Button(
                                 onClick = { viewModel.cancelWebPageMerge() },
                                 colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                 modifier = Modifier.weight(1f)
                             ) {
                                 Text("إلغاء العملية", color = Color.White, fontSize = 11.sp)
                             }

                             Button(
                                 onClick = { viewModel.saveConfirmedWebDirectives() },
                                 colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                 modifier = Modifier.weight(1f)
                             ) {
                                 Text("💾 تأكيد وحفظ البرمجيات المكتشفة", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                             }
                         }
                     }
                }
            }

        } else {
            // Existing Paste-based Text Merging
            OutlinedTextField(
                value = htmlText,
                onValueChange = { viewModel.htmlInput.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                placeholder = { Text("أدخل ملف HTML الأساسي هنا...") },
                label = { Text("كود الـ HTML") },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            )

            OutlinedTextField(
                value = cssText,
                onValueChange = { viewModel.cssInput.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                placeholder = { Text("كود الـ CSS الخارجي لدمجه تلقائياً (مماثل لمحتويات ملف style.css)...") },
                label = { Text("ملف الأنماط CSS خارجي (اختياري)") },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            )

            OutlinedTextField(
                value = jsText,
                onValueChange = { viewModel.jsInput.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                placeholder = { Text("سكريبت JS لدمجه تلقائياً...") },
                label = { Text("سكريبت JS خارجي (اختياري)") },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        // Load demo files
                        viewModel.htmlInput.value = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <title>Demo Watch App</title>
                            <link rel="stylesheet" href="style.css">
                        </head>
                        <body>
                            <h1>مرحباً بالتوجيه البرمجي المدمج</h1>
                            <p>هذه صفحة تجريبية لدمج الموارد</p>
                            <!-- @builder:file web_output/success.txt -->
                            <!-- @builder:mode overwrite -->
                            مرحبًا بك من كود توجيهات الويب المستخرج والمنسق!
                            <!-- @builder:end -->
                            <script src="script.js"></script>
                        </body>
                        </html>
                        """.trimIndent()
                        
                        viewModel.cssInput.value = """
                        body {
                            background-color: #0f172a;
                            color: white;
                            font-family: sans-serif;
                            padding: 20px;
                        }
                        h1 { color: #38bdf8; }
                        """.trimIndent()
                        viewModel.jsInput.value = "console.log('Daemon web merger is ready.');"
                        Toast.makeText(context, "تم تحميل كود الويب التجريبي", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Icon(Icons.Default.Done, "Load Demo")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تحميل كود تجريبي")
                }

                Button(
                    onClick = { viewModel.mergeOfflineWebPage() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Info, "Merge and prettify")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("دمج وتنسيق الصفحة")
                }
            }

            if (mergeResult.isNotBlank()) {
                Text("الكود النهائي المدمج والمنسق:", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = mergeResult,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                    )
                )
            }
        }
    }
}

// ---------------- TAB 4: PROJECT INTEGRATION SCANNERS ----------------
@Composable
fun ProjectCompanionTab(viewModel: SmartMonitorViewModel) {
    val extentToScan by viewModel.extensionsToScan.collectAsStateWithLifecycle()
    val packOutput by viewModel.projectPackOutput.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "📂 رفيق وحزم المشروع (Project Companion Packager)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "قم بفحص مساحة العمل بالكامل وتصدير كافة ملفات الأكواد الخاصة بك مجمعة داخل قالب حزمة توجيهات موحدة @builder:file بضغطة زر واحدة. هذا يساعدك على نسخ شجرة كود مشروعك بالكامل ونقلها بسهولة تامة لأي حوار/LLM كملف نسخة إلكترونية واحدة!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedTextField(
            value = extentToScan,
            onValueChange = { viewModel.extensionsToScan.value = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("الامتدادات المطلوبة للحزم (تفصل بينها فاصلة)") },
            placeholder = { Text("kt, py, html, css, js, json, xml") },
            singleLine = true
        )

        Button(
            onClick = { viewModel.packFolderProject() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Share, "Pack workspace files")
            Spacer(modifier = Modifier.width(8.dp))
            Text("مسح، تجميع وتصدير مساحة العمل الحالية")
        }

        if (packOutput.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("حزمة مشروعك الجاهزة للنسخ:", fontWeight = FontWeight.Bold)
                val clipboardManager = LocalClipboardManager.current
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(packOutput))
                        Toast.makeText(context, "تم نسخ الحزمة البرمجية بنجاح!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.Share, "Copy pack")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("نسخ الحزمة بالكامل")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.savePackAsWorkspaceFile() },
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Done, "Save File", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("حفظ كملف مشروع", fontSize = 11.sp)
                }

                Button(
                    onClick = {
                        viewModel.setEditorText(packOutput)
                        Toast.makeText(context, "تم إدخال الحزمة في المترجم المباشر بنجاح ويتم تحويلك الآن!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Icon(Icons.Default.Build, "Build", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تغذية المترجم المباشر", fontSize = 11.sp)
                }
            }
            
            OutlinedTextField(
                value = packOutput,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF0F1728),
                    unfocusedContainerColor = Color(0xFF0F1728)
                )
            )
        }
    }
}

@Composable
fun LogLineView(log: LogEntity) {
    val color = when (log.level) {
        "SUCCESS" -> Color(0xFF4ADE80)
        "WARN" -> Color(0xFFFBBF24)
        "ERROR" -> Color(0xFFF87171)
        else -> Color(0xFF38BDF8)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "[${log.level}]",
            color = color,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
        Text(
            text = log.message,
            color = Color(0xFFE2E8F0),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun ConsoleOverlay(
    viewModel: SmartMonitorViewModel,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        if (!isExpanded) {
            // Floating Console Toggle Button
            FloatingActionButton(
                onClick = { isExpanded = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 16.dp, vertical = 76.dp) // Offset vertically to not overlap bottom bar icons
                    .testTag("floating_console_trigger"),
                containerColor = Color(0xFF1E293B), // Sleek charcoal contrast
                contentColor = Color.White
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Open Console",
                        tint = Color(0xFF4ADE80),
                        modifier = Modifier.size(18.dp)
                    )
                    Text("الكونسول النشط", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        } else {
            // Expanded Console Overlay Panel
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(290.dp)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .clickable(enabled = false) {}, // prevent click-through
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF0F172A) // Slate slate terminal canvas
                ),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                ) {
                    // Header Area
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = "Terminal",
                                tint = Color(0xFF4ADE80),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "شاشة مراقبة العمليات والحافظة النشطة",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Undo Last Action button
                            IconButton(
                                onClick = { 
                                    viewModel.undoLastAction() 
                                    Toast.makeText(context, "جاري استعادة الحالة السابقة...", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("console_undo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Undo Last Action",
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            
                            // Clear logs
                            IconButton(
                                onClick = { viewModel.clearAllLogs() },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("console_clear_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Clear logs",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            
                            // Close overlay button
                            IconButton(
                                onClick = { isExpanded = false },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("console_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close overlay",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    
                    Divider(
                        color = Color(0xFF334155),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    
                    // Console Output Area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFF030712), shape = RoundedCornerShape(6.dp))
                            .padding(6.dp)
                    ) {
                        val consoleLogs = logs
                        if (consoleLogs.isEmpty()) {
                            Text(
                                text = "لاشيء حالياً. قم بنسخ كود توجيهي ليتم معالجته وتوثيقه هنا مباشرة...",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(consoleLogs) { log ->
                                    LogLineView(log = log)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PrefixPathRow(
    prefix: String,
    initialPath: String,
    initialEnabled: Boolean,
    onSave: (String, Boolean) -> Unit
) {
    var pathText by remember(prefix, initialPath) { mutableStateOf(initialPath) }
    var isEnabled by remember(prefix, initialEnabled) { mutableStateOf(initialEnabled) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Checkbox next to each path input to enable or disable its use
        Checkbox(
            checked = isEnabled,
            onCheckedChange = { checked ->
                isEnabled = checked
                onSave(pathText, checked)
            },
            modifier = Modifier.testTag("checkbox_${prefix.replace("@", "")}")
        )

        // Prefix Label
        Text(
            text = prefix,
            fontWeight = FontWeight.Bold,
            color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.width(80.dp),
            fontSize = 13.sp
        )

        // Custom Path Input
        OutlinedTextField(
            value = pathText,
            onValueChange = { newValue ->
                pathText = newValue
                onSave(newValue, isEnabled)
            },
            modifier = Modifier
                .weight(1f)
                .testTag("input_${prefix.replace("@", "")}"),
            placeholder = { Text("المسار المخصص") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            enabled = isEnabled
        )
    }
}

@Composable
fun IdrisEncouragementCard(
    viewModel: SmartMonitorViewModel,
    modifier: Modifier = Modifier
) {
    val quotes = listOf(
        "«إن الهندسة ليست مجرد بناء كود، بل هي تجسيد للحلول الذكية التي تبسط حياة الناس وتسعدهم.»",
        "«كل سطر برمجيات تكتبه اليوم هو لبنة في صرح غدٍ أفضل وأذكى. استمر في الشغف والابتكار!»",
        "«المبرمجون هم مهندسو المستقبل الفعليين؛ كود اليوم هو واقع الغد. فلتجعل كودك نقياً ومبدعاً!»",
        "«التراجع هو أمان الأقوياء، والتقدم هو درب المبتكرين. بيئتك البرمجية محمية دائماً بأقصى درجات الذكاء برعاية المطور إدريس!»",
        "«لا تهابوا الأخطاء في الكود؛ فكل خطأ يتم حله هو خطوة للأمام نحو الاحتراف والتألق الهندسي الكامل.»"
    )

    var currentQuoteIndex by remember { mutableStateOf(0) }
    val currentQuote = quotes[currentQuoteIndex]

    // Breathing pulse animation for premium feel
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val cardColorAlpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    val starScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "star"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = cardColorAlpha)
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Owner and branding
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Developer Badge",
                        tint = Color(0xFFF59E0B), // Glowing golden star
                        modifier = Modifier
                            .size(24.dp)
                            .scale(starScale)
                    )
                    Column {
                        Text(
                            text = "منصة المبتكر المطور 🔨",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "المهندس إدريس يوسف المداني",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = Color(0xFFB45309) // Deep Amber
                        )
                    }
                }

                // Decorative Badge
                Box(
                    modifier = Modifier
                        .background(Color(0xFFFEF3C7), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "محفّز ذكي ✨",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD97706)
                    )
                }
            }

            // Beautiful Quote block
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        RoundedCornerShape(10.dp)
                    )
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = currentQuote,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Audio & Encouragement triggers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speak Current Quote via TTS
                Button(
                    onClick = {
                        val speechText = "$currentQuote . نصيحة من المطور والمهندس إدريس يوسف المداني"
                        viewModel.speakEncouragingWord(speechText)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB45309) // Amber/Gold tone
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Speak quote"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("استمع للكلمة 🎤", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                // Next Quote + speak
                OutlinedButton(
                    onClick = {
                        currentQuoteIndex = (currentQuoteIndex + 1) % quotes.size
                        val nextQuote = quotes[currentQuoteIndex]
                        viewModel.speakEncouragingWord("إليك الهمة العالية: $nextQuote")
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Next encouragement",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("مقالة تشجيعية ✨", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
