// HomeScreen.kt
package com.godiegh.vaults

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch

private enum class SortOption(val label: String) {
    NAME_AZ("Name (A–Z)"),
    NAME_ZA("Name (Z–A)"),
    RECENT("Recently added")
}

private enum class ViewMode { LIST, GRID }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var savedServices by remember { mutableStateOf(VaultsStorage.loadServices(context)) }


    var pendingDelete by remember { mutableStateOf<ServiceConfig?>(null) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    val selectionMode = selectedIds.isNotEmpty()
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(SortOption.NAME_AZ) }
    var showSortMenu by remember { mutableStateOf(false) }
    var viewMode by remember {
        mutableStateOf(
            if (VaultsStorage.loadViewMode(context) == VaultsStorage.VIEW_MODE_GRID) ViewMode.GRID else ViewMode.LIST
        )
    }

    fun toggleSelect(id: String) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    fun persist(updated: List<ServiceConfig>) {
        savedServices = updated
        VaultsStorage.saveServices(context, updated)
    }

    fun requestDelete(service: ServiceConfig) {
        pendingDelete = service
        showConfirmDialog = true
    }

    fun performDelete(service: ServiceConfig) {
        val index = savedServices.indexOf(service)
        if (index == -1) return

        val updated = savedServices.toMutableList().apply { removeAt(index) }
        persist(updated)

        coroutineScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "${service.name.replaceFirstChar { it.uppercase() }} removed",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                val restored = savedServices.toMutableList().apply {
                    add(index.coerceAtMost(size), service)
                }
                persist(restored)
            }
        }
    }

    fun performBatchDelete() {
        val toDelete = savedServices.filter { it.id in selectedIds }
        persist(savedServices.filter { it.id !in selectedIds })
        selectedIds = setOf()
        coroutineScope.launch {
            snackbarHostState.showSnackbar("${toDelete.size} service(s) removed")
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                savedServices = VaultsStorage.loadServices(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Search + sort pipeline. "Recently added" uses list order (newest last) reversed.
    val visibleServices = remember(savedServices, searchQuery, sortOption) {
        val filtered = if (searchQuery.isBlank()) {
            savedServices
        } else {
            savedServices.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.identifier.contains(searchQuery, ignoreCase = true)
            }
        }
        when (sortOption) {
            SortOption.NAME_AZ -> filtered.sortedBy { it.name.lowercase() }
            SortOption.NAME_ZA -> filtered.sortedByDescending { it.name.lowercase() }
            SortOption.RECENT -> filtered.reversed()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("${selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = setOf() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showBatchDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete selected", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )
            } else {
                TopAppBar(
                    title = {},
                    actions = {
                        IconButton(onClick = { navController.navigate("settings") }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("add_service") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Add New Service")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (savedServices.isNotEmpty() && !selectionMode) {
                // --- SEARCH + SORT + VIEW TOGGLE ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                )  {
                    Surface(
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        "Search services",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Filled.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            SortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        sortOption = option
                                        showSortMenu = false
                                    },
                                    trailingIcon = {
                                        if (option == sortOption) {
                                            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(0.dp))
                                        }
                                    }
                                )
                            }
                        }
                    }

                    IconButton(onClick = {
                        viewMode = if (viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
                        VaultsStorage.saveViewMode(
                            context,
                            if (viewMode == ViewMode.GRID) VaultsStorage.VIEW_MODE_GRID else VaultsStorage.VIEW_MODE_LIST
                        )
                    }) {
                        Icon(
                            imageVector = if (viewMode == ViewMode.LIST) Icons.Filled.GridView else Icons.Filled.List,
                            contentDescription = "Toggle view"
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (savedServices.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No Derived PINs Yet", style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Link your mobile money wallets or bank cards to dynamically generate secure PINs.",
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { navController.navigate("add_service") }) {
                            Text("Configure First Service")
                        }
                    }
                } else if (visibleServices.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No matches for \"$searchQuery\"", style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (viewMode == ViewMode.LIST) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(visibleServices, key = { it.id }) { service ->
                            ServiceRowSwipeable(
                                service = service,
                                isSelected = service.id in selectedIds,
                                selectionMode = selectionMode,
                                onClick = {
                                    if (selectionMode) toggleSelect(service.id)
                                    else navController.navigate("reveal_pin/${service.id}/${service.countryCode}/${service.name}/${service.identifier}/${service.pinLength}/${service.rotation}")
                                },
                                onLongClick = { toggleSelect(service.id) },
                                onRotate = {
                                    navController.navigate("rotate_pin/${service.id}/${service.countryCode}/${service.name}/${service.identifier}/${service.pinLength}/${service.rotation}")
                                },
                                onCopyIdentifier = {
                                    clipboardManager.setText(AnnotatedString(service.identifier))
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Identifier copied") }
                                },
                                onRequestDelete = { requestDelete(service) }
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(visibleServices, key = { it.id }) { service ->
                            ServiceGridItem(
                                service = service,
                                isSelected = service.id in selectedIds,
                                selectionMode = selectionMode,
                                onClick = {
                                    if (selectionMode) toggleSelect(service.id)
                                    else navController.navigate("reveal_pin/${service.id}/${service.countryCode}/${service.name}/${service.identifier}/${service.pinLength}/${service.rotation}")
                                },
                                onLongClick = { toggleSelect(service.id) },
                                onCopyIdentifier = {
                                    clipboardManager.setText(AnnotatedString(service.identifier))
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Identifier copied") }
                                },
                                onRotate = {
                                    navController.navigate("rotate_pin/${service.id}/${service.countryCode}/${service.name}/${service.identifier}/${service.pinLength}/${service.rotation}")
                                },
                                onRequestDelete = { requestDelete(service) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showConfirmDialog && pendingDelete != null) {
        val service = pendingDelete!!
        AlertDialog(
            onDismissRequest = {
                showConfirmDialog = false
                pendingDelete = null
            },
            title = { Text("Remove this service?") },
            text = {
                Text("This removes ${service.name.replaceFirstChar { it.uppercase() }} (${service.countryCode} ${service.identifier}) from your list. You can undo this right after.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        pendingDelete = null
                        performDelete(service)
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        pendingDelete = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text("Remove ${selectedIds.size} service(s)?") },
            text = { Text("This can't be undone from here.") },
            confirmButton = {
                TextButton(onClick = {
                    showBatchDeleteDialog = false
                    performBatchDelete()
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ServiceRowSwipeable(
    service: ServiceConfig,
    isSelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCopyIdentifier: () -> Unit,
    onRotate: () -> Unit,
    onRequestDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRotate()
            }
            false // always snap back — rotate is an action, not a dismissal
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = !selectionMode,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp))
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Filled.Autorenew,
                    contentDescription = "Rotate PIN",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) {
        ServiceRowItem(
            service = service,
            isSelected = isSelected,
            selectionMode = selectionMode,
            onClick = onClick,
            onLongClick = onLongClick,
            onCopyIdentifier = onCopyIdentifier,
            onDelete = onRequestDelete,
            onRotate = onRotate
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ServiceRowItem(
    service: ServiceConfig,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onCopyIdentifier: () -> Unit,
    onDelete: () -> Unit,
    onRotate: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val avatarColor = remember(service.name) { avatarColorFor(service.name) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onLongClick() }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() })
                Spacer(modifier = Modifier.width(4.dp))
            } else {
                Box(
                    modifier = Modifier.size(44.dp).background(avatarColor, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        service.name.take(1).uppercase(),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = service.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = when (service.category) {
                        "MOBILE_MONEY" -> "${service.countryCode.uppercase()} ${service.identifier}"
                        "BANK" -> "Acc: ${service.identifier}"
                        "CARD" -> "•••• ${service.identifier}"
                        "WALLET" -> "@${service.identifier}"
                        else -> service.identifier
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface
            ) {
                Text(
                    "${service.pinLength}-digit",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            if (!selectionMode) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    ServiceActionMenu(
                        expanded = showMenu,
                        onDismiss = { showMenu = false },
                        onCopyIdentifier = onCopyIdentifier,
                        onRotate = onRotate,
                        onDelete = onDelete
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ServiceGridItem(
    service: ServiceConfig,
    isSelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCopyIdentifier: () -> Unit,
    onRotate: () -> Unit,
    onRequestDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val avatarColor = remember(service.name) { avatarColorFor(service.name) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onLongClick() }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                if (selectionMode) {
                    Checkbox(checked = isSelected, onCheckedChange = { onClick() })
                } else {
                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        ServiceActionMenu(
                            expanded = showMenu,
                            onDismiss = { showMenu = false },
                            onCopyIdentifier = onCopyIdentifier,
                            onRotate = onRotate,
                            onDelete = onRequestDelete
                        )
                    }
                }
            }

            Box(
                modifier = Modifier.size(52.dp).background(avatarColor, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    service.name.take(1).uppercase(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = service.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                text = when (service.category) {
                    "MOBILE_MONEY" -> "${service.countryCode.uppercase()} ${service.identifier}"
                    "BANK" -> "Acc: ${service.identifier}"
                    "CARD" -> "•••• ${service.identifier}"
                    "WALLET" -> "@${service.identifier}"
                    else -> service.identifier
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface
            ) {
                Text(
                    "${service.pinLength}-digit",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

/**
 * Anchored directly to the three-dot icon it's declared next to, with a small
 * negative offset so it hugs the icon instead of floating below the card edge.
 */
@Composable
private fun ServiceActionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onCopyIdentifier: () -> Unit,
    onRotate: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = DpOffset(x = (-8).dp, y = 0.dp)
    ) {
        DropdownMenuItem(
            text = { Text("Copy Identifier") },
            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
            onClick = { onDismiss(); onCopyIdentifier() }
        )
        DropdownMenuItem(
            text = { Text("Rotate PIN") },
            leadingIcon = { Icon(Icons.Filled.Autorenew, contentDescription = null) },
            onClick = { onDismiss(); onRotate() }
        )
        DropdownMenuItem(
            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            onClick = { onDismiss(); onDelete() }
        )
    }
}

private fun avatarColorFor(name: String): Color {
    val palette = listOf(
        Color(0xFF6750A4), Color(0xFF386A20), Color(0xFFB3261E),
        Color(0xFF00696D), Color(0xFF8E4EC6), Color(0xFF9C4146)
    )
    return palette[name.hashCode().mod(palette.size)]
}


data class ServiceConfig(
    val id: String,
    val name: String,
    val countryCode: String,
    val identifier: String,
    val pinLength: Int,
    val rotation: Int = 1,
    val displayName: String = name.replaceFirstChar { it.uppercase() }, // fallback for old entries
    val category: String = "MOBILE_MONEY" // fallback for old entries
)