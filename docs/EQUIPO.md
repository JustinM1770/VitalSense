# BioMetric AI — ITA Aguascalientes 🏆
## InnovaTecNM 2026 · Categoría: Servicios para la Salud Humana

> Retomamos HEALTHSENSOR (ya avanzó a Regional desde el ITA en 2025).
> Esta vez vamos por el **Nacional**.

---

## El proyecto en una oración

**BioMetric AI** es una plataforma de monitoreo remoto para adultos mayores que viven solos:
el adulto usa un reloj y un sensor de glucosa (Freestyle Libre), los datos llegan automáticamente
a Firebase, y el tutor/familiar ve las métricas en tiempo real con recomendaciones de IA
para **prevenir enfermedades crónicas antes de que aparezcan**.

---

## Flujo completo del sistema

```
Adulto Mayor                          Tutor / Familiar
────────────                          ────────────────
Freestyle Libre (glucosa en brazo)    App BioMetric AI (Android)
      ↓ BLE                               ↑
ESP32 en casa (hub WiFi enchufado)→ Firebase RT ← IA (Claude API)
      ↑ BLE                               ↓
Reloj inteligente                     "Glucosa elevada 3 días.
  • HR + SpO2                          Riesgo diabetes t2.
  • Botón SOS ───────────────────→     Recomendar reducir
  • Recibe notif medicamento ←──────   carbohidratos..."
```

---

## Reglas del equipo

| Regla | Detalle |
|---|---|
| **Deadline** | **Viernes 20 de Marzo, 11:59 PM** |
| **Branch** | Cada quien trabaja en su rama: `feature/axhel`, `feature/omar`, `feature/jonathan`, `feature/miguel` |
| **No tocar** | No modifiquen `Color.kt`, `Theme.kt`, `AppNavigation.kt` ni `HealthSensorApp.kt` — esos son de Justin |
| **Firebase** | Usar exactamente las rutas definidas en este doc — no inventar nodos nuevos |
| **Figma** | Las imágenes de referencia están en `figma_screens.zip` (se las mandó Justin) |

---

## Firebase — Proyecto

- **Proyecto:** vitalsenseai-1cb9f
- **DB URL:** https://vitalsenseai-1cb9f-default-rtdb.firebaseio.com
- **Console:** https://console.firebase.google.com → vitalsenseai-1cb9f

---

## Nodos Firebase por responsable

```
users/{userId}/
  ├── profile/          → OMAR
  ├── datosMedicos/     → OMAR
  ├── documentos/       → OMAR
  ├── cuestionario/     → MIGUEL
  └── notificaciones/   → AXHEL

patients/{patientId}/   → JUSTIN (no tocar)
sleep/{userId}/         → JONATHAN
medications/{userId}/   → JONATHAN
vitals/current/         → JUSTIN (no tocar)
```

---

## Colores del diseño (NO cambiar)

| Token | Hex | Uso |
|---|---|---|
| `PrimaryBlue` | `#1169FF` | Botones, bottom nav, links |
| `DashBg` | `#BDD9F2` | Fondo del Dashboard |
| `SurfaceWhite` | `#FFFFFF` | Cards |
| `InputBg` | `#F0F2F5` | Campos de texto |
| `HeartRateRed` | `#FF4560` | Gráfica HR |
| `SleepGreen` | `#00C48C` | Anillo de sueño |

---

## Instrucciones individuales

| Integrante | Archivo |
|---|---|
| Axhel | `docs/AXHEL.md` |
| Omar | `docs/OMAR.md` |
| Jonathan | `docs/JONATHAN.md` |
| Miguel | `docs/MIGUEL.md` |

---

## Por qué podemos ganar el Nacional

- El ITA ya avanzó con HEALTHSENSOR en 2025 — el jurado conoce la institución
- BioMetric AI tiene hardware real (ESP32 + Freestyle Libre) + IA + app — la mayoría de proyectos de salud solo tienen app
- El caso de uso (adulto mayor solo, prevención de diabetes) tiene el mayor impacto social de la categoría
- El prototipo funciona en demo en vivo = 50% del puntaje nacional

**Si el prototipo funciona en el stand, ganamos.**
