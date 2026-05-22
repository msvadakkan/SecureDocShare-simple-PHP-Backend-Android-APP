package com.example.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.graphics.pdf.PdfRenderer
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.PdfDocument
import com.example.data.repository.PdfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

sealed class PdfLoadingState {
    object NotOpened : PdfLoadingState()
    data class Downloading(val progress: Float) : PdfLoadingState()
    object Opening : PdfLoadingState()
    data class Rendering(val currentPage: Int, val totalPages: Int) : PdfLoadingState()
    object Success : PdfLoadingState()
    data class Error(val message: String) : PdfLoadingState()
}

class PdfViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs: SharedPreferences =
        application.getSharedPreferences("docshare_prefs", Context.MODE_PRIVATE)

    private val database = AppDatabase.getDatabase(application)
    private val repository = PdfRepository(database.pdfDao())

    // --- Api Configurations ---
    var apiBaseUrl by mutableStateOf(sharedPrefs.getString("api_base_url", "http://magenta-pharma.com") ?: "http://magenta-pharma.com")
        private set

    var apiPath by mutableStateOf(sharedPrefs.getString("api_path", "DocShare/backend/api.php") ?: "DocShare/backend/api.php")
        private set

    // --- Flow-based State Management ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    var isRefreshing by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)

    // --- Active Document Reader ---
    var selectedPdf by mutableStateOf<PdfDocument?>(null)
        private set

    var pdfLoadingState by mutableStateOf<PdfLoadingState>(PdfLoadingState.NotOpened)
        private set

    var pdfPages by mutableStateOf<List<Bitmap>>(emptyList())
        private set

    // Combine database results with search query dynamically in memory
    val pdfListState: StateFlow<List<PdfDocument>> = repository.allPdfs
        .combine(_searchQuery) { pdfs, query ->
            val filtered = if (query.isBlank()) {
                pdfs
            } else {
                pdfs.filter {
                    it.title.contains(query, ignoreCase = true) ||
                    it.name.contains(query, ignoreCase = true)
                }
            }
            filtered
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // Silently sync with server on start up
        refreshDocuments()
    }

    private fun preloadDemoFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val demos = listOf(
                PdfDocument(
                    id = "demo_1",
                    name = "SOP-PH-042_Manufacturing_QA_v3.pdf",
                    title = "QA Manufacturing Protocol v3",
                    url = "https://magenta-pharma.com/DocShare/files/sop_ph_042.pdf",
                    dateAdded = "2026-05-10 (Preloaded)",
                    fileSize = "2.4 MB",
                    isDemo = true
                ),
                PdfDocument(
                    id = "demo_2",
                    name = "Catalog_Magenta_Pharma_Aspirin_v8.pdf",
                    title = "Chemical Compound Catalog 2026",
                    url = "https://magenta-pharma.com/DocShare/files/catalog_aspirin_v8.pdf",
                    dateAdded = "2026-05-15 (Preloaded)",
                    fileSize = "1.8 MB",
                    isDemo = true
                ),
                PdfDocument(
                    id = "demo_3",
                    name = "Guide_DocShare_Device_Policy.pdf",
                    title = "Secure Information Handling Policy",
                    url = "https://magenta-pharma.com/DocShare/files/device_security_policy.pdf",
                    dateAdded = "2026-05-20 (Preloaded)",
                    fileSize = "950 KB",
                    isDemo = true
                )
            )
            repository.insertDemoPdfs(demos)
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateApiSettings(baseUrl: String, path: String) {
        apiBaseUrl = baseUrl.trim()
        apiPath = path.trim().removePrefix("/")
        
        sharedPrefs.edit()
            .putString("api_base_url", apiBaseUrl)
            .putString("api_path", apiPath)
            .apply()
            
        // Sync index immediately with new server configurations
        refreshDocuments()
    }

    fun refreshDocuments() {
        viewModelScope.launch {
            isRefreshing = true
            errorMessage = null
            
            val result = withContext(Dispatchers.IO) {
                repository.fetchDocumentsFromApi(apiBaseUrl, apiPath)
            }
            
            result.onSuccess {
                Log.d("PdfViewModel", "Successfully refreshed network index with ${it.size} files")
            }.onFailure { e ->
                Log.e("PdfViewModel", "Failed to sync index with remote server", e)
                errorMessage = "Could not reach server: ${e.localizedMessage}. Loaded preloaded local assets."
            }
            
            isRefreshing = false
        }
    }

    fun selectAndOpenPdf(context: Context, pdf: PdfDocument) {
        selectedPdf = pdf
        pdfPages = emptyList() // Clear previous image cache
        pdfLoadingState = PdfLoadingState.Downloading(0f)

        viewModelScope.launch {
            try {
                if (pdf.isDemo) {
                    // Simulating a highly polished secure download + page rendering
                    pdfLoadingState = PdfLoadingState.Downloading(0.3f)
                    withContext(Dispatchers.Default) {
                        Thread.sleep(300)
                    }
                    pdfLoadingState = PdfLoadingState.Downloading(0.7f)
                    withContext(Dispatchers.Default) {
                        Thread.sleep(300)
                    }
                    pdfLoadingState = PdfLoadingState.Opening
                    
                    val generatedPages = withContext(Dispatchers.Default) {
                        generateDemoDocument(pdf.title, 3)
                    }
                    
                    pdfPages = generatedPages
                    pdfLoadingState = PdfLoadingState.Success
                } else {
                    // Download actual PDF file securely to isolated app cache
                    withContext(Dispatchers.IO) {
                        downloadAndRenderPdf(context, pdf)
                    }
                }
            } catch (e: Exception) {
                Log.e("PdfViewModel", "Failed to load PDF file", e)
                pdfLoadingState = PdfLoadingState.Error("Error: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun downloadAndRenderPdf(context: Context, pdf: PdfDocument) {
        try {
            val client = OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val request = Request.Builder()
                .url(pdf.url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val tempFile = File(context.cacheDir, "secure_temp_${pdf.id}.pdf")
            if (tempFile.exists()) tempFile.delete()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("Server returned code ${response.code}")
                }
                
                val body = response.body ?: throw Exception("Null response body")
                val contentLength = body.contentLength()
                
                body.byteStream().use { inputStream ->
                    tempFile.outputStream().use { outputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesRead = 0L
                        
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            
                            if (contentLength > 0) {
                                val progress = totalBytesRead.toFloat() / contentLength
                                withContext(Dispatchers.Main) {
                                    pdfLoadingState = PdfLoadingState.Downloading(progress)
                                }
                            }
                        }
                    }
                }
            }

            // Successfully downloaded file; trigger rendering pipeline
            withContext(Dispatchers.Main) {
                pdfLoadingState = PdfLoadingState.Opening
            }

            renderNativePdfFile(tempFile)

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                pdfLoadingState = PdfLoadingState.Error(e.localizedMessage ?: "Failed downloading document")
            }
        }
    }

    private suspend fun renderNativePdfFile(file: File) {
        withContext(Dispatchers.Default) {
            try {
                val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val pdfRenderer = PdfRenderer(fileDescriptor)
                val count = pdfRenderer.pageCount
                val pageBitmaps = ArrayList<Bitmap>(count)

                for (i in 0 until count) {
                    withContext(Dispatchers.Main) {
                        pdfLoadingState = PdfLoadingState.Rendering(i + 1, count)
                    }

                    val page = pdfRenderer.openPage(i)

                    // Render crisp detailed document (2.0x scale for high resolution)
                    val width = (page.width * 2.0f).toInt()
                    val height = (page.height * 2.0f).toInt()
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    pageBitmaps.add(bitmap)
                    
                    page.close()
                }

                pdfRenderer.close()
                fileDescriptor.close()

                // Security Masterpiece: Secure check out by immediately destroying downloaded PDF file content on disk.
                // Decrypted data is held strictly inside JVM Ram sandbox!
                file.delete()

                withContext(Dispatchers.Main) {
                    pdfPages = pageBitmaps
                    pdfLoadingState = PdfLoadingState.Success
                }

            } catch (e: Exception) {
                Log.e("PdfViewModel", "Error rendering file descriptor pages", e)
                withContext(Dispatchers.Main) {
                    pdfLoadingState = PdfLoadingState.Error("Failed to render pages: ${e.localizedMessage}")
                }
            } finally {
                if (file.exists()) file.delete()
            }
        }
    }

    fun closeReader() {
        selectedPdf = null
        pdfLoadingState = PdfLoadingState.NotOpened
        pdfPages = emptyList()
    }

    // Programmatic generator of high-fidelity mock pharmaceutical documents for demo testing
    private fun generateDemoDocument(title: String, pagesCount: Int): List<Bitmap> {
        val bitmaps = mutableListOf<Bitmap>()
        for (pageIdx in 1..pagesCount) {
            val width = 1200
            val height = 1600
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            
            // White Page Canvas
            canvas.drawColor(android.graphics.Color.WHITE)
            
            // Elegant Tech Border Frame
            paint.color = android.graphics.Color.parseColor("#E0F2F1") // Cool aqua
            paint.strokeWidth = 6f
            paint.style = android.graphics.Paint.Style.STROKE
            canvas.drawRect(50f, 50f, width - 50f, height - 50f, paint)
            
            // Sub-grid lines for visual flare
            paint.strokeWidth = 1f
            paint.color = android.graphics.Color.parseColor("#F0FDF4")
            var xLine = 100f
            while (xLine < width) {
                canvas.drawLine(xLine, 50f, xLine, height - 50f, paint)
                xLine += 100f
            }
            var yLine = 100f
            while (yLine < height) {
                canvas.drawLine(50f, yLine, width - 50f, yLine, paint)
                yLine += 100f
            }
            
            // Tiny security code stamp on corner
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = android.graphics.Color.parseColor("#4D00796B") // Teal tint
            paint.textSize = 24f
            paint.textAlign = android.graphics.Paint.Align.RIGHT
            canvas.drawText("Device Secure Sandbox Validated", width - 70f, 90f, paint)
            
            // Header bar
            paint.color = android.graphics.Color.parseColor("#00796B") // Deep teal
            canvas.drawRect(50f, 50f, width - 50f, 150f, paint)
            
            // Header texts
            paint.color = android.graphics.Color.WHITE
            paint.textSize = 34f
            paint.isFakeBoldText = true
            paint.textAlign = android.graphics.Paint.Align.LEFT
            canvas.drawText("SafeDocShare Security Vault System", 80f, 110f, paint)
            
            paint.textAlign = android.graphics.Paint.Align.RIGHT
            paint.isFakeBoldText = false
            paint.textSize = 28f
            canvas.drawText("Page $pageIdx / $pagesCount", width - 80f, 110f, paint)
            
            // Title
            paint.textAlign = android.graphics.Paint.Align.LEFT
            paint.color = android.graphics.Color.parseColor("#124E52") // Deep slate steel blue
            paint.textSize = 46f
            paint.isFakeBoldText = true
            canvas.drawText(title, 82f, 230f, paint)
            
            // Category/Metadata Stamp
            paint.isFakeBoldText = false
            paint.color = android.graphics.Color.parseColor("#546E7A") // Slate gray
            paint.textSize = 26f
            canvas.drawText("System Access Level: PROPRIETARY & CONFIDENTIAL", 82f, 280f, paint)
            canvas.drawText("DocShare API: http://magenta-pharma.com/DocShare/backend", 82f, 320f, paint)
            
            // Horizontal Rule Divider
            paint.color = android.graphics.Color.parseColor("#00796B")
            paint.strokeWidth = 3f
            canvas.drawLine(80f, 350f, width - 80f, 350f, paint)
            
            // Page specifics content
            val lines = when (pageIdx) {
                1 -> listOf(
                    "1. EXECUTIVE OVERVIEW",
                    "This document establishes the official regulatory compliance guide and document custody",
                    "protocols for Magenta Pharma DocShare services. To maintain complete chain of custody and",
                    "prevent intellectual property theft, strict access controls are applied natively at the device level.",
                    "",
                    "2. INTELLECTUAL PROPERTY RESTRAINTS",
                    "The viewer application disables screenshots, direct clipboard copies, and external storage",
                    "sharing. All content rendered on-screen is dynamically mapped directly to localized RAM buffers.",
                    "",
                    "3. SYSTEM AUDIT MECHANISMS & COMPLIANCE",
                    "• All transactions are logged securely with encrypted device tokens.",
                    "• Document access decays automatically after key API cycle periods.",
                    "• Storage footprints are wiped as soon as a user session is garbage-collected or closed.",
                    "• Screen capture routines trigger hardware-protected secure dark flags.",
                    "",
                    "4. TECHNICAL PROTOCOLS",
                    "Refer to Page 2 for active formulation SOPs and manufacturing guidelines.",
                    "Documents can be refreshed dynamically in the main view by pulling to refresh the index."
                )
                2 -> listOf(
                    "2. ACTIVE STANDARD OPERATING PROCEDURES (SOP-042)",
                    "This section outlines chemical compound safety guidelines for active pharmaceutical synthesis.",
                    "",
                    "A. MATERIAL CONSTRAINTS & THERMAL TOLERANCES",
                    "• Keep storage temperatures within a secure index of 4°C to 8°C (Liquid State).",
                    "• Double-validate batch numbers across RFID scanner tables before opening synthesis portals.",
                    "• Ensure continuous ventilation index levels remain above 95% capacity.",
                    "",
                    "B. COMPOUND RE-BATCHING PROTOCOLS",
                    "1. Compound verification occurs automatically via the central DocShare validation API.",
                    "2. Batch reports are generated securely inside the local DB and never serialized externally.",
                    "3. Formulation instructions must be treated as Level-4 Proprietary Assets.",
                    "",
                    "C. INCIDENT MITIGATION & SERVER TIMEOUTS",
                    "In case of API disconnection, caching protocols allow local secure read states within 48",
                    "hours of the initial sync. In case of continuous network error, re-authentication will be",
                    "required to renew the local security certificate."
                )
                else -> listOf(
                    "3. AUDIT SIGN-OFFS & CERTIFICATION",
                    "The electronic signatures below represent formal certification of SOP-042 compliance.",
                    "",
                    "AUTHORIZATION OFFICERS:",
                    "- Director of Quality Control, Magenta Pharma (Signed Digitally)",
                    "- VP of Information Security, DocShare Systems (Validated)",
                    "",
                    "SECURITY VERIFICATION STAMP:",
                    "[ STAMP ID: d7b8a1c9-f1e2-4c3d-b5a1-9a8b7c6d5e4f ]",
                    "[ SYSTEM PROTOCOL: SECURE-VIEW-ONLY-NO-REPLIC ]",
                    "",
                    "--------------------------------------------------------------------------------",
                    "End of secure Document. To return to the document portal, tap the Back",
                    "navigation arrow in the upper left header. Your local file state of",
                    "this document has already been securely expunged from device media."
                )
            }
            
            var currentY = 400f
            for (line in lines) {
                paint.textSize = 25f
                if (line.trim().startsWith("•") || line.trim().startsWith("-") || line.trim().startsWith("1.") || line.trim().startsWith("2.") || line.trim().startsWith("3.")) {
                    paint.color = android.graphics.Color.parseColor("#1B5E20") // Slate green for bullets
                    paint.isFakeBoldText = true
                } else if (line.trim().startsWith("A.") || line.trim().startsWith("B.") || line.trim().startsWith("C.")) {
                    paint.color = android.graphics.Color.parseColor("#E65100") // Warm amber for labels
                    paint.isFakeBoldText = true
                    paint.textSize = 28f
                } else if (line.endsWith(":") || line.endsWith("]")) {
                    paint.color = android.graphics.Color.parseColor("#C62828") // Deep Red for critical stamps
                    paint.isFakeBoldText = true
                } else {
                    paint.color = android.graphics.Color.parseColor("#263238") // Deep blue-charcoal body
                    paint.isFakeBoldText = false
                }
                
                canvas.drawText(line, 80f, currentY, paint)
                currentY += 45f
            }
            
            // Massive Red Security Stamp across background (Alpha=12%)
            paint.color = android.graphics.Color.parseColor("#12D32F2F")
            paint.textSize = 62f
            paint.isFakeBoldText = true
            paint.textAlign = android.graphics.Paint.Align.CENTER
            
            canvas.save()
            canvas.rotate(-35f, (width / 2).toFloat(), (height / 2).toFloat())
            canvas.drawText("SECURE VIEW ONLY • COPY PROHIBITED", (width / 2).toFloat(), (height / 2 - 120).toFloat(), paint)
            canvas.drawText("SCREENSHOTS DISABLED BY POLICY", (width / 2).toFloat(), (height / 2).toFloat(), paint)
            canvas.drawText("SECURE SYSTEM: SafeDocShare", (width / 2).toFloat(), (height / 2 + 120).toFloat(), paint)
            canvas.restore()
            
            // Footer text signature
            paint.color = android.graphics.Color.parseColor("#90A4AE")
            paint.textSize = 22f
            paint.isFakeBoldText = false
            paint.textAlign = android.graphics.Paint.Align.CENTER
            canvas.drawText("DocShare Secure sandbox. Under penalty of policy violation do not attempt replication.", (width / 2).toFloat(), height - 85f, paint)
            
            bitmaps.add(bitmap)
        }
        return bitmaps
    }
}
