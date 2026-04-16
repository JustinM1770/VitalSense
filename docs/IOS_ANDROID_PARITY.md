# Paridad iOS vs Android (BioMetric AI)

Fecha: 2026-04-06

## Objetivo
Verificar que Android tenga al menos las mismas funciones y flujos que iOS.

## Resultado ejecutivo
Android ya cubre todas las funciones existentes en iOS y ademas incluye pantallas/funciones extra.
No se detectaron funciones presentes en iOS que falten en Android.

## iOS (base revisada)
- Navegacion principal: Splash, AuthFlow, TabView (Inicio, Wearable, IA, Perfil)
- Dashboard basico con lista de pacientes y metricas
- Wearable con conexion
- Chat IA basico
- Perfil

## Android (estado actual)
- Incluye todo lo de iOS
- Incluye extras no presentes en iOS:
  - Reportes (diario/detallado/sueno)
  - Notificaciones avanzadas
  - Medicamentos
  - Emergencias QR/SOS
  - Integraciones de datos de salud adicionales
  - Pantallas de documentos

## Nota importante sobre "exactamente igual interfaz"
Aunque se puede aproximar visualmente, "exactamente igual" entre iOS y Android no suele ser lo ideal por guias nativas de plataforma.
Si se desea, se puede crear un tema de paridad visual para Android (tipografias, espaciados, colores, jerarquia visual) tomando iOS como referencia.

## Conclusiones
1. No hay funciones de iOS faltantes en Android.
2. Android no requiere agregar funciones para igualar iOS.
3. Si el objetivo ahora es igualar estilo visual, se recomienda hacerlo por fases (Dashboard, Wearable, Chat, Profile).
