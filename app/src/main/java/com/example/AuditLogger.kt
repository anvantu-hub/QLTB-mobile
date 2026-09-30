package com.example

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tiện ích ghi vết kiểm toán (AuditLogger) cục bộ trong hệ thống Sổ tay điện tử.
 * 
 * Cam kết kiến trúc:
 * - Hoạt động 100% Offline trên cơ sở dữ liệu Room SQLite cục bộ.
 * - Tuyệt đối KHÔNG có bất kỳ network call hoặc remote telemetry nào.
 * - Không phụ thuộc external authentication / Firebase / Cloud.
 * - Ghi trực tiếp vào bảng [AuditLog] thông qua Repository.
 */
class AuditLogger(private val repository: Repository) {

    companion object {
        // Hằng số Action cho Tài liệu (Document)
        const val ACTION_DOCUMENT_ADD = "DOCUMENT_ADD"
        const val ACTION_DOCUMENT_EDIT = "DOCUMENT_EDIT"
        const val ACTION_DOCUMENT_DELETE = "DOCUMENT_DELETE"
        const val ACTION_DOCUMENT_LINK_DEVICE = "DOCUMENT_LINK_DEVICE"
        const val ACTION_DOCUMENT_UNLINK_DEVICE = "DOCUMENT_UNLINK_DEVICE"
        const val ACTION_DOCUMENT_FAVORITE = "DOCUMENT_FAVORITE"
        const val ACTION_DOCUMENT_OPEN = "DOCUMENT_OPEN"
        const val ACTION_DOCUMENT_SHARE = "DOCUMENT_SHARE"
        const val ACTION_DOCUMENT_CATEGORY_ADD = "DOCUMENT_CATEGORY_ADD"
        const val ACTION_DOCUMENT_CATEGORY_EDIT = "DOCUMENT_CATEGORY_EDIT"
        const val ACTION_DOCUMENT_CATEGORY_DELETE = "DOCUMENT_CATEGORY_DELETE"
        const val ACTION_DOCUMENT_BACKUP = "DOCUMENT_BACKUP"
        const val ACTION_DOCUMENT_RESTORE = "DOCUMENT_RESTORE"
        const val ACTION_EXPORT_ZIP = "EXPORT_ZIP"
        const val ACTION_IMPORT_ZIP = "IMPORT_ZIP"

        // Hằng số Action cho Thiết bị & Lưới điện
        const val ACTION_DEVICE_ADD = "DEVICE_ADD"
        const val ACTION_DEVICE_EDIT = "DEVICE_EDIT"
        const val ACTION_DEVICE_DELETE = "DEVICE_DELETE"
        const val ACTION_DEVICE_STATUS_CHANGE = "DEVICE_STATUS_CHANGE"

        const val ACTION_SUBSTATION_ADD = "SUBSTATION_ADD"
        const val ACTION_SUBSTATION_EDIT = "SUBSTATION_EDIT"
        const val ACTION_SUBSTATION_DELETE = "SUBSTATION_DELETE"

        const val ACTION_FEEDER_ADD = "FEEDER_ADD"
        const val ACTION_FEEDER_EDIT = "FEEDER_EDIT"
        const val ACTION_FEEDER_DELETE = "FEEDER_DELETE"

        const val ACTION_LOOP_ADD = "LOOP_ADD"
        const val ACTION_LOOP_EDIT = "LOOP_EDIT"
        const val ACTION_LOOP_DELETE = "LOOP_DELETE"
        const val ACTION_LOOP_V2_SAVE = "LOOP_V2_SAVE"

        // Hằng số Action cho Xác thực & Hệ thống cục bộ
        const val ACTION_LOGIN = "LOGIN"
        const val ACTION_LOGOUT = "LOGOUT"

        // Module
        const val MODULE_DOCUMENT = "DOCUMENT"
        const val MODULE_LIBRARY = "LIBRARY"
        const val MODULE_DEVICE = "DEVICE"
        const val MODULE_SUBSTATION = "SUBSTATION"
        const val MODULE_FEEDER = "FEEDER"
        const val MODULE_LOOP = "LOOP"
        const val MODULE_LOOP_V2 = "LOOP_V2"
        const val MODULE_AUTH = "AUTH"
        const val MODULE_SYSTEM = "SYSTEM"

        const val LOCAL_IP = "127.0.0.1 (LOCAL_OFFLINE)"

        fun getCurrentTimestamp(): String {
            return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        }

        fun createLog(
            action: String,
            module: String,
            targetId: String? = null,
            details: String,
            result: String = "SUCCESS",
            user: User? = null
        ): AuditLog {
            return AuditLog(
                id = 0,
                username = user?.username ?: "OFFLINE_STAFF",
                user_fullname = user?.full_name ?: "Nhân viên vận hành",
                action = action,
                module = module,
                target_id = targetId ?: "",
                details = details,
                result = result,
                ip_address = LOCAL_IP,
                created_at = getCurrentTimestamp()
            )
        }
    }

    /**
     * Ghi một sự kiện tùy chỉnh vào bảng audit_logs.
     */
    suspend fun logEvent(
        action: String,
        module: String,
        targetId: String?,
        details: String,
        result: String = "SUCCESS",
        user: User? = null
    ): AuditLog {
        val log = createLog(
            action = action,
            module = module,
            targetId = targetId,
            details = details,
            result = result,
            user = user
        )
        repository.insertAuditLog(log)
        return log
    }

