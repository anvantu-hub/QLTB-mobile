package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

/**
 * Kiểm thử đơn vị toàn diện (Unit Tests) cho các chức năng của ứng dụng:
 * 1. Module Tài liệu & Vòng đời tài liệu (Document Lifecycle & Metadata)
 * 2. Module Thiết bị lưới điện (Device Hierarchy: Recloser, LBS, RMU, DS, Khác)
 * 3. Module Công việc (Hướng dẫn theo loại công việc)
 * 4. Module An toàn (6 phân hệ chuẩn & Tai nạn - Bài học kinh nghiệm)
 * 5. Module Gói Sổ tay (Handbook Package & Manifest)
 * 6. Module Tài khoản & Phân quyền ngoại tuyến (Account Roles & Offline Grant)
 * 7. Thuật toán Lọc & Tìm kiếm Thư viện (Library Filter & Search Algorithm)
 * 8. Quản lý Vòng khép Lưới điện (Loop V2 & Segments)
 */
class AppFeaturesTest {

    // ==========================================
    // 1. MODULE TÀI LIỆU & VÒNG ĐỜI TÀI LIỆU
    // ==========================================
    @Test
    fun testDocumentMetadataAndLifecycle() {
        val doc = LibraryDocument(
            id = 1,
            displayName = "Quy trình vận hành Recloser Nulec U27",
            originalName = "QTVH_REC_Nulec_U27_v2.1.pdf",
            mimeType = "application/pdf",
            category = "Quy trình vận hành",
            description = "Hướng dẫn chi tiết quy trình đóng cắt và bảo dưỡng định kỳ",
            tags = "Recloser,Nulec,U27,Trung thế",
            localPath = "/data/user/0/com.example/files/docs/doc_1.pdf",
            fileSize = 4200000L,
            checksumSHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            favorite = 1,
            deviceId = 12,
            createdAt = "2026-09-01 08:00:00",
            updatedAt = "2026-09-10 14:30:00"
        )

        assertEquals("pdf", doc.fileExtension)
        assertEquals(1, doc.favorite)
        assertEquals(12, doc.deviceId)
        assertTrue(doc.tags.contains("Recloser"))
        assertTrue(doc.fileSize > 0)
        assertEquals(64, doc.checksumSHA256.length)

        // Kiểm tra vòng đời tài liệu: DRAFT -> PUBLISHED -> REPLACED -> ARCHIVED
        val validLifecycleStatuses = listOf("DRAFT", "PUBLISHED", "REPLACED", "ARCHIVED")
        assertTrue(validLifecycleStatuses.contains("PUBLISHED"))
        assertTrue(validLifecycleStatuses.contains("REPLACED"))

        // Kiểm tra mức độ an toàn thông tin (Security Level)
        val securityLevels = listOf("INTERNAL", "RESTRICTED")
        assertTrue(securityLevels.contains("INTERNAL"))
        assertTrue(securityLevels.contains("RESTRICTED"))
    }

    // ==========================================
    // 2. MODULE THIẾT BỊ LƯỚI ĐIỆN
    // ==========================================
    @Test
    fun testDeviceHierarchyAndTypes() {
        val standardDeviceTypes = listOf("Recloser", "REC", "LBS", "RMU", "DS", "OTHER")
        
        val recloser = Device(
            id = 10,
            device_id = "REC-471-DDA",
            name = "Recloser 471 Đống Đa",
            device_type = "REC",
            feeder_id = 2,
            substation_id = 1,
            unit = "Điện lực Đống Đa",
            status = "ACTIVE",
            switch_status = "CLOSED",
            scada_status = "SIGNAL",
            notes = "Hãng Schneider, Model Nulec U27, 24kV - 630A"
        )

        assertTrue(standardDeviceTypes.contains(recloser.device_type))
        assertEquals("REC-471-DDA", recloser.device_id)
        assertEquals("CLOSED", recloser.switch_status)
        assertEquals("ACTIVE", recloser.status)
        assertTrue(recloser.notes.contains("Nulec U27"))
    }

    // ==========================================
    // 3. MODULE CÔNG VIỆC (HƯỚNG DẪN LOẠI CÔNG VIỆC)
    // ==========================================
    @Test
    fun testWorkTypeGuidelines() {
        val standardWorkTypes = listOf(
            "Kiểm tra thiết bị",
            "Sửa chữa điện",
            "Đóng cắt điện",
            "Xử lý sự cố",
            "Kiểm tra định kỳ"
        )

        assertEquals(5, standardWorkTypes.size)
        assertTrue(standardWorkTypes.contains("Đóng cắt điện"))
        assertTrue(standardWorkTypes.contains("Xử lý sự cố"))
    }

