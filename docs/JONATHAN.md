# BioMetric AI — Instrucciones de Jonathan
## Deadline: Viernes 20 de Marzo, 11:59 PM

---

## Tu rama de trabajo
```
git checkout -b feature/jonathan
git push -u origin feature/jonathan
```

---

## Tu trabajo: 2 pantallas + datos de demo en Firebase

---

### 1. Reporte Diario — `ui/report/ReporteDiarioScreen.kt` (ya existe, conectar datos reales)

**Ver:** `reporte_diario.png` del zip

**Qué hace esta pantalla:**
- El tutor ve el reporte de salud del adulto por fecha
- Selector de fechas horizontal (timeline)
- Tarjeta con score de salud (A+) + radar chart
- Gráfica de métricas de salud (Heart Rate semanal)

**Firebase — nodos a leer:**
```
sleep/{userId}/{fecha}/
  score     → Int (0-100, mapear a letra: 90+=A+, 80+=A, 70+=B+...)
  horas     → Float
  estado    → String "Bueno/Regular/Malo"

patients/{patientId}/history/
  heartRate, glucose, spo2, timestamp
  (usar el primer paciente para la demo)
```

**Selector de fechas (DateStrip):**
```kotlin
// Mostrar 7 días: -3 a +3 desde hoy
// Al seleccionar una fecha → cargar datos de ese día de Firebase
val fechaKey = selectedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
val sleepRef = db.getReference("sleep/$userId/$fechaKey")
```

**Score → Letra:**
```kotlin
fun scoreToGrade(score: Int) = when {
    score >= 90 -> "A+"
    score >= 80 -> "A"
    score >= 70 -> "B+"
    score >= 60 -> "B"
    else -> "C"
}
```

**Radar chart (Figma):**
- Ejes: Sueño, Glucosa, Presión arterial, Oxígeno
- Dibujarlo con Canvas de Compose — polígono con 4 puntos
- Colores: verde teal para el área, línea borde
- No necesita ser perfecto — lo importante es que se vea en la demo

```kotlin
// Radar chart básico con Canvas:
Canvas(modifier = Modifier.size(160.dp)) {
    val center = Offset(size.width / 2, size.height / 2)
    val radius = size.minDimension / 2 * 0.8f
    val angles = listOf(270f, 0f, 90f, 180f) // arriba, der, abajo, izq
    val values = listOf(sleepPct, glucosePct, presionPct, oxigenoPct) // 0f a 1f

    // Dibujar fondo gris
    // Dibujar área de datos (verde teal)
    // Dibujar puntos
}
```

---

### 2. Reporte Detallado — NUEVA pantalla

**Ver:** `reporte_detallado.png` del zip

**Archivo a crear:** `ui/patient/PatientDetailScreen.kt` (ya existe — actualizar)

**Qué hace:**
- Gráfica de Heart Rate expandida por meses (Enero → Julio)
- Sección de Medicamentos debajo
- Header: "Reporte Detallado" + nombre del paciente a la derecha

**Firebase — nodos a leer:**
```
patients/{patientId}/history/
  heartRate, timestamp → agrupar por mes para la gráfica

medications/{userId}/{medId}/
  nombre, dosis, horario, activo
```

**Gráfica por meses:**
```kotlin
// Agrupar los snapshots del historial por mes
// Calcular promedio de HR por mes
// Mostrar en el chart existente (VitalsLineChart o WeeklyHrChart adaptado)
val meses = listOf("Enero","Febrero","Marzo","Abril","Mayo","Junio","Julio")
```

**Sección medicamentos:**
```kotlin
// Leer de Firebase: medications/{userId}/
// Mostrar lista: nombre + dosis + horario
// Si activo == true → mostrar
```

---

### 3. Datos de demo en Firebase (MUY IMPORTANTE para el concurso)

Necesitamos datos que se vean bien en el stand. Agrégalos manualmente en la **Firebase Console**:

**Sueño (últimos 7 días):**
```
sleep/USER_ID/
  2026-03-13: { score: 70, horas: 7.5, estado: "Bueno" }
  2026-03-12: { score: 55, horas: 6.0, estado: "Regular" }
  2026-03-11: { score: 82, horas: 8.0, estado: "Bueno" }
  2026-03-10: { score: 45, horas: 5.0, estado: "Malo" }
  2026-03-09: { score: 78, horas: 7.0, estado: "Bueno" }
  2026-03-08: { score: 88, horas: 8.5, estado: "Bueno" }
  2026-03-07: { score: 60, horas: 6.5, estado: "Regular" }
```

**Medicamentos:**
```
medications/USER_ID/
  med_001: { nombre: "Metformina", dosis: "500mg", horario: "08:00", activo: true }
  med_002: { nombre: "Losartán", dosis: "50mg", horario: "20:00", activo: true }
```

*(Reemplaza USER_ID con el UID real del usuario de prueba en Firebase Console)*

---

### 4. Pruebas en tu dispositivo Android (después del 20 de Marzo)

Una vez que Justin haga merge de todo:
- Instalar el APK en tu cel
- Verificar TODOS los flujos:
  - [ ] Login con email → va a Dashboard
  - [ ] Login con Google → va a Dashboard
  - [ ] Dashboard muestra datos de Firebase
  - [ ] Reporte Diario carga por fecha
  - [ ] Notificaciones se muestran
  - [ ] Editar Perfil guarda y recarga
  - [ ] Navegar entre todas las pantallas sin crashes

---

## Puntos importantes

- Usa `Manrope` para toda la tipografía
- Para el radar chart no necesita ser perfecto — suficiente con que represente los 4 ejes visualmente
- El `PatientDetailScreen.kt` ya tiene un ViewModel inline — reutilízalo
- **No modifiques** `AppNavigation.kt`, `Color.kt` ni `Theme.kt`
- El fondo de ReporteDiario: blanco arriba + tarjeta azul claro para el radar chart

## Al terminar
```
git add .
git commit -m "feat(jonathan): reporte diario, reporte detallado con datos Firebase"
git push origin feature/jonathan
```
Avísale a Justin para que haga el merge.
