package com.sugarcane.purchyflow.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sugarcane.purchyflow.data.model.PurchyToken
import com.sugarcane.purchyflow.data.model.TokenStatus
import com.sugarcane.purchyflow.data.model.UrgencyLevel
import com.sugarcane.purchyflow.ui.theme.DispatchedTeal
import com.sugarcane.purchyflow.ui.theme.DispatchedTealBg
import com.sugarcane.purchyflow.ui.theme.DispatchedTealBorder
import com.sugarcane.purchyflow.ui.theme.SugarcaneGreenLight
import com.sugarcane.purchyflow.ui.theme.SugarcaneGreenPrimary
import com.sugarcane.purchyflow.ui.theme.TextPrimary
import com.sugarcane.purchyflow.ui.theme.TextSecondary
import com.sugarcane.purchyflow.ui.theme.TextTertiary
import com.sugarcane.purchyflow.ui.theme.UrgencyAmber
import com.sugarcane.purchyflow.ui.theme.UrgencyAmberBg
import com.sugarcane.purchyflow.ui.theme.UrgencyAmberBorder
import com.sugarcane.purchyflow.ui.theme.UrgencyGreen
import com.sugarcane.purchyflow.ui.theme.UrgencyGreenBg
import com.sugarcane.purchyflow.ui.theme.UrgencyGreenBorder
import com.sugarcane.purchyflow.ui.theme.UrgencyGrey
import com.sugarcane.purchyflow.ui.theme.UrgencyGreyBg
import com.sugarcane.purchyflow.ui.theme.UrgencyGreyBorder
import com.sugarcane.purchyflow.ui.theme.UrgencyRed
import com.sugarcane.purchyflow.ui.theme.UrgencyRedBg
import com.sugarcane.purchyflow.ui.theme.UrgencyRedBorder
import com.sugarcane.purchyflow.ui.theme.WhatsAppBlueTicks
import com.sugarcane.purchyflow.ui.theme.WhatsAppDivider
import com.sugarcane.purchyflow.ui.theme.WhatsAppGreen
import com.sugarcane.purchyflow.ui.theme.WhatsAppTextPrimary
import com.sugarcane.purchyflow.ui.theme.WhatsAppTextSecondary
import com.sugarcane.purchyflow.util.DateUtil

/**
 * WhatsApp-Inspired Sugarcane Token & Mill Weighment Item:
 * - Familiar WhatsApp chat list row structure
 * - Left: Circular contact avatar with grower initials & status ring
 * - Center: Full-width Grower Name (Line 1) + Double ticks & weight/receipt summary (Line 2)
 * - Right: Single-line Date/Timestamp (Line 1) + Urgency/Weight Badge (Line 2)
 * - Smooth animated accordion expand for detailed micro-cards (UGC, code, mill, SMS copy, delete)
 */
