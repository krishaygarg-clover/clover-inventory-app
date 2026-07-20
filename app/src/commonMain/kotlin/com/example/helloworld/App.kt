package com.example.helloworld

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.helloworld.models.FlashDeal
import com.example.helloworld.models.FlashItem
import com.example.helloworld.services.DealService
import com.example.helloworld.services.InventoryService
import com.example.helloworld.services.initiatePayment
import kotlinx.coroutines.launch

@Composable
fun App() {
    val lightColors = lightColors(
        primary = Color(0xFF007A33),
        primaryVariant = Color(0xFF004B1A),
        secondary = Color(0xFF343434),
        background = Color(0xFFEEEEEE)
    )

    MaterialTheme(colors = lightColors) {
        val inventoryService = remember { InventoryService("KBAPSVKBCCTM1", "b157b1e8-42e4-d122-2e33-2a5b142373b7") }
        var items by remember { mutableStateOf(emptyList<FlashItem>()) }
        val scope = rememberCoroutineScope()
        var isLoading by remember { mutableStateOf(false) }
        var showAddDialog by remember { mutableStateOf(false) }
        val scaffoldState = rememberScaffoldState()
        
        val activeDeal by DealService.activeDeal.collectAsState()
        var selectedItemForFlash by remember { mutableStateOf<FlashItem?>(null) }

        fun loadInventory() {
            scope.launch {
                isLoading = true
                items = inventoryService.getInventory()
                isLoading = false
            }
        }

        LaunchedEffect(Unit) {
            loadInventory()
        }

        Scaffold(
            scaffoldState = scaffoldState,
            topBar = {
                TopAppBar(
                    title = { Text("Inventory Manager", modifier = Modifier.padding(start = 8.dp)) },
                    backgroundColor = MaterialTheme.colors.primary,
                    contentColor = Color.White,
                    elevation = 8.dp,
                    actions = {
                        IconButton(onClick = { loadInventory() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                )
            },
            floatingActionButton = {
                if (activeDeal == null) {
                    FloatingActionButton(
                        onClick = { showAddDialog = true },
                        backgroundColor = MaterialTheme.colors.primary,
                        contentColor = Color.White,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Item")
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background).padding(padding), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp) 
                        .padding(horizontal = 16.dp)
                ) {
                    if (isLoading && items.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colors.primary)
                        }
                    } else if (items.isEmpty() && activeDeal == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No items found. Click + to add one.", style = MaterialTheme.typography.h6, color = Color.Gray)
                        }
                    } else {
                        if (activeDeal != null) {
                            CustomerDealView(activeDeal!!, onReset = {
                                DealService.publishDeal(null)
                            })
                        } else {
                            MerchantInventoryView(
                                items = items,
                                onDelete = { item ->
                                    scope.launch {
                                        val success = inventoryService.deleteItem(item.id)
                                        if (success) {
                                            loadInventory()
                                            scaffoldState.snackbarHostState.showSnackbar("Deleted ${item.name}")
                                        } else {
                                            scaffoldState.snackbarHostState.showSnackbar("Error deleting item")
                                        }
                                    }
                                },
                                onFlashClick = { selectedItemForFlash = it }
                            )
                        }
                    }
                }

                if (showAddDialog) {
                    AddItemDialog(
                        onDismiss = { showAddDialog = false },
                        onConfirm = { name, price ->
                            scope.launch {
                                val success = inventoryService.addItem(name, price)
                                if (success) {
                                    loadInventory()
                                    showAddDialog = false
                                    scaffoldState.snackbarHostState.showSnackbar("Item added: $name")
                                } else {
                                    scaffoldState.snackbarHostState.showSnackbar("Failed to add item")
                                }
                            }
                        }
                    )
                }
                
                selectedItemForFlash?.let { item ->
                    FlashPriceDialog(
                        item = item,
                        onDismiss = { selectedItemForFlash = null },
                        onConfirm = { flashPrice ->
                            DealService.publishDeal(
                                FlashDeal(
                                    itemId = item.id,
                                    itemName = item.name,
                                    originalPrice = item.price,
                                    flashPrice = flashPrice,
                                    expiryTimestamp = 0L
                                )
                            )
                            selectedItemForFlash = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MerchantInventoryView(
    items: List<FlashItem>,
    onDelete: (FlashItem) -> Unit,
    onFlashClick: (FlashItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Current Store Inventory",
                style = MaterialTheme.typography.h5,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        items(items) { item ->
            InventoryItemRow(item, onDelete, onFlashClick)
        }
    }
}

@Composable
fun InventoryItemRow(item: FlashItem, onDelete: (FlashItem) -> Unit, onFlashClick: (FlashItem) -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.name, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(text = "$" + (item.price / 100.0).toString(), style = MaterialTheme.typography.subtitle1, color = Color(0xFF007A33))
            }
            Row {
                Button(
                    onClick = { onFlashClick(item) },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF007A33), contentColor = Color.White),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("FLASH")
                }
                IconButton(onClick = { onDelete(item) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFD32F2F))
                }
            }
        }
    }
}

@Composable
fun CustomerDealView(deal: FlashDeal, onReset: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("LIMITED TIME DEAL!", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(16.dp))
        Card(
            shape = RoundedCornerShape(16.dp),
            elevation = 12.dp, 
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(deal.itemName, style = MaterialTheme.typography.h4, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Regular Price: $" + (deal.originalPrice / 100.0).toString(),
                    style = MaterialTheme.typography.h6.copy(textDecoration = TextDecoration.LineThrough),
                    color = Color.Gray
                )
                Text(
                    "FLASH PRICE: $" + (deal.flashPrice / 100.0).toString(),
                    color = Color(0xFF007A33),
                    style = MaterialTheme.typography.h2,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = { initiatePayment(deal.flashPrice) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF007A33), contentColor = Color.White)
                ) {
                    Text("PAY NOW", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onReset) {
             Text("Back to Inventory", color = Color.Gray)
        }
    }
}

@Composable
fun AddItemDialog(onDismiss: () -> Unit, onConfirm: (String, Long) -> Unit) {
    var name by remember { mutableStateOf("") }
    var priceInput by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Add New Product", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.padding(top = 8.dp).width(300.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product Name") },
                    placeholder = { Text("e.g. Latte") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting,
                    singleLine = true
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = priceInput,
                    onValueChange = { if (it.all { char -> char.isDigit() }) priceInput = it },
                    label = { Text("Price (in cents)") },
                    placeholder = { Text("e.g. 450 for $4.50") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val price = priceInput.toLongOrNull()
                    if (name.isNotBlank() && price != null) {
                        isSubmitting = true
                        onConfirm(name, price)
                    }
                },
                enabled = name.isNotBlank() && priceInput.isNotBlank() && !isSubmitting,
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF007A33), contentColor = Color.White)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("ADD ITEM")
                }
            }
        },
        dismissButton = {
            if (!isSubmitting) {
                TextButton(onClick = onDismiss) {
                    Text("CANCEL")
                }
            }
        }
    )
}

@Composable
fun FlashPriceDialog(item: FlashItem, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var priceInput by remember { mutableStateOf((item.price * 0.8).toLong().toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Flash Price for ${item.name}") },
        text = {
            Column(modifier = Modifier.width(300.dp)) {
                Text("Enter the new price in cents:")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = priceInput,
                    onValueChange = { if (it.all { char -> char.isDigit() }) priceInput = it },
                    label = { Text("Price in cents (e.g. 500 for $5.00)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(priceInput.toLongOrNull() ?: 0L) }) {
                Text("START FLASH SALE")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
