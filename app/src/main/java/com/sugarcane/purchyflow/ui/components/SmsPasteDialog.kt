package com.sugarcane.purchyflow.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sugarcane.purchyflow.data.model.PurchyToken
import com.sugarcane.purchyflow.data.model.TokenStatus
import com.sugarcane.purchyflow.parser.ScannedReceiptData
import com.sugarcane.purchyflow.parser.TokenMessageParser
import com.sugarcane.purchyflow.ui.theme.TextPrimary
import com.sugarcane.purchyflow.ui.theme.TextSecondary
import com.sugarcane.purchyflow.ui.theme.WhatsAppGreen
import com.sugarcane.purchyflow.ui.theme.WhatsAppTeal
import com.sugarcane.purchyflow.ui.viewmodel.PurchyViewModel
import com.sugarcane.purchyflow.util.DateUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Slide-up Bottom Sheet: Add Purchy SMS.
 * Automatically identifies message type and extracts data directly into the active section:
 * - TOKENS section (Tab 0) -> Active cane department token (name, code, purchy, weight, date)
 * - DISPATCHED section (Tab 1) -> Dispatched mill receipt (name, code, receipt, net weight, date, mill)
 * Completely eliminates sample text and manual tab switching inside the dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsPasteDialog(
    viewModel: PurchyViewModel,
    targetTab: Int, // 0 = TOKENS, 1 = DISPATCHED
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pastedText by remember { mutableStateOf("") }

    val isTokensTarget = targetTab == 0
    val targetSectionName = if (isTokensTarget) "TOKENS" else "DISPATCHED"
    val accentColor = if (isTokensTarget) WhatsAppGreen else WhatsAppTeal
    val bgTonal = if (isTokensTarget) Color(0xFFE8F5E9) else Color(0xFFE0F2F1)

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
                .imePadding()
                .padding(bottom = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with target destination indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(bgTonal),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sms,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(bgTonal)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "EXTRACTING TO: $targetSectionName",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = accentColor,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (isTokensTarget) "Add Cane Token SMS" else "Add Mill Weighment SMS",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = TextPrimary
                        )
                    }
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

            // Text Input Area
            OutlinedTextField(
                value = pastedText,
                onValueChange = { pastedText = it },
                placeholder = {
                    Text(
                        text = if (isTokensTarget)
                            "Paste CANEDEPTT token message here...\n(Data will be extracted directly into TOKENS)"
                        else
                            "Paste Sugar Mill Taul message here...\n(Data will be extracted directly into DISPATCHED)",
                        fontSize = 13.5.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 18.sp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = Color(0xFFE2E8F0),
                    focusedContainerColor = Color(0xFFF8FAFC),
                    unfocusedContainerColor = Color(0xFFF8FAFC)
                )
            )

            // Paste from Clipboard & Clear
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        val clip = clipboardManager.getText()?.text
                        if (!clip.isNullOrBlank()) {
                            pastedText = clip.trim()
                            Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Paste from Clipboard",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor
                    )
                }

                if (pastedText.isNotEmpty()) {
                    TextButton(
                        onClick = { pastedText = "" }
                    ) {
                        Text("Clear", fontSize = 12.5.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Real-time SMS Type Identification & Extracted Preview
            val messageType = remember(pastedText) { TokenMessageParser.identifyMessageType(pastedText) }
            val tokenParsed = remember(pastedText) { if (pastedText.isNotBlank()) TokenMessageParser.parseTokenMessage(pastedText) else null }
            val taulParsed = remember(pastedText) { if (pastedText.isNotBlank()) TokenMessageParser.parseWeighmentReceipt(pastedText) else null }

            if (pastedText.isNotBlank()) {
                val (badgeBg, badgeBorder, badgeText, badgeColor) = when (messageType) {
                    com.sugarcane.purchyflow.parser.CaneMessageType.PURCHY_TOKEN -> 
                        listOf(Color(0xFFE8F5E9), Color(0xFF4CAF50), "🟢 Identified: Purchy Token (Cane Department SMS)", Color(0xFF2E7D32))
                    com.sugarcane.purchyflow.parser.CaneMessageType.TAUL_RECEIPT -> 
                        listOf(Color(0xFFE0F2F1), Color(0xFF00897B), "🔵 Identified: Taul Receipt (Sugar Mill Weighment SMS)", Color(0xFF00695C))
                    com.sugarcane.purchyflow.parser.CaneMessageType.UNKNOWN -> 
                        listOf(Color(0xFFFFFBE6), Color(0xFFF59E0B), "🟠 Unrecognized SMS Format (Check message text)", Color(0xFFB45309))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(badgeBg as Color)
                        .border(1.dp, (badgeBorder as Color).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = badgeText as String,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor as Color
                        )
                        
                        if (messageType == com.sugarcane.purchyflow.parser.CaneMessageType.PURCHY_TOKEN && tokenParsed != null) {
                            Text("• Grower: ${tokenParsed.growerName} (${tokenParsed.growerCode})", fontSize = 12.sp, color = TextPrimary)
                            Text("• Purchy #: ${tokenParsed.purchyNo} | Weight: ${tokenParsed.allottedWeight}", fontSize = 12.sp, color = TextPrimary)
                            Text("• Date: ${tokenParsed.issueDateStr} | Society: ${tokenParsed.society}", fontSize = 12.sp, color = TextSecondary)
                        } else if (messageType == com.sugarcane.purchyflow.parser.CaneMessageType.TAUL_RECEIPT && taulParsed != null) {
                            Text("• Grower: ${taulParsed.growerName ?: "N/A"} (${taulParsed.growerCode ?: "N/A"})", fontSize = 12.sp, color = TextPrimary)
                            Text("• Receipt/Purchy #: ${taulParsed.receiptNo ?: taulParsed.purchyNo ?: "N/A"} | Net Wt: ${taulParsed.actualNettedWeight}", fontSize = 12.sp, color = TextPrimary)
                            Text("• Date: ${taulParsed.dispatchDateStr} | Mill: ${taulParsed.societyOrMill ?: "Mill"}", fontSize = 12.sp, color = TextSecondary)
                        } else {
                            Text("Could not automatically parse fields. Ensure message contains Cane Department or Sugar Mill keywords.", fontSize = 11.5.sp, color = Color(0xFFB45309))
                        }
                    }
                }
            }

            // Info Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(bgTonal)
                    .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = if (isTokensTarget)
                        "⚡ Auto-Detection: Grower Name, Purchy Number, Code, Weight and Validity will be automatically identified and placed into the TOKENS section."
                    else
                        "⚡ Auto-Detection: Grower Name, Mill Receipt Number, Code, Net Weight and Date will be automatically identified and placed into the DISPATCHED section.",
                    fontSize = 12.sp,
                    color = accentColor,
                    lineHeight = 16.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Cancel", fontSize = 14.sp, color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        if (pastedText.isBlank()) {
                            Toast.makeText(context, "Please paste an SMS message first", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val raw = pastedText.trim()
                        val todayStr = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH).format(Date())

                        val tokenMsg = TokenMessageParser.parseTokenMessage(raw)
                        val taulMsg = TokenMessageParser.parseWeighmentReceipt(raw)

                        if (isTokensTarget) {
                            // Extract to TOKENS section
                            if (tokenMsg != null) {
                                val purchyNo = tokenMsg.purchyNo.replace("#", "").trim()
                                val newToken = PurchyToken(
                                    purchyNo = purchyNo,
                                    growerName = tokenMsg.growerName,
                                    growerCode = tokenMsg.growerCode,
                                    ugc = tokenMsg.ugc,
                                    caneType = tokenMsg.caneType,
                                    fc = tokenMsg.fc,
                                    allottedWeight = tokenMsg.allottedWeight,
                                    allottedWeightNumber = tokenMsg.allottedWeightNumber,
                                    issueDateStr = tokenMsg.issueDateStr,
                                    issueDateEpochMs = tokenMsg.issueDateEpochMs,
                                    validUntilStr = tokenMsg.validUntilStr,
                                    validUntilEpochMs = tokenMsg.validUntilEpochMs,
                                    society = tokenMsg.society,
                                    centre = tokenMsg.centre,
                                    status = TokenStatus.ACTIVE,
                                    rawTokenMessage = raw
                                )
                                viewModel.saveManualToken(newToken, context)
                                onDismiss()
                            } else if (taulMsg != null) {
                                val purchyNo = (taulMsg.purchyNo ?: taulMsg.receiptNo ?: "TOKEN-${System.currentTimeMillis() % 100000}")
                                    .replace(Regex("^(?:#?MILL[-\\s]*|#)", RegexOption.IGNORE_CASE), "").trim()
                                val newToken = PurchyToken(
                                    purchyNo = purchyNo,
                                    growerName = taulMsg.growerName ?: "GROWER",
                                    growerCode = taulMsg.growerCode ?: "5501/0",
                                    ugc = taulMsg.ugc ?: "",
                                    caneType = "GENERAL",
                                    fc = "1/1",
                                    allottedWeight = taulMsg.actualNettedWeight,
                                    allottedWeightNumber = taulMsg.actualNettedWeightNumber ?: 45.0,
                                    issueDateStr = taulMsg.dispatchDateStr ?: todayStr,
                                    issueDateEpochMs = DateUtil.parseDate(taulMsg.dispatchDateStr)?.time ?: System.currentTimeMillis(),
                                    validUntilStr = taulMsg.dispatchDateStr ?: todayStr,
                                    validUntilEpochMs = (DateUtil.parseDate(taulMsg.dispatchDateStr)?.time ?: System.currentTimeMillis()) + (72 * 3600 * 1000L),
                                    society = taulMsg.societyOrMill ?: "Society",
                                    centre = "100",
                                    status = TokenStatus.ACTIVE,
                                    rawTokenMessage = raw
                                )
                                viewModel.saveManualToken(newToken, context)
                                onDismiss()
                            } else {
                                Toast.makeText(context, "Could not extract valid Token data. Please check message format.", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            // Extract to DISPATCHED section
                            if (taulMsg != null) {
                                val rNo = (taulMsg.receiptNo ?: taulMsg.purchyNo ?: "TAUL-${System.currentTimeMillis() % 100000}")
                                    .replace(Regex("^(?:#?MILL[-\\s]*|#)", RegexOption.IGNORE_CASE), "").trim()
                                val weightNum = taulMsg.actualNettedWeight.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0

                                val scanned = ScannedReceiptData(
                                    millPurchyNo = rNo,
                                    societyPurchyNo = taulMsg.purchyNo ?: rNo,
                                    growerName = taulMsg.growerName ?: "GROWER",
                                    growerCode = taulMsg.growerCode ?: "5501/0",
                                    netWeight = weightNum,
                                    dateStr = taulMsg.dispatchDateStr ?: todayStr,
                                    dateEpochMs = DateUtil.parseDate(taulMsg.dispatchDateStr)?.time ?: System.currentTimeMillis(),
                                    millOrSociety = taulMsg.societyOrMill ?: "Mill Weighment",
                                    rawOcrText = raw
                                )
                                viewModel.saveScannedReceipt(scanned, context)
                                onDismiss()
                            } else if (tokenMsg != null) {
                                val purchyNo = tokenMsg.purchyNo.replace("#", "").trim()
                                val scanned = ScannedReceiptData(
                                    millPurchyNo = purchyNo,
                                    societyPurchyNo = purchyNo,
                                    growerName = tokenMsg.growerName,
                                    growerCode = tokenMsg.growerCode,
                                    netWeight = tokenMsg.allottedWeightNumber,
                                    dateStr = tokenMsg.issueDateStr,
                                    dateEpochMs = tokenMsg.issueDateEpochMs,
                                    millOrSociety = tokenMsg.society,
                                    rawOcrText = raw
                                )
                                viewModel.saveScannedReceipt(scanned, context)
                                onDismiss()
                            } else {
                                Toast.makeText(context, "Could not extract valid Weighment data. Please check message format.", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1.2f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text(
                        text = "Extract & Save",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}