@Composable
fun TokenCard(
    token: PurchyToken,
    onDelete: () -> Unit = {},
    onUnlink: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDispatched = token.status == TokenStatus.DISPATCHED
    val isLinkedWithToken = isDispatched && 
            !token.actualNettedWeight.isNullOrBlank() && 
            (!token.allottedWeight.isNullOrBlank() && token.allottedWeight != token.actualNettedWeight || 
             !token.weightReceiptNo.isNullOrBlank() && token.weightReceiptNo != token.purchyNo)
    val urgencyLevel = DateUtil.getUrgencyLevel(token.validUntilEpochMs)
    val urgencyText = DateUtil.getUrgencyBadgeText(token.validUntilEpochMs)

    var isExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showUnlinkDialog by remember { mutableStateOf(false) }

    // Clean grower name (normalize slashes with spaces, remove trailing slashes/dashes/spaces)
    val cleanGrowerName = token.growerName
        .replace(Regex("/+"), " / ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .trimEnd('/', ' ', '-')
        .ifEmpty { "GROWER" }

    // Generate 2-letter initials for WhatsApp avatar
    val initials = cleanGrowerName.split(" ", "/")
        .filter { it.isNotBlank() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
        .ifEmpty { "G" }

    // WhatsApp style chevron rotation
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "chevronRotation"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .clickable { isExpanded = !isExpanded }
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ==========================================
            // WhatsApp Contact Avatar (48dp circle)
            // ==========================================
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        if (isDispatched) Color(0xFFE0F2F1) else Color(0xFFE7F7EF)
                    )
                    .border(
                        1.2.dp,
                        if (isDispatched) DispatchedTeal.copy(alpha = 0.4f) else Color(0xFF25D366).copy(alpha = 0.4f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDispatched) DispatchedTeal else Color(0xFF075E54)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // ==========================================
            // Center & Right Columns (WhatsApp Message Row)
            // ==========================================
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // Line 1: Grower Name (Left) + Date / Timestamp (Right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = cleanGrowerName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = WhatsAppTextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Date / Timestamp (WhatsApp timestamp style)
                    Text(
                        text = if (isDispatched) {
                            token.dispatchDateStr ?: token.issueDateStr
                        } else {
                            token.issueDateStr
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = WhatsAppTextSecondary,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                // Line 2: WhatsApp Double Checkmarks + Subtext (Left) + Badge / Chevron (Right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Subtext
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isDispatched) {
                            val cleanReceipt = token.weightReceiptNo
                                ?.replace(Regex("^(?:#?MILL[-\\s]*|#)", RegexOption.IGNORE_CASE), "")
                                ?.trim() ?: token.purchyNo
                            val codePart = if (token.growerCode.isNotBlank()) " • ${token.growerCode}" else ""
                            if (isLinkedWithToken) {
                                Text(
                                    text = "Purchy #${token.purchyNo} • Mill #${cleanReceipt}",
                                    fontSize = 13.sp,
                                    color = WhatsAppTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else if (!token.purchyNo.startsWith("TAUL-") && !token.purchyNo.startsWith("REC-")) {
                                Text(
                                    text = "Purchy #${token.purchyNo}$codePart",
                                    fontSize = 13.sp,
                                    color = WhatsAppTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Text(
                                    text = "Mill #${cleanReceipt}$codePart",
                                    fontSize = 13.sp,
                                    color = WhatsAppTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            val codePart = if (token.growerCode.isNotBlank()) " • ${token.growerCode}" else ""
                            Text(
                                text = "Purchy #${token.purchyNo}$codePart",
                                fontSize = 13.sp,
                                color = WhatsAppTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Right Badge / Status Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isDispatched) {
                            val displayWeight = token.actualNettedWeight ?: "0 ql"
                            val cleanWeight = if (displayWeight.contains("Net", ignoreCase = true)) displayWeight else "Net: $displayWeight"
                            ModernBadge(
                                text = cleanWeight,
                                textColor = DispatchedTeal,
                                bgColor = DispatchedTealBg,
                                borderColor = DispatchedTealBorder,
                                icon = Icons.Default.CheckCircle
                            )
                        } else {
                            when (urgencyLevel) {
                                UrgencyLevel.EXPIRING_TODAY -> ModernBadge(
                                    text = urgencyText,
                                    textColor = UrgencyRed,
                                    bgColor = UrgencyRedBg,
                                    borderColor = UrgencyRedBorder,
                                    icon = Icons.Default.AccessTime
                                )
                                UrgencyLevel.ONE_DAY_LEFT -> ModernBadge(
                                    text = urgencyText,
                                    textColor = UrgencyAmber,
                                    bgColor = UrgencyAmberBg,
                                    borderColor = UrgencyAmberBorder,
                                    icon = Icons.Default.AccessTime
                                )
                                UrgencyLevel.TWO_DAYS_LEFT -> ModernBadge(
                                    text = urgencyText,
                                    textColor = UrgencyGreen,
                                    bgColor = UrgencyGreenBg,
                                    borderColor = UrgencyGreenBorder,
                                    icon = Icons.Default.AccessTime
                                )
                                UrgencyLevel.EXPIRED -> ModernBadge(
                                    text = "Expired",
                                    textColor = UrgencyGrey,
                                    bgColor = UrgencyGreyBg,
                                    borderColor = UrgencyGreyBorder,
                                    icon = Icons.Default.AccessTime
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand details",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier
                                .size(17.dp)
                                .rotate(chevronRotation)
                        )
                    }
                }
            }
        }

                // ==========================================
                // EXPANDED DETAILS (Reveals on tap)
                // ==========================================
                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(8.dp))

                        if (isLinkedWithToken) {
                            // ==========================================
                            // SEPARATED VIEW: 1. CANE TOKEN DETAILS
                            // ==========================================
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFF0FDF4))
                                    .border(1.dp, Color(0xFFBBF7D0), RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(SugarcaneGreenLight)
                                                .border(1.dp, Color(0xFFA5D6A7), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "🎫 CANE TOKEN",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = SugarcaneGreenPrimary
                                            )
                                        }
                                        Text(
                                            text = "Issued: ${token.issueDateStr}",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextSecondary
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable {
                                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    cm.setPrimaryClip(ClipData.newPlainText("Purchy No", token.purchyNo))
                                                    Toast.makeText(context, "Copied Purchy: ${token.purchyNo}", Toast.LENGTH_SHORT).show()
                                                }
                                        ) {
                                            Text(
                                                text = "Purchy #${token.purchyNo}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = SugarcaneGreenPrimary
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy",
                                                tint = SugarcaneGreenPrimary,
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }

                                        Text(
                                            text = "Allotted: ${token.allottedWeight}",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Code: ${token.growerCode} • ${token.caneType.ifEmpty { "GENERAL" }} (${token.fc.ifEmpty { "-" }})",
                                            fontSize = 11.5.sp,
                                            color = TextSecondary
                                        )
                                        Text(
                                            text = "${token.society} (${token.centre})",
                                            fontSize = 11.5.sp,
                                            color = TextSecondary
                                        )
                                    }

                                    if (token.ugc.isNotBlank()) {
                                        Text(
                                            text = "UGC: ${token.ugc}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF6D28D9)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Linked Connection Divider
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    tint = DispatchedTeal,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "CORRELATED MILL WEIGHMENT",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DispatchedTeal,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // ==========================================
                            // SEPARATED VIEW: 2. MILL WEIGHMENT DETAILS
                            // ==========================================
                            val cleanReceipt = token.weightReceiptNo
                                ?.replace(Regex("^(?:#?MILL[-\\s]*|#)", RegexOption.IGNORE_CASE), "")
                                ?.trim() ?: token.purchyNo

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFF0FDFA))
                                    .border(1.dp, Color(0xFF99F6E4), RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(DispatchedTealBg)
                                                .border(1.dp, DispatchedTealBorder, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "⚖️ MILL WEIGHMENT",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = DispatchedTeal
                                            )
                                        }
                                        Text(
                                            text = "Weighed: ${token.dispatchDateStr ?: token.issueDateStr}",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextSecondary
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable {
                                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    cm.setPrimaryClip(ClipData.newPlainText("Receipt", cleanReceipt))
                                                    Toast.makeText(context, "Copied Receipt: $cleanReceipt", Toast.LENGTH_SHORT).show()
                                                }
                                        ) {
                                            Text(
                                                text = "Mill Slip #$cleanReceipt",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = DispatchedTeal
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy",
                                                tint = DispatchedTeal,
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }

                                        Text(
                                            text = "Net: ${token.actualNettedWeight ?: "0ql"}",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = DispatchedTeal
                                        )
                                    }

                                    if (!token.grossWeight.isNullOrBlank() || !token.tareWeight.isNullOrBlank()) {
                                        Text(
                                            text = "Gross: ${token.grossWeight ?: "-"} • Tare: ${token.tareWeight ?: "-"}",
                                            fontSize = 11.5.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        } else {
                        // Micro-Card 1: Cane & Token Identifiers
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF8FAFC))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                .padding(9.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                // Row 1A: Grower Code & Cane Type
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = Color(0xFF64748B),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Code: ${token.growerCode}",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    }

                                    Text(
                                        text = "Type: ${token.caneType.ifEmpty { "GENERAL" }} (${token.fc.ifEmpty { "FC: -" }})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextSecondary
                                    )
                                }

                                // Row 1B: UGC & Purchy Ticket No (Clean numbers without '#', copyable)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // UGC Pill
                                    if (token.ugc.isNotEmpty()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFFEDE9FE))
                                                .border(1.dp, Color(0xFFDDD6FE), RoundedCornerShape(6.dp))
                                                .clickable {
                                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    cm.setPrimaryClip(ClipData.newPlainText("UGC", token.ugc))
                                                    Toast.makeText(context, "Copied UGC: ${token.ugc}", Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(horizontal = 7.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = "UGC: ${token.ugc}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF6D28D9)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy UGC",
                                                tint = Color(0xFF6D28D9),
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.width(1.dp))
                                    }

                                    // Purchy Ticket Number (Prominent, NO '#' prefix, never wrapping)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(SugarcaneGreenLight)
                                            .border(1.dp, Color(0xFFA5D6A7), RoundedCornerShape(6.dp))
                                            .clickable {
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cm.setPrimaryClip(ClipData.newPlainText("Purchy No", token.purchyNo))
                                                Toast.makeText(context, "Copied Purchy: ${token.purchyNo}", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(horizontal = 7.dp, vertical = 3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Receipt,
                                            contentDescription = "Purchy Ticket",
                                            tint = SugarcaneGreenPrimary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = token.purchyNo,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = SugarcaneGreenPrimary,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Micro-Card 2: Logistics & Mill Weighment Details
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isDispatched) Color(0xFFF0FDF4) else Color(0xFFF8FAFC))
                            .border(1.dp, if (isDispatched) Color(0xFFBBF7D0) else Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                            .padding(9.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                // Row 2A: Weight & Society/Centre
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Scale,
                                            contentDescription = "Weight",
                                            tint = if (isDispatched) DispatchedTeal else SugarcaneGreenPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isDispatched) "Dispatched: ${token.actualNettedWeight}" else "Allotted: ${token.allottedWeight}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDispatched) DispatchedTeal else TextPrimary
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = "Location",
                                            tint = TextTertiary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "${token.society} (Cnt: ${token.centre})",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = TextSecondary
                                        )
                                    }
                                }

                                // Row 2B: Validity / Weighment Receipt (Clean, no '#MILL')
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Valid Until: ${token.validUntilStr}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (urgencyLevel == UrgencyLevel.EXPIRING_TODAY) UrgencyRed else TextSecondary
                                    )

                                    if (isDispatched && !token.weightReceiptNo.isNullOrBlank()) {
                                        val cleanReceipt = token.weightReceiptNo
                                            .replace(Regex("^(?:#?MILL[-\\s]*|#)", RegexOption.IGNORE_CASE), "")
                                            .trim()
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(DispatchedTealBg)
                                                .border(1.dp, DispatchedTealBorder, RoundedCornerShape(6.dp))
                                            .clickable {
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cm.setPrimaryClip(ClipData.newPlainText("Receipt", cleanReceipt))
                                                Toast.makeText(context, "Copied Receipt: $cleanReceipt", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.LocalShipping,
                                                contentDescription = "Mill Weighment Receipt",
                                                tint = DispatchedTeal,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "Receipt: $cleanReceipt",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = DispatchedTeal,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                        Spacer(modifier = Modifier.height(7.dp))

                        // Row 3: Action Buttons (Unlink, Delete Message & Copy Full Text)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Unlink Button (Active only when Token and Dispatched SMS are linked together)
                                if (isLinkedWithToken) {
                                    OutlinedButton(
                                        onClick = { showUnlinkDialog = true },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color(0xFFD97706)
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(30.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 9.dp, vertical = 0.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LinkOff,
                                            contentDescription = "Unlink",
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Unlink",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Quick SMS Copy
                                if (!token.rawTokenMessage.isNullOrBlank()) {
                                    TextButton(
                                        onClick = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("Cane SMS", token.rawTokenMessage))
                                            Toast.makeText(context, "Original SMS Copied", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp),
                                            tint = Color(0xFF64748B)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Copy SMS",
                                            fontSize = 11.5.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(1.dp))
                                }
                            }

                            // Delete Message Button
                            OutlinedButton(
                                onClick = { showDeleteDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = UrgencyRed
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, UrgencyRed.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(30.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 9.dp, vertical = 0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete",
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Delete Message",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // WhatsApp Chat List Divider (indented 72dp past avatar)
                HorizontalDivider(
                    color = WhatsAppDivider,
                    thickness = 0.8.dp,
                    modifier = Modifier.padding(start = 72.dp)
                )
            }

            // Confirmation Dialog before deletion
            if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = "Archive Message Record?",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove Purchy ${token.purchyNo} for $cleanGrowerName? This will safely archive the record to preserve financial and audit integrity without accidental data loss.",
                    fontSize = 13.5.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = UrgencyRed)
                ) {
                    Text("Archive", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", fontSize = 14.sp)
                }
            }
        )
    }

    // Confirmation Dialog before unlinking
    if (showUnlinkDialog) {
        AlertDialog(
            onDismissRequest = { showUnlinkDialog = false },
            title = {
                Text(
                    text = "Unlink Token & Weighment?",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Do you want to unlink Cane Token #${token.purchyNo} from Mill Weighment #${token.weightReceiptNo ?: token.purchyNo}?\n\n• The Cane Token will return to Active Tokens.\n• The Mill Weighment will remain as a standalone Dispatched receipt.\n\nYou can re-link or review them independently without losing data.",
                    fontSize = 13.5.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnlinkDialog = false
                        onUnlink()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD97706))
                ) {
                    Text("Unlink", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlinkDialog = false }) {
                    Text("Cancel", fontSize = 14.sp)
                }
            }
        )
    }
}

/**
 * Modern, Highly Readable Badge Component with clear icons and larger typography.
 */
@Composable
fun ModernBadge(
    text: String,
    textColor: Color,
    bgColor: Color,
    borderColor: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(3.5.dp))
            }
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
