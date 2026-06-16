package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val path: String,
    val prefix: String = "@builder",
    val mode: String = "w", // "w" (overwrite) or "a" (append)
    val enabled: Boolean = true
)

@Entity(tableName = "prefix_paths")
data class PrefixPathEntity(
    @PrimaryKey val prefix: String, // e.g. "@builder", "@watcher"
    val customPath: String,         // e.g. "BuilderTest_A"
    val enabled: Boolean = true
)

@Entity(tableName = "processing_logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val message: String,
    val level: String, // "INFO", "SUCCESS", "WARN", "ERROR"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "clipboard_operations")
data class ClipboardOperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val rawText: String,
    val status: String // "SUCCESS" or "FAILED"
)

@Entity(tableName = "file_restore_states")
data class FileRestoreState(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val operationId: Int,
    val relativePath: String,
    val previousContent: String?, // null if file did not exist
    val existedBefore: Boolean
)
