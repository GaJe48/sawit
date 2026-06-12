package com.gaje48.lms.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.gaje48.lms.R

val RighteousFontFamily =
    FontFamily(
        Font(R.font.righteous_regular, FontWeight.Normal),
    )

val UbuntuSansFontFamily =
    FontFamily(
        Font(R.font.ubuntu_sans_light, FontWeight.Light),
        Font(R.font.ubuntu_sans_regular, FontWeight.Normal),
        Font(R.font.ubuntu_sans_medium, FontWeight.Medium),
        Font(R.font.ubuntu_sans_semibold, FontWeight.SemiBold),
        Font(R.font.ubuntu_sans_bold, FontWeight.Bold),
        Font(R.font.ubuntu_sans_extrabold, FontWeight.ExtraBold),
    )

private val defaultTypography = Typography()

val AppTypography =
    Typography(
        displayLarge = defaultTypography.displayLarge.copy(fontFamily = UbuntuSansFontFamily),
        displayMedium = defaultTypography.displayMedium.copy(fontFamily = UbuntuSansFontFamily),
        displaySmall = defaultTypography.displaySmall.copy(fontFamily = UbuntuSansFontFamily),
        headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = UbuntuSansFontFamily),
        headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = UbuntuSansFontFamily),
        headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = UbuntuSansFontFamily),
        titleLarge = defaultTypography.titleLarge.copy(fontFamily = UbuntuSansFontFamily),
        titleMedium = defaultTypography.titleMedium.copy(fontFamily = UbuntuSansFontFamily),
        titleSmall = defaultTypography.titleSmall.copy(fontFamily = UbuntuSansFontFamily),
        bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = UbuntuSansFontFamily),
        bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = UbuntuSansFontFamily),
        bodySmall = defaultTypography.bodySmall.copy(fontFamily = UbuntuSansFontFamily),
        labelLarge = defaultTypography.labelLarge.copy(fontFamily = UbuntuSansFontFamily),
        labelMedium = defaultTypography.labelMedium.copy(fontFamily = UbuntuSansFontFamily),
        labelSmall = defaultTypography.labelSmall.copy(fontFamily = UbuntuSansFontFamily),
    )
