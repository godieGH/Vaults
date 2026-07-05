// HomeScreen.kt
package com.godiegh.vaults

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch

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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
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
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
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
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(savedServices, key = { it.id }) { service ->
                        ServiceRowSwipeable(
                            service = service,
                            onClick = {
                                navController.navigate("reveal_pin/${service.countryCode}/${service.name}/${service.identifier}/${service.pinLength}")
                            },
                            onRequestDelete = { requestDelete(service) },
                            onCopyIdentifier = {
                                clipboardManager.setText(AnnotatedString(service.identifier))
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Identifier copied")
                                }
                            }
                        )
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
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ServiceRowSwipeable(
    service: ServiceConfig,
    onClick: () -> Unit,
    onRequestDelete: () -> Unit,
    onCopyIdentifier: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd || value == SwipeToDismissBoxValue.EndToStart) {
                onRequestDelete()
            }
            // Always reject the automatic dismissal — the row snaps back,
            // and actual removal only happens after the confirm dialog.
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    ) {
        ServiceRowItem(
            service = service,
            onClick = onClick,
            onLongClick = { /* handled via menu state below */ },
            onCopyIdentifier = onCopyIdentifier,
            onDelete = onRequestDelete
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ServiceRowItem(
    service: ServiceConfig,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onCopyIdentifier: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(service.name, style = MaterialTheme.typography.titleMedium)
                    Text("${service.countryCode} ${service.identifier}", style = MaterialTheme.typography.bodySmall)
                }
                Text("${service.pinLength}-Digit PIN", style = MaterialTheme.typography.labelLarge)
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Copy Identifier") },
                leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                onClick = {
                    showMenu = false
                    onCopyIdentifier()
                }
            )
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    showMenu = false
                    onDelete()
                }
            )
        }
    }
}

data class ServiceConfig(
    val id: String,
    val name: String,
    val countryCode: String,
    val identifier: String,
    val pinLength: Int
)