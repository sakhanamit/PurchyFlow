package com.sugarcane.purchyflow.ui.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sugarcane.purchyflow.data.model.PurchyToken
import com.sugarcane.purchyflow.data.model.TokenStatus
import com.sugarcane.purchyflow.data.model.UrgencyLevel
import com.sugarcane.purchyflow.data.repository.PurchyRepository
import com.sugarcane.purchyflow.data.sync.DiscoveredSmsItem
import com.sugarcane.purchyflow.export.ExcelReportGenerator
import com.sugarcane.purchyflow.parser.TokenMessageParser
import com.sugarcane.purchyflow.service.TextMessageNotificationListener
import com.sugarcane.purchyflow.util.DateUtil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class PurchyViewModel(private val repository: PurchyRepository) : ViewModel() {

    init {
        viewModelScope.launch {
            repository.purgeOutdatedSeasonRecords()
            repository.correlateAllUnmatched()
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTab = MutableStateFlow(0) // 0: Active Tokens, 1: Dispatched Receipts
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _selectedFilterChip = MutableStateFlow("ALL") // ALL, TODAY, EXPIRING_SOON, 45QL_PLUS
    val selectedFilterChip: StateFlow<String> = _selectedFilterChip.asStateFlow()

    private val _hasSmsPermission = MutableStateFlow(false)
    val hasSmsPermission: StateFlow<Boolean> = _hasSmsPermission.asStateFlow()

    private val _isNotificationListenerEnabled = MutableStateFlow(false)
    val isNotificationListenerEnabled: StateFlow<Boolean> = _isNotificationListenerEnabled.asStateFlow()

    private val _isPermissionGranted = MutableStateFlow(false)
    val isPermissionGranted: StateFlow<Boolean> = _isPermissionGranted.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncResult = MutableStateFlow<String?>(null)
    val lastSyncResult: StateFlow<String?> = _lastSyncResult.asStateFlow()

    private var hasTriggeredInitialSync = false

    private val _discoveredSmsList = MutableStateFlow<List<DiscoveredSmsItem>>(emptyList())
    val discoveredSmsList: StateFlow<List<DiscoveredSmsItem>> = _discoveredSmsList.asStateFlow()

    private val _showSmsSyncSelector = MutableStateFlow(false)
    val showSmsSyncSelector: StateFlow<Boolean> = _showSmsSyncSelector.asStateFlow()

    private val _isScanningInbox = MutableStateFlow(false)
    val isScanningInbox: StateFlow<Boolean> = _isScanningInbox.asStateFlow()

    private val _showExportDialog = MutableStateFlow(false)
    val showExportDialog: StateFlow<Boolean> = _showExportDialog.asStateFlow()

    // Base cane tokens from repository: all token SMS synced from Cane Department
    private val rawCaneTokens = _searchQuery.flatMapLatest { query ->
        repository.getAllCaneTokens(query)
    }

    // 1. ACTIVE TOKENS (Tab 0: TOKEN): Shows currently active, unexpired Cane Tokens sorted FIFO.
    // Expired tokens are excluded from this list (accessible in AUDIT tab for reconciliation).
    val activeTokens: StateFlow<List<PurchyToken>> = combine(rawCaneTokens, _selectedFilterChip) { list, filter ->
        val now = System.currentTimeMillis()
        val activeList = list.filter { it.status != TokenStatus.DISPATCHED && it.validUntilEpochMs >= now }

        val activeSorted = activeList.sortedWith(
            compareBy<PurchyToken> { it.validUntilEpochMs }
                .thenBy { it.issueDateEpochMs }
                .thenBy { it.createdAt }
        )

        when (filter) {
            "TODAY" -> activeSorted.filter {
                DateUtil.getUrgencyLevel(it.validUntilEpochMs, now) == UrgencyLevel.EXPIRING_TODAY
            }
            "EXPIRING_SOON" -> activeSorted.filter {
                val lvl = DateUtil.getUrgencyLevel(it.validUntilEpochMs, now)
                lvl == UrgencyLevel.EXPIRING_TODAY || lvl == UrgencyLevel.ONE_DAY_LEFT
            }
            "45QL_PLUS" -> activeSorted.filter { it.allottedWeightNumber >= 45.0 }
            else -> activeSorted
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 2. PENDING WEIGHMENT AUDIT: Unmatched tokens whose 2-day validity period has EXPIRED.
    // Automatically segregated into this dedicated tab for manual verification to prevent financial discrepancies.
    val auditTokens: StateFlow<List<PurchyToken>> = rawCaneTokens.map { list ->
        val now = System.currentTimeMillis()
        list.filter {
            it.status != TokenStatus.DISPATCHED && it.validUntilEpochMs < now
        }.sortedByDescending { it.validUntilEpochMs }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeCount: StateFlow<Int> = activeTokens.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val auditCount: StateFlow<Int> = auditTokens.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val dispatchedCount: StateFlow<Int> = repository.getDispatchedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Total weight compiled strictly from mill-weighed dispatched receipts
    val totalDispatchedWeight: StateFlow<Double> = repository.getTotalDispatchedWeight()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val urgentActiveCount: StateFlow<Int> = activeTokens.map { list ->
        val now = System.currentTimeMillis()
        list.count { it.validUntilEpochMs >= now && DateUtil.getUrgencyLevel(it.validUntilEpochMs, now) == UrgencyLevel.EXPIRING_TODAY }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _selectedDispatchedFilterChip = MutableStateFlow("ALL") // ALL, TODAY, 45QL_PLUS
    val selectedDispatchedFilterChip: StateFlow<String> = _selectedDispatchedFilterChip.asStateFlow()

    fun onDispatchedFilterChipSelected(filter: String) {
        _selectedDispatchedFilterChip.value = filter
    }

    private val rawDispatchedTokens = _searchQuery.flatMapLatest { query ->
        repository.getDispatchedReceipts(query)
    }

    val dispatchedGrowerNames: StateFlow<List<String>> = rawDispatchedTokens.map { list ->
        list.map { it.growerName.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dispatchedTokens: StateFlow<List<PurchyToken>> = combine(rawDispatchedTokens, _selectedDispatchedFilterChip) { list, filter ->
        val filtered = when {
            filter == "TODAY" -> list.filter {
                val epoch = DateUtil.parseDate(it.dispatchDateStr)?.time
                    ?: DateUtil.parseDate(it.issueDateStr)?.time
                    ?: it.dispatchDateEpochMs?.takeIf { ep -> ep > 0L }
                    ?: 0L
                DateUtil.isToday(epoch)
            }
            filter.startsWith("GROWER:") -> {
                val targetGrower = filter.removePrefix("GROWER:").trim()
                list.filter { it.growerName.trim().equals(targetGrower, ignoreCase = true) }
            }
            else -> list
        }

        if (filter == "SORT_NAME") {
            filtered.sortedWith(
                compareBy<PurchyToken> { it.growerName.trim().lowercase() }
                    .thenByDescending {
                        DateUtil.parseDate(it.dispatchDateStr)?.time
                            ?: DateUtil.parseDate(it.issueDateStr)?.time
                            ?: it.dispatchDateEpochMs?.takeIf { ep -> ep > 0L }
                            ?: it.issueDateEpochMs?.takeIf { ep -> ep > 0L }
                            ?: it.updatedAt
                    }
            )
        } else {
            filtered.sortedWith(
                compareByDescending<PurchyToken> {
                    DateUtil.parseDate(it.dispatchDateStr)?.time
                        ?: DateUtil.parseDate(it.issueDateStr)?.time
                        ?: it.dispatchDateEpochMs?.takeIf { ep -> ep > 0L }
                        ?: it.issueDateEpochMs?.takeIf { ep -> ep > 0L }
                        ?: it.updatedAt
                }.thenByDescending { it.updatedAt }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun onTabSelected(index: Int) {
        _selectedTab.value = index
    }

    fun onFilterChipSelected(filter: String) {
        _selectedFilterChip.value = filter
    }

    fun checkPermissions(context: Context) {
        val hasReceiveSms = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED

        val hasReadSms = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        val hasNotif = TextMessageNotificationListener.isNotificationServiceEnabled(context)

        _hasSmsPermission.value = hasReceiveSms || hasReadSms
        _isNotificationListenerEnabled.value = hasNotif
        _isPermissionGranted.value = hasReceiveSms || hasReadSms || hasNotif

        if (hasReadSms && !hasTriggeredInitialSync) {
            hasTriggeredInitialSync = true
            // Run correlation pass on startup to link any previously unlinked records
            viewModelScope.launch {
                repository.correlateAllUnmatched()
            }
        }
    }

    /**
     * Scans inbox and opens the selective SMS synchronization dialog.
     */
    fun openSmsSyncSelector(context: Context) {
        if (_isScanningInbox.value || _isSyncing.value) return
        viewModelScope.launch {
            _isScanningInbox.value = true
            try {
                val scanner = com.sugarcane.purchyflow.data.sync.SmsInboxScanner(context, repository)
                val list = scanner.discoverSeasonMessages()
                _discoveredSmsList.value = list
                if (list.isEmpty()) {
                    Toast.makeText(context, "No sugarcane messages found in SMS inbox for current season.", Toast.LENGTH_LONG).show()
                } else {
                    _showSmsSyncSelector.value = true
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to scan SMS: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            } finally {
                _isScanningInbox.value = false
            }
        }
    }

    fun toggleSmsSelection(smsId: String) {
        val current = _discoveredSmsList.value
        _discoveredSmsList.value = current.map { item ->
            if (item.smsId == smsId) item.copy(isSelected = !item.isSelected) else item
        }
    }

    fun selectAllSms(select: Boolean) {
        val current = _discoveredSmsList.value
        _discoveredSmsList.value = current.map { it.copy(isSelected = select) }
    }

    fun selectNewOnly() {
        val current = _discoveredSmsList.value
        _discoveredSmsList.value = current.map { item ->
            item.copy(isSelected = !item.isAlreadySynced && !item.isPreviouslyDeleted)
        }
    }

    fun dismissSmsSyncSelector() {
        _showSmsSyncSelector.value = false
    }

    fun syncSelectedMessages(context: Context) {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val scanner = com.sugarcane.purchyflow.data.sync.SmsInboxScanner(context, repository)
                val result = scanner.syncSelectedMessages(_discoveredSmsList.value)
                val retroCorrelated = repository.correlateAllUnmatched()
                val finalMsg = if (retroCorrelated > 0) {
                    "${result.message} • $retroCorrelated correlated to receipts"
                } else {
                    result.message
                }
                Toast.makeText(context, finalMsg, Toast.LENGTH_LONG).show()
                _showSmsSyncSelector.value = false
            } catch (e: Exception) {
                Toast.makeText(context, "Sync failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            } finally {
                _isSyncing.value = false
            }
        }
    }

    /**
     * Unlinks a correlated Weighment Receipt from a Token if the system matched the wrong SMS.
     * Restores the Cane Token back to ACTIVE (or AUDIT_PENDING if expired),
     * and preserves the Mill Weighment as a standalone DISPATCHED record.
     */
    fun unlinkToken(purchyNo: String, context: Context? = null) {
        viewModelScope.launch {
            val unlinked = repository.unlinkToken(purchyNo)
            if (unlinked && context != null) {
                Toast.makeText(context, "Unlinked Purchy #$purchyNo from weighment receipt", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Reads historic SMS messages from device inbox for the current cane year
     * (November 1st to April 30th next year ~6 months).
     */
    fun syncOldMessages(context: Context, showToast: Boolean = true) {
        openSmsSyncSelector(context)
    }

    fun setExportDialogVisible(visible: Boolean) {
        _showExportDialog.value = visible
    }

    /**
     * Manual or simulated injection of sugarcane SMS text messages
     * for direct testing and verification.
     */
    fun processManualMessage(message: String, context: Context) {
        val cleanMessage = message.trim('\'', '"', ' ')
        viewModelScope.launch {
            when (TokenMessageParser.identifyMessageType(cleanMessage)) {
                com.sugarcane.purchyflow.parser.CaneMessageType.TAUL_RECEIPT -> {
                    val receipt = TokenMessageParser.parseWeighmentReceipt(cleanMessage)
                    if (receipt != null) {
                        val dispatchDateEpoch = DateUtil.parseDate(receipt.dispatchDateStr)?.time ?: System.currentTimeMillis()
                        if (!DateUtil.isWithinCurrentCaneSeason(dispatchDateEpoch)) {
                            val season = DateUtil.getCurrentCaneSeason()
                            Toast.makeText(
                                context,
                                "Rejected: Date (${receipt.dispatchDateStr}) is outside current cane season (${season.seasonName}: Nov–Apr)",
                                Toast.LENGTH_LONG
                            ).show()
                            return@launch
                        }
                        val linked = repository.linkWeighmentReceipt(receipt)
                        if (linked) {
                            com.sugarcane.purchyflow.notification.PurchyNotificationHelper.notifyWeighmentCompleted(context, receipt)
                            Toast.makeText(context, "Recorded Taul: ${receipt.actualNettedWeight} for ${receipt.growerName ?: receipt.purchyNo}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Unable to record Taul receipt (check cane season or details)", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(context, "Could not parse Taul SMS format", Toast.LENGTH_LONG).show()
                    }
                }
                com.sugarcane.purchyflow.parser.CaneMessageType.PURCHY_TOKEN -> {
                    val token = TokenMessageParser.parseTokenMessage(cleanMessage)
                    if (token != null) {
                        if (!DateUtil.isWithinCurrentCaneSeason(token.issueDateEpochMs)) {
                            val season = DateUtil.getCurrentCaneSeason()
                            Toast.makeText(
                                context,
                                "Rejected: Date (${token.issueDateStr}) is outside current cane season (${season.seasonName}: Nov–Apr)",
                                Toast.LENGTH_LONG
                            ).show()
                            return@launch
                        }
                        val added = repository.saveParsedToken(token)
                        if (added) {
                            val createdToken = repository.createTokenFromParsed(token)
                            com.sugarcane.purchyflow.notification.PurchyNotificationHelper.notifyNewToken(context, createdToken)
                            Toast.makeText(context, "Added Purchy #${token.purchyNo} for ${token.growerName}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Token #${token.purchyNo} rejected (outside season or duplicate)", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Could not parse Purchy Token SMS format", Toast.LENGTH_LONG).show()
                    }
                }
                com.sugarcane.purchyflow.parser.CaneMessageType.UNKNOWN -> {
                    Toast.makeText(context, "Message format did not match Purchy or Taul pattern", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Deletes a specific token or dispatched receipt from the database.
     */
    fun deleteToken(purchyNo: String, context: Context? = null) {
        viewModelScope.launch {
            val deleted = repository.deleteToken(purchyNo)
            if (deleted && context != null) {
                Toast.makeText(context, "Deleted Purchy #$purchyNo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun saveScannedReceipt(scanned: com.sugarcane.purchyflow.parser.ScannedReceiptData, context: Context) {
        viewModelScope.launch {
            try {
                val success = repository.saveScannedReceipt(scanned)
                if (success) {
                    val weightText = scanned.netWeight?.let { "$it QTL" } ?: "Dispatched"
                    Toast.makeText(
                        context,
                        "Receipt saved: $weightText (${scanned.growerName})",
                        Toast.LENGTH_LONG
                    ).show()
                    // Switch to Dispatched tab so user sees the newly imported mill receipt immediately
                    _selectedTab.value = 1
                } else {
                    Toast.makeText(context, "Could not save weighment receipt", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error saving receipt: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun saveManualToken(token: PurchyToken, context: Context) {
        viewModelScope.launch {
            try {
                val success = repository.saveManualToken(token)
                if (success) {
                    Toast.makeText(context, "Added Token #${token.purchyNo} for ${token.growerName}", Toast.LENGTH_SHORT).show()
                    _selectedTab.value = 0
                } else {
                    Toast.makeText(context, "Could not add token (duplicate number)", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error adding token: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    fun importExcelFile(uri: android.net.Uri, context: Context) {
        viewModelScope.launch {
            try {
                _isImporting.value = true
                val summary = com.sugarcane.purchyflow.export.ExcelImporter.importFromUri(context, uri, repository)
                Toast.makeText(context, summary.message, Toast.LENGTH_LONG).show()
                if (summary.successCount > 0) {
                    _selectedTab.value = 1 // Switch to Dispatched tab to see newly imported records
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Excel import failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun exportDispatchedToExcel(context: Context) {
        viewModelScope.launch {
            try {
                _isExporting.value = true
                val dispatchedList = repository.getDispatchedTokensForExport()
                val exportList = if (dispatchedList.isNotEmpty()) dispatchedList else repository.getAllTokensForExport()
                if (exportList.isEmpty()) {
                    Toast.makeText(context, "No records available to export yet", Toast.LENGTH_LONG).show()
                    _isExporting.value = false
                    return@launch
                }

                // Generates formatted .xlsx file containing all linked fields
                val file = ExcelReportGenerator.generateExcelXlsxReport(context, exportList)
                ExcelReportGenerator.shareReportFile(context, file, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            } catch (e: Exception) {
                Toast.makeText(context, "Excel export error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun exportDispatchedToPdf(context: Context) {
        viewModelScope.launch {
            try {
                _isExporting.value = true
                val dispatchedList = repository.getDispatchedTokensForExport()
                if (dispatchedList.isEmpty()) {
                    Toast.makeText(context, "No mill-weighed dispatched records available to export yet", Toast.LENGTH_LONG).show()
                    _isExporting.value = false
                    return@launch
                }

                val file = ExcelReportGenerator.generatePdfReport(context, dispatchedList)
                ExcelReportGenerator.shareReportFile(context, file, "application/pdf")
            } catch (e: Exception) {
                Toast.makeText(context, "PDF export error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                _isExporting.value = false
            }
        }
    }
}

class PurchyViewModelFactory(private val repository: PurchyRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PurchyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PurchyViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
