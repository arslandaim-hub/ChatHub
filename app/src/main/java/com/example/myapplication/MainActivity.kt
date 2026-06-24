package com.example.myapplication

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

class MainActivity : FragmentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        setContent {
            MyApplicationTheme {
                val viewModel: MainViewModel = viewModel()
                val selectedMessageTheme by viewModel.selectedMessageTheme.collectAsState()
                val selectedAppTheme by viewModel.selectedAppTheme.collectAsState()
                val isChatLockEnabled by viewModel.isChatLockEnabled.collectAsState()
                val isFingerprintEnabled by viewModel.isFingerprintEnabled.collectAsState()
                val selectedDateFormat by viewModel.selectedDateFormat.collectAsState()
                val chatLockPin by viewModel.chatLockPin.collectAsState()
                val securityQuestion by viewModel.securityQuestion.collectAsState()
                val securityAnswer by viewModel.securityAnswer.collectAsState()
                
                // Update system bar colors based on theme
                LaunchedEffect(selectedAppTheme) {
                    val isDarkBackground = selectedAppTheme.type != AppBackgroundType.NONE
                    if (isDarkBackground) {
                        enableEdgeToEdge(
                            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                        )
                    } else {
                        enableEdgeToEdge(
                            statusBarStyle = SystemBarStyle.light(
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT
                            ),
                            navigationBarStyle = SystemBarStyle.light(
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT
                            )
                        )
                    }
                }
                
                var currentScreen by remember { mutableStateOf("Main") }
                var selectedChat by remember { mutableStateOf<String?>(null) }
                var searchFilterQuery by remember { mutableStateOf<String?>(null) }
                var searchFilterDate by remember { mutableStateOf<String?>(null) }
                var searchFilterTime by remember { mutableStateOf<String?>(null) }
                var isAuthenticated by remember { 
                    mutableStateOf(!isChatLockEnabled) 
                }
                var isServiceEnabled by remember { mutableStateOf(false) }
                
                // Ensure authentication state is synced with settings on startup
                LaunchedEffect(isChatLockEnabled) {
                    if (!isChatLockEnabled) {
                        isAuthenticated = true
                    } else {
                        // If lock is enabled, we should start locked unless already authenticated
                        // This handles the case where isChatLockEnabled loads late from DataStore
                        isAuthenticated = false
                    }
                }
                var showResetDialog by remember { mutableStateOf(false) }
                val lifecycleOwner = LocalLifecycleOwner.current
                val context = LocalContext.current

                fun launchBiometricPrompt() {
                    val executor = ContextCompat.getMainExecutor(context)
                    val biometricPrompt = BiometricPrompt(
                        context as FragmentActivity,
                        executor,
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                super.onAuthenticationSucceeded(result)
                                isAuthenticated = true
                            }
                        }
                    )

                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Chat Lock")
                        .setSubtitle("Authenticate to view messages")
                        .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                        .build()

