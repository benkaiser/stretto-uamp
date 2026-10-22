package com.example.android.uamp.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.android.uamp.ui.staggerIn
import com.example.android.uamp.ui.theme.StrettoIcons
import com.example.android.uamp.ui.theme.brandBrush
import com.example.android.uamp.viewmodels.StrettoViewModel

private fun openBrowser(context: android.content.Context, uri: Uri) {
    try {
        CustomTabsIntent.Builder().build().launchUrl(context, uri)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** First screen for signed-out users: Google through the browser, or email and password. */
@Composable
fun SignInScreen(vm: StrettoViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val busy by vm.signInBusy.collectAsState()
    val error by vm.signInError.collectAsState()
    var emailMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    fun submit() {
        focus.clearFocus()
        vm.signInWithPassword(email, password)
    }

    Box(
        modifier
            .fillMaxSize()
            .background(brandBrush(dark = androidx.compose.foundation.isSystemInDarkTheme()))
            .safeDrawingPadding()
            .imePadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Stretto",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.staggerIn(0)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Sign in to bring your whole library with you.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.staggerIn(1)
            )
            Spacer(Modifier.height(40.dp))

            SignInButton(
                label = "Continue with Google",
                enabled = !busy,
                filled = true,
                onClick = { openBrowser(context, vm.browserSignInUri()) },
                modifier = Modifier.staggerIn(2)
            )
            Spacer(Modifier.height(14.dp))
            SignInButton(
                label = if (emailMode) "Hide email sign-in" else "Use email instead",
                enabled = !busy,
                filled = false,
                onClick = { emailMode = !emailMode },
                modifier = Modifier.staggerIn(3)
            )

            AnimatedVisibility(visible = emailMode) {
                Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.55f),
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.75f),
                        cursorColor = Color.White
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        singleLine = true,
                        colors = colors,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        colors = colors,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    SignInButton(
                        label = "Sign in",
                        enabled = !busy,
                        filled = true,
                        onClick = ::submit
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
                when {
                    busy -> CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(28.dp)
                    )
                    error != null -> Text(
                        error.orEmpty(),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun SignInButton(
    label: String,
    enabled: Boolean,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = CircleShape
    Box(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(shape)
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.16f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (filled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = if (enabled) 1f else 0.5f)
        )
    }
}

/** Shown while the library is downloading, when it failed to download, or when it is empty. */
@Composable
fun LibraryStatusScreen(vm: StrettoViewModel, modifier: Modifier = Modifier) {
    val syncing by vm.syncing.collectAsState()
    val error by vm.syncError.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val account by vm.accountState.collectAsState()
    val loading = error == null && (syncing || !loaded)

    Column(
        modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (loading) {
            CircularProgressIndicator(strokeWidth = 4.dp, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(24.dp))
            Text("Loading your library…", style = MaterialTheme.typography.titleMedium)
        } else {
            Text(
                if (error != null) "Couldn't load your library" else "Your library is empty",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                error ?: "Add songs to ${account.email ?: "your account"} on next.kaiserapps.com, then refresh.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Retry", StrettoIcons.History, filled = true, onClick = vm::retrySync)
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = vm::signOut) { Text("Sign out") }
    }
}

/** Avatar that opens the account menu on the home screen. */
@Composable
fun AccountMenu(vm: StrettoViewModel, modifier: Modifier = Modifier) {
    val account by vm.accountState.collectAsState()
    var open by remember { mutableStateOf(false) }
    val initial = account.email?.firstOrNull()?.uppercase() ?: "S"
    Box(modifier) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(brandBrush(dark = androidx.compose.foundation.isSystemInDarkTheme()))
                .clickable { open = true },
            contentAlignment = Alignment.Center
        ) {
            Text(initial, color = Color.White, fontWeight = FontWeight.Bold)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            account.email?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            DropdownMenuItem(
                text = { Text("Refresh library") },
                onClick = { open = false; vm.retrySync() }
            )
            DropdownMenuItem(
                text = { Text("Sign out") },
                onClick = { open = false; vm.signOut() }
            )
        }
    }
}
