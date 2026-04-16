# BioMetric AI 🩺

**BioMetric AI** es una plataforma de monitoreo de salud en tiempo real que integra una aplicación Android para teléfono, una aplicación Wear OS para reloj inteligente y un backend en Firebase. Permite monitorear signos vitales, enviar alertas SOS con ubicación, gestionar medicamentos y acceder a datos de salud históricos.

---

## 📱 Módulos del Proyecto

| Módulo | Descripción |
|--------|-------------|
| `app`  | Aplicación Android para teléfono (Kotlin + Jetpack Compose) |
| `wear` | Aplicación Wear OS para reloj inteligente (Kotlin + Wear Compose) |
| `shared` | Código compartido entre módulos |
| `web`  | Interfaz web (panel de control) |
| `functions` | Cloud Functions de Firebase |

---

## ✨ Funcionalidades Principales

### Aplicación del Teléfono (`app`)
- **Dashboard en tiempo real** con frecuencia cardíaca y oxigenación en sangre (SpO₂)
- **Sincronización con Health Connect** para leer datos históricos del reloj
- **Gestión de dispositivos**: vinculación con reloj vía código de emparejamiento de 8 caracteres
- **Alertas SOS**: recepción de alertas de emergencia desde el reloj con ubicación GPS y código QR
- **Monitoreo de glucosa** mediante NFC con sensores Freestyle Libre
- **Recordatorio de medicamentos** con configuración de horarios
- **Informe diario de salud** con historial y estadísticas
- **Autenticación** mediante correo, Google, Facebook y biométrico
- **Login anónimo** en el reloj para sincronización independiente

### Aplicación del Reloj (`wear`)
- **Monitoreo continuo de BPM** usando Wear Health Services (MeasureClient)
- **Monitoreo de SpO₂** vía Health Connect (OxygenSaturationRecord) con fallback a sensor OEM
- **Monitoreo en segundo plano** (pantalla apagada): `PassiveListenerService` para HR continua
- **Keep-alive Firebase**: sincronización de últimos valores conocidos cada 30 s
- **Alerta SOS** con ubicación GPS en caché (respuesta inmediata sin esperar al GPS)
- **Detector de sacudida** para confirmar SOS
- **Pantalla de emparejamiento** con código temporal de 5 minutos
- **Pantalla de emergencia** con código QR y PIN para primeros auxilios
- **Modo ambiente** (pantalla siempre activa reduciendo brillo)
- **Arranque automático** del servicio de monitoreo al encender el reloj

---

## 🏗️ Arquitectura

```
┌─────────────────────┐         ┌─────────────────────┐
│   Teléfono (app)    │◄───────►│  Firebase Realtime   │
│                     │  sync   │  Database            │
│  HealthConnectVM    │         │  vitals/current/{uid}│
│  DeviceViewModel    │         │  alerts/{uid}        │
│  HealthConnectRepo  │         │  patients/{uid}      │
└─────────────────────┘         └──────────┬──────────┘
                                            │ sync
┌─────────────────────┐                    │
│    Reloj (wear)     │◄───────────────────┘
│                     │
│  VitalSignsService  │  ← Foreground Service
│  HeartRateManager   │  ← Health Services MeasureClient
│  SpO2Manager        │  ← Health Connect + SensorManager
│  PassiveDataReceiver│  ← PassiveListenerService (screen off)
│  ShakeDetector      │  ← SOS por acelerómetro
└─────────────────────┘
```

---

## 🔧 Stack Tecnológico

| Área | Tecnología |
|------|-----------|
| Lenguaje | Kotlin |
| UI Teléfono | Jetpack Compose + Material 3 |
| UI Reloj | Wear Compose |
| Backend | Firebase Realtime Database + Auth + Storage |
| Salud | Wear Health Services + Health Connect |
| Localización | Google Play Services Location (FusedLocationProvider) |
| Sensores BPM | `MeasureClient` (Health Services) |
| Sensores SpO₂ | Health Connect `OxygenSaturationRecord` + SensorManager OEM |
| Monitoreo background | `PassiveListenerService` (Health Services) |
| QR | ZXing 3.5.3 |
| NFC | Android NFC API (Freestyle Libre) |
| Autenticación | Firebase Auth + Google Identity + Facebook SDK + Biometric |
| Notificaciones | FCM (Firebase Cloud Messaging) |

---

## 🚀 Configuración del Proyecto

### Prerrequisitos

- Android Studio Hedgehog o superior
- SDK mínimo: **API 26** (teléfono), **API 30** (reloj)
- Dispositivo Wear OS 3+ con sensor de frecuencia cardíaca y SpO₂
- Cuenta de Firebase

### Instalación

1. **Clonar el repositorio:**
   ```bash
   git clone https://github.com/JustinM1770/VitalSense.git
   cd VitalSense
   ```

2. **Configurar Firebase:**
   - Crea un proyecto en [Firebase Console](https://console.firebase.google.com)
   - Descarga `google-services.json` y colócalo en `app/` y en `wear/`
   - Habilita **Realtime Database**, **Authentication** y **Storage**

3. **Ejecutar en el teléfono:**
   ```
   Android Studio → Run → app
   ```

4. **Ejecutar en el reloj:**
   ```
   Android Studio → Run → wear
   ```

---

## 🔗 Vinculación Teléfono ↔ Reloj

1. Abre BioMetric AI en el **reloj** — aparece un código de 8 caracteres (válido 5 minutos).
2. Abre BioMetric AI en el **teléfono** → Conectar wearable → ingresa el código.
3. Una vez emparejado, el reloj inicia automáticamente el monitoreo de signos vitales.

---

## 📡 Estructura de Firebase

```
/vitals/current/{userId}
  ├── heartRate        (número)
  ├── spo2             (número)
  └── timestamp        (ms)

/alerts/{userId}/{alertId}
  ├── type             ("SOS")
  ├── lat, lng         (coordenadas GPS)
  ├── status           ("active" | "accepted" | "resolved")
  └── timestamp        (ms)

/patients/{userId}
  ├── activeEmergency  (token de emergencia activa)
  └── medications/...  (recordatorios de medicamentos)

/patients/pairing_codes/{code}
  ├── paired           (boolean)
  └── userId           (string)
```

---

## 📋 Permisos del Reloj

| Permiso | Uso |
|---------|-----|
| `BODY_SENSORS` | Lectura de sensores en primer plano |
| `BODY_SENSORS_BACKGROUND` | Lectura de sensores con pantalla apagada |
| `ACCESS_FINE_LOCATION` | Coordenadas GPS para SOS |
| `ACCESS_BACKGROUND_LOCATION` | GPS activo con pantalla apagada |
| `ACTIVITY_RECOGNITION` | Detección de movimiento |
| `POST_NOTIFICATIONS` | Notificaciones de SOS y medicamentos |

> **Nota:** `ACCESS_BACKGROUND_LOCATION` debe habilitarse manualmente en:  
> *Ajustes del reloj → Privacidad → Permisos → Ubicación → Permitir todo el tiempo*

---

## 🛠️ Scripts útiles

```bash
# Compilar el módulo del reloj
./gradlew :wear:assembleDebug

# Compilar el módulo del teléfono
./gradlew :app:assembleDebug

# Limpiar y recompilar todo
./gradlew clean assembleDebug
```

---

## 📄 Licencia

Este proyecto fue desarrollado como proyecto académico en el **Instituto Tecnológico de Aguascalientes (ITA)**.

---

*BioMetric AI — Monitoreo de salud inteligente en tiempo real* 💙
