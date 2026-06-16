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
        // Workspace Directory Metadata Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
            )
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
                    Text(
                        text = "📁 مجلد الحفظ والمحاكاة النشط:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    IconButton(onClick = { viewModel.refreshWorkspaceFiles() }) {
                        Icon(Icons.Default.Refresh, "Refresh Files")
                    }
                }
                Text(
                    text = viewModel.repository.getWorkspaceDirectory().absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
                            onDelete = { viewModel.deleteWorkspaceFile(it) }
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
    onDelete: (FileItem) -> Unit
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
        
        if (fileItem.isDirectory && expanded) {
            fileItem.children.forEach { child ->
                FileNodeItem(
                    fileItem = child,
                    depth = depth + 1,
                    onPreview = onPreview,
                    onDelete = onDelete
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
    }
}

// ---------------- TAB 3: SAVED WEB PAGES MERGER ----------------
@Composable
fun WebMergerTab(viewModel: SmartMonitorViewModel) {
    val htmlText by viewModel.htmlInput.collectAsStateWithLifecycle()
    val cssText by viewModel.cssInput.collectAsStateWithLifecycle()
    val jsText by viewModel.jsInput.collectAsStateWithLifecycle()
    val mergeResult by viewModel.webMergeResult.collectAsStateWithLifecycle()
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
