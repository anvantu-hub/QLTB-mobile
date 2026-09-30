package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.camera.EquipmentGalleryScreen
import com.example.camera.EquipmentImageViewModel
import com.example.camera.StationCameraScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContentUI(viewModel: AppViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val importUiState by viewModel.importUiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    BackHandler(enabled = currentScreen != Screen.DASHBOARD && currentScreen != Screen.LOGIN) {
        when (currentScreen) {
            Screen.LOOP_V2_CANVAS -> viewModel.navigateTo(Screen.LOOP_V2_DETAIL)
            Screen.LOOP_V2_CREATE_EDIT -> {
                if (viewModel.editingLoopV2Id != null) {
                    viewModel.navigateTo(Screen.LOOP_V2_DETAIL)
                } else {
                    viewModel.navigateTo(Screen.LOOP_V2_LIST)
                }
            }
            Screen.LOOP_V2_DETAIL -> viewModel.navigateTo(Screen.LOOP_V2_LIST)
            Screen.LOOP_V2_LIST, Screen.LOOPS -> viewModel.navigateTo(Screen.DASHBOARD)
            Screen.STATION_DETAIL -> viewModel.navigateTo(Screen.SUBSTATIONS)
            Screen.FEEDER_DETAIL -> {
                if (viewModel.selectedSubstationId != null) {
                    viewModel.navigateTo(Screen.STATION_DETAIL)
                } else {
                    viewModel.navigateTo(Screen.FEEDERS)
                }
            }
            Screen.DEVICE_DETAIL -> {
                if (viewModel.deviceNavigationSource == Screen.FEEDER_DETAIL && viewModel.selectedFeederId != null) {
                    viewModel.navigateTo(Screen.FEEDER_DETAIL)
                } else {
                    viewModel.navigateTo(Screen.DEVICES)
                }
            }
            Screen.SUBSTATIONS, Screen.FEEDERS -> viewModel.navigateTo(Screen.DASHBOARD)
            Screen.STATION_CAMERA -> viewModel.navigateTo(Screen.STATION_DETAIL)
            Screen.EQUIPMENT_GALLERY -> viewModel.navigateTo(Screen.STATION_DETAIL)
            Screen.AUDIT_LOGS, Screen.USERS -> viewModel.navigateTo(Screen.ADMIN)
            Screen.DEVICES, Screen.LIBRARY, Screen.DOCUMENT_DETAIL, Screen.CATEGORIES, Screen.ADMIN, Screen.PROFILE -> {
                viewModel.navigateTo(Screen.DASHBOARD)
            }
            else -> {}
        }
    }

    val showBottomBar = currentScreen in listOf(
        Screen.DASHBOARD,
        Screen.DEVICES,
        Screen.LOOP_V2_LIST,
        Screen.LIBRARY,
        Screen.ADMIN
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                AppBottomNavigationBar(
                    currentScreen = currentScreen,
                    onNavigate = { viewModel.navigateTo(it) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                Screen.LOGIN -> LoginScreen(viewModel)
                Screen.DASHBOARD -> DashboardScreen(viewModel)
                Screen.DEVICES -> DevicesScreen(viewModel)
                Screen.DEVICE_DETAIL -> DeviceDetailScreen(viewModel)
                Screen.SUBSTATIONS -> SubstationsScreen(viewModel)
                Screen.STATION_DETAIL -> SubstationDetailScreen(viewModel)
                Screen.FEEDERS -> FeedersScreen(viewModel)
                Screen.FEEDER_DETAIL -> FeederDetailScreen(viewModel)
                Screen.LOOPS, Screen.LOOP_V2_LIST -> LoopV2ListScreen(viewModel)
                Screen.LOOP_V2_DETAIL -> LoopV2DetailScreen(viewModel)
                Screen.LOOP_V2_CREATE_EDIT -> LoopV2CreateEditScreen(viewModel)
                Screen.LOOP_V2_CANVAS -> LoopV2CanvasScreen(viewModel)
                Screen.LIBRARY, Screen.DOCUMENT_DETAIL, Screen.CATEGORIES -> LibraryScreen(viewModel)
                Screen.ADMIN -> AdminScreen(viewModel)
                Screen.AUDIT_LOGS -> AuditLogsScreen(viewModel)
                Screen.USERS -> UsersScreen(viewModel)
                Screen.PROFILE -> ProfileScreen(viewModel)
                Screen.STATION_CAMERA -> {
                    val stationId = viewModel.cameraStationId ?: viewModel.selectedSubstationId ?: 0
                    val stationName = viewModel.cameraStationName
                    val stationCode = viewModel.cameraStationCode
                    val equipmentCode = viewModel.cameraEquipmentCode
                    val equipmentName = viewModel.cameraEquipmentName
                    val cameraVm: EquipmentImageViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                    StationCameraScreen(
                        stationId = stationId,
                        stationName = stationName,
                        stationCode = stationCode,
                        equipmentCode = equipmentCode,
                        equipmentName = equipmentName,
                        viewModel = cameraVm,
                        onBack = { viewModel.navigateTo(Screen.STATION_DETAIL) },
                        onPhotoSaved = { viewModel.navigateTo(Screen.EQUIPMENT_GALLERY) }
                    )
                }
                Screen.EQUIPMENT_GALLERY -> {
                    val stationId = viewModel.cameraStationId ?: viewModel.selectedSubstationId ?: 0
                    val stationName = viewModel.cameraStationName
                    val stationCode = viewModel.cameraStationCode
                    val cameraVm: EquipmentImageViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                    EquipmentGalleryScreen(
                        stationId = stationId,
                        stationName = stationName,
                        viewModel = cameraVm,
                        onBack = { viewModel.navigateTo(Screen.STATION_DETAIL) },
                        onNavigateToCamera = {
                            viewModel.openStationCamera(
                                stationId = stationId,
                                stationName = stationName,
                                stationCode = stationCode,
                                equipmentCode = "TB-110kV",
                                equipmentName = "Thiết bị Trạm"
                            )
                        }
                    )
                }
            }

            // Global Excel Import Dialog
            ExcelImportDialogHost(
                uiState = importUiState,
                onConfirm = { viewModel.confirmImport(it) },
                onDismiss = { viewModel.dismissImportDialog() }
            )
        }
    }
}

