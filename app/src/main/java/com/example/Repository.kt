package com.example

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class OrphanDataReport(
    val feedersMissingSubstation: Int,
    val devicesMissingFeeder: Int,
    val devicesMismatchedSubstation: Int
)

sealed class LoopV2SaveResult {
    data class Success(val loopId: Int, val isUpdate: Boolean) : LoopV2SaveResult()
    data class ValidationError(val message: String) : LoopV2SaveResult()
    data class VersionConflict(val message: String) : LoopV2SaveResult()
    data class Error(val message: String) : LoopV2SaveResult()
}

class Repository(private val db: AppDatabase) : StartupDataSource {

    suspend fun auditOrphanData(): OrphanDataReport {
        val substations = db.substationDao().getAllSubstationsNow().associateBy { it.id }
        val feeders = db.feederDao().getAllFeedersNow().associateBy { it.id }
        val devices = db.deviceDao().getAllDevicesNow()

        val feedersMissingSubstation = feeders.values.count { f ->
            !substations.containsKey(f.substation_id)
        }

        val devicesMissingFeeder = devices.count { d ->
            d.feeder_id == null || !feeders.containsKey(d.feeder_id)
        }

        val devicesMismatchedSubstation = devices.count { d ->
            val parentFeeder = feeders[d.feeder_id]
            parentFeeder != null && d.substation_id != null && d.substation_id != parentFeeder.substation_id
        }

        return OrphanDataReport(
            feedersMissingSubstation = feedersMissingSubstation,
            devicesMissingFeeder = devicesMissingFeeder,
            devicesMismatchedSubstation = devicesMismatchedSubstation
        )
    }

    val allUsers: Flow<List<User>> = db.userDao().getAllUsers()

    val allSubstations: Flow<List<Substation>> = db.substationDao().getAllSubstations()
    val allFeeders: Flow<List<Feeder>> = db.feederDao().getAllFeeders()
    val allDevices: Flow<List<Device>> = db.deviceDao().getAllDevices()
    val allLoops: Flow<List<Loop>> = db.loopDao().getAllLoops()
    val allLoopsV2: Flow<List<LoopV2>> = db.loopV2Dao().getAllLoopsV2()
    val allAuditLogs: Flow<List<AuditLog>> = db.auditLogDao().getAllAuditLogs()

    suspend fun insertLoopV2(loop: LoopV2) = db.loopV2Dao().insert(loop)
    suspend fun insertAllLoopsV2(loops: List<LoopV2>) = db.loopV2Dao().insertAll(loops)
    suspend fun deleteLoopV2(loop: LoopV2) = db.loopV2Dao().delete(loop)
    suspend fun deleteLoopV2ById(id: Int) = db.loopV2Dao().deleteById(id)
    suspend fun getLoopV2ById(id: Int): LoopV2? = db.loopV2Dao().getLoopById(id)
    override suspend fun clearLoopsV2() = db.loopV2Dao().clearLoopsV2()