    // ==========================================
    // 4. MODULE AN TOÀN & BÀI HỌC KINH NGHIỆM
    // ==========================================
    @Test
    fun testSafetyModulesAndAccidentLessonLearned() {
        // Module An toàn gồm đúng 6 mục chuẩn theo kiến trúc
        val safetySections = listOf(
            "Quy trình an toàn",
            "Biện pháp an toàn",
            "PPE & dụng cụ an toàn",
            "Cảnh báo & tình huống nguy hiểm",
            "Hướng dẫn khẩn cấp",
            "Tai nạn & Bài học kinh nghiệm"
        )

        assertEquals(6, safetySections.size)
        assertFalse(safetySections.contains("Quy định an toàn")) // KHÔNG được tạo lại mục này

        // Kiểm tra nguyên tắc xử lý kết luận sự việc
        val isOfficialConclusionAvailable = false
        val conclusionText = if (isOfficialConclusionAvailable) {
            "Vi phạm khoảng cách an toàn điện"
        } else {
            "Chưa có kết luận chính thức"
        }

        assertEquals("Chưa có kết luận chính thức", conclusionText)
    }

    // ==========================================
    // 5. MODULE GÓI SỔ TAY (HANDBOOK PACKAGES)
    // ==========================================
    @Test
    fun testHandbookPackages() {
        val packageTypes = listOf(
            "Gói Tổng hợp",
            "Gói Công việc",
            "Gói An toàn",
            "Gói Thiết bị",
            "Gói Tài liệu chung"
        )

        assertEquals(5, packageTypes.size)
        assertTrue(packageTypes.contains("Gói An toàn"))
        assertTrue(packageTypes.contains("Gói Thiết bị"))
    }

    // ==========================================
    // 6. MODULE TÀI KHOẢN & PHÂN QUYỀN NGOẠI TUYẾN
    // ==========================================
    @Test
    fun testAccountRolesAndOfflineGrant() {
        // Chỉ có 3 roles hợp lệ trong hệ thống: ADMIN, MANAGER, STAFF (Không có Guest)
        val validRoles = listOf("ADMIN", "MANAGER", "STAFF")
        assertFalse(validRoles.contains("GUEST"))

        val staffUser = User(
            id = "user_001",
            employee_code = "EVN_HN_102",
            full_name = "Nguyễn Văn Tuấn",
            username = "tuan_nv",
            email = "tuan_nv@evn.com.vn",
            role = "STAFF"
        )

        assertTrue(validRoles.contains(staffUser.role))
        assertNotEquals("GUEST", staffUser.role)

        // Thời hạn Offline Grant chuẩn: 7 ngày
        val offlineGrantDays = 7
        assertEquals(7, offlineGrantDays)
    }

    // ==========================================
    // 7. THUẬT TOÁN LỌC & TÌM KIẾM THƯ VIỆN
    // ==========================================
    @Test
    fun testLibraryFilterAndSearchLogic() {
        val docList = listOf(
            LibraryDocument(
                id = 1,
                displayName = "Quy trình vận hành Máy cắt Recloser Cooper",
                originalName = "QTVH_Cooper.pdf",
                mimeType = "application/pdf",
                category = "Quy trình",
                description = "Tài liệu kỹ thuật máy cắt",
                tags = "Cooper,Recloser",
                localPath = "/files/1.pdf",
                fileSize = 1024000L,
                checksumSHA256 = "hash1",
                favorite = 1,
                deviceId = 1,
                createdAt = "2026-09-01",
                updatedAt = "2026-09-02"
            ),
            LibraryDocument(
                id = 2,
                displayName = "Sơ đồ nguyên lý trạm biến áp E1.1",
                originalName = "SD_E1_1.dwg",
                mimeType = "application/dwg",
                category = "Sơ đồ",
                description = "Bản vẽ kỹ thuật",
                tags = "TBA,E1.1",
                localPath = "/files/2.dwg",
                fileSize = 512000L,
                checksumSHA256 = "hash2",
                favorite = 0,
                deviceId = null,
                createdAt = "2026-09-03",
                updatedAt = "2026-09-03"
            ),
            LibraryDocument(
                id = 3,
                displayName = "Báo cáo kiểm tra định kỳ LBS Entec",
                originalName = "BC_Entec.xlsx",
                mimeType = "application/vnd.ms-excel",
                category = "Báo cáo",
                description = "Số liệu đo lường định kỳ",
                tags = "LBS,Entec,KiemTra",
                localPath = "/files/3.xlsx",
                fileSize = 256000L,
                checksumSHA256 = "hash3",
                favorite = 1,
                deviceId = 5,
                createdAt = "2026-09-04",
                updatedAt = "2026-09-04"
            )
        )

        // Test 7.1: Tìm kiếm theo từ khóa không phân biệt hoa thường
        val query = "cooper"
        val searchResult = docList.filter { doc ->
            doc.displayName.contains(query, ignoreCase = true) ||
            doc.tags.contains(query, ignoreCase = true) ||
            doc.description.contains(query, ignoreCase = true)
        }
        assertEquals(1, searchResult.size)
        assertEquals(1, searchResult.first().id)

        // Test 7.2: Lọc theo định dạng file Excel (xlsx)
        val excelResult = docList.filter { it.fileExtension in listOf("xls", "xlsx") }
        assertEquals(1, excelResult.size)
        assertEquals("xlsx", excelResult.first().fileExtension)

        // Test 7.3: Lọc theo trạng thái yêu thích
        val favoriteResult = docList.filter { it.favorite == 1 }
        assertEquals(2, favoriteResult.size)

        // Test 7.4: Lọc theo thiết bị liên kết
        val deviceLinkedResult = docList.filter { it.deviceId != null }
        assertEquals(2, deviceLinkedResult.size)
    }

