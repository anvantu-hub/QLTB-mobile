package com.example.camera

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object quản lý dữ liệu hình ảnh thiết bị Trạm 110kV
 */
@Dao
interface EquipmentImageDao {

    @Query("SELECT * FROM equipment_images WHERE stationId = :stationId ORDER BY capturedAt DESC")
    fun getImagesByStationId(stationId: Int): Flow<List<EquipmentImageEntity>>

    @Query("""
        SELECT * FROM equipment_images 
        WHERE stationId = :stationId AND (:equipmentCode = '' OR equipmentCode = :equipmentCode)
        ORDER BY capturedAt DESC
    """)
    fun getImagesByStationAndEquipment(stationId: Int, equipmentCode: String): Flow<List<EquipmentImageEntity>>

    @Query("SELECT * FROM equipment_images WHERE id = :id LIMIT 1")
    suspend fun getImageById(id: Long): EquipmentImageEntity?

    @Query("SELECT COUNT(*) FROM equipment_images WHERE stationId = :stationId")
    fun getImageCountForStation(stationId: Int): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(image: EquipmentImageEntity): Long

    @Delete
    suspend fun delete(image: EquipmentImageEntity)

    @Query("DELETE FROM equipment_images WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE equipment_images SET isSynced = :isSynced WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, isSynced: Boolean)
}
