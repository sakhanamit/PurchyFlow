package com.sugarcane.purchyflow.parser

data class ParsedToken(
    val growerName: String,
    val growerCode: String,
    val ugc: String,
    val caneType: String,
    val purchyNo: String,
    val fc: String,
    val allottedWeight: String,
    val allottedWeightNumber: Double,
    val issueDateStr: String,
    val issueDateEpochMs: Long,
    val validUntilStr: String,
    val validUntilEpochMs: Long,
    val society: String,
    val centre: String,
    val rawMessage: String
)

data class ParsedWeighmentReceipt(
    val purchyNo: String?,
    val growerCode: String?,
    val ugc: String?,
    val growerName: String? = null,
    val societyOrMill: String? = null,
    val actualNettedWeight: String,
    val actualNettedWeightNumber: Double,
    val dispatchDateStr: String?,
    val receiptNo: String?,
    val grossWeight: String? = null,
    val tareWeight: String? = null,
    val rawMessage: String
)
