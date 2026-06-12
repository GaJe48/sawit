package com.gaje48.lms.ui.screens.login

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gaje48.lms.model.AuthStatus
import com.gaje48.lms.ui.components.FloatingBlobsBackground
import com.gaje48.lms.ui.components.rememberPressedState
import com.gaje48.lms.ui.theme.RighteousFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun LoginScreen(viewModel: LoginViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LoginScreenStateless(
        status = uiState.status,
        errorMessage = uiState.errorMessage,
        onLoginClick = { nim, password -> viewModel.manualLogin(nim, password) },
    )
}

@Composable
fun LoginScreenStateless(
    status: AuthStatus,
    errorMessage: String?,
    onLoginClick: (String, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var nim by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val passwordVisibilityInteraction = remember { MutableInteractionSource() }
    val isPasswordVisible by passwordVisibilityInteraction.collectIsPressedAsState()

    val interaction = remember { MutableInteractionSource() }
    val isPressed by rememberPressedState(interaction)
    val buttonScale by animateFloatAsState(if (isPressed) 0.95f else 1f, label = "buttonScale")

    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec =
                    keyframes {
                        durationMillis = 400
                        0f at 0
                        20f at 50
                        (-20f) at 100
                        15f at 150
                        (-15f) at 200
                        10f at 250
                        (-10f) at 300
                        5f at 350
                        0f at 400
                    },
            )
        }

        if (errorMessage == "NIM atau Password salah") {
            keyboardController?.show()
        }
    }

    FloatingBlobsBackground {
        Box(
            modifier = Modifier.fillMaxSize().imePadding().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "SAWIT",
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = RighteousFontFamily,
                    letterSpacing = 2.sp,
                    style = MaterialTheme.typography.displayLarge,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "Sistem Akademik & Wahana Informasi Terpadu",
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )

                Spacer(modifier = Modifier.height(48.dp))

                Card(
                    shape = RoundedCornerShape(32.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                MaterialTheme.colorScheme.surface.copy(
                                    alpha = 0.4f,
                                ),
                        ),
                    border =
                        BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        ),
                ) {
                    Column(modifier = Modifier.padding(32.dp)) {
                        Text(
                            text = "Selamat Datang",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        Spacer(Modifier.height(20.dp))

                        OutlinedTextField(
                            value = nim,
                            onValueChange = { input ->
                                if (input.all { it.isDigit() }) nim = input
                            },
                            label = { Text("NIM Mahasiswa") },
                            keyboardOptions =
                                KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next,
                                ),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = "Ikon NIM",
                                )
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor =
                                        MaterialTheme.colorScheme.onSurface.copy(
                                            alpha = 0.2f,
                                        ),
                                    focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                                ),
                        )

                        Spacer(Modifier.height(20.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password Portal") },
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions =
                                KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done,
                                ),
                            keyboardActions =
                                KeyboardActions(
                                    onDone = {
                                        keyboardController?.hide()

                                        if (status == AuthStatus.IDLE && nim.isNotEmpty() && password.isNotEmpty()) {
                                            onLoginClick(nim, password)
                                        }
                                    },
                                ),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "Ikon Password",
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { },
                                    interactionSource = passwordVisibilityInteraction,
                                ) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Tahan untuk melihat password",
                                    )
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor =
                                        MaterialTheme.colorScheme.onSurface.copy(
                                            alpha = 0.2f,
                                        ),
                                    focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                                ),
                        )

                        Spacer(Modifier.height(15.dp))

                        AnimatedVisibility(visible = errorMessage != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                border =
                                    BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                                    ),
                            ) {
                                Text(
                                    text = errorMessage.orEmpty(),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp),
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }

                        Spacer(Modifier.height(15.dp))

                        Button(
                            onClick = {
                                keyboardController?.hide()

                                scope.launch {
                                    delay(150)
                                    onLoginClick(nim, password)
                                }
                            },
                            modifier =
                                Modifier.fillMaxWidth().height(56.dp).graphicsLayer {
                                    scaleX = buttonScale
                                    scaleY = buttonScale
                                },
                            interactionSource = interaction,
                            enabled = status == AuthStatus.IDLE && nim.isNotEmpty() && password.isNotEmpty(),
                            shape = RoundedCornerShape(16.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    disabledContainerColor =
                                        MaterialTheme.colorScheme.primary.copy(
                                            alpha = 0.3f,
                                        ),
                                    disabledContentColor =
                                        MaterialTheme.colorScheme.onPrimary.copy(
                                            alpha = 0.6f,
                                        ),
                                ),
                        ) {
                            AnimatedContent(
                                targetState = status,
                                label = "LoginButtonStateAnimation",
                            ) { currentState ->
                                when (currentState) {
                                    AuthStatus.LOADING -> {
                                        Row {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                strokeWidth = 2.5.dp,
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = "Memproses Keamanan...",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                            )
                                        }
                                    }

                                    AuthStatus.SUCCESS -> {
                                        Row {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Sukses",
                                                modifier = Modifier.size(22.dp),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Berhasil Masuk!",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 16.sp,
                                            )
                                        }
                                    }

                                    AuthStatus.IDLE -> {
                                        Text(
                                            text = "Masuk Portal",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp,
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

@Preview
@Composable
fun PreviewLoginScreen() {
    LoginScreenStateless(
        status = AuthStatus.IDLE,
        errorMessage = null,
        onLoginClick = { _, _ -> },
    )
}
