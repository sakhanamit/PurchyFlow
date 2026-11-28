package com.sugarcane.purchyflow

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.sugarcane.purchyflow.security.BiometricAuthManager
import com.sugarcane.purchyflow.ui.screens.AppLockScreen
import com.sugarcane.purchyflow.ui.screens.HomeScreen
import com.sugarcane.purchyflow.ui.screens.WelcomeScreen
import com.sugarcane.purchyflow.ui.theme.PurchyFlowTheme
import com.sugarcane.purchyflow.ui.viewmodel.PurchyViewModel
import com.sugarcane.purchyflow.ui.viewmodel.PurchyViewModelFactory

class MainActivity : FragmentActivity() {

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 1001
    }

    private val viewModel: PurchyViewModel by viewModels {
        val app = application as PurchyApplication
        PurchyViewModelFactory(app.repository)
    }

    private var isAppUnlockedState = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIntent(intent)

        val prefs = getSharedPreferences("purchyflow_prefs", Context.MODE_PRIVATE)
        val isBiometricEnabled = BiometricAuthManager.isBiometricLockEnabled(this)
        isAppUnlockedState.value = !isBiometricEnabled

        setContent {
            PurchyFlowTheme {
                val isPermissionGranted by viewModel.isPermissionGranted.collectAsState()
                var hasSeenWelcome by remember {
                    mutableStateOf(prefs.getBoolean("has_seen_welcome", false))
                }
                val isUnlocked by remember { isAppUnlockedState }

                if (!isUnlocked) {
                    AppLockScreen(
                        onUnlockClick = { triggerBiometricUnlock() }
                    )
                } else if (!hasSeenWelcome) {
                    WelcomeScreen(
                        isPermissionGranted = isPermissionGranted,
                        onRequestPermission = { requestRequiredPermissions() },
                        onContinueToHome = {
                            prefs.edit().putBoolean("has_seen_welcome", true).apply()
                            hasSeenWelcome = true
                        }
                    )
                } else {
                    HomeScreen(
                        viewModel = viewModel,
                        onRequestSmsPermission = { requestRequiredPermissions() },
                        onOpenWelcome = { hasSeenWelcome = false }
                    )
                }
            }
        }

        if (isBiometricEnabled) {
            triggerBiometricUnlock()
        }
    }

    override fun onResume() {
        super.onResume()
        // Recheck permissions on return to app
        viewModel.checkPermissions(this)

        if (BiometricAuthManager.isBiometricLockEnabled(this) && !isAppUnlockedState.value) {
            triggerBiometricUnlock()
        }
    }

    private fun triggerBiometricUnlock() {
        if (!BiometricAuthManager.canAuthenticate(this)) {
            // Fallback: unlock if no biometric/PIN setup on device
            isAppUnlockedState.value = true
            return
        }

        BiometricAuthManager.promptAuthentication(
            activity = this,
            title = "Unlock PurchyFlow",
            subtitle = "Authenticate to access sugarcane records",
            onSuccess = {
                isAppUnlockedState.value = true
            },
            onError = { errMsg ->
                Toast.makeText(this, "Authentication failed: $errMsg", Toast.LENGTH_SHORT).show()
            }
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra("test_sms")?.let { sms ->
            viewModel.processManualMessage(sms, this)
        }
    }

    private fun requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    PERMISSIONS_REQUEST_CODE
                )
            }
        }
        viewModel.openNotificationDisclosure()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            viewModel.checkPermissions(this)
        }
    }
}
