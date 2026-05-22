package com.example.data.repository

import android.util.Log
import com.example.data.local.PdfDao
import com.example.data.model.PdfDocument
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.regex.Pattern

class PdfRepository(private val pdfDao: PdfDao) {

    val allPdfs: Flow<List<PdfDocument>> = pdfDao.getAllPdfs()

    suspend fun fetchDocumentsFromApi(baseUrlString: String, pathString: String): Result<List<PdfDocument>> {
        return try {
            val client = OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val fullUrl = buildFullUrl(baseUrlString, pathString)
            Log.d("PdfRepository", "Fetching documents from: $fullUrl")

            val request = Request.Builder()
                .url(fullUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json,text/html,application/xhtml+xml,application/xml")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("HTTP error: ${response.code}"))
                }

                val bodyString = response.body?.string() ?: ""
                val fetchedDocs = parseResponse(bodyString, fullUrl)
                
                if (fetchedDocs.isNotEmpty()) {
                    pdfDao.refreshNetworkPdfs(fetchedDocs)
                    Result.success(fetchedDocs)
                } else {
                    Result.failure(Exception("No documents found at endpoint"))
                }
            }
        } catch (e: Exception) {
            Log.e("PdfRepository", "Error fetching documents", e)
            Result.failure(e)
        }
    }

    private fun buildFullUrl(base: String, path: String): String {
        val cleanBase = base.trim().removeSuffix("/")
        val cleanPath = path.trim().removePrefix("/")
        return if (cleanPath.isEmpty()) {
            cleanBase
        } else {
            if (cleanBase.startsWith("http://") || cleanBase.startsWith("https://")) {
                "$cleanBase/$cleanPath"
            } else {
                "https://$cleanBase/$cleanPath"
            }
        }
    }

    private fun parseResponse(body: String, requestUrl: String): List<PdfDocument> {
        val trimmedBody = body.trim()
        val docs = mutableListOf<PdfDocument>()

        if (trimmedBody.startsWith("[") || trimmedBody.startsWith("{")) {
            try {
                if (trimmedBody.startsWith("[")) {
                    val array = JSONArray(trimmedBody)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val doc = parseJsonPdfObject(obj, requestUrl)
                        if (doc != null) docs.add(doc)
                    }
                } else {
                    val obj = JSONObject(trimmedBody)
                    if (obj.has("data") && obj.get("data") is JSONArray) {
                        val array = obj.getJSONArray("data")
                        for (i in 0 until array.length()) {
                            val subObj = array.getJSONObject(i)
                            val doc = parseJsonPdfObject(subObj, requestUrl)
                            if (doc != null) docs.add(doc)
                        }
                    } else if (obj.has("files") && obj.get("files") is JSONArray) {
                        val array = obj.getJSONArray("files")
                        for (i in 0 until array.length()) {
                            val subObj = array.getJSONObject(i)
                            val doc = parseJsonPdfObject(subObj, requestUrl)
                            if (doc != null) docs.add(doc)
                        }
                    } else {
                        val doc = parseJsonPdfObject(obj, requestUrl)
                        if (doc != null) docs.add(doc)
                    }
                }
                
                if (docs.isNotEmpty()) {
                    return docs
                }
            } catch (jsone: Exception) {
                Log.d("PdfRepository", "Not a valid custom JSON schema: ${jsone.message}")
            }
        }

        try {
            val anchorPattern = Pattern.compile(
                "<a[^>]+href\\s*=\\s*[\"']([^\"']+\\.pdf[^\"']*)[\"'][^>]*>(.*?)</a>", 
                Pattern.CASE_INSENSITIVE or Pattern.DOTALL
            )
            val matcher = anchorPattern.matcher(body)
            while (matcher.find()) {
                val hrefValue = matcher.group(1) ?: ""
                val anchorTextClean = (matcher.group(2) ?: "")
                    .replace("<[^>]*>".toRegex(), "")
                    .trim()

                if (hrefValue.isNotEmpty()) {
                    val absoluteUrl = resolveRelativeUrl(hrefValue, requestUrl)
                    val title = if (anchorTextClean.isNotEmpty() && anchorTextClean.length < 100) {
                        anchorTextClean
                    } else {
                        extractTitleFromUrl(absoluteUrl)
                    }
                    val fileName = extractFileNameFromUrl(absoluteUrl)
                    
                    docs.add(
                        PdfDocument(
                            id = generateId(absoluteUrl),
                            name = fileName,
                            title = title,
                            url = absoluteUrl,
                            dateAdded = "Discovered Link",
                            fileSize = "Remote Doc"
                        )
                    )
                }
            }

            if (docs.size < 2) {
                val urlPattern = Pattern.compile("(https?://[^\\s\"'<>]+?\\.pdf)", Pattern.CASE_INSENSITIVE)
                val urlMatcher = urlPattern.matcher(body)
                while (urlMatcher.find()) {
                    val rawUrl = urlMatcher.group(1) ?: ""
                    if (rawUrl.isNotEmpty() && docs.none { it.url == rawUrl }) {
                        val title = extractTitleFromUrl(rawUrl)
                        val fileName = extractFileNameFromUrl(rawUrl)
                        docs.add(
                            PdfDocument(
                                id = generateId(rawUrl),
                                name = fileName,
                                title = title,
                                url = rawUrl,
                                dateAdded = "Raw PDF Link",
                                fileSize = "Direct File"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PdfRepository", "Error regex parsing HTML", e)
        }

        return docs.distinctBy { it.url }
    }

    private fun parseJsonPdfObject(obj: JSONObject, requestUrl: String): PdfDocument? {
        return try {
            val url = obj.optString("url", obj.optString("path", obj.optString("file", "")))
            if (url.isEmpty()) return null
            
            val absoluteUrl = resolveRelativeUrl(url, requestUrl)
            val name = obj.optString("name", obj.optString("filename", extractFileNameFromUrl(absoluteUrl)))
            val title = obj.optString("title", obj.optString("displayName", extractTitleFromUrl(absoluteUrl)))
            val date = obj.optString("dateAdded", obj.optString("createdAt", obj.optString("date", "Today")))
            val size = obj.optString("fileSize", obj.optString("size", "Unknown Size"))
            
            PdfDocument(
                id = obj.optString("id", generateId(absoluteUrl)),
                name = name,
                title = title,
                url = absoluteUrl,
                dateAdded = date,
                fileSize = size
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveRelativeUrl(url: String, requestUrl: String): String {
        if (url.startsWith("http://", true) || url.startsWith("https://", true)) {
            return url
        }

        val requestUri = java.net.URI(requestUrl)
        
        return if (url.startsWith("/")) {
            "${requestUri.scheme}://${requestUri.host}${url}"
        } else {
            val parentPath = requestUrl.substringBeforeLast("/")
            "$parentPath/$url"
        }
    }

    private fun extractFileNameFromUrl(url: String): String {
        return url.substringAfterLast("/").substringBefore("?").ifEmpty { "document.pdf" }
    }

    private fun extractTitleFromUrl(url: String): String {
        val rawName = extractFileNameFromUrl(url).substringBeforeLast(".pdf")
        return rawName.replace("[_-]".toRegex(), " ")
            .split(" ")
            .filter { it.isNotEmpty() }
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
            .ifEmpty { "Secure PDF" }
    }

    private fun generateId(url: String): String {
        return UUID.nameUUIDFromBytes(url.toByteArray()).toString()
    }
    
    suspend fun insertDemoPdfs(demos: List<PdfDocument>) {
        pdfDao.insertPdfs(demos)
    }
}
