package com.example.wishproject

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = AppDatabase.getDatabase(this)
        val dao = db.wishDao()

        setContent {
            WishlistApp(dao)
        }
    }
}

@Composable
fun WishlistApp(dao: WishDao) {
    var currentUser by remember { mutableStateOf<User?>(null) }
    val allUsers by dao.getAllUsers().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    if (currentUser == null) {
        LoginScreen(
            users = allUsers,
            onLogin = { user -> currentUser = user },
            onCreateUser = { name -> scope.launch { dao.insertUser(User(name = name)) } }
        )
    } else {
        DashboardScreen(
            currentUser = currentUser!!,
            allUsers = allUsers,
            dao = dao,
            onLogout = { currentUser = null }
        )
    }
}

@Composable
fun LoginScreen(users: List<User>, onLogin: (User) -> Unit, onCreateUser: (String) -> Unit) {
    var newName by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🎅 Wishlist Santa", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))
        Text("Se connecter :")
        LazyColumn(modifier = Modifier.height(150.dp).fillMaxWidth()) {
            items(users) { user ->
                Button(onClick = { onLogin(user) }, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                    Text(user.name)
                }
            }
        }
        Divider(modifier = Modifier.padding(vertical = 16.dp))
        Text("Nouveau profil :")
        OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Nom") })
        Button(onClick = { if (newName.isNotBlank()) { onCreateUser(newName); newName = "" } }) { Text("Créer") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(currentUser: User, allUsers: List<User>, dao: WishDao, onLogout: () -> Unit) {
    var viewingUser by remember { mutableStateOf(currentUser) }
    val items by dao.getItemsForUser(viewingUser.userId).collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer)) {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Bonjour ${currentUser.name} !", fontWeight = FontWeight.Bold)
                    Button(onClick = onLogout) { Text("Déco") }
                }
                Text("Voir la liste de :", modifier = Modifier.padding(start = 8.dp), fontSize = 12.sp)
                LazyColumn(modifier = Modifier.height(60.dp).padding(8.dp)) {
                    items(allUsers) { user ->
                        FilterChip(
                            selected = user.userId == viewingUser.userId,
                            onClick = { viewingUser = user },
                            label = { Text(if (user.userId == currentUser.userId) "Moi" else user.name) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (viewingUser.userId == currentUser.userId) {
                FloatingActionButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, "Ajouter") }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text(if (viewingUser.userId == currentUser.userId) "Ma Liste" else "Liste de ${viewingUser.name}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            LazyColumn {
                items(items) { item ->
                    WishItemRow(
                        item = item,
                        currentUserId = currentUser.userId,
                        listOwnerId = viewingUser.userId,
                        onLinkClick = { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } catch (e: Exception) {} },
                        onToggleReserve = {
                            val newStatus = !item.isReserved
                            scope.launch { dao.updateItem(item.copy(isReserved = newStatus, reservedByUserId = if (newStatus) currentUser.userId else null)) }
                        },
                        onDelete = { scope.launch { dao.deleteItem(item) } }
                    )
                    Divider()
                }
            }
        }
    }
    if (showAddDialog) {
        AddItemDialog(onDismiss = { showAddDialog = false }) { name, link ->
            scope.launch { dao.insertItem(WishItem(ownerId = currentUser.userId, name = name, link = link)) }
            showAddDialog = false
        }
    }
}

@Composable
fun WishItemRow(item: WishItem, currentUserId: Int, listOwnerId: Int, onLinkClick: (String) -> Unit, onToggleReserve: () -> Unit, onDelete: () -> Unit) {
    val isMyList = currentUserId == listOwnerId
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Lien >", color = Color.Blue, modifier = Modifier.clickable { onLinkClick(item.link) })
            if (!isMyList) {
                if (item.isReserved) {
                    val byMe = item.reservedByUserId == currentUserId
                    Text(if (byMe) "Réservé par VOUS" else "Déjà réservé", color = if (byMe) Color.Green else Color.Red)
                } else Text("Disponible", color = Color.Gray)
            }
        }
        if (isMyList) IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Supprimer", tint = Color.Red) }
        else if (!item.isReserved || item.reservedByUserId == currentUserId) Button(onClick = onToggleReserve) { Text(if (item.isReserved) "Annuler" else "Réserver") }
        else Icon(Icons.Default.Lock, "Bloqué", tint = Color.Gray)
    }
}

@Composable
fun AddItemDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter") },
        text = { Column { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom") }); OutlinedTextField(value = link, onValueChange = { link = it }, label = { Text("Lien") }) } },
        confirmButton = { Button(onClick = { if (name.isNotEmpty()) onAdd(name, link) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}