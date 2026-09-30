package com.example.library

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * Data Transfer Object nhận từ API từ xa
 */
data class RemoteDocumentDto(
    val id: String,
    val title: String,
    val category: String,
    val fileUrl: String,
    val fileSize: Long,
    val fileType: String,
    val updatedAt: String
)

/**
 * Interface Retrofit cho các thao tác tải tài liệu thư viện từ máy chủ quản lý lưới điện.
 */
interface LibraryApiService {

    /**
     * Lấy danh mục tài liệu mới nhất từ máy chủ
     */
    @GET("api/v1/library/documents")
    suspend fun getDocuments(): Response<List<RemoteDocumentDto>>

    /**
     * Tải nội dung tệp nhị phân ngầm bằng streaming để không tốn bộ nhớ RAM (hỗ trợ tệp PDF lớn và ảnh sơ đồ)
     */
    @Streaming
    @GET
    suspend fun downloadFileStream(@Url fileUrl: String): Response<ResponseBody>
}
