package com.example.wishproject

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

// --- DONNÉES ---
data class WishList(
    val listId: String = "",
    val ownerId: String = "",
    val title: String = ""
)

data class WishItem(
    val itemId: String = "",
    val listId: String = "",
    val name: String = "",
    val link: String = "",
    val isReserved: Boolean = false,
    val reservedByUserId: String? = null
)

// --- REPOSITORY ---
class FirestoreRepository {
    private val db = FirebaseFirestore.getInstance()

    // 1. GESTION LISTES
    fun createList(ownerId: String, title: String) {
        val newDoc = db.collection("lists").document()
        newDoc.set(WishList(listId = newDoc.id, ownerId = ownerId, title = title))
    }

    // Récupérer une liste spécifique via son ID (Pour le partage)
    suspend fun getListById(listId: String): WishList? {
        return try {
            val snapshot = db.collection("lists").document(listId).get().await()
            snapshot.toObject(WishList::class.java)
        } catch (e: Exception) { null }
    }

    fun getListsForUser(userId: String): Flow<List<WishList>> = callbackFlow {
        val subscription = db.collection("lists").whereEqualTo("ownerId", userId)
            .addSnapshotListener { s, _ -> if (s != null) trySend(s.toObjects(WishList::class.java)) }
        awaitClose { subscription.remove() }
    }

    fun deleteList(listId: String) { db.collection("lists").document(listId).delete() }

    // 2. GESTION ITEMS
    fun addItem(listId: String, name: String, link: String) {
        val newDoc = db.collection("items").document()
        newDoc.set(WishItem(itemId = newDoc.id, listId = listId, name = name, link = link))
    }

    // Pour réserver un objet
    fun updateItem(item: WishItem) {
        if (item.itemId.isNotEmpty()) db.collection("items").document(item.itemId).set(item)
    }

    fun getItemsInList(listId: String): Flow<List<WishItem>> = callbackFlow {
        val subscription = db.collection("items").whereEqualTo("listId", listId)
            .addSnapshotListener { s, _ -> if (s != null) trySend(s.toObjects(WishItem::class.java)) }
        awaitClose { subscription.remove() }
    }

    fun deleteItem(itemId: String) { db.collection("items").document(itemId).delete() }
}