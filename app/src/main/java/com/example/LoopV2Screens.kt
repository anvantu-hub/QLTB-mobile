package com.example

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoopV2ListScreen(viewModel: AppViewModel) {
    val resolvedLoops by viewModel.resolvedLoopsV2.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quản Lý Vòng Khép V2", fontWeight = FontWeight.Bold) },
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
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    viewModel.editingLoopV2Id = null
                    viewModel.navigateTo(Screen.LOOP_V2_CREATE_EDIT)
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Thêm Vòng V2") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            )
        }
    ) { innerPadding ->
        if (resolvedLoops.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Chưa có vòng khép V2 nào", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Nhấn '+' để thiết lập sơ đồ khép vòng mới", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(resolvedLoops) { dto ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.selectedLoopV2Id = dto.id
                                viewModel.navigateTo(Screen.LOOP_V2_DETAIL)
                            },
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        dto.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("v${dto.version}") }
                                )
                            }

                            if (!dto.isIntegrityValid && dto.integrityWarning != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                                ) {
                                    Text(
                                        dto.integrityWarning,
                                        modifier = Modifier.padding(8.dp),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(12.dp))

                            // Three V2 Zones
                            // ZONE 1: PHÍA A
                            ZoneRow(
                                label = "PHÍA A",
                                station = dto.stationAName,
                                feeder = dto.feederAName,
                                devices = dto.sideADevices,
                                badgeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // ZONE 2: ĐIỂM DỪNG PHÁP LÝ (BOUNDARY)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "ĐIỂM DỪNG PHÁP LÝ",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        dto.legalBoundaryDevice?.name ?: "Chưa xác định",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        dto.legalBoundaryDevice?.device_type ?: "N/A",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // ZONE 3: PHÍA B
                            ZoneRow(
                                label = "PHÍA B",
                                station = dto.stationBName,
                                feeder = dto.feederBName,
                                devices = dto.sideBDevices,
                                badgeColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                                contentColor = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ZoneRow(
    label: String,
    station: String,
    feeder: String,
    devices: List<Device>,
    badgeColor: Color,
    contentColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Text(
                "$station / $feeder",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        
        if (devices.isEmpty()) {
            Text("Không có thiết bị", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Show up to 3 devices
                val displayed = devices.take(3)
                displayed.forEach { dev ->
                    Box(
                        modifier = Modifier
                            .background(badgeColor, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(dev.name, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = contentColor)
                    }
                }
                if (devices.size > 3) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("+${devices.size - 3}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoopV2DetailScreen(viewModel: AppViewModel) {
    val resolvedLoops by viewModel.resolvedLoopsV2.collectAsState()
    val dto = resolvedLoops.find { it.id == viewModel.selectedLoopV2Id }
    
    if (dto == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chi Tiết Vòng Khép V2", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(Screen.LOOP_V2_LIST) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở lại")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.editingLoopV2Id = dto.id
                        viewModel.navigateTo(Screen.LOOP_V2_CREATE_EDIT)
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Sửa")
                    }
                    IconButton(onClick = {
                        viewModel.deleteLoopV2(dto.id) {
                            viewModel.navigateTo(Screen.LOOP_V2_LIST)
                        }
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Xóa", tint = MaterialTheme.colorScheme.error)
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!dto.isIntegrityValid && dto.integrityWarning != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        dto.integrityWarning,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // General Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(dto.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Phiên bản cấu trúc: v${dto.version}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Tạo ngày: ${dto.createdAt}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                }
            }

            // Legal Boundary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.error, CircleShape)
                            .size(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White)
                    }
                    Column {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.error, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("ĐIỂM DỪNG PHÁP LÝ", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            dto.legalBoundaryDevice?.name ?: "Không rõ thiết bị",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "Loại: ${dto.legalBoundaryDevice?.device_type ?: "N/A"} • Cột: ${dto.legalBoundaryDevice?.pole_number ?: "N/A"}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // side A card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("PHÍA NGUỒN A", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Trạm biến áp: ${dto.stationAName}", fontSize = 13.sp)
                    Text("Phát tuyến: ${dto.feederAName}", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Danh sách thiết bị liên kết phía A (Thứ tự điện):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    dto.sideADevices.forEachIndexed { idx, dev ->
                        Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${idx + 1}. ", fontWeight = FontWeight.Bold)
                            Text(dev.name, fontSize = 13.sp)
                        }
                    }
                }
            }

            // side B card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("PHÍA NGUỒN B", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Trạm biến áp: ${dto.stationBName}", fontSize = 13.sp)
                    Text("Phát tuyến: ${dto.feederBName}", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Danh sách thiết bị liên kết phía B (Thứ tự điện):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    dto.sideBDevices.forEachIndexed { idx, dev ->
                        Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${idx + 1}. ", fontWeight = FontWeight.Bold)
                            Text(dev.name, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Topology action button
            Button(
                onClick = { viewModel.navigateTo(Screen.LOOP_V2_CANVAS) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Menu, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Xem Sơ Đồ Topology Canvas", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoopV2CreateEditScreen(viewModel: AppViewModel) {
    val substations by viewModel.substationsList.collectAsState()
    val feeders by viewModel.feedersList.collectAsState()
    val devices by viewModel.rawDevices.collectAsState()
    val resolvedLoops by viewModel.resolvedLoopsV2.collectAsState()

    val editingId = viewModel.editingLoopV2Id
    var isInitialized by remember { mutableStateOf(editingId == null) }
    var editingVersion by remember { mutableIntStateOf(1) }

    var step by remember { mutableIntStateOf(1) }
    
    // States for wizard steps
    var loopName by remember { mutableStateOf("") }
    
    var stationAId by remember { mutableIntStateOf(0) }
    var feederAId by remember { mutableIntStateOf(0) }
    val sideADevices = remember { mutableStateListOf<Device>() }
    var searchQuerySideA by remember { mutableStateOf("") }

    var boundaryDeviceId by remember { mutableIntStateOf(0) }
    var searchQueryBoundary by remember { mutableStateOf("") }

    var stationBId by remember { mutableIntStateOf(0) }
    var feederBId by remember { mutableIntStateOf(0) }
    val sideBDevices = remember { mutableStateListOf<Device>() }
    var searchQuerySideB by remember { mutableStateOf("") }

    var conflictMessage by remember { mutableStateOf<String?>(null) }

    // Load editing DTO asynchronously once available
    LaunchedEffect(editingId, resolvedLoops) {
        if (editingId != null && !isInitialized) {
            val dto = resolvedLoops.find { it.id == editingId }
            if (dto != null) {
                loopName = dto.name
                stationAId = dto.stationAId
                feederAId = dto.feederAId
                sideADevices.clear()
                sideADevices.addAll(dto.sideADevices)
                boundaryDeviceId = dto.legalBoundaryDeviceId
                stationBId = dto.stationBId
                feederBId = dto.feederBId
                sideBDevices.clear()
                sideBDevices.addAll(dto.sideBDevices)
                editingVersion = dto.version
                isInitialized = true
            }
        }
    }

    if (!isInitialized) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text("Đang tải dữ liệu vòng khép...", fontSize = 14.sp)
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editingId != null) "Sửa Vòng Khép V2" else "Tạo Vòng Khép V2", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (editingId != null) {
                            viewModel.navigateTo(Screen.LOOP_V2_DETAIL)
                        } else {
                            viewModel.navigateTo(Screen.LOOP_V2_LIST)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // STEP CONTENT
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(
                    "BƯỚC $step / 5",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                LinearProgressIndicator(
                    progress = { step / 5f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (conflictMessage != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            conflictMessage!!,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                when (step) {
                    1 -> {
                        Text("Thiết lập tên và Trạm A", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = loopName,
                            onValueChange = { loopName = it },
                            label = { Text("Tên Vòng V2") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Chọn Trạm 110kV A:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        substations.forEach { station ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (stationAId != station.id) {
                                            stationAId = station.id
                                            val validFeeders = feeders.filter { it.substation_id == station.id }
                                            if (validFeeders.none { it.id == feederAId }) {
                                                feederAId = 0
                                                sideADevices.clear()
                                            }
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = stationAId == station.id,
                                    onClick = {
                                        if (stationAId != station.id) {
                                            stationAId = station.id
                                            val validFeeders = feeders.filter { it.substation_id == station.id }
                                            if (validFeeders.none { it.id == feederAId }) {
                                                feederAId = 0
                                                sideADevices.clear()
                                            }
                                        }
                                    }
                                )
                                Text(station.name, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                    2 -> {
                        Text("Chọn Phát Tuyến Lộ Phía A", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        val stationA = substations.find { it.id == stationAId }
                        Text("Trạm A đang chọn: ${stationA?.name ?: "Chưa chọn"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(12.dp))
                        val filteredFeeders = feeders.filter { it.substation_id == stationAId }
                        if (filteredFeeders.isEmpty()) {
                            Text("Trạm này chưa có phát tuyến nào. Hãy quay lại bước 1.", color = MaterialTheme.colorScheme.error)
                        } else {
                            filteredFeeders.forEach { f ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (feederAId != f.id) {
                                                feederAId = f.id
                                                sideADevices.removeAll { it.feeder_id != f.id }
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = feederAId == f.id,
                                        onClick = {
                                            if (feederAId != f.id) {
                                                feederAId = f.id
                                                sideADevices.removeAll { it.feeder_id != f.id }
                                            }
                                        }
                                    )
                                    Text(f.name, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                    3 -> {
                        Text("Chọn nhiều thiết bị phía A & Sắp xếp thứ tự điện", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Các thiết bị thuộc Lộ Phía A:", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = searchQuerySideA,
                            onValueChange = { searchQuerySideA = it },
                            label = { Text("Tìm kiếm thiết bị phía A") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val availableDevicesA = devices.filter { it.feeder_id == feederAId }
                        val filteredDevicesA = if (searchQuerySideA.isBlank()) availableDevicesA else {
                            availableDevicesA.filter {
                                it.name.contains(searchQuerySideA, ignoreCase = true) ||
                                it.device_id.contains(searchQuerySideA, ignoreCase = true)
                            }
                        }
                        
                        filteredDevicesA.forEach { dev ->
                            val isChecked = sideADevices.any { it.id == dev.id }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isChecked) {
                                            sideADevices.removeAll { it.id == dev.id }
                                        } else {
                                            sideADevices.add(dev)
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) sideADevices.add(dev) else sideADevices.removeAll { it.id == dev.id }
                                    }
                                )
                                Text("${dev.name} (${dev.device_id})", modifier = Modifier.padding(start = 8.dp))
                            }
                        }

                        if (sideADevices.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Thứ tự điện đã chọn (Bấm mũi tên để Sắp xếp):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            sideADevices.forEachIndexed { index, dev ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                        .padding(8.dp)
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${index + 1}. ${dev.name}", fontSize = 13.sp)
                                    Row {
                                        if (index > 0) {
                                            IconButton(onClick = {
                                                val temp = sideADevices[index]
                                                sideADevices[index] = sideADevices[index - 1]
                                                sideADevices[index - 1] = temp
                                            }, modifier = Modifier.size(30.dp)) {
                                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
                                            }
                                        }
                                        if (index < sideADevices.size - 1) {
                                            IconButton(onClick = {
                                                val temp = sideADevices[index]
                                                sideADevices[index] = sideADevices[index + 1]
                                                sideADevices[index + 1] = temp
                                            }, modifier = Modifier.size(30.dp)) {
                                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                    4 -> {
                        Text("Chọn Điểm Dừng Pháp Lý (Boundary Device)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Chỉ hiển thị các thiết bị thuộc Trạm A (${substations.find { it.id == stationAId }?.name ?: "Trạm A"})", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = searchQueryBoundary,
                            onValueChange = { searchQueryBoundary = it },
                            label = { Text("Tìm kiếm thiết bị điểm dừng") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val stationADevices = devices.filter { dev ->
                            dev.substation_id == stationAId || feeders.find { f -> f.id == dev.feeder_id }?.substation_id == stationAId
                        }

                        val filteredBoundaryDevices = if (searchQueryBoundary.isBlank()) stationADevices else {
                            stationADevices.filter {
                                it.name.contains(searchQueryBoundary, ignoreCase = true) ||
                                it.device_id.contains(searchQueryBoundary, ignoreCase = true)
                            }
                        }

                        if (filteredBoundaryDevices.isEmpty()) {
                            Text("Không tìm thấy thiết bị phù hợp thuộc Trạm A.", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        } else {
                            filteredBoundaryDevices.forEach { dev ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { boundaryDeviceId = dev.id }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = boundaryDeviceId == dev.id, onClick = { boundaryDeviceId = dev.id })
                                    Text("${dev.name} (${dev.device_id})", modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                    5 -> {
                        Text("Phía Nguồn B - Thiết Lập Trạm B & Lộ B", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text("Chọn Trạm B:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        substations.forEach { station ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (stationBId != station.id) {
                                            stationBId = station.id
                                            val validFeeders = feeders.filter { it.substation_id == station.id }
                                            if (validFeeders.none { it.id == feederBId }) {
                                                feederBId = 0
                                                sideBDevices.clear()
                                            }
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = stationBId == station.id,
                                    onClick = {
                                        if (stationBId != station.id) {
                                            stationBId = station.id
                                            val validFeeders = feeders.filter { it.substation_id == station.id }
                                            if (validFeeders.none { it.id == feederBId }) {
                                                feederBId = 0
                                                sideBDevices.clear()
                                            }
                                        }
                                    }
                                )
                                Text(station.name, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Chọn Phát Tuyến Lộ Phía B:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        val filteredFeedersB = feeders.filter { it.substation_id == stationBId }
                        filteredFeedersB.forEach { f ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (feederBId != f.id) {
                                            feederBId = f.id
                                            sideBDevices.removeAll { it.feeder_id != f.id }
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = feederBId == f.id,
                                    onClick = {
                                        if (feederBId != f.id) {
                                            feederBId = f.id
                                            sideBDevices.removeAll { it.feeder_id != f.id }
                                        }
                                    }
                                )
                                Text(f.name, modifier = Modifier.padding(start = 8.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Thiết bị Lộ Phía B (Sắp xếp hướng điện từ Trạm B tới Điểm dừng):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        
                        OutlinedTextField(
                            value = searchQuerySideB,
                            onValueChange = { searchQuerySideB = it },
                            label = { Text("Tìm kiếm thiết bị phía B") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val availableDevicesB = devices.filter { it.feeder_id == feederBId }
                        val filteredDevicesB = if (searchQuerySideB.isBlank()) availableDevicesB else {
                            availableDevicesB.filter {
                                it.name.contains(searchQuerySideB, ignoreCase = true) ||
                                it.device_id.contains(searchQuerySideB, ignoreCase = true)
                            }
                        }

                        filteredDevicesB.forEach { dev ->
                            val isChecked = sideBDevices.any { it.id == dev.id }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isChecked) {
                                            sideBDevices.removeAll { it.id == dev.id }
                                        } else {
                                            sideBDevices.add(dev)
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = isChecked, onCheckedChange = { checked ->
                                    if (checked) sideBDevices.add(dev) else sideBDevices.removeAll { it.id == dev.id }
                                })
                                Text("${dev.name} (${dev.device_id})", modifier = Modifier.padding(start = 8.dp))
                            }
                        }

                        if (sideBDevices.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            sideBDevices.forEachIndexed { index, dev ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${index + 1}. ${dev.name}", fontSize = 13.sp)
                                    Row {
                                        if (index > 0) {
                                            IconButton(onClick = {
                                                val temp = sideBDevices[index]
                                                sideBDevices[index] = sideBDevices[index - 1]
                                                sideBDevices[index - 1] = temp
                                            }, modifier = Modifier.size(30.dp)) {
                                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
                                            }
                                        }
                                        if (index < sideBDevices.size - 1) {
                                            IconButton(onClick = {
                                                val temp = sideBDevices[index]
                                                sideBDevices[index] = sideBDevices[index + 1]
                                                sideBDevices[index + 1] = temp
                                            }, modifier = Modifier.size(30.dp)) {
                                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }
                    }
                }
            }

            // NAVIGATION ACTIONS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (step > 1) {
                    Button(onClick = { step-- }) {
                        Text("Quay lại")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (step < 5) {
                    Button(
                        onClick = {
                            // Basic step validations
                            if (step == 1 && (loopName.isBlank() || stationAId == 0)) {
                                viewModel.showSnackbar("Vui lòng nhập tên vòng và chọn Trạm A")
                            } else if (step == 2 && feederAId == 0) {
                                viewModel.showSnackbar("Vui lòng chọn Phát tuyến Lộ A")
                            } else if (step == 4 && boundaryDeviceId == 0) {
                                viewModel.showSnackbar("Vui lòng chọn Điểm dừng pháp lý")
                            } else {
                                step++
                            }
                        }
                    ) {
                        Text("Tiếp theo")
                    }
                } else {
                    Button(
                        onClick = {
                            val sideADeviceIdsArr = JSONArray().apply { sideADevices.forEach { put(it.id) } }
                            val sideBDeviceIdsArr = JSONArray().apply { sideBDevices.forEach { put(it.id) } }

                            val loopV2ToSave = LoopV2(
                                id = editingId ?: 0,
                                name = loopName.trim(),
                                stationAId = stationAId,
                                feederAId = feederAId,
                                sideADeviceIdsJson = sideADeviceIdsArr.toString(),
                                legalBoundaryDeviceId = boundaryDeviceId,
                                sideBDeviceIdsJson = sideBDeviceIdsArr.toString(),
                                feederBId = feederBId,
                                stationBId = stationBId,
                                layout = "",
                                version = editingVersion
                            )

                            viewModel.saveLoopV2Validated(
                                loop = loopV2ToSave,
                                isUpdate = editingId != null,
                                onResult = { result ->
                                    when (result) {
                                        is LoopV2SaveResult.Success -> {
                                            viewModel.showSnackbar(if (result.isUpdate) "Cập nhật vòng khép V2 thành công!" else "Tạo vòng khép V2 thành công!")
                                            viewModel.navigateTo(Screen.LOOP_V2_LIST)
                                        }
                                        is LoopV2SaveResult.ValidationError -> {
                                            conflictMessage = result.message
                                            viewModel.showSnackbar(result.message)
                                        }
                                        is LoopV2SaveResult.VersionConflict -> {
                                            conflictMessage = result.message
                                            viewModel.showSnackbar(result.message)
                                        }
                                        is LoopV2SaveResult.Error -> {
                                            conflictMessage = result.message
                                            viewModel.showSnackbar(result.message)
                                        }
                                    }
                                }
                            )
                        }
                    ) {
                        Text(if (editingId != null) "Cập nhật" else "Lưu")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoopV2CanvasScreen(viewModel: AppViewModel) {
    val resolvedLoops by viewModel.resolvedLoopsV2.collectAsState()
    val dto = resolvedLoops.find { it.id == viewModel.selectedLoopV2Id }

    if (dto == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Không tìm thấy vòng khép V2!")
        }
        return
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val totalNodes = 2 + dto.sideADevices.size + 1 + dto.sideBDevices.size + 2

    // Dynamic fit view
    fun fitView() {
        val spacingX = 220f
        val totalWidth = (totalNodes - 1) * spacingX + 200f
        scale = (1000f / totalWidth).coerceIn(0.5f, 2.0f)
        offset = Offset(30f, 0f)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Topology Sơ Đồ Khép Vòng V2", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(Screen.LOOP_V2_DETAIL) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở lại")
                    }
                },
                actions = {
                    IconButton(onClick = { /* Trigger fitView via BoxWithConstraints */ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Fit View")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
                .padding(innerPadding)
        ) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val containerWidthPx = with(density) { maxWidth.toPx() }
            val containerHeightPx = with(density) { maxHeight.toPx() }

            fun fitView() {
                val spacingX = 220f
                val totalWidth = (totalNodes - 1) * spacingX + 300f
                scale = (containerWidthPx / totalWidth).coerceIn(0.2f, 2.5f)
                val totalHeight = 200f
                val startX = 100f * scale
                offset = Offset(
                    x = (containerWidthPx - totalWidth * scale) / 2f + 50f * scale,
                    y = (containerHeightPx - containerHeightPx * scale) / 2f
                )
            }

            LaunchedEffect(maxWidth, maxHeight) {
                fitView()
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.2f, 3.0f)
                            offset += pan
                        }
                    }
            ) {
                withTransform({
                    translate(offset.x, offset.y)
                    scale(scale, scale)
                }) {
                    val gridSize = 60f
                    val gridPaint = Color(0xFF1E293B)
                    for (x in 0..size.width.toInt() step gridSize.toInt()) {
                        drawLine(gridPaint, Offset(x.toFloat(), 0f), Offset(x.toFloat(), size.height), 1f)
                    }
                    for (y in 0..size.height.toInt() step gridSize.toInt()) {
                        drawLine(gridPaint, Offset(0f, y.toFloat()), Offset(size.width, y.toFloat()), 1f)
                    }

                    val totalSequence = mutableListOf<String>()
                    totalSequence.add("TRẠM A\n${dto.stationAName}")
                    totalSequence.add("FEEDER A\n${dto.feederAName}")
                    dto.sideADevices.forEach { totalSequence.add(it.name) }
                    totalSequence.add("[ĐIỂM DỪNG]\n${dto.legalBoundaryDevice?.name ?: "Ranh Giới"}")
                    dto.sideBDevices.reversed().forEach { totalSequence.add(it.name) }
                    totalSequence.add("FEEDER B\n${dto.feederBName}")
                    totalSequence.add("TRẠM B\n${dto.stationBName}")

                    val nodeY = size.height / 2f
                    val spacingX = 220f
                    val startX = 100f

                    for (i in 0 until totalSequence.size - 1) {
                        val fromX = startX + i * spacingX
                        val toX = startX + (i + 1) * spacingX
                        
                        val boundaryIdx = 2 + dto.sideADevices.size
                        val isPhiaA = i < boundaryIdx
                        val lineColor = if (isPhiaA) Color(0xFF00E676) else Color(0xFFFFD600)

                        drawLine(
                            color = lineColor,
                            start = Offset(fromX, nodeY),
                            end = Offset(toX, nodeY),
                            strokeWidth = 6f
                        )

                        val arrowX = (fromX + toX) / 2f
                        val arrowPath = Path()
                        
                        if (i < boundaryIdx) {
                            arrowPath.moveTo(arrowX + 10f, nodeY)
                            arrowPath.lineTo(arrowX - 10f, nodeY - 10f)
                            arrowPath.lineTo(arrowX - 10f, nodeY + 10f)
                            arrowPath.close()
                        } else {
                            arrowPath.moveTo(arrowX - 10f, nodeY)
                            arrowPath.lineTo(arrowX + 10f, nodeY - 10f)
                            arrowPath.lineTo(arrowX + 10f, nodeY + 10f)
                            arrowPath.close()
                        }

                        drawPath(arrowPath, color = lineColor)
                    }

                    for (i in 0 until totalSequence.size) {
                        val x = startX + i * spacingX
                        val boundaryIdx = 2 + dto.sideADevices.size
                        
                        val isBoundary = i == boundaryIdx
                        val isStation = i == 0 || i == totalSequence.size - 1
                        val isFeeder = i == 1 || i == totalSequence.size - 2

                        val nodeRadius = if (isBoundary) 32f else if (isStation) 28f else 24f
                        val nodeColor = if (isBoundary) {
                            Color(0xFFEF4444)
                        } else if (isStation) {
                            Color(0xFF3B82F6)
                        } else if (isFeeder) {
                            Color(0xFF8B5CF6)
                        } else {
                            Color(0xFF10B981)
                        }

                        drawCircle(color = nodeColor, radius = nodeRadius, center = Offset(x, nodeY))
                        drawCircle(color = Color.White, radius = nodeRadius / 2f, center = Offset(x, nodeY))

                        if (isBoundary) {
                            drawCircle(
                                color = Color(0xFFFFD600),
                                radius = nodeRadius + 10f,
                                center = Offset(x, nodeY),
                                style = Stroke(width = 3f)
                            )
                        }

                        drawContext.canvas.nativeCanvas.apply {
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.WHITE
                                textSize = 28f
                                isFakeBoldText = isBoundary || isStation
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                            
                            val lines = totalSequence[i].split("\n")
                            var yOffset = nodeY + nodeRadius + 35f
                            lines.forEach { line ->
                                drawText(line, x, yOffset, paint)
                                yOffset += 30f
                            }
                        }
                    }
                }
            }

            // Info Overlay Panel
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xEC1E293B)),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (!dto.isIntegrityValid && dto.integrityWarning != null) {
                        Text(
                            dto.integrityWarning,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF4444)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "CHI TIẾT TOPOLOGY V2",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E676)
                        )
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFEF4444), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("ĐIỂM DỪNG PHÁP LÝ", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Hướng điện: TRẠM A → ĐIỂM DỪNG ← TRẠM B",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Dùng cử chỉ để kéo (Pan) hoặc phóng to thu nhỏ (Pinch Zoom) sơ đồ.",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}
