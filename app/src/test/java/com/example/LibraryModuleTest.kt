package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.library.DocumentDao
import com.example.library.DocumentEntity
import com.example.library.DocumentRepository
import com.example.library.StorageStats
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LibraryModuleTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var documentDao: DocumentDao
    private lateinit var repository: DocumentRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        documentDao = db.documentDao()
        repository = DocumentRepository(documentDao, context, apiService = null)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testDocumentDao_CRUDAndFiltering() = runBlocking {
        val doc1 = DocumentEntity(
            id = "DOC-01",
            title = "Quy trình thao tác Recloser 22kV Cooper",
            category = "Quy trình vận hành",
            fileUrl = "https://grid.vn/docs/rec_cooper.pdf",
            fileType = "PDF",
            fileSize = 2048000L,
            isDownloaded = false,
            isFavorite = false,
            updatedAt = "2026-09-01"
        )
        val doc2 = DocumentEntity(
            id = "DOC-02",
            title = "Sơ đồ nguyên lý Trạm 110kV Hòa Thọ và Xuất tuyến 22kV",
            category = "Sơ đồ lưới điện",
            fileUrl = "https://grid.vn/docs/so_do_hoa_tho.jpg",
            fileType = "IMAGE",
            fileSize = 5242880L,
            isDownloaded = true,
            localPath = "/fake/path/so_do.jpg",
            isFavorite = true,
            updatedAt = "2026-09-10"
        )

        documentDao.insertOrUpdateAll(listOf(doc1, doc2))

        // 1. Get all documents
        val all = documentDao.getAllDocuments().first()
        assertEquals(2, all.size)

        // 2. Search by keyword
        val searchRec = documentDao.searchAndFilterDocuments(
            query = "Recloser",
            category = "Tất cả",
            fileType = "ALL",
            onlyDownloaded = false,
            onlyFavorite = false
        ).first()
        assertEquals(1, searchRec.size)
        assertEquals("DOC-01", searchRec[0].id)

        // 3. Filter by category
        val filterCat = documentDao.searchAndFilterDocuments(
            query = "",
            category = "Sơ đồ lưới điện",
            fileType = "ALL",
            onlyDownloaded = false,
            onlyFavorite = false
        ).first()
        assertEquals(1, filterCat.size)
        assertEquals("DOC-02", filterCat[0].id)

        // 4. Filter only downloaded
        val downloaded = documentDao.getDownloadedDocuments().first()
        assertEquals(1, downloaded.size)
        assertEquals("DOC-02", downloaded[0].id)

        // 5. Update favorite
        documentDao.updateFavorite("DOC-01", true)
        val updatedDoc1 = documentDao.getDocumentById("DOC-01")
        assertNotNull(updatedDoc1)
        assertTrue(updatedDoc1!!.isFavorite)

        // 6. Update download status
        documentDao.updateDownloadStatus("DOC-01", true, "/data/rec.pdf", 2048000L)
        val downloadedAfter = documentDao.getDownloadedDocuments().first()
        assertEquals(2, downloadedAfter.size)

        // 7. Clear local file
        documentDao.clearLocalFile("DOC-01")
        val clearedDoc1 = documentDao.getDocumentById("DOC-01")
        assertNotNull(clearedDoc1)
        assertFalse(clearedDoc1!!.isDownloaded)
        assertNull(clearedDoc1.localPath)
    }

    @Test
    fun testDocumentRepository_OfflineSingleSourceOfTruth() = runBlocking {
        val doc = DocumentEntity(
            id = "DOC-OFFLINE",
            title = "Hướng dẫn chỉnh định rơ le SEL-351",
            category = "Tài liệu kỹ thuật",
            fileUrl = "https://grid.vn/docs/sel351.pdf",
            fileType = "PDF"
        )
        repository.insertDocument(doc)

        // Check offline retrieval
        val retrieved = repository.getDocumentById("DOC-OFFLINE")
        assertNotNull(retrieved)
        assertEquals("DOC-OFFLINE", retrieved!!.id)

        // Toggle favorite
        repository.toggleFavorite("DOC-OFFLINE", false)
        val favDoc = repository.getDocumentById("DOC-OFFLINE")
        assertTrue(favDoc!!.isFavorite)

        // Sync without API service falls back gracefully
        val syncResult = repository.syncDocumentsFromRemote()
        assertTrue(syncResult.isSuccess)
    }

    @Test
    fun testStorageStats_FormatCalculation() {
        val stats1 = StorageStats(downloadedCount = 2, totalSizeBytes = 1536 * 1024L) // 1.5 MB
        assertEquals("1.5 MB", stats1.formattedSize)

        val stats2 = StorageStats(downloadedCount = 1, totalSizeBytes = 512 * 1024L) // 512 KB
        assertEquals("512.0 KB", stats2.formattedSize)

        val statsZero = StorageStats(downloadedCount = 0, totalSizeBytes = 0L)
        assertEquals("0 KB", statsZero.formattedSize)
    }
}
