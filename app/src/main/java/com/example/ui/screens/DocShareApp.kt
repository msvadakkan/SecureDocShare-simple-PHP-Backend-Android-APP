package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.layout.ContentScale
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.PdfDocument
import com.example.ui.PdfLoadingState
import com.example.ui.PdfViewModel

// Secure Color Palette (Cyber Security Slate & Glowing Teal Theme)
private val DarkBackground = Color(0xFF0D111A)
private val DeepSurface = Color(0xFF161C2C)
private val AccentTeal = Color(0xFF10B981) // Secure pulsing emerald/teal
private val GlowCyan = Color(0xFF06B6D4) // Tech cyan accent
private val BorderSlate = Color(0xFF2E3D52)
private val TextWhite = Color(0xFFF9FAFB)
private val TextMuted = Color(0xFF9CA3AF)
private val WarningCrimson = Color(0xFFEF4444)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocShareApp(viewModel: PdfViewModel = viewModel()) {
    val context = LocalContext.current
    val pdfList by viewModel.pdfListState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    // Ambient background brush
    val backgroundBrush = remember {
        Brush.verticalGradient(
            colors = listOf(DarkBackground, Color(0xFF080B12))
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Scaffold(
            topBar = {
                MainAppBar(
                    searchQuery = searchQuery,
                    onSearchChange = { viewModel.updateSearchQuery(it) },
                    onOpenSettings = { showSettingsDialog = true },
                    onRefresh = { viewModel.refreshDocuments() },
                    isRefreshing = viewModel.isRefreshing
                )
            },
            floatingActionButton = {
                if (viewModel.selectedPdf == null) {
                    FloatingActionButton(
                        onClick = { viewModel.refreshDocuments() },
                        containerColor = AccentTeal,
                        contentColor = Color.Black,
                        shape = CircleShape,
                        modifier = Modifier.testTag("refresh_docs_fab")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync from server",
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Documents Directory Layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    // Security Enforced Badge
                    SecurityEnforcedBadge()
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    if (viewModel.errorMessage != null) {
                        ErrorStateNotice(
                            message = viewModel.errorMessage ?: "",
                            onClearNotice = { viewModel.errorMessage = null }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    if (pdfList.isEmpty()) {
                        EmptyFilesPlaceholder(searchQuery = searchQuery)
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 80.dp),
                            modifier = Modifier.fillMaxSize().testTag("pdf_list")
                        ) {
                            items(pdfList, key = { it.id }) { pdf ->
                                PdfDocumentCard(
                                    pdf = pdf,
                                    onViewClick = {
                                        viewModel.selectAndOpenPdf(context, pdf)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Dynamic PDF Secure Viewer Layer (Fullscreen Immersive Overlay)
        AnimatedVisibility(
            visible = viewModel.selectedPdf != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            viewModel.selectedPdf?.let { pdf ->
                SecurePdfViewerScreen(
                    pdf = pdf,
                    loadingState = viewModel.pdfLoadingState,
                    pages = viewModel.pdfPages,
                    onClose = { viewModel.closeReader() }
                )
            }
        }

        // Settings / Server Endpoint Configuration
        if (showSettingsDialog) {
            SettingsConfigDialog(
                currentBaseUrl = viewModel.apiBaseUrl,
                currentPath = viewModel.apiPath,
                onSave = { base, path ->
                    viewModel.updateApiSettings(base, path)
                    showSettingsDialog = false
                },
                onDismiss = { showSettingsDialog = false }
            )
        }
    }
}

@Composable
fun MainAppBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Surface(
        color = DeepSurface,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header Title Line
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Lock Secure Secure Logo",
                    tint = AccentTeal,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "DocShare Vault",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    modifier = Modifier.testTag("appbar_sync_button")
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            color = AccentTeal,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync",
                            tint = GlowCyan
                        )
                    }
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("appbar_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Server Settings Configuration",
                        tint = TextWhite
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Option Container
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_input_field"),
                placeholder = {
                    Text(
                        "Search secure document name...",
                        color = TextMuted,
                        fontSize = 15.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Icon",
                        tint = AccentTeal,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear text search",
                                tint = TextMuted
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    focusedContainerColor = DarkBackground,
                    unfocusedContainerColor = DarkBackground,
                    focusedBorderColor = AccentTeal,
                    unfocusedBorderColor = BorderSlate,
                    cursorColor = AccentTeal
                )
            )
        }
    }
}

@Composable
fun SecurityEnforcedBadge() {
    Surface(
        color = AccentTeal.copy(alpha = 0.08f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, AccentTeal.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(AccentTeal) // Green Pulsing node
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "SANDBOX ENFORCED • SCREEN CAPTURE & COPYING DISABLED BY WINDOW RULE",
                color = AccentTeal,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ErrorStateNotice(message: String, onClearNotice: () -> Unit) {
    Surface(
        color = WarningCrimson.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, WarningCrimson.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Alert logo",
                tint = WarningCrimson,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                color = WarningCrimson,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onClearNotice,
                modifier = Modifier.size(18.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Hide Error Notice",
                    tint = WarningCrimson,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun PdfDocumentCard(
    pdf: PdfDocument,
    onViewClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DeepSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSlate.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .testTag("document_card_${pdf.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Stack / Secure indicators
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1F2E3E)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Confidential secured badge",
                    tint = GlowCyan,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Body Metadata
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = pdf.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    text = pdf.name,
                    fontSize = 13.sp,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Network File",
                        color = GlowCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(
                                GlowCyan.copy(alpha = 0.08f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = pdf.fileSize,
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Action View Only Safe CTA Button
            Button(
                onClick = onViewClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentTeal,
                    contentColor = Color.Black
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("view_button_${pdf.id}")
            ) {
                Text(
                    text = "VIEW",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun EmptyFilesPlaceholder(searchQuery: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.7f),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "Empty Directory list logo",
            tint = BorderSlate,
            modifier = Modifier.size(72.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = if (searchQuery.isNotEmpty()) "No Matching Files" else "Secure Vault Index Blank",
            fontSize = 18.sp,
            color = TextWhite,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(6.dp))
        
        Text(
            text = if (searchQuery.isNotEmpty()) {
                "Try searching for another pharmaceutical file title or clear filters."
            } else {
                "Could not sync with Magenta Pharma DocShare server panel. Change server URL configuration or refresh."
            },
            fontSize = 14.sp,
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

// SECURE DEEP READER SCREEN (Screenshots Blocked by Activity Window settings)
@Composable
fun SecurePdfViewerScreen(
    pdf: PdfDocument,
    loadingState: PdfLoadingState,
    pages: List<Bitmap>,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        if (window != null) {
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            val window = (context as? android.app.Activity)?.window
            if (window != null) {
                val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Surface(
        color = DarkBackground,
        modifier = Modifier
            .fillMaxSize()
            .testTag("secure_pdf_viewer")
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Reader Control Panel header
            Surface(
                color = DeepSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("close_viewer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Close Document secure reader",
                            tint = TextWhite
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = pdf.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Security sign",
                                tint = AccentTeal,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "SCREENSHOT DISABLED • COPYING DISABLED",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = AccentTeal,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Red Flag Label indicator
                    Surface(
                        color = WarningCrimson.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, WarningCrimson.copy(alpha = 0.4f)),
                        modifier = Modifier.padding(horizontal = 6.dp)
                    ) {
                        Text(
                            text = "RAM SHIELDED",
                            fontSize = 10.sp,
                            color = WarningCrimson,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // PDF Display Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF070A10)),
                contentAlignment = Alignment.Center
            ) {
                when (loadingState) {
                    is PdfLoadingState.Downloading -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                progress = loadingState.progress,
                                color = AccentTeal,
                                strokeWidth = 5.dp,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                text = "Securing download buffer... ${(loadingState.progress * 100).toInt()}%",
                                color = TextWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = loadingState.progress,
                                color = GlowCyan,
                                trackColor = BorderSlate,
                                modifier = Modifier
                                    .width(200.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                            )
                        }
                    }
                    is PdfLoadingState.Opening -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = GlowCyan)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Decrypting RAM clusters...", color = TextMuted, fontSize = 13.sp)
                        }
                    }
                    is PdfLoadingState.Rendering -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = AccentTeal)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Rendering page ${loadingState.currentPage} of ${loadingState.totalPages}...",
                                color = TextWhite,
                                fontSize = 14.sp
                            )
                        }
                    }
                    is PdfLoadingState.Success -> {
                        if (pages.isEmpty()) {
                            Text("No pages found inside file", color = WarningCrimson)
                        } else {
                            // Pinch To Zoom and Double tap Zoom Multi-touch container
                            ZoomableBox(modifier = Modifier.fillMaxSize()) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth().testTag("pdf_pages_list"),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    itemsIndexed(pages) { index, bitmap ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .border(2.dp, BorderSlate, RoundedCornerShape(12.dp))
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color.White)
                                        ) {
                                            Image(
                                                bitmap = bitmap.asImageBitmap(),
                                                contentDescription = "Page ${index + 1} of PDF",
                                                modifier = Modifier.fillMaxWidth(),
                                                contentScale = ContentScale.FillWidth
                                            )
                                            
                                            // Bottom secure footer indicators for user awareness
                                            Surface(
                                                color = Color(0xFFF1F5F9),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(10.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "SECURE PORTAL PAGE ${index + 1} OF ${pages.size}",
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF334155),
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                    Text(
                                                        text = "ENCRYPTED BUFFER",
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF64748B),
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    is PdfLoadingState.Error -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error icon Logo",
                                tint = WarningCrimson,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Secure Reader Fault",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = loadingState.message,
                                color = TextMuted,
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = onClose,
                                colors = ButtonDefaults.buttonColors(containerColor = BorderSlate)
                            ) {
                                Text("Return to Directory")
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

// Pinch gesture zoomable layout Box wrapper
@Composable
fun ZoomableBox(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        offset += offsetChange * scale
    }
    
    Box(
        modifier = modifier
            .clip(RectangleShape)
            .transformable(state = state)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2.2f
                        offset = Offset.Zero
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        ) {
            content()
        }
        
        // Custom Scale indicator overlay HUD
        if (scale > 1f) {
            Surface(
                color = AccentTeal.copy(alpha = 0.9f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
            ) {
                Text(
                    text = "${(scale * 100).toInt()}% ZOOMED • Double tap to reset",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

// Dialog for Server and API configuration overrides
@Composable
fun SettingsConfigDialog(
    currentBaseUrl: String,
    currentPath: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var baseInput by remember { mutableStateOf(currentBaseUrl) }
    var pathInput by remember { mutableStateOf(currentPath) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .border(1.dp, BorderSlate, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            color = DeepSurface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Gear Options icon logo",
                        tint = AccentTeal,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Server Configuration",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                
                Text(
                    text = "Configure DocShare server cluster or API addresses below. The app parses files list from HTML anchors or custom JSON structures automatically.",
                    fontSize = 13.sp,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Base Url Text input
                Text("API Base Domain", color = AccentTeal, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = baseInput,
                    onValueChange = { baseInput = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("api_base_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = AccentTeal,
                        unfocusedBorderColor = BorderSlate,
                        focusedContainerColor = DarkBackground,
                        unfocusedContainerColor = DarkBackground
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Sub-path Input
                Text("API Path Endpoint", color = AccentTeal, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = pathInput,
                    onValueChange = { pathInput = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("api_path_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = AccentTeal,
                        unfocusedBorderColor = BorderSlate,
                        focusedContainerColor = DarkBackground,
                        unfocusedContainerColor = DarkBackground
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkBackground),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Primary Target URL:",
                            fontWeight = FontWeight.Bold,
                            color = GlowCyan,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (baseInput.startsWith("http")) {
                                "${baseInput.trim().removeSuffix("/")}/${pathInput.trim().removePrefix("/")}"
                            } else {
                                "https://${baseInput.trim().removeSuffix("/")}/${pathInput.trim().removePrefix("/")}"
                            },
                            color = TextWhite,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // CTAs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCEL", color = TextMuted)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { onSave(baseInput, pathInput) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentTeal,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("save_config_button")
                    ) {
                        Text("SAVE & INDEX", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