    suspend fun validateAndSaveLoopV2(loopToSave: LoopV2, isUpdate: Boolean): LoopV2SaveResult {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val nowStr = sdf.format(Date())

        return try {
            db.withTransaction {
                // 1. JSON corruption check
                val resA = loopToSave.parseSideAJson()
                if (resA is JsonIdListResult.Invalid) {
                    return@withTransaction LoopV2SaveResult.ValidationError("Danh sách thiết bị Phía A bị hỏng JSON: ${resA.reason}")
                }
                val resB = loopToSave.parseSideBJson()
                if (resB is JsonIdListResult.Invalid) {
                    return@withTransaction LoopV2SaveResult.ValidationError("Danh sách thiết bị Phía B bị hỏng JSON: ${resB.reason}")
                }

                // 2. Direct DB validation for Substations
                val stationA = db.substationDao().getSubstationByIdDirect(loopToSave.stationAId)
                val stationB = db.substationDao().getSubstationByIdDirect(loopToSave.stationBId)
                if (stationA == null || stationB == null) {
                    return@withTransaction LoopV2SaveResult.ValidationError("Trạm biến áp A hoặc Trạm B không tồn tại trong cơ sở dữ liệu!")
                }

                // 3. Direct DB validation for Feeders
                if (loopToSave.feederAId == loopToSave.feederBId) {
                    return@withTransaction LoopV2SaveResult.ValidationError("Phát tuyến Lộ A và Lộ B phải khác nhau!")
                }

                val feederA = db.feederDao().getFeederByIdDirect(loopToSave.feederAId)
                val feederB = db.feederDao().getFeederByIdDirect(loopToSave.feederBId)
                if (feederA == null || feederA.substation_id != loopToSave.stationAId) {
                    return@withTransaction LoopV2SaveResult.ValidationError("Phát tuyến Lộ A không tồn tại hoặc không thuộc Trạm A!")
                }
                if (feederB == null || feederB.substation_id != loopToSave.stationBId) {
                    return@withTransaction LoopV2SaveResult.ValidationError("Phát tuyến Lộ B không tồn tại hoặc không thuộc Trạm B!")
                }

                // 4. Validate Side A devices
                val sideADeviceIds = (resA as JsonIdListResult.Valid).ids
                if (sideADeviceIds.isEmpty()) {
                    return@withTransaction LoopV2SaveResult.ValidationError("Danh sách thiết bị phía A không được để rỗng (Phải chọn ít nhất 1 thiết bị)!")
                }
                for (devId in sideADeviceIds) {
                    val dev = db.deviceDao().getDeviceById(devId)
                    if (dev == null) {
                        return@withTransaction LoopV2SaveResult.ValidationError("Thiết bị phía A (ID: $devId) không tồn tại trong cơ sở dữ liệu!")
                    }
                    if (dev.feeder_id != loopToSave.feederAId) {
                        return@withTransaction LoopV2SaveResult.ValidationError("Thiết bị '${dev.name}' (phía A) không thuộc Phát tuyến Lộ A!")
                    }
                }

                // 5. Validate Side B devices
                val sideBDeviceIds = (resB as JsonIdListResult.Valid).ids
                if (sideBDeviceIds.isEmpty()) {
                    return@withTransaction LoopV2SaveResult.ValidationError("Danh sách thiết bị phía B không được để rỗng (Phải chọn ít nhất 1 thiết bị)!")
                }
                for (devId in sideBDeviceIds) {
                    val dev = db.deviceDao().getDeviceById(devId)
                    if (dev == null) {
                        return@withTransaction LoopV2SaveResult.ValidationError("Thiết bị phía B (ID: $devId) không tồn tại trong cơ sở dữ liệu!")
                    }
                    if (dev.feeder_id != loopToSave.feederBId) {
                        return@withTransaction LoopV2SaveResult.ValidationError("Thiết bị '${dev.name}' (phía B) không thuộc Phát tuyến Lộ B!")
                    }
                }

                // 6. Validate Legal Boundary Device
                val boundaryDev = db.deviceDao().getDeviceById(loopToSave.legalBoundaryDeviceId)
                if (boundaryDev == null) {
                    return@withTransaction LoopV2SaveResult.ValidationError("Thiết bị làm Điểm dừng pháp lý không tồn tại trong cơ sở dữ liệu!")
                }

                val boundarySubstationId = boundaryDev.substation_id
                val boundaryFeeder = boundaryDev.feeder_id?.let { db.feederDao().getFeederByIdDirect(it) }

                if (boundaryDev.feeder_id != null && boundaryFeeder == null) {
                    return@withTransaction LoopV2SaveResult.ValidationError("Thiết bị điểm dừng '${boundaryDev.name}' có Phát tuyến cha (ID: ${boundaryDev.feeder_id}) không tồn tại!")
                }

                val boundaryFeederSubstationId = boundaryFeeder?.substation_id
                if (boundaryFeeder != null && boundarySubstationId != null && boundarySubstationId != boundaryFeederSubstationId) {
                    return@withTransaction LoopV2SaveResult.ValidationError("Thiết bị điểm dừng '${boundaryDev.name}' có mâu thuẫn giữa Trạm biến áp ($boundarySubstationId) và Trạm của phát tuyến cha ($boundaryFeederSubstationId)!")
                }

                val effectiveBoundarySubstationId = boundaryFeederSubstationId ?: boundarySubstationId
                if (effectiveBoundarySubstationId == null || effectiveBoundarySubstationId != loopToSave.stationAId) {
                    return@withTransaction LoopV2SaveResult.ValidationError("Thiết bị làm Điểm dừng pháp lý phải thuộc Trạm A!")
                }

                // 7. Duplicate device check
                val allSelectedIds = sideADeviceIds + sideBDeviceIds + listOf(loopToSave.legalBoundaryDeviceId)
                val duplicates = allSelectedIds.groupBy { it }.filter { it.value.size > 1 }.keys
                if (duplicates.isNotEmpty()) {
                    val dupNames = duplicates.mapNotNull { db.deviceDao().getDeviceById(it)?.name ?: "ID: $it" }
                    return@withTransaction LoopV2SaveResult.ValidationError("Lỗi trùng lặp thiết bị trong sơ đồ khép vòng: ${dupNames.joinToString(", ")}!")
                }

                // 8. Atomically Create or Update
                if (!isUpdate) {
                    val maxId = db.loopV2Dao().getMaxLoopV2Id() ?: 0
                    val newId = maxId + 1
                    val finalLoop = loopToSave.copy(
                        id = newId,
                        version = 1,
                        createdAt = nowStr,
                        updatedAt = nowStr
                    )
                    val rowId = db.loopV2Dao().insertLoopV2Raw(finalLoop)
                    if (rowId <= 0) {
                        return@withTransaction LoopV2SaveResult.Error("Lỗi không thể chèn vòng khép V2 mới vào cơ sở dữ liệu.")
                    }
                    LoopV2SaveResult.Success(loopId = newId, isUpdate = false)
                } else {
                    val existing = db.loopV2Dao().getLoopById(loopToSave.id)
                    if (existing == null) {
                        return@withTransaction LoopV2SaveResult.ValidationError("Không tìm thấy bản ghi vòng khép V2 (ID: ${loopToSave.id}) để cập nhật!")
                    }
                    if (existing.version != loopToSave.version) {
                        return@withTransaction LoopV2SaveResult.VersionConflict("Dữ liệu vòng khép đã bị chỉnh sửa bởi người dùng khác (Phiên bản DB: v${existing.version}, Phiên bản đang sửa: v${loopToSave.version}). Vui lòng tải lại!")
                    }

                    val updatedCount = db.loopV2Dao().updateLoopV2Conditional(
                        id = loopToSave.id,
                        oldVersion = loopToSave.version,
                        name = loopToSave.name,
                        stationAId = loopToSave.stationAId,
                        feederAId = loopToSave.feederAId,
                        sideADeviceIdsJson = loopToSave.sideADeviceIdsJson,
                        legalBoundaryDeviceId = loopToSave.legalBoundaryDeviceId,
                        sideBDeviceIdsJson = loopToSave.sideBDeviceIdsJson,
                        feederBId = loopToSave.feederBId,
                        stationBId = loopToSave.stationBId,
                        layout = loopToSave.layout,
                        updatedAt = nowStr
                    )

                    if (updatedCount == 0) {
                        LoopV2SaveResult.VersionConflict("Cập nhật thất bại do xung đột phiên bản dữ liệu (Phiên bản đã thay đổi). Vui lòng nạp lại!")
                    } else {
                        LoopV2SaveResult.Success(loopId = loopToSave.id, isUpdate = true)
                    }
                }
            }
        } catch (e: Exception) {
            LoopV2SaveResult.Error("Lỗi hệ thống khi lưu: ${e.localizedMessage}")
        }
    }

