package com.example.camera

import android.app.Application
import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * ViewModel phụ trách luồng nghiệp vụ chụp ảnh, lưu trữ và duyệt thư viện ảnh thiết bị Trạm 110kV
 */
class EquipmentImageViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val dao = db.equipmentImageDao()

    val currentStationId = MutableStateFlow<Int?>(null)
    val selectedEquipmentFilter = MutableStateFlow("")

    // Danh sách hình ảnh phản ứng theo Trạm và Bộ lọc thiết bị
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val imagesList: StateFlow<List<EquipmentImageEntity>> = combine(
        currentStationId,
        selectedEquipmentFilter
    ) { stationId, filter ->
        Pair(stationId, filter)
    }.flatMapLatest { (stationId, filter) ->
        if (stationId != null) {
            dao.getImagesByStationAndEquipment(stationId, filter)
        } else {
            flowOf(emptyList<EquipmentImageEntity>())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setStation(stationId: Int) {
        currentStationId.value = stationId
    }

    fun setEquipmentFilter(code: String) {
        selectedEquipmentFilter.value = code
    }

    /**
     * Tạo file tạm thời chuẩn cho CameraX lưu ảnh:
     * Định dạng: [MãTrạm]_[MãThiếtBị]_[YYYYMMDD_HHMMSS].jpg
     */
    fun createPhotoFile(context: Context, stationCode: String, equipmentCode: String): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val cleanStation = stationCode.replace(" ", "_").replace("/", "-")
        val cleanEquip = equipmentCode.ifBlank { "TB" }.replace(" ", "_").replace("/", "-")
        val fileName = "${cleanStation}_${cleanEquip}_$timeStamp.jpg"

        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) 
            ?: File(context.filesDir, "equipment_pictures").apply { if (!exists()) mkdirs() }

        return File(storageDir, fileName)
    }

    /**
     * Lưu thông tin ảnh đã chụp vào Room Database
     */
    fun saveCapturedImage(
        stationId: Int,
        stationName: String,
        equipmentCode: String,
        equipmentName: String,
        imageFile: File,
        capturedBy: String = "Kỹ sư vận hành",
        note: String = "",
        onSaved: (Long) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val nowStr = sdf.format(Date())

            val entity = EquipmentImageEntity(
                stationId = stationId,
                stationName = stationName,
                equipmentCode = equipmentCode,
                equipmentName = equipmentName,
                imagePath = imageFile.absolutePath,
                capturedAt = nowStr,
                capturedBy = capturedBy,
                note = note,
                isSynced = false
            )
            val newId = dao.insert(entity)
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "Đã lưu ảnh thiết bị: $equipmentCode", Toast.LENGTH_SHORT).show()
                onSaved(newId)
            }
        }
    }

    /**
     * Xóa ảnh khỏi Database và xóa tệp vật lý khỏi bộ nhớ máy
     */
    fun deleteImage(image: EquipmentImageEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(image.imagePath)
            if (file.exists()) {
                file.delete()
            }
            dao.delete(image)
            withContext(Dispatchers.Main) {
                Toast.makeText(getApplication(), "Đã xóa ảnh", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
