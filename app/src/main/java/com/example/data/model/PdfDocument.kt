package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@Entity(tableName = "pdf_documents")
@JsonClass(generateAdapter = true)
data class PdfDocument(
    @PrimaryKey val id: String,
    val name: String,
    val title: String,
    val url: String,
    val dateAdded: String = "",
    val fileSize: String = "",
    val isDemo: Boolean = false
)