    suspend fun insertUser(user: User) = db.userDao().insert(user)
    suspend fun insertAllUsers(users: List<User>) = db.userDao().insertAll(users)
    suspend fun updateUser(user: User) = db.userDao().update(user)
    suspend fun deleteUser(user: User) = db.userDao().delete(user)
    suspend fun getUserById(id: String): User? = db.userDao().getUserById(id)

    suspend fun insertSubstation(substation: Substation) = db.substationDao().insert(substation)
    suspend fun insertAllSubstations(substations: List<Substation>) = db.substationDao().insertAll(substations)
    suspend fun updateSubstation(substation: Substation) = db.substationDao().update(substation)
    suspend fun deleteSubstation(substation: Substation) {
        db.withTransaction {
            val activeLoops = db.loopV2Dao().getAllLoopsV2Now()
            for (loop in activeLoops) {
                if (loop.stationAId == substation.id || loop.stationBId == substation.id) {
                    throw IllegalStateException("Không thể xóa Trạm '${substation.name}' (ID: ${substation.id}) do đang là Trạm A/B của sơ đồ Khép vòng V2 '#${loop.name}' (ID: ${loop.id}).")
                }
            }
            db.substationDao().delete(substation)
        }
    }
    suspend fun getSubstationByCode(code: String): Substation? = db.substationDao().getSubstationByCode(code)
    fun observeSubstationById(id: Int): Flow<Substation?> = db.substationDao().observeSubstationById(id)
    suspend fun getSubstationByIdDirect(id: Int): Substation? = db.substationDao().getSubstationByIdDirect(id)