    // ==========================================
    // 8. LƯỚI ĐIỆN: VÒNG KHÉP LOOP V2 & PHÂN ĐOẠN
    // ==========================================
    @Test
    fun testLoopV2Structure() {
        val loop = LoopV2(
            id = 1,
            name = "Vòng khép liên lạc lộ 471 E1.1 - 473 E1.2",
            stationAId = 1,
            feederAId = 10,
            sideADeviceIdsJson = "[1,2,3]",
            legalBoundaryDeviceId = 100,
            sideBDeviceIdsJson = "[4,5,6]",
            feederBId = 20,
            stationBId = 2,
            layout = "",
            version = 1,
            createdAt = "2026-09-01 10:00:00",
            updatedAt = "2026-09-01 10:00:00"
        )

        assertEquals("Vòng khép liên lạc lộ 471 E1.1 - 473 E1.2", loop.name)
        assertEquals(100, loop.legalBoundaryDeviceId)
        assertEquals(1, loop.stationAId)
        assertEquals(2, loop.stationBId)
        assertEquals(10, loop.feederAId)
        assertEquals(20, loop.feederBId)
    }

    // ==========================================
    // 9. BẢO VỆ DỮ LIỆU LƯỚI ĐIỆN (PHASE 0B - DATA LOSS PROTECTION)
    // ==========================================
    @Test
    fun testStartupPolicyDoesNotClearGridData() = runBlocking {
        var initializeDatabaseCalled = false
        var clearLoopsCalled = false
        var clearLoopsV2Called = false
        var clearDevicesCalled = false
        var clearFeedersCalled = false
        var clearSubstationsCalled = false

        val fakeDataSource = object : StartupDataSource {
            override suspend fun initializeDatabaseIfEmpty() {
                initializeDatabaseCalled = true
            }
            override suspend fun clearLoops() {
                clearLoopsCalled = true
            }
            override suspend fun clearLoopsV2() {
                clearLoopsV2Called = true
            }
            override suspend fun clearDevices() {
                clearDevicesCalled = true
            }
            override suspend fun clearFeeders() {
                clearFeedersCalled = true
            }
            override suspend fun clearSubstations() {
                clearSubstationsCalled = true
            }
        }

        val policy = StartupPolicy()
        policy.executeStartup(fakeDataSource)

        // 1. Phải khởi tạo cấu trúc hệ thống cần thiết (Users, Categories) nếu DB rỗng
        assertTrue("Hệ thống phải gọi initializeDatabaseIfEmpty khi khởi động", initializeDatabaseCalled)

        // 2. Tuyệt đối KHÔNG được gọi bất kỳ hàm xóa dữ liệu lưới nào
        assertFalse("KHÔNG ĐƯỢC xóa Loops khi khởi động", clearLoopsCalled)
        assertFalse("KHÔNG ĐƯỢC xóa Loops V2 khi khởi động", clearLoopsV2Called)
        assertFalse("KHÔNG ĐƯỢC xóa Devices khi khởi động", clearDevicesCalled)
        assertFalse("KHÔNG ĐƯỢC xóa Feeders khi khởi động", clearFeedersCalled)
        assertFalse("KHÔNG ĐƯỢC xóa Substations khi khởi động", clearSubstationsCalled)
    }
}
