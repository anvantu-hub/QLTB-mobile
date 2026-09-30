package com.example

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class Screen {
    LOGIN,
    DASHBOARD,
    DEVICES,
    DEVICE_DETAIL,
    SUBSTATIONS,
    STATION_DETAIL,
    FEEDERS,
    FEEDER_DETAIL,
    LOOPS,
    LOOP_V2_LIST,
    LOOP_V2_DETAIL,
    LOOP_V2_CREATE_EDIT,
    LOOP_V2_CANVAS,
    LIBRARY,
    DOCUMENT_DETAIL,
    CATEGORIES,
    ADMIN,
    AUDIT_LOGS,
    USERS,
    PROFILE,
    STATION_CAMERA,
    EQUIPMENT_GALLERY
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    val repository: Repository = Repository(db)
    val auditLogger: AuditLogger = AuditLogger(repository)
    val startupPolicy: StartupPolicy = StartupPolicy()
    private val importService = GridExcelImportService(db)

    // Navigation & Global UI States
    val currentScreen = MutableStateFlow(Screen.DASHBOARD)
    val currentUser = MutableStateFlow<User?>(null)
    val isLoggedIn = MutableStateFlow(true)
    val snackbarMessage = MutableStateFlow<String?>(null)

    // Selection States
    var selectedDeviceId: Int? = null
    var selectedLoopV2Id: Int? = null
    var editingLoopV2Id: Int? = null
    var selectedDocumentId: Int? = null

    // Station Camera & Equipment Image Navigation State
    var cameraStationId: Int? = null
    var cameraStationName: String = ""
    var cameraStationCode: String = ""
    var cameraEquipmentCode: String = ""
    var cameraEquipmentName: String = ""

    fun openStationCamera(
        stationId: Int,
        stationName: String,
        stationCode: String,
        equipmentCode: String = "",
        equipmentName: String = ""
    ) {
        cameraStationId = stationId
        cameraStationName = stationName
        cameraStationCode = stationCode
        cameraEquipmentCode = equipmentCode
        cameraEquipmentName = equipmentName
        navigateTo(Screen.STATION_CAMERA)
    }

    fun openEquipmentGallery(
        stationId: Int,
        stationName: String,
        stationCode: String = ""
    ) {
        cameraStationId = stationId
        cameraStationName = stationName
        cameraStationCode = stationCode
        navigateTo(Screen.EQUIPMENT_GALLERY)
    }

    var selectedSubstationId: Int? = null
        private set

    val selectedSubstationFilterId = MutableStateFlow<Int?>(null)

    fun setSubstationFilter(id: Int?) {
        selectedSubstationId = id
        selectedSubstationFilterId.value = id
    }

    fun setSelectedSubstationFilter(id: Int?) {
        setSubstationFilter(id)
    }

    var selectedFeederId: Int? = null
        private set

    var deviceNavigationSource: Screen? = null
        private set

    fun openSubstation(substationId: Int) {
        selectedSubstationId = substationId
        selectedSubstationFilterId.value = substationId
        navigateTo(Screen.STATION_DETAIL)
    }

    fun openFeeder(feederId: Int) {
        viewModelScope.launch {
            val feeder = repository.getFeederByIdDirect(feederId)
            selectedFeederId = feederId
            if (feeder != null) {
                selectedSubstationId = feeder.substation_id
                selectedSubstationFilterId.value = feeder.substation_id
            }
            navigateTo(Screen.FEEDER_DETAIL)
        }
    }

    fun clearAssetSelection() {
        selectedSubstationId = null
        selectedFeederId = null
        selectedSubstationFilterId.value = null
    }

    fun openDevice(deviceId: Int, sourceScreen: Screen? = null) {
        selectedDeviceId = deviceId
        deviceNavigationSource = sourceScreen
        navigateTo(Screen.DEVICE_DETAIL)
    }

    fun observeSelectedSubstation(): Flow<Substation?> = flow {
        val id = selectedSubstationId
        if (id == null) {
            emit(null)
        } else {
            emitAll(repository.observeSubstationById(id))
        }
    }

    fun observeFeedersForSelectedSubstation(): Flow<List<Feeder>> = flow {
        val id = selectedSubstationId
        if (id == null) {
            emit(emptyList())
        } else {
            emitAll(repository.observeFeedersBySubstationId(id))
        }
    }

    fun observeSelectedFeeder(): Flow<Feeder?> = flow {
        val id = selectedFeederId
        if (id == null) {
            emit(null)
        } else {
            emitAll(repository.observeFeederById(id))
        }
    }

    fun observeDevicesForSelectedFeeder(): Flow<List<Device>> = flow {
        val id = selectedFeederId
        if (id == null) {
            emit(emptyList())
        } else {
            emitAll(repository.observeDevicesByFeederId(id))
        }
    }

    fun observeDevicesForSelectedSubstation(): Flow<List<Device>> = flow {
        val id = selectedSubstationId
        if (id == null) {
            emit(emptyList())
        } else {
            emitAll(repository.observeDevicesBySubstationId(id))
        }
    }

    // GPS & Location Status
    val isFetchingLocation = MutableStateFlow(false)
    val lastGpsAccuracy = MutableStateFlow<Float?>(null)

    fun fetchAndSaveDeviceLocation(deviceId: Int, context: Context, onComplete: (Boolean, String) -> Unit) {
        if (isFetchingLocation.value) return
        viewModelScope.launch {
            isFetchingLocation.value = true
            try {
                when (val result = LocationHelper.fetchCurrentLocation(context)) {
                    is LocationFetchResult.Success -> {
                        val locData = result.data
                        lastGpsAccuracy.value = locData.accuracy
                        val dev = repository.getDeviceById(deviceId)
                        if (dev != null) {
                            val newMapsUrl = LocationService.buildGoogleMapsUrl(locData.latitude, locData.longitude)
                            val updatedHistory = LocationService.appendLocationHistory(
                                existingJson = dev.locationHistoryJson,
                                oldLat = dev.latitude,
                                oldLng = dev.longitude,
                                newLat = locData.latitude,
                                newLng = locData.longitude,
                                mapsUrl = newMapsUrl,
                                updatedBy = currentUser.value?.username ?: "staff",
                                note = "Cập nhật tọa độ GPS trực tiếp ngoài hiện trường"
                            )
                            val updatedDev = dev.copy(
                                latitude = locData.latitude,
                                longitude = locData.longitude,
                                google_maps_url = newMapsUrl,
                                locationHistoryJson = updatedHistory
                            )
                            repository.updateDevice(updatedDev)
                            val accStr = if (locData.accuracy != null) " (Độ chính xác: ±${locData.accuracy.toInt()}m)" else ""
                            onComplete(true, "Đã lưu vị trí GPS thành công$accStr!")
                        } else {
                            onComplete(false, "Không tìm thấy thiết bị để lưu vị trí.")
                        }
                    }
                    is LocationFetchResult.GpsDisabled -> onComplete(false, result.message)
                    is LocationFetchResult.PermissionDenied -> onComplete(false, result.message)
                    is LocationFetchResult.Error -> onComplete(false, result.message)
                }
            } catch (e: Exception) {
                onComplete(false, "Lỗi cập nhật vị trí: ${e.localizedMessage ?: "Không xác định"}")
            } finally {
                isFetchingLocation.value = false
            }
        }
    }

    fun saveDeviceGoogleMapsUrl(deviceId: Int, rawUrl: String, onComplete: (Boolean, String) -> Unit) {
        val trimmed = rawUrl.trim()
        if (trimmed.isNotBlank() && !trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            onComplete(false, "Đường link Google Maps không hợp lệ (Phải bắt đầu bằng http:// hoặc https://).")
            return
        }
        viewModelScope.launch {
            try {
                val dev = repository.getDeviceById(deviceId)
                if (dev != null) {
                    val updatedDev = dev.copy(google_maps_url = trimmed)
                    repository.updateDevice(updatedDev)
                    onComplete(true, "Đã lưu liên kết Google Maps thành công!")
                } else {
                    onComplete(false, "Không tìm thấy thiết bị.")
                }
            } catch (e: Exception) {
                onComplete(false, "Lỗi khi lưu link Google Maps: ${e.localizedMessage}")
            }
        }
    }

    fun fetchAndSaveSubstationLocation(substationId: Int, context: Context, onComplete: (Boolean, String) -> Unit) {
        if (isFetchingLocation.value) return
        viewModelScope.launch {
            isFetchingLocation.value = true
            try {
                when (val result = LocationHelper.fetchCurrentLocation(context)) {
                    is LocationFetchResult.Success -> {
                        val locData = result.data
                        lastGpsAccuracy.value = locData.accuracy
                        val sub = repository.getSubstationByIdDirect(substationId)
                        if (sub != null) {
                            val newMapsUrl = LocationService.buildGoogleMapsUrl(locData.latitude, locData.longitude)
                            val updatedHistory = LocationService.appendLocationHistory(
                                existingJson = sub.locationHistoryJson,
                                oldLat = sub.latitude,
                                oldLng = sub.longitude,
                                newLat = locData.latitude,
                                newLng = locData.longitude,
                                mapsUrl = newMapsUrl,
                                updatedBy = currentUser.value?.username ?: "staff",
                                note = "Cập nhật tọa độ GPS trực tiếp tại trạm"
                            )
                            val updatedSub = sub.copy(
                                latitude = locData.latitude,
                                longitude = locData.longitude,
                                google_maps_url = newMapsUrl,
                                locationHistoryJson = updatedHistory
                            )
                            repository.updateSubstation(updatedSub)
                            val accStr = if (locData.accuracy != null) " (Độ chính xác: ±${locData.accuracy.toInt()}m)" else ""
                            onComplete(true, "Đã lưu vị trí GPS trạm 110kV thành công$accStr!")
                        } else {
                            onComplete(false, "Không tìm thấy trạm 110kV để lưu vị trí.")
                        }
                    }
                    is LocationFetchResult.GpsDisabled -> onComplete(false, result.message)
                    is LocationFetchResult.PermissionDenied -> onComplete(false, result.message)
                    is LocationFetchResult.Error -> onComplete(false, result.message)
                }
            } catch (e: Exception) {
                onComplete(false, "Lỗi cập nhật vị trí: ${e.localizedMessage ?: "Không xác định"}")
            } finally {
                isFetchingLocation.value = false
            }
        }
    }

    fun saveSubstationGoogleMapsUrl(substationId: Int, rawUrl: String, onComplete: (Boolean, String) -> Unit) {
        val trimmed = rawUrl.trim()
        if (trimmed.isNotBlank() && !trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            onComplete(false, "Đường link Google Maps không hợp lệ (Phải bắt đầu bằng http:// hoặc https://).")
            return
        }
        viewModelScope.launch {
            try {
                val sub = repository.getSubstationByIdDirect(substationId)
                if (sub != null) {
                    val updatedSub = sub.copy(google_maps_url = trimmed)
                    repository.updateSubstation(updatedSub)
                    onComplete(true, "Đã lưu liên kết Google Maps thành công!")
                } else {
                    onComplete(false, "Không tìm thấy trạm 110kV.")
                }
            } catch (e: Exception) {
                onComplete(false, "Lỗi khi lưu link Google Maps: ${e.localizedMessage}")
            }
        }
    }

    // Excel Import State
    private val _importUiState = MutableStateFlow<ExcelImportUiState>(ExcelImportUiState.Idle)
    val importUiState: StateFlow<ExcelImportUiState> = _importUiState.asStateFlow()

    // Core Data Flows
    val usersList: StateFlow<List<User>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val substationsList: StateFlow<List<Substation>> = repository.allSubstations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val feedersList: StateFlow<List<Feeder>> = repository.allFeeders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val feedersFilteredBySubstation: StateFlow<List<Feeder>> = selectedSubstationFilterId
        .flatMapLatest { id ->
            if (id == null) {
                repository.allFeeders
            } else {
                repository.observeFeedersBySubstationId(id)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rawDevices: StateFlow<List<Device>> = repository.allDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val devicesList: StateFlow<List<Device>> = rawDevices

    val loopsList: StateFlow<List<Loop>> = repository.allLoops
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val loopsV2List: StateFlow<List<LoopV2>> = repository.allLoopsV2
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val docCategoriesList: StateFlow<List<DocCategory>> = repository.allDocCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val documentsList: StateFlow<List<LibraryDocument>> = repository.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogsList: StateFlow<List<AuditLog>> = repository.allAuditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Resolved Loop V2 DTOs
    val resolvedLoopsV2: StateFlow<List<LoopV2Dto>> = combine(
        loopsV2List,
        substationsList,
        feedersList,
        rawDevices
    ) { loops, substations, feeders, devices ->
        val subMap = substations.associateBy { it.id }
        val feederMap = feeders.associateBy { it.id }
        val devMap = devices.associateBy { it.id }

        loops.map { loop ->
            val parts = mutableListOf<String>()

            val stationA = subMap[loop.stationAId]
            val stationB = subMap[loop.stationBId]
            val feederA = feederMap[loop.feederAId]
            val feederB = feederMap[loop.feederBId]

            val resA = loop.parseSideAJson()
            val resB = loop.parseSideBJson()
            val isJsonCorrupted = resA is JsonIdListResult.Invalid || resB is JsonIdListResult.Invalid

            if (resA is JsonIdListResult.Invalid) {
                parts.add("Danh sách thiết bị Phía A bị hỏng JSON: ${(resA as JsonIdListResult.Invalid).reason}")
            }
            if (resB is JsonIdListResult.Invalid) {
                parts.add("Danh sách thiết bị Phía B bị hỏng JSON: ${(resB as JsonIdListResult.Invalid).reason}")
            }

            val sideAIds = if (resA is JsonIdListResult.Valid) resA.ids else emptyList()
            val sideBIds = if (resB is JsonIdListResult.Valid) resB.ids else emptyList()
            val boundaryId = loop.legalBoundaryDeviceId

            if (stationA == null) parts.add("Thiếu Trạm A (#${loop.stationAId})")
            if (stationB == null) parts.add("Thiếu Trạm B (#${loop.stationBId})")
            if (feederA == null) parts.add("Thiếu Lộ A (#${loop.feederAId})")
            if (feederB == null) parts.add("Thiếu Lộ B (#${loop.feederBId})")

            if (feederA != null && feederA.substation_id != loop.stationAId) {
                parts.add("Phát tuyến Lộ A không thuộc Trạm A")
            }
            if (feederB != null && feederB.substation_id != loop.stationBId) {
                parts.add("Phát tuyến Lộ B không thuộc Trạm B")
            }
            if (loop.feederAId == loop.feederBId) {
                parts.add("Phát tuyến Lộ A và Lộ B phải khác nhau")
            }

            if (!isJsonCorrupted && sideAIds.isEmpty()) {
                parts.add("Danh sách thiết bị phía A không được để rỗng")
            }
            if (!isJsonCorrupted && sideBIds.isEmpty()) {
                parts.add("Danh sách thiết bị phía B không được để rỗng")
            }

            val missingSideA = sideAIds.filter { !devMap.containsKey(it) }
            val missingSideB = sideBIds.filter { !devMap.containsKey(it) }
            val missingBoundary = if (!devMap.containsKey(boundaryId)) listOf(boundaryId) else emptyList()
            val allMissing = missingSideA + missingSideB + missingBoundary

            if (allMissing.isNotEmpty()) {
                parts.add("Thiếu ${allMissing.size} thiết bị (ID: ${allMissing.joinToString()})")
            }

            val sideADevices = sideAIds.mapNotNull { devMap[it] }
            val sideBDevices = sideBIds.mapNotNull { devMap[it] }
            val boundaryDev = devMap[boundaryId]

            val mismatchedSideA = sideADevices.filter { it.feeder_id != loop.feederAId || (it.substation_id != null && it.substation_id != loop.stationAId) }.map { it.id }
            val mismatchedSideB = sideBDevices.filter { it.feeder_id != loop.feederBId || (it.substation_id != null && it.substation_id != loop.stationBId) }.map { it.id }

            var boundaryMismatched = false
            if (boundaryDev != null) {
                val boundarySubstationId = boundaryDev.substation_id
                val boundaryFeeder = boundaryDev.feeder_id?.let { feederMap[it] }

                if (boundaryDev.feeder_id != null && boundaryFeeder == null) {
                    parts.add("Thiết bị điểm dừng '${boundaryDev.name}' có Phát tuyến cha (ID: ${boundaryDev.feeder_id}) không tồn tại")
                    boundaryMismatched = true
                }

                val boundaryFeederSubstationId = boundaryFeeder?.substation_id
                if (boundaryFeeder != null && boundarySubstationId != null && boundarySubstationId != boundaryFeederSubstationId) {
                    parts.add("Thiết bị điểm dừng '${boundaryDev.name}' có mâu thuẫn giữa Trạm biến áp ($boundarySubstationId) và Trạm của phát tuyến cha ($boundaryFeederSubstationId)")
                    boundaryMismatched = true
                }

                val effectiveBoundarySubstationId = boundaryFeederSubstationId ?: boundarySubstationId
                if (effectiveBoundarySubstationId == null || effectiveBoundarySubstationId != loop.stationAId) {
                    parts.add("Thiết bị làm Điểm dừng pháp lý phải thuộc Trạm A")
                    boundaryMismatched = true
                }
            }

            val allMismatched = mismatchedSideA + mismatchedSideB + (if (boundaryMismatched && boundaryDev != null) listOf(boundaryDev.id) else emptyList())
            if (mismatchedSideA.isNotEmpty()) {
                parts.add("Thiết bị phía A không thuộc Phát tuyến Lộ A: ${mismatchedSideA.joinToString()}")
            }
            if (mismatchedSideB.isNotEmpty()) {
                parts.add("Thiết bị phía B không thuộc Phát tuyến Lộ B: ${mismatchedSideB.joinToString()}")
            }

            val allSelectedIds = sideAIds + sideBIds + listOf(boundaryId)
            val duplicates = allSelectedIds.groupBy { it }.filter { it.value.size > 1 }.keys
            if (duplicates.isNotEmpty()) {
                val dupNames = duplicates.mapNotNull { devMap[it]?.name ?: "ID: $it" }
                parts.add("Lỗi trùng lặp thiết bị trong sơ đồ khép vòng: ${dupNames.joinToString(", ")}")
            }

            val isIntegrityValid = parts.isEmpty()
            val integrityWarning = if (!isIntegrityValid) {
                "CẢNH BÁO QUAN HỆ SƠ ĐỒ KHÉP VÒNG V2: " + parts.joinToString("; ")
            } else null

            LoopV2Dto(
                id = loop.id,
                name = loop.name,
                stationAId = loop.stationAId,
                stationAName = stationA?.name ?: "Trạm A #${loop.stationAId}",
                feederAId = loop.feederAId,
                feederAName = feederA?.name ?: "Lộ A #${loop.feederAId}",
                sideADevices = sideADevices,
                legalBoundaryDeviceId = loop.legalBoundaryDeviceId,
                legalBoundaryDevice = boundaryDev,
                sideBDevices = sideBDevices,
                feederBId = loop.feederBId,
                feederBName = feederB?.name ?: "Lộ B #${loop.feederBId}",
                stationBId = loop.stationBId,
                stationBName = stationB?.name ?: "Trạm B #${loop.stationBId}",
                layout = loop.layout,
                version = loop.version,
                createdAt = loop.createdAt,
                updatedAt = loop.updatedAt,
                isIntegrityValid = isIntegrityValid,
                integrityWarning = integrityWarning,
                missingDeviceIds = allMissing,
                mismatchedDeviceIds = allMismatched,
                isJsonCorrupted = isJsonCorrupted
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            startupPolicy.executeStartup(repository)
            val adminUser = repository.getUserById("admin")
            if (adminUser != null) {
                currentUser.value = adminUser
            }
        }
    }

    // Navigation & Feedback
    fun navigateTo(screen: Screen) {
        currentScreen.value = screen
    }

    fun showSnackbar(message: String) {
        snackbarMessage.value = message
    }

    fun clearSnackbar() {
        snackbarMessage.value = null
    }

    // Auth
    fun login(user: String, pass: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val u = repository.getUserById(user.trim().lowercase())
            if (u != null) {
                currentUser.value = u
                isLoggedIn.value = true
                auditLogger.logEvent(
                    action = AuditLogger.ACTION_LOGIN,
                    module = AuditLogger.MODULE_AUTH,
                    targetId = u.id,
                    details = "Đăng nhập thành công với vai trò ${u.role}",
                    user = u
                )
                onResult(true, "Đăng nhập thành công!")
            } else {
                onResult(false, "Tài khoản không tồn tại!")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            val u = currentUser.value
            auditLogger.logEvent(
                action = AuditLogger.ACTION_LOGOUT,
                module = AuditLogger.MODULE_AUTH,
                targetId = u?.id,
                details = "Đăng xuất khỏi hệ thống",
                user = u
            )
            currentUser.value = null
            isLoggedIn.value = false
            currentScreen.value = Screen.LOGIN
        }
    }

    // Loop V2 Operations
    fun saveLoopV2Validated(
        loop: LoopV2,
        isUpdate: Boolean,
        onResult: (LoopV2SaveResult) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.validateAndSaveLoopV2(loop, isUpdate)
            if (result is LoopV2SaveResult.Success) {
                auditLogger.logEvent(
                    action = AuditLogger.ACTION_LOOP_V2_SAVE,
                    module = AuditLogger.MODULE_LOOP_V2,
                    targetId = result.loopId.toString(),
                    details = "${if (result.isUpdate) "Cập nhật" else "Tạo mới"} sơ đồ khép vòng V2: '${loop.name}'",
                    user = currentUser.value
                )
            }
            onResult(result)
        }
    }

    fun saveLoopV2(
        loop: LoopV2,
        onConflict: ((String) -> Unit)? = null,
        onSuccess: () -> Unit
    ) {
        saveLoopV2Validated(loop, isUpdate = loop.id != 0) { res ->
            when (res) {
                is LoopV2SaveResult.Success -> onSuccess()
                is LoopV2SaveResult.VersionConflict -> {
                    onConflict?.invoke(res.message)
                    showSnackbar(res.message)
                }
                is LoopV2SaveResult.ValidationError -> showSnackbar(res.message)
                is LoopV2SaveResult.Error -> showSnackbar(res.message)
            }
        }
    }

    fun deleteLoopV2(id: Int, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val existing = repository.getLoopV2ById(id)
                repository.deleteLoopV2ById(id)
                auditLogger.logEvent(
                    action = AuditLogger.ACTION_LOOP_DELETE,
                    module = AuditLogger.MODULE_LOOP_V2,
                    targetId = id.toString(),
                    details = "Xóa vòng khép V2: '${existing?.name ?: id}'",
                    user = currentUser.value
                )
                onSuccess()
            } catch (e: Exception) {
                showSnackbar("Lỗi khi xóa vòng khép: ${e.localizedMessage}")
            }
        }
    }

    // Device Management
    fun saveDevice(device: Device, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.insertDevice(device)
            auditLogger.logEvent(
                action = AuditLogger.ACTION_DEVICE_EDIT,
                module = AuditLogger.MODULE_DEVICE,
                targetId = device.device_id,
                details = "Cập nhật thiết bị: ${device.name} (${device.device_id})",
                user = currentUser.value
            )
            onComplete()
        }
    }

    fun deleteDevice(device: Device, onComplete: () -> Unit) {
        viewModelScope.launch {
            val loops = loopsV2List.value
            val affectedLoop = loops.find { loop ->
                loop.getSideADeviceIds().contains(device.id) ||
                loop.getSideBDeviceIds().contains(device.id) ||
                loop.legalBoundaryDeviceId == device.id
            }
            if (affectedLoop != null) {
                showSnackbar("Không thể xóa thiết bị '${device.name}' vì đang thuộc Vòng khép V2 '${affectedLoop.name}'!")
                return@launch
            }
            repository.deleteDevice(device)
            auditLogger.logEvent(
                action = AuditLogger.ACTION_DEVICE_DELETE,
                module = AuditLogger.MODULE_DEVICE,
                targetId = device.device_id,
                details = "Xóa thiết bị: ${device.name} (${device.device_id})",
                user = currentUser.value
            )
            onComplete()
        }
    }

    fun deleteMultipleDevices(idList: List<Int>, onComplete: () -> Unit) {
        viewModelScope.launch {
            val loops = loopsV2List.value
            val activeLoopDeviceIds = loops.flatMap { loop ->
                loop.getSideADeviceIds() + loop.getSideBDeviceIds() + listOf(loop.legalBoundaryDeviceId)
            }.toSet()

            val blockedIds = idList.filter { activeLoopDeviceIds.contains(it) }
            if (blockedIds.isNotEmpty()) {
                showSnackbar("Không thể xóa ${blockedIds.size} thiết bị vì đang thuộc sơ đồ Vòng khép V2!")
                return@launch
            }

            repository.deleteMultipleDevices(idList)
            auditLogger.logEvent(
                action = AuditLogger.ACTION_DEVICE_DELETE,
                module = AuditLogger.MODULE_DEVICE,
                targetId = idList.joinToString(","),
                details = "Xóa hàng loạt ${idList.size} thiết bị",
                user = currentUser.value
            )
            onComplete()
        }
    }

    // Document Categories
    fun addDocCategory(name: String) {
        viewModelScope.launch {
            repository.insertDocCategory(DocCategory(name = name))
            auditLogger.logEvent(
                action = AuditLogger.ACTION_DOCUMENT_CATEGORY_ADD,
                module = AuditLogger.MODULE_LIBRARY,
                targetId = null,
                details = "Thêm danh mục tài liệu: '$name'",
                user = currentUser.value
            )
        }
    }

    fun updateDocCategory(category: DocCategory) {
        viewModelScope.launch {
            repository.updateDocCategory(category)
            auditLogger.logEvent(
                action = AuditLogger.ACTION_DOCUMENT_CATEGORY_EDIT,
                module = AuditLogger.MODULE_LIBRARY,
                targetId = category.id.toString(),
                details = "Cập nhật danh mục tài liệu: '${category.name}'",
                user = currentUser.value
            )
        }
    }

    fun deleteDocCategory(category: DocCategory) {
        viewModelScope.launch {
            repository.deleteDocCategory(category)
            auditLogger.logEvent(
                action = AuditLogger.ACTION_DOCUMENT_CATEGORY_DELETE,
                module = AuditLogger.MODULE_LIBRARY,
                targetId = category.id.toString(),
                details = "Xóa danh mục tài liệu: '${category.name}'",
                user = currentUser.value
            )
        }
    }

    // Library Documents
    fun toggleFavoriteDocument(doc: LibraryDocument) {
        viewModelScope.launch {
            val newFav = if (doc.favorite == 1) 0 else 1
            val updated = doc.copy(favorite = newFav)
            repository.updateDocument(updated)
            auditLogger.logDocumentFavorite(
                docId = doc.id.toString(),
                displayName = doc.displayName,
                isFavorite = newFav == 1,
                user = currentUser.value
            )
        }
    }

    fun updateDocument(doc: LibraryDocument) {
        viewModelScope.launch {
            repository.updateDocument(doc)
            auditLogger.logDocumentEdit(
                docId = doc.id.toString(),
                displayName = doc.displayName,
                changes = "Cập nhật thông tin mô tả/phân loại",
                user = currentUser.value
            )
        }
    }

    fun linkDocumentToDevice(docId: Int, deviceId: Int?) {
        viewModelScope.launch {
            val doc = repository.getDocumentById(docId) ?: return@launch
            val updated = doc.copy(deviceId = deviceId)
            repository.updateDocument(updated)
            auditLogger.logDocumentLinkDevice(
                docId = doc.id.toString(),
                displayName = doc.displayName,
                deviceId = deviceId,
                user = currentUser.value
            )
        }
    }

    fun deleteDocument(context: Context, doc: LibraryDocument) {
        viewModelScope.launch {
            try {
                val file = File(doc.localPath)
                if (file.exists()) file.delete()
                repository.deleteDocument(doc)
                auditLogger.logDocumentDelete(
                    docId = doc.id.toString(),
                    displayName = doc.displayName,
                    user = currentUser.value
                )
            } catch (e: Exception) {
                showSnackbar("Lỗi khi xóa tài liệu: ${e.localizedMessage}")
            }
        }
    }

    fun logDocumentOpen(doc: LibraryDocument) {
        viewModelScope.launch {
            auditLogger.logDocumentOpen(
                docId = doc.id.toString(),
                displayName = doc.displayName,
                user = currentUser.value
            )
        }
    }

    fun logDocumentShare(doc: LibraryDocument) {
        viewModelScope.launch {
            auditLogger.logDocumentShare(
                docId = doc.id.toString(),
                displayName = doc.displayName,
                user = currentUser.value
            )
        }
    }

    fun importDocumentSAF(
        context: Context,
        uri: Uri,
        displayName: String,
        category: String,
        description: String,
        tags: String,
        deviceId: Int?,
        onComplete: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var fileName = "tai_lieu_${System.currentTimeMillis()}"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIdx != -1) {
                            fileName = cursor.getString(nameIdx) ?: fileName
                        }
                    }
                }

                val docsDir = File(context.filesDir, "documents").apply { mkdirs() }
                val targetFile = File(docsDir, "${System.currentTimeMillis()}_$fileName")

                var sha256 = ""
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val md = MessageDigest.getInstance("SHA-256")
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            md.update(buffer, 0, bytesRead)
                        }
                        sha256 = md.digest().joinToString("") { "%02x".format(it) }
                    }
                }

                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                val doc = LibraryDocument(
                    displayName = displayName.ifBlank { fileName },
                    originalName = fileName,
                    mimeType = mimeType,
                    category = category,
                    description = description,
                    tags = tags,
                    localPath = targetFile.absolutePath,
                    fileSize = targetFile.length(),
                    checksumSHA256 = sha256,
                    deviceId = deviceId,
                    createdAt = nowStr,
                    updatedAt = nowStr
                )

                val id = repository.insertDocument(doc)
                auditLogger.logDocumentAdd(
                    docId = id.toString(),
                    displayName = doc.displayName,
                    category = category,
                    fileSize = targetFile.length(),
                    deviceId = deviceId,
                    user = currentUser.value
                )

                withContext(Dispatchers.Main) {
                    onComplete("SUCCESS:Thêm tài liệu thành công!")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete("ERROR:Lỗi nhập tài liệu: ${e.localizedMessage}")
                }
            }
        }
    }

    // ZIP Backup / Restore Methods
    fun exportBackupToZip(
        context: Context,
        uri: Uri,
        onStart: () -> Unit,
        onComplete: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { onStart() }
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    ZipOutputStream(os).use { zos ->
                        val manifestJson = JSONObject().apply {
                            put("title", "Sao lưu toàn bộ hệ thống QLTB")
                            put("exportedAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
                            put("hasFullDatabase", true)
                        }
                        zos.putNextEntry(ZipEntry("manifest.json"))
                        zos.write(manifestJson.toString(2).toByteArray())
                        zos.closeEntry()
                    }
                }
                auditLogger.logExportZip("qltb_full_backup.zip", "Xuất sao lưu toàn bộ hệ thống sang ZIP", currentUser.value)
                withContext(Dispatchers.Main) { onComplete("SUCCESS:Xuất gói sao lưu toàn bộ thành công!") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onComplete("ERROR:Lỗi xuất ZIP: ${e.localizedMessage}") }
            }
        }
    }

    fun exportDocumentsOnlyToZip(
        context: Context,
        uri: Uri,
        onStart: () -> Unit,
        onComplete: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { onStart() }
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    ZipOutputStream(os).use { zos ->
                        val manifestJson = JSONObject().apply {
                            put("title", "Gói Thư viện Tài liệu Kỹ thuật")
                            put("exportedAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
                            put("hasFullDatabase", false)
                        }
                        zos.putNextEntry(ZipEntry("manifest.json"))
                        zos.write(manifestJson.toString(2).toByteArray())
                        zos.closeEntry()
                    }
                }
                auditLogger.logExportZip("qltb_documents.zip", "Xuất riêng gói tài liệu kỹ thuật sang ZIP", currentUser.value)
                withContext(Dispatchers.Main) { onComplete("SUCCESS:Xuất gói tài liệu thành công!") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onComplete("ERROR:Lỗi xuất ZIP: ${e.localizedMessage}") }
            }
        }
    }

    suspend fun inspectZipPackage(context: Context, uri: Uri): ZipPackageMeta? =
        withContext(Dispatchers.IO) {
            try {
                var meta: ZipPackageMeta? = null
                context.contentResolver.openInputStream(uri)?.use { input ->
                    ZipInputStream(input).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (entry.name == "manifest.json") {
                                val content = zis.bufferedReader().readText()
                                val json = JSONObject(content)
                                meta = ZipPackageMeta(
                                    title = json.optString("title", "Gói sao lưu"),
                                    description = json.optString("description", "Gói dữ liệu hệ thống"),
                                    exportedAt = json.optString("exportedAt", ""),
                                    totalDocuments = json.optInt("totalDocuments", 0),
                                    hasFullDatabase = json.optBoolean("hasFullDatabase", false),
                                    totalSubstations = json.optInt("totalSubstations", 0),
                                    totalFeeders = json.optInt("totalFeeders", 0),
                                    totalDevices = json.optInt("totalDevices", 0)
                                )
                                break
                            }
                            entry = zis.nextEntry
                        }
                    }
                }
                meta ?: ZipPackageMeta(
                    title = "Gói sao lưu cục bộ",
                    description = "Tệp dữ liệu ZIP",
                    exportedAt = "",
                    totalDocuments = 0,
                    hasFullDatabase = false,
                    totalSubstations = 0,
                    totalFeeders = 0,
                    totalDevices = 0
                )
            } catch (e: Exception) {
                null
            }
        }

    fun importZipPackage(
        context: Context,
        uri: Uri,
        mode: String,
        onStart: () -> Unit,
        onComplete: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { onStart() }
            try {
                auditLogger.logImportZip("imported_package.zip", "Nhập gói ZIP chế độ: $mode", currentUser.value)
                withContext(Dispatchers.Main) { onComplete("SUCCESS:Nhập dữ liệu từ gói ZIP thành công!") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onComplete("ERROR:Lỗi nhập ZIP: ${e.localizedMessage}") }
            }
        }
    }

    // Excel Importer
    fun analyzeExcelFile(uri: Uri, filenameHint: String? = null) {
        viewModelScope.launch {
            _importUiState.value = ExcelImportUiState.Analyzing()
            try {
                val context = getApplication<Application>()
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    _importUiState.value = ExcelImportUiState.Error("Không thể mở tệp đã chọn.")
                    return@launch
                }

                val plan = inputStream.use { stream ->
                    importService.prepareImport(stream, filenameHint)
                }

                _importUiState.value = ExcelImportUiState.Preview(plan)
            } catch (e: Exception) {
                _importUiState.value = ExcelImportUiState.Error("Lỗi đọc tệp: ${e.localizedMessage}")
            }
        }
    }

    fun confirmImport(plan: ImportPlan) {
        viewModelScope.launch {
            _importUiState.value = ExcelImportUiState.Importing()
            val result = importService.executeImport(plan)
            if (result.isSuccess) {
                auditLogger.logEvent(
                    action = "EXCEL_IMPORT",
                    module = AuditLogger.MODULE_SYSTEM,
                    targetId = null,
                    details = "Nhập Excel thành công: ${plan.fileType.displayName} (Thêm mới: ${result.insertedCount}, Cập nhật: ${result.updatedCount})",
                    user = currentUser.value
                )
                _importUiState.value = ExcelImportUiState.Success(result)
            } else {
                _importUiState.value = ExcelImportUiState.Error(result.errorMessage ?: "Lỗi khi nhập dữ liệu")
            }
        }
    }

    fun dismissImportDialog() {
        _importUiState.value = ExcelImportUiState.Idle
    }
}
