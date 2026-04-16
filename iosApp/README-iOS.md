# BioMetric AI iOS App — Setup Instructions

## Prerequisites
- Xcode 15.4 or later
- macOS Sonoma or later
- CocoaPods (`gem install cocoapods`) or Swift Package Manager
- Firebase project already configured (same project as Android)
- Apple Developer account (for device testing, NFC, HealthKit)

---

## 1. Create the Xcode Project

Open Xcode and create a new project:

1. **File → New → Project**
2. Select **iOS → App**
3. Set the following:
   - **Product Name**: `iosApp`
   - **Team**: your Apple Developer team
   - **Organization Identifier**: `mx.ita`
   - **Bundle Identifier**: `mx.ita.vitalsense`
   - **Interface**: SwiftUI
   - **Language**: Swift
   - **Minimum Deployments**: iOS 16.0
4. Save in: `BioMetricAI/iosApp/`

---

## 2. Add Swift source files

After creating the project, add all the Swift files already created in this directory:

```
iosApp/
├── BioMetricAIApp.swift
├── ContentView.swift
├── Navigation/
│   ├── AppNavigation.swift
│   └── MainTabView.swift
├── Views/
│   ├── Splash/SplashView.swift
│   ├── Onboarding/OnboardingView.swift
│   ├── Auth/LoginView.swift
│   ├── Auth/RegisterView.swift
│   ├── Dashboard/DashboardView.swift
│   ├── Dashboard/DashboardViewModel.swift
│   ├── Patient/PatientDetailView.swift
│   ├── Chat/ChatBotView.swift
│   └── Profile/ProfileView.swift
├── Models/SharedModels.swift
├── Services/
│   ├── FirebaseService.swift
│   ├── BLEService.swift
│   ├── NFCService.swift
│   └── HealthKitService.swift
└── Utilities/ColorExtension.swift
```

In Xcode: drag all folders into the project navigator, checking "Copy items if needed" and "Create groups".

---

## 3. Add Firebase via Swift Package Manager

1. In Xcode: **File → Add Package Dependencies...**
2. Enter: `https://github.com/firebase/firebase-ios-sdk`
3. Select version: **11.x.x** (latest stable)
4. Add these products:
   - `FirebaseAuth`
   - `FirebaseDatabase`
   - `FirebaseMessaging` (optional, for push notifications)

---

## 4. Add google-services plist

1. Go to [Firebase Console](https://console.firebase.google.com) → your project → iOS app
3. Register app with bundle ID: `mx.ita.vitalsense`
4. Download `GoogleService-Info.plist`
5. Make sure "Copy items if needed" is checked
5. Make sure "Copy items if needed" is checked

---

## 5. Configure Info.plist capabilities

Open `Info.plist` and add:

```xml
<!-- Claude AI API key -->
<key>CLAUDE_API_KEY</key>
<string>YOUR_API_KEY_HERE</string>

<!-- NFC usage description -->
<key>NFCReaderUsageDescription</key>
   <string>BioMetric AI usa NFC para leer el sensor Freestyle Libre</string>

<!-- Bluetooth usage descriptions -->
<key>NSBluetoothAlwaysUsageDescription</key>
   <string>BioMetric AI usa Bluetooth para conectarse a sensores de salud</string>
<key>NSBluetoothPeripheralUsageDescription</key>
   <string>BioMetric AI usa Bluetooth para conectarse a sensores de salud</string>

<!-- HealthKit usage descriptions -->
<key>NSHealthShareUsageDescription</key>
   <string>BioMetric AI lee tus datos de salud para monitoreo</string>
<key>NSHealthUpdateUsageDescription</key>
   <string>BioMetric AI actualiza tus datos de salud</string>
```

---

## 6. Enable Xcode Capabilities

In Xcode → target → **Signing & Capabilities**, add:

- **HealthKit** — for Apple Watch / Health app data
- **Near Field Communication Tag Reading** — for Freestyle Libre NFC
- **Push Notifications** — for Firebase Cloud Messaging
- **Background Modes** → check "Remote notifications"

---

## 7. Add fonts

Copy the Manrope font files from the Android project into the iOS project:
```
app/src/main/res/font/manrope_medium.ttf   → iosApp/Fonts/Manrope-Medium.ttf
app/src/main/res/font/manrope_semibold.ttf → iosApp/Fonts/Manrope-SemiBold.ttf
app/src/main/res/font/manrope_bold.ttf     → iosApp/Fonts/Manrope-Bold.ttf
```

Then in `Info.plist`, add:
```xml
<key>UIAppFonts</key>
<array>
    <string>Manrope-Medium.ttf</string>
    <string>Manrope-SemiBold.ttf</string>
    <string>Manrope-Bold.ttf</string>
</array>
```

---

## 8. Add image assets

In Xcode's `Assets.xcassets`, add:
- `ic_logo_eye` — from `app/src/main/res/drawable/ic_logo_eye.png`
- `illus_stethoscope` — from `app/src/main/res/drawable/illus_stethoscope.png`
- `illus_qr` — from `app/src/main/res/drawable/illus_qr.png`
- `PrimaryBlue` color set: `#1169FF`

---

## 9. Integrate KMP shared module (optional — advanced)

To use the Kotlin shared module from iOS:

1. Build the shared framework from the root project:
   ```bash
   cd BioMetricAI
   ./gradlew :shared:assembleReleaseXCFramework
   ```
   This generates: `shared/build/XCFrameworks/release/shared.xcframework`

2. In Xcode → target → **General → Frameworks, Libraries, and Embedded Content**:
   - Click **+** → **Add Other... → Add Files...**
   - Select `shared/build/XCFrameworks/release/shared.xcframework`
   - Set to **Embed & Sign**

3. In Swift, import and use:
   ```swift
   import shared

   // Use KMP models directly
   let ai = BioMetricAI(apiKey: "your-key")
   ```

---

## 10. Run the app

1. Select your target device (simulator or physical iPhone)
2. Press **Cmd+R** to build and run
3. For NFC testing, a physical iPhone is required (NFC does not work in simulator)

---

## Firebase Realtime Database rules (for development)

```json
{
  "rules": {
    ".read": "auth != null",
    ".write": "auth != null"
  }
}
```

---

## Architecture notes

- **SwiftUI + ObservableObject** mirrors the Android MVVM pattern
- `DashboardViewModel.swift` observes Firebase `patients/` — same node as Android
- `SharedModels.swift` mirrors the KMP data classes; once the `.xcframework` is integrated these can be replaced with direct KMP imports
- All Firebase writes use the same schema as the Android/KMP layer so both apps share the same backend data
