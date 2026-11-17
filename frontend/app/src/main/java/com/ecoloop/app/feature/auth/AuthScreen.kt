package com.ecoloop.app.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ecoloop.app.data.repository.DISTRICTS
import com.ecoloop.app.ui.components.EcoDropdownField
import com.ecoloop.app.ui.components.EcoField
import com.ecoloop.app.ui.components.EcoPrimaryButton

@Composable
fun AuthScreen(
    snackbarHostState: SnackbarHostState,
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            BrandHeader()
            Spacer(Modifier.height(28.dp))

            Text(
                text = if (state.isRegister) "Buat akun baru" else "Masuk ke akunmu",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (state.isRegister) {
                    EcoField(
                        value = state.name,
                        onValueChange = viewModel::onNameChange,
                        label = "Nama lengkap",
                        leadingIcon = Icons.Filled.Person,
                        error = state.nameError.takeIf { state.showErrors },
                    )
                }
                EcoField(
                    value = state.email,
                    onValueChange = viewModel::onEmailChange,
                    label = "Email",
                    leadingIcon = Icons.Filled.Email,
                    keyboardType = KeyboardType.Email,
                    error = state.emailError.takeIf { state.showErrors },
                )
                EcoField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = "Password",
                    leadingIcon = Icons.Filled.Lock,
                    isPassword = true,
                    imeAction = if (state.isRegister) ImeAction.Next else ImeAction.Done,
                    error = state.passwordError.takeIf { state.showErrors },
                    supportingText = if (state.isRegister) "Minimal 8 karakter" else null,
                )
                if (state.isRegister) {
                    EcoDropdownField(
                        label = "Wilayah (DKI Jakarta)",
                        selected = state.district,
                        options = DISTRICTS,
                        optionLabel = { it },
                        onSelect = viewModel::onDistrictChange,
                        leadingIcon = Icons.Filled.Place,
                    )
                }
            }

            state.serverError?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            EcoPrimaryButton(
                text = if (state.isRegister) "Buat akun" else "Masuk",
                onClick = viewModel::submit,
                loading = state.submitting,
            )
            TextButton(
                onClick = viewModel::toggleMode,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(if (state.isRegister) "Sudah punya akun? Masuk" else "Belum punya akun? Daftar")
            }
        }
    }
}

@Composable
private fun BrandHeader() {
    Column {
        Box(
            Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Recycling, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("EcoLoop", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            "Bahan sisa, peluang baru.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
