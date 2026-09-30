package com.example.camera

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Thực thể lưu trữ metadata hình ảnh hiện trường của thiết bị thuộc Trạm 110kV
 */
@Entity(tableName = "equipment_images")
@Serializable
data class EquipmentImageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val stationId: Int, // ID của Trạm 110kV
    val stationName: String = "", // Tên trạm (VD: "Trạm 110kV Hòa Thọ")
    val equipmentCode: String, // Mã thiết bị (VD: "MBA-T1", "LBS-101", "CLS-110")
    val equipmentName: String = "", // Tên thiết bị (VD: "Máy biến áp T1 63MVA", "Chống sét van 110kV")
    val imagePath: String, // Đường dẫn file ảnh cục bộ trong getExternalFilesDir
    val capturedAt: String, // Thời điểm chụp định dạng YYYY-MM-DD HH:mm:ss
    val capturedBy: String = "Kỹ sư vận hành", // Cán bộ / người chụp ảnh
    val note: String = "", // Ghi chú kiểm tra tình trạng thiết bị
    val isSynced: Boolean = false // Trạng thái đồng bộ lên máy chủ
)
