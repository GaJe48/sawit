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
import androidx.compose.material.icons.filled.Email
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
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
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.gaje48.lms.R
import com.gaje48.lms.model.AuthStatus
import com.gaje48.lms.ui.components.FloatingBlobsBackground
import com.gaje48.lms.ui.components.rememberPressedState
import com.gaje48.lms.ui.theme.RighteousFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun LoginScreen(component: LoginComponent) {
    val uiState by component.uiState.subscribeAsState()

    LoginScreenStateless(
        status = uiState.status,
        errorMessage = uiState.errorMessage,
        onLoginClick = { nim, password -> component.manualLogin(nim, password) },
        onResetPasswordClick = { email -> component.requestResetPassword(email) },
        onResetStatus = { component.resetError() },
    )
}

@Composable
fun LoginScreenStateless(
    status: AuthStatus,
    errorMessage: String?,
    onLoginClick: (String, String) -> Unit,
    onResetPasswordClick: (String) -> Unit,
    onResetStatus: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var isResetMode by rememberSaveable { mutableStateOf(false) }
    var nim by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    val isEmailValid =
        remember(email) {
            email.isEmpty() ||
                android.util.Patterns.EMAIL_ADDRESS
                    .matcher(email)
                    .matches()
        }

    val isNimValid =
        remember(nim) {
            nim.isEmpty() || nim.length >= 12
        }

    val modeToggleButton: @Composable (text: String) -> Unit = { text ->
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by rememberPressedState(interactionSource)
        val buttonScale by animateFloatAsState(if (isPressed) 0.95f else 1f, label = "modeButtonScale")

        val isButtonEnabled = status == AuthStatus.IDLE

        OutlinedButton(
            onClick = {
                scope.launch {
                    delay(150.milliseconds)
                    isResetMode = !isResetMode
                    onResetStatus()
                }
            },
            modifier =
                Modifier.fillMaxWidth().graphicsLayer {
                    scaleX = buttonScale
                    scaleY = buttonScale
                },
            enabled = isButtonEnabled,
            shape = RoundedCornerShape(16.dp),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                    disabledContentColor = MaterialTheme.colorScheme.primary.copy(0.2f),
                ),
            border =
                BorderStroke(
                    width = 2.dp,
                    color =
                        if (isButtonEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.primary.copy(0.2f)
                        },
                ),
            interactionSource = interactionSource,
        ) {
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }

    val passwordVisibilityInteraction = remember { MutableInteractionSource() }
    val isPasswordVisible by passwordVisibilityInteraction.collectIsPressedAsState()

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

        if (errorMessage == "NIM atau Password salah" || errorMessage == "Email Anda belum terdaftar") {
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
                    text = stringResource(R.string.app_name),
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = RighteousFontFamily,
                    letterSpacing = 2.sp,
                    style = MaterialTheme.typography.displayLarge,
                )

                Text(
                    text = stringResource(R.string.login_title_sub),
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )

                Spacer(Modifier.height(40.dp))

                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(0.4f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(0.2f)),
                ) {
                    AnimatedContent(targetState = isResetMode, label = "cardContentTransition") { currentIsReset ->
                        Column(modifier = Modifier.padding(32.dp)) {
                            if (!currentIsReset) {
                                Text(
                                    text = stringResource(R.string.login_welcome),
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.headlineSmall,
                                )

                                Spacer(Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = nim,
                                    onValueChange = { input -> if (input.all { it.isDigit() }) nim = input },
                                    label = { Text(stringResource(R.string.login_username_label)) },
                                    isError = !isNimValid,
                                    supportingText =
                                        if (!isNimValid) {
                                            {
                                                Text(
                                                    text = stringResource(R.string.login_username_validation_error),
                                                    color = MaterialTheme.colorScheme.error,
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                    keyboardOptions =
                                        KeyboardOptions(
                                            keyboardType = KeyboardType.Number,
                                            imeAction = ImeAction.Next,
                                        ),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    leadingIcon = { Icon(Icons.Default.Person, stringResource(R.string.login_username_label)) },
                                    shape = RoundedCornerShape(16.dp),
                                    colors =
                                        OutlinedTextFieldDefaults.colors(
                                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(0.2f),
                                            focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                                        ),
                                )

                                Spacer(Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text(stringResource(R.string.login_password_label)) },
                                    visualTransformation =
                                        if (isPasswordVisible) {
                                            VisualTransformation.None
                                        } else {
                                            PasswordVisualTransformation()
                                        },
                                    keyboardOptions =
                                        KeyboardOptions(
                                            keyboardType = KeyboardType.Password,
                                            imeAction = ImeAction.Done,
                                        ),
                                    keyboardActions =
                                        KeyboardActions(
                                            onDone = {
                                                keyboardController?.hide()

                                                if (status == AuthStatus.IDLE && nim.isNotEmpty() && isNimValid && password.isNotEmpty()) {
                                                    onLoginClick(nim, password)
                                                }
                                            },
                                        ),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    leadingIcon = { Icon(Icons.Default.Lock, stringResource(R.string.login_password_label)) },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { },
                                            interactionSource = passwordVisibilityInteraction,
                                        ) {
                                            Icon(
                                                imageVector =
                                                    if (isPasswordVisible) {
                                                        Icons.Default.Visibility
                                                    } else {
                                                        Icons.Default.VisibilityOff
                                                    },
                                                contentDescription = stringResource(R.string.login_password_toggle_desc),
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    colors =
                                        OutlinedTextFieldDefaults.colors(
                                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(0.2f),
                                            focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                                        ),
                                )

                                Spacer(Modifier.height(10.dp))

                                AnimatedVisibility(errorMessage != null) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer.copy(0.2f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(0.2f)),
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

                                Spacer(Modifier.height(10.dp))

                                val interaction = remember { MutableInteractionSource() }
                                val isPressed by rememberPressedState(interaction)
                                val buttonScale by animateFloatAsState(if (isPressed) 0.95f else 1f, label = "buttonScale")

                                Button(
                                    onClick = {
                                        keyboardController?.hide()

                                        scope.launch {
                                            delay(150.milliseconds)
                                            onLoginClick(nim, password)
                                        }
                                    },
                                    modifier =
                                        Modifier.fillMaxWidth().height(56.dp).graphicsLayer {
                                            scaleX = buttonScale
                                            scaleY = buttonScale
                                        },
                                    interactionSource = interaction,
                                    enabled = status == AuthStatus.IDLE && nim.isNotEmpty() && isNimValid && password.isNotEmpty(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors =
                                        ButtonDefaults.buttonColors(
                                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(0.3f),
                                            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(0.6f),
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
                                                    Spacer(Modifier.width(12.dp))
                                                    Text(
                                                        text = stringResource(R.string.login_button_processing),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 16.sp,
                                                    )
                                                }
                                            }

                                            AuthStatus.SUCCESS -> {
                                                Row {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = stringResource(R.string.login_button_success),
                                                        modifier = Modifier.size(22.dp),
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        text = stringResource(R.string.login_button_success),
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Black,
                                                    )
                                                }
                                            }

                                            AuthStatus.IDLE -> {
                                                Text(
                                                    text = stringResource(R.string.login_button_idle),
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Black,
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(10.dp))

                                modeToggleButton(stringResource(R.string.login_forgot_password_link))
                            } else {
                                Text(
                                    text = stringResource(R.string.login_forgot_password_title),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.headlineSmall,
                                )

                                Spacer(Modifier.height(10.dp))

                                Text(
                                    text = stringResource(R.string.login_forgot_password_desc),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 20.sp,
                                    style = MaterialTheme.typography.bodyMedium,
                                )

                                Spacer(Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    label = { Text(stringResource(R.string.login_email_label)) },
                                    isError = !isEmailValid,
                                    supportingText =
                                        if (!isEmailValid) {
                                            {
                                                Text(
                                                    text = stringResource(R.string.login_email_validation_error),
                                                    color = MaterialTheme.colorScheme.error,
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    keyboardActions =
                                        KeyboardActions(
                                            onDone = {
                                                keyboardController?.hide()

                                                if (status == AuthStatus.IDLE && email.isNotEmpty() && isEmailValid) {
                                                    onResetPasswordClick(email)
                                                }
                                            },
                                        ),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    leadingIcon = { Icon(Icons.Default.Email, stringResource(R.string.login_email_label)) },
                                    shape = RoundedCornerShape(16.dp),
                                    colors =
                                        OutlinedTextFieldDefaults.colors(
                                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(0.2f),
                                            focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                                        ),
                                )

                                Spacer(Modifier.height(10.dp))

                                AnimatedVisibility(errorMessage != null) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer.copy(0.2f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(0.2f)),
                                    ) {
                                        Text(
                                            text = errorMessage.orEmpty(),
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(12.dp),
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }

                                AnimatedVisibility(visible = status == AuthStatus.SUCCESS) {
                                    Surface(
                                        color = Color(0xFFE6F4EA).copy(0.2f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        border = BorderStroke(1.dp, Color(0xFF137333).copy(0.3f)),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.login_reset_success_banner),
                                            color = Color(0xFF137333),
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(12.dp),
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }

                                Spacer(Modifier.height(10.dp))

                                val resetInteraction = remember { MutableInteractionSource() }
                                val isResetPressed by rememberPressedState(resetInteraction)
                                val resetBtnScale by animateFloatAsState(
                                    targetValue = if (isResetPressed) 0.95f else 1f,
                                    label = "resetBtnScale",
                                )

                                Button(
                                    onClick = {
                                        keyboardController?.hide()
                                        scope.launch {
                                            delay(150.milliseconds)
                                            onResetPasswordClick(email)
                                        }
                                    },
                                    modifier =
                                        Modifier.fillMaxWidth().height(56.dp).graphicsLayer {
                                            scaleX = resetBtnScale
                                            scaleY = resetBtnScale
                                        },
                                    interactionSource = resetInteraction,
                                    enabled = status == AuthStatus.IDLE && email.isNotEmpty() && isEmailValid,
                                    shape = RoundedCornerShape(16.dp),
                                    colors =
                                        ButtonDefaults.buttonColors(
                                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(0.3f),
                                            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(0.6f),
                                        ),
                                ) {
                                    AnimatedContent(
                                        targetState = status,
                                        label = "ResetButtonStateAnimation",
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
                                                        text = stringResource(R.string.login_reset_button_processing),
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Bold,
                                                    )
                                                }
                                            }

                                            AuthStatus.SUCCESS -> {
                                                Row {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = stringResource(R.string.login_reset_button_success),
                                                        modifier = Modifier.size(22.dp),
                                                        tint = Color(0xFF137333),
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = stringResource(R.string.login_reset_button_success),
                                                        color = Color(0xFF137333),
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Black,
                                                    )
                                                }
                                            }

                                            AuthStatus.IDLE -> {
                                                Text(
                                                    text = stringResource(R.string.login_reset_button_idle),
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Black,
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(10.dp))

                                modeToggleButton(stringResource(R.string.login_back_to_login))
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
        onResetPasswordClick = { },
        onResetStatus = { },
    )
}
