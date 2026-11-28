package com.sugarcane.purchyflow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sugarcane.purchyflow.data.sync.DiscoveredSmsItem
import com.sugarcane.purchyflow.parser.CaneMessageType
import com.sugarcane.purchyflow.ui.theme.DispatchedTeal
import com.sugarcane.purchyflow.ui.theme.DispatchedTealBg
import com.sugarcane.purchyflow.ui.theme.DispatchedTealBorder
import com.sugarcane.purchyflow.ui.theme.ModernTonalEmerald
import com.sugarcane.purchyflow.ui.theme.ModernTonalMint
import com.sugarcane.purchyflow.ui.theme.SugarcaneGreenLight
import com.sugarcane.purchyflow.ui.theme.SugarcaneGreenPrimary
import com.sugarcane.purchyflow.ui.theme.TextPrimary
import com.sugarcane.purchyflow.ui.theme.TextSecondary
import com.sugarcane.purchyflow.ui.theme.UrgencyRed

enum class SmsFilterTab {
    ALL, NEW, DELETED, SYNCED
}

/**
 * Interactive SMS Selection & Sync Dialog.
 *
 * Scans the device inbox for Sugarcane messages and displays all discovered messages.
 * Allows the user to inspect each message, view its synchronization status (New, Already Synced,
 * or Previously Deleted), filter by category, and select exactly which SMS to sync.
 *
 * Safeguards user deletions: previously deleted messages are UNCHECKED by default and will never
 * sync again unless the user deliberately selects them.
 */
