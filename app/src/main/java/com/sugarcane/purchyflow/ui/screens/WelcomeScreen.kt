package com.sugarcane.purchyflow.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sugarcane.purchyflow.R
import com.sugarcane.purchyflow.ui.theme.ModernBgSky
import com.sugarcane.purchyflow.ui.theme.ModernBgTeal
import com.sugarcane.purchyflow.ui.theme.ModernBorderMint
import com.sugarcane.purchyflow.ui.theme.ModernBorderSky
import com.sugarcane.purchyflow.ui.theme.ModernBorderTeal
import com.sugarcane.purchyflow.ui.theme.ModernTonalEmerald
import com.sugarcane.purchyflow.ui.theme.ModernTonalMint
import com.sugarcane.purchyflow.ui.theme.ModernTonalSky
import com.sugarcane.purchyflow.ui.theme.ModernTonalTeal
import com.sugarcane.purchyflow.ui.theme.SugarcaneGreenPrimary
import com.sugarcane.purchyflow.ui.theme.TextPrimary
import com.sugarcane.purchyflow.ui.theme.TextSecondary
import com.sugarcane.purchyflow.ui.theme.UrgencyGreen

/**
 * Consumer Landing Page & Welcome Onboarding Screen:
 * - Bold above-the-fold hero with punchy headline, 1-2 line subheading & standout CTA
 * - Generous vertical rhythm with alternating light/tinted surfaces
 * - Bento-style modern feature cards with crisp custom icons
 * - Clear, transparent permission request module for SMS auto-capture
 * - High-contrast outdoors-ready typography with vibrant emerald/gold accents
 */
@Composable
fun WelcomeScreen(
    isPermissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onContinueToHome: () -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAF9))
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Dismiss / Close button if opened from Home menu
            if (onDismiss != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE2E8F0))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ==========================================
            // 1. Above-the-Fold Hero Section
            // ==========================================
            // Category Eyebrow Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(ModernTonalMint)
                    .border(BorderStroke(1.dp, ModernBorderMint), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = ModernTonalEmerald,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PURCHYFLOW v${com.sugarcane.purchyflow.BuildConfig.VERSION_NAME} • OFFLINE & ENCRYPTED",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ModernTonalEmerald,
                        letterSpacing = 0.8.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // New Eye-Catching Sugarcane Logo Hero Emblem
            Image(
                painter = painterResource(id = R.drawable.ic_purchyflow_logo),
                contentDescription = "PurchyFlow Brand Logo",
                modifier = Modifier
                    .size(92.dp)
                    .shadow(12.dp, CircleShape, spotColor = Color(0x40006C4C))
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Punchy Headline
            Text(
                text = "Pure Flow for Every Sugarcane Purchy",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 26.sp,
                    lineHeight = 32.sp
                ),
                color = Color(0xFF0F172A),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Short 1-2 line subheading
            Text(
                text = "Track CANEDEPTT token messages, tally mill weighbridge slips in real-time, and avoid duplicate entries effortlessly.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                ),
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Standout Hero Primary CTA
            Button(
                onClick = {
                    if (!isPermissionGranted) {
                        onRequestPermission()
                    } else {
                        onContinueToHome()
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ModernTonalEmerald
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (!isPermissionGranted) "⚡ Grant Permissions & Get Started" else "Enter PurchyFlow Dashboard",
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // ==========================================
            // 2. Bento-Style Modern Feature Grid
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "WHY GROWERS TRUST PURCHYFLOW",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 0.8.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bento Card 1: Instant SMS Auto-Capture
            BentoFeatureCard(
                icon = Icons.Default.Sms,
                iconTint = ModernTonalEmerald,
                iconBg = ModernTonalMint,
                title = "Zero-Touch SMS Auto-Capture",
                description = "Incoming CANEDEPTT token SMS and mill weighment receipts are detected instantly and filed in FIFO order."
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Bento Row: 2 Column Bento Split
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Bento Card 2: Live Mill Weight Counter
                BentoSmallCard(
                    icon = Icons.Default.Scale,
                    iconTint = ModernTonalTeal,
                    iconBg = ModernBgTeal,
                    title = "Live Net Weight",
                    description = "Prominent counter displays exact mill quintals at top right.",
                    modifier = Modifier.weight(1f)
                )

                // Bento Card 3: Duplicate Shield
                BentoSmallCard(
                    icon = Icons.Default.Security,
                    iconTint = ModernTonalSky,
                    iconBg = ModernBgSky,
                    title = "Duplicate Shield",
                    description = "Intelligent matching ensures zero duplicate tickets.",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bento Card 4: One-Click Excel / PDF Export
            BentoFeatureCard(
                icon = Icons.Default.FileDownload,
                iconTint = Color(0xFFB45309),
                iconBg = Color(0xFFFEF3C7),
                title = "Instant Excel & PDF Statements",
                description = "Export complete cane dispatch registries to share with society supervisors or mill accountants in seconds."
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ==========================================
            // 3. Permission & Privacy Assurance Section
            // ==========================================
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isPermissionGranted) Color(0xFFF0FDF4) else Color.White
                ),
                border = BorderStroke(
                    1.2.dp,
                    if (isPermissionGranted) Color(0xFF86EFAC) else Color(0xFFE2E8F0)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isPermissionGranted) Color(0xFFDCFCE7) else Color(0xFFFEF9C3)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPermissionGranted) Icons.Default.CheckCircle else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (isPermissionGranted) UrgencyGreen else Color(0xFFCA8A04),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = if (isPermissionGranted) "SMS Permission Active" else "SMS Access Required",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (isPermissionGranted) "PurchyFlow is listening for mill slips" else "Used strictly on-device to read cane tokens",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "🔒 100% Private & Offline: Your sugarcane slips never leave your phone. No logins, no internet needed, no cloud tracking.",
                        fontSize = 12.sp,
                        color = Color(0xFF475569),
                        lineHeight = 17.sp
                    )

                    if (!isPermissionGranted) {
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = onRequestPermission,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.2.dp, ModernTonalEmerald),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Grant SMS Permission Now",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = ModernTonalEmerald
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Bottom Navigation Button
            Button(
                onClick = onContinueToHome,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0F172A)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Continue to App",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun BentoFeatureCard(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = title,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

@Composable
private fun BentoSmallCard(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 1.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = description,
                fontSize = 11.5.sp,
                color = TextSecondary,
                lineHeight = 15.sp
            )
        }
    }
}
