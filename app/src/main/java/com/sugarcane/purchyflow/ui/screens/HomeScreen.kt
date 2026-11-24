package com.sugarcane.purchyflow.ui.screens

import android.net.Uri
import com.sugarcane.purchyflow.data.model.TokenStatus
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sugarcane.purchyflow.R
import com.sugarcane.purchyflow.data.model.PurchyToken
import com.sugarcane.purchyflow.ui.components.AddActionChooserDialog
import com.sugarcane.purchyflow.ui.components.ExportFormatDialog
import com.sugarcane.purchyflow.ui.components.ImportDataDialog
import com.sugarcane.purchyflow.ui.components.ManualAddDialog
import com.sugarcane.purchyflow.ui.components.PermissionBanner
import com.sugarcane.purchyflow.ui.components.SmsPasteDialog
import com.sugarcane.purchyflow.ui.components.SmsSyncSelectorDialog
import com.sugarcane.purchyflow.ui.components.TokenCard
import com.sugarcane.purchyflow.ui.theme.BackgroundSoft
import com.sugarcane.purchyflow.ui.theme.GoldAccent
import com.sugarcane.purchyflow.ui.theme.HeaderGradientStart
import com.sugarcane.purchyflow.ui.theme.ModernTonalEmerald
import com.sugarcane.purchyflow.ui.theme.ModernTonalMint
import com.sugarcane.purchyflow.ui.theme.ModernTonalSky
import com.sugarcane.purchyflow.ui.theme.ModernTonalTeal
import com.sugarcane.purchyflow.ui.theme.SugarcaneGreenPrimary
import com.sugarcane.purchyflow.ui.theme.TextPrimary
import com.sugarcane.purchyflow.ui.theme.TextSecondary
import com.sugarcane.purchyflow.ui.theme.UrgencyRed
import com.sugarcane.purchyflow.ui.theme.WhatsAppBackgroundSoft
import com.sugarcane.purchyflow.ui.theme.WhatsAppBadgeGreen
import com.sugarcane.purchyflow.ui.theme.WhatsAppDivider
import com.sugarcane.purchyflow.ui.theme.WhatsAppGreen
import com.sugarcane.purchyflow.ui.theme.WhatsAppGreenDark
import com.sugarcane.purchyflow.ui.theme.WhatsAppTeal
import com.sugarcane.purchyflow.ui.theme.WhatsAppTextPrimary
import com.sugarcane.purchyflow.ui.theme.WhatsAppTextSecondary
import com.sugarcane.purchyflow.ui.viewmodel.PurchyViewModel
import com.sugarcane.purchyflow.util.DateUtil
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: PurchyViewModel,
    onRequestSmsPermission: () -> Unit = {},
    onOpenWelcome: () -> Unit = {}
) {
    val context = LocalContext.current
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val selectedFilter by viewModel.selectedFilterChip.collectAsState()
    val selectedDispatchedFilter by viewModel.selectedDispatchedFilterChip.collectAsState()
    val isPermissionGranted by viewModel.isPermissionGranted.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val showExportDialog by viewModel.showExportDialog.collectAsState()

    var showActionChooserDialog by remember { mutableStateOf(false) }
    var showManualAddDialog by remember { mutableStateOf(false) }
    var showSmsPasteDialog by remember { mutableStateOf(false) }
    var showImportDataDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showWelcomeDialog by remember { mutableStateOf(false) }

    val activeTokens by viewModel.activeTokens.collectAsState()
    val dispatchedTokens by viewModel.dispatchedTokens.collectAsState()
    val auditTokens by viewModel.auditTokens.collectAsState()
    val dispatchedGrowerNames by viewModel.dispatchedGrowerNames.collectAsState()

    val showSmsSyncSelector by viewModel.showSmsSyncSelector.collectAsState()
    val discoveredSmsList by viewModel.discoveredSmsList.collectAsState()
    val isScanningInbox by viewModel.isScanningInbox.collectAsState()

    // File picker for importing Excel (.xlsx) / CSV (.csv) sheets directly from TopAppBar
    val excelFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importExcelFile(uri, context)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkPermissions(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Official Brand Emblem Badge
                        Image(
                            painter = painterResource(id = R.drawable.ic_purchyflow_logo),
                            contentDescription = "PurchyFlow Logo",
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = "PurchyFlow",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 19.5.sp
                                )
                            )
                            Text(
                                text = "Sugarcane Grower & Mill Dispatch",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFFB2DFDB),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            )
                        }
                    }
                },
                actions = {
                    // Prominent Total Weight Counter in Top Right
                    val totalDispWeight = dispatchedTokens.sumOf { it.actualNettedWeightNumber ?: 0.0 }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF064E3B),
                        border = BorderStroke(1.2.dp, Color(0xFF34D399)),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Scale,
                                contentDescription = "Total Net Weight",
                                tint = Color(0xFF6EE7B7),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = String.format(Locale.ENGLISH, "%.1f", totalDispWeight),
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 0.4.sp
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "ql",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFA7F3D0)
                            )
                        }
                    }

                    // App Menu Bar (Sync, Export, Import, Guide)
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            modifier = Modifier
                                .background(Color.White)
                                .clip(RoundedCornerShape(16.dp))
                        ) {
                            // 1. Sync Season SMS
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isSyncing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = SugarcaneGreenPrimary
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Sync,
                                                contentDescription = null,
                                                tint = ModernTonalEmerald,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text("Sync Season SMS", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                                            Text("Scan CANEDEPTT messages", fontSize = 11.sp, color = TextSecondary)
                                        }
                                    }
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.openSmsSyncSelector(context)
                                }
                            )

                            HorizontalDivider(color = Color(0xFFF1F5F9))

                            // 2. Import Data (Excel / CSV)
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isImporting) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = ModernTonalTeal
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.UploadFile,
                                                contentDescription = null,
                                                tint = ModernTonalTeal,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text("Import Data", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                                            Text("Load Excel (.xlsx) / CSV file", fontSize = 11.sp, color = TextSecondary)
                                        }
                                    }
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    excelFilePicker.launch("*/*")
                                }
                            )

                            HorizontalDivider(color = Color(0xFFF1F5F9))

                            // 3. Export Records (Excel / PDF)
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isExporting) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = ModernTonalSky
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.FileDownload,
                                                contentDescription = null,
                                                tint = ModernTonalSky,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text("Export Records", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                                            Text("Generate Excel & PDF report", fontSize = 11.sp, color = TextSecondary)
                                        }
                                    }
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.setExportDialogVisible(true)
                                }
                            )

                            HorizontalDivider(color = Color(0xFFF1F5F9))

                            // 4. Welcome & Guide
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = Color(0xFF64748B),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text("Welcome & Guide", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                                            Text("v${com.sugarcane.purchyflow.BuildConfig.VERSION_NAME} • Features & guide", fontSize = 11.sp, color = TextSecondary)
                                        }
                                    }
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    showWelcomeDialog = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = WhatsAppGreen
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showActionChooserDialog = true },
                containerColor = WhatsAppTeal,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Purchy or Weighment Data",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        containerColor = Color.White
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ==========================================
            // ==========================================
            // Dashboard Clean Navigation Tabs (Active, Dispatched, Pending Audit)
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WhatsAppGreen)
            ) {
                // Tab 0: CANE TOKENS
                val isTokensSelected = selectedTab == 0
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.onTabSelected(0) }
                        .padding(top = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "TOKEN",
                            fontSize = 13.5.sp,
                            fontWeight = if (isTokensSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isTokensSelected) Color.White else Color(0xCCFFFFFF),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isTokensSelected) Color.White else Color(0x33FFFFFF))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${activeTokens.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isTokensSelected) WhatsAppGreen else Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.5.dp)
                            .background(if (isTokensSelected) Color.White else Color.Transparent)
                    )
                }

                // Tab 1: DISPATCHED RECEIPTS
                val isDispatchedSelected = selectedTab == 1
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .clickable { viewModel.onTabSelected(1) }
                        .padding(top = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "DISPATCHED",
                            fontSize = 13.5.sp,
                            fontWeight = if (isDispatchedSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isDispatchedSelected) Color.White else Color(0xCCFFFFFF),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDispatchedSelected) Color.White else Color(0x33FFFFFF))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${dispatchedTokens.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDispatchedSelected) WhatsAppGreen else Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.5.dp)
                            .background(if (isDispatchedSelected) Color.White else Color.Transparent)
                    )
                }

                // Tab 2: PENDING WEIGHMENT AUDIT
                val isAuditSelected = selectedTab == 2
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.onTabSelected(2) }
                        .padding(top = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "AUDIT",
                            fontSize = 13.5.sp,
                            fontWeight = if (isAuditSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isAuditSelected) Color.White else Color(0xCCFFFFFF),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (auditTokens.isNotEmpty()) Color(0xFFE53935)
                                    else if (isAuditSelected) Color.White else Color(0x33FFFFFF)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${auditTokens.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (auditTokens.isNotEmpty()) Color.White
                                else if (isAuditSelected) WhatsAppGreen else Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.5.dp)
                            .background(if (isAuditSelected) Color.White else Color.Transparent)
                    )
                }
            }

            // Permission Banner
            PermissionBanner(
                isPermissionGranted = isPermissionGranted,
                onGrantClick = onRequestSmsPermission
            )

            // WhatsApp Search Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WhatsAppBackgroundSoft)
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .border(1.dp, WhatsAppDivider, RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = WhatsAppTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search by Grower, Purchy No, Centre...",
                                    fontSize = 13.sp,
                                    color = Color(0xFF8696A0),
                                    maxLines = 1
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.onSearchQueryChanged(it) },
                                singleLine = true,
                                maxLines = 1,
                                textStyle = TextStyle(
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = WhatsAppTextPrimary
                                ),
                                cursorBrush = SolidColor(WhatsAppGreen),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = WhatsAppTextSecondary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { viewModel.onSearchQueryChanged("") }
                            )
                        }
                    }
                }
            }

            // WhatsApp Filter Chips (shown on Active Valid Tokens tab)
            AnimatedVisibility(visible = selectedTab == 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .background(Color.White)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == "ALL",
                        onClick = { viewModel.onFilterChipSelected("ALL") },
                        label = { Text("All Tokens", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WhatsAppGreen,
                            selectedLabelColor = Color.White
                        )
                    )

                    FilterChip(
                        selected = selectedFilter == "TODAY",
                        onClick = { viewModel.onFilterChipSelected("TODAY") },
                        label = { Text("🔥 Expiring Today", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = UrgencyRed,
                            selectedLabelColor = Color.White
                        )
                    )

                    FilterChip(
                        selected = selectedFilter == "EXPIRING_SOON",
                        onClick = { viewModel.onFilterChipSelected("EXPIRING_SOON") },
                        label = { Text("⏳ 1-2 Days Left", fontSize = 12.sp, fontWeight = FontWeight.Medium) }
                    )

                    FilterChip(
                        selected = selectedFilter == "45QL_PLUS",
                        onClick = { viewModel.onFilterChipSelected("45QL_PLUS") },
                        label = { Text("📦 Heavy (≥45ql)", fontSize = 12.sp, fontWeight = FontWeight.Medium) }
                    )
                }
            }

            // Filter Chips for Dispatched Receipts tab
            AnimatedVisibility(visible = selectedTab == 1) {
                var showGrowerSortMenu by remember { mutableStateOf(false) }
                val isGrowerActive = selectedDispatchedFilter.startsWith("GROWER:") || selectedDispatchedFilter == "SORT_NAME"
                val growerLabel = when {
                    selectedDispatchedFilter.startsWith("GROWER:") -> {
                        val g = selectedDispatchedFilter.removePrefix("GROWER:").trim()
                        if (g.length > 14) g.take(12) + "…" else g
                    }
                    selectedDispatchedFilter == "SORT_NAME" -> "A-Z (Name)"
                    else -> "Sort by Grower"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .background(Color.White)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedDispatchedFilter == "ALL",
                        onClick = { viewModel.onDispatchedFilterChipSelected("ALL") },
                        label = { Text("All Dispatched", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WhatsAppGreen,
                            selectedLabelColor = Color.White
                        )
                    )

                    FilterChip(
                        selected = selectedDispatchedFilter == "TODAY",
                        onClick = { viewModel.onDispatchedFilterChipSelected("TODAY") },
                        label = { Text("📅 Today / Recent", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WhatsAppGreen,
                            selectedLabelColor = Color.White
                        )
                    )

                    // Sort by Grower Dropdown Tab
                    Box {
                        FilterChip(
                            selected = isGrowerActive,
                            onClick = { showGrowerSortMenu = true },
                            label = { Text("👤 $growerLabel", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Select Grower",
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = WhatsAppGreen,
                                selectedLabelColor = Color.White
                            )
                        )

                        DropdownMenu(
                            expanded = showGrowerSortMenu,
                            onDismissRequest = { showGrowerSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Sort All by Name (A to Z)", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                                onClick = {
                                    viewModel.onDispatchedFilterChipSelected("SORT_NAME")
                                    showGrowerSortMenu = false
                                }
                            )
                            if (dispatchedGrowerNames.isNotEmpty()) {
                                HorizontalDivider(color = Color(0xFFF1F5F9))
                            }
                            dispatchedGrowerNames.forEach { name ->
                                DropdownMenuItem(
                                    text = { Text(name, fontSize = 13.sp) },
                                    onClick = {
                                        viewModel.onDispatchedFilterChipSelected("GROWER:$name")
                                        showGrowerSortMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Individual Quick Filter Chips for each Grower
                    dispatchedGrowerNames.forEach { grower ->
                        val isSelected = selectedDispatchedFilter == "GROWER:$grower"
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) {
                                    viewModel.onDispatchedFilterChipSelected("ALL")
                                } else {
                                    viewModel.onDispatchedFilterChipSelected("GROWER:$grower")
                                }
                            },
                            label = {
                                Text(
                                    text = grower,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = WhatsAppGreen,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // Token List / WhatsApp Empty State
            val currentList = when (selectedTab) {
                0 -> activeTokens
                1 -> dispatchedTokens
                else -> auditTokens
            }

            if (currentList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Official Emblem Illustration
                        Image(
                            painter = painterResource(id = R.drawable.ic_purchyflow_logo),
                            contentDescription = "PurchyFlow Emblem",
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = when (selectedTab) {
                                0 -> "No Cane Tokens Available"
                                1 -> "No Dispatched Mill Receipts Yet"
                                else -> "No Pending Audits"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            ),
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = when (selectedTab) {
                                0 -> "Incoming sugarcane token messages from CANEDEPTT will automatically appear here as tokens for dispatch."
                                1 -> "Once sugarcane is dispatched and weighed in the mill, weighbridge receipts and compiled weights appear here."
                                else -> "All expired tokens have been successfully reconciled or weighed! Unfulfilled tokens past their 2-day validity will appear here for manual verification."
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            ),
                            color = Color(0xFF64748B),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Quick Action Button
                        Button(
                            onClick = {
                                if (selectedTab == 0 || selectedTab == 2) {
                                    viewModel.openSmsSyncSelector(context)
                                } else {
                                    showActionChooserDialog = true
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (selectedTab == 2) Color(0xFFE53935) else SugarcaneGreenPrimary),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = if (selectedTab == 0 || selectedTab == 2) Icons.Default.Sync else Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedTab == 0) "Sync Season Tokens" else if (selectedTab == 2) "Check Inbox for Weighments" else "Add Manual Entry",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 2.dp, bottom = 84.dp)
                ) {
                    if (selectedTab == 2) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F2)),
                                border = BorderStroke(1.dp, Color(0xFFFDA4AF)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFE11D48),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Pending Weighment Audit (${auditTokens.size} Unmatched)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            color = Color(0xFF9F1239)
                                        )
                                        Text(
                                            text = "These tokens have exceeded their 2-day validity period without a matched mill weighment. Review or reconcile manually to prevent financial discrepancies.",
                                            fontSize = 11.5.sp,
                                            color = Color(0xFFBE123C),
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    items(currentList, key = { it.purchyNo }) { token ->
                        TokenCard(
                            token = token,
                            onDelete = { viewModel.deleteToken(token.purchyNo, context) },
                            onUnlink = { viewModel.unlinkToken(token.purchyNo, context) }
                        )
                    }
                }
            }
        }
    }

    // Export Options Dialog (Excel vs PDF)
    if (showExportDialog) {
        ExportFormatDialog(
            dispatchedCount = dispatchedTokens.size,
            totalDispatchedWeight = dispatchedTokens.sumOf { it.actualNettedWeightNumber ?: 0.0 },
            onDismiss = { viewModel.setExportDialogVisible(false) },
            onExportExcel = { viewModel.exportDispatchedToExcel(context) },
            onExportPdf = { viewModel.exportDispatchedToPdf(context) }
        )
    }

    // 3 Dedicated Windows + Action Chooser (Targeted to Active Tab)
    if (showActionChooserDialog) {
        AddActionChooserDialog(
            targetTab = selectedTab,
            onDismiss = { showActionChooserDialog = false },
            onChooseManual = {
                showActionChooserDialog = false
                showManualAddDialog = true
            },
            onChooseSms = {
                showActionChooserDialog = false
                showSmsPasteDialog = true
            },
            onChooseImport = {
                showActionChooserDialog = false
                showImportDataDialog = true
            }
        )
    }

    if (showManualAddDialog) {
        ManualAddDialog(
            viewModel = viewModel,
            targetTab = selectedTab,
            onDismiss = { showManualAddDialog = false }
        )
    }

    if (showSmsPasteDialog) {
        SmsPasteDialog(
            viewModel = viewModel,
            targetTab = selectedTab,
            onDismiss = { showSmsPasteDialog = false }
        )
    }

    if (showImportDataDialog) {
        ImportDataDialog(
            viewModel = viewModel,
            onDismiss = { showImportDataDialog = false }
        )
    }

    // Welcome Page & Permissions Guide Modal
    if (showWelcomeDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showWelcomeDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            com.sugarcane.purchyflow.ui.screens.WelcomeScreen(
                isPermissionGranted = isPermissionGranted,
                onRequestPermission = onRequestSmsPermission,
                onContinueToHome = { showWelcomeDialog = false },
                onDismiss = { showWelcomeDialog = false }
            )
        }
    }

    // Interactive Selective SMS Sync Dialog
    if (showSmsSyncSelector) {
        SmsSyncSelectorDialog(
            items = discoveredSmsList,
            isSyncing = isSyncing,
            onToggleSelect = { smsId -> viewModel.toggleSmsSelection(smsId) },
            onSelectAll = { select -> viewModel.selectAllSms(select) },
            onSelectNewOnly = { viewModel.selectNewOnly() },
            onDismiss = { viewModel.dismissSmsSyncSelector() },
            onConfirmSync = { viewModel.syncSelectedMessages(context) }
        )
    }

    // Scanning SMS Indicator
    if (isScanningInbox) {
        androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                tonalElevation = 6.dp,
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = ModernTonalEmerald,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Scanning SMS inbox for messages...",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}
