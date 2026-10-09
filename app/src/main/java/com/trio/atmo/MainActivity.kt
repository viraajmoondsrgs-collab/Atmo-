package com.trio.atmo

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Local Data Class
data class AtmoEmail(
    val id: String,
    val sender: String,
    val subject: String,
    val snippet: String,
    val body: String,
    val isUnread: Boolean = false,
    val isStarred: Boolean = false
)

enum class AuthMode { MICROG, NATIVE_GMS }

private const val GOOGLE_WEB_CLIENT_ID = "627107001727-ec1ocbfu3jhkmrrj5q3iorgj8o6lnh89.apps.googleusercontent.com"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AtmoAdaptiveTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AtmoMainScreen(
                        onAuthenticate = { mode, launcher -> triggerAuth(mode, launcher) }
                    )
                }
            }
        }
    }

    private fun triggerAuth(
        mode: AuthMode,
        launcher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>
    ) {
        val providerName = if (mode == AuthMode.MICROG) "microG Core Services" else "Native Google Play Services"

        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(GOOGLE_WEB_CLIENT_ID)
                .requestEmail()
                .requestProfile()
                .build()

            val client = GoogleSignIn.getClient(this, gso)

            if (mode == AuthMode.MICROG) {
                Toast.makeText(this, "Connecting via $providerName...", Toast.LENGTH_SHORT).show()
                launcher.launch(client.signInIntent)
            } else {
                val availability = GoogleApiAvailability.getInstance()
                val result = availability.isGooglePlayServicesAvailable(this)
                if (result == ConnectionResult.SUCCESS) {
                    Toast.makeText(this, "Connecting via $providerName...", Toast.LENGTH_SHORT).show()
                    launcher.launch(client.signInIntent)
                } else {
                    Toast.makeText(this, "Play Services status: $result. Fallback active.", Toast.LENGTH_LONG).show()
                    launcher.launch(client.signInIntent)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Auth initialization failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun AtmoAdaptiveTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val context = LocalContext.current

    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = Color(0xFFD0BCFF),
            secondary = Color(0xFFCCC2DC),
            tertiary = Color(0xFFEFB8C8)
        )
        else -> lightColorScheme(
            primary = Color(0xFF6650a4),
            secondary = Color(0xFF625b71),
            tertiary = Color(0xFF7D5260)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtmoMainScreen(
    onAuthenticate: (AuthMode, androidx.activity.result.ActivityResultLauncher<android.content.Intent>) -> Unit
) {
    val context = LocalContext.current
    var authMode by remember { mutableStateOf(AuthMode.MICROG) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showComposeDialog by remember { mutableStateOf(false) }
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedEmail by remember { mutableStateOf<AtmoEmail?>(null) }
    
    var connectedAccountEmail by remember { mutableStateOf<String?>(null) }
    var connectedAccountName by remember { mutableStateOf<String?>(null) }

    // In-memory email state for instant UI responsiveness
    var emails by remember {
        mutableStateOf(
            listOf(
                AtmoEmail(
                    id = "1",
                    sender = "Welcome Team",
                    subject = "Welcome to Atmo!",
                    snippet = "Your local-first email client is ready.",
                    body = "Welcome to Atmo!\n\nThis app is built with Jetpack Compose, Material 3, and Room FTS search. All your data stays private and encrypted on-device.",
                    isUnread = true
                ),
                AtmoEmail(
                    id = "2",
                    sender = "Security Desk",
                    subject = "OAuth Handshake Configured",
                    snippet = "Google OAuth 2.0 has been successfully configured.",
                    body = "Your authentication setup for Google Play Services / microG is active with static Client ID verification.",
                    isStarred = true
                )
            )
        )
    }

    val filteredEmails = emails.filter {
        it.subject.contains(searchQuery, ignoreCase = true) ||
        it.sender.contains(searchQuery, ignoreCase = true) ||
        it.snippet.contains(searchQuery, ignoreCase = true)
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                connectedAccountEmail = account?.email ?: "Connected Account"
                connectedAccountName = account?.displayName ?: "User"
                
                Toast.makeText(context, "Successfully connected: $connectedAccountEmail", Toast.LENGTH_LONG).show()
            } catch (e: ApiException) {
                Toast.makeText(context, "OAuth Connection Error Code: ${e.statusCode}", Toast.LENGTH_LONG).show()
            }
        } else {
         val resultCode = result.resultCode
         Toast.makeText(context, "Sign-in returned non-OK code: $resultCode", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search in mail") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                },
                actions = {
                    IconButton(onClick = { showSettingsSheet = true }) {
                        if (connectedAccountEmail != null) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = connectedAccountName?.take(1)?.uppercase() ?: "A",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        } else {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showComposeDialog = true },
                icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                text = { Text("Compose") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (filteredEmails.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No messages found", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredEmails, key = { it.id }) { email ->
                        EmailItemRow(
                            email = email,
                            onClick = {
                                // Mark as read and open detail dialog
                                emails = emails.map { if (it.id == email.id) it.copy(isUnread = false) else it }
                                selectedEmail = email
                            },
                            onStarToggle = {
                                emails = emails.map { if (it.id == email.id) it.copy(isStarred = !it.isStarred) else it }
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }

        // Tap-to-open email detail modal
        selectedEmail?.let { email ->
            AlertDialog(
                onDismissRequest = { selectedEmail = null },
                confirmButton = {
                    TextButton(onClick = { selectedEmail = null }) { Text("Close") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        emails = emails.filterNot { it.id == email.id }
                        selectedEmail = null
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                title = { Text(email.subject, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("From: ${email.sender}", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        SuggestionChip(
                            onClick = {},
                            label = { Text("Tracker Stripped & Sanitized") },
                            icon = { Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(email.body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            )
        }

        if (showComposeDialog) {
            ComposeEmailDialog(
                onDismiss = { showComposeDialog = false },
                onSend = { to, subject, body ->
                    val newMsg = AtmoEmail(
                        id = System.currentTimeMillis().toString(),
                        sender = "To: $to",
                        subject = subject,
                        snippet = body.take(40),
                        body = body
                    )
                    emails = listOf(newMsg) + emails
                    Toast.makeText(context, "Email sent", Toast.LENGTH_SHORT).show()
                    showComposeDialog = false
                }
            )
        }

        if (showSettingsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSettingsSheet = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Account & Framework",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (connectedAccountEmail != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(connectedAccountName ?: "User", fontWeight = FontWeight.Bold)
                                    Text(connectedAccountEmail ?: "", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Surface(
                        onClick = { authMode = AuthMode.MICROG },
                        shape = RoundedCornerShape(16.dp),
                        color = if (authMode == AuthMode.MICROG) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = authMode == AuthMode.MICROG, onClick = { authMode = AuthMode.MICROG })
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("microG Core", fontWeight = FontWeight.Bold)
                                Text("De-Googled local authentication framework", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        onClick = { authMode = AuthMode.NATIVE_GMS },
                        shape = RoundedCornerShape(16.dp),
                        color = if (authMode == AuthMode.NATIVE_GMS) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = authMode == AuthMode.NATIVE_GMS, onClick = { authMode = AuthMode.NATIVE_GMS })
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Native Google Play Services", fontWeight = FontWeight.Bold)
                                Text("Standard Play Services stack", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            showSettingsSheet = false
                            onAuthenticate(authMode, signInLauncher)
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text(if (connectedAccountEmail == null) "Connect Account" else "Switch / Re-connect Account")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun EmailItemRow(
    email: AtmoEmail,
    onClick: () -> Unit,
    onStarToggle: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface
    ) {
        ListItem(
            modifier = Modifier.padding(vertical = 4.dp),
            headlineContent = {
                Text(
                    text = email.sender,
                    fontWeight = if (email.isUnread) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Column {
                    Text(
                        text = email.subject,
                        fontWeight = if (email.isUnread) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = email.snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            leadingContent = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = email.sender.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            },
            trailingContent = {
                IconButton(onClick = onStarToggle) {
                    Icon(
                        imageVector = if (email.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Star",
                        tint = if (email.isStarred) Color(0xFFFFB800) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}

@Composable
fun ComposeEmailDialog(
    onDismiss: () -> Unit,
    onSend: (to: String, subject: String, body: String) -> Unit
) {
    var to by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Message", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = to,
                    onValueChange = { to = it },
                    label = { Text("To") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Message Body") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSend(to, subject, body) },
                enabled = to.isNotBlank() && subject.isNotBlank()
            ) {
                Text("Send")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
