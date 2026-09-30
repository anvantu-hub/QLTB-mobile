package com.example.library

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat

enum class LibraryFilterType(val label: String) {
    ALL("Tất cả"),
    DOWNLOADED("Đã tải (Offline)"),
    FAVORITE("Yêu thích"),
    PDF("Tài liệu PDF"),
    IMAGE("Sơ đồ 22kV / Bản vẽ")
}

data class StorageStats(
    val downloadedCount: Int = 0,
    val totalSizeBytes: Long = 0L
) {
    val formattedSize: String
        get() {
            if (totalSizeBytes <= 0) return "0 KB"
            val kb = totalSizeBytes / 1024.0
            val mb = kb / 1024.0
            val df = DecimalFormat("#,##0.0")
            return if (mb >= 1.0) "${df.format(mb)} MB" else "${df.format(kb)} KB"
        }
}

/**
 * ViewModel quản lý logic giao diện, luồng tải ngầm và trạng thái Offline cho Module Thư viện
 */
class LibraryViewModel(
    application: Application,
    private val repository: DocumentRepository
) : AndroidViewModel(application) {

    // UI Input States
    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow("Tất cả")
    val selectedFilter = MutableStateFlow(LibraryFilterType.ALL)

    // Download progress map: documentId -> percentage (0..100)
    private val _downloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Int>> = _downloadProgress.asStateFlow()

    // Loading & Refresh State
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Danh sách tài liệu phản ứng theo tìm kiếm, danh mục và bộ lọc
    val documents: StateFlow<List<DocumentEntity>> = combine(
        searchQuery,
        selectedCategory,
        selectedFilter
    ) { query, category, filter ->
        Triple(query, category, filter)
    }.flatMapLatest { (query, category, filter) ->
        val onlyDownloaded = filter == LibraryFilterType.DOWNLOADED
        val onlyFavorite = filter == LibraryFilterType.FAVORITE
        val fileType = when (filter) {
            LibraryFilterType.PDF -> "PDF"
            LibraryFilterType.IMAGE -> "IMAGE"
            else -> "ALL"
        }
        repository.getFilteredDocuments(
            query = query,
            category = category,
            fileType = fileType,
            onlyDownloaded = onlyDownloaded,
            onlyFavorite = onlyFavorite
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Danh mục tài liệu có sẵn trong hệ thống
    val categories: StateFlow<List<String>> = repository.getAllCategories()
        .map { listOf("Tất cả") + it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("Tất cả"))

    // Thống kê dung lượng Offline
    val storageStats: StateFlow<StorageStats> = repository.getDownloadedDocuments()
        .map { list ->
            StorageStats(
                downloadedCount = list.size,
                totalSizeBytes = list.sumOf { it.fileSize }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StorageStats())

    // Danh sách các tệp Offline cho OfflineManagerScreen
    val downloadedDocuments: StateFlow<List<DocumentEntity>> = repository.getDownloadedDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Tìm kiếm theo từ khóa
     */
    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    /**
     * Lọc theo danh mục
     */
    fun onCategorySelect(category: String) {
        selectedCategory.value = category
    }

    /**
     * Lọc theo loại (Tất cả, Đã tải, Yêu thích, PDF, Ảnh)
     */
    fun onFilterSelect(filter: LibraryFilterType) {
        selectedFilter.value = filter
    }

    /**
     * Bật/tắt trạng thái yêu thích
     */
    fun toggleFavorite(document: DocumentEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(document.id, document.isFavorite)
        }
    }

    /**
     * Kích hoạt tải tệp ngầm về máy để xem Offline
     */
    fun downloadDocument(document: DocumentEntity) {
        if (_downloadProgress.value.containsKey(document.id)) return // Đang tải

        viewModelScope.launch {
            _downloadProgress.update { it + (document.id to 0) }
            val result = repository.downloadDocument(document) { progress ->
                _downloadProgress.update { it + (document.id to progress) }
            }

            _downloadProgress.update { it - document.id }

            result.onSuccess {
                Toast.makeText(getApplication(), "Đã tải xong: ${document.title}", Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                Toast.makeText(getApplication(), "Tải lỗi: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Xóa tệp tải về khỏi máy
     */
    fun deleteLocalFile(document: DocumentEntity) {
        viewModelScope.launch {
            val success = repository.deleteLocalFile(document.id)
            if (success) {
                Toast.makeText(getApplication(), "Đã xóa tệp đệm: ${document.title}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Xóa toàn bộ bộ nhớ đệm
     */
    fun clearAllCache() {
        viewModelScope.launch {
            val count = repository.clearAllDownloadedCache()
            Toast.makeText(getApplication(), "Đã dọn dẹp $count tệp trong bộ nhớ tạm", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Làm mới danh sách từ máy chủ từ xa
     */
    fun refreshRemoteDocuments() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = repository.syncDocumentsFromRemote()
            _isRefreshing.value = false

            result.onSuccess { count ->
                Toast.makeText(getApplication(), "Đã đồng bộ $count tài liệu mới", Toast.LENGTH_SHORT).show()
            }.onFailure {
                // Tiếp tục dùng offline
                Toast.makeText(getApplication(), "Đang ở chế độ Ngoại tuyến (Offline)", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
