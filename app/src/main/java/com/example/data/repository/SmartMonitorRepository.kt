package com.example.data.repository

import android.content.Context
import com.example.data.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.regex.Pattern

class SmartMonitorRepository(
    private val context: Context,
    private val templateDao: TemplateDao,
    private val prefixPathDao: PrefixPathDao,
    private val logDao: LogDao,
    private val clipboardOperationDao: ClipboardOperationDao
) {
    // Flows from ROOM
    val templates: Flow<List<TemplateEntity>> = templateDao.getAllTemplates()
    val prefixPaths: Flow<List<PrefixPathEntity>> = prefixPathDao.getAllPrefixPaths()
    val logs: Flow<List<LogEntity>> = logDao.getAllLogs()
    val clipboardOperations: Flow<List<ClipboardOperationEntity>> = clipboardOperationDao.getAllOperations()

    // Default Workspace directory in external files/sandbox (accessible on device)
    private var baseDir: File = File(context.getExternalFilesDir(null), "BuilderOutput").apply {
        if (!exists()) mkdirs()
    }

    fun getWorkspaceDirectory(): File {
        return baseDir
    }

    fun setWorkspaceDirectory(path: String) {
        val newDir = File(path)
        if (!newDir.exists()) {
            newDir.mkdirs()
        }
        baseDir = newDir
    }

    // DB Operations
    suspend fun insertTemplate(template: TemplateEntity) = templateDao.insertTemplate(template)
    suspend fun updateTemplate(template: TemplateEntity) = templateDao.updateTemplate(template)
    suspend fun deleteTemplate(template: TemplateEntity) = templateDao.deleteTemplate(template)
    suspend fun setTemplateEnabled(id: Int, enabled: Boolean) = templateDao.setEnabled(id, enabled)

    suspend fun insertPrefixPath(prefixPath: PrefixPathEntity) = prefixPathDao.insertPrefixPath(prefixPath)
    suspend fun deletePrefixPath(prefixPath: PrefixPathEntity) = prefixPathDao.deletePrefixPath(prefixPath)

    suspend fun log(message: String, level: String = "INFO") {
        logDao.insertLog(LogEntity(message = message, level = level))
    }

    suspend fun clearLogs() = logDao.clearLogs()

    suspend fun undoLastAction(): Boolean = withContext(Dispatchers.IO) {
        val lastOp = clipboardOperationDao.getLastOperation() ?: run {
            log("⚠️ لا توجد أي عمليات سابقة للتراجع عنها في السجل.", "WARN")
            return@withContext false
        }
        
        log("🔄 جاري التراجع عن العملية الأخيرة (مُعرّف: ${lastOp.id})...", "INFO")
        val states = clipboardOperationDao.getRestoreStatesForOperation(lastOp.id)
        
        var restoredCount = 0
        var deletedCount = 0
        
        for (state in states) {
            val finalFile = File(baseDir, state.relativePath)
            try {
                if (state.existedBefore) {
                    if (state.previousContent != null) {
                        finalFile.parentFile?.mkdirs()
                        finalFile.writeText(state.previousContent)
                        restoredCount++
                    }
                } else {
                    if (finalFile.exists()) {
                        finalFile.delete()
                        deletedCount++
                    }
                }
            } catch (e: Exception) {
                log("❌ فشل استعادة الملف ${state.relativePath}: ${e.message}", "ERROR")
            }
        }
        
        clipboardOperationDao.deleteRestoreStatesForOperation(lastOp.id)
        clipboardOperationDao.deleteOperationById(lastOp.id)
        
        log("✅ [استرجاع الناجح] تم التراجع بنجاح! تم استعادة $restoredCount ملفات وحذف $deletedCount ملفات جديدة.", "SUCCESS")
        true
    }

    // --- Core Parsing Engine (Smart Monitor 5.9 Specifications) ---
    suspend fun processText(rawText: String, activePrefixes: List<String>): List<String> = withContext(Dispatchers.IO) {
        val results = mutableListOf<String>()
        val prefixes = if (activePrefixes.isEmpty()) listOf("@builder") else activePrefixes
        val fileRestoreStates = mutableListOf<FileRestoreState>()
        
        // Fetch current active custom prefix configurations
        val prefixMap = mutableMapOf<String, PrefixPathEntity>()
        prefixPathDao.getAllPrefixPaths().collect { list ->
            list.forEach { prefixMap[it.prefix] = it }
        }

        val hasDirectives = hasDirectives(rawText, prefixes)

        if (hasDirectives) {
            log("🔍 تم اكتشاف توجيهات، جاري المعالجة...", "INFO")
            // Parse blocks manually using state machine to support overlapping, comment stripping, and mode selections
            val blocks = parseDirectiveBlocks(rawText, prefixes)
            log("🔢 تم العثور على ${blocks.size} كتل عمل", "INFO")

            if (blocks.isEmpty()) {
                log("⚠️ كشف توجيهات فارغة أو فشل الاستخراج.", "WARN")
                results.add("⚠️ كشف توجيهات فارغة أو غير متطابقة.")
            }

            for (block in blocks) {
                // Determine target base dir
                val cfg = prefixMap[block.prefix]
                val subDir = if (cfg != null && cfg.enabled && cfg.customPath.isNotBlank()) {
                    File(baseDir, cfg.customPath)
                } else {
                    baseDir
                }

                // Sanitize Path
                val cleanPath = sanitizePath(block.filePath, results)
                if (cleanPath == null) {
                    val errMsg = "⛔ مسار مرفوض أو غير صالح: ${block.filePath}"
                    log(errMsg, "ERROR")
                    results.add(errMsg)
                    continue
                }

                val finalFile = File(subDir, cleanPath)
                finalFile.parentFile?.apply {
                    if (!exists()) mkdirs()
                }

                // Backup before change for Undo
                try {
                    val relativeFromBase = finalFile.relativeTo(baseDir).path.replace('\\', '/')
                    if (fileRestoreStates.none { it.relativePath == relativeFromBase }) {
                        val existedBefore = finalFile.exists()
                        val prevContent = if (existedBefore) {
                            try { finalFile.readText() } catch (e: Exception) { null }
                        } else {
                            null
                        }
                        fileRestoreStates.add(
                            FileRestoreState(
                                operationId = 0,
                                relativePath = relativeFromBase,
                                previousContent = prevContent,
                                existedBefore = existedBefore
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Ignore background failure
                }

                try {
                    val append = block.mode.lowercase() in listOf("append", "a")
                    if (append) {
                        finalFile.appendText(block.content)
                        val msg = "✅ [OK] $cleanPath (إلحاق) - ${block.content.length} حرفاً"
                        log(msg, "SUCCESS")
                        results.add(msg)
                    } else {
                        finalFile.writeText(block.content)
                        val msg = "✅ [OK] $cleanPath (كتابة) - ${block.content.length} حرفاً"
                        log(msg, "SUCCESS")
                        results.add(msg)
                    }
                } catch (e: Exception) {
                    val errMsg = "❌ فشل حفظ الملف: $cleanPath. الخطأ: ${e.message}"
                    log(errMsg, "ERROR")
                    results.add(errMsg)
                }
            }
        } else {
            // No direct templates: Apply active templates
            var templatesApplied = false
            // Collect the templates dynamically from Flow safely by retrieving a single snapshot
            val activeTemplates = mutableListOf<TemplateEntity>()
            var list: List<TemplateEntity> = emptyList()
            try {
                templateDao.getAllTemplates()
            } catch (e: Exception) {
                // Fallback
            }
            
            kotlin.runCatching {
                val flow = templateDao.getAllTemplates()
                kotlinx.coroutines.withTimeoutOrNull(800) {
                    flow.collect {
                        list = it
                        throw kotlinx.coroutines.CancellationException() // break collection
                    }
                }
            }

            val active = list.filter { it.enabled }
            if (active.isNotEmpty()) {
                log("📋 لا توجد توجيهات، تطبيق القوالب النشطة (${active.size})...", "INFO")
                for (tmpl in active) {
                    val cfg = prefixMap[tmpl.prefix]
                    val subDir = if (cfg != null && cfg.enabled && cfg.customPath.isNotBlank()) {
                        File(baseDir, cfg.customPath)
                    } else {
                        baseDir
                    }
                    val cleanPath = sanitizePath(tmpl.path, results) ?: continue
                    val finalFile = File(subDir, cleanPath)
                    finalFile.parentFile?.apply {
                        if (!exists()) mkdirs()
                    }

                    // Backup before change for Undo
                    try {
                        val relativeFromBase = finalFile.relativeTo(baseDir).path.replace('\\', '/')
                        if (fileRestoreStates.none { it.relativePath == relativeFromBase }) {
                            val existedBefore = finalFile.exists()
                            val prevContent = if (existedBefore) {
                                try { finalFile.readText() } catch (e: Exception) { null }
                            } else {
                                null
                            }
                            fileRestoreStates.add(
                                FileRestoreState(
                                    operationId = 0,
                                    relativePath = relativeFromBase,
                                    previousContent = prevContent,
                                    existedBefore = existedBefore
                                )
                            )
                        }
                    } catch (e: Exception) {
                        // ignore fail
                    }

                    try {
                        val append = tmpl.mode == "a"
                        if (append) {
                            finalFile.appendText(rawText)
                            val msg = "✅ [قالب] $cleanPath (إلحاق) - ${rawText.length} حرفاً"
                            log(msg, "SUCCESS")
                            results.add(msg)
                        } else {
                            finalFile.writeText(rawText)
                            val msg = "✅ [قالب] $cleanPath (كتابة) - ${rawText.length} حرفاً"
                            log(msg, "SUCCESS")
                            results.add(msg)
                        }
                        templatesApplied = true
                    } catch (e: Exception) {
                        val errMsg = "❌ فشل كتابة القالب $cleanPath: ${e.message}"
                        log(errMsg, "ERROR")
                        results.add(errMsg)
                    }
                }
            }

            if (!templatesApplied) {
                log("ℹ️ لا توجد توجيهات ولا قوالب نشطة للتطبيق، تم التجاهل.", "INFO")
                results.add("ℹ️ لا توجد توجيهات ولا قوالب نشطة للتطبيق.")
            }
        }

        // Save persistent block history log with full backup
        if (fileRestoreStates.isNotEmpty()) {
            try {
                val opId = clipboardOperationDao.insertOperation(
                    ClipboardOperationEntity(
                        rawText = rawText,
                        status = "SUCCESS"
                    )
                ).toInt()
                val statesToSave = fileRestoreStates.map { it.copy(operationId = opId) }
                clipboardOperationDao.insertRestoreStates(statesToSave)
                log("💾 سجل التراجع: تم حفظ لقطة استعادة بنجاح (مُعرّف العملية: $opId).", "SUCCESS")
            } catch (e: Exception) {
                log("⚠️ فشل حفظ سجل استرداد العملية: ${e.message}", "WARN")
            }
        }

        results
    }

    private fun hasDirectives(text: String, prefixes: List<String>): Boolean {
        val lines = text.split("\n")
        for (line in lines) {
            val s = line.trim()
            if (s.isEmpty()) continue
            for (p in prefixes) {
                if (s.contains("$p:file") || s.contains("$p:mode")) {
                    return true
                }
            }
        }
        return false
    }

    data class DirectBlock(
        val prefix: String,
        val filePath: String,
        var mode: String, // "w" or "a"
        var content: String
    )

    private fun parseDirectiveBlocks(text: String, prefixes: List<String>): List<DirectBlock> {
        val blocks = mutableListOf<DirectBlock>()
        val lines = text.split("\n")
        var currentBlock: DirectBlock? = null
        val contentBuilder = StringBuilder()

        // Match patterns for direct styling or comments
        // Standard modes inside blocks:
        // <!-- @prefix:mode append -->
        // @prefix:mode append / w
        val fileRegexes = prefixes.map { p ->
            p to Pattern.compile(".*?" + Pattern.quote(p) + ":file\\s+(\\S+).*?")
        }
        val modeRegexes = prefixes.map { p ->
            p to Pattern.compile(".*?" + Pattern.quote(p) + ":mode\\s+(\\S+).*?")
        }
        val endRegexes = prefixes.map { p ->
            p to Pattern.compile(".*?" + Pattern.quote(p) + ":end.*?")
        }

        for (line in lines) {
            val trimmed = line.trim()
            
            // Check if this line starts a compile block
            var foundStart = false
            for ((prefix, pattern) in fileRegexes) {
                val matcher = pattern.matcher(trimmed)
                if (matcher.matches()) {
                    // Save previous block if any (though typically closed with :end)
                    val prev = currentBlock
                    if (prev != null) {
                        prev.content = contentBuilder.toString()
                        blocks.add(prev)
                    }
                    val path = matcher.group(1).trim()
                    currentBlock = DirectBlock(prefix = prefix, filePath = path, mode = "w", content = "")
                    contentBuilder.clear()
                    foundStart = true
                    break
                }
            }
            if (foundStart) continue

            val cb = currentBlock
            if (cb != null) {
                // Check if this is mode directive
                var foundMode = false
                for ((_, pattern) in modeRegexes) {
                    val matcher = pattern.matcher(trimmed)
                    if (matcher.matches()) {
                        val mVal = matcher.group(1).trim().lowercase()
                        cb.mode = if (mVal in listOf("append", "a")) "a" else "w"
                        foundMode = true
                        break
                    }
                }
                if (foundMode) continue

                // Check if this is end directive
                var foundEnd = false
                for ((_, pattern) in endRegexes) {
                    val matcher = pattern.matcher(trimmed)
                    if (matcher.matches()) {
                        cb.content = contentBuilder.toString().trimEnd() + "\n"
                        blocks.add(cb)
                        currentBlock = null
                        contentBuilder.clear()
                        foundEnd = true
                        break
                    }
                }
                if (foundEnd) continue

                // Otherwise, accumulate content
                contentBuilder.append(line).append("\n")
            }
        }

        // Catch unclosed block
        val finalBlock = currentBlock
        if (finalBlock != null) {
            finalBlock.content = contentBuilder.toString().trimEnd() + "\n"
            blocks.add(finalBlock)
        }

        return blocks
    }

    private fun sanitizePath(pathStr: String, results: MutableList<String>): String? {
        var p = pathStr.trim()
        if (p.isEmpty()) return null

        // Handle Windows absolute path C:\ or absolute path / starting with slash
        if (p.matches(Regex("^[a-zA-Z]:[\\\\/].*"))) {
            val converted = p.substring(3).replace('\\', '/')
            val warning = "ℹ️ تحويل مسار مطلق ويندوز إلى نسبي: $p -> $converted"
            results.add(warning)
            return converted
        }

        if (p.startsWith("/") || p.startsWith("\\")) {
            val converted = p.dropWhile { it == '/' || it == '\\' }.replace('\\', '/')
            val warning = "ℹ️ تحويل مسار مطلق إلى نسبي: $p -> $converted"
            results.add(warning)
            return converted
        }

        return p.replace('\\', '/')
    }

    // --- Saved Web Page Parser & Offline Resource Merger (Test 9 specifications) ---
    suspend fun mergeAndProcessSavedWebPage(
        htmlText: String,
        extCssText: String?,
        extJsText: String?
    ): Pair<String, List<TemplateEntity>> = withContext(Dispatchers.Default) {
        log("🌐 معالجة صفحة ويب مدمجة وتنسيقها...", "INFO")
        
        // Match CSS stylesheet links and inline them
        var mergedHtml = htmlText
        if (!extCssText.isNullOrBlank()) {
            // Replace css links or embed directly under <head>
            mergedHtml = if (mergedHtml.contains("</head>")) {
                mergedHtml.replace("</head>", "<style>\n$extCssText\n</style>\n</head>")
            } else {
                "<style>\n$extCssText\n</style>\n$mergedHtml"
            }
            log("🔗 تم دمج ملف الأنماط CSS خارجي بنجاح.", "SUCCESS")
        }

        if (!extJsText.isNullOrBlank()) {
            mergedHtml = if (mergedHtml.contains("</body>")) {
                mergedHtml.replace("</body>", "<script>\n$extJsText\n</script>\n</body>")
            } else {
                "$mergedHtml\n<script>\n$extJsText\n</script>"
            }
            log("🔗 تم دمج سكريبت JS خارجي بنجاح.", "SUCCESS")
        }

        // Format HTML (Simple Indentation prettifier)
        val prettyHtml = formatHtmlString(mergedHtml)
        log("📝 اكتمل تنسيق الكود وترتيب الأسطر تلقائياً.", "SUCCESS")

        // Parse any nested blocks inside HTML comments or directly
        val extractedDirectives = mutableListOf<TemplateEntity>()
        val prefixes = listOf("@builder", "@watcher", "@deploy")
        val blocks = parseDirectiveBlocks(prettyHtml, prefixes)
        for (b in blocks) {
            extractedDirectives.add(TemplateEntity(
                name = "توجيه مستخرج: ${b.filePath}",
                path = b.filePath,
                prefix = b.prefix,
                mode = b.mode,
                enabled = true
            ))
        }

        Pair(prettyHtml, extractedDirectives)
    }

    private fun formatHtmlString(html: String): String {
        val lines = html.split("\n")
        val output = StringBuilder()
        var indent = 0
        for (line in lines) {
            val stripped = line.trim()
            if (stripped.isEmpty()) continue
            
            if (stripped.startsWith("</") || stripped.startsWith("}") || stripped == ");") {
                indent = maxOf(0, indent - 1)
            }
            
            output.append("  ".repeat(indent)).append(stripped).append("\n")
            
            if (stripped.startsWith("<") && !stripped.startsWith("<!") && !stripped.startsWith("<meta") &&
                !stripped.startsWith("<br") && !stripped.startsWith("<hr") && !stripped.startsWith("<img") &&
                !stripped.startsWith("<input") && !stripped.endsWith("/>") && !stripped.contains("</")) {
                if (stripped.startsWith("<") && stripped.endsWith(">")) {
                    indent++
                }
            }
        }
        return output.toString()
    }

    // --- Project Companion: Scan Folder & Pack Workspace (Test 7 specifications) ---
    suspend fun scanAndPackProject(
        targetDir: File,
        extensions: List<String>,
        ignoreDirs: Set<String> = setOf("venv", ".venv", "node_modules", ".git", "__pycache__", "build", "dist")
    ): String = withContext(Dispatchers.IO) {
        log("📂 رفيق المشروع: جاري مسح المجلد وحزم الملفات لسهولة النسخ والتصدير...", "INFO")
        val packedContent = StringBuilder()
        
        if (!targetDir.exists() || !targetDir.isDirectory) {
            log("❌ مسار مجلد رفيق المشروع غير موجود أو ليس مجلداً صالحاً.", "ERROR")
            return@withContext "خطأ: المجلد غير موجود."
        }

        var filesScanned = 0
        targetDir.walkTopDown().forEach { file ->
            if (file.isFile) {
                // Check excludes
                val relativePath = file.relativeTo(targetDir).path.replace('\\', '/')
                val hasIgnoredParent = relativePath.split('/').any { part -> part in ignoreDirs || part.startsWith(".") }
                
                if (!hasIgnoredParent) {
                    val ext = file.extension.lowercase()
                    if (extensions.contains(ext) || extensions.contains(".$ext")) {
                        filesScanned++
                        try {
                            val content = file.readText(Charsets.UTF_8)
                            packedContent.append("<!-- @builder:file $relativePath -->\n")
                            packedContent.append("<!-- @builder:mode overwrite -->\n")
                            packedContent.append(content.trimEnd()).append("\n")
                            packedContent.append("<!-- @builder:end -->\n\n")
                        } catch (e: Exception) {
                            packedContent.append("<!-- ❌ فشل قراءة الملف: $relativePath - ${e.message} -->\n\n")
                        }
                    }
                }
            }
        }

        log("📂 رفيق المشروع: اكتمل المسح بنجاح! تم حزم $filesScanned ملفات.", "SUCCESS")
        packedContent.toString()
    }

    // --- Process Folder (Test 6 specifications) ---
    suspend fun processFolder(
        folder: File,
        extensions: List<String>,
        activePrefixes: List<String>
    ): Int = withContext(Dispatchers.IO) {
        log("📁 جاري معالجة الملفات داخل مجلد: ${folder.name}...", "INFO")
        var count = 0
        if (!folder.exists() || !folder.isDirectory) {
            log("❌ المجلد المحدد غير موجود لمعالجته.", "ERROR")
            return@withContext 0
        }

        folder.walkTopDown().forEach { file ->
            if (file.isFile) {
                val ext = file.extension.lowercase()
                if (extensions.contains(ext) || extensions.contains(".$ext")) {
                    try {
                        val text = file.readText(Charsets.UTF_8)
                        val results = processText(text, activePrefixes)
                        if (results.any { it.contains("✅") }) {
                            count++
                        }
                    } catch (e: Exception) {
                        log("❌ فشل قراءة ومعالجة ملف المجلد ${file.name}: ${e.message}", "ERROR")
                    }
                }
            }
        }
        log("📁 اكتملت معالجة المجلد! تم تحديث/إنشاء ملفات لـ $count ملفاً من كتل التوجيهات.", "SUCCESS")
        count
    }
}
