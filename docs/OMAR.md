# VitalSense — Instrucciones de Omar
## Deadline: Viernes 20 de Marzo, 11:59 PM

---

## Tu rama de trabajo
```
git checkout -b feature/omar
git push -u origin feature/omar
```

---

## Tu trabajo: conectar 3 pantallas a Firebase

Estas pantallas ya tienen UI hecha. Tu tarea es hacer que **guarden y lean datos reales** de Firebase.

---

### 1. Editar Perfil — `ui/profile/ProfileScreen.kt`

**Ver:** `editar_perfil.png` del zip

**Qué hace esta pantalla:**
- El tutor edita su perfil personal
- Al presionar "Guardar" → escribe en Firebase
- Al abrir → lee de Firebase y llena los campos

**Firebase — nodo a usar:**
```
users/{userId}/profile/
  nombre, apellidos, email, nacimiento,
  celular, genero, frecuenciaPromedio, edad
```

**Código para guardar:**
```kotlin
val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
val db = FirebaseDatabase.getInstance()
val profileRef = db.getReference("users/$userId/profile")

val data = mapOf(
    "nombre" to nombre,
    "apellidos" to apellidos,
    "email" to email,
    "nacimiento" to nacimiento,
    "celular" to celular,
    "genero" to genero,
    "frecuenciaPromedio" to frecuenciaPromedio.toIntOrNull(),
    "edad" to edad.toIntOrNull()
)
profileRef.setValue(data)
```

**Código para leer al abrir:**
```kotlin
profileRef.get().addOnSuccessListener { snapshot ->
    nombre = snapshot.child("nombre").getValue(String::class.java) ?: ""
    apellidos = snapshot.child("apellidos").getValue(String::class.java) ?: ""
    // ... etc
}
```

**Diseño (referencia figma editar_perfil.png):**
- Fondo superior azul claro con círculo azul grande (foto de perfil placeholder)
- Ícono de lápiz en el círculo (editar foto — solo UI por ahora)
- "Editar datos personales" título
- Campos en 2 columnas: Nombre | Apellidos, Nacimiento | Celular
- Campos full width: Email, Contraseña, Genero
- Frecuencia promedio (con ❤️) | Edad — 2 columnas
- 2 botones al final: "Guardar" (azul) y "Datos Importantes" (azul, navega a DatosImportantes)

---

### 2. Datos Importantes / Archivos — `ui/archivos/DatosImportantesScreen.kt`

**Ver:** `archivos.png` del zip

**Qué hace esta pantalla:**
- Muestra CURP.pdf e INE.pdf del usuario
- Muestra un QR con los datos del usuario
- Botón "Guardar" → guarda URLs en Firebase

**Firebase — nodo a usar:**
```
users/{userId}/documentos/
  curpUrl    → String (URL o nombre del archivo)
  ineUrl     → String (URL o nombre del archivo)
```

**Para el QR** — usar la librería ZXing que ya está disponible, o generar con datos básicos:
```kotlin
// El QR debe contener los datos de emergencia del usuario:
// "Nombre: X | Sangre: O+ | Alergias: Penicilina | Tel: +52..."
// Leer de users/{userId}/datosMedicos/ para armar el string del QR
```

**Por ahora los PDFs son solo nombres (sin subir archivos reales):**
- Mostrar un TextField con "Nombre" del documento
- Ícono de PDF rojo al lado
- Botón "Guardar" guarda los nombres en Firebase

---

### 3. Documentos Cliente — `ui/documentos/DocumentosScreen.kt`

**Ver:** `documentos.png` del zip

**Qué hace esta pantalla:**
- Es la vista de **solo lectura** del perfil médico del adulto mayor
- El tutor la ve para consultar en emergencias

**Firebase — nodo a leer:**
```
users/{userId}/datosMedicos/
  tipoSangre, alergias, telefonoEmergencia

users/{userId}/profile/
  nombre, edad (o nacimiento para calcular)

users/{userId}/documentos/
  curpUrl, ineUrl
```

**Diseño (referencia documentos.png):**
- Fondo degradado azul claro
- Avatar círculo azul con inicial + nombre + edad
- Card "❤️ Tipo de sangre" → valor (ej. O+)
- Card "🌿 Alergias" → fondo rosa claro, valor (ej. Penicilina)
- "Teléfono de contacto" → ícono teléfono + número
- "Documentos personales:" → lista con CURP.pdf e INE.pdf
- Banner amarillo al fondo: "⚠️ Esta información es confidencial y solo debe usarse en emergencias médicas"

**Para obtener el userId del adulto monitoreado:**
Por ahora usar el `currentUser?.uid` del tutor (en el concurso el tutor es también el paciente demo).

---

## Patrón general para todos tus archivos

```kotlin
// 1. Obtener referencia
val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
val ref = FirebaseDatabase.getInstance().getReference("users/$userId/tuNodo")

// 2. Leer con LaunchedEffect al entrar a la pantalla
LaunchedEffect(Unit) {
    ref.get().addOnSuccessListener { snapshot ->
        // llenar variables de estado
    }
}

// 3. Guardar al presionar botón
fun guardar() {
    ref.setValue(mapOf("campo" to valor))
        .addOnSuccessListener { /* mostrar "Guardado" */ }
        .addOnFailureListener { /* mostrar error */ }
}
```

---

## Puntos importantes

- Usa `Manrope` para toda la tipografía
- El ícono de lápiz para editar foto es solo visual — sin funcionalidad real
- Para el QR puedes usar `androidx.compose.ui.graphics.asImageBitmap()` con ZXing, o simplemente mostrar un placeholder de QR por ahora
- **No modifiques** `AppNavigation.kt`, `Color.kt` ni `Theme.kt`
- Si tienes dudas sobre los nodos Firebase, revisa `docs/EQUIPO.md`

## Al terminar
```
git add .
git commit -m "feat(omar): perfil, archivos y documentos conectados a Firebase"
git push origin feature/omar
```
Avísale a Justin para que haga el merge.
