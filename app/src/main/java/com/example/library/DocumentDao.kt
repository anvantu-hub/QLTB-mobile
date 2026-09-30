package com.example.library

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) cho tài liệu thư viện.
 * Hỗ trợ các thao tác CRUD, tìm kiếm, lọc danh mục và quản lý tệp Offline.
 */
@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents ORDER BY isFavorite DESC, updatedAt DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: String): DocumentEntity?

    @Query("""
        SELECT * FROM documents 
        WHERE (:query = '' OR title LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%')
          AND (:category = 'Tất cả' OR category = :category)
          AND (:fileType = 'ALL' OR fileType = :fileType)
          AND (:onlyDownloaded = 0 OR isDownloaded = 1)
          AND (:onlyFavorite = 0 OR isFavorite = 1)
        ORDER BY isFavorite DESC, updatedAt DESC
    """)
    fun searchAndFilterDocuments(
        query: String,
        category: String,
        fileType: String,
        onlyDownloaded: Boolean,
        onlyFavorite: Boolean
    ): Flow<List<DocumentEntity>>

    @Query("SELECT DISTINCT category FROM documents ORDER BY category ASC")
    fun getAllCategories(): Flow<List<String>>

    @Query("SELECT * FROM documents WHERE isDownloaded = 1 ORDER BY fileSize DESC")
    fun getDownloadedDocuments(): Flow<List<DocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(document: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(documents: List<DocumentEntity>)

    @Update
    suspend fun update(document: DocumentEntity)

    @Delete
    suspend fun delete(document: DocumentEntity)

    @Query("UPDATE documents SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: String, isFavorite: Boolean)

    @Query("UPDATE documents SET isDownloaded = :isDownloaded, localPath = :localPath, fileSize = :fileSize WHERE id = :id")
    suspend fun updateDownloadStatus(id: String, isDownloaded: Boolean, localPath: String?, fileSize: Long)

    @Query("UPDATE documents SET isDownloaded = 0, localPath = NULL WHERE id = :id")
    suspend fun clearLocalFile(id: String)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteById(id: String)
}
