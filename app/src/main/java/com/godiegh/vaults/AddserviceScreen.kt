package com.godiegh.vaults

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneAndroid
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

data class CountryInfo(
    val name: String,
    val code: String,
    val abbrev: String,
    // National subscriber-number length (digits typed after removing a leading 0 or the country code).
    // Used to validate mobile money numbers. Defaults to 9 if a country entry omits it.
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
    // Countries (lowercase abbrev) this service is available in. Null/empty means
    // it's available everywhere (e.g. international wallets, card networks).
    val countries: List<String>? = null
)

private fun avatarColorFor(key: String): Color {
    val palette = listOf(
        Color(0xFF6750A4), Color(0xFF386A20), Color(0xFFB3261E),
        Color(0xFF00696D), Color(0xFF8E4EC6), Color(0xFF9C4146)
    )
    return palette[key.hashCode().mod(palette.size)]
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
            value.length > country.mobileLength -> "Too many digits — ${country.mobileLength} expected for ${country.name}"
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServiceScreen(navController: NavController) {
    val context = LocalContext.current

    // 1. Load the JSON immediately (from cache or assets)
    var rawJsonString by remember { mutableStateOf(ConfigManager.getLocalConfig(context)) }

    // 2. Try to sync with GitHub in the background
    LaunchedEffect(Unit) {
        ConfigManager.syncWithGitHub(context)
        // Optionally update the UI immediately if the download succeeds while they are on this screen
        rawJsonString = ConfigManager.getLocalConfig(context)
    }

    val (countries, availableServices) = remember(rawJsonString) {
        try {
            val jsonObject = org.json.JSONObject(rawJsonString)

            // 1. Parse Countries
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

            // 2. Parse Services
            val servicesArray = jsonObject.getJSONArray("services")
            val parsedServices = List(servicesArray.length()) { i ->
                val sObj = servicesArray.getJSONObject(i)
                val categoryEnum = try {
                    ServiceCategory.valueOf(sObj.getString("category"))
                } catch (e: Exception) {
                    ServiceCategory.MOBILE_MONEY // Fallback safely if enum name mismatches
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
            // Fail-safe defaults to keep the app from crashing if JSON format breaks
            Pair(emptyList<CountryInfo>(), emptyList<ServiceInfo>())
        }
    }

    // Initialize as nullable so it doesn't crash if lists are processing
    var selectedCountry by remember { mutableStateOf<CountryInfo?>(null) }

    // Auto-select the first country once the parsed list is populated
    if (selectedCountry == null && countries.isNotEmpty()) {
        selectedCountry = countries[0]
    }

    // A safe handle to reference throughout the UI layout
    val activeCountry = selectedCountry ?: CountryInfo("Tanzania", "+255", "tz", 9)

    var personalNumber by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ServiceCategory?>(null) }
    var selectedService by remember { mutableStateOf<ServiceInfo?>(null) }
    var pinLength by remember { mutableStateOf(4) }

    var countryExpanded by remember { mutableStateOf(false) }
    var serviceExpanded by remember { mutableStateOf(false) }

    val isPhoneBased = selectedCategory == ServiceCategory.MOBILE_MONEY
    val fullIdentifier = if (isPhoneBased) "${activeCountry.code}$personalNumber" else personalNumber

    // Scope the provider list to the selected country. Global services (countries == null)
    // always show; country-specific ones only show when they list the active country.
    val filteredServices = availableServices.filter { service ->
        service.category == selectedCategory &&
                (service.countries.isNullOrEmpty() || service.countries.contains(activeCountry.abbrev))
    }

    // Clear a previously chosen provider if it's no longer valid for the current country/category
    LaunchedEffect(selectedCategory, activeCountry) {
        if (selectedService != null && selectedService !in filteredServices) {
            selectedService = null
        }
    }

    val validationError = identifierError(selectedCategory, personalNumber, activeCountry)
    val canSave = selectedService != null && validationError == null

    Scaffold(
        topBar = { TopAppBar(title = { Text("Link New Service") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding() // <--- Key addition for virtual screen keyboard responsiveness
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

            // --- CATEGORY PICKER ---
            Text("What are you linking?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val chunks = ServiceCategory.entries.chunked(2)
                chunks.forEach { rowCategories ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowCategories.forEach { category ->
                            val selected = category == selectedCategory
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(72.dp)
                                    .clickable {
                                        selectedCategory = category
                                        selectedService = null
                                        personalNumber = ""
                                    },
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        category.icon,
                                        contentDescription = null,
                                        tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        category.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (rowCategories.size < 2) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isPhoneBased) {
                            Box(modifier = Modifier.width(120.dp)) {
                                OutlinedTextField(
                                    value = "${activeCountry.abbrev.uppercase()} ${activeCountry.code}",
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    singleLine = true
                                )
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable { countryExpanded = true }
                                )
                                DropdownMenu(
                                    expanded = countryExpanded,
                                    onDismissRequest = { countryExpanded = false },
                                    modifier = Modifier.heightIn(max = 320.dp)
                                ) {
                                    countries.forEach { country ->
                                        DropdownMenuItem(
                                            text = { Text("${country.name} (${country.code})") },
                                            onClick = {
                                                selectedCountry = country
                                                // Number was valid for the old country; force re-entry so
                                                // the length constraint is re-validated against the new one.
                                                personalNumber = ""
                                                countryExpanded = false
                                            }
                                        )
                                    }
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
                                when {
                                    validationError != null && personalNumber.isNotEmpty() ->
                                        Text(validationError, color = MaterialTheme.colorScheme.error)
                                    selectedCategory == ServiceCategory.MOBILE_MONEY ->
                                        Text("${activeCountry.name}: ${activeCountry.mobileLength} digits, no leading 0")
                                    selectedCategory == ServiceCategory.CARD ->
                                        Text("Last 4 digits of the card number only — never the CVV or full number")
                                    else -> {}
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
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedService?.displayName ?: "",
                            onValueChange = {},
                            readOnly = true,
                            placeholder = {
                                Text(
                                    if (filteredServices.isEmpty())
                                        "No providers yet for ${activeCountry.name}"
                                    else "Choose a service..."
                                )
                            },
                            leadingIcon = {
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
                            },
                            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            enabled = filteredServices.isNotEmpty()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable(enabled = filteredServices.isNotEmpty()) { serviceExpanded = true }
                        )
                        DropdownMenu(
                            expanded = serviceExpanded,
                            onDismissRequest = { serviceExpanded = false },
                            modifier = Modifier.heightIn(max = 320.dp)
                        ) {
                            filteredServices.forEach { service ->
                                DropdownMenuItem(
                                    text = { Text(service.displayName) },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(avatarColorFor(service.rustKey), shape = CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                service.displayName.take(1).uppercase(),
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    },
                                    trailingIcon = {
                                        if (service == selectedService) {
                                            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    },
                                    onClick = {
                                        selectedService = service
                                        serviceExpanded = false
                                    }
                                )
                            }
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
}