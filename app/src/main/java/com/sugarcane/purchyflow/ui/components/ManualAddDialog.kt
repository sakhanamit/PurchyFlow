package com.sugarcane.purchyflow.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sugarcane.purchyflow.data.model.PurchyToken
import com.sugarcane.purchyflow.data.model.TokenStatus
import com.sugarcane.purchyflow.parser.ScannedReceiptData
import com.sugarcane.purchyflow.ui.theme.DispatchedTeal
import com.sugarcane.purchyflow.ui.theme.ModernBorderMint
import com.sugarcane.purchyflow.ui.theme.ModernTonalEmerald
import com.sugarcane.purchyflow.ui.theme.ModernTonalMint
import com.sugarcane.purchyflow.ui.theme.SugarcaneGreenPrimary
import com.sugarcane.purchyflow.ui.theme.TextPrimary
import com.sugarcane.purchyflow.ui.theme.TextSecondary
import com.sugarcane.purchyflow.ui.viewmodel.PurchyViewModel
import com.sugarcane.purchyflow.util.DateUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated Window 1: Add Purchy Manually.
 * Features clean rounded text sections exclusively for manual typing:
 * - Grower Name
 * - Date (DD-MM-YYYY)
 * - Grower Code (Village/Grower)
 * - Receipt / Purchy Number
 * - Net Weight (in Quintals)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualAddDialog(
    viewModel: PurchyViewModel,
    targetTab: Int = 0,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var growerName by remember { mutableStateOf("") }
    var dateStr by remember {
        mutableStateOf(SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH).format(Date()))
    }
    var growerCode by remember { mutableStateOf("") }
    var receiptNo by remember { mutableStateOf("") }
    var netWeight by remember { mutableStateOf("") }

    // Record type: 0 = Weighment (Dispatched), 1 = Active Cane Token
    // Defaults automatically based on user's active home tab
    var recordType by remember { mutableStateOf(if (targetTab == 1) 0 else 1) }

    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Color.White,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                                imageVector = Icons.Default.EditNote,
                                contentDescription = null,
                                tint = ModernTonalEmerald,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Add Purchy Manually",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.5.sp
                                ),
                                color = TextPrimary
                            )
                            Text(
                                text = "Enter weighment or token fields below",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(30.dp)
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

                // Record Type Selector (Modern Segmented Pills)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF1F5F9))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Option 0: Weighment Receipt (Dispatched)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (recordType == 0) Color.White else Color.Transparent)
                            .clickable { recordType = 0 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Weighment Receipt",
                            fontSize = 12.5.sp,
                            fontWeight = if (recordType == 0) FontWeight.Bold else FontWeight.Medium,
                            color = if (recordType == 0) DispatchedTeal else Color(0xFF64748B)
                        )
                    }

                    // Option 1: Active Cane Token
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (recordType == 1) Color.White else Color.Transparent)
                            .clickable { recordType = 1 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Active Cane Token",
                            fontSize = 12.5.sp,
                            fontWeight = if (recordType == 1) FontWeight.Bold else FontWeight.Medium,
                            color = if (recordType == 1) ModernTonalEmerald else Color(0xFF64748B)
                        )
                    }
                }

                // Box 1: Grower Name
                OutlinedTextField(
                    value = growerName,
                    onValueChange = { growerName = it },
                    label = { Text("Grower Name *", fontSize = 13.sp) },
                    placeholder = { Text("e.g. DEEPAK VERMA", fontSize = 12.5.sp, color = Color.LightGray) },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, tint = ModernTonalEmerald, modifier = Modifier.size(20.dp))
                    },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ModernTonalEmerald,
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Box 2: Date
                OutlinedTextField(
                    value = dateStr,
                    onValueChange = { dateStr = it },
                    label = { Text("Date (DD-MM-YYYY) *", fontSize = 13.sp) },
                    placeholder = { Text("e.g. 18-11-2026", fontSize = 12.5.sp, color = Color.LightGray) },
                    leadingIcon = {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = ModernTonalEmerald, modifier = Modifier.size(19.dp))
                    },
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                dateStr = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH).format(Date())
                            }
                        ) {
                            Text("Today", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = ModernTonalEmerald)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ModernTonalEmerald,
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Box 3: Grower Code
                OutlinedTextField(
                    value = growerCode,
                    onValueChange = { growerCode = it },
                    label = { Text("Grower Code (Village/Grower) *", fontSize = 13.sp) },
                    placeholder = { Text("e.g. 5501/34 or 5501/45", fontSize = 12.5.sp, color = Color.LightGray) },
                    leadingIcon = {
                        Icon(Icons.Default.Numbers, contentDescription = null, tint = ModernTonalEmerald, modifier = Modifier.size(19.dp))
                    },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ModernTonalEmerald,
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Box 4: Receipt / Purchy Number
                OutlinedTextField(
                    value = receiptNo,
                    onValueChange = { receiptNo = it },
                    label = { Text("Receipt / Purchy Number *", fontSize = 13.sp) },
                    placeholder = { Text("e.g. 8800101 or 700100200304", fontSize = 12.5.sp, color = Color.LightGray) },
                    leadingIcon = {
                        Icon(Icons.Default.Receipt, contentDescription = null, tint = ModernTonalEmerald, modifier = Modifier.size(19.dp))
                    },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ModernTonalEmerald,
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Box 5: Net Weight
                OutlinedTextField(
                    value = netWeight,
                    onValueChange = {
                        netWeight = it
                        if (it.isNotBlank() && recordType == 1) {
                            recordType = 0 // Switch to Dispatched when weight is entered
                        }
                    },
                    label = { Text("Net Weight (in Quintals)", fontSize = 13.sp) },
                    placeholder = { Text("e.g. 47.77 or 54.9", fontSize = 12.5.sp, color = Color.LightGray) },
                    leadingIcon = {
                        Icon(Icons.Default.Scale, contentDescription = null, tint = DispatchedTeal, modifier = Modifier.size(19.dp))
                    },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DispatchedTeal,
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Bottom Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", fontSize = 14.sp, color = Color(0xFF64748B))
                    }

                    Button(
                        onClick = {
                            val cleanName = growerName.trim().replace(Regex("/+"), " / ").trimEnd('/', ' ')
                            val cleanReceipt = receiptNo.replace(Regex("^(?:#?MILL[-\\s]*|#)", RegexOption.IGNORE_CASE), "").trim()

                            if (cleanName.isBlank()) {
                                Toast.makeText(context, "Please enter Grower Name", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (cleanReceipt.isBlank()) {
                                Toast.makeText(context, "Please enter Receipt / Purchy Number", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val dateEpoch = DateUtil.parseDate(dateStr.trim())?.time ?: System.currentTimeMillis()
                            val weightVal = netWeight.trim().toDoubleOrNull()

                            if (recordType == 0) {
                                // Save as Dispatched Weighment Receipt
                                val scanned = ScannedReceiptData(
                                    millPurchyNo = cleanReceipt,
                                    societyPurchyNo = cleanReceipt,
                                    growerName = cleanName,
                                    growerCode = growerCode.trim().ifEmpty { "GROWER" },
                                    netWeight = weightVal ?: 0.0,
                                    dateStr = dateStr.trim(),
                                    dateEpochMs = dateEpoch,
                                    millOrSociety = "Mill Weighment",
                                    rawOcrText = "Manual Entry: $cleanName, Code:${growerCode.trim()}, Receipt:$cleanReceipt, Wt:$weightVal"
                                )
                                viewModel.saveScannedReceipt(scanned, context)
                            } else {
                                // Save as Active Cane Token
                                val effectiveWeight = weightVal ?: 45.0
                                val newToken = PurchyToken(
                                    purchyNo = cleanReceipt,
                                    growerName = cleanName,
                                    growerCode = growerCode.trim().ifEmpty { "5501/0" },
                                    ugc = "",
                                    caneType = "GENERAL",
                                    fc = "1/1",
                                    allottedWeight = "${effectiveWeight.toInt()}ql",
                                    allottedWeightNumber = effectiveWeight,
                                    issueDateStr = dateStr.trim(),
                                    issueDateEpochMs = dateEpoch,
                                    validUntilStr = dateStr.trim(),
                                    validUntilEpochMs = dateEpoch + (72 * 3600 * 1000L),
                                    society = "Mill",
                                    centre = "Centre",
                                    status = TokenStatus.ACTIVE,
                                    rawTokenMessage = "Manual Entry: $cleanName, Purchy:$cleanReceipt"
                                )
                                viewModel.saveManualToken(newToken, context)
                            }

                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ModernTonalEmerald),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Text("✓ Save Record", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