                    biometricPrompt.authenticate(promptInfo)
                }

                // Reset authentication when app is backgrounded and check for notification access
                DisposableEffect(isChatLockEnabled, lifecycleOwner) {
                    if (!isChatLockEnabled) {
                        isAuthenticated = true
                    }
                    
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_STOP && isChatLockEnabled) {
                            isAuthenticated = false
                        }
                        if (event == Lifecycle.Event.ON_START) {
                            isServiceEnabled = isNotificationServiceEnabled()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                // Launch biometric prompt when arriving at Main screen if locked
                LaunchedEffect(currentScreen, isAuthenticated, isChatLockEnabled, isFingerprintEnabled) {
                    if (currentScreen == "Main" && isChatLockEnabled && isFingerprintEnabled && !isAuthenticated) {
                        launchBiometricPrompt()
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedBackground(type = selectedAppTheme.type)
                    
                    if (currentScreen == "Main") {
                        if (!isAuthenticated && isChatLockEnabled) {
                            LockScreen(
                                isFingerprintEnabled = isFingerprintEnabled,
                                onPinEntered = { pin ->
                                    if (pin == chatLockPin) {
                                        isAuthenticated = true
                                    }
                                },
                                onFingerprintRequested = { launchBiometricPrompt() },
                                onResetPinRequested = { showResetDialog = true }
                            )
                        } else if (selectedChat != null) {
                            ChatDetailScreen(
                                chatName = selectedChat!!,
                                filterQuery = searchFilterQuery,
                                filterDate = searchFilterDate,
                                filterTime = searchFilterTime,
                                viewModel = viewModel,
                                selectedMessageTheme = selectedMessageTheme,
                                selectedAppTheme = selectedAppTheme,
                                onBack = { 
                                    selectedChat = null
                                    searchFilterQuery = null
                                    searchFilterDate = null
                                    searchFilterTime = null
                                }
                            )
                            BackHandler { 
                                selectedChat = null
                                searchFilterQuery = null
                                searchFilterDate = null
                                searchFilterTime = null
                            }
                        } else {
                            MainContent(
                                viewModel = viewModel,
                                isServiceEnabled = isServiceEnabled,
                                selectedMessageTheme = selectedMessageTheme,
                                selectedAppTheme = selectedAppTheme,
                                onOpenSettings = { currentScreen = "Settings" },
                                onChatSelected = { name, query, date, time ->
                                    selectedChat = name
                                    searchFilterQuery = query
                                    searchFilterDate = date
                                    searchFilterTime = time
                                }
                            )
                        }
                    } else if (currentScreen == "Settings") {
                        SettingsScreen(
                            viewModel = viewModel,
                            selectedMessageTheme = selectedMessageTheme,
                            selectedAppTheme = selectedAppTheme,
                            selectedDateFormat = selectedDateFormat,
                            isChatLockEnabled = isChatLockEnabled,
                            isFingerprintEnabled = isFingerprintEnabled,
                            chatLockPin = chatLockPin,
                            securityQuestion = securityQuestion,
                            securityAnswer = securityAnswer,
                            onBack = { currentScreen = "Main" }
                        )
                        BackHandler { currentScreen = "Main" }
                    }

                    if (showResetDialog) {
                        PinSetupDialog(
                            currentPin = chatLockPin,
                            isEnabled = isChatLockEnabled,
                            savedQuestion = securityQuestion,
                            savedAnswer = securityAnswer,
                            onSave = { pin, enabled, question, answer ->
                                viewModel.setChatLockPin(pin)
                                viewModel.setChatLockEnabled(enabled)
                                viewModel.setSecurityInfo(question, answer)
                                if (!enabled) {
                                    viewModel.setFingerprintEnabled(false)
                                }
                                showResetDialog = false
                                if (enabled && pin.length == 4) {
                                    isAuthenticated = true // Unlock after manual reset
                                }
                            },
                            onDismiss = { showResetDialog = false }
                        )
                    }
                }
            }
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainContent(
        viewModel: MainViewModel,
        isServiceEnabled: Boolean,
        selectedMessageTheme: MessageTheme,
        selectedAppTheme: AppBackgroundTheme,
        onOpenSettings: () -> Unit,
        onChatSelected: (String, String?, String?, String?) -> Unit
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = Color(0xFF303F9F),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(42.dp),
                                shadowElevation = 4.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Chat,
                                        contentDescription = null,
                                        modifier = Modifier.size(26.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "ChatHub",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 28.sp
                                )
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = if (selectedAppTheme.type != AppBackgroundType.NONE) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (selectedAppTheme.type != AppBackgroundType.NONE) Color.Black.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                        titleContentColor = if (selectedAppTheme.type != AppBackgroundType.NONE) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                )
            }
        ) { innerPadding ->
            MainScreen(
                modifier = Modifier.padding(innerPadding),
                isServiceEnabled = isServiceEnabled,
                viewModel = viewModel,
                selectedMessageTheme = selectedMessageTheme,
                selectedAppTheme = selectedAppTheme,
                onChatSelected = onChatSelected
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ChatDetailScreen(
        chatName: String,
        filterQuery: String? = null,
        filterDate: String? = null,
        filterTime: String? = null,
        viewModel: MainViewModel,
        selectedMessageTheme: MessageTheme,
        selectedAppTheme: AppBackgroundTheme,
        onBack: () -> Unit
    ) {
        val messages by viewModel.allMessagesUnified.collectAsState(initial = emptyList())
        val dateFormat by viewModel.selectedDateFormat.collectAsState()
        
        val chatMessages = remember(chatName, messages, filterQuery, filterDate, filterTime, dateFormat) {
            messages.filter { msg ->
                val matchesChat = if (msg.isGroupMessage) msg.groupName == chatName else msg.senderName == chatName
                if (!matchesChat) return@filter false

                val sdfDate = SimpleDateFormat(dateFormat.pattern, Locale.getDefault())
                val sdfTime = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val msgDate = sdfDate.format(Date(msg.timestamp))
                val msgTime = sdfTime.format(Date(msg.timestamp))

                val matchesDate = filterDate == null || msgDate == filterDate
                val matchesTime = filterTime == null || msgTime.contains(filterTime, ignoreCase = true)
                val matchesQuery = filterQuery == null || msg.messageText.contains(filterQuery, ignoreCase = true)

                matchesDate && matchesTime && matchesQuery
            }.sortedByDescending { it.timestamp }
        }

        var messageToDelete by remember { mutableStateOf<WhatsappMessage?>(null) }
        val context = LocalContext.current

        if (messageToDelete != null) {
            AlertDialog(
                onDismissRequest = { messageToDelete = null },
                title = { Text("Message Options") },
                text = { 
                    Column {
                        Text("Choose an action for this message:")
                        Spacer(modifier = Modifier.padding(8.dp))
                        Text(
                            text = messageToDelete?.messageText ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 2
                        )
                    }
                },
                confirmButton = {
                    Row {
                        TextButton(
                            onClick = {
                                messageToDelete?.let { message: WhatsappMessage ->
                                    val sendIntent: Intent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, message.messageText)
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, null)
                                    context.startActivity(shareIntent)
                                }
                                messageToDelete = null
                            }
                        ) {
                            Text("Forward")
                        }
                        TextButton(
                            onClick = {
                                messageToDelete?.let { viewModel.deleteMessage(it) }
                                messageToDelete = null
                            }
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { messageToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(chatName, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (selectedAppTheme.type != AppBackgroundType.NONE) Color.Black.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                        titleContentColor = if (selectedAppTheme.type != AppBackgroundType.NONE) Color.White else MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = if (selectedAppTheme.type != AppBackgroundType.NONE) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                items(chatMessages) { message ->
                    MessageItem(
                        message = message,
                        theme = selectedMessageTheme,
                        dateFormat = dateFormat,
                        searchQuery = filterQuery ?: "",
                        onClick = { messageToDelete = message }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SettingsScreen(
        viewModel: MainViewModel,
        selectedMessageTheme: MessageTheme,
        selectedAppTheme: AppBackgroundTheme,
        selectedDateFormat: DateFormatPreference,
        isChatLockEnabled: Boolean,
        isFingerprintEnabled: Boolean,
        chatLockPin: String?,
        securityQuestion: String?,
        securityAnswer: String?,
        onBack: () -> Unit
    ) {
        var showAppThemeDialog by remember { mutableStateOf(false) }
        var showMessageThemeDialog by remember { mutableStateOf(false) }
        var showDateFormatDialog by remember { mutableStateOf(false) }
        var showPinDialog by remember { mutableStateOf(false) }
        val context = LocalContext.current
        
        val biometricManager = remember { BiometricManager.from(context) }
        val isBiometricAvailable = remember {
            biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Settings", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (selectedAppTheme.type != AppBackgroundType.NONE) Color.Black.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                        titleContentColor = if (selectedAppTheme.type != AppBackgroundType.NONE) Color.White else MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = if (selectedAppTheme.type != AppBackgroundType.NONE) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                item {
                    SettingsSectionHeader("Personalization", selectedAppTheme)
                }
                item {
                    SettingsItem(
                        title = "App Theme",
                        subtitle = selectedAppTheme.displayName,
                        icon = Icons.Default.Palette,
                        selectedAppTheme = selectedAppTheme,
                        onClick = { showAppThemeDialog = true }
                    )
                }
                item {
                    SettingsItem(
                        title = "Message Theme",
                        subtitle = selectedMessageTheme.displayName,
                        icon = Icons.Default.ColorLens,
                        selectedAppTheme = selectedAppTheme,
                        onClick = { showMessageThemeDialog = true }
                    )
                }
                item {
                    SettingsItem(
                        title = "Date Format",
                        subtitle = selectedDateFormat.displayName,
                        icon = Icons.Default.DateRange,
                        selectedAppTheme = selectedAppTheme,
                        onClick = { showDateFormatDialog = true }
                    )
                }

                item {
                    SettingsSectionHeader("Security & Privacy", selectedAppTheme)
                }
                item {
                    SettingsItem(
                        title = "Chat Lock",
                        subtitle = if (isChatLockEnabled) "Enabled (PIN: ****)" else "Disabled",
                        icon = if (isChatLockEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                        selectedAppTheme = selectedAppTheme,
                        onClick = { showPinDialog = true }
                    )
                }
                item {
                    SettingsItem(
                        title = "Fingerprint Lock",
                        subtitle = if (!isBiometricAvailable) "No fingerprint support" 
                                   else if (!isChatLockEnabled) "Enable Chat Lock first"
                                   else if (isFingerprintEnabled) "Enabled" else "Disabled",
                        icon = Icons.Default.Fingerprint,
                        selectedAppTheme = selectedAppTheme,
                        onClick = { 
                            if (isBiometricAvailable && isChatLockEnabled) {
                                viewModel.setFingerprintEnabled(!isFingerprintEnabled)
                            }
                        }
                    )
                }
                item {
                    SettingsItem(
                        title = "Notification Access",
                        subtitle = "Grant permissions to capture messages",
                        icon = Icons.Default.Notifications,
                        selectedAppTheme = selectedAppTheme,
                        onClick = {
                            context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                        }
                    )
                }

                item {
                    SettingsSectionHeader("About", selectedAppTheme)
                }
                item {
                    SettingsItem(
                        title = "About Developer",
                        subtitle = "Learn more about the creator",
                        icon = Icons.Default.Person,
                        selectedAppTheme = selectedAppTheme,
                        onClick = { /* Empty for now */ }
                    )
                }
                item {
                    SettingsItem(
                        title = "View Source Code",
                        subtitle = "Check out the code on GitHub",
                        icon = Icons.Default.Code,
                        selectedAppTheme = selectedAppTheme,
                        onClick = { /* Empty for now */ }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        if (showAppThemeDialog) {
            ThemeSelectionDialog(
                title = "Choose App Theme",
                options = AppBackgroundTheme.entries,
                selectedOption = selectedAppTheme,
                onOptionSelected = { viewModel.setAppTheme(it) },
                onDismiss = { showAppThemeDialog = false }
            )
        }

        if (showMessageThemeDialog) {
            ThemeSelectionDialog(
                title = "Choose Message Theme",
                options = MessageTheme.entries,
                selectedOption = selectedMessageTheme,
                onOptionSelected = { viewModel.setMessageTheme(it) },
                onDismiss = { showMessageThemeDialog = false },
                showPreview = true
            )
        }

        if (showDateFormatDialog) {
            ThemeSelectionDialog(
                title = "Choose Date Format",
                options = DateFormatPreference.entries,
                selectedOption = selectedDateFormat,
                onOptionSelected = { viewModel.setDateFormat(it) },
                onDismiss = { showDateFormatDialog = false }
            )
        }

        if (showPinDialog) {
            PinSetupDialog(
                currentPin = chatLockPin,
                isEnabled = isChatLockEnabled,
                savedQuestion = securityQuestion,
                savedAnswer = securityAnswer,
                onSave = { pin, enabled, question, answer ->
                    viewModel.setChatLockPin(pin)
                    viewModel.setChatLockEnabled(enabled)
                    viewModel.setSecurityInfo(question, answer)
                    if (!enabled) {
                        viewModel.setFingerprintEnabled(false)
                    }
                    showPinDialog = false
                },
                onDismiss = { showPinDialog = false }
            )
        }
    }

    @Composable
    fun SettingsSectionHeader(title: String, selectedAppTheme: AppBackgroundTheme) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = if (selectedAppTheme.type != AppBackgroundType.NONE) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )
    }

    @Composable
    fun SettingsItem(
        title: String,
        subtitle: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        selectedAppTheme: AppBackgroundTheme,
        onClick: () -> Unit
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clickable { onClick() },
            color = if (selectedAppTheme.type != AppBackgroundType.NONE) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selectedAppTheme.type != AppBackgroundType.NONE) Color.White else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedAppTheme.type != AppBackgroundType.NONE) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selectedAppTheme.type != AppBackgroundType.NONE) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    @Composable
    fun <T> ThemeSelectionDialog(
        title: String,
        options: List<T>,
        selectedOption: T,
        onOptionSelected: (T) -> Unit,
        onDismiss: () -> Unit,
        showPreview: Boolean = false
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { option ->
                        val displayName = when(option) {
                            is AppBackgroundTheme -> option.displayName
                            is MessageTheme -> option.displayName
                            is DateFormatPreference -> option.displayName
                            else -> ""
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOptionSelected(option) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = option == selectedOption, onClick = { onOptionSelected(option) })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = displayName)
                            if (showPreview && option is MessageTheme) {
                                Spacer(modifier = Modifier.weight(1f))
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (option.backgroundColor == Color.Transparent) MaterialTheme.colorScheme.surfaceVariant else option.backgroundColor)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
        )
    }

    @Composable
    fun PinSetupDialog(
        currentPin: String?,
        isEnabled: Boolean,
        savedQuestion: String?,
        savedAnswer: String?,
        onSave: (String, Boolean, String, String) -> Unit,
        onDismiss: () -> Unit
    ) {
        var pin by remember { mutableStateOf(currentPin ?: "") }
        var enabled by remember { mutableStateOf(isEnabled) }
        var question by remember { mutableStateOf(savedQuestion ?: "") }
        var answer by remember { mutableStateOf(savedAnswer ?: "") }
        
        var isVerifying by remember { mutableStateOf(currentPin != null && !savedQuestion.isNullOrBlank()) }
        var verificationAnswer by remember { mutableStateOf("") }
        var verificationError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (isVerifying) "Verify Identity" else "Chat Lock Settings") },
            text = {
                Column {
                    if (isVerifying) {
                        Text("Security Question:")
                        Text(
                            text = savedQuestion ?: "No question set.",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        OutlinedTextField(
                            value = verificationAnswer,
                            onValueChange = { 
                                verificationAnswer = it
                                verificationError = false
                            },
                            label = { Text("Your Answer") },
                            isError = verificationError,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (verificationError) {
                            Text(
                                "Incorrect answer. Please try again.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Enable Chat Lock")
                            Spacer(modifier = Modifier.weight(1f))
                            Switch(checked = enabled, onCheckedChange = { enabled = it })
                        }
                        if (enabled) {
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = pin,
                                onValueChange = { if (it.length <= 4) pin = it },
                                label = { Text("Enter 4-digit PIN") },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Security Question (for PIN reset):", style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(
                                value = question,
                                onValueChange = { question = it },
                                label = { Text("e.g., What is your pet's name?") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = answer,
                                onValueChange = { answer = it },
                                label = { Text("Your Answer") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (isVerifying) {
                    TextButton(
                        onClick = {
                            if (verificationAnswer.trim().equals(savedAnswer, ignoreCase = true)) {
                                isVerifying = false
                                // Clear old data so user can enter new values
                                pin = ""
                                question = ""
                                answer = ""
                            } else {
                                verificationError = true
                            }
                        }
                    ) { Text("Verify") }
                } else {
                    val canSave = !enabled || (pin.length == 4 && question.isNotBlank() && answer.isNotBlank())
                    TextButton(
                        onClick = { if (canSave) onSave(pin, enabled, question, answer) },
                        enabled = canSave
                    ) { Text("Save") }
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
    }

    @Composable
    fun LockScreen(
        isFingerprintEnabled: Boolean,
        onPinEntered: (String) -> Unit,
        onFingerprintRequested: () -> Unit,
        onResetPinRequested: () -> Unit
    ) {
        var pin by remember { mutableStateOf("") }
        
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "Chat Lock Active",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Enter your 4-digit PIN to continue",
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { 
                            if (it.length <= 4) {
                                pin = it
                                if (it.length == 4) onPinEntered(it)
                            }
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.width(150.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
                        )
                    )
                    
                    if (isFingerprintEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))
                        IconButton(
                            onClick = onFingerprintRequested,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Use Fingerprint",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = onResetPinRequested) {
                        Text("Forgot PIN?", color = Color.White.copy(alpha = 0.8f))
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedBackground(type: AppBackgroundType) {
    when (type) {
        AppBackgroundType.STARS -> StaticStarryBackground()
        AppBackgroundType.WAVES -> StaticWaveBackground()
        AppBackgroundType.AURORA -> StaticAuroraBackground()
        AppBackgroundType.NONE -> { /* No background */ }
    }
}

@Composable
fun StaticStarryBackground() {
    val stars = remember { List(100) { Offset(Random.nextFloat(), Random.nextFloat()) } }

    Canvas(modifier = Modifier.fillMaxSize().background(Color(0xFF0D1B2A))) {
        stars.forEach { star ->
            drawCircle(
                color = Color.White,
                radius = 1.5.dp.toPx(),
                center = Offset(star.x * size.width, star.y * size.height),
                alpha = Random.nextFloat() * 0.7f + 0.3f
            )
        }
    }
}

@Composable
fun StaticWaveBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF0077B6), Color(0xFF00B4D8), Color(0xFF90E0EF))
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = Path()
            val height = size.height
            val width = size.width
            
            path.moveTo(0f, height)
            for (x in 0..width.toInt() step 10) {
                val y = height * 0.75f + Math.sin((x.toFloat() / width * 2 * Math.PI)).toFloat() * 40f
                path.lineTo(x.toFloat(), y)
            }
            path.lineTo(width, height)
            path.close()
            
            drawPath(path, Color.White.copy(alpha = 0.15f))
        }
    }
}

@Composable
fun StaticAuroraBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF004D40), Color(0xFF1B5E20), Color(0xFF000000))
                )
            )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    isServiceEnabled: Boolean,
    viewModel: MainViewModel = viewModel(),
    selectedMessageTheme: MessageTheme,
    selectedAppTheme: AppBackgroundTheme,
    onChatSelected: (String, String?, String?, String?) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchDate by remember { mutableStateOf("") }
    var searchTime by remember { mutableStateOf("") }
    var filterGroups by remember { mutableStateOf<Boolean?>(null) }
    var sortByRecent by remember { mutableStateOf(true) }
    var isSearchExpanded by remember { mutableStateOf(false) }

    val allMessages by viewModel.allMessagesUnified.collectAsState(initial = emptyList())
    val filteredMessages by viewModel.searchMessages(
        query = searchQuery,
        date = searchDate.ifBlank { null },
        time = searchTime.ifBlank { null },
        filterGroups = filterGroups,
        sortByRecent = sortByRecent
    ).collectAsState(initial = emptyList())
    val selectedDateFormat by viewModel.selectedDateFormat.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        if (!isServiceEnabled) {
            Text(
                text = "Notification access is required to receive messages.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        }

        if (isSearchExpanded) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by name or text...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            IconButton(onClick = { 
                                searchQuery = ""
                                searchDate = ""
                                searchTime = ""
                                filterGroups = null
                                isSearchExpanded = false
                            }) { Icon(Icons.Default.Close, null) }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = searchDate,
                            onValueChange = { input ->
                                val digitsOnly = input.filter { it.isDigit() }.take(8)
                                val formatted = StringBuilder()
                                for (i in digitsOnly.indices) {
                                    formatted.append(digitsOnly[i])
                                    if ((i == 1 || i == 3) && i < digitsOnly.length - 1) {
                                        formatted.append("/")
                                    }
                                }
                                searchDate = formatted.toString()
                            },
                            placeholder = { Text(if (selectedDateFormat == DateFormatPreference.UK) "Date (DDMMYYYY)" else "Date (MMDDYYYY)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = searchTime,
                            onValueChange = { searchTime = it },
                            placeholder = { Text("Time (HH:MM AM/PM)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = filterGroups == true,
                            onClick = { filterGroups = if (filterGroups == true) null else true },
                            label = { Text("Groups") },
                            leadingIcon = if (filterGroups == true) {
                                { Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) }
                            } else null
                        )
                        FilterChip(
                            selected = filterGroups == false,
                            onClick = { filterGroups = if (filterGroups == false) null else false },
                            label = { Text("Private") },
                            leadingIcon = if (filterGroups == false) {
                                { Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) }
                            } else null
                        )
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        IconButton(onClick = { sortByRecent = !sortByRecent }) {
                            Icon(
                                imageVector = if (sortByRecent) Icons.Default.Sort else Icons.Default.Abc,
                                contentDescription = "Toggle Sort",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text(
                        "Tip: Use query for name/text, or filter by date/time/type.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = { isSearchExpanded = true }) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = if (selectedAppTheme.type != AppBackgroundType.NONE) Color.White else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        val messagesToDisplay = if (searchQuery.isNotBlank() || searchDate.isNotBlank() || searchTime.isNotBlank() || filterGroups != null) {
            filteredMessages
        } else {
            allMessages
        }

        MessageList(
            messages = messagesToDisplay,
            viewModel = viewModel,
            messageTheme = selectedMessageTheme,
            appTheme = selectedAppTheme,
            dateFormat = selectedDateFormat,
            searchQuery = searchQuery,
            isSearching = searchQuery.isNotBlank() || searchDate.isNotBlank() || searchTime.isNotBlank() || filterGroups != null,
            onChatSelected = { name ->
                onChatSelected(name, searchQuery.ifBlank { null }, searchDate.ifBlank { null }, searchTime.ifBlank { null })
            }
        )
    }
}

@Composable
fun MessageList(
    messages: List<WhatsappMessage>, 
    viewModel: MainViewModel, 
    messageTheme: MessageTheme,
    appTheme: AppBackgroundTheme,
    dateFormat: DateFormatPreference,
    searchQuery: String,
    isSearching: Boolean,
    onChatSelected: (String) -> Unit
) {
    val context = LocalContext.current
    if (messages.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No messages found.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp),
                color = if (appTheme.type != AppBackgroundType.NONE) Color.White else Color.Unspecified
            )
        }
    } else {
        val groupedMessages = messages.groupBy { 
            if (it.isGroupMessage) (it.groupName ?: it.senderName).trim() else it.senderName.trim()
        }
        var messageToDelete by remember { mutableStateOf<WhatsappMessage?>(null) }
        var chatToDelete by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

        if (chatToDelete != null) {
            AlertDialog(
                onDismissRequest = { chatToDelete = null },
                title = { Text("Delete Chat") },
                text = { Text("Are you sure you want to delete all messages from \"${chatToDelete?.first}\"?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            chatToDelete?.let { (name, isGroup) ->
                                viewModel.deleteChat(name, isGroup)
                            }
                            chatToDelete = null
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { chatToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (messageToDelete != null) {
            AlertDialog(
                onDismissRequest = { messageToDelete = null },
                title = { Text("Message Options") },
                text = { 
                    Column {
                        Text("Choose an action for this message:")
                        Spacer(modifier = Modifier.padding(8.dp))
                        Text(
                            text = messageToDelete?.messageText ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 2
                        )
                    }
                },
                confirmButton = {
                    Row {
                        TextButton(
                            onClick = {
                                messageToDelete?.let { message: WhatsappMessage ->
                                    val sendIntent: Intent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, message.messageText)
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, null)
                                    context.startActivity(shareIntent)
                                }
                                messageToDelete = null
                            }
                        ) {
                            Text("Forward")
                        }
                        TextButton(
                            onClick = {
                                messageToDelete?.let { viewModel.deleteMessage(it) }
                                messageToDelete = null
                            }
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { messageToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            if (isSearching) {
                items(messages) { message ->
                    MessageItem(
                        message = message,
                        theme = messageTheme,
                        dateFormat = dateFormat,
                        searchQuery = searchQuery,
                        onClick = { messageToDelete = message }
                    )
                }
            } else {
                groupedMessages.forEach { (sender, senderMessages) ->
                    val isGroup = senderMessages.firstOrNull()?.isGroupMessage == true
                    item {
                        SenderHeader(
                            senderName = sender,
                            searchQuery = searchQuery,
                            onToggle = { onChatSelected(sender) },
                            onLongClick = { chatToDelete = sender to isGroup },
                            appTheme = appTheme,
                            isGroup = isGroup
                        )
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.padding(8.dp))
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SenderHeader(
    senderName: String,
    searchQuery: String,
    onToggle: () -> Unit,
    onLongClick: () -> Unit,
    appTheme: AppBackgroundTheme,
    isGroup: Boolean = false
) {
    val initial = senderName.firstOrNull()?.toString() ?: "?"
    
    val backgroundColor = remember(senderName) {
        val colors = listOf(
            Color(0xFFE57373), Color(0xFFF06292), Color(0xFFBA68C8),
            Color(0xFF9575CD), Color(0xFF7986CB), Color(0xFF64B5F6),
            Color(0xFF4FC3F7), Color(0xFF4DD0E1), Color(0xFF4DB6AC),
            Color(0xFF81C784), Color(0xFFAED581), Color(0xFFFFD54F)
        )
        colors[Math.abs(senderName.hashCode()) % colors.size]
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp)
            .combinedClickable(
                onClick = onToggle,
                onLongClick = onLongClick
            ),
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (appTheme.type != AppBackgroundType.NONE) 0.6f else 0.8f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(backgroundColor),
                contentAlignment = Alignment.Center
            ) {
                if (isGroup) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text(
                        text = initial,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = SearchUtils.highlightText(
                    text = senderName,
                    query = searchQuery,
                    highlightColor = Color(0xFFFFEB3B).copy(alpha = 0.5f)
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (appTheme.type != AppBackgroundType.NONE) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (appTheme.type != AppBackgroundType.NONE) Color.White else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MessageItem(
    message: WhatsappMessage, 
    theme: MessageTheme, 
    dateFormat: DateFormatPreference, 
    searchQuery: String = "",
    onClick: () -> Unit
) {
    val sdf = SimpleDateFormat("${dateFormat.pattern}, hh:mm a", Locale.getDefault())
    val dateString = sdf.format(Date(message.timestamp))
    var isExpanded by remember { mutableStateOf(false) }
    val bubbleColor = if (theme.backgroundColor == Color.Transparent) MaterialTheme.colorScheme.surface.copy(alpha = 0.9f) else theme.backgroundColor

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .combinedClickable(
                onClick = {
                    if (message.messageText.lines().size > 2) {
                        isExpanded = !isExpanded
                    }
                },
                onLongClick = {
                    onClick()
                }
            )
    ) {
        Box(
            modifier = Modifier
                .padding(start = 24.dp)
                .size(14.dp, 8.dp)
                .drawBehind {
                    val path = Path().apply {
                        moveTo(0f, size.height)
                        lineTo(size.width / 2f, 0f)
                        lineTo(size.width, size.height)
                        close()
                    }
                    drawPath(path, bubbleColor)
                }
        )

        Card(
            modifier = Modifier
                .fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(
                containerColor = bubbleColor
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Surface(
                    color = if (theme.contentColor == Color.Unspecified) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else theme.contentColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = SearchUtils.highlightText(
                            text = if (message.isGroupMessage && !message.groupName.isNullOrBlank()) {
                                "${message.senderName} @ ${message.groupName}"
                            } else {
                                message.senderName
                            },
                            query = searchQuery,
                            highlightColor = Color(0xFFFFEB3B).copy(alpha = 0.4f)
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (theme.contentColor == Color.Unspecified) MaterialTheme.colorScheme.primary else theme.contentColor,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = SearchUtils.highlightText(
                        text = message.messageText,
                        query = searchQuery,
                        highlightColor = Color(0xFFFFEB3B).copy(alpha = 0.4f)
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (theme.contentColor == Color.Unspecified) Color.Unspecified else theme.contentColor,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (!isExpanded && message.messageText.lines().size > 2) {
                    Text(
                        text = "Tap to expand • Long press for options",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (theme.contentColor == Color.Unspecified) MaterialTheme.colorScheme.primary else theme.contentColor,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else if (message.messageText.lines().size <= 2) {
                    Text(
                        text = "Long press for options",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (theme.contentColor == Color.Unspecified) MaterialTheme.colorScheme.outline.copy(alpha = 0.5f) else theme.contentColor.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Text(
                    text = dateString,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (theme.contentColor == Color.Unspecified) MaterialTheme.colorScheme.outline else theme.contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
                
                if (isExpanded) {
                    TextButton(
                        onClick = { isExpanded = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Show Less", color = if (theme.contentColor == Color.Unspecified) MaterialTheme.colorScheme.primary else theme.contentColor)
                    }
                }
            }
        }
    }
}
