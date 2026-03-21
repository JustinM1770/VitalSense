import SwiftUI

struct ChatMessage: Identifiable {
    let id = UUID()
    let text: String
    let isUser: Bool
}

struct ChatBotView: View {
    @State private var messages: [ChatMessage] = [
        ChatMessage(text: "Hola, soy tu asistente de salud VitalSense. ¿En qué puedo ayudarte hoy?", isUser: false)
    ]
    @State private var inputText = ""
    @State private var isLoading = false

    var body: some View {
        ZStack {
            Color(hex: "#F0F2F5").ignoresSafeArea()
            VStack(spacing: 0) {
                // Header
                HStack {
                    Image(systemName: "brain")
                        .foregroundColor(Color(hex: "#1169FF"))
                        .font(.title2)
                    Text("Asistente IA")
                        .font(.system(size: 20, weight: .bold))
                    Spacer()
                }
                .padding()
                .background(Color.white)
                .shadow(color: .black.opacity(0.05), radius: 4, x: 0, y: 2)

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
                    .onChange(of: messages.count) { _ in
                        withAnimation { proxy.scrollTo(messages.last?.id) }
                    }
                }

                // Input bar
                HStack(spacing: 12) {
                    TextField("Escribe tu pregunta...", text: $inputText)
                        .padding(12)
                        .background(Color.white)
                        .cornerRadius(24)
                        .submitLabel(.send)
                        .onSubmit { sendMessage() }

                    Button(action: sendMessage) {
                        Image(systemName: "paperplane.fill")
                            .foregroundColor(.white)
                            .padding(12)
                            .background(inputText.trimmingCharacters(in: .whitespaces).isEmpty || isLoading
                                        ? Color.gray : Color(hex: "#1169FF"))
                            .clipShape(Circle())
                    }
                    .disabled(inputText.trimmingCharacters(in: .whitespaces).isEmpty || isLoading)
                }
                .padding()
                .background(Color(hex: "#F0F2F5"))
            }
        }
        .navigationBarHidden(true)
    }

    private func sendMessage() {
        let text = inputText.trimmingCharacters(in: .whitespaces)
        guard !text.isEmpty else { return }
        messages.append(ChatMessage(text: text, isUser: true))
        inputText = ""
        isLoading = true
        Task {
            let response = await callClaudeAPI(prompt: text)
            await MainActor.run {
                messages.append(ChatMessage(text: response, isUser: false))
                isLoading = false
            }
        }
    }

    private func callClaudeAPI(prompt: String) async -> String {
        guard let apiKey = Bundle.main.infoDictionary?["CLAUDE_API_KEY"] as? String, !apiKey.isEmpty else {
            return "Configura CLAUDE_API_KEY en Info.plist para activar el asistente."
        }
        guard let url = URL(string: "https://api.anthropic.com/v1/messages") else { return "Error de URL" }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue(apiKey, forHTTPHeaderField: "x-api-key")
        request.setValue("2023-06-01", forHTTPHeaderField: "anthropic-version")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let body: [String: Any] = [
            "model": "claude-haiku-4-5-20251001",
            "max_tokens": 512,
            "system": "Eres un asistente médico de telemedicina VitalSense. Responde en español, de forma empática y concisa.",
            "messages": [["role": "user", "content": prompt]]
        ]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        do {
            let (data, _) = try await URLSession.shared.data(for: request)
            if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let content = (json["content"] as? [[String: Any]])?.first,
               let text = content["text"] as? String { return text }
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
                .font(.system(size: 14))
                .padding(12)
                .background(message.isUser ? Color(hex: "#1169FF") : Color.white)
                .foregroundColor(message.isUser ? .white : .primary)
                .cornerRadius(16)
                .shadow(color: .black.opacity(0.05), radius: 4, x: 0, y: 1)
            if !message.isUser { Spacer(minLength: 60) }
        }
    }
}
