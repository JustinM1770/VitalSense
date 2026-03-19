# VitalSense — Instrucciones de Axhel
## Deadline: Viernes 20 de Marzo, 11:59 PM

---

## Tu rama de trabajo
```
git checkout -b feature/axhel
git push -u origin feature/axhel
```

---

## Tus 4 pantallas (rediseño fiel al Figma)

### 1. Login — `ui/login/LoginScreen.kt` (ya existe, rediseñar)

**Ver:** `login.png` del zip

Cambios que hay que hacer:
- Fondo blanco con gradiente muy sutil arriba
- Campo Email: fondo `#F0F2F5`, ícono sobre, sin borde outlined — usar `Box` + `BasicTextField`
- Campo Contraseña: mismo estilo, ícono candado, ojo para mostrar/ocultar
- "Olvidaste tu contraseña?" — texto azul `#1169FF` alineado a la derecha
- Botón "Iniciar Sesión" — azul `#1169FF`, ancho completo, radius 32dp, texto blanco
- Separador "O" entre botón y social
- Botón Google — borde gris, fondo blanco, logo G + texto "Inicia Sesion con Google"
- Botón Facebook — mismo estilo, ícono F azul oscuro
- "No tienes cuenta? **Regístrate**" — link azul al final

```kotlin
// Estructura de campo de texto (referencia):
Box(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(Color(0xFFF0F2F5))
        .padding(horizontal = 16.dp, vertical = 14.dp)
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(/* ícono */, tint = Color(0xFFB0B8C4))
        Spacer(Modifier.width(12.dp))
        // BasicTextField o Text placeholder
    }
}
```

---

### 2. Register — `ui/register/RegisterScreen.kt` (ya existe, rediseñar)

**Ver:** `registro.png` del zip

- Mismo estilo de campos que Login
- Campos: Nombre, Email, Contraseña (con ojo)
- Checkbox "Acepto los **Términos de Servicio** y **Políticas de Privacidad**" — links azules
- Botón "Regístrate" — mismo estilo que "Iniciar Sesión"
- "Ya tienes cuenta? **Inicia Sesión**" al final
- **Sin** botón de Google aquí (solo en Login)

---

### 3. Dashboard (Menú Principal) — `ui/dashboard/DashboardScreen.kt`

**Ver:** `menu_principal.png` del zip

El header y search bar ya los agregó Justin. Lo que te falta:

- Verificar que el fondo sea `DashBg` (`#BDD9F2`) correcto
- La sección "Esta semana →" debe tener el arrow clickeable que lleva al Reporte
- La tarjeta de Sueño: círculo verde `#00C48C` con porcentaje, texto "Sueño", fecha, "+10%"
- La gráfica de HR debe mostrar el tooltip "Heart Rate ❤️ 118" en el punto activo (viernes)
- La sección Medicamentos debe tener el `DateStrip` con "Today, 14 Feb" resaltado en azul claro
- El bottom nav ya está implementado — solo verifica que `HOME` esté activo

---

### 4. Notificaciones — `ui/notifications/NotificacionesScreen.kt` (ya existe, conectar datos)

**Ver:** `notificacion.png` del zip

Actualmente tiene datos mock. Hay que leer de Firebase:

```kotlin
// Nodo a leer:
// users/{userId}/notificaciones/{notifId}/
//   titulo, descripcion, tipo, timestamp, leida

// Agrupar por: Hoy / Ayer / fecha anterior
```

Estructura visual:
- Header: "← Notificaciones" + chip "News ●" arriba derecha
- Chip de fecha: "Hoy", "Ayer", "15 Abril" — fondo azul claro redondeado
- "Mark all" — texto azul derecha
- Cada item: círculo azul con ícono + título bold + descripción + tiempo (2M, 2H, etc.)
- Item seleccionado/no leído: fondo azul muy claro `#EBF2FF`
- Bottom nav con `CHAT` activo (ícono de burbuja)

Para obtener el userId:
```kotlin
val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
val ref = FirebaseDatabase.getInstance().getReference("users/$userId/notificaciones")
```

---

## Puntos importantes

- Usa `Manrope` para toda la tipografía (ya está importado en `Type.kt`)
- Los botones sociales (Google/Facebook) en Login son solo UI — la lógica ya está en `LoginViewModel`
- Para Facebook: por ahora solo UI, sin lógica real (no hay tiempo)
- Prueba en el emulador que los campos de texto no se tapen con el teclado (`imePadding()`)
- **No modifiques** `AppNavigation.kt` — si necesitas una ruta nueva, avísale a Justin

## Al terminar
```
git add .
git commit -m "feat(axhel): login, register, dashboard y notificaciones rediseño Figma"
git push origin feature/axhel
```
Avísale a Justin para que haga el merge.
