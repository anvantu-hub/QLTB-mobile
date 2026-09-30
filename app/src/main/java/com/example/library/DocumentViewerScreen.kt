package com.example.library

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Màn hình xem tài liệu kỹ thuật (DocumentViewerScreen).
 * Hỗ trợ hiển thị tệp PDF nhiều trang và bộ xem ảnh bản vẽ sơ đồ 22kV có tính năng Pinch-to-zoom và Pan mượt mà.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    document: DocumentEntity,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isPdf = remember(document) { document.fileType.equals("PDF", ignoreCase = true) }

    // Trạng thái tệp cục bộ
    val localFile = remember(document.localPath) {
        document.localPath?.let { File(it) }?.takeIf { it.exists() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = document.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1
                        )
                        Text(
                            text = if (isPdf) "Tài liệu PDF" else "Sơ đồ bản vẽ 22kV",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở lại")
                    }
                },
                actions = {
                    // Mở bằng ứng dụng bên ngoài nếu có
                    if (localFile != null) {
                        IconButton(onClick = { openFileWithExternalApp(context, localFile, isPdf) }) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "Mở bằng ứng dụng ngoài")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF1E1E1E))
        ) {
            if (localFile == null) {
                // Tệp chưa được tải về máy
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.padding(24.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Tài liệu chưa được lưu trữ Offline",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                "Vui lòng bấm nút 'Tải về' ở danh sách để lưu tài liệu vào máy trước khi tra cứu tại hiện trường.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                if (isPdf) {
                    PdfViewComponent(file = localFile)
                } else {
                    ZoomableImageViewer(file = localFile)
                }
            }
        }
    }
}

/**
 * Thành phần hiển thị tệp PDF bằng Android PdfRenderer chuẩn
 */
@Composable
fun PdfViewComponent(file: File) {
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            try {
                val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val pdfRenderer = PdfRenderer(fileDescriptor)
                val pageCount = pdfRenderer.pageCount
                val pageBitmaps = mutableListOf<Bitmap>()

                for (i in 0 until pageCount) {
                    val page = pdfRenderer.openPage(i)
                    // Scale bitmap phù hợp màn hình thiết bị
                    val width = (page.width * 1.5).toInt()
                    val height = (page.height * 1.5).toInt()
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    pageBitmaps.add(bitmap)
                    page.close()
                }
                pdfRenderer.close()
                fileDescriptor.close()

                withContext(Dispatchers.Main) {
                    pages = pageBitmaps
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = "Không thể mở tệp PDF: ${e.localizedMessage}"
                    isLoading = false
                }
            }
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    } else if (errorMessage != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(errorMessage!!, color = Color.White, fontSize = 14.sp)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(pages) { index, bitmap ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Trang ${index + 1}",
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.FillWidth
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Trang ${index + 1} / ${pages.size}",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/**
 * Bộ xem ảnh sơ đồ 22kV hỗ trợ tính năng Pinch-to-zoom và Pan kéo thả
 */
@Composable
fun ZoomableImageViewer(file: File) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            val bmp = BitmapFactory.decodeFile(file.absolutePath)
            withContext(Dispatchers.Main) {
                bitmap = bmp
            }
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 6.0f)
                    offsetX += pan.x * scale
                    offsetY += pan.y * scale
                }
            }
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Sơ đồ bản vẽ 22kV",
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
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Thanh công cụ Zoom nhanh ở góc phải dưới
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = { scale = (scale + 0.5f).coerceAtMost(6.0f) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.ZoomIn, contentDescription = "Phóng to", tint = Color.White)
            }
            IconButton(
                onClick = {
                    scale = 1f
                    offsetX = 0f
                    offsetY = 0f
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = "Đặt lại", tint = Color.White)
            }
            IconButton(
                onClick = { scale = (scale - 0.5f).coerceAtLeast(0.5f) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.ZoomOut, contentDescription = "Thu nhỏ", tint = Color.White)
            }
        }
    }
}

/**
 * Hỗ trợ mở tệp bằng các ứng dụng đọc PDF hoặc Xem ảnh chuyên dụng đã cài trên máy
 */
fun openFileWithExternalApp(context: Context, file: File, isPdf: Boolean) {
    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mimeType = if (isPdf) "application/pdf" else "image/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Mở tài liệu bằng..."))
    } catch (e: Exception) {
        Toast.makeText(context, "Không tìm thấy ứng dụng phù hợp để mở tệp: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}
