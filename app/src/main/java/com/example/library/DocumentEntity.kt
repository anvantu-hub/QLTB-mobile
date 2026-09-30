package com.example.library

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Thực thể tài liệu kỹ thuật lưới điện 22kV lưu trữ trong Room Database.
 * Phục vụ tra cứu Offline tại hiện trường ngay cả khi mất sóng viễn thông.
 */
@Entity(tableName = "documents")
@Serializable
data class DocumentEntity(
    @PrimaryKey
    val id: String, // Mã tài liệu duy nhất (ví dụ: "DOC-22KV-001")
    val title: String, // Tên tài liệu / quy trình / sơ đồ
    val category: String, // Danh mục: "Quy trình thao tác", "Sơ đồ 22kV", "Hướng dẫn LBS/Recloser",...
    val fileUrl: String, // Đường dẫn tải tệp từ máy chủ
    val localPath: String? = null, // Đường dẫn lưu tệp cục bộ trên bộ nhớ máy (khi đã tải)
    val fileSize: Long = 0L, // Kích thước tệp (bytes)
    val fileType: String = "PDF", // Loại tài liệu: "PDF", "IMAGE" (Bản vẽ sơ đồ)
    val isDownloaded: Boolean = false, // Trạng thái sẵn sàng mở Offline
    val isFavorite: Boolean = false, // Đánh dấu tài liệu hay dùng / yêu thích
    val updatedAt: String = "" // Thời gian cập nhật gần nhất
)
