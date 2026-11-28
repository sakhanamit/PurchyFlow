package com.sugarcane.purchyflow.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.sugarcane.purchyflow.data.model.PurchyToken
import com.sugarcane.purchyflow.data.model.TokenStatus
import com.sugarcane.purchyflow.security.KeyStoreManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

class Converters {
    @TypeConverter
    fun fromTokenStatus(value: TokenStatus): String = value.name

    @TypeConverter
    fun toTokenStatus(value: String): TokenStatus = try {
        TokenStatus.valueOf(value)
    } catch (e: Exception) {
        TokenStatus.ACTIVE
    }
}

@Database(
    entities = [PurchyToken::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PurchyDatabase : RoomDatabase() {

    abstract fun purchyDao(): PurchyDao

    companion object {
        @Volatile
        private var INSTANCE: PurchyDatabase? = null

        fun getDatabase(context: Context): PurchyDatabase {
            return INSTANCE ?: synchronized(this) {
                try {
                    System.loadLibrary("sqlcipher")
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
                val passphrase = KeyStoreManager.getOrCreateDatabasePassphrase(context.applicationContext)
                val factory = SupportOpenHelperFactory(passphrase)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PurchyDatabase::class.java,
                    "purchy_flow_encrypted.db"
                )
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
