package com.godiegh.vaults

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

data class CountryInfo(val name: String, val code: String, val abbrev: String)
enum class ServiceCategory(val label: String, val icon: ImageVector) {
    MOBILE_MONEY("Mobile Money", Icons.Filled.PhoneAndroid),
    BANK("Bank Account", Icons.Filled.AccountBalance),
    CARD("ATM / Card", Icons.Filled.CreditCard),
    WALLET(label = "Wallet / Apps Services", Icons.Filled.Wallet)
}

data class ServiceInfo(val displayName: String, val rustKey: String, val category: ServiceCategory)
private fun avatarColorFor(key: String): Color {
    val palette = listOf(
        Color(0xFF6750A4), Color(0xFF386A20), Color(0xFFB3261E),
        Color(0xFF00696D), Color(0xFF8E4EC6), Color(0xFF9C4146)
    )
    return palette[key.hashCode().mod(palette.size)]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServiceScreen(navController: NavController) {
    val context = LocalContext.current

    val countries = listOf(
        CountryInfo("Tanzania", "+255", "tz"),
        CountryInfo("Kenya", "+254", "ke"),
        CountryInfo("Uganda", "+256", "ug"),
        CountryInfo("Rwanda", "+250", "rw"),
        CountryInfo("Ethiopia", "+251", "et"),
        CountryInfo("Zambia", "+260", "zm"),
        CountryInfo("Mozambique", "+258", "mz"),
        CountryInfo("Malawi", "+265", "mw"),
        CountryInfo("Ghana", "+233", "gh"),
        CountryInfo("Nigeria", "+234", "ng"),
        CountryInfo("South Africa", "+27", "za"),
        CountryInfo("Cameroon", "+237", "cm"),
        CountryInfo("Senegal", "+221", "sn"),
        CountryInfo("Ivory Coast", "+225", "ci"),
        CountryInfo("DRC", "+243", "cd")
    )

    val availableServices = listOf(
        ServiceInfo("M-Pesa", "mpesa", ServiceCategory.MOBILE_MONEY),
        ServiceInfo("Airtel Money", "airtelmoney", ServiceCategory.MOBILE_MONEY),
        ServiceInfo("Mixx By Yas (TigoPesa)", "tigopesa", ServiceCategory.MOBILE_MONEY),
        ServiceInfo("HaloPesa", "halopesa", ServiceCategory.MOBILE_MONEY),
        ServiceInfo("MTN Mobile Money", "mtnmomo", ServiceCategory.MOBILE_MONEY),
        ServiceInfo("CRDB SimBanking", "crdbsimbanking", ServiceCategory.BANK),
        ServiceInfo("NMB Mkonjani", "nmbmklik", ServiceCategory.BANK),
        ServiceInfo("NBC Kiganjani", "nbckiganjani", ServiceCategory.BANK),
        ServiceInfo("Equity Bank", "equitybank", ServiceCategory.BANK),
        ServiceInfo("KCB M-Pesa", "kcbmpesa", ServiceCategory.BANK),
        ServiceInfo("Generic ATM Card", "atmcard", ServiceCategory.CARD),
        ServiceInfo("Wave Wallet", "wave", ServiceCategory.WALLET),
        ServiceInfo("Selcom Pay", "selcom", ServiceCategory.WALLET),
        ServiceInfo("Nala App", "nala", ServiceCategory.WALLET)
    )

    var selectedCountry by remember { mutableStateOf(countries[0]) }
    var personalNumber by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ServiceCategory?>(null) }
    var selectedService by remember { mutableStateOf<ServiceInfo?>(null) }
    var pinLength by remember { mutableStateOf(4) }

    var countryExpanded by remember { mutableStateOf(false) }
    var serviceExpanded by remember { mutableStateOf(false) }

    val isPhoneBased = selectedCategory == ServiceCategory.MOBILE_MONEY
    val fullIdentifier = if (isPhoneBased) "${selectedCountry.code}$personalNumber" else personalNumber
    val filteredServices = availableServices.filter { it.category == selectedCategory }
    val canSave = personalNumber.isNotBlank() && selectedService != null

    Scaffold(
        topBar = { TopAppBar(title = { Text("Link New Service") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ServiceCategory.entries.forEach { category ->
                    val selected = category == selectedCategory
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clickable {
                                selectedCategory = category
                                selectedService = null
                                personalNumber = ""
                            },
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                category.icon,
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                category.label,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // --- REST OF FORM: only shows once a category is picked ---
            AnimatedVisibility(visible = selectedCategory != null, enter = fadeIn(), exit = fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {

                    // --- IDENTIFIER ROW (phone / account / card / wallet) ---
                    Text(
                        when (selectedCategory) {
                            ServiceCategory.MOBILE_MONEY -> "Phone Number"
                            ServiceCategory.BANK -> "Account Number"
                            ServiceCategory.CARD -> "Card Last 4 Digits"
                            ServiceCategory.WALLET -> "Wallet ID / Username"
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
                                    value = "${selectedCountry.abbrev.uppercase()} ${selectedCountry.code}",
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
                                    onDismissRequest = { countryExpanded = false }
                                ) {
                                    countries.forEach { country ->
                                        DropdownMenuItem(
                                            text = { Text("${country.name} (${country.code})") },
                                            onClick = {
                                                selectedCountry = country
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
                                        when {
                                            clean.startsWith(selectedCountry.code.removePrefix("+")) ->
                                                clean.removePrefix(selectedCountry.code.removePrefix("+"))
                                            clean.startsWith("0") -> clean.removePrefix("0")
                                            else -> clean
                                        }
                                    }
                                    ServiceCategory.BANK -> input.filter { it.isDigit() }
                                    ServiceCategory.WALLET -> input
                                    null -> input
                                }
                            },
                            label = {
                                Text(
                                    when (selectedCategory) {
                                        ServiceCategory.CARD -> "Last 4 digits"
                                        ServiceCategory.WALLET -> "Username / ID"
                                        else -> "Number"
                                    }
                                )
                            },
                            placeholder = {
                                Text(
                                    when (selectedCategory) {
                                        ServiceCategory.MOBILE_MONEY -> "712345678"
                                        ServiceCategory.BANK -> "0123456789"
                                        ServiceCategory.CARD -> "1234"
                                        ServiceCategory.WALLET -> "your_username"
                                        null -> ""
                                    }
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (selectedCategory == ServiceCategory.WALLET) KeyboardType.Text else KeyboardType.Number
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
                            placeholder = { Text("Choose a service...") },
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
                            singleLine = true
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { serviceExpanded = true }
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
                                countryCode = selectedCountry.abbrev,
                                identifier = fullIdentifier,
                                pinLength = pinLength,
                                displayName = service.displayName,
                                category = selectedCategory!!.name
                            )

                            val currentServices = VaultsStorage.loadServices(context).toMutableList()
                            currentServices.add(finalConfig)
                            VaultsStorage.saveServices(context, currentServices)
                            navController.popBackStack()
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