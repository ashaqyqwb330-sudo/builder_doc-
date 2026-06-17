package com.example.ui.viewmodel

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.PrefixPathEntity
import com.example.data.database.TemplateEntity
import com.example.data.repository.SmartMonitorRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import android.media.MediaPlayer
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader

class SmartMonitorViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val repository = SmartMonitorRepository(
        context = application,
        templateDao = db.templateDao(),
        prefixPathDao = db.prefixPathDao(),
        logDao = db.logDao(),
        clipboardOperationDao = db.clipboardOperationDao()
    )
    private var tts: android.speech.tts.TextToSpeech? = null

    // Prefix Settings
    private val _directivePrefixes = MutableStateFlow(listOf("@builder", "@watcher", "@deploy"))
    val directivePrefixes = _directivePrefixes.asStateFlow()

    // Workspace Files State
    private val _workspaceFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val workspaceFiles = _workspaceFiles.asStateFlow()

    // Reactive states from DB
    val templates: StateFlow<List<TemplateEntity>> = repository.templates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val prefixPaths: StateFlow<List<PrefixPathEntity>> = repository.prefixPaths
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val logs = repository.logs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val clipboardOperations = repository.clipboardOperations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Tab state
    private val _currentTab = MutableStateFlow(0)
    val currentTab = _currentTab.asStateFlow()

    // Clipboard checking flag
    private val _isClipboardMonitoring = MutableStateFlow(false)
    val isClipboardMonitoring = _isClipboardMonitoring.asStateFlow()

    // Text editor input
    private val _editorText = MutableStateFlow("")
    val editorText = _editorText.asStateFlow()

    // File content preview in explorer
    private val _previewedFile = MutableStateFlow<Pair<String, String>?>(null) // Name to Content
    val previewedFile = _previewedFile.asStateFlow()

    // Web Page Offline merger states
    val htmlInput = MutableStateFlow("")
    val cssInput = MutableStateFlow("")
    val jsInput = MutableStateFlow("")
    private val _webMergeResult = MutableStateFlow("")
    val webMergeResult = _webMergeResult.asStateFlow()

    // Project Companion scanner states
    val extensionsToScan = MutableStateFlow("kt, py, html, css, js, json, xml")
    private val _projectPackOutput = MutableStateFlow("")
    val projectPackOutput = _projectPackOutput.asStateFlow()

    // Single File processing input path
    val fileProcessInputPath = MutableStateFlow("test_input.txt")

    // --- Folder Watcher (Long-Term Monitoring) States ---
    private val prefs = application.getSharedPreferences("smart_monitor_prefs", Context.MODE_PRIVATE)
    val watchFolder = MutableStateFlow(prefs.getString("watch_folder", "watcher_inputs") ?: "watcher_inputs")
    val watchExtensions = MutableStateFlow(prefs.getString("watch_extensions", "txt, py, html, md") ?: "txt, py, html, md")
    val watchPrefix = MutableStateFlow(prefs.getString("watch_prefix", "@watcher") ?: "@watcher")
    private val _isFolderWatching = MutableStateFlow(prefs.getBoolean("watch_enabled", false))
    val isFolderWatching = _isFolderWatching.asStateFlow()
    private var watchJob: kotlinx.coroutines.Job? = null

    val watchOutputPath = MutableStateFlow(prefs.getString("watch_output_path", "") ?: "")

    // Custom path navigation state variables
    val customWorkspacePath = MutableStateFlow(prefs.getString("custom_workspace_path", repository.getWorkspaceDirectory().absolutePath) ?: repository.getWorkspaceDirectory().absolutePath)

    // Cumulative clipboard monitoring state variables
    val isCumulativeClipboardEnabled = MutableStateFlow(prefs.getBoolean("cumulative_clip_enabled", false))
    val cumulativeClipboardBuffer = MutableStateFlow("")

    // Python Plugins state variables
    data class PythonPlugin(
        val name: String,
        val file: File,
        val isAutoRunAtStartup: Boolean = false,
        val isRunning: Boolean = false,
        val lastOutput: String = ""
    )
    val pythonPlugins = MutableStateFlow<List<PythonPlugin>>(emptyList())

    // --- Evidence Music & Zamil Player System States ---
    data class AudioTrack(
        val name: String,
        val fileName: String,
        val isAsset: Boolean = false,
        val absolutePath: String = "",
        val isSimulation: Boolean = false
    )

    val musicTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val currentPlayingTrack = MutableStateFlow<AudioTrack?>(null)
    val isMusicPlaying = MutableStateFlow(false)
    val musicPlaybackPosition = MutableStateFlow(0) // in seconds
    val musicPlaybackDuration = MutableStateFlow(0) // in seconds
    val isMusicLoopActive = MutableStateFlow(false)
    val musicVolume = MutableStateFlow(1.0f) // 0f to 1f
    val visualizerWaves = MutableStateFlow<List<Float>>(List(16) { 0.1f })

    private var mediaPlayer: MediaPlayer? = null
    private var musicJob: kotlinx.coroutines.Job? = null

    init {
        // Read any custom saved workspace path to initialize the repository with it
        val savedWorkspacePath = prefs.getString("custom_workspace_path", null)
        if (savedWorkspacePath != null) {
            repository.setWorkspaceDirectory(savedWorkspacePath)
        }

        // Initialize user music tracks and scan files
        refreshMusicTracks()

        // Pre-populate some demo templates if database is empty
        viewModelScope.launch {
            repository.templates.first().let { current ->
                if (current.isEmpty()) {
                    repository.insertTemplate(TemplateEntity(name = "سجل المعالجة اليومي", path = "logs/daily_log.txt", prefix = "@builder", mode = "a", enabled = true))
                    repository.insertTemplate(TemplateEntity(name = "نسخة احتياطية سريعة", path = "backup/snapshot.txt", prefix = "@builder", mode = "w", enabled = false))
                }
            }
            repository.prefixPaths.first().let { current ->
                if (current.isEmpty()) {
                    repository.insertPrefixPath(PrefixPathEntity("@builder", "src", true))
                    repository.insertPrefixPath(PrefixPathEntity("@watcher", "watcher_outputs", true))
                    repository.insertPrefixPath(PrefixPathEntity("@deploy", "deployment", false))
                }
            }
            refreshWorkspaceFiles()
            repository.log("📊 تم بدء نظام التشغيل والمراقبة بنجاح.", "INFO")
            
            // Auto resume folder watching if it was active
            if (prefs.getBoolean("watch_enabled", false)) {
                startFolderWatching()
            }

            // Sync Python Plugins on Startup and Run authorized Auto-Run ones
            try {
                refreshPlugins()
                runAutoPluginsOnStartup()
            } catch(e: Exception) {
                repository.log("❌ فشل تشغيل نظام الإضافات التلقائي: ${e.message}", "ERROR")
            }
        }

        // Initialize TextToSpeech engine
        tts = android.speech.tts.TextToSpeech(application) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                tts?.language = java.util.Locale("ar")
                speakEncouragingWord("أهلاً بك في تطبيق المراقب الذكي، يرحب بك المطور والمهندس إدريس يوسف المداني ويتمنى لك تجربة برمجية ممتعة ومميزة!")
            }
        }
    }

    fun setTab(index: Int) {
        _currentTab.value = index
    }

    fun setEditorText(text: String) {
        _currentTab.value = 0 // Move focus to compiler view
        _editorText.value = text
    }

    fun refreshWorkspaceFiles() {
        val root = repository.getWorkspaceDirectory()
        val items = mutableListOf<FileItem>()
        if (root.exists() && root.isDirectory) {
            root.listFiles()?.forEach { file ->
                items.add(buildFileTree(file, root))
            }
        }
        _workspaceFiles.value = items.sortedBy { !it.isDirectory }
    }

    private fun buildFileTree(file: File, base: File): FileItem {
        val relativePath = file.relativeTo(base).path.replace('\\', '/')
        val children = if (file.isDirectory) {
            file.listFiles()?.map { buildFileTree(it, base) }?.sortedBy { !it.isDirectory } ?: emptyList()
        } else {
            emptyList()
        }
        return FileItem(
            name = file.name,
            relativePath = relativePath,
            absolutePath = file.absolutePath,
            isDirectory = file.isDirectory,
            size = if (file.isFile) file.length() else 0,
            children = children
        )
    }

    // Direct Compiler Actions
    fun compilePastedText() {
        val text = _editorText.value
        if (text.isBlank()) {
            viewModelScope.launch {
                repository.log("⚠️ حقل المحرر فارغ للتنفيذ.", "WARN")
            }
            return
        }
        viewModelScope.launch {
            val results = repository.processText(text, _directivePrefixes.value)
            refreshWorkspaceFiles()
            if (results.any { !it.contains("فشل") && !it.contains("لا توجد") }) {
                speakEncouragingWord("تم معالجة التوجيهات وترتيبها في مجلدات العمل بنجاح ونقاء متميز! المطور إدريس يحيي همتكم العالية!")
            }
        }
    }

    fun readAndProcessClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val pasteText = clip.getItemAt(0).text?.toString() ?: ""
            if (pasteText.isNotBlank()) {
                if (isCumulativeClipboardEnabled.value) {
                    val current = cumulativeClipboardBuffer.value
                    val delimiter = if (current.isNotEmpty() && !current.endsWith("\n")) "\n" else ""
                    cumulativeClipboardBuffer.value = current + delimiter + pasteText
                    viewModelScope.launch {
                        repository.log("📥 [تراكم الحافظة يدوياً] تم حشد الدفعة المنقولة بنجاح (الحجم الكلي: ${cumulativeClipboardBuffer.value.length} حرفاً).", "INFO")
                    }
                } else {
                    _editorText.value = pasteText
                    viewModelScope.launch {
                        repository.log("📋 تم جلب النص من الحافظة تلقائياً (الطول: ${pasteText.length} حرفاً).", "INFO")
                        val results = repository.processText(pasteText, _directivePrefixes.value)
                        refreshWorkspaceFiles()
                        if (results.any { !it.contains("فشل") && !it.contains("لا توجد") }) {
                            speakEncouragingWord("تم استخراج التوجيهات وحزم العمل بنجاح وبسرعة فائقة! أحسنت يا مهندس المستقبل!")
                        }
                    }
                }
            } else {
                viewModelScope.launch { repository.log("📋 الحافظة فارغة حالياً.", "WARN") }
            }
        } else {
            viewModelScope.launch { repository.log("📋 لم يتم العثور على قيم في حافظة النظام.", "WARN") }
        }
    }

    fun toggleClipboardMonitoring(context: Context) {
        _isClipboardMonitoring.value = !_isClipboardMonitoring.value
        val intent = android.content.Intent(context, com.example.service.ClipboardWatcherService::class.java)
        viewModelScope.launch {
            if (_isClipboardMonitoring.value) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                repository.log("▶️ بدء المراقبة النشطة عبر خدمة الخلفية (Foreground Service).", "INFO")
            } else {
                context.stopService(intent)
                repository.log("⏸️ تم إيقاف خدمة مراقبة الحافظة في الخلفية.", "INFO")
                lastClipboardHash = 0
            }
        }
    }

    fun undoLastAction() {
        viewModelScope.launch {
            val success = repository.undoLastAction()
            if (success) {
                refreshWorkspaceFiles()
                speakEncouragingWord("تم التراجع عن آخر عملية وحذف الملفات الدخيلة بنجاح. السلامة والأمان مضمون في بيئتك البرمجية!")
            }
        }
    }

    // Execute monitoring step (called e.g., on app resume or manual refresh)
    fun checkClipboardForDirectives(context: Context) {
        if (_isClipboardMonitoring.value) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: ""
                // Avoid infinite feedback if clipboard hasn't changed
                if (text.isNotBlank() && text.hashCode() != lastClipboardHash) {
                    lastClipboardHash = text.hashCode()
                    
                    if (isCumulativeClipboardEnabled.value) {
                        val current = cumulativeClipboardBuffer.value
                        val delimiter = if (current.isNotEmpty() && !current.endsWith("\n")) "\n" else ""
                        cumulativeClipboardBuffer.value = current + delimiter + text
                        viewModelScope.launch {
                            repository.log("📥 [تراكم الحافظة] تمت إضافة الكود المنسوخ المتراكم بنجاح (الحجم الكلي: ${cumulativeClipboardBuffer.value.length} حرفاً).", "INFO")
                        }
                    } else {
                        _editorText.value = text
                        viewModelScope.launch {
                            repository.log("🔄 كشف تغيير في الحافظة، جاري التوزيع في التوجيهات...", "INFO")
                            repository.processText(text, _directivePrefixes.value)
                            refreshWorkspaceFiles()
                        }
                    }
                }
            }
        }
    }

    fun toggleCumulativeClipboard() {
        val enabled = !isCumulativeClipboardEnabled.value
        isCumulativeClipboardEnabled.value = enabled
        prefs.edit().putBoolean("cumulative_clip_enabled", enabled).apply()
        viewModelScope.launch {
            repository.log("⚙️ وضع التراكم الذكي للححافظة: ${if (enabled) "مُفعّل 🟢 (سيتم تجميع أجزاء الكود ونسخ الدفعات المتعاقبة دون الكتابة فوق بعضها)" else "غير مُفعّل ⏸️ (الوضع العادي لدمج النسخة مباشرة)"}", "INFO")
        }
    }

    fun clearCumulativeClipboardBuffer() {
        cumulativeClipboardBuffer.value = ""
        viewModelScope.launch {
            repository.log("🧹 تم مسح حوض النصوص المتراكمة للحفظ بنجاح.", "INFO")
        }
    }

    fun processCumulativeClipboardBuffer() {
        val text = cumulativeClipboardBuffer.value
        if (text.isBlank()) {
            viewModelScope.launch {
                repository.log("⚠️ حزمة التراكم فارغة! لا توجد كتل برمجية لمعالجتها.", "WARN")
            }
            return
        }
        viewModelScope.launch {
            repository.log("🚀 جاري البدء في المعالجة الكلية التراكمية للحزمة المحشورة (عدد الأسطر: ${text.lines().size}، الحجم: ${text.length} حرفاً)...", "INFO")
            val results = repository.processText(text, _directivePrefixes.value)
            refreshWorkspaceFiles()
            val validCount = results.count { !it.contains("فشل") && !it.contains("لا توجد") }
            if (validCount > 0) {
                speakEncouragingWord("أحسنت! تم معالجة $validCount توجيهاً برمجياً تراكمياً وبنجاح فائق!")
                repository.log("✨ تمت معالجة الحزمة المتراكمة بنجاح. تم مسح الحوض مؤقتاً لتسهيل الحشد القادم.", "SUCCESS")
                cumulativeClipboardBuffer.value = "" // clear after processing successfully!
            } else {
                repository.log("⚠️ تم معالجة الحزمة، ولكن لم يُكشف فيها عن أي كتل توجيهية صالحة بالبادئات المعروفة.", "WARN")
            }
        }
    }

    fun processDirectivesFromFile(relativePath: String) {
        viewModelScope.launch {
            val root = repository.getWorkspaceDirectory()
            val fileObj = File(root, relativePath)
            if (fileObj.exists()) {
                repository.log("📄 معالجة ملف كمصدر توجيهات يدوية: جاري قراءة الملف $relativePath...", "INFO")
                val text = fileObj.readText(Charsets.UTF_8)
                val results = repository.processText(text, _directivePrefixes.value)
                val count = results.count { !it.contains("فشل") && !it.contains("لا توجد") }
                if (count > 0) {
                    speakEncouragingWord("تم معالجة التوجيهات من الملف $relativePath بنجاح فائق وتلقائي!")
                } else {
                    repository.log("⚠️ لم يعثر على توجيهات برمجية صالحة داخل الملف $relativePath.", "WARN")
                }
                refreshWorkspaceFiles()
            } else {
                repository.log("❌ فشل المعالجة: الملف $relativePath غير موجود.", "ERROR")
            }
        }
    }

    fun selectWebPageAndSwitchTab(relativePath: String) {
        setTab(3) // Switch to web merger Tab 3
        selectAndProcessWebPageFile(relativePath)
    }

    private var lastClipboardHash: Int = 0

    // Database configurations updating
    fun addTemplate(name: String, path: String, prefix: String, mode: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.insertTemplate(TemplateEntity(
                name = name,
                path = path,
                prefix = prefix,
                mode = mode,
                enabled = enabled
            ))
            repository.log("➕ تم إضافة القالب الجديد: $name ($path)", "INFO")
        }
    }

    fun toggleTemplate(template: TemplateEntity) {
        viewModelScope.launch {
            repository.setTemplateEnabled(template.id, !template.enabled)
            repository.log("🔌 تم تغيير حالة قالب '${template.name}' إلى: ${if (!template.enabled) "نشط" else "معطل"}", "INFO")
        }
    }

    fun removeTemplate(template: TemplateEntity) {
        viewModelScope.launch {
            repository.deleteTemplate(template)
            repository.log("🗑️ تم حذف القالب: ${template.name}", "INFO")
        }
    }

    fun updatePrefixesList(spaceString: String) {
        val list = spaceString.split(" ").map { it.trim() }.filter { it.isNotBlank() }
        if (list.isNotEmpty()) {
            _directivePrefixes.value = list
            viewModelScope.launch {
                repository.log("🏷️ تم تحديث البادئات المدعومة للمراقبة: ${list.joinToString(", ")}", "SUCCESS")
            }
        }
    }

    fun addPrefixPath(prefix: String, customPath: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.insertPrefixPath(PrefixPathEntity(prefix, customPath, enabled))
            repository.log("📂 ربط البادئة $prefix بالمجلد المخصص: $customPath (نشط: $enabled)", "INFO")
        }
    }

    fun deletePrefixPath(entity: PrefixPathEntity) {
        viewModelScope.launch {
            repository.deletePrefixPath(entity)
            repository.log("🗑️ تم إزالة تخصيص مسار البادئة ${entity.prefix}", "INFO")
        }
    }

    // Single File manual testing
    fun processManualSingleFile() {
        viewModelScope.launch {
            val fileName = fileProcessInputPath.value
            val root = repository.getWorkspaceDirectory()
            val fileObj = File(root, fileName)
            if (fileObj.exists()) {
                repository.log("📄 معالجة ملف واحد: جاري قراءة الملف $fileName...", "INFO")
                val text = fileObj.readText(Charsets.UTF_8)
                repository.processText(text, _directivePrefixes.value)
                refreshWorkspaceFiles()
            } else {
                repository.log("❌ فشل معالجة ملف واحد: الملف $fileName غير موجود بداخل مجلد الحفظ $root.", "ERROR")
            }
        }
    }

    // Process Workspace Folder
    fun processSpecificFolder(folderName: String) {
        viewModelScope.launch {
            val root = repository.getWorkspaceDirectory()
            val targetFolder = File(root, folderName)
            if (targetFolder.exists() && targetFolder.isDirectory) {
                repository.processFolder(targetFolder, listOf("txt", "py", "html", "css", "js", "xml"), _directivePrefixes.value)
                refreshWorkspaceFiles()
            } else {
                repository.log("❌ فشل معالجة مجلد: المجلد $folderName غير موجود داخل مجلد الحفظ.", "ERROR")
            }
        }
    }

    // Offline saved web page merger and format
    fun mergeOfflineWebPage() {
        val html = htmlInput.value
        val css = cssInput.value
        val js = jsInput.value

        if (html.isBlank()) {
            viewModelScope.launch {
                repository.log("❌ فشل دمج الصفحة: محتوى ملف HTML فارغ.", "ERROR")
            }
            return
        }

        viewModelScope.launch {
            val (prettyHtml, directives) = repository.mergeAndProcessSavedWebPage(html, css, js)
            _webMergeResult.value = prettyHtml
            
            // Auto create index_merged.html in the workspace for preview
            val mergedFile = File(repository.getWorkspaceDirectory(), "web_output/index_merged.html")
            mergedFile.parentFile?.mkdirs()
            mergedFile.writeText(prettyHtml)
            
            repository.log("🌐 تم إنشاء كود الصفحة المدمجة وتصديرها بصيغة index_merged.html بنجاح.", "SUCCESS")
            
            // Auto register extracted directives as templates to play with, resolving Test 9 instructions!
            directives.forEach { 
                repository.insertTemplate(it)
                repository.log("📥 رصد توجيه مستورد مسبقاً من كود الـ HTML: ${it.path}", "INFO")
            }
            refreshWorkspaceFiles()
        }
    }

    // Projects packing companion
    fun packFolderProject() {
        viewModelScope.launch {
            val root = repository.getWorkspaceDirectory()
            val extensions = extensionsToScan.value.split(",")
                .map { it.trim().lowercase().removePrefix(".") }
                .filter { it.isNotBlank() }
            
            val packed = repository.scanAndPackProject(root, extensions)
            _projectPackOutput.value = packed
        }
    }

    fun savePackAsWorkspaceFile() {
        val output = _projectPackOutput.value
        if (output.isBlank()) return
        viewModelScope.launch {
            try {
                val root = repository.getWorkspaceDirectory()
                val packFile = File(root, "project_pack_output.txt")
                packFile.writeText(output, Charsets.UTF_8)
                repository.log("💾 تم تصدير حزمة المشروع بنجاح إلى ملف: project_pack_output.txt", "SUCCESS")
                refreshWorkspaceFiles()
                speakEncouragingWord("تم حفظ حزمة المشروع داخل ملف الحزم بنجاح تام!")
            } catch (e: Exception) {
                repository.log("❌ فشل حفظ ملف حزمة المشروع: ${e.message}", "ERROR")
            }
        }
    }

    // Preview code file
    fun previewFile(fileItem: FileItem) {
        if (fileItem.isDirectory) return
        val file = File(fileItem.absolutePath)
        if (file.exists()) {
            try {
                val text = file.readText(Charsets.UTF_8)
                _previewedFile.value = Pair(fileItem.name, text)
            } catch (e: Exception) {
                _previewedFile.value = Pair(fileItem.name, "خطأ في قراءة ملف العمل: ${e.message}")
            }
        }
    }

    fun closePreview() {
        _previewedFile.value = null
    }

    // Save edited file contents back in editor
    fun saveFileContent(fileName: String, content: String) {
        val root = repository.getWorkspaceDirectory()
        val file = File(root, fileName)
        if (isSystemOrProtectedPath(file.absolutePath)) {
            viewModelScope.launch {
                repository.log("⚠️ حظر أمني: تم منع تعديل أو الكتابة على ملفات النظام لمنع حدوث عطب في نظام التشغيل: ${file.absolutePath}", "ERROR")
            }
            return
        }
        viewModelScope.launch {
            try {
                file.parentFile?.mkdirs()
                file.writeText(content)
                repository.log("💾 تم حفظ تعديلات الملف المباشرة: $fileName", "SUCCESS")
                refreshWorkspaceFiles()
                _previewedFile.value = Pair(fileName, content)
            } catch (e: Exception) {
                repository.log("❌ فشل حفظ التعديلات في الملف: $fileName - ${e.message}", "ERROR")
            }
        }
    }

    // Delete a workspace file/directory
    fun deleteWorkspaceFile(fileItem: FileItem) {
        val file = File(fileItem.absolutePath)
        if (isSystemOrProtectedPath(file.absolutePath)) {
            viewModelScope.launch {
                repository.log("⚠️ حظر أمني: تم منع حذف مَسار تسيير النظام لمنع تدمير أو عطب ملفات نظام التشغيل: ${fileItem.relativePath}", "ERROR")
            }
            return
        }
        viewModelScope.launch {
            if (file.exists()) {
                val success = file.deleteRecursively()
                if (success) {
                    repository.log("🗑️ تم حذف الملف/المجلد بنجاح: ${fileItem.relativePath}", "SUCCESS")
                } else {
                    repository.log("❌ فشل حذف الملف: ${fileItem.relativePath}", "ERROR")
                }
                refreshWorkspaceFiles()
                if (_previewedFile.value?.first == fileItem.name) {
                    _previewedFile.value = null
                }
            }
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            repository.clearLogs()
            repository.log("🧹 تم تنظيف سجل الأحداث والمخرجات بنجاح.", "INFO")
        }
    }

    // --- Folder Watcher Controls ---
    fun startFolderWatching() {
        val folderPath = watchFolder.value.trim()
        if (folderPath.isBlank()) {
            viewModelScope.launch {
                repository.log("❌ فشل بدء المراقبة: مجلد المراقبة فارغ.", "ERROR")
            }
            return
        }

        val extensionsList = watchExtensions.value.split(",")
            .map { it.trim().lowercase().removePrefix(".") }
            .filter { it.isNotBlank() }

        val prefix = watchPrefix.value.trim()
        if (prefix.isBlank() || !prefix.startsWith("@")) {
            viewModelScope.launch {
                repository.log("❌ فشل بدء المراقبة: بادئة الحفظ غير صالحة (مثال: @watcher).", "ERROR")
            }
            return
        }

        // Resolve directory
        val root = repository.getWorkspaceDirectory()
        val watchDir = if (File(folderPath).isAbsolute) {
            File(folderPath)
        } else {
            File(root, folderPath)
        }

        if (!watchDir.exists()) {
            watchDir.mkdirs()
        }

        val outPath = watchOutputPath.value.trim()

        // auto register prefix if not already present in prefix list
        val currentPrefixes = _directivePrefixes.value.toMutableList()
        if (!currentPrefixes.contains(prefix)) {
            currentPrefixes.add(prefix)
            _directivePrefixes.value = currentPrefixes
            viewModelScope.launch {
                repository.log("🏷️ تمت إضافة البادئة '$prefix' تلقائياً لخيارات العمل المراقبة.", "SUCCESS")
            }
        }

        viewModelScope.launch {
            if (outPath.isNotBlank()) {
                repository.insertPrefixPath(PrefixPathEntity(prefix, outPath, true))
                repository.log("📂 تم دمج وربط البادئة $prefix بمسار التصدير المخصص: $outPath", "SUCCESS")
            } else {
                val mappings = prefixPaths.value
                val hasMatch = mappings.any { it.prefix == prefix }
                if (!hasMatch) {
                    repository.insertPrefixPath(PrefixPathEntity(prefix, "watcher_outputs", true))
                }
            }
        }

        prefs.edit().apply {
            putString("watch_folder", folderPath)
            putString("watch_extensions", watchExtensions.value)
            putString("watch_prefix", prefix)
            putString("watch_output_path", outPath)
            putBoolean("watch_enabled", true)
            apply()
        }

        _isFolderWatching.value = true

        watchJob?.cancel()
        watchJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repository.log("🔄 بدأت مراقبة المجلد طويل الأمد: ${watchDir.absolutePath} (الامتدادات: $extensionsList)", "INFO")
            
            val knownFiles = mutableSetOf<String>()
            // Populate snapshot of existing files
            if (watchDir.exists() && watchDir.isDirectory) {
                watchDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val ext = file.extension.lowercase()
                        if (extensionsList.contains(ext)) {
                            knownFiles.add(file.absolutePath)
                        }
                    }
                }
            }

            while (_isFolderWatching.value) {
                kotlinx.coroutines.delay(2000)
                if (!_isFolderWatching.value) break

                val currentFiles = mutableSetOf<String>()
                val newFiles = mutableListOf<File>()

                if (watchDir.exists() && watchDir.isDirectory) {
                    watchDir.walkTopDown().forEach { file ->
                        if (file.isFile) {
                            val ext = file.extension.lowercase()
                            if (extensionsList.contains(ext)) {
                                currentFiles.add(file.absolutePath)
                                if (!knownFiles.contains(file.absolutePath)) {
                                    newFiles.add(file)
                                }
                            }
                        }
                    }
                }

                for (file in newFiles) {
                    repository.log("📄 ملف جديد في مجلد المراقبة: ${file.name}", "INFO")
                    try {
                        val text = file.readText(Charsets.UTF_8)
                        val results = repository.processText(text, _directivePrefixes.value)
                        
                        if (results.any { !it.contains("فشل") && !it.contains("لا توجد") }) {
                            speakEncouragingWord("تم رصد ملف جديد باسم ${file.name} ومعالجة توجيهاته بنجاح فائق وتلقائي!")
                        }
                        refreshWorkspaceFiles()
                    } catch (e: Exception) {
                        repository.log("❌ فشل معالجة الملف الجديد ${file.name}: ${e.message}", "ERROR")
                    }
                }

                knownFiles.clear()
                knownFiles.addAll(currentFiles)
            }
        }
    }

    fun stopFolderWatching() {
        _isFolderWatching.value = false
        watchJob?.cancel()
        watchJob = null
        prefs.edit().putBoolean("watch_enabled", false).apply()
        viewModelScope.launch {
            repository.log("⏹️ توقفت مراقبة المجلد طويل الأمد.", "INFO")
        }
    }

    fun speakEncouragingWord(text: String) {
        viewModelScope.launch {
            try {
                tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "encouragement_speech_id")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- V5.8 Saved Web Page File processing states and functions ---
    private val _webPageFileMergeResult = MutableStateFlow<com.example.data.repository.WebPageMergeResult?>(null)
    val webPageFileMergeResult = _webPageFileMergeResult.asStateFlow()

    private val _extractedWebDirectives = MutableStateFlow<List<com.example.data.repository.WebDirective>>(emptyList())
    val extractedWebDirectives = _extractedWebDirectives.asStateFlow()

    fun selectAndProcessWebPageFile(relativePath: String) {
        val root = repository.getWorkspaceDirectory()
        val htmlFile = File(root, relativePath)
        if (!htmlFile.exists() || !htmlFile.isFile) {
            viewModelScope.launch {
                repository.log("❌ فشل معالجة صفحة الويب: الملف غير موجود أو ليس ملفاً صالحاً: $relativePath", "ERROR")
            }
            return
        }

        viewModelScope.launch {
            repository.log("🌐 جاري تحليل ومعالجة صفحة الويب المحفوظة: ${htmlFile.name}...", "INFO")
            
            // 1. Find resource folder
            val parentDir = htmlFile.parentFile ?: root
            val stem = htmlFile.nameWithoutExtension
            val candidates = listOf(
                File(parentDir, "${stem}_files"),
                File(parentDir, "$stem.files"),
                File(parentDir, "page_files"),
                File(parentDir, "assets"),
                File(parentDir, "resources")
            )
            val resourceFolder = candidates.firstOrNull { it.exists() && it.isDirectory }
            
            var mergedHtml = htmlFile.readText(Charsets.UTF_8)
            var folderMsg = "لم يُعثر على مجلد موارد خارجي."
            if (resourceFolder != null) {
                folderMsg = "تم العثور على مجلد الموارد: ${resourceFolder.name}"
                repository.log("📂 $folderMsg. جاري محاذاة وتوحيد أوراق الأنماط والسكريبتات...", "INFO")
                mergedHtml = repository.mergeResourcesIntoHtml(mergedHtml, resourceFolder)
            } else {
                repository.log("⚠️ $folderMsg. سيتم معالجة كتل التوجيهات من النص مباشرة.", "WARN")
            }
            
            // 2. Prettify HTML string
            val prettified = repository.formatHtmlStringPublic(mergedHtml)
            
            // 3. Extract directives using standard prefixes
            val prefixes = _directivePrefixes.value
            val blocks = repository.parseDirectiveBlocksPublic(prettified, prefixes)
            
            val directivesList = blocks.map { block ->
                com.example.data.repository.WebDirective(
                    path = block.filePath,
                    mode = block.mode,
                    content = block.content,
                    prefix = block.prefix,
                    isSelected = true
                )
            }
            
            _webPageFileMergeResult.value = com.example.data.repository.WebPageMergeResult(
                mergedHtml = prettified,
                directives = directivesList,
                resourceFolder = resourceFolder?.absolutePath
            )
            
            _extractedWebDirectives.value = directivesList
            
            repository.log("✨ تم التنسيق والتحليل بنجاح لملف الويب. البادئات: ${prefixes.joinToString()}. عُثر على ${directivesList.size} توجيهات.", "SUCCESS")
            speakEncouragingWord("اكتمل معالجة صفحة الويب بنجاح تام! يرجى تأكيد حفظ الملفات المحددة.")
        }
    }

    fun toggleExtractedWebDirective(index: Int) {
        val current = _extractedWebDirectives.value.toMutableList()
        if (index in current.indices) {
            val item = current[index]
            current[index] = item.copy(isSelected = !item.isSelected)
            _extractedWebDirectives.value = current
        }
    }

    fun cancelWebPageMerge() {
        _webPageFileMergeResult.value = null
        _extractedWebDirectives.value = emptyList()
        viewModelScope.launch {
            repository.log("ℹ️ تم إلغاء معالجة صفحة الويب وتجاهل التوجيهات.", "INFO")
        }
    }

    fun saveConfirmedWebDirectives() {
        val result = _webPageFileMergeResult.value ?: return
        val selected = _extractedWebDirectives.value.filter { it.isSelected }
        
        if (selected.isEmpty()) {
            viewModelScope.launch {
                repository.log("⚠️ لم يتم اختيار أي توجيه برمجية مستخرج للحفظ فعلياً.", "WARN")
            }
            _webPageFileMergeResult.value = null
            _extractedWebDirectives.value = emptyList()
            return
        }

        viewModelScope.launch {
            // Build direct block text for standard unified processing to reuse backups and logs
            val builderText = StringBuilder()
            selected.forEach { dir ->
                builderText.append("<!-- ${dir.prefix}:file ${dir.path} -->\n")
                if (dir.mode == "a") {
                    builderText.append("<!-- ${dir.prefix}:mode append -->\n")
                } else {
                    builderText.append("<!-- ${dir.prefix}:mode overwrite -->\n")
                }
                builderText.append(dir.content)
                if (!dir.content.endsWith("\n")) builderText.append("\n")
                builderText.append("<!-- ${dir.prefix}:end -->\n\n")
            }

            repository.log("💾 جاري كتابة وحفظ الكود المختار وتفعيل نقاط التراجع الذكي...", "INFO")
            repository.processText(builderText.toString(), _directivePrefixes.value)
            
            // Also write index_merged.html in the web_output
            try {
                val mergedFile = File(repository.getWorkspaceDirectory(), "web_output/index_merged.html")
                mergedFile.parentFile?.mkdirs()
                mergedFile.writeText(result.mergedHtml, Charsets.UTF_8)
                repository.log("🌐 تم تفريغ وتصدير ملف HTML الموحد بنجاح: web_output/index_merged.html", "SUCCESS")
            } catch (e: Exception) {
                repository.log("❌ فشل تصدير HTML الموحد: ${e.message}", "ERROR")
            }

            _webPageFileMergeResult.value = null
            _extractedWebDirectives.value = emptyList()
            refreshWorkspaceFiles()
            speakEncouragingWord("تم حفظ ملفات الويب والأكواد البرمجية المحددة بنجاح فائق وتلقائي وتحت ظل الحماية البرمجية المباشرة!")
        }
    }

    // --- Dynamic Python Plugins & Custom Directory Management System ---

    fun refreshPlugins() {
        val root = repository.getWorkspaceDirectory()
        val pluginsDir = File(root, "plugins")
        if (!pluginsDir.exists()) {
            pluginsDir.mkdirs()
            // Create a default demo Python plugin so that the directory has samples
            val demo = File(pluginsDir, "demo_cleanup_plugin.py")
            if (!demo.exists()) {
                demo.writeText("""
# -*- coding: utf-8 -*-
import os
import sys

print("🟢 مرحبا بك من ملحق بايثون التلقائي الذكي!")
print("⚙️ هذا الملحق يعمل تلقائياً لتوسيع ومتابعة مهام البناء.")

workspace = os.getcwd()
print(f"📁 مسار العمل النشط الحالي: {workspace}")

# List files in workspace
files = os.listdir(workspace)
print(f"📦 الملفات المكتشفة في مجلد العمل النشط: {files}")

# Show arguments python was called with
print(f"📍 معطيات وسائط الفحص التشغيلي: {sys.argv}")
print("🚀 تم إنهاء مهام الملحق بنجاح تام!")
                """.trimIndent(), Charsets.UTF_8)
            }
        }

        val items = mutableListOf<PythonPlugin>()
        pluginsDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".py", true)) {
                val isAutoRun = prefs.getBoolean("plugin_autorun_${file.name}", true)
                items.add(PythonPlugin(
                    name = file.name,
                    file = file,
                    isAutoRunAtStartup = isAutoRun,
                    isRunning = false,
                    lastOutput = ""
                ))
            }
        }
        pythonPlugins.value = items
    }

    private fun runAutoPluginsOnStartup() {
        val root = repository.getWorkspaceDirectory()
        val pluginsDir = File(root, "plugins")
        if (pluginsDir.exists() && pluginsDir.isDirectory) {
            pluginsDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".py", true)) {
                    val isAutoRun = prefs.getBoolean("plugin_autorun_${file.name}", true)
                    if (isAutoRun) {
                        viewModelScope.launch(Dispatchers.IO) {
                            runPythonPlugin(file.name)
                        }
                    }
                }
            }
        }
    }

    fun runPythonPlugin(pluginName: String) {
        val plist = pythonPlugins.value.toMutableList()
        val idx = plist.indexOfFirst { it.name == pluginName }
        if (idx == -1) return
        val p = plist[idx]

        // Mark as running
        plist[idx] = p.copy(isRunning = true, lastOutput = "جاري تشغيل الملحق الآن...\n")
        pythonPlugins.value = plist

        viewModelScope.launch(Dispatchers.IO) {
            val root = repository.getWorkspaceDirectory()
            try {
                repository.log("🐍 جاري تشغيل ملحق بايثون: ${p.name}...", "INFO")
                val pb = ProcessBuilder("python", p.file.absolutePath)
                pb.directory(root)
                pb.redirectErrorStream(true)
                val process = pb.start()
                val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
                val output = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
                val exitCode = process.waitFor()
                val fullOutput = output.toString()
                withContext(Dispatchers.Main) {
                    val updatedList = pythonPlugins.value.toMutableList()
                    val targetIdx = updatedList.indexOfFirst { it.name == pluginName }
                    if (targetIdx != -1) {
                        updatedList[targetIdx] = updatedList[targetIdx].copy(
                            isRunning = false,
                            lastOutput = "رمز الخروج: $exitCode\n$fullOutput"
                        )
                        pythonPlugins.value = updatedList
                    }
                    repository.log("🐍 الملحق '${p.name}' انتهى بالرمز $exitCode.\nالمخرجات:\n$fullOutput", "SUCCESS")
                }
            } catch (e: Exception) {
                // Try python3 fallback
                try {
                    val pb = ProcessBuilder("python3", p.file.absolutePath)
                    pb.directory(root)
                    pb.redirectErrorStream(true)
                    val process = pb.start()
                    val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
                    val output = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        output.append(line).append("\n")
                    }
                    val exitCode = process.waitFor()
                    val fullOutput = output.toString()
                    withContext(Dispatchers.Main) {
                        val updatedList = pythonPlugins.value.toMutableList()
                        val targetIdx = updatedList.indexOfFirst { it.name == pluginName }
                        if (targetIdx != -1) {
                            updatedList[targetIdx] = updatedList[targetIdx].copy(
                                isRunning = false,
                                lastOutput = "رمز الخروج: $exitCode\n$fullOutput"
                            )
                            pythonPlugins.value = updatedList
                        }
                        repository.log("🐍 [python3] الملحق '${p.name}' حصد رمز الخروج $exitCode.\n$fullOutput", "SUCCESS")
                    }
                } catch (e2: Exception) {
                    withContext(Dispatchers.Main) {
                        val updatedList = pythonPlugins.value.toMutableList()
                        val targetIdx = updatedList.indexOfFirst { it.name == pluginName }
                        if (targetIdx != -1) {
                            updatedList[targetIdx] = updatedList[targetIdx].copy(
                                isRunning = false,
                                lastOutput = "خطأ في التشغيل: ${e.message ?: "غير معروف"}\nتنبيه: تأكد من تثبيت بايثون ومدرجه بالبيئة لتشغيل الملحقات الحقيقية."
                            )
                            pythonPlugins.value = updatedList
                        }
                        repository.log("❌ فشل تشغيل ملحق بايثون '${p.name}' لعدم توفر مفسر بايثون بالبيئة المحاكاة: ${e.message}", "ERROR")
                    }
                }
            }
        }
    }

    fun togglePluginAutoRun(pluginName: String) {
        val plist = pythonPlugins.value.toMutableList()
        val idx = plist.indexOfFirst { it.name == pluginName }
        if (idx == -1) return
        val p = plist[idx]
        val nextVal = !p.isAutoRunAtStartup

        prefs.edit().putBoolean("plugin_autorun_$pluginName", nextVal).apply()
        plist[idx] = p.copy(isAutoRunAtStartup = nextVal)
        pythonPlugins.value = plist

        viewModelScope.launch {
            repository.log("⚙️ تم تعديل وضع التشغيل التلقائي للملحق $pluginName إلى: ${if (nextVal) "نشط ومفعل عند بدء التشغيل تلقائياً" else "متوقف"}", "INFO")
        }
    }

    fun updateWorkspaceDirectory(newPath: String) {
        val cleanPath = newPath.trim()
        if (cleanPath.isBlank()) return
        val fileTarget = File(cleanPath)
        
        try {
            if (!fileTarget.exists()) {
                fileTarget.mkdirs()
            }
            repository.setWorkspaceDirectory(cleanPath)
            customWorkspacePath.value = cleanPath
            prefs.edit().putString("custom_workspace_path", cleanPath).apply()
            
            refreshWorkspaceFiles()
            refreshPlugins()
            
            viewModelScope.launch {
                repository.log("🐾 تم تحويل مسار تصفح وبناء المشروع بنجاح إلى: $cleanPath", "SUCCESS")
                speakEncouragingWord("أحسنت يا مهندس! تم تحديث مسار المشروع النشط بنجاح وبقوة مطلقة!")
            }
        } catch (e: Exception) {
            viewModelScope.launch {
                repository.log("❌ فشل تحويل مسار المشروع المخصص: ${e.message}", "ERROR")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            stopMusicProgressJob()
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Protection validation logic to prevent critical OS file corruption
    fun isSystemOrProtectedPath(path: String): Boolean {
        val clean = java.io.File(path).absolutePath
        val protectedPrefixes = listOf(
            "/system",
            "/vendor",
            "/apex",
            "/proc",
            "/sys",
            "/dev",
            "/sbin",
            "/etc",
            "/bin",
            "/usr",
            "/var",
            "/lib",
            "/metadata",
            "/odm",
            "/product",
            "/oem"
        )
        
        // Critical block on absolute root deletion/modification
        if (clean == "/") return true
        
        for (prefix in protectedPrefixes) {
            if (clean.equals(prefix, ignoreCase = true) || clean.startsWith(prefix + "/", ignoreCase = true)) {
                return true
            }
        }
        
        // Block external storage core system storage
        if (clean.equals("/storage/emulated/0/Android", ignoreCase = true) || clean.startsWith("/storage/emulated/0/Android/", ignoreCase = true)) {
            return true
        }
        
        return false
    }

    // Check full read/write storage permission state
    fun hasFullStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val read = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            val write = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            read && write
        }
    }

    // Launch settings to grant full storage access manually
    fun requestFullStoragePermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                viewModelScope.launch {
                    repository.log("⚙️ تم توجيه المستخدم لصفحة ضبط ملفات النظام لمنح تطبيقنا كامل الصلاحيات لتصفح وكتابة الملفات يدوياً.", "INFO")
                }
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e2: Exception) {
                    viewModelScope.launch {
                        repository.log("❌ فشل فتح إعدادات صلاحية إدارة الملفات الكلية: ${e2.message}", "ERROR")
                    }
                }
            }
        } else {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                viewModelScope.launch {
                    repository.log("⚙️ تم فتح صفحة معلومات التطبيق للتصريح بصلاحيات التخزين العادية يدوياً.", "INFO")
                }
            } catch (e: Exception) {
                viewModelScope.launch {
                    repository.log("❌ فشل فتح إعدادات التطبيق: ${e.message}", "ERROR")
                }
            }
        }
    }

    // --- Evidence Music & Zamil Player System Core Functions ---
    fun refreshMusicTracks() {
        val list = mutableListOf<AudioTrack>()
        
        // Accurate user-defined tracks corresponding to the uploaded files
        val defaultFiles = listOf(
            "🪖 موسيقى الأدلة – نسخة متوازنة (Balanced Instrumental Zāmil).mp3",
            "🪖 موسيقى الأدلة – نسخة متوازنة (Balanced Instrumental Zāmil) (1).mp3",
            "خطة التنفيذ.mp3",
            "خطة التنفيذ (1).mp3",
            "خطة تنفيذ محكمة.mp3"
        )
        
        val activeRoot = repository.getWorkspaceDirectory()
        defaultFiles.forEach { name ->
            val wFile = File(activeRoot, name)
            // Look also in "/" direct container root as fallback
            val rootFile = File("/", name)
            
            if (wFile.exists() && wFile.length() > 0) {
                list.add(AudioTrack(name = name.removeSuffix(".mp3"), fileName = name, isAsset = false, absolutePath = wFile.absolutePath, isSimulation = false))
            } else if (rootFile.exists() && rootFile.length() > 0) {
                list.add(AudioTrack(name = name.removeSuffix(".mp3"), fileName = name, isAsset = false, absolutePath = rootFile.absolutePath, isSimulation = false))
            } else {
                // Empty files on container or not found -> enable simulation mode
                list.add(AudioTrack(name = name.removeSuffix(".mp3"), fileName = name, isAsset = false, absolutePath = wFile.absolutePath, isSimulation = true))
            }
        }
        
        // Look for any other MP3s in workspace
        try {
            if (activeRoot.exists() && activeRoot.isDirectory) {
                activeRoot.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.endsWith(".mp3", ignoreCase = true) && !defaultFiles.contains(file.name)) {
                        list.add(AudioTrack(
                            name = "🎵 " + file.name.removeSuffix(".mp3"),
                            fileName = file.name,
                            isAsset = false,
                            absolutePath = file.absolutePath,
                            isSimulation = file.length() == 0L
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        musicTracks.value = list
    }

    fun playTrack(track: AudioTrack) {
        viewModelScope.launch {
            repository.log("🎧 جاري تشغيل: ${track.name}...", "INFO")
        }
        stopMusicProgressJob()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        currentPlayingTrack.value = track
        
        if (track.isSimulation) {
            isMusicPlaying.value = true
            musicPlaybackDuration.value = 180
            musicPlaybackPosition.value = 0
            startMusicSimulationJob(track)
            return
        }
        
        try {
            val mp = MediaPlayer().apply {
                setDataSource(track.absolutePath)
                setVolume(musicVolume.value, musicVolume.value)
                isLooping = isMusicLoopActive.value
                prepare()
                start()
            }
            mediaPlayer = mp
            isMusicPlaying.value = true
            musicPlaybackDuration.value = mp.duration / 1000
            musicPlaybackPosition.value = 0
            
            startRealMusicProgressJob()
            viewModelScope.launch {
                repository.log("🔊 تشغيل حي للملف الصوتي: ${track.name}", "SUCCESS")
            }
        } catch (e: Exception) {
            viewModelScope.launch {
                repository.log("⚠️ تعذر تشغيل الصوت الحي لملف غير ممهد. تم التحويل التلقائي لمحاكي دمج الصوت والـ TTS: ${track.name}", "WARN")
            }
            isMusicPlaying.value = true
            musicPlaybackDuration.value = 150
            musicPlaybackPosition.value = 0
            startMusicSimulationJob(track)
        }
    }

    fun pauseTrack() {
        isMusicPlaying.value = false
        stopMusicProgressJob()
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        viewModelScope.launch {
            repository.log("⏸️ تم الإيقاف المؤقت للمسار الصوتي.", "INFO")
        }
    }

    fun resumeTrack() {
        val track = currentPlayingTrack.value ?: return
        isMusicPlaying.value = true
        if (track.isSimulation) {
            startMusicSimulationJob(track)
            return
        }
        try {
            mediaPlayer?.start()
            startRealMusicProgressJob()
        } catch (e: Exception) {
            e.printStackTrace()
            startMusicSimulationJob(track)
        }
        viewModelScope.launch {
            repository.log("▶️ تم استئناف المقطع الصوتي.", "INFO")
        }
    }

    fun stopTrack() {
        isMusicPlaying.value = false
        stopMusicProgressJob()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        musicPlaybackPosition.value = 0
        currentPlayingTrack.value = null
        visualizerWaves.value = List(16) { 0.1f }
        viewModelScope.launch {
            repository.log("⏹️ تم إيقاف المسار الصوتي وتحرير المشغل.", "INFO")
        }
    }

    fun seekTo(seconds: Int) {
        val track = currentPlayingTrack.value ?: return
        musicPlaybackPosition.value = seconds
        if (!track.isSimulation) {
            try {
                mediaPlayer?.seekTo(seconds * 1000)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleMute() {
        isMuted.value = !isMuted.value
        val vol = if (isMuted.value) 0f else musicVolume.value
        try {
            mediaPlayer?.setVolume(vol, vol)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleLoop() {
        isMusicLoopActive.value = !isMusicLoopActive.value
        try {
            mediaPlayer?.isLooping = isMusicLoopActive.value
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setMusicVolume(vol: Float) {
        musicVolume.value = vol
        if (!isMuted.value) {
            try {
                mediaPlayer?.setVolume(vol, vol)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val isMuted = MutableStateFlow(false)

    private fun stopMusicProgressJob() {
        musicJob?.cancel()
        musicJob = null
    }

    private fun startRealMusicProgressJob() {
        musicJob = viewModelScope.launch(Dispatchers.Main) {
            while (isMusicPlaying.value) {
                delay(1000)
                mediaPlayer?.let { mp ->
                    try {
                        if (mp.isPlaying) {
                            musicPlaybackPosition.value = mp.currentPosition / 1000
                            val pos = musicPlaybackPosition.value
                            val newWaves = List(16) { (0.2f + Math.sin((pos.toDouble() + it.toDouble()) * 0.5).toFloat() * 0.6f + (0..10).random() / 30f).coerceIn(0.1f, 1f) }
                            visualizerWaves.value = newWaves
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun startMusicSimulationJob(track: AudioTrack) {
        musicJob = viewModelScope.launch(Dispatchers.Main) {
            speakEncouragingWord("أهلاً بك يا مهندس! سأقوم بمرافقتك وقراءة هذا المسار الصوتي بذكاء: ${track.name}")
            while (isMusicPlaying.value) {
                delay(1000)
                val currentPos = musicPlaybackPosition.value
                val duration = musicPlaybackDuration.value
                if (currentPos < duration) {
                    musicPlaybackPosition.value = currentPos + 1
                    val newWaves = List(16) { (0.2f + Math.sin((currentPos.toDouble() + it.toDouble()) * 0.5).toFloat() * 0.6f + (0..10).random() / 30f).coerceIn(0.1f, 1f) }
                    visualizerWaves.value = newWaves

                    if (currentPos % 30 == 0 && currentPos > 0) {
                        val lyric = getLyricForTrackAndSecond(track.name, currentPos)
                        speakEncouragingWord(lyric)
                    }
                } else {
                    if (isMusicLoopActive.value) {
                        musicPlaybackPosition.value = 0
                    } else {
                        isMusicPlaying.value = false
                    }
                }
            }
        }
    }

    fun getLyricForTrackAndSecond(trackName: String, second: Int): String {
        return when {
            trackName.contains("موسيقى") || trackName.contains("Zāmil") || trackName.contains("متوازنة") -> {
                when (second / 30 % 4) {
                    0 -> "هذا مطلع المجد والبرمجة الأبية! واثقو الخطى نحو الريادة البرمجية!"
                    1 -> "ندعم المترجم، نتابع الحافظة، وننشئ قوالب الأدلة والترتيبات لغدٍ مشرق!"
                    2 -> "بالبناء والهمة العالية، نجمع حزم الأكواد لخدمة أبطال البناء رفقاء الحوار!"
                    else -> "عزيمتنا صلبة، وكودنا خالٍ من العيوب وسهل الوصول في كل الأوقات!"
                }
            }
            trackName.contains("خطة") || trackName.contains("خطة التنفيذ") || trackName.contains("محكمة") -> {
                when (second / 30 % 4) {
                    0 -> "خطة التنفيذ الميدانية: سنرتب الملفات ونكشف التوجيهات فورياً!"
                    1 -> "انتهت مرحلة التخطيط والآن بدأ البناء الشامل لكل مفاصل التعليمات!"
                    2 -> "مراقبة الحافظة نشطة وتنبيهات الكود ترسل مباشرة لمنصة العمل والتحقق!"
                    else -> "أركان النظام متماسكة والفقاعة العائمة تفتح بوابات الأوامر من هاتفكم مباشرةً!"
                }
            }
            else -> {
                "مستمرون بالبناء والإنتاج الفوري وتأكيد المعالجة بنجاح متميز!"
            }
        }
    }
}

// Helper POJO for workspace files listing
data class FileItem(
    val name: String,
    val relativePath: String,
    val absolutePath: String,
    val isDirectory: Boolean,
    val size: Long,
    val children: List<FileItem> = emptyList()
)
