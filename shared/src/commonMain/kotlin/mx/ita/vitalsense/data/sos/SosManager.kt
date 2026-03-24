package mx.ita.vitalsense.data.sos

/**
 * Gestiona el envío y escucha de alertas SOS.
 * Implementación platform-specific: Android usa Firebase directamente,
 * iOS delega al código Swift nativo.
 */
expect class SosManager constructor() {
    fun enviarSOS(userId: String, patientName: String)
    fun escucharSOS(userId: String, onAlerta: (String) -> Unit)
}
