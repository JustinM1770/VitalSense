# BioMetric AI — Instrucciones de Miguel
## Deadline: Viernes 20 de Marzo, 11:59 PM

---

## Tu rama de trabajo
```
git checkout -b feature/miguel
git push -u origin feature/miguel
```

---

## Tu trabajo: flujo completo de Cuestionarios (4 pantallas)

Este es el flujo que aparece justo después de que el tutor se registra por primera vez.
Son 4 pantallas encadenadas que recopilan el perfil del adulto mayor que va a monitorear.

---

### Pantalla 1: Cuestionario Personal — `ui/cuestionario/CuestionarioScreen.kt`

**Ver:** `cuestionario_personal.png` del zip

**Qué hace:**
- Recopila los datos personales del usuario/adulto mayor
- Al presionar "Siguiente" → guarda en Firebase y va a las preguntas

**Campos:**
- Nombre (TextField)
- Apellidos (TextField)
- Email (TextField, read-only — prellenado con el email del login)
- Contraseña (TextField, password)
- Nacimiento (TextField, placeholder `**/**/2006`)
- Celular (TextField, placeholder `+52 xxx`)
- Género (TextField o Dropdown: Hombre/Mujer/Otro)
- Frecuencia promedio (TextField numérico, con ❤️ a la izquierda)
- Tipo de Sangre (TextField: O+, A-, B+, etc.)

**Diseño:**
- Ícono de Face ID negro redondeado arriba (solo visual)
- "Informacion de tu perfil" como título
- Campos en 2 columnas donde aplique (Nombre|Apellidos, Nacimiento|Celular)
- "Da Click En La Casilla Si Aceptas Verificación Por Face ID" — checkbox abajo
- Botón "Siguiente" — azul, centrado, radius 32dp

**Guardar en Firebase al presionar Siguiente:**
```kotlin
val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
val db = FirebaseDatabase.getInstance()

// Guardar perfil
db.getReference("users/$userId/profile").setValue(mapOf(
    "nombre" to nombre,
    "apellidos" to apellidos,
    "email" to email,
    "nacimiento" to nacimiento,
    "celular" to celular,
    "genero" to genero,
    "frecuenciaPromedio" to frecuenciaPromedio.toIntOrNull(),
    "tipoSangre" to tipoSangre
))

// Navegar a tabs de cuestionario
onNext()
```

---

### Pantallas 2, 3 y 4: Cuestionario con Tabs

**Ver:** `cuest_alim.png`, `cuest_fisica.png`, `cuest_salud.png` del zip

Estas 3 pantallas son UNA sola pantalla con 3 tabs:
- Tab 1: **Alimentación** (activo al entrar)
- Tab 2: **Act. Fisica**
- Tab 3: **Salud**

**Crear en:** `ui/cuestionario/CuestionarioTabsScreen.kt` (archivo nuevo)

**Estructura general:**
```kotlin
@Composable
fun CuestionarioTabsScreen(
    onBack: () -> Unit,
    onFinish: () -> Unit, // al terminar → va al Dashboard
) {
    var tabActual by remember { mutableStateOf(0) }
    val tabs = listOf("Alimentación", "Act. Fisica", "Salud")

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        // Título
        Text("Ayudanos A Conocer Mas De [Nombre]",
            textAlign = TextAlign.Center,
            color = PrimaryBlue,
            fontWeight = FontWeight.Bold)

        // Tabs (chips redondeados, no los tabs estándar de Material)
        Row {
            tabs.forEachIndexed { index, tab ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (tabActual == index) PrimaryBlue else Color(0xFFEEEEEE))
                        .clickable { tabActual = index }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(tab, color = if (tabActual == index) Color.White else Color.Gray)
                }
            }
        }

        // Contenido del tab actual
        when (tabActual) {
            0 -> TabAlimentacion(...)
            1 -> TabActFisica(...)
            2 -> TabSalud(...)
        }
    }
}
```

---

**Tab 1 — Alimentación (`cuest_alim.png`):**
- Pregunta: "¿Cuál es su nombre?" → TextField
- Pregunta: "Escribe tu número telefonico" → TextField con "+52" prefijo
- Botón "Siguiente" → cambia a tab 1 (Act. Física)

---

**Tab 2 — Actividad Física (`cuest_fisica.png`):**
- "4. ¿Qué actividad realiza o le gustaría realizar?"
- Radio buttons (selección única):
  - ○ Estiramientos / Yoga
  - ● Caminata (seleccionado por defecto)
  - ○ Natación O Ejercicios En Agua
  - ○ Ninguno
- Botones: "Anterior" (gris) + "Siguiente" (azul)

**Radio button personalizado:**
```kotlin
@Composable
fun RadioOption(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFFEBF2FF) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) PrimaryBlue else Color.Gray, CircleShape)
                .background(if (selected) PrimaryBlue else Color.Transparent)
        )
        Spacer(Modifier.width(12.dp))
        Text(text, fontFamily = Manrope, fontSize = 15.sp)
    }
}
```

---

**Tab 3 — Salud (`cuest_salud.png`):**
- "9. ¿Ha tenido alguno de estos eventos en el último año?"
- Radio buttons:
  - ○ Desmayos O Pérdida De Conciencia
  - ● Caídas Accidentales
  - ○ Hospitalización De Emergencia
  - ○ Ninguno De Los Anteriores
- Botones: "Anterior" + "Siguiente"
- Al presionar "Siguiente" en este tab → guardar en Firebase + ir al Dashboard

**Guardar todo en Firebase al terminar:**
```kotlin
val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
val db = FirebaseDatabase.getInstance()
val cuestionarioRef = db.getReference("users/$userId/cuestionario")

cuestionarioRef.setValue(mapOf(
    "completado" to true,
    "alimentacion" to mapOf(
        "nombre" to nombreAlim,
        "telefono" to telefonoAlim
    ),
    "actividadFisica" to mapOf(
        "actividad" to actividadSeleccionada
    ),
    "salud" to mapOf(
        "eventoPasado" to eventoSeleccionado
    )
)).addOnSuccessListener {
    onFinish() // va al Dashboard
}
```

---

## Agregar ruta en AppNavigation

**Avísale a Justin** que necesita agregar en `AppNavigation.kt`:
```kotlin
// Después de Route.CUESTIONARIO, agregar:
composable(Route.CUESTIONARIO_TABS) {
    CuestionarioTabsScreen(
        onBack = { navController.popBackStack() },
        onFinish = {
            navController.navigate(Route.DASHBOARD) {
                popUpTo(0) { inclusive = true }
            }
        }
    )
}
```
Y en `Route` object:
```kotlin
const val CUESTIONARIO_TABS = "cuestionario_tabs"
```

---

## Puntos importantes

- Usa `PrimaryBlue` y `Manrope` (ya importados)
- Los tabs NO son los `Tab` de Material3 — son `Box` con `clip(RoundedCornerShape)` para verse como chips
- El título "Ayudanos A Conocer Mas De [User]" debe mostrar el nombre real del usuario de Firebase Auth
- **No modifiques** `AppNavigation.kt` directamente — avísale a Justin con el código que necesita agregar
- No uses `OutlinedTextField` — usa `Box` + `BasicTextField` para el estilo del Figma

## Al terminar
```
git add .
git commit -m "feat(miguel): cuestionario personal y tabs alimentacion/fisica/salud con Firebase"
git push origin feature/miguel
```
Avísale a Justin para que haga el merge.
