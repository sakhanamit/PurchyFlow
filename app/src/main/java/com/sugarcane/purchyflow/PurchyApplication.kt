package com.sugarcane.purchyflow

import android.app.Application
import com.sugarcane.purchyflow.data.local.PurchyDatabase
import com.sugarcane.purchyflow.data.repository.PurchyRepository
import com.sugarcane.purchyflow.notification.PurchyNotificationHelper

class PurchyApplication : Application() {

    val database by lazy { PurchyDatabase.getDatabase(this) }
    val repository by lazy { PurchyRepository(database.purchyDao()) }

    override fun onCreate() {
        super.onCreate()
        try {
            System.loadLibrary("sqlcipher")
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        // Initialize notification channel for instant alerts
        PurchyNotificationHelper.createNotificationChannel(this)
    }
}
