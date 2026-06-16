package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY id ASC")
    fun getAllTemplates(): Flow<List<TemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: TemplateEntity)

    @Update
    suspend fun updateTemplate(template: TemplateEntity)

    @Delete
    suspend fun deleteTemplate(template: TemplateEntity)

    @Query("UPDATE templates SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Int, enabled: Boolean)
}

@Dao
interface PrefixPathDao {
    @Query("SELECT * FROM prefix_paths ORDER BY prefix ASC")
    fun getAllPrefixPaths(): Flow<List<PrefixPathEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrefixPath(prefixPath: PrefixPathEntity)

    @Delete
    suspend fun deletePrefixPath(prefixPath: PrefixPathEntity)
}

@Dao
interface LogDao {
    @Query("SELECT * FROM processing_logs ORDER BY timestamp DESC, id DESC LIMIT 500")
    fun getAllLogs(): Flow<List<LogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: LogEntity)

    @Query("DELETE FROM processing_logs")
    suspend fun clearLogs()
}

@Dao
interface ClipboardOperationDao {
    @Query("SELECT * FROM clipboard_operations ORDER BY timestamp DESC")
    fun getAllOperations(): Flow<List<ClipboardOperationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOperation(op: ClipboardOperationEntity): Long

    @Query("DELETE FROM clipboard_operations WHERE id = :id")
    suspend fun deleteOperationById(id: Int)

    @Query("SELECT * FROM file_restore_states WHERE operationId = :opId")
    suspend fun getRestoreStatesForOperation(opId: Int): List<FileRestoreState>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRestoreStates(states: List<FileRestoreState>)

    @Query("DELETE FROM file_restore_states WHERE operationId = :opId")
    suspend fun deleteRestoreStatesForOperation(opId: Int)

    @Query("SELECT * FROM clipboard_operations ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastOperation(): ClipboardOperationEntity?
}
