#if os(iOS)
import SwiftUI

struct ChatMessage: Identifiable {
    let id = UUID()
    let text: String
    let isUser: Bool
}

struct ChatBotView: View {
    /// Contexto inicial opcional: si se pasa, se envía automáticamente al aparecer la vista
    var initialContext: String = ""

    @State private var messages: [ChatMessage] = [
        ChatMessage(text: "Hola, soy tu asistente de salud BioMetric AI. ¿En qué puedo ayudarte hoy? Puedo analizar tus signos vitales, explicar predicciones de riesgo o responder preguntas de salud.", isUser: false)
    ]
    @State private var inputText = ""
    @State private var isLoading = false
    @State private var messageCount = 1

    var body: some View {
        ZStack {
            Color.white.ignoresSafeArea()
            VStack(spacing: 0) {
                // Header
                HStack(spacing: 12) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 24, style: .continuous)
                            .fill(Color.primaryBlue)
                            .frame(width: 40, height: 40)
                        Image(systemName: "bubble.left.and.bubble.right.fill")
                            .foregroundColor(.white)
                            .font(.system(size: 16))
                    }
                    Text("Chat Bot AI")
                        .font(.manropeBold(size: 22))
                        .foregroundColor(Color.textPrimary)
                    Spacer()
                }
                .padding(.horizontal, Spacing.xl)
                .padding(.vertical, 16)
                .background(Color.white)
                .vsShadow(.small)

                // Messages
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing: 12) {
                            ForEach(messages) { msg in
                                ChatBubble(message: msg).id(msg.id)
                            }
                            if isLoading {
                                HStack {
                                    ProgressView().scaleEffect(0.8)
                                    Text("Pensando...").font(.caption).foregroundColor(.secondary)
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.leading)
                            }
                        }
                        .padding()
                    }
                    .onChange(of: messageCount) { _ in
                        withAnimation { proxy.scrollTo(messages.last?.id) }
                    }
                }

                // Input bar — padded to clear the floating BiometricBottomNav (~90pt)
                VStack(spacing: 0) {
                    HStack(spacing: 12) {
                        TextField("Escribe tu pregunta...", text: $inputText)
                            .font(.manrope(size: 14))
                            .foregroundColor(Color.textPrimary)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 12)
                            .background(Color.surfaceGray)
                            .cornerRadius(24)
                            .overlay(
                                RoundedRectangle(cornerRadius: 24)
                                    .stroke(Color.borderGray, lineWidth: 1)
                            )
                            .submitLabel(.send)
                            .onSubmit { sendMessage() }

                        Button(action: sendMessage) {
                            Image(systemName: "paperplane.fill")
                                .foregroundColor(.white)
                                .padding(12)
                                .background(inputText.trimmingCharacters(in: .whitespaces).isEmpty || isLoading
                                            ? Color.textHint : Color.primaryBlue)
                                .clipShape(Circle())
                        }
                        .disabled(inputText.trimmingCharacters(in: .whitespaces).isEmpty || isLoading)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)

                    // Spacer so floating nav doesn't cover the input bar
                    Color.clear.frame(height: 80)
                }
                .background(Color.white)
                .shadow(color: .black.opacity(0.04), radius: 4, x: 0, y: -2)
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            guard !initialContext.isEmpty else { return }
            // Auto-enviar el contexto del análisis IA al aparecer
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                inputText = initialContext
                sendMessage()
            }
        }
    }

    private func sendMessage() {
        let text = inputText.trimmingCharacters(in: .whitespaces)
        guard !text.isEmpty else { return }
        messages.append(ChatMessage(text: text, isUser: true))
        messageCount = messages.count
        inputText = ""
        isLoading = true
        Task {
            let response = await callClaudeAPI(prompt: text)
            await MainActor.run {
                messages.append(ChatMessage(text: response, isUser: false))
                messageCount = messages.count
                isLoading = false
            }
        }
    }

    private func callClaudeAPI(prompt: String) async -> String {
        guard let apiKey = Bundle.main.infoDictionary?["GEMINI_API_KEY"] as? String, !apiKey.isEmpty else {
            return "Configura GEMINI_API_KEY en Info.plist para activar el asistente."
        }
        let urlStr = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=\(apiKey)"
        guard let url = URL(string: urlStr) else { return "Error de URL" }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let systemText = "Eres BioMetric AI, asistente médico especializado en análisis de signos vitales y predicción de enfermedades crónicas. Responde en español de forma empática, concisa y profesional. Máximo 100 palabras. Cuando el usuario comparte resultados de análisis IA (FC, SpO2, glucosa, predicciones de riesgo), interprétalos con profundidad clínica. Estructura: hallazgo principal, significado clínico, acción recomendada, cuándo buscar atención urgente. Siempre añade: 'No sustituye consulta médica presencial'."

        // Construir historial de mensajes para Gemini (excluye el primer saludo del bot)
        var contents: [[String: Any]] = []
        for msg in messages.dropFirst() {
            contents.append([
                "role": msg.isUser ? "user" : "model",
                "parts": [["text": msg.text]]
            ])
        }
        contents.append(["role": "user", "parts": [["text": prompt]]])

        let body: [String: Any] = [
            "systemInstruction": ["parts": [["text": systemText]]],
            "contents": contents,
            "generationConfig": ["temperature": 0.7, "maxOutputTokens": 512]
        ]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        do {
            let (data, _) = try await URLSession.shared.data(for: request)
            if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let candidates = json["candidates"] as? [[String: Any]],
               let content = candidates.first?["content"] as? [String: Any],
               let parts = content["parts"] as? [[String: Any]],
               let text = parts.first?["text"] as? String { return text }
        } catch {}
        return "No pude procesar tu consulta. Intenta de nuevo."
    }
}

struct ChatBubble: View {
    let message: ChatMessage
    var body: some View {
        HStack {
            if message.isUser { Spacer(minLength: 60) }
            Text(message.text)
                .font(.manrope(size: 14))
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(message.isUser ? Color.primaryBlue : Color(hex: "#F5F5F5"))
                .foregroundColor(message.isUser ? .white : Color.textPrimary)
                .cornerRadius(16)
                .shadow(color: .black.opacity(0.04), radius: 4, x: 0, y: 1)
            if !message.isUser { Spacer(minLength: 60) }
        }
    }
}

#endif
