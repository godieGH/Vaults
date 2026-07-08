package com.godiegh.vaults

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController

data class CountryInfo(
    val name: String,
    val code: String,
    val abbrev: String,
    // National subscriber-number length (digits typed after removing a leading 0 or the country code).
    val mobileLength: Int = 9
)

enum class ServiceCategory(val label: String, val icon: ImageVector) {
    MOBILE_MONEY("Mobile Money", Icons.Filled.PhoneAndroid),
    BANK("Bank Account", Icons.Filled.AccountBalance),
    CARD("ATM / Card", Icons.Filled.CreditCard),
    WALLET("Wallet / Apps", Icons.Filled.Wallet),
    PAYMENT("Payment / Gateway", Icons.Filled.Payment)
}

data class ServiceInfo(
    val displayName: String,
    val rustKey: String,
    val category: ServiceCategory,
    // Countries (lowercase abbrev) this service is available in. Null/empty means global.
    val countries: List<String>? = null
)

private fun avatarColorFor(key: String): Color {
    val palette = listOf(
        Color(0xFF6750A4), Color(0xFF386A20), Color(0xFFB3261E),
        Color(0xFF00696D), Color(0xFF8E4EC6), Color(0xFF9C4146)
    )
    return palette[key.hashCode().mod(palette.size)]
}

/** Converts a 2-letter country abbreviation (e.g. "tz") into a flag emoji. */
private fun countryFlagEmoji(abbrev: String): String {
    if (abbrev.length != 2) return "🏳️"
    val base = 0x1F1E6
    val a = abbrev[0].uppercaseChar()
    val b = abbrev[1].uppercaseChar()
    if (a !in 'A'..'Z' || b !in 'A'..'Z') return "🏳️"
    val first = base + (a - 'A')
    val second = base + (b - 'A')
    return String(Character.toChars(first)) + String(Character.toChars(second))
}

/**
 * Validates the raw identifier the user typed for a given category/country combo.
 * Returns null when valid, or a short user-facing error message when invalid.
 */
private fun identifierError(category: ServiceCategory?, value: String, country: CountryInfo): String? {
    if (category == null) return null
    return when (category) {
        ServiceCategory.MOBILE_MONEY -> when {
            value.isEmpty() -> "Enter a phone number"
            value.length < country.mobileLength -> "Needs ${country.mobileLength} digits (${country.mobileLength - value.length} more)"
            value.length > country.mobileLength -> "Too many digits for ${country.name}"
            else -> null
        }
        ServiceCategory.BANK -> when {
            value.isEmpty() -> "Enter an account number"
            value.length < 6 -> "Account numbers are usually at least 6 digits"
            value.length > 20 -> "That's longer than a typical account number"
            else -> null
        }
        ServiceCategory.CARD -> when {
            value.isEmpty() -> "Enter the last 4 digits"
            value.length < 4 -> "Needs 4 digits (${4 - value.length} more)"
            else -> null
        }
        ServiceCategory.WALLET, ServiceCategory.PAYMENT -> when {
            value.isBlank() -> "Enter an ID, username, or number"
            value.trim().length < 3 -> "Too short"
            else -> null
        }
    }
}

/**
 * Responsive category picker. Always a single row of equally-sized chips so it never
 * looks lopsided. On narrow screens each chip shows only its icon; once there's enough
 * width for every chip to comfortably fit an icon + label, labels appear too.
 */
@Composable
private fun CategoryPicker(selected: ServiceCategory?, onSelect: (ServiceCategory) -> Unit) {
    val chipWidthForLabel = 96.dp
    val spacing = 8.dp
    val count = ServiceCategory.entries.size

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val neededForLabels = chipWidthForLabel * count + spacing * (count - 1)
        val showLabels = maxWidth >= neededForLabels

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            ServiceCategory.entries.forEach { category ->
                val isSelected = category == selected
                val bg = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                val fg = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (showLabels) 68.dp else 56.dp)
                        .clickable { onSelect(category) },
                    shape = RoundedCornerShape(14.dp),
                    color = bg
                ) {
                    if (showLabels) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(category.icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                category.label,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = fg
                            )
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(category.icon, contentDescription = category.label, tint = fg, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    }
}

