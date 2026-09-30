package com.example

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY full_name ASC")
    fun getAllUsers(): Flow<List<User>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: User)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(users: List<User>)

    @Update
    suspend fun update(user: User)

    @Delete
    suspend fun delete(user: User)

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: String): User?
}

@Dao
interface SubstationDao {
    @Query("SELECT * FROM substations ORDER BY name ASC")
    fun getAllSubstations(): Flow<List<Substation>>

    @Query("SELECT * FROM substations WHERE id = :substationId LIMIT 1")
    fun observeSubstationById(substationId: Int): Flow<Substation?>

    @Query("SELECT * FROM substations WHERE id = :substationId LIMIT 1")
    suspend fun getSubstationByIdDirect(substationId: Int): Substation?

    @Query("SELECT * FROM substations WHERE substation_code = :code LIMIT 1")
    suspend fun getSubstationByCode(code: String): Substation?

    @Query("SELECT * FROM substations")
    suspend fun getAllSubstationsNow(): List<Substation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(substation: Substation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(substations: List<Substation>)

    @Update
    suspend fun update(substation: Substation)

    @Delete
    suspend fun delete(substation: Substation)

    @Query("DELETE FROM substations")
    suspend fun clearSubstations()
}

@Dao
interface FeederDao {
    @Query("SELECT * FROM feeders ORDER BY name ASC")
    fun getAllFeeders(): Flow<List<Feeder>>

    @Query("""
        SELECT * FROM feeders
        WHERE substation_id = :substationId
        ORDER BY feeder_code ASC
    """)
    fun observeFeedersBySubstationId(substationId: Int): Flow<List<Feeder>>

    @Query("SELECT * FROM feeders WHERE id = :feederId LIMIT 1")
    fun observeFeederById(feederId: Int): Flow<Feeder?>

    @Query("SELECT * FROM feeders WHERE id = :feederId LIMIT 1")
    suspend fun getFeederByIdDirect(feederId: Int): Feeder?

    @Query("SELECT * FROM feeders")
    suspend fun getAllFeedersNow(): List<Feeder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(feeder: Feeder)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(feeders: List<Feeder>)

    @Delete
    suspend fun delete(feeder: Feeder)

    @Query("DELETE FROM feeders")
    suspend fun clearFeeders()
}

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY name ASC")
    fun getAllDevices(): Flow<List<Device>>

    @Query("""
        SELECT * FROM devices
        WHERE feeder_id = :feederId
        ORDER BY pole_number ASC, name ASC
    """)
    fun observeDevicesByFeederId(feederId: Int): Flow<List<Device>>

    @Query("""
        SELECT * FROM devices
        WHERE substation_id = :substationId
        ORDER BY feeder_id ASC, pole_number ASC, name ASC
    """)
    fun observeDevicesBySubstationId(substationId: Int): Flow<List<Device>>

    @Query("SELECT * FROM devices")
    suspend fun getAllDevicesNow(): List<Device>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: Device)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(devices: List<Device>)

    @Update
    suspend fun update(device: Device)

    @Delete
    suspend fun delete(device: Device)

    @Query("DELETE FROM devices WHERE id IN (:idList)")
    suspend fun deleteMultiple(idList: List<Int>)

    @Query("SELECT * FROM devices WHERE id = :id LIMIT 1")
    suspend fun getDeviceById(id: Int): Device?

    @Query("DELETE FROM devices")
    suspend fun clearDevices()
}

@Dao
interface LoopDao {
    @Query("SELECT * FROM loops ORDER BY name ASC")
    fun getAllLoops(): Flow<List<Loop>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(loop: Loop)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(loops: List<Loop>)

    @Delete
    suspend fun delete(loop: Loop)

    @Query("DELETE FROM loops")
    suspend fun clearLoops()
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY created_at DESC")
    fun getAllAuditLogs(): Flow<List<AuditLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: AuditLog)

    @Query("DELETE FROM audit_logs")
    suspend fun clearLogs()
}

@Dao
interface LoopV2Dao {
    @Query("SELECT * FROM loops_v2 ORDER BY name ASC")
    fun getAllLoopsV2(): Flow<List<LoopV2>>

    @Query("SELECT * FROM loops_v2")
    suspend fun getAllLoopsV2Now(): List<LoopV2>

    @Query("SELECT * FROM loops_v2 WHERE id = :id LIMIT 1")
    suspend fun getLoopById(id: Int): LoopV2?

    @Query("SELECT MAX(id) FROM loops_v2")
    suspend fun getMaxLoopV2Id(): Int?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLoopV2Raw(loop: LoopV2): Long

    @Update
    suspend fun updateLoopV2Raw(loop: LoopV2): Int

    @Query("""
        UPDATE loops_v2 
        SET name = :name, 
            stationAId = :stationAId, 
            feederAId = :feederAId, 
            sideADeviceIdsJson = :sideADeviceIdsJson, 
            legalBoundaryDeviceId = :legalBoundaryDeviceId, 
            sideBDeviceIdsJson = :sideBDeviceIdsJson, 
            feederBId = :feederBId, 
            stationBId = :stationBId, 
            layout = :layout, 
            version = version + 1, 
            updatedAt = :updatedAt 
        WHERE id = :id AND version = :oldVersion
    """)
    suspend fun updateLoopV2Conditional(
        id: Int,
        oldVersion: Int,
        name: String,
        stationAId: Int,
        feederAId: Int,
        sideADeviceIdsJson: String,
        legalBoundaryDeviceId: Int,
        sideBDeviceIdsJson: String,
        feederBId: Int,
        stationBId: Int,
        layout: String,
        updatedAt: String
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(loop: LoopV2)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(loops: List<LoopV2>)

    @Delete
    suspend fun delete(loop: LoopV2)

    @Query("DELETE FROM loops_v2 WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM loops_v2")
    suspend fun clearLoopsV2()
}

@Dao
interface DocCategoryDao {
    @Query("SELECT * FROM doc_categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<DocCategory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: DocCategory)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<DocCategory>)

    @Update
    suspend fun update(category: DocCategory)

    @Delete
    suspend fun delete(category: DocCategory)

    @Query("DELETE FROM doc_categories")
    suspend fun clearCategories()
}

@Dao
interface LibraryDocumentDao {
    @Query("SELECT * FROM library_documents ORDER BY updatedAt DESC, displayName ASC")
    fun getAllDocuments(): Flow<List<LibraryDocument>>

    @Query("SELECT * FROM library_documents WHERE deviceId = :deviceId ORDER BY updatedAt DESC, displayName ASC")
    fun getDocumentsForDevice(deviceId: Int): Flow<List<LibraryDocument>>

    @Query("SELECT * FROM library_documents WHERE checksumSHA256 = :checksum LIMIT 1")
    suspend fun findByChecksum(checksum: String): LibraryDocument?

    @Query("SELECT * FROM library_documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: Int): LibraryDocument?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: LibraryDocument): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(documents: List<LibraryDocument>)

    @Update
    suspend fun update(document: LibraryDocument)

    @Delete
    suspend fun delete(document: LibraryDocument)

    @Query("DELETE FROM library_documents WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM library_documents")
    suspend fun clearAllDocuments()
}


