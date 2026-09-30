package com.example.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Màn hình bộ sưu tập ảnh thiết bị Trạm 110kV (EquipmentGalleryScreen).
 * Hiển thị dạng lưới ảnh, hỗ trợ xem chi tiết với Pinch-to-zoom và hiển thị đầy đủ Metadata kỹ thuật.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquipmentGalleryScreen(
    stationId: Int,
    stationName: String,
    viewModel: EquipmentImageViewModel,
    onBack: () -> Unit,
    onNavigateToCamera: () -> Unit
) {
    LaunchedEffect(stationId) {
        viewModel.setStation(stationId)
    }

    val images by viewModel.imagesList.collectAsState()
    var selectedImageForDetail by remember { mutableStateOf<EquipmentImageEntity?>(null) }
    var imageToDelete by remember { mutableStateOf<EquipmentImageEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Bộ Sưu Tập Ảnh Thiết Bị", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(stationName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở lại")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToCamera) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = "Chụp ảnh mới", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToCamera,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Chụp ảnh thiết bị")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Thống kê số lượng ảnh
            Text(
                text = "Tổng số ảnh hiện trường: ${images.size} ảnh",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (images.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            "Chưa có ảnh nào của trạm này",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                        Button(onClick = onNavigateToCamera) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Chụp ảnh thiết bị đầu tiên")
                        }
                    }
                }
            } else {
                // Lưới hiển thị ảnh (LazyVerticalGrid)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(images, key = { it.id }) { img ->
                        GalleryThumbnailCard(
                            image = img,
                            onClick = { selectedImageForDetail = img },
                            onDelete = { imageToDelete = img }
                        )
                    }
                }
            }
        }
    }

    // ==================== DIALOG XEM ẢNH PHÓNG TO & METADATA ====================
    selectedImageForDetail?.let { img ->
        ImageDetailDialog(
            image = img,
            onDismiss = { selectedImageForDetail = null },
            onDelete = {
                imageToDelete = img
                selectedImageForDetail = null
            }
        )
    }

    // ==================== DIALOG XÁC NHẬN XÓA ẢNH ====================
    imageToDelete?.let { img ->
        AlertDialog(
            onDismissRequest = { imageToDelete = null },
            title = { Text("Xác nhận xóa ảnh", fontWeight = FontWeight.Bold) },
            text = { Text("Bạn có chắc chắn muốn xóa ảnh của thiết bị '${img.equipmentCode}' chụp lúc ${img.capturedAt}?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteImage(img)
                        imageToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Xóa")
                }
            },
            dismissButton = {
                TextButton(onClick = { imageToDelete = null }) {
                    Text("Hủy")
                }
            }
        )
    }
}

/**
 * Thumbnail Card trong lưới ảnh
 */
@Composable
fun GalleryThumbnailCard(
    image: EquipmentImageEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val file = remember(image.imagePath) { File(image.imagePath) }
    var bitmap by remember(image.imagePath) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(image.imagePath) {
        withContext(Dispatchers.IO) {
            if (file.exists()) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                val bmp = BitmapFactory.decodeFile(file.absolutePath, opts)
                withContext(Dispatchers.Main) {
                    bitmap = bmp
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(Color.DarkGray)
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = image.equipmentCode,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }

                // Badge mã thiết bị góc trên
                Surface(
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = image.equipmentCode,
                        color = Color.Yellow,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = image.equipmentName.ifBlank { image.equipmentCode },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = image.capturedAt,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Hộp thoại phóng to ảnh và hiển thị chi tiết Metadata kỹ thuật
 */
@Composable
fun ImageDetailDialog(
    image: EquipmentImageEntity,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val file = remember(image.imagePath) { File(image.imagePath) }
    var bitmap by remember(image.imagePath) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(image.imagePath) {
        withContext(Dispatchers.IO) {
            if (file.exists()) {
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                withContext(Dispatchers.Main) {
                    bitmap = bmp
                }
            }
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Vùng xem ảnh Pinch-to-zoom / Pan
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.5f, 6.0f)
                                offsetX += pan.x * scale
                                offsetY += pan.y * scale
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap!!.asImageBitmap(),
                            contentDescription = "Chi tiết ảnh",
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                ),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        CircularProgressIndicator(color = Color.White)
                    }
                }

                // Nút đóng ở góc trên trái
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .align(Alignment.TopStart)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Đóng", tint = Color.White)
                }

                // Nút xóa ảnh ở góc trên phải
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .align(Alignment.TopEnd)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Xóa", tint = Color.Red)
                }

                // Card hiển thị thông tin Metadata ở cạnh dưới
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${image.stationName} • ${image.equipmentCode}",
                            color = Color.Yellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Tên thiết bị: ${image.equipmentName.ifBlank { "Không có tên" }}",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Thời gian chụp: ${image.capturedAt}",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Người chụp: ${image.capturedBy}",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                        if (image.note.isNotBlank()) {
                            Text(
                                text = "Ghi chú: ${image.note}",
                                color = Color(0xFF81D4FA),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
