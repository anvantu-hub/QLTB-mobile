package com.example

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class DeviceImage(
    val id: Int,
    val device_id: Int,
    val image_url: String,
    val is_primary: Int,
    val caption: String? = "",
    val created_at: String = ""
)

@Serializable
data class DeviceLocation(
    val id: Int,
    val device_id: Int,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val google_maps_url: String? = "",
    val note: String? = "",
    val updated_by: String? = "",
    val created_at: String = ""
)

@Serializable
data class DeviceStatusHistory(
    val id: Int,
    val device_id: Int,
    val old_switch_status: String? = "",
    val new_switch_status: String? = "",
    val old_scada_status: String? = "",
    val new_scada_status: String? = "",
    val old_relay_79: String? = "",
    val new_relay_79: String? = "",
    val note: String? = "",
    val updated_by: String? = "",
    val created_at: String = ""
)

@Entity(tableName = "users")
@Serializable
data class User(
    @PrimaryKey val id: String,
    val employee_code: String,
    val full_name: String,
    val username: String,
    val email: String,
    val phone: String = "",
    val unit: String = "",
    val team: String = "",
    val title: String = "",
    val status: String = "ACTIVE", // ACTIVE, PENDING, LOCKED
    val created_at: String = "",
    val role: String = "STAFF" // ADMIN, MANAGER, SHIFT_LEADER, STAFF, VIEWER
)

@Entity(tableName = "substations")
@Serializable
data class Substation(
    @PrimaryKey val id: Int,
    val substation_code: String,
    val name: String,
    val address: String = "",
    val status: String = "ACTIVE", // ACTIVE, INACTIVE, MAINTENANCE
    val notes: String = "",
    val created_at: String = "",
    val updated_at: String = "",
    var device_count: Int = 0,
    var feeder_count: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val google_maps_url: String = "",
    val primary_image: String = "",
    val imagesJson: String = "[]",
    val locationHistoryJson: String = "[]"
)

@Entity(tableName = "feeders")
@Serializable
data class Feeder(
    @PrimaryKey val id: Int,
    val feeder_code: String,
    val name: String,
    val substation_id: Int,
    val start_point: String = "",
    val end_point: String = "",
    val notes: String = "",
    val status: String = "ACTIVE", // ACTIVE, INACTIVE
    val created_at: String = "",
    val updated_at: String = ""
)

@Entity(tableName = "devices")
@Serializable
data class Device(
    @PrimaryKey val id: Int,
    val device_id: String, // e.g. "RCL-101"
    val device_code: String = "",
    val name: String,
    val device_type: String = "LBS", // LBS, DS, RCL, REC, RMU, OTHER
    val pole_number: String = "",
    val feeder_id: Int? = null,
    val substation_id: Int? = null,
    val unit: String = "ĐL Hải Châu",
    val team: String = "Tổ Thao Tác Lưu Động",
    val status: String = "ACTIVE", // ACTIVE, INACTIVE, MAINTENANCE
    val switch_status: String = "CLOSED", // CLOSED, OPEN, UNKNOWN
    val scada_status: String = "SIGNAL", // SIGNAL, NO_SIGNAL, UNKNOWN
    val relay_79: String = "ON", // ON, OFF, N_A
    val battery_status: String = "GOOD", // GOOD, WEAK, BROKEN, REPLACING, UNCHECKED
    val latitude: Double? = null,
    val longitude: Double? = null,
    val google_maps_url: String = "",
    val notes: String = "",
    val current_setting: String = "",
    val primary_image: String = "",
    val imagesJson: String = "[]", // Serialized List<DeviceImage>
    val statusHistoryJson: String = "[]", // Serialized List<DeviceStatusHistory>
    val locationHistoryJson: String = "[]", // Serialized List<DeviceLocation>
    val created_at: String = "",
    val updated_at: String = ""
)

@Entity(tableName = "loops")
@Serializable
data class Loop(
    @PrimaryKey val id: Int,
    val loop_id: String,
    val name: String,
    val substation_id_a: Int,
    val feeder_id_a: Int,
    val device_id_a: String,
    val substation_id_b: Int,
    val feeder_id_b: Int,
    val device_id_b: String,
    val status: String = "OPEN", // OPEN, CLOSED, INACTIVE
    val notes: String = "",
    val created_at: String = "",
    val updated_at: String = ""
)

