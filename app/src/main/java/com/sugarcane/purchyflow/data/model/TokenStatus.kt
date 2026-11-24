package com.sugarcane.purchyflow.data.model

enum class TokenStatus {
    ACTIVE,
    DISPATCHED,
    EXPIRED,
    ARCHIVED
}

enum class UrgencyLevel {
    EXPIRING_TODAY,  // Red: <= 0 days or expires today
    ONE_DAY_LEFT,    // Amber: 1 day left
    TWO_DAYS_LEFT,   // Green: 2 days left
    EXPIRED          // Grey: past validity date
}
