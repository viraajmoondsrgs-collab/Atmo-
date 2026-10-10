package com.trio.atmo

import android.accounts.Account
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
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
    private val WEB_CLIENT_ID = "627107001727-ec1ocbfu3jhkmrrj5q3iorgj8o6lnh89.apps.googleusercontent.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AtmoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(WEB_CLIENT_ID)
                }
            }
        }
    }
}

@Composable
fun AtmoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        content = content
    )
}

@Composable
fun MainScreen(webClientId: String) {
    val context = LocalContext.current
    val prefs: SharedPreferences = remember {
        context.getSharedPreferences("AtmoPrefs", Context.MODE_PRIVATE)
    }

    var signedInEmail by remember {
        mutableStateOf(prefs.getString("logged_in_email", null))
    }

    var selectedTab by remember { mutableStateOf(EmailCategory.PRIMARY) }
    var emailsMap by remember { mutableStateOf<Map<EmailCategory, List<CategorizedEmail>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(false) }

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope("https://www.googleapis.com/auth/gmail.readonly"),
                Scope("https://www.googleapis.com/auth/gmail.send")
            )
            .requestIdToken(webClientId)
            .build()
    }

    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    // Fetch emails automatically when signed in
    LaunchedEffect(signedInEmail) {
        if (signedInEmail != null) {
            isLoading = true
            emailsMap = fetchAllMailSections(context, signedInEmail!!)
            isLoading = false
        }
    }

    val signInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.result
            val email = account?.email
            if (email != null) {
                prefs.edit().putString("logged_in_email", email).apply()
                signedInEmail = email
                Toast.makeText(context, "Signed in as $email", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Sign-in failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (signedInEmail == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Button(onClick = {
                    googleSignInClient.signOut().addOnCompleteListener {
                        signInLauncher.launch(googleSignInClient.signInIntent)
                    }
                }) {
                    Text("Connect Google Account")
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
                    googleSignInClient.signOut().addOnCompleteListener {
                        prefs.edit().remove("logged_in_email").apply()
                        signedInEmail = null
                        emailsMap = emptyMap()
                        Toast.makeText(context, "Signed out successfully", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Switch / Sign Out")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Category & Sent Navigation Tabs
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
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(text = email.subject, style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = "From: ${email.sender}", style = MaterialTheme.typography.bodySmall)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = email.snippet, style = MaterialTheme.typography.bodyMedium)
                                    
                                    // Display attachment badges if present
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

// Background fetcher with full message parsing and attachment detection
suspend fun fetchAllMailSections(context: Context, accountEmail: String): Map<EmailCategory, List<CategorizedEmail>> {
    return withContext(Dispatchers.IO) {
        val result = mutableMapOf<EmailCategory, MutableList<CategorizedEmail>>()
        EmailCategory.values().forEach { result[it] = mutableListOf() }

        try {
            val account = Account(accountEmail, "com.google")
            val token = GoogleAuthUtil.getToken(context, account, "oauth2:https://www.googleapis.com/auth/gmail.readonly")

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
                                        // Parse headers
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

                                        // Recursive function to find file attachments in email parts
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