/** A generic full-screen searchable list dialog, used for both country and provider pickers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> FullScreenSelectorDialog(
    title: String,
    items: List<T>,
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    itemKey: (T) -> Any,
    itemContent: @Composable (T) -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Close")
                        }
                    }
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                if (items.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("No results", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(items, key = itemKey) { item -> itemContent(item) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServiceScreen(navController: NavController) {
    val context = LocalContext.current

    // 1. Load the JSON immediately (from cache or assets)
    var rawJsonString by remember { mutableStateOf(ConfigManager.getLocalConfig(context)) }

    // 2. Try to sync with GitHub in the background
    LaunchedEffect(Unit) {
        ConfigManager.syncWithGitHub(context)
        rawJsonString = ConfigManager.getLocalConfig(context)
    }

    val (countries, availableServices) = remember(rawJsonString) {
        try {
            val jsonObject = org.json.JSONObject(rawJsonString)

            val countriesArray = jsonObject.getJSONArray("countries")
            val parsedCountries = List(countriesArray.length()) { i ->
                val cObj = countriesArray.getJSONObject(i)
                CountryInfo(
                    name = cObj.getString("name"),
                    code = cObj.getString("code"),
                    abbrev = cObj.getString("abbrev"),
                    mobileLength = cObj.optInt("mobileLength", 9)
                )
            }

            val servicesArray = jsonObject.getJSONArray("services")
            val parsedServices = List(servicesArray.length()) { i ->
                val sObj = servicesArray.getJSONObject(i)
                val categoryEnum = try {
                    ServiceCategory.valueOf(sObj.getString("category"))
                } catch (e: Exception) {
                    ServiceCategory.MOBILE_MONEY
                }
                val countriesForService = sObj.optJSONArray("countries")?.let { arr ->
                    List(arr.length()) { j -> arr.getString(j) }
                }
                ServiceInfo(
                    displayName = sObj.getString("displayName"),
                    rustKey = sObj.getString("rustKey"),
                    category = categoryEnum,
                    countries = countriesForService
                )
            }

            Pair(parsedCountries, parsedServices)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(emptyList<CountryInfo>(), emptyList<ServiceInfo>())
        }
    }

    var selectedCountry by remember { mutableStateOf<CountryInfo?>(null) }
    if (selectedCountry == null && countries.isNotEmpty()) {
        selectedCountry = countries[0]
    }
    val activeCountry = selectedCountry ?: CountryInfo("Tanzania", "+255", "tz", 9)

    var personalNumber by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ServiceCategory?>(null) }
    var selectedService by remember { mutableStateOf<ServiceInfo?>(null) }
    var pinLength by remember { mutableStateOf(4) }

    var showCountryDialog by remember { mutableStateOf(false) }
    var showServiceDialog by remember { mutableStateOf(false) }
    var countryQuery by remember { mutableStateOf("") }
    var serviceQuery by remember { mutableStateOf("") }

    val isPhoneBased = selectedCategory == ServiceCategory.MOBILE_MONEY
    val fullIdentifier = if (isPhoneBased) "${activeCountry.code}$personalNumber" else personalNumber

    // Scope providers to the selected country. Global services (countries == null) always show.
    val filteredServices = availableServices.filter { service ->
        service.category == selectedCategory &&
                (service.countries.isNullOrEmpty() || service.countries.contains(activeCountry.abbrev))
    }

    LaunchedEffect(selectedCategory, activeCountry) {
        if (selectedService != null && selectedService !in filteredServices) {
            selectedService = null
        }
    }

    val validationError = identifierError(selectedCategory, personalNumber, activeCountry)
    val canSave = selectedService != null && validationError == null

    val filteredCountries = remember(countries, countryQuery) {
        if (countryQuery.isBlank()) countries
        else countries.filter {
            it.name.contains(countryQuery, ignoreCase = true) || it.code.contains(countryQuery)
        }
    }
    val filteredServicesForDialog = remember(filteredServices, serviceQuery) {
        if (serviceQuery.isBlank()) filteredServices
        else filteredServices.filter { it.displayName.contains(serviceQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Link New Service") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // --- HEADER ICON ---
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .align(Alignment.CenterHorizontally)
                    .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Phone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            // --- CATEGORY PICKER (responsive, single row) ---
            Text("What are you linking?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            CategoryPicker(selected = selectedCategory) { category ->
                selectedCategory = category
                selectedService = null
                personalNumber = ""
            }

            // --- REST OF FORM ---
            AnimatedVisibility(visible = selectedCategory != null, enter = fadeIn(), exit = fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {

                    // --- IDENTIFIER ROW ---
                    Text(
                        when (selectedCategory) {
                            ServiceCategory.MOBILE_MONEY -> "Phone Number"
                            ServiceCategory.BANK -> "Account Number"
                            ServiceCategory.CARD -> "Card Last 4 Digits"
                            ServiceCategory.WALLET -> "Wallet ID / Username"
                            ServiceCategory.PAYMENT -> "Account / Merchant ID"
                            null -> ""
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isPhoneBased) {
                            // Compact country trigger — sized to content, not stretched.
                            Surface(
                                modifier = Modifier
                                    .height(56.dp)
                                    .clickable { showCountryDialog = true },
                                shape = RoundedCornerShape(14.dp),
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(countryFlagEmoji(activeCountry.abbrev), fontSize = 18.sp)
                                    Text(activeCountry.code, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        OutlinedTextField(
                            value = personalNumber,
                            onValueChange = { input ->
                                personalNumber = when (selectedCategory) {
                                    ServiceCategory.CARD -> input.filter { it.isDigit() }.take(4)
                                    ServiceCategory.MOBILE_MONEY -> {
                                        val clean = input.filter { it.isDigit() }
                                        val stripped = when {
                                            clean.startsWith(activeCountry.code.removePrefix("+")) ->
                                                clean.removePrefix(activeCountry.code.removePrefix("+"))
                                            clean.startsWith("0") -> clean.removePrefix("0")
                                            else -> clean
                                        }
                                        stripped.take(activeCountry.mobileLength)
                                    }
                                    ServiceCategory.BANK -> input.filter { it.isDigit() }.take(20)
                                    ServiceCategory.WALLET, ServiceCategory.PAYMENT -> input
                                    null -> input
                                }
                            },
                            isError = personalNumber.isNotEmpty() && validationError != null,
                            supportingText = {
                                if (validationError != null && personalNumber.isNotEmpty()) {
                                    Text(validationError, color = MaterialTheme.colorScheme.error)
                                } else if (selectedCategory == ServiceCategory.CARD) {
                                    Text("Last 4 digits of the card number only — never the CVV or full number")
                                }
                            },
                            label = {
                                Text(
                                    when (selectedCategory) {
                                        ServiceCategory.CARD -> "Last 4 digits"
                                        ServiceCategory.WALLET -> "Username / ID"
                                        ServiceCategory.PAYMENT -> "Account / ID"
                                        else -> "Number"
                                    }
                                )
                            },
                            placeholder = {
                                Text(
                                    when (selectedCategory) {
                                        ServiceCategory.MOBILE_MONEY -> "7XXXXXXXX".take(activeCountry.mobileLength)
                                        ServiceCategory.BANK -> "0123456789"
                                        ServiceCategory.CARD -> "1234"
                                        ServiceCategory.WALLET -> "your_username"
                                        ServiceCategory.PAYMENT -> "merchant_id or account"
                                        null -> ""
                                    }
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = when (selectedCategory) {
                                    ServiceCategory.WALLET, ServiceCategory.PAYMENT -> KeyboardType.Text
                                    else -> KeyboardType.Number
                                }
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true
                        )
                    }

                    // --- SERVICE PROVIDER ---
                    Text("Service Provider", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clickable(enabled = filteredServices.isNotEmpty()) { showServiceDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (selectedService != null) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(avatarColorFor(selectedService!!.rustKey), shape = CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        selectedService!!.displayName.take(1).uppercase(),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                            Text(
                                selectedService?.displayName
                                    ?: if (filteredServices.isEmpty()) "No providers yet for ${activeCountry.name}" else "Choose a service...",
                                modifier = Modifier.weight(1f),
                                color = if (selectedService == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                    }

                    // --- PIN LENGTH STEPPER ---
                    Text("PIN Length", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { if (pinLength > 4) pinLength-- },
                                enabled = pinLength > 4
                            ) {
                                Icon(Icons.Filled.Remove, contentDescription = "Decrease PIN length")
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "$pinLength",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "digits",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = { if (pinLength < 8) pinLength++ },
                                enabled = pinLength < 8
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Increase PIN length")
                            }
                        }
                    }

                    // --- LIVE PREVIEW CARD ---
                    AnimatedVisibility(visible = canSave, enter = fadeIn(), exit = fadeOut()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            selectedService?.let { avatarColorFor(it.rustKey) } ?: MaterialTheme.colorScheme.primary,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        selectedService?.displayName?.take(1)?.uppercase() ?: "",
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        selectedService?.displayName ?: "",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "$fullIdentifier • $pinLength-digit PIN",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // --- SAVE BUTTON ---
                    Button(
                        onClick = {
                            val service = selectedService!!

                            val finalConfig = ServiceConfig(
                                id = java.util.UUID.randomUUID().toString(),
                                name = service.rustKey,
                                countryCode = activeCountry.abbrev,
                                identifier = fullIdentifier,
                                pinLength = pinLength,
                                displayName = service.displayName,
                                category = service.category.name
                            )

                            PendingServiceHolder.pending = finalConfig
                            navController.navigate("new_service_pin")
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        enabled = canSave
                    ) {
                        Text("Save Service Link", style = MaterialTheme.typography.titleMedium)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    // --- FULL-SCREEN COUNTRY PICKER ---
    if (showCountryDialog) {
        FullScreenSelectorDialog(
            title = "Choose a country",
            items = filteredCountries,
            query = countryQuery,
            onQueryChange = { countryQuery = it },
            onDismiss = { showCountryDialog = false; countryQuery = "" },
            itemKey = { it.abbrev }
        ) { country ->
            ListItem(
                headlineContent = { Text(country.name) },
                supportingContent = { Text(country.code) },
                leadingContent = { Text(countryFlagEmoji(country.abbrev), fontSize = 24.sp) },
                trailingContent = {
                    if (country.abbrev == activeCountry.abbrev) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                },
                modifier = Modifier.clickable {
                    selectedCountry = country
                    personalNumber = "" // re-validate length against the new country
                    showCountryDialog = false
                    countryQuery = ""
                }
            )
        }
    }

    // --- FULL-SCREEN SERVICE PROVIDER PICKER ---
    if (showServiceDialog) {
        FullScreenSelectorDialog(
            title = "Choose a service provider",
            items = filteredServicesForDialog,
            query = serviceQuery,
            onQueryChange = { serviceQuery = it },
            onDismiss = { showServiceDialog = false; serviceQuery = "" },
            itemKey = { it.rustKey }
        ) { service ->
            ListItem(
                headlineContent = { Text(service.displayName) },
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(avatarColorFor(service.rustKey), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(service.displayName.take(1).uppercase(), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                },
                trailingContent = {
                    if (service == selectedService) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                },
                modifier = Modifier.clickable {
                    selectedService = service
                    showServiceDialog = false
                    serviceQuery = ""
                }
            )
        }
    }
}