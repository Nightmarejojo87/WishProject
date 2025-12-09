package com.example.wishproject

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.PropertyName // <--- L'IMPORT IMPORTANT
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

// --- 1. LES DONNÉES ---

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

    // --- LE CORRECTIF EST ICI ---
    // On force Firebase à bien lire/écrire ce champ, même s'il commence par "is"
    @get:PropertyName("isReserved")
    val isReserved: Boolean = false,

    val reservedByUserId: String? = null
)

// --- 2. LE REPOSITORY ---

class FirestoreRepository {
    private val db = FirebaseFirestore.getInstance()

    // --- GESTION DES LISTES ---

    fun createList(ownerId: String, title: String) {
        val newDoc = db.collection("lists").document()
        newDoc.set(WishList(listId = newDoc.id, ownerId = ownerId, title = title))
    }

    suspend fun getListById(listId: String): WishList? {
        return try {
            val snapshot = db.collection("lists").document(listId).get().await()
            snapshot.toObject(WishList::class.java)?.copy(listId = snapshot.id)
        } catch (e: Exception) { null }
    }

    fun getListsForUser(userId: String): Flow<List<WishList>> = callbackFlow {
        val subscription = db.collection("lists").whereEqualTo("ownerId", userId)
            .addSnapshotListener { s, _ ->
                if (s != null) {
                    val lists = s.documents.mapNotNull { doc -> doc.toObject(WishList::class.java)?.copy(listId = doc.id) }
                    trySend(lists)
                }
            }
        awaitClose { subscription.remove() }
    }

    fun getListsByIds(listIds: List<String>): Flow<List<WishList>> = callbackFlow {
        if (listIds.isEmpty()) { trySend(emptyList()); awaitClose {}; return@callbackFlow }
        val subscription = db.collection("lists").whereIn(com.google.firebase.firestore.FieldPath.documentId(), listIds)
            .addSnapshotListener { s, _ ->
                if (s != null) {
                    val lists = s.documents.mapNotNull { doc -> doc.toObject(WishList::class.java)?.copy(listId = doc.id) }
                    trySend(lists)
                }
            }
        awaitClose { subscription.remove() }
    }

    fun deleteList(listId: String) { db.collection("lists").document(listId).delete() }

    // --- GESTION DES ITEMS ---

    fun addItem(listId: String, name: String, link: String) {
        val newDoc = db.collection("items").document()
        newDoc.set(WishItem(itemId = newDoc.id, listId = listId, name = name, link = link))
    }

    fun getItemsInList(listId: String): Flow<List<WishItem>> = callbackFlow {
        val subscription = db.collection("items").whereEqualTo("listId", listId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val items = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(WishItem::class.java)?.copy(itemId = doc.id)
                    }
                    trySend(items)
                }
            }
        awaitClose { subscription.remove() }
    }

    // Mise à jour ciblée (Plus fiable pour le boolean)
    fun updateItem(item: WishItem) {
        if (item.itemId.isNotBlank()) {
            val updates = mapOf(
                "isReserved" to item.isReserved, // On force le nom ici aussi
                "reservedByUserId" to item.reservedByUserId
            )
            db.collection("items").document(item.itemId).update(updates)
        }
    }

    fun deleteItem(itemId: String) {
        if(itemId.isNotBlank()) db.collection("items").document(itemId).delete()
    }
}