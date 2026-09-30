package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val categories: List<DocCategory> by viewModel.docCategoriesList.collectAsState()
    val documents: List<LibraryDocument> by viewModel.documentsList.collectAsState()
    val rawDevices: List<Device> by viewModel.rawDevices.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Tất cả") }
    var selectedFileType by remember { mutableStateOf("Tất cả") }
    var sortBy by remember { mutableStateOf("date") } // "name", "date", "size"
    var showOnlyFavorite by remember { mutableStateOf(false) }

    // Dialog state holders
    var showAddDocDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showEditDocDialog by remember { mutableStateOf<LibraryDocument?>(null) }
    var showDetailDocDialog by remember { mutableStateOf<LibraryDocument?>(null) }
    var showLinkDeviceDialog by remember { mutableStateOf<LibraryDocument?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<LibraryDocument?>(null) }

    // ZIP Package State Holders
    var showZipOptionsDialog by remember { mutableStateOf(false) }
    var showZipImportConfirmDialog by remember { mutableStateOf(false) }
    var pendingZipUri by remember { mutableStateOf<Uri?>(null) }
    var pendingZipMeta by remember { mutableStateOf<ZipPackageMeta?>(null) }
    var selectedImportMode by remember { mutableStateOf("FULL_RESTORE") }
    val coroutineScope = rememberCoroutineScope()

    // Temp state for file import
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingOriginalName by remember { mutableStateOf("") }

    // Backup states
    var isBackupInProgress by remember { mutableStateOf(false) }

    // Storage Access Framework Picker
    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            var fileName = "tai_lieu_${System.currentTimeMillis()}"
            val cursor = context.contentResolver.query(it, null, null, null, null)
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) {
                        fileName = c.getString(nameIdx) ?: fileName
                    }
                }
            }
            pendingImportUri = it
            pendingOriginalName = fileName
            showAddDocDialog = true
        }
    }

    // 1. Xuất Toàn Bộ Hệ Thống sang ZIP (CSDL + Tệp đính kèm)
    val zipBackupPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let {
            viewModel.exportBackupToZip(
                context = context,
                uri = it,
                onStart = { isBackupInProgress = true },
                onComplete = { result ->
                    isBackupInProgress = false
                    Toast.makeText(context, result.substringAfter(":"), Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    // 2. Xuất Riêng Gói Thư Viện Tài Liệu sang ZIP
    val zipDocsOnlyExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let {
            viewModel.exportDocumentsOnlyToZip(
                context = context,
                uri = it,
                onStart = { isBackupInProgress = true },
                onComplete = { result ->
                    isBackupInProgress = false
                    Toast.makeText(context, result.substringAfter(":"), Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    // 3. Nhập Gói ZIP (Kiểm tra manifest, hiển thị bảng xác nhận & lựa chọn chế độ nhập)
    val zipRestorePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                isBackupInProgress = true
                val meta = viewModel.inspectZipPackage(context, it)
                isBackupInProgress = false
                if (meta != null) {
                    pendingZipUri = it
                    pendingZipMeta = meta
                    selectedImportMode = if (meta.hasFullDatabase) "FULL_RESTORE" else "MERGE_DOCUMENTS"
                    showZipImportConfirmDialog = true
                } else {
                    Toast.makeText(context, "Không thể đọc thông tin gói ZIP hoặc tệp không hợp lệ", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Filter documents
    val filteredDocuments = documents.filter { doc ->
        val ext = doc.fileExtension
        val matchesSearch = doc.displayName.contains(searchQuery, ignoreCase = true) ||
                doc.originalName.contains(searchQuery, ignoreCase = true) ||
                doc.tags.contains(searchQuery, ignoreCase = true) ||
                doc.description.contains(searchQuery, ignoreCase = true)

        val matchesCategory = selectedCategory == "Tất cả" || doc.category == selectedCategory
        val matchesType = selectedFileType == "Tất cả" || when (selectedFileType) {
            "PDF" -> ext == "pdf"
            "Word" -> ext in listOf("doc", "docx")
            "Excel" -> ext in listOf("xls", "xlsx")
            "Ảnh" -> ext in listOf("jpg", "jpeg", "png", "webp", "gif")
            "TXT" -> ext in listOf("txt", "csv", "log")
            else -> ext !in listOf("pdf", "doc", "docx", "xls", "xlsx", "jpg", "jpeg", "png", "webp", "gif", "txt", "csv", "log")
        }
        val matchesFavorite = !showOnlyFavorite || doc.favorite == 1

        matchesSearch && matchesCategory && matchesType && matchesFavorite
    }.sortedWith { a, b ->
        when (sortBy) {
            "name" -> a.displayName.compareTo(b.displayName, ignoreCase = true)
            "size" -> a.fileSize.compareTo(b.fileSize)
            else -> a.updatedAt.compareTo(b.updatedAt)
        }
    }.let { if (sortBy == "name") it else it.reversed() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Thư viện", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                actions = {
                    FilledTonalButton(
                        onClick = { showZipOptionsDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Gói ZIP", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { docPickerLauncher.launch("*/*") },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Thêm tài liệu", tint = Color.White)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isBackupInProgress) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Đang xử lý tệp sao lưu/khôi phục, xin vui lòng chờ...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }

            // 1. Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Tìm kiếm tài liệu, số hiệu, tags...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 2. Categories Horizontal Bar with Manage button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Danh mục:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Quản lý danh mục",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { showAddCategoryDialog = true }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            ScrollableTabRow(
                selectedTabIndex = (listOf("Tất cả") + categories.map { it.name }).indexOf(selectedCategory).let { if (it == -1) 0 else it },
                edgePadding = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedCategory == "Tất cả",
                    onClick = { selectedCategory = "Tất cả" },
                    text = { Text("Tất cả", fontSize = 12.sp) }
                )
                categories.forEach { cat ->
                    Tab(
                        selected = selectedCategory == cat.name,
                        onClick = { selectedCategory = cat.name },
                        text = { Text(cat.name, fontSize = 12.sp) }
                    )
                }
            }

            // 3. File Type Quick Filter Row
            val fileTypes = listOf("Tất cả", "PDF", "Word", "Excel", "Ảnh", "TXT", "Khác")
            ScrollableTabRow(
                selectedTabIndex = fileTypes.indexOf(selectedFileType).let { if (it == -1) 0 else it },
                edgePadding = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                fileTypes.forEach { type ->
                    Tab(
                        selected = selectedFileType == type,
                        onClick = { selectedFileType = type },
                        text = { Text(type, fontSize = 11.sp) }
                    )
                }
            }

            // 4. Favorite & Sort Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showOnlyFavorite = !showOnlyFavorite }) {
                        Icon(
                            imageVector = if (showOnlyFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = if (showOnlyFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (showOnlyFavorite) "Chỉ tài liệu yêu thích" else "Tất cả tài liệu", fontSize = 12.sp)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sắp xếp: ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    var expandedSortMenu by remember { mutableStateOf(false) }
                    Box {
                        Text(
                            text = when (sortBy) {
                                "name" -> "Tên A-Z"
                                "size" -> "Dung lượng"
                                else -> "Mới nhất"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { expandedSortMenu = true }
                                .padding(4.dp)
                        )
                        DropdownMenu(
                            expanded = expandedSortMenu,
                            onDismissRequest = { expandedSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Mới cập nhật") },
                                onClick = { sortBy = "date"; expandedSortMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Tên A-Z") },
                                onClick = { sortBy = "name"; expandedSortMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Dung lượng tệp") },
                                onClick = { sortBy = "size"; expandedSortMenu = false }
                            )
                        }
                    }
                }
            }

            // 5. Document List
            if (filteredDocuments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Chưa có tài liệu nào phù hợp.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredDocuments, key = { it.id }) { doc ->
                        val linkedDevice = remember(doc.deviceId, rawDevices) {
                            rawDevices.find { it.id == doc.deviceId }
                        }

                        DocumentItemCard(
                            doc = doc,
                            linkedDeviceName = linkedDevice?.let { "${it.device_id} (${it.name})" },
                            onOpen = { openDocumentFile(context, doc, viewModel) },
                            onShowDetail = { showDetailDocDialog = doc },
                            onFavoriteToggle = { viewModel.toggleFavoriteDocument(doc) },
                            onEdit = { showEditDocDialog = doc },
                            onLinkDevice = { showLinkDeviceDialog = doc },
                            onDelete = { showDeleteConfirmDialog = doc },
                            onShare = { shareDocumentFile(context, doc, viewModel) }
                        )
                    }
                }
            }
        }
    }

    // --- DIALOGS ---

    // 1. Add Document Dialog (SAF Import)
    if (showAddDocDialog && pendingImportUri != null) {
        val originalName = pendingOriginalName
        val baseName = getBaseName(originalName)
        var displayNameInput by remember { mutableStateOf(baseName) }
        var categoryInput by remember {
            mutableStateOf(if (categories.isNotEmpty()) categories.first().name else "Tài liệu kỹ thuật")
        }
        var descriptionInput by remember { mutableStateOf("") }
        var tagsInput by remember { mutableStateOf("") }
        var selectedDeviceId by remember { mutableStateOf<Int?>(null) }

        var categoryMenuExpanded by remember { mutableStateOf(false) }
        var deviceMenuExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                showAddDocDialog = false
                pendingImportUri = null
            },
            title = { Text("Thêm tài liệu vào Thư viện", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Tên tệp gốc: $originalName", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    OutlinedTextField(
                        value = displayNameInput,
                        onValueChange = { displayNameInput = it },
                        label = { Text("Tên hiển thị") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = categoryInput,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Danh mục") },
                            trailingIcon = {
                                IconButton(onClick = { categoryMenuExpanded = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = categoryMenuExpanded,
                            onDismissRequest = { categoryMenuExpanded = false }
                        ) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = {
                                        categoryInput = cat.name
                                        categoryMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Link device option
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val selectedDev = rawDevices.find { it.id == selectedDeviceId }
                        val deviceLabel: String = if (selectedDev != null) "${selectedDev.device_id} - ${selectedDev.name}" else "Không liên kết thiết bị"
                        OutlinedTextField(
                            value = deviceLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Liên kết thiết bị (tùy chọn)") },
                            trailingIcon = {
                                IconButton(onClick = { deviceMenuExpanded = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = deviceMenuExpanded,
                            onDismissRequest = { deviceMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Không liên kết") },
                                onClick = {
                                    selectedDeviceId = null
                                    deviceMenuExpanded = false
                                }
                            )
                            rawDevices.forEach { dev ->
                                DropdownMenuItem(
                                    text = { Text("${dev.device_id} - ${dev.name}") },
                                    onClick = {
                                        selectedDeviceId = dev.id
                                        deviceMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = descriptionInput,
                        onValueChange = { descriptionInput = it },
                        label = { Text("Mô tả / Ghi chú") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = tagsInput,
                        onValueChange = { tagsInput = it },
                        label = { Text("Tags (phân cách bằng dấu phẩy)") },
                        singleLine = true,
                        placeholder = { Text("quy trình, datasheet, rcl") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = pendingImportUri
                        if (uri != null) {
                            viewModel.importDocumentSAF(
                                context = context,
                                uri = uri,
                                displayName = displayNameInput.trim().ifBlank { baseName },
                                category = categoryInput,
                                description = descriptionInput.trim(),
                                tags = tagsInput.trim(),
                                deviceId = selectedDeviceId,
                                onComplete = { result ->
                                    Toast.makeText(context, result.substringAfter(":"), Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                        showAddDocDialog = false
                        pendingImportUri = null
                    }
                ) {
                    Text("Lưu tài liệu")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDocDialog = false
                    pendingImportUri = null
                }) {
                    Text("Hủy")
                }
            }
        )
    }

    // 2. Manage Categories Dialog
    if (showAddCategoryDialog) {
        var newCatName by remember { mutableStateOf("") }
        var showCatEditDialog by remember { mutableStateOf<DocCategory?>(null) }

        Dialog(onDismissRequest = { showAddCategoryDialog = false }) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Quản lý danh mục tài liệu", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newCatName,
                            onValueChange = { newCatName = it },
                            placeholder = { Text("Tên danh mục mới...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (newCatName.isNotBlank()) {
                                    viewModel.addDocCategory(newCatName.trim())
                                    newCatName = ""
                                }
                            }
                        ) {
                            Text("Thêm")
                        }
                    }

                    HorizontalDivider()

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(categories) { cat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(cat.name, fontSize = 13.sp)
                                Row {
                                    IconButton(onClick = { showCatEditDialog = cat }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Sửa", modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = { viewModel.deleteDocCategory(cat) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Xóa", tint = Color.Red, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        TextButton(onClick = { showAddCategoryDialog = false }) {
                            Text("Đóng")
                        }
                    }
                }
            }
        }

        if (showCatEditDialog != null) {
            val cat = showCatEditDialog!!
            var editName by remember { mutableStateOf(cat.name) }
            AlertDialog(
                onDismissRequest = { showCatEditDialog = null },
                title = { Text("Sửa tên danh mục") },
                text = {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (editName.isNotBlank()) {
                                viewModel.updateDocCategory(cat.copy(name = editName.trim()))
                                showCatEditDialog = null
                            }
                        }
                    ) {
                        Text("Lưu")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCatEditDialog = null }) {
                        Text("Hủy")
                    }
                }
            )
        }
    }

    // 3. Document Details Dialog
    if (showDetailDocDialog != null) {
        val doc = showDetailDocDialog!!
        val linkedDevice = remember(doc.deviceId, rawDevices) {
            rawDevices.find { it.id == doc.deviceId }
        }

        AlertDialog(
            onDismissRequest = { showDetailDocDialog = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val iconInfo = getFileIconInfo(doc.fileExtension)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(iconInfo.backgroundColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(iconInfo.icon, contentDescription = null, tint = iconInfo.tintColor, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(doc.displayName, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DetailItemRow("Tên tệp gốc", doc.originalName)
                    DetailItemRow("Định dạng", doc.fileExtension.uppercase())
                    DetailItemRow("Danh mục", doc.category)
                    DetailItemRow("Dung lượng", formatFileSize(doc.fileSize))
                    DetailItemRow("Ngày nhập", doc.createdAt)
                    DetailItemRow("Cập nhật lần cuối", doc.updatedAt)
                    DetailItemRow(
                        "Thiết bị liên kết",
                        if (linkedDevice != null) "${linkedDevice.device_id} - ${linkedDevice.name}" else "Chưa liên kết"
                    )
                    if (doc.description.isNotBlank()) {
                        DetailItemRow("Mô tả", doc.description)
                    }
                    if (doc.tags.isNotBlank()) {
                        DetailItemRow("Tags", doc.tags)
                    }
                    if (doc.checksumSHA256.isNotBlank()) {
                        DetailItemRow(
                            "Mã băm SHA-256",
                            doc.checksumSHA256.take(16) + "..." + doc.checksumSHA256.takeLast(8)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    openDocumentFile(context, doc, viewModel)
                    showDetailDocDialog = null
                }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Mở tài liệu")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        shareDocumentFile(context, doc, viewModel)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Chia sẻ")
                    }
                    TextButton(onClick = { showDetailDocDialog = null }) {
                        Text("Đóng")
                    }
                }
            }
        )
    }

    // 4. Edit Document Dialog
    if (showEditDocDialog != null) {
        val doc = showEditDocDialog!!
        var editDisplayName by remember { mutableStateOf(doc.displayName) }
        var editCategory by remember { mutableStateOf(doc.category) }
        var editDescription by remember { mutableStateOf(doc.description) }
        var editTags by remember { mutableStateOf(doc.tags) }
        var editDeviceId by remember { mutableStateOf(doc.deviceId) }

        var categoryMenuExpanded by remember { mutableStateOf(false) }
        var deviceMenuExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showEditDocDialog = null },
            title = { Text("Chỉnh sửa tài liệu") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = editDisplayName,
                        onValueChange = { editDisplayName = it },
                        label = { Text("Tên hiển thị") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = editCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Danh mục") },
                            trailingIcon = {
                                IconButton(onClick = { categoryMenuExpanded = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = categoryMenuExpanded,
                            onDismissRequest = { categoryMenuExpanded = false }
                        ) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = {
                                        editCategory = cat.name
                                        categoryMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Device link selector
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val selectedDev = rawDevices.find { it.id == editDeviceId }
                        val editDeviceLabel: String = if (selectedDev != null) "${selectedDev.device_id} - ${selectedDev.name}" else "Không liên kết thiết bị"
                        OutlinedTextField(
                            value = editDeviceLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Thiết bị liên kết") },
                            trailingIcon = {
                                IconButton(onClick = { deviceMenuExpanded = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = deviceMenuExpanded,
                            onDismissRequest = { deviceMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Không liên kết (Bỏ chọn)") },
                                onClick = {
                                    editDeviceId = null
                                    deviceMenuExpanded = false
                                }
                            )
                            rawDevices.forEach { dev ->
                                DropdownMenuItem(
                                    text = { Text("${dev.device_id} - ${dev.name}") },
                                    onClick = {
                                        editDeviceId = dev.id
                                        deviceMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editDescription,
                        onValueChange = { editDescription = it },
                        label = { Text("Mô tả / Ghi chú") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editTags,
                        onValueChange = { editTags = it },
                        label = { Text("Tags") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = doc.copy(
                            displayName = editDisplayName.trim(),
                            category = editCategory,
                            description = editDescription.trim(),
                            tags = editTags.trim(),
                            deviceId = editDeviceId
                        )
                        viewModel.updateDocument(updated)
                        showEditDocDialog = null
                    }
                ) {
                    Text("Cập nhật")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDocDialog = null }) {
                    Text("Hủy")
                }
            }
        )
    }

    // 5. Link Device Quick Dialog
    if (showLinkDeviceDialog != null) {
        val doc = showLinkDeviceDialog!!
        var selectedDevId by remember { mutableStateOf(doc.deviceId) }

        AlertDialog(
            onDismissRequest = { showLinkDeviceDialog = null },
            title = { Text("Liên kết thiết bị") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Chọn thiết bị liên kết với tài liệu '${doc.displayName}':", fontSize = 13.sp)

                    LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedDevId = null }
                                    .background(if (selectedDevId == null) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .padding(8.dp)
                            ) {
                                Text("Không liên kết thiết bị", fontWeight = FontWeight.Medium)
                            }
                        }
                        items(rawDevices) { dev ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedDevId = dev.id }
                                    .background(if (selectedDevId == dev.id) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .padding(8.dp)
                            ) {
                                Column {
                                    Text("${dev.device_id} - ${dev.name}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Cột: ${dev.pole_number} • Đơn vị: ${dev.unit}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.linkDocumentToDevice(doc.id, selectedDevId)
                        showLinkDeviceDialog = null
                    }
                ) {
                    Text("Xác nhận")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLinkDeviceDialog = null }) {
                    Text("Hủy")
                }
            }
        )
    }

    // 6. Delete Document Confirm Dialog
    if (showDeleteConfirmDialog != null) {
        val doc = showDeleteConfirmDialog!!

        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Xác nhận xóa tài liệu")
                }
            },
            text = {
                Text("Bạn có chắc chắn muốn xóa tệp '${doc.displayName}' khỏi Thư viện và bộ nhớ máy không?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDocument(context, doc)
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Xác nhận Xóa")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Hủy")
                }
            }
        )
    }

    // 7. Hộp thoại Quản lý Nhập / Xuất Gói ZIP
    if (showZipOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showZipOptionsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Nhập / Xuất Gói ZIP", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "Đóng gói dữ liệu hệ thống & tài liệu nội bộ dạng tệp nén tiêu chuẩn (.zip) phục vụ sao lưu và tra cứu offline hiện trường.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Card Xuất gói
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("XUẤT GÓI ZIP (EXPORT)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)

                            OutlinedButton(
                                onClick = {
                                    showZipOptionsDialog = false
                                    zipBackupPickerLauncher.launch("sotay_backup_${System.currentTimeMillis()}.zip")
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text("Xuất Toàn Bộ Hệ Thống", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("Trạm, Lộ, Thiết bị, Mạch vòng, Danh mục & Tài liệu", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    showZipOptionsDialog = false
                                    zipDocsOnlyExportLauncher.launch("sotay_documents_${System.currentTimeMillis()}.zip")
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text("Xuất Gói Thư Viện Tài Liệu", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("Chỉ danh mục và toàn bộ tệp tài liệu đính kèm", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    // Card Nhập gói
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("NHẬP GÓI ZIP (IMPORT)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                            Text(
                                "Nạp dữ liệu từ tệp ZIP. Hệ thống sẽ tự động phân tích manifest và cho phép chọn chế độ khôi phục toàn bộ hoặc bổ sung tài liệu.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    showZipOptionsDialog = false
                                    zipRestorePickerLauncher.launch("application/zip")
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Chọn tệp ZIP để Nhập", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showZipOptionsDialog = false }) {
                    Text("Đóng")
                }
            }
        )
    }

    // 8. Hộp thoại Xác nhận & Tùy chọn Nhập gói ZIP
    if (showZipImportConfirmDialog && pendingZipMeta != null && pendingZipUri != null) {
        val meta = pendingZipMeta!!
        AlertDialog(
            onDismissRequest = {
                showZipImportConfirmDialog = false
                pendingZipUri = null
                pendingZipMeta = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Xác nhận nhập gói ZIP", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = meta.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = meta.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (meta.exportedAt.isNotBlank()) {
                        Text("Thời điểm tạo: ${meta.exportedAt}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    HorizontalDivider()

                    Text("Thống kê nội dung gói:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("• Số lượng tài liệu:", fontSize = 12.sp)
                        Text("${meta.totalDocuments} tệp", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    if (meta.hasFullDatabase) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("• Trạm 110kV:", fontSize = 12.sp)
                            Text("${meta.totalSubstations} trạm", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("• Phát tuyến:", fontSize = 12.sp)
                            Text("${meta.totalFeeders} lộ ra", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("• Thiết bị lưới điện:", fontSize = 12.sp)
                            Text("${meta.totalDevices} thiết bị", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Phương thức nhập:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedImportMode = "FULL_RESTORE" }
                        ) {
                            RadioButton(
                                selected = selectedImportMode == "FULL_RESTORE",
                                onClick = { selectedImportMode = "FULL_RESTORE" }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text("Khôi phục toàn bộ (Ghi đè)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text("Đồng bộ toàn bộ CSDL và tài liệu theo gói sao lưu", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedImportMode = "MERGE_DOCUMENTS" }
                        ) {
                            RadioButton(
                                selected = selectedImportMode == "MERGE_DOCUMENTS",
                                onClick = { selectedImportMode = "MERGE_DOCUMENTS" }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text("Nhập bổ sung tài liệu", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text("Chỉ nạp thêm tài liệu/danh mục, giữ nguyên thiết bị hiện tại", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "Chế độ: Nhập bổ sung và cập nhật tài liệu vào Thư viện hiện tại mà không làm thay đổi danh mục thiết bị lưới điện.",
                                fontSize = 11.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = pendingZipUri!!
                        val mode = selectedImportMode
                        showZipImportConfirmDialog = false
                        pendingZipUri = null
                        pendingZipMeta = null

                        viewModel.importZipPackage(
                            context = context,
                            uri = uri,
                            mode = mode,
                            onStart = { isBackupInProgress = true },
                            onComplete = { result ->
                                isBackupInProgress = false
                                Toast.makeText(context, result.substringAfter(":"), Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                ) {
                    Text("Bắt đầu nhập ZIP")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showZipImportConfirmDialog = false
                        pendingZipUri = null
                        pendingZipMeta = null
                    }
                ) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
fun DocumentItemCard(
    doc: LibraryDocument,
    linkedDeviceName: String?,
    onOpen: () -> Unit,
    onShowDetail: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onEdit: () -> Unit,
    onLinkDevice: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onShowDetail() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val iconInfo = getFileIconInfo(doc.fileExtension)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconInfo.backgroundColor)
                    .clickable { onOpen() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconInfo.icon,
                    contentDescription = null,
                    tint = iconInfo.tintColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = doc.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (doc.favorite == 1) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "Yêu thích",
                            tint = Color(0xFFE53935),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = doc.category,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Text(text = formatFileSize(doc.fileSize), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = doc.updatedAt.take(10), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (linkedDeviceName != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = linkedDeviceName,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (doc.description.isNotBlank()) {
                    Text(
                        text = doc.description,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Tùy chọn")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Mở tệp") },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                        onClick = { onOpen(); menuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Xem chi tiết") },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        onClick = { onShowDetail(); menuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text(if (doc.favorite == 1) "Bỏ yêu thích" else "Yêu thích") },
                        leadingIcon = {
                            Icon(
                                if (doc.favorite == 1) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (doc.favorite == 1) Color.Red else Color.Gray
                            )
                        },
                        onClick = { onFavoriteToggle(); menuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Sửa thông tin") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { onEdit(); menuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Liên kết thiết bị") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                        onClick = { onLinkDevice(); menuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Chia sẻ") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = { onShare(); menuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Xóa tài liệu") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                        onClick = { onDelete(); menuExpanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailItemRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// Helpers

val LibraryDocument.fileExtension: String
    get() {
        val lastDot = originalName.lastIndexOf('.')
        return if (lastDot != -1 && lastDot < originalName.length - 1) {
            originalName.substring(lastDot + 1).lowercase()
        } else {
            ""
        }
    }

private fun getFileIconInfo(ext: String): FileIconInfo {
    return when (ext.lowercase()) {
        "pdf" -> FileIconInfo(
            icon = Icons.Default.Warning,
            backgroundColor = Color(0xFFFFEBEE),
            tintColor = Color(0xFFD32F2F)
        )
        "doc", "docx" -> FileIconInfo(
            icon = Icons.Default.Info,
            backgroundColor = Color(0xFFE3F2FD),
            tintColor = Color(0xFF1976D2)
        )
        "xls", "xlsx" -> FileIconInfo(
            icon = Icons.Default.Check,
            backgroundColor = Color(0xFFE8F5E9),
            tintColor = Color(0xFF388E3C)
        )
        "jpg", "jpeg", "png", "gif", "webp" -> FileIconInfo(
            icon = Icons.Default.Lock,
            backgroundColor = Color(0xFFF3E5F5),
            tintColor = Color(0xFF7B1FA2)
        )
        "txt", "csv", "log" -> FileIconInfo(
            icon = Icons.Default.Menu,
            backgroundColor = Color(0xFFECEFF1),
            tintColor = Color(0xFF455A64)
        )
        else -> FileIconInfo(
            icon = Icons.Default.Share,
            backgroundColor = Color(0xFFFFF3E0),
            tintColor = Color(0xFFF57C00)
        )
    }
}

private data class FileIconInfo(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val backgroundColor: Color,
    val tintColor: Color
)

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}

private fun getBaseName(fileName: String): String {
    val lastDot = fileName.lastIndexOf('.')
    return if (lastDot != -1) {
        fileName.substring(0, lastDot)
    } else {
        fileName
    }
}

fun openDocumentFile(context: Context, doc: LibraryDocument, viewModel: AppViewModel? = null) {
    val file = File(doc.localPath)
    if (!file.exists()) {
        Toast.makeText(context, "Tệp không tồn tại trên bộ nhớ máy!", Toast.LENGTH_LONG).show()
        return
    }

    try {
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, file)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, doc.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Mở tài liệu bằng...")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        viewModel?.logDocumentOpen(doc)
    } catch (e: Exception) {
        Toast.makeText(context, "Không thể mở tài liệu: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}

fun shareDocumentFile(context: Context, doc: LibraryDocument, viewModel: AppViewModel? = null) {
    val file = File(doc.localPath)
    if (!file.exists()) {
        Toast.makeText(context, "Tệp không tồn tại trên bộ nhớ!", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, file)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = doc.mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, doc.displayName)
            putExtra(Intent.EXTRA_TEXT, "Chia sẻ tài liệu kỹ thuật: ${doc.displayName}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Chia sẻ tài liệu...")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        viewModel?.logDocumentShare(doc)
    } catch (e: Exception) {
        Toast.makeText(context, "Lỗi chia sẻ: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}
