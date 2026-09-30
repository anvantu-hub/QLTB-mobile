package com.example.library

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat

/**
 * Màn hình Trang chủ Thư viện Tài liệu 22kV (LibraryHomeScreen).
 * Cung cấp tìm kiếm nhanh, lọc theo danh mục dạng Chip, hiển thị trạng thái Offline/Tải về.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryHomeScreen(
    viewModel: LibraryViewModel,
    onOpenDocument: (DocumentEntity) -> Unit,
    onNavigateToOfflineManager: () -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val documents by viewModel.documents.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val storageStats by viewModel.storageStats.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Thư Viện Kỹ Thuật 22kV", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "Tài liệu, quy trình & sơ đồ lưới điện",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Nút chuyển sang màn hình quản lý Offline
                    IconButton(onClick = onNavigateToOfflineManager) {
                        BadgedBox(
                            badge = {
                                if (storageStats.downloadedCount > 0) {
                                    Badge { Text("${storageStats.downloadedCount}") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.CloudDone, contentDescription = "Quản lý Offline")
                        }
                    }
                    // Nút đồng bộ từ máy chủ
                    IconButton(onClick = { viewModel.refreshRemoteDocuments() }) {
                        Icon(Icons.Default.Sync, contentDescription = "Làm mới")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. THANH TÌM KIẾM
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text("Tìm theo tên tài liệu, quy trình, thiết bị...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Xóa tìm kiếm")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // 2. BỘ LỌC CHIP THEO LOẠI TÀI LIỆU
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(LibraryFilterType.values()) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { viewModel.onFilterSelect(filter) },
                        label = { Text(filter.label, fontSize = 12.sp) },
                        leadingIcon = when (filter) {
                            LibraryFilterType.DOWNLOADED -> {
                                { Icon(Icons.Default.DownloadDone, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            }
                            LibraryFilterType.FAVORITE -> {
                                { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            }
                            LibraryFilterType.PDF -> {
                                { Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            }
                            LibraryFilterType.IMAGE -> {
                                { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            }
                            else -> null
                        }
                    )
                }
            }

            // 3. BỘ LỌC DANH MỤC (CATEGORIES)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(categories) { cat ->
                    val isSelected = selectedCategory == cat
                    SuggestionChip(
                        onClick = { viewModel.onCategorySelect(cat) },
                        label = { Text(cat, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 4. DANH SÁCH TÀI LIỆU
            if (documents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            "Không tìm thấy tài liệu phù hợp",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(documents, key = { it.id }) { doc ->
                        DocumentItemCard(
                            document = doc,
                            progress = downloadProgress[doc.id],
                            onOpen = { onOpenDocument(doc) },
                            onToggleFavorite = { viewModel.toggleFavorite(doc) },
                            onDownload = { viewModel.downloadDocument(doc) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Thành phần Card hiển thị thông tin từng tài liệu
 */
@Composable
fun DocumentItemCard(
    document: DocumentEntity,
    progress: Int?,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Hàng tiêu đề & Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Badge loại tệp
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isPdf = document.fileType.equals("PDF", ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isPdf) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
                    ) {
                        Text(
                            text = if (isPdf) "PDF" else "SƠ ĐỒ 22kV",
                            color = if (isPdf) Color(0xFFC62828) else Color(0xFF2E7D32),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Biểu tượng trạng thái Offline
                    if (document.isDownloaded) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Offline",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    "Offline",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                // Nút Yêu thích (Star)
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (document.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Yêu thích",
                        tint = if (document.isFavorite) Color(0xFFFFA000) else MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Tên tài liệu
            Text(
                text = document.title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Thông tin danh mục & dung lượng
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${document.category} • ${formatFileSize(document.fileSize)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Nút tải về nếu chưa có Offline
                if (!document.isDownloaded) {
                    if (progress != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Text("$progress%", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onDownload,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tải về", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 KB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val df = DecimalFormat("#,##0.0")
    return if (mb >= 1.0) "${df.format(mb)} MB" else "${df.format(kb)} KB"
}