@Composable
fun SmsSyncSelectorDialog(
    items: List<DiscoveredSmsItem>,
    isSyncing: Boolean,
    onToggleSelect: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onSelectNewOnly: () -> Unit,
    onDismiss: () -> Unit,
    onConfirmSync: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf(SmsFilterTab.ALL) }

    val newCount = items.count { !it.isAlreadySynced && !it.isPreviouslyDeleted }
    val deletedCount = items.count { it.isPreviouslyDeleted }
    val syncedCount = items.count { it.isAlreadySynced }
    val selectedCount = items.count { it.isSelected }

    val filteredList = when (selectedFilter) {
        SmsFilterTab.ALL -> items
        SmsFilterTab.NEW -> items.filter { !it.isAlreadySynced && !it.isPreviouslyDeleted }
        SmsFilterTab.DELETED -> items.filter { it.isPreviouslyDeleted }
        SmsFilterTab.SYNCED -> items.filter { it.isAlreadySynced }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ==========================================
                // 1. Header: Title & Close
                // ==========================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(ModernTonalMint),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = ModernTonalEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = "Select Messages to Sync",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                ),
                                color = TextPrimary
                            )
                            Text(
                                text = "${items.size} Cane messages found • $selectedCount selected",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

                // ==========================================
                // 2. Filter Tabs (All, New, Deleted, Synced)
                // ==========================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == SmsFilterTab.ALL,
                        onClick = { selectedFilter = SmsFilterTab.ALL },
                        label = { Text("All (${items.size})", fontSize = 11.5.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ModernTonalEmerald,
                            selectedLabelColor = Color.White
                        )
                    )
                    FilterChip(
                        selected = selectedFilter == SmsFilterTab.NEW,
                        onClick = { selectedFilter = SmsFilterTab.NEW },
                        label = { Text("New ($newCount)", fontSize = 11.5.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF2563EB),
                            selectedLabelColor = Color.White
                        )
                    )
                    FilterChip(
                        selected = selectedFilter == SmsFilterTab.DELETED,
                        onClick = { selectedFilter = SmsFilterTab.DELETED },
                        label = { Text("Deleted ($deletedCount)", fontSize = 11.5.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFE11D48),
                            selectedLabelColor = Color.White
                        )
                    )
                    FilterChip(
                        selected = selectedFilter == SmsFilterTab.SYNCED,
                        onClick = { selectedFilter = SmsFilterTab.SYNCED },
                        label = { Text("Synced ($syncedCount)", fontSize = 11.5.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DispatchedTeal,
                            selectedLabelColor = Color.White
                        )
                    )
                }

                // Quick Selection Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { onSelectAll(true) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Select All", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = ModernTonalEmerald)
                        }

                        TextButton(
                            onClick = onSelectNewOnly,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("New Only", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2563EB))
                        }

                        TextButton(
                            onClick = { onSelectAll(false) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Clear", fontSize = 11.5.sp, color = Color(0xFF64748B))
                        }
                    }

                    Text(
                        text = "$selectedCount of ${items.size} selected",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedCount > 0) ModernTonalEmerald else Color(0xFF94A3B8)
                    )
                }

                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                // ==========================================
                // 3. Scrollable List of Discovered SMS Cards
                // ==========================================
                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No messages match the current filter",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredList, key = { it.smsId }) { item ->
                            DiscoveredSmsCard(
                                item = item,
                                onToggle = { onToggleSelect(item.smsId) }
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

                // ==========================================
                // 4. Bottom Action Bar: Cancel & Sync Selected
                // ==========================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text("Cancel", fontSize = 13.5.sp, color = Color(0xFF64748B))
                    }

                    Button(
                        onClick = onConfirmSync,
                        enabled = selectedCount > 0 && !isSyncing,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ModernTonalEmerald,
                            disabledContainerColor = Color(0xFFCBD5E1)
                        ),
                        modifier = Modifier
                            .height(44.dp)
                            .padding(start = 10.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Syncing...", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sync Selected ($selectedCount)",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveredSmsCard(
    item: DiscoveredSmsItem,
    onToggle: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    val isToken = item.messageType == CaneMessageType.PURCHY_TOKEN
    val cardBg = if (item.isSelected) Color(0xFFF0FDF4) else Color.White
    val borderStrokeColor = when {
        item.isSelected -> ModernTonalEmerald
        item.isPreviouslyDeleted -> Color(0xFFFDA4AF)
        item.isAlreadySynced -> Color(0xFFE2E8F0)
        else -> Color(0xFFCBD5E1)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.2.dp, borderStrokeColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (item.isSelected) 2.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Row 1: Checkbox + Badges + Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = item.isSelected,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = ModernTonalEmerald,
                        uncheckedColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Message Type Badge
                if (isToken) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SugarcaneGreenLight)
                            .border(1.dp, Color(0xFFA5D6A7), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "🎫 TOKEN",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = SugarcaneGreenPrimary
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(DispatchedTealBg)
                            .border(1.dp, DispatchedTealBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "⚖️ WEIGHMENT",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = DispatchedTeal
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Status Badge
                when {
                    item.isPreviouslyDeleted -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFFF1F2))
                                .border(1.dp, Color(0xFFFECDD3), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFE11D48),
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "DELETED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE11D48)
                                )
                            }
                        }
                    }
                    item.isAlreadySynced -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFF1F5F9))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "SYNCED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFEFF6FF))
                                .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "NEW",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2563EB)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "${item.dateStr} • ${item.sender}",
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Row 2: Grower Name & Code
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.growerName.ifEmpty { "GROWER" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (item.growerCode.isNotEmpty()) {
                    Text(
                        text = "Code: ${item.growerCode}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF475569)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Row 3: Ticket / Slip Number + Weight + Mill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val label = if (isToken) "Purchy #${item.purchyOrReceiptNo}" else "Receipt #${item.purchyOrReceiptNo}"
                Text(
                    text = label,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isToken) SugarcaneGreenPrimary else DispatchedTeal
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Scale,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = item.weightStr,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            // Expandable raw SMS snippet
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp, top = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { isExpanded = !isExpanded },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text(
                        text = if (isExpanded) "Hide SMS preview" else "View original SMS",
                        fontSize = 10.5.sp,
                        color = Color(0xFF64748B)
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 32.dp, top = 4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFF8FAFC))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = item.rawBody,
                        fontSize = 11.sp,
                        color = Color(0xFF334155),
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}
