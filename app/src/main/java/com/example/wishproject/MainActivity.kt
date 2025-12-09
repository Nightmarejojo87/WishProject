package com.example.wishproject

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. GESTION DE L'IDENTITÉ (ID UNIQUE)
        val sharedPrefs = getSharedPreferences("WishAppPrefs", Context.MODE_PRIVATE)
        var myUserId = sharedPrefs.getString("USER_ID", null)
        if (myUserId == null) {
            myUserId = UUID.randomUUID().toString()
            sharedPrefs.edit().putString("USER_ID", myUserId).apply()
        }

        val repository = FirestoreRepository()

        // 2. RÉCUPÉRATION DU LIEN DE PARTAGE
        val data: Uri? = intent?.data
        var initialListId: String? = null

        if (data != null) {
            // On cherche l'ID dans le lien, peu importe le domaine
            initialListId = data.getQueryParameter("id")
        }

        setContent {
            // On passe les SharedPreferences pour sauvegarder les listes amies
            WishlistApp(repository, myUserId!!, initialListId, sharedPrefs)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistApp(repo: FirestoreRepository, myUserId: String, deepLinkListId: String?, prefs: android.content.SharedPreferences) {
    var selectedList by remember { mutableStateOf<WishList?>(null) }
    val context = LocalContext.current

    // --- TRAITEMENT DU LIEN (PARTAGE) ---
    LaunchedEffect(deepLinkListId) {
        if (deepLinkListId != null) {
            Toast.makeText(context, "🔄 Recherche de la liste...", Toast.LENGTH_SHORT).show()
            val sharedList = repo.getListById(deepLinkListId)

            if (sharedList != null) {
                selectedList = sharedList

                // Si ce n'est pas ma liste, je l'ajoute à mes favoris (Sauvegarde locale)
                if (sharedList.ownerId != myUserId) {
                    val savedIds = prefs.getStringSet("SAVED_LISTS", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                    savedIds.add(deepLinkListId)
                    prefs.edit().putStringSet("SAVED_LISTS", savedIds).apply()
                    Toast.makeText(context, "✅ Liste ajoutée aux favoris !", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(context, "❌ Erreur : Liste introuvable", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Gestion du bouton retour physique
    BackHandler(enabled = selectedList != null) { selectedList = null }

    if (selectedList == null) {
        // Écran d'accueil (Mes listes + Listes suivies)
        HomeScreen(userId = myUserId, repo = repo, prefs = prefs, onListClick = { selectedList = it })
    } else {
        // Écran de détail (Objets)
        ListDetailScreen(
            wishList = selectedList!!,
            myUserId = myUserId,
            repo = repo,
            onBack = { selectedList = null }
        )
    }
}

// --- ÉCRAN 1 : ACCUEIL ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(userId: String, repo: FirestoreRepository, prefs: android.content.SharedPreferences, onListClick: (WishList) -> Unit) {
    // Mes listes
    val myLists by repo.getListsForUser(userId).collectAsState(initial = emptyList())

    // Listes des amis (lues depuis la mémoire du téléphone)
    val savedIds = prefs.getStringSet("SAVED_LISTS", emptySet())?.toList() ?: emptyList()
    val followedLists by repo.getListsByIds(savedIds).collectAsState(initial = emptyList())

    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Mes Listes") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) },
        floatingActionButton = { FloatingActionButton(onClick = { showCreateDialog = true }) { Icon(Icons.Default.Add, "Créer") } }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {

            // SECTION 1 : MES LISTES
            item { Text("👑 Mes Créations", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
            if (myLists.isEmpty()) item { Text("Aucune liste créée.", color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp)) }
            items(myLists) { list ->
                ListCard(list, isMine = true, onDelete = { repo.deleteList(list.listId) }, onClick = { onListClick(list) })
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            // SECTION 2 : LISTES DES AMIS
            if (savedIds.isNotEmpty()) {
                item { Text("🎁 Listes des Amis", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary) }
                if (followedLists.isEmpty()) item { Text("Chargement...", color = Color.Gray) }
                items(followedLists) { list ->
                    ListCard(list, isMine = false, onDelete = {
                        // Ici "Supprimer" veut dire "Ne plus suivre"
                        val newIds = savedIds.toMutableSet()
                        newIds.remove(list.listId)
                        prefs.edit().putStringSet("SAVED_LISTS", newIds).apply()
                    }, onClick = { onListClick(list) })
                }
            }
        }
    }

    if (showCreateDialog) {
        SimpleDialog(title = "Nouvelle Liste", label = "Titre", onDismiss = { showCreateDialog = false }) { title ->
            repo.createList(userId, title)
            showCreateDialog = false
        }
    }
}

@Composable
fun ListCard(list: WishList, isMine: Boolean, onDelete: () -> Unit, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onClick() }, elevation = CardDefaults.cardElevation(4.dp)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(list.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (!isMine) Text("Partagé avec moi", fontSize = 12.sp, color = Color.Gray)
            }
            Row {
                IconButton(onClick = onDelete) { Icon(if(isMine) Icons.Default.Delete else Icons.Default.Close, "Action", tint = if(isMine) Color.Red else Color.Gray) }
                Icon(Icons.Default.KeyboardArrowRight, "Ouvrir")
            }
        }
    }
}

// --- ÉCRAN 2 : DÉTAIL DES OBJETS ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailScreen(wishList: WishList, myUserId: String, repo: FirestoreRepository, onBack: () -> Unit) {
    val items by repo.getItemsInList(wishList.listId).collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isMyList = (wishList.ownerId == myUserId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(wishList.title)
                        if(!isMyList) Text("Mode Père Noël 🎅", fontSize = 12.sp, color = Color.Red)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Retour") } },
                actions = {
                    if (isMyList) {
                        IconButton(onClick = {
                            // METTRE ICI TON LIEN FIREBASE (Celui de ton Manifest)
                            val domain = "wishproject-27a8b.web.app"
                            val link = "https://$domain/partage?id=${wishList.listId}"
                            val msg = "🎁 Ma liste \"${wishList.title}\" !\nInstalle l'appli puis clique :\n$link"
                            val intent = Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_TEXT, msg); type = "text/plain" }
                            context.startActivity(Intent.createChooser(intent, "Partager"))
                        }) { Icon(Icons.Default.Share, "Partager") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        },
        floatingActionButton = {
            if (isMyList) {
                FloatingActionButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, "Ajouter") }
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {
            items(items) { item ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        if (item.link.isNotBlank()) {
                            Text("Voir le lien >", color = Color.Blue, modifier = Modifier.clickable {
                                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.link))) } catch (e: Exception) {}
                            })
                        }
                        // Statut visible pour les amis
                        if (!isMyList) {
                            if (item.isReserved) {
                                val reservedByMe = item.reservedByUserId == myUserId
                                Text(if (reservedByMe) "Réservé par VOUS" else "Déjà réservé", color = if (reservedByMe) Color(0xFF4CAF50) else Color.Red, fontWeight = FontWeight.Bold)
                            } else Text("Disponible", color = Color.Gray)
                        }
                    }

                    // BOUTONS D'ACTION
                    if (isMyList) {
                        IconButton(onClick = { repo.deleteItem(item.itemId) }) { Icon(Icons.Default.Delete, "Supprimer", tint = Color.Red) }
                    } else {
                        // Logique de réservation
                        if (!item.isReserved || item.reservedByUserId == myUserId) {
                            Button(
                                onClick = {
                                    // VÉRIFICATION CRITIQUE DE L'ID
                                    if (item.itemId.isBlank()) {
                                        Toast.makeText(context, "Erreur : ID de l'objet manquant. Supprimez et recréez l'objet.", Toast.LENGTH_LONG).show()
                                    } else {
                                        val newStatus = !item.isReserved
                                        val newReserver = if (newStatus) myUserId else null
                                        repo.updateItem(item.copy(isReserved = newStatus, reservedByUserId = newReserver))

                                        // Feedback visuel
                                        val feedback = if (newStatus) "C'est réservé ! 🎁" else "Réservation annulée"
                                        Toast.makeText(context, feedback, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = if (item.isReserved) Color.Gray else Color(0xFFE91E63))
                            ) { Text(if (item.isReserved) "Annuler" else "Réserver") }
                        } else {
                            Icon(Icons.Default.Lock, "Bloqué", tint = Color.Gray)
                        }
                    }
                }
                Divider()
            }
        }
    }
    if (showAddDialog) {
        AddItemDialog(onDismiss = { showAddDialog = false }) { name, link ->
            repo.addItem(wishList.listId, name, link)
            showAddDialog = false
        }
    }
}

// --- DIALOGUES ---

@Composable
fun SimpleDialog(title: String, label: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(label) }) },
        confirmButton = { Button(onClick = { if (text.isNotBlank()) onConfirm(text) }) { Text("Ok") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
fun AddItemDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter objet") },
        text = { Column { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom") }); OutlinedTextField(value = link, onValueChange = { link = it }, label = { Text("Lien") }) } },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) onConfirm(name, link) }) { Text("Ok") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}