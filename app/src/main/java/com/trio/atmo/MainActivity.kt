package com.trio.atmo

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class EmailCategory {
    PRIMARY, PROMOTIONS, UPDATES, SENT
}

data class EmailAttachment(
    val filename: String,
    val mimeType: String,
    val attachmentId: String
)

data class CategorizedEmail(
    val id: String,
    val threadId: String,
    val snippet: String,
    val subject: String,
    val sender: String,
    val category: EmailCategory,
    val attachments: List<EmailAttachment> = emptyList()
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AtmoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun AtmoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(),
        content = content
    )
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs: SharedPreferences = remember {
        context.getSharedPreferences("AtmoPrefs", Context.MODE_PRIVATE)
    }

    var signedInEmail by remember {
        mutableStateOf(prefs.getString("logged_in_email", null))
    }

    var selectedTab by remember { mutableStateOf(EmailCategory.PRIMARY) }
    var emailsMap by remember { mutableStateOf<Map<EmailCategory, List<CategorizedEmail>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(false) }

    // Detail & Compose UI States
    var selectedEmailForDetail by remember { mutableStateOf<CategorizedEmail?>(null) }
    var emailBodyText by remember { mutableStateOf<String?>(null) }
    var isFetchingBody by remember { mutableStateOf(false) }

    var showComposeSheet by remember { mutableStateOf(false) }
    var composeTo by remember { mutableStateOf("") }
    var composeSubject by remember { mutableStateOf("") }
    var composeBody by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    // Account permission handler
    fun getDeviceGoogleAccounts(): List<String> {
        val accountManager = AccountManager.get(context)
        return try {
            accountManager.getAccountsByType("com.google").map { it.name }
        } catch (_: Exception) {
            emptyList()
        }
    }

    var availableAccounts by remember { mutableStateOf<List<String>>(emptyList()) }

    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (signedInEmail != null) {
            coroutineScope.launch {
                isLoading = true
                emailsMap = fetchAllMailSections(context, signedInEmail!!) { intent ->
                    // Recoverable Auth triggered
                }
                isLoading = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            availableAccounts = getDeviceGoogleAccounts()
        } else {
            Toast.makeText(context, "Account permission required to select accounts", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.GET_ACCOUNTS) == PackageManager.PERMISSION_GRANTED) {
            availableAccounts = getDeviceGoogleAccounts()
        } else {
            permissionLauncher.launch(Manifest.permission.GET_ACCOUNTS)
        }
    }

    // Auto-fetch emails on startup or account switch
    LaunchedEffect(signedInEmail) {
        if (signedInEmail != null) {
            isLoading = true
            emailsMap = fetchAllMailSections(context, signedInEmail!!) { intent ->
                authLauncher.launch(intent)
            }
            isLoading = false
        }
    }

    Scaffold(
        floatingActionButton = {
            if (signedInEmail != null) {
                FloatingActionButton(onClick = { showComposeSheet = true }) {
                    Text("+ Compose")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            if (signedInEmail == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Select an Account for Atmo", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(16.dp))

                        if (availableAccounts.isEmpty()) {
                            Text("No Google accounts found or permission missing.", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = {
                                permissionLauncher.launch(Manifest.permission.GET_ACCOUNTS)
                                availableAccounts = getDeviceGoogleAccounts()
                            }) {
                                Text("Grant Permission & Refresh")
                            }
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                                items(availableAccounts) { accountEmail ->
                                    Button(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        onClick = {
                                            prefs.edit().putString("logged_in_email", accountEmail).apply()
                                            signedInEmail = accountEmail
                                            Toast.makeText(context, "Selected $accountEmail", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Text(accountEmail)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Account: $signedInEmail", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = {
                        prefs.edit().remove("logged_in_email").apply()
                        signedInEmail = null
                        emailsMap = emptyMap()
                        availableAccounts = getDeviceGoogleAccounts()
                        Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Switch")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                ScrollableTabRow(selectedTabIndex = selectedTab.ordinal) {
                    EmailCategory.values().forEach { category ->
                        Tab(
                            selected = selectedTab == category,
                            onClick = { selectedTab = category },
                            text = { Text(category.name) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val currentEmails = emailsMap[selectedTab] ?: emptyList()
                    if (currentEmails.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text("No messages in ${selectedTab.name}")
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            items(currentEmails) { email ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            selectedEmailForDetail = email
                                            coroutineScope.launch {
                                                isFetchingBody = true
                                                emailBodyText = fetchEmailBody(context, signedInEmail!!, email.id)
                                                isFetchingBody = false
                                            }
                                        },
                                    elevation = CardDefaults.cardElevation(2.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(text = email.subject, style = MaterialTheme.typography.titleMedium)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = "From: ${email.sender}", style = MaterialTheme.typography.bodySmall)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(text = email.snippet, style = MaterialTheme.typography.bodyMedium)

                                        if (email.attachments.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "📎 Attachments: ${email.attachments.joinToString { it.filename }}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Detail View Dialog
    selectedEmailForDetail?.let { email ->
        AlertDialog(
            onDismissRequest = {
                selectedEmailForDetail = null
                emailBodyText = null
            },
            title = { Text(email.subject) },
            text = {
                Column {
                    Text("From: ${email.sender}", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isFetchingBody) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else {
                        Text(
                            text = emailBodyText ?: email.snippet,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedEmailForDetail = null
                    emailBodyText = null
                }) {
                    Text("Close")
                }
            }
        )
    }

    // Compose Email Dialog Sheet
    if (showComposeSheet) {
        AlertDialog(
            onDismissRequest = { if (!isSending) showComposeSheet = false },
            title = { Text("New Message") },
            text = {
                Column {
                    OutlinedTextField(
                        value = composeTo,
                        onValueChange = { composeTo = it },
                        label = { Text("To") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = composeSubject,
                        onValueChange = { composeSubject = it },
                        label = { Text("Subject") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = composeBody,
                        onValueChange = { composeBody = it },
                        label = { Text("Message Body") },
                        modifier = Modifier.fillMaxWidth().height(120.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !isSending && composeTo.isNotEmpty(),
                    onClick = {
                        coroutineScope.launch {
                            isSending = true
                            val success = sendEmailApi(context, signedInEmail!!, composeTo, composeSubject, composeBody)
                            isSending = false
                            if (success) {
                                Toast.makeText(context, "Email Sent!", Toast.LENGTH_SHORT).show()
                                showComposeSheet = false
                                composeTo = ""
                                composeSubject = ""
                                composeBody = ""
                            } else {
                                Toast.makeText(context, "Failed to send email", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text(if (isSending) "Sending..." else "Send")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isSending,
                    onClick = { showComposeSheet = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

// Background Mail List Fetcher with Recoverable Consent Handling
suspend fun fetchAllMailSections(
    context: Context,
    accountEmail: String,
    onAuthRequired: (android.content.Intent) -> Unit
): Map<EmailCategory, List<CategorizedEmail>> {
    return withContext(Dispatchers.IO) {
        val result = mutableMapOf<EmailCategory, MutableList<CategorizedEmail>>()
        EmailCategory.values().forEach { result[it] = mutableListOf() }

        try {
            val account = Account(accountEmail, "com.google")
            val scope = "oauth2:https://www.googleapis.com/auth/gmail.readonly"
            val token = try {
                GoogleAuthUtil.getToken(context, account, scope)
            } catch (e: UserRecoverableAuthException) {
                withContext(Dispatchers.Main) { onAuthRequired(e.intent) }
                return@withContext result
            }

            fun fetchMessagesForLabel(label: String, category: EmailCategory) {
                val listUrl = URL("https://gmail.googleapis.com/gmail/v1/users/me/messages?labelIds=$label&maxResults=10")
                val listConn = (listUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $token")
                }

                if (listConn.responseCode == HttpURLConnection.HTTP_OK) {
                    val json = listConn.inputStream.bufferedReader().use { it.readText() }
                    val messages = JSONObject(json).optJSONArray("messages")

                    if (messages != null) {
                        for (i in 0 until messages.length()) {
                            val msgObj = messages.getJSONObject(i)
                            val id = msgObj.getString("id")
                            val threadId = msgObj.getString("threadId")

                            var subject = "No Subject"
                            var sender = "Unknown"
                            var snippet = ""
                            val attachments = mutableListOf<EmailAttachment>()

                            try {
                                val detailUrl = URL("https://gmail.googleapis.com/gmail/v1/users/me/messages/$id")
                                val detailConn = (detailUrl.openConnection() as HttpURLConnection).apply {
                                    requestMethod = "GET"
                                    setRequestProperty("Authorization", "Bearer $token")
                                }

                                if (detailConn.responseCode == HttpURLConnection.HTTP_OK) {
                                    val detailJson = detailConn.inputStream.bufferedReader().use { it.readText() }
                                    val detailObj = JSONObject(detailJson)
                                    snippet = detailObj.optString("snippet", "")

                                    val payload = detailObj.optJSONObject("payload")
                                    if (payload != null) {
                                        val headers = payload.optJSONArray("headers")
                                        if (headers != null) {
                                            for (h in 0 until headers.length()) {
                                                val header = headers.getJSONObject(h)
                                                when (header.getString("name").lowercase()) {
                                                    "subject" -> subject = header.getString("value")
                                                    "from" -> sender = header.getString("value")
                                                }
                                            }
                                        }

                                        fun parseParts(part: JSONObject) {
                                            val filename = part.optString("filename", "")
                                            val body = part.optJSONObject("body")
                                            val attachmentId = body?.optString("attachmentId", "") ?: ""
                                            if (filename.isNotEmpty() && attachmentId.isNotEmpty()) {
                                                val mimeType = part.optString("mimeType", "application/octet-stream")
                                                attachments.add(EmailAttachment(filename, mimeType, attachmentId))
                                            }
                                            val subParts = part.optJSONArray("parts")
                                            if (subParts != null) {
                                                for (p in 0 until subParts.length()) {
                                                    parseParts(subParts.getJSONObject(p))
                                                }
                                            }
                                        }
                                        parseParts(payload)
                                    }
                                }
                            } catch (_: Exception) {}

                            result[category]?.add(
                                CategorizedEmail(
                                    id = id,
                                    threadId = threadId,
                                    snippet = snippet,
                                    subject = subject,
                                    sender = sender,
                                    category = category,
                                    attachments = attachments
                                )
                            )
                        }
                    }
                }
            }

            fetchMessagesForLabel("INBOX", EmailCategory.PRIMARY)
            fetchMessagesForLabel("SENT", EmailCategory.SENT)
            fetchMessagesForLabel("CATEGORY_PROMOTIONS", EmailCategory.PROMOTIONS)
            fetchMessagesForLabel("CATEGORY_UPDATES", EmailCategory.UPDATES)

        } catch (e: Exception) {
            e.printStackTrace()
        }
        result
    }
}

// Fetch Full Plain-Text Email Body
suspend fun fetchEmailBody(context: Context, accountEmail: String, messageId: String): String {
    return withContext(Dispatchers.IO) {
        try {
            val account = Account(accountEmail, "com.google")
            val scope = "oauth2:https://www.googleapis.com/auth/gmail.readonly"
            val token = GoogleAuthUtil.getToken(context, account, scope)

            val url = URL("https://gmail.googleapis.com/gmail/v1/users/me/messages/$messageId?format=full")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                val detailObj = JSONObject(json)
                val payload = detailObj.optJSONObject("payload") ?: return@withContext "Unable to load email content."

                fun extractText(part: JSONObject): String? {
                    val mimeType = part.optString("mimeType", "")
                    val body = part.optJSONObject("body")
                    val data = body?.optString("data", "") ?: ""

                    if (mimeType == "text/plain" && data.isNotEmpty()) {
                        val decodedBytes = Base64.decode(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                        return String(decodedBytes, Charsets.UTF_8)
                    }

                    val parts = part.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val result = extractText(parts.getJSONObject(i))
                            if (result != null) return result
                        }
                    }
                    return null
                }

                return@withContext extractText(payload) ?: detailObj.optString("snippet", "No body text found.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        "Error loading full message."
    }
}

// Send Email API Call
suspend fun sendEmailApi(
    context: Context,
    accountEmail: String,
    recipient: String,
    subject: String,
    body: String
): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val account = Account(accountEmail, "com.google")
            val scope = "oauth2:https://www.googleapis.com/auth/gmail.send"
            val token = GoogleAuthUtil.getToken(context, account, scope)

            val rawEmail = "To: $recipient\r\n" +
                    "Subject: $subject\r\n" +
                    "Content-Type: text/plain; charset=UTF-8\r\n\r\n" +
                    body

            val encodedEmail = Base64.encodeToString(
                rawEmail.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )

            val jsonBody = "{\"raw\": \"$encodedEmail\"}"

            val url = URL("https://gmail.googleapis.com/gmail/v1/users/me/messages/send")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }

            connection.outputStream.use { os ->
                os.write(jsonBody.toByteArray(Charsets.UTF_8))
            }

            connection.responseCode == HttpURLConnection.HTTP_OK
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