@Composable
fun AppBottomNavigationBar(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = currentScreen == Screen.DASHBOARD,
            onClick = { onNavigate(Screen.DASHBOARD) },
            icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
            label = { Text("Tổng quan") },
            modifier = Modifier.testTag("nav_dashboard")
        )
        NavigationBarItem(
            selected = currentScreen == Screen.DEVICES,
            onClick = { onNavigate(Screen.DEVICES) },
            icon = { Icon(Icons.Default.Bolt, contentDescription = "Thiết bị") },
            label = { Text("Thiết bị") },
            modifier = Modifier.testTag("nav_devices")
        )
        NavigationBarItem(
            selected = currentScreen == Screen.LOOP_V2_LIST || currentScreen == Screen.LOOPS,
            onClick = { onNavigate(Screen.LOOP_V2_LIST) },
            icon = { Icon(Icons.Default.Refresh, contentDescription = "Khép vòng") },
            label = { Text("Khép vòng") },
            modifier = Modifier.testTag("nav_loop_v2")
        )
        NavigationBarItem(
            selected = currentScreen == Screen.LIBRARY,
            onClick = { onNavigate(Screen.LIBRARY) },
            icon = { Icon(Icons.Default.Folder, contentDescription = "Thư viện") },
            label = { Text("Thư viện") },
            modifier = Modifier.testTag("nav_library")
        )
        NavigationBarItem(
            selected = currentScreen == Screen.ADMIN,
            onClick = { onNavigate(Screen.ADMIN) },
            icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = "Quản trị") },
            label = { Text("Quản trị") },
            modifier = Modifier.testTag("nav_admin")
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: AppViewModel) {
    val substations by viewModel.substationsList.collectAsStateWithLifecycle()
    val feeders by viewModel.feedersList.collectAsStateWithLifecycle()
    val devices by viewModel.devicesList.collectAsStateWithLifecycle()
    val loopsV2 by viewModel.resolvedLoopsV2.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quản Lý Lưới Điện 22kV", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.navigateTo(Screen.PROFILE) }) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Tài khoản")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Welcome Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ElectricBolt, contentDescription = null, tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Xin chào, ${currentUser?.full_name ?: "Kỹ sư vận hành"}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Vai trò: ${currentUser?.role ?: "Quản trị viên"}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text("Thống Kê Hệ Thống", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            // 2x2 Grid stats
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    title = "Trạm 110kV",
                    count = substations.size.toString(),
                    icon = Icons.Default.LocationCity,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.navigateTo(Screen.SUBSTATIONS) }
                )
                StatCard(
                    title = "Phát tuyến",
                    count = feeders.size.toString(),
                    icon = Icons.Default.AltRoute,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.navigateTo(Screen.FEEDERS) }
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    title = "Thiết bị 22kV",
                    count = devices.size.toString(),
                    icon = Icons.Default.Bolt,
                    color = Color(0xFF00897B),
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.navigateTo(Screen.DEVICES) }
                )
                StatCard(
                    title = "Vòng khép V2",
                    count = loopsV2.size.toString(),
                    icon = Icons.Default.Refresh,
                    color = Color(0xFFF57C00),
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.navigateTo(Screen.LOOP_V2_LIST) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Actions
            Text("Chức Năng Nhanh", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        viewModel.editingLoopV2Id = null
                        viewModel.navigateTo(Screen.LOOP_V2_CREATE_EDIT)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tạo Vòng V2")
                }
                OutlinedButton(
                    onClick = { viewModel.navigateTo(Screen.ADMIN) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Nhập Excel")
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    count: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(count, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            var fileName: String? = null
            context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) fileName = cursor.getString(idx)
                }
            }
            viewModel.analyzeExcelFile(it, fileName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quản Trị Hệ Thống", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Excel Import Section
            Text("Nhập Dữ Liệu Excel", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Hỗ trợ nhập trực tiếp 4 file mẫu tiêu chuẩn:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("1. Import_18 Tram_110kV.xlsx", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("2. Import 145 Phat_Tuyen.xlsx", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("3. Mau_Import_TBDC_LBS.xlsx", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("4. Mau_Import_TBDC_REC.xlsx", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Button(
                        onClick = {
                            filePickerLauncher.launch(
                                arrayOf(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "application/vnd.ms-excel"
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_excel_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Chọn tệp Excel để nhập")
                    }
                }
            }

            // System Management
            Text("Hệ Thống & Kiểm Toán", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("Nhật Ký Kiểm Toán (Audit Logs)", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Xem toàn bộ lịch sử thao tác dữ liệu", fontSize = 12.sp) },
                        leadingContent = { Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                        modifier = Modifier.clickable { viewModel.navigateTo(Screen.AUDIT_LOGS) }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Quản Lý Người Dùng", fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Danh sách tài khoản kỹ sư vận hành", fontSize = 12.sp) },
                        leadingContent = { Icon(Icons.Default.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                        modifier = Modifier.clickable { viewModel.navigateTo(Screen.USERS) }
                    )
                }
            }
        }
    }
}

@Composable
fun ExcelImportDialogHost(
    uiState: ExcelImportUiState,
    onConfirm: (ImportPlan) -> Unit,
    onDismiss: () -> Unit
) {
    when (uiState) {
        is ExcelImportUiState.Idle -> {}
        is ExcelImportUiState.Analyzing -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Đang phân tích tệp Excel") },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Text("Đang đọc tiêu đề và xác thực các ràng buộc...")
                    }
                },
                confirmButton = {}
            )
        }
        is ExcelImportUiState.Importing -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Đang nhập dữ liệu") },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Text("Đang cập nhật CSDL SQLite trong giao dịch an toàn...")
                    }
                },
                confirmButton = {}
            )
        }
        is ExcelImportUiState.Preview -> {
            val plan = uiState.plan
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text(
                        "Xem trước: ${plan.fileType.displayName}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Summary info
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (plan.canExecute)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("• Thêm mới (Insert): ${plan.toInsertCount}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("• Cập nhật (Update): ${plan.toUpdateCount}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Errors
                        if (plan.errors.isNotEmpty()) {
                            Text("Lỗi phát hiện (${plan.errors.size}):", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                plan.errors.forEach { err ->
                                    Text("• Dòng ${err.rowNumber}: ${err.message}", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        // Warnings
                        if (plan.warnings.isNotEmpty()) {
                            Text("Cảnh báo (${plan.warnings.size}):", fontWeight = FontWeight.Bold, color = Color(0xFFF57C00), fontSize = 13.sp)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                plan.warnings.take(5).forEach { warn ->
                                    Text("• Dòng ${warn.rowNumber}: ${warn.message}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (plan.warnings.size > 5) {
                                    Text("... và ${plan.warnings.size - 5} cảnh báo khác", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { onConfirm(plan) },
                        enabled = plan.canExecute,
                        modifier = Modifier.testTag("confirm_import_button")
                    ) {
                        Text("Xác nhận nhập")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Hủy")
                    }
                }
            )
        }
        is ExcelImportUiState.Success -> {
            val result = uiState.result
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Nhập dữ liệu thành công!", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("• Thêm mới thành công: ${result.insertedCount} bản ghi", fontSize = 13.sp)
                        Text("• Cập nhật thành công: ${result.updatedCount} bản ghi", fontSize = 13.sp)
                        Text("Dữ liệu đã được lưu trữ an toàn trong CSDL cục bộ.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = {
                    Button(onClick = onDismiss) {
                        Text("Hoàn tất")
                    }
                }
            )
        }
        is ExcelImportUiState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Lỗi nhập dữ liệu", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
                text = { Text(uiState.message, fontSize = 13.sp) },
                confirmButton = {
                    Button(onClick = onDismiss) {
                        Text("Đóng")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(viewModel: AppViewModel) {
    val devices by viewModel.devicesList.collectAsStateWithLifecycle()
    val feeders by viewModel.feedersList.collectAsStateWithLifecycle()
    val substations by viewModel.substationsList.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf("ALL") }
    var selectedSubstationFilterId by remember { mutableStateOf<Int?>(null) }
    var selectedFeederFilterId by remember { mutableStateOf<Int?>(null) }

    // Dropdown expanded states
    var subDropdownExpanded by remember { mutableStateOf(false) }
    var feederDropdownExpanded by remember { mutableStateOf(false) }

    // When substation filter changes, reset feeder filter if it doesn't belong to the new substation
    val availableFeeders = remember(feeders, selectedSubstationFilterId) {
        if (selectedSubstationFilterId == null) {
            feeders
        } else {
            feeders.filter { it.substation_id == selectedSubstationFilterId }
        }
    }

    LaunchedEffect(selectedSubstationFilterId) {
        if (selectedFeederFilterId != null) {
            val valid = availableFeeders.any { it.id == selectedFeederFilterId }
            if (!valid) {
                selectedFeederFilterId = null
            }
        }
    }

    val filtered = remember(devices, searchQuery, selectedTypeFilter, selectedSubstationFilterId, selectedFeederFilterId) {
        devices.filter { dev ->
            val matchSubstation = selectedSubstationFilterId == null || dev.substation_id == selectedSubstationFilterId
            val matchFeeder = selectedFeederFilterId == null || dev.feeder_id == selectedFeederFilterId
            val matchType = selectedTypeFilter == "ALL" || dev.device_type.equals(selectedTypeFilter, ignoreCase = true)
            val matchSearch = searchQuery.isBlank() ||
                dev.name.contains(searchQuery, ignoreCase = true) ||
                dev.device_id.contains(searchQuery, ignoreCase = true) ||
                dev.pole_number.contains(searchQuery, ignoreCase = true)

            matchSubstation && matchFeeder && matchType && matchSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Thiết Bị 22kV (${filtered.size}/${devices.size})", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("device_search_input"),
                placeholder = { Text("Tìm theo tên, mã, số trụ...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

            // Hierarchical Dropdowns Row (Substation & Feeder)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Substation Dropdown
                Box(modifier = Modifier.weight(1f)) {
                    val subName = substations.find { it.id == selectedSubstationFilterId }?.name ?: "Tất cả trạm"
                    OutlinedButton(
                        onClick = { subDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = subName,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    DropdownMenu(
                        expanded = subDropdownExpanded,
                        onDismissRequest = { subDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Tất cả trạm") },
                            onClick = {
                                selectedSubstationFilterId = null
                                subDropdownExpanded = false
                            }
                        )
                        substations.forEach { sub ->
                            DropdownMenuItem(
                                text = { Text("${sub.name} (${sub.substation_code})") },
                                onClick = {
                                    selectedSubstationFilterId = sub.id
                                    subDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Feeder Dropdown
                Box(modifier = Modifier.weight(1f)) {
                    val feederName = availableFeeders.find { it.id == selectedFeederFilterId }?.feeder_code ?: "Tất cả tuyến"
                    OutlinedButton(
                        onClick = { feederDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = feederName,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    DropdownMenu(
                        expanded = feederDropdownExpanded,
                        onDismissRequest = { feederDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Tất cả tuyến") },
                            onClick = {
                                selectedFeederFilterId = null
                                feederDropdownExpanded = false
                            }
                        )
                        availableFeeders.forEach { f ->
                            DropdownMenuItem(
                                text = { Text("${f.feeder_code} - ${f.name}") },
                                onClick = {
                                    selectedFeederFilterId = f.id
                                    feederDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Type Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedTypeFilter == "ALL",
                    onClick = { selectedTypeFilter = "ALL" },
                    label = { Text("Tất cả", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedTypeFilter == "LBS",
                    onClick = { selectedTypeFilter = "LBS" },
                    label = { Text("LBS", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedTypeFilter == "REC",
                    onClick = { selectedTypeFilter = "REC" },
                    label = { Text("REC", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedTypeFilter == "DS",
                    onClick = { selectedTypeFilter = "DS" },
                    label = { Text("DS", fontSize = 11.sp) }
                )
            }

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Không tìm thấy thiết bị nào", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(filtered, key = { it.id }) { dev ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.openDevice(dev.id, Screen.DEVICES)
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        dev.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(dev.device_type, fontSize = 10.sp) }
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Mã: ${dev.device_id} • Trụ: ${dev.pole_number.ifBlank { "N/A" }}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val feederName = feeders.find { it.id == dev.feeder_id }?.name ?: "N/A"
                                val subName = substations.find { it.id == dev.substation_id }?.name ?: "N/A"
                                Text(
                                    "Trạm: $subName • Tuyến: $feederName • TT: ${dev.switch_status}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(viewModel: AppViewModel) {
    val devices by viewModel.devicesList.collectAsStateWithLifecycle()
    val feeders by viewModel.feedersList.collectAsStateWithLifecycle()
    val substations by viewModel.substationsList.collectAsStateWithLifecycle()
    val isFetchingLocation by viewModel.isFetchingLocation.collectAsStateWithLifecycle()
    val lastAccuracy by viewModel.lastGpsAccuracy.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val dev = devices.find { it.id == viewModel.selectedDeviceId }
    val feeder = feeders.find { it.id == dev?.feeder_id }
    val substation = substations.find { it.id == dev?.substation_id }

    val isLinkInconsistent = dev != null && feeder != null && dev.substation_id != null && dev.substation_id != feeder.substation_id

    var showGpsSettingsDialog by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            if (!LocationHelper.isGpsEnabled(context)) {
                showGpsSettingsDialog = true
            } else {
                dev?.let { d ->
                    viewModel.fetchAndSaveDeviceLocation(d.id, context) { _, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            Toast.makeText(
                context,
                "Ứng dụng cần quyền vị trí để lấy tọa độ GPS hiện trường. Vui lòng cấp quyền trong Cài đặt nếu bạn đã từ chối.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    if (showGpsSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showGpsSettingsDialog = false },
            title = { Text("Bật dịch vụ định vị (GPS)", fontWeight = FontWeight.Bold) },
            text = { Text("Dịch vụ định vị GPS trên thiết bị đang tắt. Vui lòng bật vị trí trong Cài đặt để ứng dụng có thể lấy tọa độ hiện trường chính xác.") },
            confirmButton = {
                Button(onClick = {
                    showGpsSettingsDialog = false
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }) {
                    Text("Mở Cài đặt")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGpsSettingsDialog = false }) {
                    Text("Đóng")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(dev?.name ?: "Chi Tiết Thiết Bị", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (viewModel.deviceNavigationSource == Screen.FEEDER_DETAIL && viewModel.selectedFeederId != null) {
                            viewModel.navigateTo(Screen.FEEDER_DETAIL)
                        } else {
                            viewModel.navigateTo(Screen.DEVICES)
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        if (dev == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Không tìm thấy thông tin thiết bị")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Inconsistency Warning
                if (isLinkInconsistent) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = "Cảnh báo", tint = MaterialTheme.colorScheme.error)
                            Text(
                                text = "Cảnh báo: Dữ liệu liên kết không nhất quán! Trạm trên thiết bị khác với Trạm của phát tuyến.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Full Asset Hierarchy Display
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Cấu Trúc Lưới Điện", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        HorizontalDivider()
                        DetailRow("Trạm 110kV:", "${substation?.name ?: "N/A"} (${substation?.substation_code ?: "N/A"})")
                        DetailRow("Phát tuyến:", "${feeder?.name ?: "N/A"} (${feeder?.feeder_code ?: "N/A"})")
                        DetailRow("Thiết bị:", "${dev.name} (${dev.device_id})")
                    }
                }

                // Technical specs
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Thông Tin Kỹ Thuật", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        HorizontalDivider()
                        DetailRow("Mã thiết bị:", dev.device_id)
                        DetailRow("Tên thiết bị:", dev.name)
                        DetailRow("Loại thiết bị:", dev.device_type)
                        DetailRow("Vị trí trụ:", dev.pole_number)
                        DetailRow("Đơn vị quản lý:", dev.unit)
                        DetailRow("Đội quản lý:", dev.team)
                        DetailRow("Trạng thái dao:", dev.switch_status)
                        DetailRow("SCADA:", dev.scada_status)
                        DetailRow("Rơ-le 79:", dev.relay_79)
                    }
                }

                // Location Management Card
                LocationManagementSection(
                    title = "VỊ TRÍ THIẾT BỊ",
                    latitude = dev.latitude,
                    longitude = dev.longitude,
                    googleMapsUrl = dev.google_maps_url,
                    isFetching = isFetchingLocation,
                    lastAccuracy = lastAccuracy,
                    onFetchGps = {
                        if (LocationHelper.hasLocationPermission(context)) {
                            if (!LocationHelper.isGpsEnabled(context)) {
                                showGpsSettingsDialog = true
                            } else {
                                viewModel.fetchAndSaveDeviceLocation(dev.id, context) { _, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    onSaveUrl = { url ->
                        viewModel.saveDeviceGoogleMapsUrl(dev.id, url) { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpenMaps = {
                        val ok = LocationService.openGoogleMaps(
                            context = context,
                            latitude = dev.latitude,
                            longitude = dev.longitude,
                            googleMapsUrl = dev.google_maps_url,
                            label = dev.name
                        )
                        if (!ok) {
                            Toast.makeText(context, "Chưa có vị trí", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpenDirections = {
                        val ok = LocationService.openDirections(
                            context = context,
                            latitude = dev.latitude,
                            longitude = dev.longitude,
                            googleMapsUrl = dev.google_maps_url
                        )
                        if (!ok) {
                            Toast.makeText(context, "Thiết bị chưa có vị trí", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun LocationManagementSection(
    title: String,
    latitude: Double?,
    longitude: Double?,
    googleMapsUrl: String,
    isFetching: Boolean,
    lastAccuracy: Float?,
    onFetchGps: () -> Unit,
    onSaveUrl: (String) -> Unit,
    onOpenMaps: () -> Unit,
    onOpenDirections: () -> Unit
) {
    var showUrlDialog by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf(googleMapsUrl) }
    val hasCoordinates = latitude != null && longitude != null
    val hasUrl = googleMapsUrl.isNotBlank()
    val hasAnyLocation = hasCoordinates || hasUrl

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasAnyLocation) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (hasCoordinates) {
                    Surface(
                        color = Color(0xFF2E7D32).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(14.dp))
                            Text("✅ Đã có vị trí", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                            Text("⚠ Chưa có vị trí", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            HorizontalDivider()

            if (hasCoordinates) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Vĩ độ:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$latitude", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Kinh độ:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$longitude", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                if (lastAccuracy != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Độ chính xác GPS:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("±${lastAccuracy.toInt()} m", fontSize = 12.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (hasUrl) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Google Maps:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        googleMapsUrl,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isFetching) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Đang lấy vị trí...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onFetchGps,
                    enabled = !isFetching,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (hasCoordinates) "📍 CẬP NHẬT VỊ TRÍ" else "📍 LẤY VỊ TRÍ HIỆN TẠI", fontSize = 11.sp, maxLines = 1)
                }

                OutlinedButton(
                    onClick = {
                        urlInput = googleMapsUrl
                        showUrlDialog = true
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("🔗 NHẬP LINK GOOGLE MAPS", fontSize = 11.sp, maxLines = 1)
                }
            }

            // Open Map & Directions Buttons (if location or URL exists)
            if (hasAnyLocation) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onOpenMaps,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("🗺️ MỞ GOOGLE MAPS", fontSize = 11.sp, maxLines = 1)
                    }

                    FilledTonalButton(
                        onClick = onOpenDirections,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("🧭 CHỈ ĐƯỜNG", fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
    }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("Dán / Nhập Link Google Maps", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nhập hoặc dán link Google Maps (bắt đầu bằng https://):", fontSize = 12.sp)
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://maps.google.com/...") },
                        singleLine = false,
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSaveUrl(urlInput)
                    showUrlDialog = false
                }) {
                    Text("Lưu link")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "N/A" }, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubstationsScreen(viewModel: AppViewModel) {
    val substations by viewModel.substationsList.collectAsStateWithLifecycle()
    val feeders by viewModel.feedersList.collectAsStateWithLifecycle()
    val devices by viewModel.devicesList.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Danh Sách Trạm 110kV (${substations.size})", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(Screen.DASHBOARD) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(substations, key = { it.id }) { sub ->
                val feederCount = feeders.count { it.substation_id == sub.id }
                val deviceCount = devices.count { it.substation_id == sub.id }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.openSubstation(sub.id) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(sub.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Mã: ${sub.substation_code} • Địa chỉ: ${sub.address.ifBlank { "N/A" }}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Số phát tuyến: $feederCount", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                Text("Số thiết bị: $deviceCount", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = "Chi tiết")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubstationDetailScreen(viewModel: AppViewModel) {
    val substation by viewModel.observeSelectedSubstation().collectAsStateWithLifecycle(initialValue = null)
    val feeders by viewModel.observeFeedersForSelectedSubstation().collectAsStateWithLifecycle(initialValue = emptyList())
    val devices by viewModel.observeDevicesForSelectedSubstation().collectAsStateWithLifecycle(initialValue = emptyList())
    val isFetchingLocation by viewModel.isFetchingLocation.collectAsStateWithLifecycle()
    val lastAccuracy by viewModel.lastGpsAccuracy.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showGpsSettingsDialog by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            if (!LocationHelper.isGpsEnabled(context)) {
                showGpsSettingsDialog = true
            } else {
                substation?.let { sub ->
                    viewModel.fetchAndSaveSubstationLocation(sub.id, context) { _, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            Toast.makeText(
                context,
                "Ứng dụng cần quyền vị trí để lấy tọa độ GPS trạm 110kV. Vui lòng cấp quyền trong Cài đặt nếu bạn đã từ chối.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    if (showGpsSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showGpsSettingsDialog = false },
            title = { Text("Bật dịch vụ định vị (GPS)", fontWeight = FontWeight.Bold) },
            text = { Text("Dịch vụ định vị GPS trên thiết bị đang tắt. Vui lòng bật vị trí trong Cài đặt để ứng dụng có thể lấy tọa độ trạm 110kV chính xác.") },
            confirmButton = {
                Button(onClick = {
                    showGpsSettingsDialog = false
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }) {
                    Text("Mở Cài đặt")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGpsSettingsDialog = false }) {
                    Text("Đóng")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(substation?.name ?: "Chi Tiết Trạm 110kV", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(Screen.SUBSTATIONS) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        if (substation == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Không tìm thấy trạm 110kV")
            }
        } else {
            val sub = substation!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Station Overview Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(sub.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Mã trạm: ${sub.substation_code}", fontSize = 13.sp)
                        Text("Địa chỉ: ${sub.address.ifBlank { "N/A" }}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Tổng số tuyến ra: ${feeders.size}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Tổng số thiết bị: ${devices.size}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }

                // Substation Location Card
                LocationManagementSection(
                    title = "VỊ TRÍ TRẠM 110kV",
                    latitude = sub.latitude,
                    longitude = sub.longitude,
                    googleMapsUrl = sub.google_maps_url,
                    isFetching = isFetchingLocation,
                    lastAccuracy = lastAccuracy,
                    onFetchGps = {
                        if (LocationHelper.hasLocationPermission(context)) {
                            if (!LocationHelper.isGpsEnabled(context)) {
                                showGpsSettingsDialog = true
                            } else {
                                viewModel.fetchAndSaveSubstationLocation(sub.id, context) { _, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    onSaveUrl = { url ->
                        viewModel.saveSubstationGoogleMapsUrl(sub.id, url) { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpenMaps = {
                        val ok = LocationService.openGoogleMaps(
                            context = context,
                            latitude = sub.latitude,
                            longitude = sub.longitude,
                            googleMapsUrl = sub.google_maps_url,
                            label = sub.name
                        )
                        if (!ok) {
                            Toast.makeText(context, "Chưa có thông tin vị trí", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpenDirections = {
                        val ok = LocationService.openDirections(
                            context = context,
                            latitude = sub.latitude,
                            longitude = sub.longitude,
                            googleMapsUrl = sub.google_maps_url
                        )
                        if (!ok) {
                            Toast.makeText(context, "Trạm chưa có vị trí", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                // Substation Equipment Images Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("HÌNH ẢNH HIỆN TRƯỜNG THIẾT BỊ", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            "Chụp ảnh có gắn mã trạm, nhãn thiết bị (MBA, LBS, Recloser) và xem bộ sưu tập ảnh hiện trường.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.openStationCamera(
                                        stationId = sub.id,
                                        stationName = sub.name,
                                        stationCode = sub.substation_code,
                                        equipmentCode = "TB-110kV",
                                        equipmentName = "Thiết bị Trạm"
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Chụp ảnh", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.openEquipmentGallery(
                                        stationId = sub.id,
                                        stationName = sub.name,
                                        stationCode = sub.substation_code
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Bộ sưu tập", fontSize = 12.sp)
                            }
                        }
                    }
                }

                Text(
                    "Danh Sách Phát Tuyến thuộc Trạm (${feeders.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                if (feeders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("Chưa có phát tuyến nào thuộc trạm này", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(feeders, key = { it.id }) { f ->
                            val feederDeviceCount = devices.count { it.feeder_id == f.id }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.openFeeder(f.id) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("${f.feeder_code} - ${f.name}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("Trạng thái: ${f.status} • Số thiết bị: $feederDeviceCount", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Chi tiết tuyến")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedersScreen(viewModel: AppViewModel) {
    val selectedSubstationFilterId by viewModel.selectedSubstationFilterId.collectAsStateWithLifecycle()
    val reQueriedFeeders by viewModel.feedersFilteredBySubstation.collectAsStateWithLifecycle()
    val allFeedersList by viewModel.feedersList.collectAsStateWithLifecycle()
    val substations by viewModel.substationsList.collectAsStateWithLifecycle()
    val devices by viewModel.devicesList.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val filteredFeeders = remember(reQueriedFeeders, searchQuery) {
        if (searchQuery.isBlank()) {
            reQueriedFeeders
        } else {
            reQueriedFeeders.filter { f ->
                f.feeder_code.contains(searchQuery, ignoreCase = true) ||
                        f.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Danh Sách Tuyến Ra (${filteredFeeders.size})", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(Screen.DASHBOARD) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở lại")
                    }
                },
                actions = {
                    if (selectedSubstationFilterId != null) {
                        TextButton(
                            onClick = { viewModel.setSelectedSubstationFilter(null) },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                        ) {
                            Text("Xóa lọc", fontSize = 12.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("feeders_screen_container")
        ) {
            // Filter & Search Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Search Input Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().testTag("feeder_search_input"),
                    placeholder = { Text("Tìm tên hoặc mã tuyến ra...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Xóa tìm kiếm")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                // Substation Filter Dropdown Component
                SubstationDropdownFilter(
                    substations = substations,
                    selectedSubstationId = selectedSubstationFilterId,
                    allFeedersCount = allFeedersList.size,
                    getFeederCountForSubstation = { subId -> allFeedersList.count { it.substation_id == subId } },
                    onSubstationSelected = { subId -> viewModel.setSubstationFilter(subId) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Filter Chips (Horizontal Scroll)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedSubstationFilterId == null,
                            onClick = { viewModel.setSelectedSubstationFilter(null) },
                            label = { Text("Tất cả (${allFeedersList.size})", fontSize = 12.sp) },
                            modifier = Modifier.testTag("substation_chip_all")
                        )
                    }
                    items(substations, key = { it.id }) { sub ->
                        val count = allFeedersList.count { it.substation_id == sub.id }
                        FilterChip(
                            selected = selectedSubstationFilterId == sub.id,
                            onClick = {
                                val nextId = if (selectedSubstationFilterId == sub.id) null else sub.id
                                viewModel.setSelectedSubstationFilter(nextId)
                            },
                            label = { Text("${sub.substation_code} ($count)", fontSize = 12.sp) },
                            modifier = Modifier.testTag("substation_chip_${sub.id}")
                        )
                    }
                }
            }

            // Feeder List
            if (filteredFeeders.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Không tìm thấy tuyến ra nào phù hợp",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filteredFeeders, key = { it.id }) { f ->
                        val sub = substations.find { it.id == f.substation_id }
                        val subName = sub?.name ?: "N/A"
                        val devCount = devices.count { it.feeder_id == f.id }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("feeder_item_${f.id}")
                                .clickable { viewModel.openFeeder(f.id) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        "${f.feeder_code} - ${f.name}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        "Trạm: $subName • Số thiết bị: $devCount",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!f.start_point.isNullOrEmpty() || !f.end_point.isNullOrEmpty()) {
                                        Text(
                                            "Điểm đầu-cuối: ${f.start_point ?: "---"} ➔ ${f.end_point ?: "---"}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = "Chi tiết")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubstationDropdownFilter(
    substations: List<Substation>,
    selectedSubstationId: Int?,
    allFeedersCount: Int,
    getFeederCountForSubstation: (Int) -> Int,
    onSubstationSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedSub = substations.find { it.id == selectedSubstationId }
    val selectedText = if (selectedSub != null) "${selectedSub.name} (${selectedSub.substation_code})" else "Tất cả trạm 110kV"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            label = { Text("Lọc Theo Trạm 110kV") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .testTag("feeder_substation_filter"),
            shape = RoundedCornerShape(10.dp)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        "Tất cả trạm 110kV ($allFeedersCount tuyến)",
                        fontWeight = if (selectedSubstationId == null) FontWeight.Bold else FontWeight.Normal
                    )
                },
                onClick = {
                    onSubstationSelected(null)
                    expanded = false
                }
            )
            substations.forEach { sub ->
                val feederCountInSub = getFeederCountForSubstation(sub.id)
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${sub.name} (${sub.substation_code})",
                                fontWeight = if (selectedSubstationId == sub.id) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                "$feederCountInSub tuyến",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    },
                    onClick = {
                        onSubstationSelected(sub.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeederDetailScreen(viewModel: AppViewModel) {
    val feeder by viewModel.observeSelectedFeeder().collectAsStateWithLifecycle(initialValue = null)
    val substation by viewModel.observeSelectedSubstation().collectAsStateWithLifecycle(initialValue = null)
    val devices by viewModel.observeDevicesForSelectedFeeder().collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(feeder?.name ?: "Chi Tiết Phát Tuyến", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (viewModel.selectedSubstationId != null) {
                            viewModel.navigateTo(Screen.STATION_DETAIL)
                        } else {
                            viewModel.navigateTo(Screen.FEEDERS)
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        if (feeder == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Không tìm thấy phát tuyến")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Feeder Header Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${feeder!!.feeder_code} - ${feeder!!.name}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Thuộc trạm: ${substation?.name ?: "N/A"} (${substation?.substation_code ?: "N/A"})", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        DetailRow("Điểm đầu:", feeder!!.start_point.ifBlank { "Trạm 110kV" })
                        DetailRow("Điểm cuối:", feeder!!.end_point.ifBlank { "N/A" })
                        DetailRow("Trạng thái:", feeder!!.status)
                        DetailRow("Tổng số thiết bị:", "${devices.size}")
                    }
                }

                Text(
                    "Danh Sách Thiết Bị Trên Tuyến (${devices.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                if (devices.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Chưa có thiết bị nào trên tuyến này", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(devices, key = { it.id }) { dev ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.openDevice(dev.id, Screen.FEEDER_DETAIL) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(dev.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        SuggestionChip(
                                            onClick = {},
                                            label = { Text(dev.device_type, fontSize = 10.sp) }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Mã: ${dev.device_id} • Trụ: ${dev.pole_number.ifBlank { "N/A" }}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Trạng thái dao: ${dev.switch_status} • SCADA: ${dev.scada_status}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogsScreen(viewModel: AppViewModel) {
    val logs by viewModel.auditLogsList.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nhật Ký Kiểm Toán (${logs.size})", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(Screen.ADMIN) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Chưa có nhật ký kiểm toán nào")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(log.action, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                Text(log.created_at, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(log.details, fontSize = 12.sp)
                            Text("Thực hiện bởi: ${log.user_fullname} (${log.username})", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(viewModel: AppViewModel) {
    val users by viewModel.usersList.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quản Lý Người Dùng (${users.size})", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(Screen.ADMIN) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(users, key = { it.id }) { user ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    ListItem(
                        headlineContent = { Text(user.full_name, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Tài khoản: ${user.username} • Quyền: ${user.role}", fontSize = 12.sp) },
                        leadingContent = { Icon(Icons.Default.AccountCircle, contentDescription = null) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(viewModel: AppViewModel) {
    val user by viewModel.currentUser.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hồ Sơ Cá Nhân", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(Screen.DASHBOARD) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            }

            Text(user?.full_name ?: "Kỹ sư vận hành", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Tài khoản: ${user?.username ?: "admin"}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailRow("Vai trò:", user?.role ?: "Quản trị viên")
                    DetailRow("Đơn vị:", user?.unit ?: "ĐL Hải Châu")
                    DetailRow("Đội:", user?.team ?: "Tổ Thao Tác Lưu Động")
                    DetailRow("Chức danh:", user?.title ?: "Kỹ sư")
                    DetailRow("Số điện thoại:", user?.phone ?: "0905111222")
                    DetailRow("Email:", user?.email ?: "admin@grid.vn")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { viewModel.logout() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Đăng xuất")
            }
        }
    }
}

@Composable
fun LoginScreen(viewModel: AppViewModel) {
    var username by remember { mutableStateOf("admin") }
    var password by remember { mutableStateOf("admin123") }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Default.ElectricBolt,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "QLTB Lưới Điện 22kV",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Tên đăng nhập") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Mật khẩu") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Button(
                    onClick = {
                        viewModel.login(username, password) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (success) {
                                viewModel.navigateTo(Screen.DASHBOARD)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Đăng nhập")
                }
            }
        }
    }
}
