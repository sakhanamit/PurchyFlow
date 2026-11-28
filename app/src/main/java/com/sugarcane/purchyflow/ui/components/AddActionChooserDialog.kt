package com.sugarcane.purchyflow.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sugarcane.purchyflow.ui.theme.ModernBgSky
import com.sugarcane.purchyflow.ui.theme.ModernBgTeal
import com.sugarcane.purchyflow.ui.theme.ModernBorderMint
import com.sugarcane.purchyflow.ui.theme.ModernBorderSky
import com.sugarcane.purchyflow.ui.theme.ModernBorderTeal
import com.sugarcane.purchyflow.ui.theme.ModernTonalEmerald
import com.sugarcane.purchyflow.ui.theme.ModernTonalMint
import com.sugarcane.purchyflow.ui.theme.ModernTonalSky
import com.sugarcane.purchyflow.ui.theme.ModernTonalTeal
import com.sugarcane.purchyflow.ui.theme.TextPrimary
import com.sugarcane.purchyflow.ui.theme.TextSecondary
import com.sugarcane.purchyflow.ui.theme.WhatsAppGreen
import com.sugarcane.purchyflow.ui.theme.WhatsAppTeal

/**
 * Slide-up Bottom Sheet shown when the user taps the plus (+) FAB button.
 * Surfaces 3 distinct options targeting the currently active tab:
 * 1. Add Purchy via SMS (directly extracts to TOKENS or DISPATCHED)
 * 2. Add Purchy Manually (manual form)
 * 3. Import Data (Excel / CSV)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddActionChooserDialog(
    targetTab: Int, // 0 = TOKENS, 1 = DISPATCHED
    onDismiss: () -> Unit,
    onChooseManual: () -> Unit,
    onChooseSms: () -> Unit,
    onChooseImport: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isTokens = targetTab == 0
    val targetSection = if (isTokens) "TOKENS" else "DISPATCHED"
    val accentColor = if (isTokens) WhatsAppGreen else WhatsAppTeal
    val bgTonal = if (isTokens) Color(0xFFE8F5E9) else Color(0xFFE0F2F1)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = Color.White,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(bgTonal)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ADDING TO: $targetSection",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = accentColor,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add Cane Record",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = TextPrimary
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF1F5F9))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Option 1: Add via SMS (extracts directly to active section)
            ChooserOptionCard(
                title = "Add via SMS",
                subtitle = "Paste SMS text to directly extract into $targetSection",
                icon = Icons.Default.Sms,
                iconTint = Color.White,
                iconBg = accentColor,
                containerBg = bgTonal,
                borderStroke = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.35f)),
                onClick = {
                    onDismiss()
                    onChooseSms()
                }
            )

            // Option 2: Add Manually
            ChooserOptionCard(
                title = "Add Manually",
                subtitle = "Fill in grower name, date, code & weight by hand",
                icon = Icons.Default.EditNote,
                iconTint = Color.White,
                iconBg = ModernTonalEmerald,
                containerBg = ModernTonalMint,
                borderStroke = BorderStroke(1.2.dp, ModernBorderMint),
                onClick = {
                    onDismiss()
                    onChooseManual()
                }
            )

            // Option 3: Import Spreadsheet
            ChooserOptionCard(
                title = "Import Data",
                subtitle = "Load records from Excel (.xlsx) or CSV file",
                icon = Icons.Default.UploadFile,
                iconTint = Color.White,
                iconBg = ModernTonalTeal,
                containerBg = ModernBgTeal,
                borderStroke = BorderStroke(1.2.dp, ModernBorderTeal),
                onClick = {
                    onDismiss()
                    onChooseImport()
                }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Cancel Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Cancel",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF64748B)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChooserOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    containerBg: Color,
    borderStroke: BorderStroke,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = containerBg,
        border = borderStroke,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(13.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
