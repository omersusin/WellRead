package com.omersusin.wellread.ui.screens.library

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.omersusin.wellread.domain.model.Book
import com.omersusin.wellread.domain.model.BookType
import com.omersusin.wellread.domain.model.ReadingMode
import com.omersusin.wellread.ui.theme.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReader: (Long, String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {}
            val mimeType = context.contentResolver.getType(it)
            viewModel.importFile(it, mimeType)
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        "Library",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                navigationIcon = {
                    FilledTonalIconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.showAddDialog() },
                icon   = { Icon(Icons.Default.Add, null) },
                text   = { Text("Add Content", fontWeight = FontWeight.SemiBold) },
                shape  = RoundedCornerShape(20.dp)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search books...") },
                leadingIcon  = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQuery("") }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                },
                shape      = RoundedCornerShape(20.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            )

            LazyRow(
                contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(BookFilter.values()) { filter ->
                    FilterChip(
                        selected = uiState.selectedFilter == filter,
                        onClick  = { viewModel.onFilterSelected(filter) },
                        label    = { Text(filter.label(), fontWeight = FontWeight.Medium) },
                        shape    = RoundedCornerShape(14.dp)
                    )
                }
            }

            AnimatedVisibility(visible = uiState.isImporting) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if (uiState.filteredBooks.isEmpty()) {
                EmptyState(modifier = Modifier.weight(1f), onAddBook = { viewModel.showAddDialog() })
            } else {
                LazyVerticalGrid(
                    columns               = GridCells.Fixed(2),
                    contentPadding        = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement   = Arrangement.spacedBy(12.dp),
                    modifier              = Modifier.weight(1f)
                ) {
                    items(uiState.filteredBooks, key = { it.id }) { book ->
                        LibraryBookCard(
                            book          = book,
                            onBookClick   = { onNavigateToReader(book.id, ReadingMode.BIONIC.name) },
                            onDeleteClick = { viewModel.deleteBook(book) }
                        )
                    }
                }
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (uiState.showAddDialog) {
        AddBookDialog(
            onDismiss       = viewModel::hideAddDialog,
            onAddFile       = {
                viewModel.hideAddDialog()
                fileLauncher.launch(
                    arrayOf(
                        "application/pdf",
                        "application/epub+zip",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/msword", "application/rtf", "text/rtf",
                        "text/plain", "text/html", "text/markdown", "*/*"
                    )
                )
            },
            onAddUrl       = viewModel::showUrlDialog,
            onAddClipboard = viewModel::showClipboardDialog
        )
    }

    if (uiState.showUrlDialog) {
        UrlImportDialog(onDismiss = viewModel::hideUrlDialog, onImport = viewModel::importFromUrl)
    }

    if (uiState.showClipboardDialog) {
        val clipManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipText = remember {
            clipManager.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        }
        ClipboardImportDialog(
            clipboardPreview = clipText,
            onDismiss = viewModel::hideClipboardDialog,
            onImport  = { title -> viewModel.importFromClipboard(clipText, title) }
        )
    }

    uiState.importError?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            icon    = { Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title   = { Text("Import Failed", fontWeight = FontWeight.Bold) },
            text    = { Text(error) },
            confirmButton = {
                FilledTonalButton(onClick = viewModel::clearError) { Text("OK") }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }
}

// ── Book card ─────────────────────────────────────────────────────────────────

@Composable
private fun LibraryBookCard(book: Book, onBookClick: () -> Unit, onDeleteClick: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    val progress = if (book.totalWords > 0) book.currentPosition.toFloat() / book.totalWords else 0f
    val typeColor = book.typeColor()

    Card(
        modifier  = Modifier.fillMaxWidth().clickable(onClick = onBookClick),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = typeColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = book.typeLabel(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = typeColor
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.MoreVert, null, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = { showMenu = false; onDeleteClick() },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = typeColor.copy(alpha = 0.12f),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(book.typeIcon(), null, modifier = Modifier.size(24.dp), tint = typeColor)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            if (book.author != "Unknown") {
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress   = { progress },
                modifier   = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                color      = typeColor,
                trackColor = typeColor.copy(alpha = 0.15f)
            )
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${(progress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${book.totalWords / 1000}K words",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Dialogs ───────────────────────────────────────────────────────────────────

@Composable
private fun AddBookDialog(
    onDismiss: () -> Unit,
    onAddFile: () -> Unit,
    onAddUrl: () -> Unit,
    onAddClipboard: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Content", fontWeight = FontWeight.ExtraBold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AddOptionButton(
                    icon     = Icons.Outlined.UploadFile,
                    title    = "From File",
                    subtitle = "PDF, EPUB, DOCX, TXT, HTML, Markdown, RTF",
                    color    = BionicColor,
                    onClick  = onAddFile
                )
                AddOptionButton(
                    icon     = Icons.Outlined.Language,
                    title    = "From Web URL",
                    subtitle = "Paste any article or Wikipedia URL",
                    color    = FlashColor,
                    onClick  = onAddUrl
                )
                AddOptionButton(
                    icon     = Icons.Outlined.ContentPaste,
                    title    = "From Clipboard",
                    subtitle = "Paste long text copied from anywhere",
                    color    = ChunkColor,
                    onClick  = onAddClipboard
                )
            }
        },
        confirmButton  = {},
        dismissButton  = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape          = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

@Composable
private fun AddOptionButton(
    icon: ImageVector, title: String, subtitle: String, color: Color, onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        border   = BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.15f), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(icon, null, modifier = Modifier.size(20.dp), tint = color)
                }
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = color)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun UrlImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Import from URL", fontWeight = FontWeight.ExtraBold) },
        text    = {
            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text("Article URL") },
                placeholder = { Text("https://...") },
                leadingIcon = { Icon(Icons.Outlined.Language, null) },
                modifier   = Modifier.fillMaxWidth(),
                shape      = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (url.isNotBlank()) onImport(url.trim()) }),
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { if (url.isNotBlank()) onImport(url.trim()) }, enabled = url.isNotBlank(), shape = RoundedCornerShape(12.dp)) {
                Text("Import", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

@Composable
private fun ClipboardImportDialog(
    clipboardPreview: String,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import from Clipboard", fontWeight = FontWeight.ExtraBold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (clipboardPreview.isBlank()) {
                    Text("Clipboard is empty. Copy some text first, then try again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error)
                } else {
                    Surface(
                        shape  = RoundedCornerShape(12.dp),
                        color  = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = clipboardPreview.take(200).let { if (clipboardPreview.length > 200) "$it..." else it },
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "${clipboardPreview.length} characters",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = title, onValueChange = { title = it },
                        label = { Text("Title (optional)") },
                        placeholder = { Text("Clipboard Import") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            if (clipboardPreview.isNotBlank()) {
                Button(onClick = { onImport(title) }, shape = RoundedCornerShape(12.dp)) {
                    Text("Add to Library", fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier, onAddBook: () -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(84.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(Icons.Outlined.LibraryBooks, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Library is empty", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Text("Add a book to get started", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAddBook, shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(6.dp))
            Text("Add Content", fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Book helpers ──────────────────────────────────────────────────────────────

fun Book.typeColor(): Color = when (type) {
    BookType.PDF       -> BionicColor
    BookType.EPUB      -> FocusColor
    BookType.TXT       -> TrainColor
    BookType.WEB       -> FlashColor
    BookType.DOCX      -> SwipeColor
    BookType.MARKDOWN  -> ChunkColor
    BookType.HTML      -> ParagraphColor
    BookType.CLIPBOARD -> AccentTeal
}

fun Book.typeIcon(): ImageVector = when (type) {
    BookType.PDF       -> Icons.Outlined.PictureAsPdf
    BookType.EPUB      -> Icons.AutoMirrored.Outlined.MenuBook
    BookType.TXT       -> Icons.Outlined.Article
    BookType.WEB       -> Icons.Outlined.Language
    BookType.DOCX      -> Icons.Outlined.Description
    BookType.MARKDOWN  -> Icons.Outlined.Code
    BookType.HTML      -> Icons.Outlined.Code
    BookType.CLIPBOARD -> Icons.Outlined.ContentPaste
}

fun Book.typeLabel(): String = when (type) {
    BookType.PDF       -> "PDF"
    BookType.EPUB      -> "EPUB"
    BookType.TXT       -> "TXT"
    BookType.WEB       -> "WEB"
    BookType.DOCX      -> "DOCX"
    BookType.MARKDOWN  -> "MD"
    BookType.HTML      -> "HTML"
    BookType.CLIPBOARD -> "CLIP"
}

private fun BookFilter.label(): String = when (this) {
    BookFilter.ALL      -> "All"
    BookFilter.READING  -> "Reading"
    BookFilter.FINISHED -> "Finished"
    BookFilter.PDF      -> "PDF"
    BookFilter.EPUB     -> "EPUB"
    BookFilter.DOCX     -> "DOCX"
    BookFilter.WEB      -> "Web"
    BookFilter.TXT      -> "TXT"
    BookFilter.OTHER    -> "Other"
}
