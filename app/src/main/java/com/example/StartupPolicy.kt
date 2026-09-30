package com.example

/**
 * StartupDataSource định nghĩa các thao tác dữ liệu liên quan đến khởi động.
 * Tách biệt interface để phục vụ kiểm thử đơn vị độc lập với Android Context / Room DB.
 */
interface StartupDataSource {
    suspend fun initializeDatabaseIfEmpty()
    suspend fun clearLoops()
    suspend fun clearLoopsV2()
    suspend fun clearDevices()
    suspend fun clearFeeders()
    suspend fun clearSubstations()
}

/**
 * StartupPolicy: Chính sách bảo vệ dữ liệu khi khởi động ứng dụng.
 * - Chỉ cho phép khởi tạo dữ liệu mặc định hệ thống (User, AuditLog, Danh mục) nếu cơ sở dữ liệu hoàn toàn rỗng.
 * - TUYỆT ĐỐI KHÔNG GỌI bất kỳ hàm xóa dữ liệu lưới điện (Substations, Feeders, Devices, Loops) khi mở app.
 */
class StartupPolicy {
    suspend fun executeStartup(dataSource: StartupDataSource) {
        // Chỉ khởi tạo dữ liệu mặc định hệ thống nếu rỗng, TUYỆT ĐỐI KHÔNG xóa dữ liệu lưới điện của người dùng
        dataSource.initializeDatabaseIfEmpty()
    }
}
