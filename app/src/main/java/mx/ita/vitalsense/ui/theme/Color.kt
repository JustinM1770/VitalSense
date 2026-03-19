package mx.ita.vitalsense.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Brand ───────────────────────────────────────────────────────────────────
val PrimaryBlue      = Color(0xFF1169FF)   // botones, bottom nav, links activos
val PrimaryBlueDark  = Color(0xFF0A4FCC)   // pressed state
val PrimaryBlueLight = Color(0xFFEBF2FF)   // fondos de pantallas blancas (Login, Register, etc.)

// ─── Backgrounds ─────────────────────────────────────────────────────────────
val DashBg           = Color(0xFFBDD9F2)   // fondo azul medio del Dashboard
val SurfaceWhite     = Color(0xFFFFFFFF)   // tarjetas y superficies
val InputBg          = Color(0xFFF0F2F5)   // fondo de campos de texto

// ─── Splash / Onboarding ─────────────────────────────────────────────────────
val LogoNavy         = Color(0xFF0A2540)
val LogoTeal         = Color(0xFF52A2C4)
val OnboardingBlue   = Color(0xFF126AFF)
val OnboardingButtonText  = Color(0xFFB6D8FF)
val OnboardingDotInactive = Color(0xFFE3E1E8)
val GradientStart    = Color(0xFFFFFFFF)
val GradientEnd      = Color(0xFFB6D8FF)

// ─── Text ────────────────────────────────────────────────────────────────────
val TextPrimary      = Color(0xFF0D1B2A)
val TextSecondary    = Color(0xFF6B7A8D)
val TextHint         = Color(0xFFB0B8C4)
val TextLink         = PrimaryBlue

// ─── Metric accents ──────────────────────────────────────────────────────────
val HeartRateRed     = Color(0xFFFF4560)
val HeartRateSoft    = Color(0xFFFDE8E8)
val GlucoseOrange    = Color(0xFFFF9800)
val GlucoseSoft      = Color(0xFFFFF3E0)
val SpO2Green        = Color(0xFF4CAF50)
val SpO2Soft         = Color(0xFFE8F5E9)
val SleepGreen       = Color(0xFF00C48C)   // anillo de sueño (circular chart)
val ChartRed         = Color(0xFFFF4560)   // línea Heart Rate en gráfica

// ─── Alerts ──────────────────────────────────────────────────────────────────
val AlertBackground  = Color(0xFFFFF8E1)
val AlertBorder      = Color(0xFFFFB300)
val AlertText        = Color(0xFF795548)
val AlertWarning     = Color(0xFFFFF3CD)   // banner "confidencial" en Documentos

// ─── Aliases usados en código legacy (no eliminar) ───────────────────────────
val DashCard              = SurfaceWhite
val DashBlue              = PrimaryBlue
val TextMuted             = TextHint              // VitalsLineChart y otros
val NeomorphicBackground  = InputBg
val NeomorphicLightShadow = Color(0xFFFFFFFF)
val NeomorphicDarkShadow  = Color(0xFFA3B1C6)
