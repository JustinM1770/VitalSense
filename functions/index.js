/**
 * Firebase Cloud Function — triggerEmergencyCall
 *
 * Recibe los datos de emergencia desde la app Android y realiza una llamada
 * de voz via Twilio. La IA actúa como operadora y anuncia la alerta con el PIN
 * para que el paramédico pueda acceder al historial clínico del paciente.
 *
 * Deploy:
 *   cd functions
 *   npm install
 *   firebase deploy --only functions
 *
 * Variables de entorno requeridas (firebase functions:config:set):
 *   twilio.account_sid   = "ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
 *   twilio.auth_token    = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
 *   twilio.from_number   = "+1XXXXXXXXXX"   (número Twilio verificado)
 */

const functions = require("firebase-functions");
const twilio    = require("twilio");
const admin     = require("firebase-admin");
const crypto    = require("crypto");

admin.initializeApp();

// ─── Handler principal ────────────────────────────────────────────────────────

// ─── verifyEmergencyPin ───────────────────────────────────────────────────────
// Verifica el PIN del paramédico server-side y devuelve el perfil médico.
// Los datos médicos NUNCA salen de Firebase sin pasar por aquí.
//
// POST body: { tokenId: string, pin: string }
// Response:  { ok: true, profile: {...} }  |  { ok: false, reason: string }

exports.verifyEmergencyPin = functions.https.onRequest(async (req, res) => {
  // CORS — permite acceso desde la web de emergencia
  res.set("Access-Control-Allow-Origin", "*");
  res.set("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.set("Access-Control-Allow-Headers", "Content-Type");
  if (req.method === "OPTIONS") return res.status(204).send("");
  if (req.method !== "POST")   return res.status(405).json({ ok: false, reason: "method_not_allowed" });

  const { tokenId, pin } = req.body;
  if (!tokenId || !pin) return res.status(400).json({ ok: false, reason: "missing_params" });
  if (!/^\d{4}$/.test(pin)) return res.status(400).json({ ok: false, reason: "invalid_pin_format" });

  const db  = admin.database();
  const ref = db.ref(`emergency_tokens/${tokenId}`);

  try {
    const snap = await ref.once("value");
    if (!snap.exists()) return res.status(404).json({ ok: false, reason: "not_found" });

    const data = snap.val();

    // Validar estado del token
    if (!data.active)               return res.json({ ok: false, reason: "revoked" });
    if (Date.now() > data.expiresAt) return res.json({ ok: false, reason: "expired" });

    // Verificar PIN con hash SHA-256(pin + tokenId) como sal
    const expectedHash = crypto
      .createHash("sha256")
      .update(pin + tokenId)
      .digest("hex");

    if (data.pinHash !== expectedHash) {
      // Registrar intento fallido (sin exponer info sensible)
      console.warn(`[PIN] intento fallido tokenId=${tokenId}`);
      return res.json({ ok: false, reason: "wrong_pin" });
    }

    // PIN correcto — devolver perfil médico (sin pinHash)
    const { pinHash: _removed, ...profile } = data;
    console.log(`[PIN] acceso concedido tokenId=${tokenId}`);
    return res.json({ ok: true, profile });

  } catch (err) {
    console.error("[verifyEmergencyPin]", err.message);
    return res.status(500).json({ ok: false, reason: "server_error" });
  }
});

// ─── triggerEmergencyCall ─────────────────────────────────────────────────────

exports.triggerEmergencyCall = functions.https.onRequest(async (req, res) => {
  // Solo POST
  if (req.method !== "POST") {
    return res.status(405).json({ error: "Method Not Allowed" });
  }

  const {
    tokenId,
    patientName  = "el paciente",
    anomalyType  = "anomalía crítica",
    heartRate    = 0,
    pin          = "0000",
    lat          = 0,
    lng          = 0,
    toPhone,
  } = req.body;

  if (!toPhone) {
    return res.status(400).json({ error: "toPhone es requerido" });
  }

  // Leer configuración de Twilio
  const cfg           = functions.config().twilio;
  const accountSid    = cfg.account_sid;
  const authToken     = cfg.auth_token;
  const fromNumber    = cfg.from_number;
  const client        = twilio(accountSid, authToken);

  // Formatear PIN para texto a voz: "2324" → "2, 3, 2, 4"
  const pinSpoken = pin.split("").join(", ");

  // Ubicación legible
  const locationText =
    lat !== 0 && lng !== 0
      ? `Coordenadas GPS: latitud ${lat.toFixed(4)}, longitud ${lng.toFixed(4)}.`
      : "Ubicación GPS no disponible.";

  // Mensaje TwiML — se repite 3 veces con pausa de 2 s entre repeticiones
  const message = [
    `Alerta de BioMetric.`,
    `El usuario ${patientName} presenta una anomalía de ${anomalyType}.`,
    `Frecuencia cardíaca: ${heartRate} latidos por minuto.`,
    locationText,
    `Para acceder a su historial clínico en el dispositivo,`,
    `use el PIN: ${pinSpoken}.`,
  ].join(" ");

  const twiml = `<?xml version="1.0" encoding="UTF-8"?>
<Response>
  <Repeat count="3">
    <Say language="es-MX" voice="Polly.Mia">${message}</Say>
    <Pause length="2"/>
  </Repeat>
</Response>`;

  try {
    const call = await client.calls.create({
      twiml,
      to:   toPhone,
      from: fromNumber,
    });

    console.log(`[EmergencyCall] tokenId=${tokenId} callSid=${call.sid} to=${toPhone}`);
    return res.status(200).json({ success: true, callSid: call.sid });
  } catch (err) {
    console.error("[EmergencyCall] Twilio error:", err.message);
    return res.status(500).json({ error: err.message });
  }
});
