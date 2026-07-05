package com.godiegh.vaults

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

data class CountryInfo(val name: String, val code: String, val abbrev: String)
data class ServiceInfo(val displayName: String, val rustKey: String)

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
        ServiceInfo("M-Pesa", "mpesa"),
        ServiceInfo("Airtel Money", "airtelmoney"),
        ServiceInfo("Mixx By Yas (TigoPesa)", "tigopesa"),
        ServiceInfo("HaloPesa", "halopesa"),
        ServiceInfo("CRDB SimBanking", "crdbsimbanking"),
        ServiceInfo("NMB Mklik", "nmbmklik"),
        ServiceInfo("NBC Kiganjani", "nbckiganjani"),
        ServiceInfo("Equity Bank", "equitybank"),
        ServiceInfo("KCB M-Pesa", "kcbmpesa"),
        ServiceInfo("MTN Mobile Money", "mtnmomo")
    )

    var selectedCountry by remember { mutableStateOf(countries[0]) }
    var personalNumber by remember { mutableStateOf("") }
    var selectedService by remember { mutableStateOf<ServiceInfo?>(null) }
    var pinLength by remember { mutableStateOf("") }

    var countryExpanded by remember { mutableStateOf(false) }
    var serviceExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Link New Service") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- PHONE NUMBER ROW ---
            Text("Phone / Account Number", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Country code — compact box
                Box(modifier = Modifier.width(110.dp)) {
                    OutlinedTextField(
                        value = "${selectedCountry.abbrev.uppercase()} ${selectedCountry.code}",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            Icon(Icons.Filled.ArrowDropDown, null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    // Invisible overlay to capture full box tap
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

                // Phone number input
                OutlinedTextField(
                    value = personalNumber,
                    onValueChange = { input ->
                        val clean = input.filter { it.isDigit() }
                        personalNumber = when {
                            clean.startsWith(selectedCountry.code.removePrefix("+")) ->
                                clean.removePrefix(selectedCountry.code.removePrefix("+"))
                            clean.startsWith("0") -> clean.removePrefix("0")
                            else -> clean
                        }
                    },
                    label = { Text("Number") },
                    placeholder = { Text("712345678") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            // --- SERVICE PROVIDER ---
            Text("Service Provider", style = MaterialTheme.typography.titleSmall)
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = selectedService?.displayName ?: "Choose a service...",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        Icon(Icons.Filled.ArrowDropDown, null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { serviceExpanded = true }
                )
                DropdownMenu(
                    expanded = serviceExpanded,
                    onDismissRequest = { serviceExpanded = false }
                ) {
                    availableServices.forEach { service ->
                        DropdownMenuItem(
                            text = { Text(service.displayName) },
                            onClick = {
                                selectedService = service
                                serviceExpanded = false
                            }
                        )
                    }
                }
            }

            // --- PIN LENGTH ---
            Text("PIN Length", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = pinLength,
                onValueChange = { input ->
                    val clean = input.filter { it.isDigit() }
                    if (clean.length <= 1 && (clean.isEmpty() || clean.toInt() in 4..8)) {
                        pinLength = clean
                    }
                },
                label = { Text("Digits (4–8)") },
                placeholder = { Text("e.g. 4") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.weight(1f))

            // --- SAVE BUTTON ---
            Button(
                onClick = {
                    val service = selectedService!!
                    val length = pinLength.toInt()
                    val fullPhone = "${selectedCountry.code}$personalNumber"

                    val finalConfig = ServiceConfig(
                        id = java.util.UUID.randomUUID().toString(),
                        name = service.rustKey,
                        countryCode = selectedCountry.abbrev,
                        identifier = fullPhone,
                        pinLength = length
                    )

                    val currentServices = VaultsStorage.loadServices(context).toMutableList()
                    currentServices.add(finalConfig)
                    VaultsStorage.saveServices(context, currentServices)
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = personalNumber.isNotBlank() && selectedService != null && pinLength.isNotBlank()
            ) {
                Text("Save Service Link")
            }
        }
    }
}