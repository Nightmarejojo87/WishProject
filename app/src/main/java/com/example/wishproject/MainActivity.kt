package com.example.wishproject

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.* // Importe toutes les icônes
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

        // 1. IDENTITÉ
        val sharedPrefs = getSharedPreferences("WishAppPrefs", Context.MODE_PRIVATE)
        var myUserId = sharedPrefs.getString("USER_ID", null)
        if (myUserId == null) {
            myUserId = UUID.randomUUID().toString()
            sharedPrefs.edit().putString("USER_ID", myUserId).apply()
        }

        val repository = FirestoreRepository()

        // 2. GESTION DU LIEN DE PARTAGE (Deep Link)
        val data: Uri? = intent?.data
        var initialListId: String? = null

        if (data != null && data.scheme == "santacloud" && data.host == "liste") {
            initialListId = data.getQueryParameter("id")
        }

        setContent {
            WishlistApp(repository, myUserId!!, initialListId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistApp(repo: FirestoreRepository, myUserId: String, deepLinkListId: String?) {
    // État : Quelle liste est affichée ?
    var selectedList by remember { mutableStateOf<WishList?>(null) }

    // Si on a ouvert l'appli via un lien, on charge la liste immédiatement
    LaunchedEffect(deepLinkListId) {
        if (deepLinkListId != null) {
            val sharedList = repo.getListById(deepLinkListId)
            if (sharedList != null) {
                selectedList = sharedList
            }
        }
    }

    // Gestion du bouton retour physique du téléphone
    BackHandler(enabled = selectedList != null) {
        selectedList = null // On revient à l'accueil
    }

    if (selectedList == null) {
        HomeScreen(userId = myUserId, repo = repo, onListClick = { selectedList = it })
    } else {
        ListDetailScreen(
            wishList = selectedList!!,
            myUserId = myUserId, // On passe ton ID pour savoir si tu es le créateur ou le père noël
            repo = repo,
            onBack = { selectedList = null }
        )
    }
}

// --- ÉCRAN 1 : MES LISTES ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(userId: String, repo: FirestoreRepository, onListClick: (WishList) -> Unit) {
    val myLists by repo.getListsForUser(userId).collectAsState(initial = emptyList())
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Mes Listes") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) },
        floatingActionButton = { FloatingActionButton(onClick = { showCreateDialog = true }) { Icon(Icons.Default.Add, "Créer") } }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            if (myLists.isEmpty()) Text("Aucune liste. Créez-en une !", color = Color.Gray)
            LazyColumn {
                items(myLists) { list ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onListClick(list) }, elevation = CardDefaults.cardElevation(4.dp)) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(list.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Row {
                                IconButton(onClick = { repo.deleteList(list.listId) }) { Icon(Icons.Default.Delete, "Supprimer", tint = Color.Gray) }
                                Icon(Icons.Default.KeyboardArrowRight, "Ouvrir")
                            }
                        }
                    }
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

// --- ÉCRAN 2 : DÉTAIL (AVEC LOGIQUE PÈRE NOËL) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailScreen(wishList: WishList, myUserId: String, repo: FirestoreRepository, onBack: () -> Unit) {
    val items by repo.getItemsInList(wishList.listId).collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // VERIFICATION : Est-ce MA liste ?
    val isMyList = (wishList.ownerId == myUserId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(wishList.title)
                        if(!isMyList) Text("Mode Père Noël activé 🎅", fontSize = 12.sp, color = Color.Red)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Retour") } },
                actions = {
                    // BOUTON PARTAGER (Seulement si c'est MA liste)
                    if (isMyList) {
                        IconButton(onClick = {
                            val link = "santacloud://liste?id=${wishList.listId}"
                            val msg = """
                                Salut ! 🎅
                                J'ai créé une liste "${wishList.title}" sur l'appli.
                                1. Installe l'appli.
                                2. Clique ici : $link
                            """.trimIndent()
                            val intent = Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_TEXT, msg); type = "text/plain" }
                            context.startActivity(Intent.createChooser(intent, "Partager"))
                        }) {
                            Icon(Icons.Default.Share, "Partager")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        },
        floatingActionButton = {
            // Seul le créateur peut ajouter des objets
            if (isMyList) {
                FloatingActionButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, "Ajouter") }
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {
            items(items) { item ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    // GAUCHE : Infos Objet
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        if (item.link.isNotBlank()) {
                            Text("Voir le lien >", color = Color.Blue, modifier = Modifier.clickable {
                                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.link))) } catch (e: Exception) {}
                            })
                        }

                        // INFO RÉSERVATION (Visible uniquement pour les invités)
                        if (!isMyList) {
                            if (item.isReserved) {
                                val reservedByMe = item.reservedByUserId == myUserId
                                Text(if (reservedByMe) "Réservé par VOUS" else "Déjà réservé", color = if (reservedByMe) Color(0xFF4CAF50) else Color.Red, fontWeight = FontWeight.Bold)
                            } else {
                                Text("Disponible", color = Color.Gray)
                            }
                        }
                    }

                    // DROITE : Actions
                    if (isMyList) {
                        // CRÉATEUR : Supprimer
                        IconButton(onClick = { repo.deleteItem(item.itemId) }) { Icon(Icons.Default.Delete, "Supprimer", tint = Color.Red) }
                    } else {
                        // INVITÉ : Réserver
                        if (!item.isReserved || item.reservedByUserId == myUserId) {
                            Button(
                                onClick = {
                                    val newStatus = !item.isReserved
                                    val newReserver = if (newStatus) myUserId else null
                                    repo.updateItem(item.copy(isReserved = newStatus, reservedByUserId = newReserver))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = if (item.isReserved) Color.Gray else Color(0xFFE91E63))
                            ) {
                                Text(if (item.isReserved) "Annuler" else "Réserver")
                            }
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