    suspend fun insertFeeder(feeder: Feeder) = db.feederDao().insert(feeder)
    suspend fun insertAllFeeders(feeders: List<Feeder>) = db.feederDao().insertAll(feeders)
    suspend fun deleteFeeder(feeder: Feeder) {
        db.withTransaction {
            val activeLoops = db.loopV2Dao().getAllLoopsV2Now()
            for (loop in activeLoops) {
                if (loop.feederAId == feeder.id || loop.feederBId == feeder.id) {
                    throw IllegalStateException("Không thể xóa Phát tuyến '${feeder.name}' (ID: ${feeder.id}) do đang là Lộ A/B của sơ đồ Khép vòng V2 '#${loop.name}' (ID: ${loop.id}).")
                }
                val devIds = loop.getSideADeviceIds() + loop.getSideBDeviceIds() + loop.legalBoundaryDeviceId
                for (dId in devIds) {
                    val d = db.deviceDao().getDeviceById(dId)
                    if (d != null && d.feeder_id == feeder.id) {
                        throw IllegalStateException("Không thể xóa Phát tuyến '${feeder.name}' (ID: ${feeder.id}) do chứa thiết bị '${d.name}' thuộc sơ đồ Khép vòng V2 '#${loop.name}' (ID: ${loop.id}).")
                    }
                }
            }
            db.feederDao().delete(feeder)
        }
    }
    fun observeFeedersBySubstationId(id: Int): Flow<List<Feeder>> = db.feederDao().observeFeedersBySubstationId(id)
    fun observeFeederById(id: Int): Flow<Feeder?> = db.feederDao().observeFeederById(id)
    suspend fun getFeederByIdDirect(id: Int): Feeder? = db.feederDao().getFeederByIdDirect(id)