@Entity(tableName = "audit_logs")
@Serializable
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val user_fullname: String,
    val action: String,
    val module: String,
    val target_id: String? = "",
    val details: String = "",
    val result: String = "SUCCESS", // SUCCESS, FAILURE
    val ip_address: String = "127.0.0.1",
    val created_at: String = ""
)

sealed class JsonIdListResult {
    data class Valid(val ids: List<Int>) : JsonIdListResult()
    data class Invalid(val reason: String) : JsonIdListResult()
}

fun parseJsonIdList(jsonString: String): JsonIdListResult {
    val trimmed = jsonString.trim()
    if (trimmed.isEmpty() || !trimmed.startsWith("[") || !trimmed.endsWith("]")) {
        return JsonIdListResult.Invalid("Dữ liệu JSON rỗng hoặc không có định dạng mảng [ ... ]")
    }
    return try {
        val arr = org.json.JSONArray(trimmed)
        val list = mutableListOf<Int>()
        for (i in 0 until arr.length()) {
            val elem = arr.get(i)
            if (elem !is Int && elem !is Long) {
                return JsonIdListResult.Invalid("Phần tử tại vị trí ${i + 1} ('$elem') không phải số nguyên hợp lệ")
            }
            list.add((elem as Number).toInt())
        }
        JsonIdListResult.Valid(list)
    } catch (e: Throwable) {
        JsonIdListResult.Invalid("Lỗi cú pháp JSON: ${e.message ?: "Chuỗi JSON hỏng"}")
    }
}

@Entity(tableName = "loops_v2")
@Serializable
data class LoopV2(
    @PrimaryKey val id: Int,
    val name: String,
    val stationAId: Int,
    val feederAId: Int,
    val sideADeviceIdsJson: String, // Serialized List<Int>
    val legalBoundaryDeviceId: Int,
    val sideBDeviceIdsJson: String, // Serialized List<Int>
    val feederBId: Int,
    val stationBId: Int,
    val layout: String = "",
    val version: Int = 1,
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    fun parseSideAJson(): JsonIdListResult = parseJsonIdList(sideADeviceIdsJson)
    fun parseSideBJson(): JsonIdListResult = parseJsonIdList(sideBDeviceIdsJson)

    fun getSideADeviceIds(): List<Int> = when (val res = parseSideAJson()) {
        is JsonIdListResult.Valid -> res.ids
        is JsonIdListResult.Invalid -> emptyList()
    }

    fun getSideBDeviceIds(): List<Int> = when (val res = parseSideBJson()) {
        is JsonIdListResult.Valid -> res.ids
        is JsonIdListResult.Invalid -> emptyList()
    }

    fun isSideAJsonCorrupted(): Boolean = parseSideAJson() is JsonIdListResult.Invalid
    fun isSideBJsonCorrupted(): Boolean = parseSideBJson() is JsonIdListResult.Invalid
}

data class LoopV2Dto(
    val id: Int,
    val name: String,
    val stationAId: Int,
    val stationAName: String,
    val feederAId: Int,
    val feederAName: String,
    val sideADevices: List<Device>,
    val legalBoundaryDeviceId: Int,
    val legalBoundaryDevice: Device?,
    val sideBDevices: List<Device>,
    val feederBId: Int,
    val feederBName: String,
    val stationBId: Int,
    val stationBName: String,
    val layout: String,
    val version: Int,
    val createdAt: String,
    val updatedAt: String,
    val isIntegrityValid: Boolean = true,
    val integrityWarning: String? = null,
    val missingDeviceIds: List<Int> = emptyList(),
    val mismatchedDeviceIds: List<Int> = emptyList(),
    val isJsonCorrupted: Boolean = false
)

@Entity(tableName = "doc_categories")
@Serializable
data class DocCategory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
)

@Entity(tableName = "library_documents")
@Serializable
data class LibraryDocument(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val displayName: String,
    val originalName: String,
    val mimeType: String,
    val category: String,
    val description: String = "",
    val tags: String = "",
    val localPath: String,
    val fileSize: Long,
    val checksumSHA256: String,
    val favorite: Int = 0,
    val deviceId: Int? = null,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class ZipPackageMeta(
    val title: String = "",
    val description: String = "",
    val exportedAt: String = "",
    val totalDocuments: Int = 0,
    val hasFullDatabase: Boolean = false,
    val totalSubstations: Int = 0,
    val totalFeeders: Int = 0,
    val totalDevices: Int = 0
)



