package com.example.ui.viewmodel

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.PrefixPathEntity
import com.example.data.database.TemplateEntity
import com.example.data.repository.SmartMonitorRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

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

    // Cumulative clipboard monitoring state variables
    val isCumulativeClipboardEnabled = MutableStateFlow(prefs.getBoolean("cumulative_clip_enabled", false))
    val cumulativeClipboardBuffer = MutableStateFlow("")

    init {
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
        viewModelScope.launch {
            val file = File(fileItem.absolutePath)
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

    override fun onCleared() {
        super.onCleared()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            e.printStackTrace()
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