    suspend fun insertDevice(device: Device) = db.deviceDao().insert(device)
    suspend fun insertAllDevices(devices: List<Device>) = db.deviceDao().insertAll(devices)
    suspend fun updateDevice(device: Device) {
        db.withTransaction {
            val existing = db.deviceDao().getDeviceById(device.id)
            if (existing != null && (existing.feeder_id != device.feeder_id || existing.substation_id != device.substation_id)) {
                val activeLoops = db.loopV2Dao().getAllLoopsV2Now()
                for (loop in activeLoops) {
                    val sideAIds = loop.getSideADeviceIds()
                    val sideBIds = loop.getSideBDeviceIds()
                    if (device.id in sideAIds && device.feeder_id != loop.feederAId) {
                        throw IllegalStateException("Không thể chuyển Phát tuyến của thiết bị '${device.name}' do đang thuộc Phía A của sơ đồ Khép vòng V2 '#${loop.name}'.")
                    }
                    if (device.id in sideBIds && device.feeder_id != loop.feederBId) {
                        throw IllegalStateException("Không thể chuyển Phát tuyến của thiết bị '${device.name}' do đang thuộc Phía B của sơ đồ Khép vòng V2 '#${loop.name}'.")
                    }
                    if (device.id == loop.legalBoundaryDeviceId) {
                        val newFeeder = device.feeder_id?.let { db.feederDao().getFeederByIdDirect(it) }
                        val effectiveStation = newFeeder?.substation_id ?: device.substation_id
                        if (effectiveStation != loop.stationAId) {
                            throw IllegalStateException("Không thể chuyển Lộ/Trạm của điểm dừng '${device.name}' làm sai Trạm A của sơ đồ Khép vòng V2 '#${loop.name}'.")
                        }
                    }
                }
            }
            db.deviceDao().update(device)
        }
    }
    suspend fun deleteDevice(device: Device) {
        db.withTransaction {
            val activeLoops = db.loopV2Dao().getAllLoopsV2Now()
            for (loop in activeLoops) {
                val sideAIds = loop.getSideADeviceIds()
                val sideBIds = loop.getSideBDeviceIds()
                if (device.id in sideAIds || device.id in sideBIds || device.id == loop.legalBoundaryDeviceId) {
                    throw IllegalStateException("Không thể xóa thiết bị '${device.name}' (ID: ${device.id}) do đang tham gia sơ đồ Khép vòng V2 '#${loop.name}' (ID: ${loop.id}).")
                }
            }
            db.deviceDao().delete(device)
        }
    }
    suspend fun deleteMultipleDevices(idList: List<Int>) {
        db.withTransaction {
            val activeLoops = db.loopV2Dao().getAllLoopsV2Now()
            val idSet = idList.toSet()
            for (loop in activeLoops) {
                val sideAIds = loop.getSideADeviceIds().toSet()
                val sideBIds = loop.getSideBDeviceIds().toSet()
                val loopDevIds = sideAIds + sideBIds + loop.legalBoundaryDeviceId
                val intersect = idSet.intersect(loopDevIds)
                if (intersect.isNotEmpty()) {
                    throw IllegalStateException("Không thể xóa các thiết bị (IDs: ${intersect.joinToString()}) do đang tham gia sơ đồ Khép vòng V2 '#${loop.name}' (ID: ${loop.id}).")
                }
            }
            db.deviceDao().deleteMultiple(idList)
        }
    }
    suspend fun getDeviceById(id: Int): Device? = db.deviceDao().getDeviceById(id)
    fun observeDevicesByFeederId(id: Int): Flow<List<Device>> = db.deviceDao().observeDevicesByFeederId(id)
    fun observeDevicesBySubstationId(id: Int): Flow<List<Device>> = db.deviceDao().observeDevicesBySubstationId(id)

    suspend fun insertLoop(loop: Loop) = db.loopDao().insert(loop)
    suspend fun insertAllLoops(loops: List<Loop>) = db.loopDao().insertAll(loops)
    suspend fun deleteLoop(loop: Loop) = db.loopDao().delete(loop)

    suspend fun insertAuditLog(log: AuditLog) = db.auditLogDao().insert(log)
    suspend fun clearAuditLogs() = db.auditLogDao().clearLogs()

    // Document and Category support
    val allDocCategories: Flow<List<DocCategory>> = db.docCategoryDao().getAllCategories()
    val allDocuments: Flow<List<LibraryDocument>> = db.libraryDocumentDao().getAllDocuments()

    fun getDocumentsForDevice(deviceId: Int): Flow<List<LibraryDocument>> = db.libraryDocumentDao().getDocumentsForDevice(deviceId)
    suspend fun findDocumentByChecksum(checksum: String): LibraryDocument? = db.libraryDocumentDao().findByChecksum(checksum)
    suspend fun getDocumentById(id: Int): LibraryDocument? = db.libraryDocumentDao().getDocumentById(id)
    suspend fun insertDocument(document: LibraryDocument): Long = db.libraryDocumentDao().insert(document)
    suspend fun insertAllDocuments(documents: List<LibraryDocument>) = db.libraryDocumentDao().insertAll(documents)
    suspend fun updateDocument(document: LibraryDocument) = db.libraryDocumentDao().update(document)
    suspend fun deleteDocument(document: LibraryDocument) = db.libraryDocumentDao().delete(document)
    suspend fun clearAllDocuments() = db.libraryDocumentDao().clearAllDocuments()

