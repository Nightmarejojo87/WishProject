package com.example.wishproject

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// --- LES TABLES ---
@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val userId: Int = 0,
    val name: String
)

@Entity(tableName = "items")
data class WishItem(
    @PrimaryKey(autoGenerate = true) val itemId: Int = 0,
    val ownerId: Int,
    val name: String,
    val link: String,
    val isReserved: Boolean = false,
    val reservedByUserId: Int? = null
)

// --- LE DAO ---
@Dao
interface WishDao {
    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<User>>

    @Insert
    suspend fun insertUser(user: User)

    @Query("SELECT * FROM items WHERE ownerId = :ownerId")
    fun getItemsForUser(ownerId: Int): Flow<List<WishItem>>

    @Insert
    suspend fun insertItem(item: WishItem)

    @Update
    suspend fun updateItem(item: WishItem)

    @Delete
    suspend fun deleteItem(item: WishItem)
}

// --- LA CONFIGURATION ---
@Database(entities = [User::class, WishItem::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wishDao(): WishDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wishlist_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}