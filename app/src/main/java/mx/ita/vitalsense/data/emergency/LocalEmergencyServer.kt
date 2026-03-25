package mx.ita.vitalsense.data.emergency

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.net.NetworkInterface

/**
 * Servidor HTTP local que sirve el perfil médico de emergencia directamente
 * desde el teléfono del paciente — sin internet, sin Firebase, sin app instalada.
 *
 * El socorrista escanea el QR, su navegador abre:
 *   http://{IP_LOCAL}:8080/emergency/{pin}
 * Si el PIN es correcto, ve los datos médicos en HTML.
 *
 * Funciona via WiFi local o hotspot del teléfono del paciente.
 */
class LocalEmergencyServer(
    private val context: Context,
    port: Int = PORT,
) : NanoHTTPD(port) {

    /** Datos a servir — se setean antes de iniciar el servidor. */
    var emergencyData: LocalEmergencyData? = null

    override fun serve(session: IHTTPSession): Response {
        val data = emergencyData ?: return newFixedLengthResponse(
            Response.Status.SERVICE_UNAVAILABLE, MIME_HTML,
            errorPage("Servidor no inicializado")
        )

        val uri = session.uri
        return when {
            uri.startsWith("/emergency/") -> {
                val enteredPin = uri.removePrefix("/emergency/").trim()
                if (enteredPin == data.pin) {
                    newFixedLengthResponse(Response.Status.OK, MIME_HTML, buildProfilePage(data))
                } else {
                    newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_HTML, pinErrorPage())
                }
            }
            uri == "/" || uri == "/emergency" -> {
                newFixedLengthResponse(Response.Status.OK, MIME_HTML, pinEntryPage())
            }
            uri == "/sos" -> {
                newFixedLengthResponse(Response.Status.OK, MIME_HTML, buildSosPage(data))
            }
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_HTML, errorPage("Página no encontrada"))
        }
    }

    // ─── Páginas HTML ─────────────────────────────────────────────────────────

    private fun pinEntryPage(): String = """
        <!DOCTYPE html><html lang="es"><head>
        <meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>VitalSense — Acceso de Emergencia</title>
        <style>
          body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:#B71C1C;
               display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0}
          .card{background:#fff;border-radius:20px;padding:32px;max-width:360px;width:90%;text-align:center;box-shadow:0 8px 32px rgba(0,0,0,0.3)}
          h1{color:#B71C1C;font-size:22px;margin-bottom:4px}
          p{color:#757575;font-size:13px;margin-bottom:24px}
          input{width:100%;box-sizing:border-box;font-size:32px;letter-spacing:12px;text-align:center;
                border:2px solid #E0E0E0;border-radius:12px;padding:14px;outline:none;font-weight:bold}
          input:focus{border-color:#B71C1C}
          button{width:100%;background:#B71C1C;color:#fff;border:none;border-radius:12px;
                 padding:16px;font-size:16px;font-weight:700;margin-top:16px;cursor:pointer}
          button:hover{background:#D32F2F}
          .logo{font-size:13px;color:#B71C1C;font-weight:700;margin-bottom:16px}
        </style></head><body>
        <div class="card">
          <div class="logo">● VitalSense BioMetric</div>
          <h1>Acceso de Emergencia</h1>
          <p>Ingresa el PIN de 4 dígitos anunciado por la llamada de emergencia</p>
          <form action="" method="get" id="f">
            <input type="number" name="pin" maxlength="4" placeholder="0000" autofocus
                   oninput="if(this.value.length>4)this.value=this.value.slice(0,4)">
            <button type="submit">Verificar PIN</button>
          </form>
        </div>
        <script>
          document.getElementById('f').addEventListener('submit',function(e){
            e.preventDefault();
            var pin=document.querySelector('input').value;
            if(pin.length===4) window.location.href='/emergency/'+pin;
          });
        </script>
        </body></html>
    """.trimIndent()

    private fun pinErrorPage(): String = """
        <!DOCTYPE html><html lang="es"><head>
        <meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>PIN Incorrecto</title>
        <style>
          body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:#B71C1C;
               display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0}
          .card{background:#fff;border-radius:20px;padding:32px;max-width:360px;width:90%;text-align:center}
          h1{color:#B71C1C}p{color:#757575;font-size:13px}
          a{display:block;background:#B71C1C;color:#fff;border-radius:12px;padding:14px;
            text-decoration:none;font-weight:700;margin-top:20px}
        </style></head><body>
        <div class="card">
          <h1>PIN Incorrecto</h1>
          <p>El PIN ingresado no es válido. Verifica la llamada de emergencia.</p>
          <a href="/">Intentar de nuevo</a>
        </div></body></html>
    """.trimIndent()

    private fun buildProfilePage(data: LocalEmergencyData): String {
        val rows = buildString {
            fun row(label: String, value: String, highlight: Boolean = false) {
                if (value.isBlank()) return
                val bg    = if (highlight) "#FFF8F8" else "#FAFAFA"
                val color = if (highlight) "#B71C1C" else "#333"
                append("""
                    <div style="background:$bg;border-radius:12px;padding:14px 16px;margin-bottom:10px;
                                ${if (highlight) "border-left:4px solid #B71C1C" else ""}">
                      <div style="font-size:11px;color:#999;text-transform:uppercase;letter-spacing:.5px">$label</div>
                      <div style="font-size:15px;color:$color;font-weight:600;margin-top:4px">$value</div>
                    </div>
                """.trimIndent())
            }
            if (data.tipoSangre.isNotBlank()) {
                append("""
                    <div style="text-align:center;margin-bottom:16px">
                      <span style="background:#B71C1C;color:#fff;font-size:28px;font-weight:900;
                                   padding:8px 24px;border-radius:30px">${data.tipoSangre}</span>
                    </div>
                """.trimIndent())
            }
            row("⚠ Alergias", data.alergias, highlight = true)
            row("Padecimientos", data.padecimientos)
            row("Medicamentos actuales", data.medicamentos)
            row("Anomalía detectada", "${data.anomalyType} · ${data.heartRate} BPM")
            row("Contacto de emergencia", data.contactoEmergencia)
            row("Teléfono emergencia", data.telefonoEmergencia)
        }

        return """
            <!DOCTYPE html><html lang="es"><head>
            <meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>${data.nombre} — Perfil Médico</title>
            <style>
              *{box-sizing:border-box}
              body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;
                   background:#F5F5F5;margin:0;padding:0}
              .header{background:#B71C1C;color:#fff;padding:24px 20px 32px;text-align:center}
              .avatar{width:64px;height:64px;background:rgba(255,255,255,.2);border-radius:50%;
                      display:inline-flex;align-items:center;justify-content:center;
                      font-size:26px;font-weight:700;margin-bottom:12px}
              .name{font-size:20px;font-weight:700;margin:0}
              .badge{display:inline-block;background:rgba(255,255,255,.15);border-radius:20px;
                     padding:4px 14px;font-size:12px;margin-top:6px}
              .content{padding:16px 16px 32px;max-width:480px;margin:0 auto}
              .logo{font-size:11px;text-align:center;color:#B71C1C;font-weight:700;
                    margin-bottom:16px;padding-top:8px}
              .call-btn{display:block;background:#2E7D32;color:#fff;border-radius:12px;
                        padding:14px;text-align:center;text-decoration:none;font-weight:700;
                        font-size:15px;margin-top:4px}
            </style></head><body>
            <div class="header">
              <div class="avatar">${data.initials}</div>
              <p class="name">${data.nombre} ${data.apellidos}</p>
              <span class="badge">⚡ ${data.anomalyType}</span>
            </div>
            <div class="content">
              <div class="logo">● VitalSense BioMetric — Acceso de Emergencia</div>
              $rows
              ${if (data.telefonoEmergencia.isNotBlank())
                  """<a class="call-btn" href="tel:${data.telefonoEmergencia}">
                     📞 Llamar a contacto: ${data.telefonoEmergencia}</a>""" else ""}
            </div></body></html>
        """.trimIndent()
    }

    private fun buildSosPage(data: LocalEmergencyData): String = """
        <!DOCTYPE html><html lang="es"><head>
        <meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>SOS — ${data.nombre}</title>
        <style>
          body{font-family:-apple-system,sans-serif;background:#B71C1C;
               display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0}
          .card{background:#fff;border-radius:20px;padding:24px;max-width:360px;width:90%}
          h1{color:#B71C1C;margin:0 0 4px}
          .name{font-size:18px;font-weight:700;margin:8px 0}
          a{display:block;background:#1565C0;color:#fff;border-radius:12px;padding:14px;
            text-align:center;text-decoration:none;font-weight:700;margin-top:16px}
        </style></head><body>
        <div class="card">
          <h1>🆘 Alerta SOS</h1>
          <div class="name">${data.nombre} ${data.apellidos}</div>
          <p style="color:#757575;font-size:13px">Tipo de sangre: <strong>${data.tipoSangre}</strong></p>
          ${if (data.lat != 0.0 && data.lng != 0.0)
              """<a href="https://maps.google.com?q=${data.lat},${data.lng}">
                 📍 Ver ubicación en Maps</a>""" else ""}
        </div></body></html>
    """.trimIndent()

    private fun errorPage(msg: String): String =
        "<html><body style='font-family:sans-serif;padding:32px'><h2>$msg</h2></body></html>"

    // ─── Utilidad IP local ────────────────────────────────────────────────────

    companion object {
        const val PORT = 8080

        /**
         * Obtiene la IP local del dispositivo en la red WiFi activa.
         * Fallback: itera todas las interfaces de red.
         */
        fun getLocalIpAddress(context: Context): String? {
            // Intento 1: WifiManager (solo IPv4)
            try {
                val wifiMgr = context.applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as WifiManager
                val ip = wifiMgr.connectionInfo?.ipAddress ?: 0
                if (ip != 0) {
                    return String.format(
                        "%d.%d.%d.%d",
                        ip and 0xff,
                        ip shr 8 and 0xff,
                        ip shr 16 and 0xff,
                        ip shr 24 and 0xff,
                    )
                }
            } catch (_: Exception) {}

            // Intento 2: NetworkInterface (cubre hotspot)
            try {
                for (iface in NetworkInterface.getNetworkInterfaces()) {
                    for (addr in iface.inetAddresses) {
                        if (!addr.isLoopbackAddress && addr.hostAddress?.contains(':') == false) {
                            return addr.hostAddress
                        }
                    }
                }
            } catch (_: Exception) {}

            return null
        }
    }
}

/** Datos del paciente que el servidor sirve localmente (sin Firebase). */
data class LocalEmergencyData(
    val nombre: String              = "",
    val apellidos: String           = "",
    val tipoSangre: String          = "",
    val alergias: String            = "",
    val padecimientos: String       = "",
    val medicamentos: String        = "",
    val contactoEmergencia: String  = "",
    val telefonoEmergencia: String  = "",
    val anomalyType: String         = "",
    val heartRate: Int              = 0,
    val pin: String                 = "",
    val lat: Double                 = 0.0,
    val lng: Double                 = 0.0,
) {
    val initials: String get() {
        val n = nombre.firstOrNull()?.uppercaseChar() ?: ""
        val a = apellidos.firstOrNull()?.uppercaseChar() ?: ""
        return "$n$a".ifEmpty { "VS" }
    }
}
