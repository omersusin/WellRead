package com.omersusin.wellread.ui.screens.library

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

    // Use OpenDocument for persistent URI permissions
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // Take persistable permission so URI survives app restarts
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
            TopAppBar(
                title = {
                    Text(
                        "Library",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.showAddDialog() },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add Book") },
                containerColor = WellReadPurple,
                contentColor = Color.White,
                shape = RoundedCornerShape(20.dp)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search books…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQuery("") }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                },
                shape = RoundedCornerShape(18.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WellReadPurple,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(BookFilter.values()) { filter ->
                    FilterChip(
                        selected = uiState.selectedFilter == filter,
                        onClick = { viewModel.onFilterSelected(filter) },
                        label = { Text(filter.label()) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WellReadPurple,
                            selectedLabelColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            if (uiState.isImporting) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    color = WellReadPurple
                )
            }

            if (uiState.filteredBooks.isEmpty()) {
                EmptyState(
                    modifier = Modifier.weight(1f),
                    onAddBook = { viewModel.showAddDialog() }
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(uiState.filteredBooks, key = { it.id }) { book ->
                        LibraryBookCard(
                            book = book,
                            onBookClick = { onNavigateToReader(book.id, ReadingMode.BIONIC.name) },
                            onDeleteClick = { viewModel.deleteBook(book) }
                        )
                    }
                }
            }
        }
    }

    if (uiState.showAddDialog) {
        AddBookDialog(
            onDismiss = viewModel::hideAddDialog,
            onAddFile = {
                viewModel.hideAddDialog()
                fileLauncher.launch(arrayOf(
                    "application/pdf",
                    "application/epub+zip",
                    "text/plain",
                    "*/*"
                ))
            },
            onAddUrl = viewModel::showUrlDialog
        )
    }

    if (uiState.showUrlDialog) {
        UrlImportDialog(
            onDismiss = viewModel::hideUrlDialog,
            onImport = viewModel::importFromUrl
        )
    }

    uiState.importError?.let { error ->
        LaunchedEffect(error) { viewModel.clearError() }
    }
}

@Composable
private fun LibraryBookCard(
    book: Book,
    onBookClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val progress = if (book.totalWords > 0) book.currentPosition.toFloat() / book.totalWords else 0f

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onBookClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = book.typeColor().copy(alpha = 0.15f)
                ) {
                    Text(
                        text = book.type.name,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = book.typeColor()
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.MoreVert, null, modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = { showMenu = false; onDeleteClick() },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = book.typeColor().copy(alpha = 0.12f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = book.typeIcon(),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = book.typeColor()
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (book.author != "Unknown") {
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                color = book.typeColor(),
                trackColor = book.typeColor().copy(alpha = 0.2f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${(progress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${book.totalWords / 1000}K words",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AddBookDialog(
    onDismiss: () -> Unit,
    onAddFile: () -> Unit,
    onAddUrl: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Content", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AddOptionButton(
                    icon = Icons.Outlined.UploadFile,
                    title = "From File",
                    subtitle = "PDF, EPUB or TXT",
                    color = BionicColor,
                    onClick = onAddFile
                )
                AddOptionButton(
                    icon = Icons.Outlined.Language,
                    title = "From Web URL",
                    subtitle = "Paste any article URL",
                    color = FlashColor,
                    onClick = onAddUrl
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    )
}

@Composable
private fun AddOptionButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.15f), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(icon, null, modifier = Modifier.size(22.dp), tint = color)
                }
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun UrlImportDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import from URL", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Article URL") },
                placeholder = { Text("https://…") },
                leadingIcon = { Icon(Icons.Outlined.Language, null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (url.isNotBlank()) onImport(url) }),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = WellReadPurple)
            )
        },
        confirmButton = {
            Button(
                onClick = { if (url.isNotBlank()) onImport(url) },
                enabled = url.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WellReadPurple)
            ) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    )
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier, onAddBook: () -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(shape = CircleShape, color = WellReadPurple.copy(alpha = 0.12f), modifier = Modifier.size(80.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(Icons.Outlined.LibraryBooks, null, modifier = Modifier.size(40.dp), tint = WellReadPurple)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("No books yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Add your first book to get started",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun Book.typeColor(): Color = when (type) {
    BookType.PDF  -> BionicColor
    BookType.EPUB -> FocusColor
    BookType.TXT  -> TrainColor
    BookType.WEB  -> FlashColor
}

private fun Book.typeIcon(): ImageVector = when (type) {
    BookType.PDF  -> Icons.Outlined.PictureAsPdf
    BookType.EPUB -> Icons.AutoMirrored.Outlined.MenuBook
    BookType.TXT  -> Icons.Outlined.Article
    BookType.WEB  -> Icons.Outlined.Language
}

private fun BookFilter.label(): String = when (this) {
    BookFilter.ALL      -> "All"
    BookFilter.READING  -> "Reading"
    BookFilter.FINISHED -> "Finished"
    BookFilter.PDF      -> "PDF"
    BookFilter.EPUB     -> "EPUB"
    BookFilter.WEB      -> "Web"
}
