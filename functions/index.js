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
 *
 * Variables opcionales:
 *   twilio.whatsapp_from = "whatsapp:+14155238886" (sender habilitado para WhatsApp)
 */

const functions = require("firebase-functions");
const admin = require("firebase-admin");
const twilio    = require("twilio");
const admin     = require("firebase-admin");
const crypto    = require("crypto");

admin.initializeApp();

admin.initializeApp();

function normalizePhone(input) {
  const value = String(input || "").trim();
  if (!value) return "";
  if (value.startsWith("+")) return value;
  const digits = value.replace(/\D/g, "");
  if (!digits) return "";
  return `+${digits}`;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function buildContactList({ contacts, toPhone }) {
  const incoming = Array.isArray(contacts) ? contacts : [];
  const normalized = incoming
    .map((c, idx) => ({
      name: c && c.name ? String(c.name).trim() : `Contacto ${idx + 1}`,
      phone: normalizePhone(c && c.phone),
    }))
    .filter((c) => c.phone);

  const fallback = normalizePhone(toPhone);
  if (fallback && !normalized.some((c) => c.phone === fallback)) {
    normalized.unshift({ name: "Contacto principal", phone: fallback });
  }

  const seen = new Set();
  return normalized.filter((c) => {
    if (seen.has(c.phone)) return false;
    seen.add(c.phone);
    return true;
  });
}

async function callWithFallback({ client, fromNumber, twiml, contactList, retryWaitSecs }) {
  const pollEveryMs = 3_000;
  const waitMs = Math.max(8, Number(retryWaitSecs) || 18) * 1_000;
  const attempts = [];

  for (const contact of contactList) {
    const call = await client.calls.create({
      twiml,
      to: contact.phone,
      from: fromNumber,
    });

    const startedAt = Date.now();
    let finalStatus = call.status || "queued";
    let answered = false;

    while (Date.now() - startedAt < waitMs) {
      await sleep(pollEveryMs);
      const refreshed = await client.calls(call.sid).fetch();
      finalStatus = refreshed.status || finalStatus;

      if (finalStatus === "in-progress" || finalStatus === "completed") {
        answered = true;
        break;
      }

      if (["busy", "failed", "no-answer", "canceled"].includes(finalStatus)) {
        break;
      }
    }

    if (!answered && ["queued", "ringing", "initiated"].includes(finalStatus)) {
      await client.calls(call.sid)
        .update({ status: "completed" })
        .catch(() => null);
      finalStatus = "timeout";
    }

    attempts.push({
      name: contact.name,
      phone: contact.phone,
      sid: call.sid,
      status: finalStatus,
      answered,
    });

    if (answered) {
      return { answered: true, attempts, answeredBy: contact.phone };
    }
  }

  return { answered: false, attempts, answeredBy: null };
}

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
    webUrl,
    lat          = 0,
    lng          = 0,
    toPhone,
    contacts,
    retryWaitSecs = 18,
  } = req.body;

  // Leer configuración de Twilio
  const cfg           = functions.config().twilio;
  const accountSid    = cfg.account_sid;
  const authToken     = cfg.auth_token;
  const fromNumber    = cfg.from_number;
  const whatsappFrom  = cfg.whatsapp_from || `whatsapp:${fromNumber}`;
  const client        = twilio(accountSid, authToken);
  const contactList   = buildContactList({ contacts, toPhone });

  if (!accountSid || !authToken || !fromNumber) {
    return res.status(500).json({ error: "Configuración Twilio incompleta" });
  }

  if (!contactList.length) {
    return res.status(400).json({ error: "No hay contactos de emergencia válidos" });
  }

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

  const emergencyUrl = webUrl || `https://vitalsenseai-1cb9f.web.app/emergency.html?t=${tokenId}`;
  const textMessage = [
    "Alerta VitalSense BioMetric.",
    `${patientName} presenta ${anomalyType}.`,
    `FC: ${heartRate} BPM.`,
    `PIN de acceso: ${pin}.`,
    `Abrir ficha de emergencia: ${emergencyUrl}`,
  ].join(" ");

  try {
    const channels = {
      call: null,
      sms: null,
      whatsapp: null,
    };

    // 1) Llamada de voz (comportamiento existente)
    const voiceResult = await callWithFallback({
      client,
      fromNumber,
      twiml,
      contactList,
      retryWaitSecs,
    });
    channels.call = voiceResult;

    // 2) SMS con PIN + URL a todos los contactos
    channels.sms = await Promise.all(
      contactList.map((contact) =>
        client.messages.create({
          body: textMessage,
          to: contact.phone,
          from: fromNumber,
        }).then((msg) => ({
          sid: msg.sid,
          to: contact.phone,
          status: msg.status,
        }))
      )
    );

    // 3) WhatsApp con PIN + URL a todos los contactos (si el sender está habilitado)
    channels.whatsapp = await Promise.all(
      contactList.map(async (contact) => {
        try {
          const wa = await client.messages.create({
            body: textMessage,
            to: `whatsapp:${contact.phone}`,
            from: whatsappFrom,
          });
          return { sid: wa.sid, to: contact.phone, status: wa.status };
        } catch (waErr) {
          console.warn(`[EmergencyCall] WhatsApp no enviado a ${contact.phone}: ${waErr.message}`);
          return { sid: null, to: contact.phone, status: "failed", error: waErr.message };
        }
      })
    );

    console.log(
      `[EmergencyCall] tokenId=${tokenId} answered=${channels.call.answered} answeredBy=${channels.call.answeredBy || "none"} sms=${channels.sms.length} wa=${channels.whatsapp.length}`
    );

    return res.status(200).json({
      success: true,
      channels: {
        call: channels.call,
        sms: channels.sms,
        whatsapp: channels.whatsapp,
      },
    });
  } catch (err) {
    console.error("[EmergencyCall] Twilio error:", err.message);
    return res.status(500).json({ error: err.message });
  }
});

exports.notifySosCreated = functions.database
  .ref("/alerts/{uid}/{sosId}")
  .onCreate(async (snapshot, context) => {
    const uid = context.params.uid;
    const sosId = context.params.sosId;
    const alert = snapshot.val() || {};

    if (alert.type !== "SOS") {
      return null;
    }

    const tokenSnap = await admin.database().ref(`patients/${uid}/deviceToken`).get();
    const deviceToken = tokenSnap.val();
    if (!deviceToken) {
      console.log(`[notifySosCreated] No deviceToken for uid=${uid}`);
      return null;
    }

    const title = "¡EMERGENCIA SOS!";
    const body = alert.lat && alert.lng
      ? `Se activó una alerta SOS con ubicación disponible.`
      : `Se activó una alerta SOS desde el reloj.`;

    await admin.messaging().send({
      token: deviceToken,
      notification: {
        title,
        body,
      },
      data: {
        alertId: sosId,
        lat: String(alert.lat || 0),
        lng: String(alert.lng || 0),
        title,
        body,
        open_notifications: "true",
      },
      android: {
        priority: "high",
        notification: {
          channelId: "vital_alerts",
        },
      },
    });

    console.log(`[notifySosCreated] Push sent uid=${uid} sosId=${sosId}`);
    return null;
  });
