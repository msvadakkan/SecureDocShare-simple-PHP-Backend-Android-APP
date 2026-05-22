package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.model.PdfDocument
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDao {
    @Query("SELECT * FROM pdf_documents WHERE isDemo = 0 ORDER BY title ASC")
    fun getAllPdfs(): Flow<List<PdfDocument>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPdfs(pdfs: List<PdfDocument>)

    @Query("DELETE FROM pdf_documents WHERE isDemo = 0")
    suspend fun clearNetworkPdfs()

    @Transaction
    suspend fun refreshNetworkPdfs(pdfs: List<PdfDocument>) {
        clearNetworkPdfs()
        insertPdfs(pdfs)
    }
}
