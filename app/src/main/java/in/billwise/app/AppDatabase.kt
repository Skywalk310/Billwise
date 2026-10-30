package `in`.billwise.app

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "business_profile")
data class BusinessProfile(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val phone: String = "",
    val address: String = "",
    val gstin: String = "",
    val logoPath: String = "",
    val upiId: String = "",
    val signaturePath: String = ""
)

@Entity(tableName = "clients")
data class Client(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val signaturePath: String = "",
    val stampPath: String = ""
)

@Entity(tableName = "items")
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val rate: Double
)

@Entity(tableName = "invoices")
data class Invoice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val clientId: Long?,
    val clientName: String,
    val clientPhone: String = "",
    val clientAddress: String = "",
    val dateMillis: Long,
    val dueDateMillis: Long?,
    val subtotal: Double,
    val discount: Double,
    val gstEnabled: Boolean,
    val gstRate: Double,
    val total: Double,
    val status: String, // "PAID" or "UNPAID"
    val remarks: String = ""
)

@Entity(tableName = "invoice_lines")
data class InvoiceLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val itemName: String,
    val qty: Double,
    val rate: Double,
    val amount: Double
)

@Dao
interface BillWiseDao {
    @Query("SELECT * FROM business_profile WHERE id = 1")
    suspend fun getProfile(): BusinessProfile?

    @Insert
    suspend fun insertProfile(profile: BusinessProfile)

    @Update
    suspend fun updateProfile(profile: BusinessProfile)

    @Query("SELECT * FROM clients ORDER BY name ASC")
    fun getClients(): Flow<List<Client>>

    @Query("SELECT * FROM clients ORDER BY name ASC")
    suspend fun getClientsOnce(): List<Client>

    @Insert
    suspend fun insertClient(client: Client): Long

    @Update
    suspend fun updateClient(client: Client)

    @Delete
    suspend fun deleteClient(client: Client)

    @Query("SELECT * FROM items ORDER BY name ASC")
    fun getItems(): Flow<List<Item>>

    @Query("SELECT * FROM items ORDER BY name ASC")
    suspend fun getItemsOnce(): List<Item>

    @Insert
    suspend fun insertItem(item: Item): Long

    @Update
    suspend fun updateItem(item: Item)

    @Delete
    suspend fun deleteItem(item: Item)

    @Query("SELECT * FROM invoices ORDER BY dateMillis DESC, id DESC")
    fun getInvoices(): Flow<List<Invoice>>

    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun getInvoice(id: Long): Invoice?

    @Query("SELECT MAX(id) FROM invoices")
    suspend fun getMaxInvoiceId(): Long?

    @Insert
    suspend fun insertInvoice(invoice: Invoice): Long

    @Update
    suspend fun updateInvoice(invoice: Invoice)

    @Delete
    suspend fun deleteInvoice(invoice: Invoice)

    @Query("SELECT * FROM invoice_lines WHERE invoiceId = :invoiceId ORDER BY id ASC")
    suspend fun getLines(invoiceId: Long): List<InvoiceLine>

    @Insert
    suspend fun insertLine(line: InvoiceLine)

    @Query("DELETE FROM invoice_lines WHERE invoiceId = :invoiceId")
    suspend fun deleteLines(invoiceId: Long)
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Recreate the three changed tables so the resulting schema matches Room's
        // v2 expectations exactly (plain ALTER TABLE ... ADD COLUMN ... DEFAULT ''
        // would leave a default-value mismatch that fails Room's schema check).
        db.execSQL("CREATE TABLE business_profile_new (id INTEGER NOT NULL, name TEXT NOT NULL, phone TEXT NOT NULL, address TEXT NOT NULL, gstin TEXT NOT NULL, logoPath TEXT NOT NULL, upiId TEXT NOT NULL, PRIMARY KEY(id))")
        db.execSQL("INSERT INTO business_profile_new (id, name, phone, address, gstin, logoPath, upiId) SELECT id, name, phone, address, gstin, '', '' FROM business_profile")
        db.execSQL("DROP TABLE business_profile")
        db.execSQL("ALTER TABLE business_profile_new RENAME TO business_profile")

        db.execSQL("CREATE TABLE clients_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, phone TEXT NOT NULL, address TEXT NOT NULL, signaturePath TEXT NOT NULL, stampPath TEXT NOT NULL)")
        db.execSQL("INSERT INTO clients_new (id, name, phone, address, signaturePath, stampPath) SELECT id, name, phone, address, '', '' FROM clients")
        db.execSQL("DROP TABLE clients")
        db.execSQL("ALTER TABLE clients_new RENAME TO clients")

        db.execSQL("CREATE TABLE invoices_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, number TEXT NOT NULL, clientId INTEGER, clientName TEXT NOT NULL, clientPhone TEXT NOT NULL, clientAddress TEXT NOT NULL, dateMillis INTEGER NOT NULL, dueDateMillis INTEGER, subtotal REAL NOT NULL, discount REAL NOT NULL, gstEnabled INTEGER NOT NULL, gstRate REAL NOT NULL, total REAL NOT NULL, status TEXT NOT NULL, remarks TEXT NOT NULL)")
        db.execSQL("INSERT INTO invoices_new (id, number, clientId, clientName, clientPhone, clientAddress, dateMillis, dueDateMillis, subtotal, discount, gstEnabled, gstRate, total, status, remarks) SELECT id, number, clientId, clientName, clientPhone, clientAddress, dateMillis, dueDateMillis, subtotal, discount, gstEnabled, gstRate, total, status, '' FROM invoices")
        db.execSQL("DROP TABLE invoices")
        db.execSQL("ALTER TABLE invoices_new RENAME TO invoices")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Only business_profile gains a column (the company's own signature,
        // printed on every invoice). Same recreate pattern as MIGRATION_1_2 so
        // Room's schema check matches exactly.
        db.execSQL("CREATE TABLE business_profile_new (id INTEGER NOT NULL, name TEXT NOT NULL, phone TEXT NOT NULL, address TEXT NOT NULL, gstin TEXT NOT NULL, logoPath TEXT NOT NULL, upiId TEXT NOT NULL, signaturePath TEXT NOT NULL, PRIMARY KEY(id))")
        db.execSQL("INSERT INTO business_profile_new (id, name, phone, address, gstin, logoPath, upiId, signaturePath) SELECT id, name, phone, address, gstin, logoPath, upiId, '' FROM business_profile")
        db.execSQL("DROP TABLE business_profile")
        db.execSQL("ALTER TABLE business_profile_new RENAME TO business_profile")
    }
}

@Database(
    entities = [BusinessProfile::class, Client::class, Item::class, Invoice::class, InvoiceLine::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): BillWiseDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "billwise.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
        }
    }
}