    suspend fun insertDocCategory(category: DocCategory) = db.docCategoryDao().insert(category)
    suspend fun insertAllDocCategories(categories: List<DocCategory>) = db.docCategoryDao().insertAll(categories)
    suspend fun updateDocCategory(category: DocCategory) = db.docCategoryDao().update(category)
    suspend fun deleteDocCategory(category: DocCategory) = db.docCategoryDao().delete(category)
    suspend fun clearDocCategories() = db.docCategoryDao().clearCategories()

    override suspend fun clearSubstations() = db.substationDao().clearSubstations()
    override suspend fun clearFeeders() = db.feederDao().clearFeeders()
    override suspend fun clearDevices() = db.deviceDao().clearDevices()
    override suspend fun clearLoops() = db.loopDao().clearLoops()

    override suspend fun initializeDatabaseIfEmpty() {
        // Let's check if the database is empty by querying a user
        val existingUsers = db.userDao().getUserById("admin")
        if (existingUsers == null) {
            // Database is empty! Prepopulate with robust grid management records
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val nowStr = sdf.format(Date())

            // 1. Core Default Users with distinct roles
            val defaultUsers = listOf(
                User("admin", "NV-001", "Nguyễn Văn Admin", "admin", "admin@grid.vn", "0905111222", "ĐL Hải Châu", "Phòng Kỹ Thuật", "Trưởng Phòng", "ACTIVE", nowStr, "ADMIN"),
                User("manager", "NV-002", "Trần Văn Quản Lý", "manager", "manager@grid.vn", "0905111333", "ĐL Hải Châu", "Ban Giám Đốc", "Phó Giám Đốc", "ACTIVE", nowStr, "MANAGER"),
                User("shift_leader", "NV-003", "Phạm Văn Trưởng Ca", "shift_leader", "shift@grid.vn", "0905111444", "ĐL Hải Châu", "Tổ Thao Tác Lưu Động", "Trưởng Ca", "ACTIVE", nowStr, "SHIFT_LEADER"),
                User("staff", "NV-004", "Lê Văn Nhân Viên", "staff", "staff@grid.vn", "0905111555", "ĐL Hải Châu", "Tổ Thao Tác Lưu Động", "Kỹ Thuật Viên", "ACTIVE", nowStr, "STAFF"),
                User("viewer", "NV-005", "Hoàng Văn Khách", "viewer", "viewer@grid.vn", "0905111666", "ĐL Hải Châu", "Phòng Giám Sát", "Chuyên Viên", "ACTIVE", nowStr, "VIEWER"),
                User("pending_user", "NV-006", "Vũ Thị Chờ Duyệt", "pending", "pending@grid.vn", "0905111777", "ĐL Liên Chiểu", "Phòng Kỹ Thuật", "Nhân Viên Mới", "PENDING", nowStr, "STAFF")
            )
            insertAllUsers(defaultUsers)

            // Audit Logs
            val defaultLogs = listOf(
                AuditLog(0, "admin", "Nguyễn Văn Admin", "LOGIN", "AUTH", "admin", "Đăng nhập hệ thống bằng tài khoản Quản trị viên", "SUCCESS", "192.168.1.100", nowStr),
                AuditLog(0, "admin", "Nguyễn Văn Admin", "INIT_DB", "SYSTEM", "system", "Tự động khởi tạo cấu trúc hệ thống", "SUCCESS", "127.0.0.1", nowStr)
            )
            for (log in defaultLogs) {
                insertAuditLog(log)
            }

            // 7. Prepopulate Document Categories
            val defaultCategories = listOf(
                DocCategory(0, "Quy trình / Quy định"),
                DocCategory(0, "Hướng dẫn vận hành"),
                DocCategory(0, "Tài liệu kỹ thuật"),
                DocCategory(0, "Biểu mẫu"),
                DocCategory(0, "Sơ đồ"),
                DocCategory(0, "Khác")
            )
            insertAllDocCategories(defaultCategories)
        }
    }
}
