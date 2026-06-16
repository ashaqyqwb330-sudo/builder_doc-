package com.example.util

import java.io.File
import java.util.regex.Pattern

object ClipboardParserUtil {

    data class ClipboardAction(
        val prefix: String,
        val filePath: String,
        val actionType: String, // "CREATE", "OVERWRITE", "APPEND"
        val content: String
    )

    fun containsDirectives(text: String, prefixes: List<String>): Boolean {
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

    fun parseClipboardText(text: String, prefixes: List<String>, baseDir: File): List<ClipboardAction> {
        val actions = mutableListOf<ClipboardAction>()
        val lines = text.split("\n")
        var currentPrefix = ""
        var filePath = ""
        var mode = "w" // default write
        val contentBuilder = StringBuilder()
        var insideBlock = false

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

            // Check file start
            var foundStart = false
            for ((prefix, pattern) in fileRegexes) {
                val matcher = pattern.matcher(trimmed)
                if (matcher.matches()) {
                    if (insideBlock) {
                        // Close previous block
                        val finalContent = contentBuilder.toString()
                        val calculatedActionType = findActionType(filePath, mode, baseDir)
                        actions.add(ClipboardAction(currentPrefix, filePath, calculatedActionType, finalContent))
                    }
                    currentPrefix = prefix
                    filePath = matcher.group(1).trim()
                    mode = "w"
                    contentBuilder.clear()
                    insideBlock = true
                    foundStart = true
                    break
                }
            }
            if (foundStart) continue

            if (insideBlock) {
                // Check mode
                var foundMode = false
                for ((_, pattern) in modeRegexes) {
                    val matcher = pattern.matcher(trimmed)
                    if (matcher.matches()) {
                        val mVal = matcher.group(1).trim().lowercase()
                        mode = if (mVal in listOf("append", "a")) "a" else "w"
                        foundMode = true
                        break
                    }
                }
                if (foundMode) continue

                // Check end
                var foundEnd = false
                for ((_, pattern) in endRegexes) {
                    val matcher = pattern.matcher(trimmed)
                    if (matcher.matches()) {
                        val finalContent = contentBuilder.toString().trimEnd() + "\n"
                        val calculatedActionType = findActionType(filePath, mode, baseDir)
                        actions.add(ClipboardAction(currentPrefix, filePath, calculatedActionType, finalContent))
                        insideBlock = false
                        contentBuilder.clear()
                        foundEnd = true
                        break
                    }
                }
                if (foundEnd) continue

                contentBuilder.append(line).append("\n")
            }
        }

        if (insideBlock) {
            val finalContent = contentBuilder.toString().trimEnd() + "\n"
            val calculatedActionType = findActionType(filePath, mode, baseDir)
            actions.add(ClipboardAction(currentPrefix, filePath, calculatedActionType, finalContent))
        }

        return actions
    }

    private fun findActionType(filePath: String, mode: String, baseDir: File): String {
        val cleanPath = filePath.replace('\\', '/').trimStart('/')
        val file = File(baseDir, cleanPath)
        return if (mode == "a") {
            "APPEND"
        } else {
            if (file.exists()) "OVERWRITE" else "CREATE"
        }
    }
}