    /**
     * Ghi vết khi thêm tài liệu mới (DOCUMENT_ADD).
     */
    suspend fun logDocumentAdd(
        docId: String,
        displayName: String,
        category: String,
        fileSize: Long,
        deviceId: Int? = null,
        user: User? = null
    ): AuditLog {
        val deviceText = if (deviceId != null) " • Thiết bị #$deviceId" else ""
        return logEvent(
            action = ACTION_DOCUMENT_ADD,
            module = MODULE_DOCUMENT,
            targetId = docId,
            details = "Thêm tài liệu: '$displayName' (Danh mục: $category, ${formatSize(fileSize)})$deviceText",
            user = user
        )
    }

    /**
     * Ghi vết khi chỉnh sửa thông tin tài liệu (DOCUMENT_EDIT).
     */
    suspend fun logDocumentEdit(
        docId: String,
        displayName: String,
        changes: String,
        user: User? = null
    ): AuditLog {
        return logEvent(
            action = ACTION_DOCUMENT_EDIT,
            module = MODULE_DOCUMENT,
            targetId = docId,
            details = "Cập nhật tài liệu '$displayName': $changes",
            user = user
        )
    }

    /**
     * Ghi vết khi xóa tài liệu (DOCUMENT_DELETE).
     */
    suspend fun logDocumentDelete(
        docId: String,
        displayName: String,
        user: User? = null
    ): AuditLog {
        return logEvent(
            action = ACTION_DOCUMENT_DELETE,
            module = MODULE_DOCUMENT,
            targetId = docId,
            details = "Xóa tài liệu khỏi bộ nhớ nội bộ: '$displayName'",
            user = user
        )
    }

    /**
     * Ghi vết khi liên kết hoặc hủy liên kết tài liệu với thiết bị (DOCUMENT_LINK_DEVICE).
     */
    suspend fun logDocumentLinkDevice(
        docId: String,
        displayName: String,
        deviceId: Int?,
        user: User? = null
    ): AuditLog {
        val action = if (deviceId != null) ACTION_DOCUMENT_LINK_DEVICE else ACTION_DOCUMENT_UNLINK_DEVICE
        val desc = if (deviceId != null) {
            "Liên kết tài liệu '$displayName' với thiết bị #$deviceId"
        } else {
            "Hủy liên kết tài liệu '$displayName' khỏi thiết bị"
        }
        return logEvent(
            action = action,
            module = MODULE_DOCUMENT,
            targetId = docId,
            details = desc,
            user = user
        )
    }

    /**
     * Ghi vết khi đánh dấu/bỏ yêu thích tài liệu (DOCUMENT_FAVORITE).
     */
    suspend fun logDocumentFavorite(
        docId: String,
        displayName: String,
        isFavorite: Boolean,
        user: User? = null
    ): AuditLog {
        val statusText = if (isFavorite) "Đánh dấu yêu thích" else "Bỏ đánh dấu yêu thích"
        return logEvent(
            action = ACTION_DOCUMENT_FAVORITE,
            module = MODULE_DOCUMENT,
            targetId = docId,
            details = "$statusText tài liệu: '$displayName'",
            user = user
        )
    }

    /**
     * Ghi vết khi mở đọc tài liệu (DOCUMENT_OPEN).
     */
    suspend fun logDocumentOpen(
        docId: String,
        displayName: String,
        user: User? = null
    ): AuditLog {
        return logEvent(
            action = ACTION_DOCUMENT_OPEN,
            module = MODULE_DOCUMENT,
            targetId = docId,
            details = "Mở xem tài liệu nội bộ: '$displayName'",
            user = user
        )
    }

    /**
     * Ghi vết khi chia sẻ tài liệu qua FileProvider (DOCUMENT_SHARE).
     */
    suspend fun logDocumentShare(
        docId: String,
        displayName: String,
        user: User? = null
    ): AuditLog {
        return logEvent(
            action = ACTION_DOCUMENT_SHARE,
            module = MODULE_DOCUMENT,
            targetId = docId,
            details = "Chia sẻ tệp an toàn qua FileProvider: '$displayName'",
            user = user
        )
    }

    /**
     * Ghi vết khi xuất gói ZIP dữ liệu/tài liệu (EXPORT_ZIP).
     */
    suspend fun logExportZip(
        fileName: String,
        details: String,
        user: User? = null
    ): AuditLog {
        return logEvent(
            action = ACTION_EXPORT_ZIP,
            module = MODULE_SYSTEM,
            targetId = fileName,
            details = details,
            user = user
        )
    }

    /**
     * Ghi vết khi nhập gói ZIP dữ liệu/tài liệu (IMPORT_ZIP).
     */
    suspend fun logImportZip(
        fileName: String,
        details: String,
        user: User? = null
    ): AuditLog {
        return logEvent(
            action = ACTION_IMPORT_ZIP,
            module = MODULE_SYSTEM,
            targetId = fileName,
            details = details,
            user = user
        )
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val formatted = String.format(Locale.US, "%.1f", bytes / Math.pow(1024.0, digitGroups.toDouble()))
        return "$formatted ${units.getOrElse(digitGroups) { "B" }}"
    }
}
