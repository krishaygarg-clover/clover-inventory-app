package com.example.helloworld

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlayArrow
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
import com.example.helloworld.services.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun formatPrice(cents: Long): String {
    val dollars = cents / 100
    val remainder = cents % 100
    return "$dollars.${remainder.toString().padStart(2, '0')}"
}

@Composable
fun App(aiService: AIService? = null) {
    val lightColors = lightColors(
        primary = Color(0xFF007A33),
        primaryVariant = Color(0xFF004B1A),
        secondary = Color(0xFF1A1A1A),
        background = Color(0xFFF5F5F5),
        surface = Color.White,
        error = Color(0xFFB00020)
    )

    MaterialTheme(colors = lightColors) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
            val inventoryService = remember { InventoryService(Constants.CLOVER_MERCHANT_ID, Constants.CLOVER_API_TOKEN) }
            val resolvedAiService = remember { aiService ?: AIService() }
            
            var items by remember { mutableStateOf(emptyList<FlashItem>()) }
            var insights by remember { mutableStateOf(emptyList<AIInsight>()) }
            
            val scope = rememberCoroutineScope()
            var isLoading by remember { mutableStateOf(false) }
            var showAddDialog by remember { mutableStateOf(false) }
            val scaffoldState = rememberScaffoldState()

            val activeDeals by DealService.activeDeals.collectAsState()
            var selectedItemForFlash by remember { mutableStateOf<FlashItem?>(null) }
            val itemDescriptions = remember { mutableStateMapOf<String, String>() }

            fun loadInventory() {
                scope.launch {
                    isLoading = true
                    items = inventoryService.getInventory()
                    insights = resolvedAiService.getMerchantInsights(items)
                    
                    // NEW: Generate descriptions for all items in memory
                    items.forEach { item ->
                        if (!itemDescriptions.containsKey(item.id)) {
                            itemDescriptions[item.id] = resolvedAiService.generateDescription(item.name, item.id)
                        }
                    }
                    isLoading = false
                }
            }

            LaunchedEffect(Unit) {
                loadInventory()
            }

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                scaffoldState = scaffoldState,
                topBar = {
                    TopAppBar(
                        title = {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text("Clover Flash", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Inventory & Deals", style = MaterialTheme.typography.caption, color = Color.Gray)
                                Spacer(Modifier.width(12.dp))
                                if (resolvedAiService.isAiReady) {
                                    Surface(
                                        color = Color(0xFF007A33).copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            "AI READY", 
                                            color = Color(0xFF007A33), 
                                            fontSize = 9.sp, 
                                            fontWeight = FontWeight.Black, 
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                } else {
                                    var currentProgress by remember { mutableStateOf(0.0) }
                                    LaunchedEffect(Unit) {
                                        while (true) {
                                            currentProgress = resolvedAiService.aiProgress
                                            delay(500)
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("AI LOADING ${(currentProgress * 100).toInt()}%", style = MaterialTheme.typography.caption, color = Color.Gray, fontSize = 8.sp)
                                        Spacer(Modifier.width(4.dp))
                                        Box(modifier = Modifier.width(60.dp)) {
                                            LinearProgressIndicator(
                                                progress = currentProgress.toFloat(),
                                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                                color = Color(0xFF007A33),
                                                backgroundColor = Color.LightGray.copy(alpha = 0.3f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                        backgroundColor = MaterialTheme.colors.surface,
                        contentColor = MaterialTheme.colors.primary,
                        elevation = 0.dp,
                        actions = {
                            IconButton(onClick = { loadInventory() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            }
                            if (activeDeals.isNotEmpty()) {
                                TextButton(onClick = { DealService.clearAllDeals() }) {
                                    Text("Clear All Deals", color = MaterialTheme.colors.error)
                                }
                            }
                        }
                    )
                },
                floatingActionButton = {
                    if (activeDeals.isEmpty()) {
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.TopCenter
                ) {
                    AnimatedContent(
                        targetState = activeDeals.isNotEmpty(),
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        }
                    ) { showingDeals ->
                        if (showingDeals) {
                            Box(modifier = Modifier.widthIn(max = 1000.dp).fillMaxSize()) {
                                CustomerDealView(activeDeals, onReset = {
                                    DealService.clearAllDeals()
                                }, onRemoveDeal = { 
                                    DealService.removeDeal(it.itemId)
                                })
                            }
                        } else {
                            if (isLoading && items.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = MaterialTheme.colors.primary)
                                }
                            } else if (items.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("No items found. Click + to add one.", style = MaterialTheme.typography.h6, color = Color.Gray)
                                }
                            } else {
                                Box(modifier = Modifier.widthIn(max = 1400.dp).fillMaxSize()) {
                                    MerchantInventoryView(
                                        items = items,
                                        insights = insights,
                                        itemDescriptions = itemDescriptions,
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
                            onConfirm = { flashPrice, durationMinutes ->
                                DealService.publishDeal(
                                    FlashDeal(
                                        itemId = item.id,
                                        itemName = item.name,
                                        originalPrice = item.price,
                                        flashPrice = flashPrice,
                                        expiryTimestamp = (durationMinutes * 60 * 1000).toLong() // Simplified for demo
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
}

@Composable
fun MerchantInventoryView(
    items: List<FlashItem>,
    insights: List<AIInsight>,
    itemDescriptions: Map<String, String>,
    onDelete: (FlashItem) -> Unit,
    onFlashClick: (FlashItem) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 350.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        if (insights.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(
                        "AI Recommendations",
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF007A33),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        insights.forEach { insight ->
                            RecommendationCard(insight, items, onFlashClick)
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    "Store Inventory",
                    style = MaterialTheme.typography.h4,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colors.secondary
                )
                Text(
                    "Manage your products and launch flash deals",
                    style = MaterialTheme.typography.subtitle1,
                    color = Color.Gray
                )
                Spacer(Modifier.height(16.dp))
                Divider(thickness = 1.dp, color = Color.LightGray.copy(alpha = 0.5f))
            }
        }
        items(items) { item ->
            InventoryItemRow(
                item = item,
                description = itemDescriptions[item.id] ?: "Generating description...",
                onDelete = onDelete,
                onFlashClick = onFlashClick
            )
        }
    }
}

@Composable
fun RecommendationCard(insight: AIInsight, items: List<FlashItem>, onFlashClick: (FlashItem) -> Unit) {
    val item = items.find { it.id == insight.suggestedItemId }
    val combo = insight.suggestedCombo
    
    Card(
        modifier = Modifier.width(300.dp),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = Color(0xFF007A33).copy(alpha = 0.05f),
        elevation = 0.dp,
        border = BorderStroke(1.dp, Color(0xFF007A33).copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (combo != null) Color(0xFF6200EE) else Color(0xFF007A33),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        if (combo != null) "COMBO" else insight.type.name,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(insight.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.subtitle1)
            }
            Spacer(Modifier.height(8.dp))
            Text(insight.description, style = MaterialTheme.typography.body2, color = Color.DarkGray)
            
            if (item != null || combo != null) {
                Spacer(Modifier.height(12.dp))
                Divider(color = Color(0xFF007A33).copy(alpha = 0.1f))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        val suggestedPrice = combo?.bundlePrice ?: (item!!.price * (1 - insight.suggestedDiscount)).toLong()
                        Text(combo?.name ?: item!!.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "Deal: $" + formatPrice(suggestedPrice),
                            color = Color(0xFF007A33),
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                    Button(
                        onClick = { 
                            if (combo != null) {
                                DealService.publishDeal(
                                    FlashDeal(
                                        itemId = combo.id,
                                        itemName = combo.name,
                                        originalPrice = items.filter { it.id in combo.itemIds }.sumOf { it.price },
                                        flashPrice = combo.bundlePrice,
                                        expiryTimestamp = 600000L,
                                        description = combo.description,
                                        isCombo = true
                                    )
                                )
                            } else {
                                onFlashClick(item!!)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (combo != null) Color(0xFF6200EE) else Color(0xFF007A33),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = ButtonDefaults.elevation(defaultElevation = 2.dp, pressedElevation = 0.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("APPLY", fontSize = 13.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun InventoryItemRow(
    item: FlashItem, 
    description: String,
    onDelete: (FlashItem) -> Unit, 
    onFlashClick: (FlashItem) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = 0.dp,
        backgroundColor = MaterialTheme.colors.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colors.secondary
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$" + formatPrice(item.price),
                    style = MaterialTheme.typography.body1,
                    color = Color(0xFF007A33),
                    fontWeight = FontWeight.Medium
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { onFlashClick(item) },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF007A33).copy(alpha = 0.12f), 
                        contentColor = Color(0xFF007A33)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.elevation(0.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("FLASH", fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
                Spacer(Modifier.width(12.dp))
                IconButton(onClick = { onDelete(item) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.Gray.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CustomerDealView(deals: List<FlashDeal>, onReset: () -> Unit, onRemoveDeal: (FlashDeal) -> Unit) {
    var timeLeft by remember { mutableStateOf(600) } // 10 minutes demo

    LaunchedEffect(Unit) {
        while (timeLeft > 0) {
            delay(1000)
            timeLeft--
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color.Red,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "FLASH SALES ACTIVE",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Text(
                "Ends in ${timeLeft / 60}:${(timeLeft % 60).toString().padStart(2, '0')}",
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.secondary
            )
        }

        Spacer(Modifier.height(24.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(deals) { deal ->
                DealCard(deal, onRemove = { onRemoveDeal(deal) })
            }
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onReset) {
            Text("Clear All and Return to Inventory", color = Color.Gray)
        }
    }
}

@Composable
fun DealCard(deal: FlashDeal, onRemove: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        elevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(deal.itemName, style = MaterialTheme.typography.h4, fontWeight = FontWeight.Black)
                    if (deal.description.isNotEmpty()) {
                        Text(deal.description, style = MaterialTheme.typography.body1, color = Color.Gray)
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove Deal", tint = Color.LightGray)
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Original: $" + formatPrice(deal.originalPrice),
                        style = MaterialTheme.typography.h6.copy(textDecoration = TextDecoration.LineThrough),
                        color = Color.Gray
                    )
                    Text(
                        "$" + formatPrice(deal.flashPrice),
                        color = Color(0xFF007A33),
                        style = MaterialTheme.typography.h3,
                        fontWeight = FontWeight.Black
                    )
                }
                
                Button(
                    onClick = { initiatePayment(deal.flashPrice) },
                    modifier = Modifier.height(56.dp).width(160.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF007A33), contentColor = Color.White)
                ) {
                    Text("PAY NOW", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
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
fun FlashPriceDialog(item: FlashItem, onDismiss: () -> Unit, onConfirm: (Long, Int) -> Unit) {
    var priceInput by remember { mutableStateOf((item.price * 0.8).toLong().toString()) }
    var durationMinutes by remember { mutableStateOf("10") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure Flash Sale: ${item.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.width(300.dp)) {
                Text("Set the discount price and how long it should last.")
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = priceInput,
                    onValueChange = { if (it.all { char -> char.isDigit() }) priceInput = it },
                    label = { Text("Flash Price (cents)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = durationMinutes,
                    onValueChange = { if (it.all { char -> char.isDigit() }) durationMinutes = it },
                    label = { Text("Duration (minutes)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        priceInput.toLongOrNull() ?: 0L,
                        durationMinutes.toIntOrNull() ?: 10
                    )
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF007A33), contentColor = Color.White)
            ) {
                Text("GO LIVE!")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL")
            }
        }
    )
}
