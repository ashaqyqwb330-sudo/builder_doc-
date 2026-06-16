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
        logDao = db.logDao()
    )

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
        }
    }

    fun readAndProcessClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val pasteText = clip.getItemAt(0).text?.toString() ?: ""
            if (pasteText.isNotBlank()) {
                _editorText.value = pasteText
                viewModelScope.launch {
                    repository.log("📋 تم جلب النص من الحافظة تلقائياً (الطول: ${pasteText.length} حرفاً).", "INFO")
                    val results = repository.processText(pasteText, _directivePrefixes.value)
                    refreshWorkspaceFiles()
                }
            } else {
                viewModelScope.launch { repository.log("📋 الحافظة فارغة حالياً.", "WARN") }
            }
        } else {
            viewModelScope.launch { repository.log("📋 لم يتم العثور على قيم في حافظة النظام.", "WARN") }
        }
    }

    fun toggleClipboardMonitoring() {
        _isClipboardMonitoring.value = !_isClipboardMonitoring.value
        viewModelScope.launch {
            if (_isClipboardMonitoring.value) {
                repository.log("▶️ بدء المراقبة النشطة للحافظة في الخلفية.", "INFO")
            } else {
                repository.log("⏸️ تم إيقاف مراقبة الحافظة.", "INFO")
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
