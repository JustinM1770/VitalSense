#if os(iOS)
import SwiftUI
import Combine
import FirebaseAuth
import FirebaseDatabase

// MARK: - Model
struct NotifItem: Identifiable {
    let id: String
    let icon: String
    let title: String
    let body: String
    let time: String
    let timestamp: TimeInterval
    let isHighlighted: Bool
    let lat: Double
    let lng: Double
}

// MARK: - ViewModel
class NotificationsViewModel: ObservableObject {
    @Published var items: [NotifItem] = []
    @Published var isLoading = true
    private var ref: DatabaseReference?

    func load() {
        guard let uid = Auth.auth().currentUser?.uid else { isLoading = false; return }
        ref = Database.database().reference().child("users/\(uid)/notificaciones")
        ref?.queryOrdered(byChild: "timestamp").queryLimited(toLast: 50)
            .observe(.value) { [weak self] snap in
                guard let self else { return }
                var result: [NotifItem] = []
                for child in snap.children.reversed() {
                    let s = child as! DataSnapshot
                    let d = s.value as? [String: Any] ?? [:]
                    let ts = (d["timestamp"] as? TimeInterval ?? 0) / 1000
                    let tipo = d["tipo"] as? String ?? ""
                    let icon: String
                    switch tipo {
                    case "alerta": icon = "exclamationmark.triangle.fill"
                    case "sos":    icon = "sos.circle.fill"
                    case "med":    icon = "pill.fill"
                    default:       icon = "heart.fill"
                    }
                    result.append(NotifItem(
                        id: s.key,
                        icon: icon,
                        title: d["titulo"] as? String ?? "Notificación",
                        body:  d["cuerpo"] as? String ?? "",
                        time:  self.relativeTime(ts),
                        timestamp: ts,
                        isHighlighted: tipo == "alerta" || tipo == "sos",
                        lat: d["lat"] as? Double ?? 0,
                        lng: d["lng"] as? Double ?? 0
                    ))
                }
                DispatchQueue.main.async {
                    self.items = result
                    self.isLoading = false
                }
            }
    }

    private func relativeTime(_ ts: TimeInterval) -> String {
        let diff = Date().timeIntervalSince1970 - ts
        let min = Int(diff / 60); let hr = min / 60; let day = hr / 24
        if day > 0 { return "\(day)D" }
        if hr > 0  { return "\(hr)H" }
        if min > 0 { return "\(min)M" }
        return "Ahora"
    }
}

// MARK: - View
struct NotificationsView: View {
    @StateObject private var vm = NotificationsViewModel()

    var body: some View {
        NavigationStack {
            ZStack {
                Color.white.ignoresSafeArea()

                if vm.isLoading {
                    ProgressView("Cargando...")
                } else if vm.items.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "bell.slash")
                            .font(.system(size: 44))
                            .foregroundColor(Color.textHint)
                        Text("Sin notificaciones")
                            .font(.system(size: 16, weight: .medium))
                            .foregroundColor(Color.textSecondary)
                    }
                } else {
                    ScrollView(showsIndicators: false) {
                        LazyVStack(spacing: 0) {
                            ForEach(groupedItems, id: \.0) { label, group in
                                VStack(alignment: .leading, spacing: 0) {
                                    Text(label)
                                        .font(.system(size: 12, weight: .semibold))
                                        .foregroundColor(Color.textSecondary)
                                        .padding(.horizontal, Spacing.xl)
                                        .padding(.top, 20)
                                        .padding(.bottom, 8)

                                    ForEach(group) { item in
                                        NotifRow(item: item)
                                    }
                                }
                            }
                            Spacer(minLength: 90)
                        }
                    }
                }
            }
            .navigationTitle("Notificaciones")
            .navigationBarTitleDisplayMode(.large)
        }
        .onAppear { vm.load() }
    }

    // Group by Hoy / Ayer / Anterior
    private var groupedItems: [(String, [NotifItem])] {
        let now = Date().timeIntervalSince1970
        let dayStart = Calendar.current.startOfDay(for: Date()).timeIntervalSince1970
        let yesterdayStart = dayStart - 86400

        var hoy: [NotifItem] = []; var ayer: [NotifItem] = []; var ant: [NotifItem] = []
        for item in vm.items {
            if item.timestamp >= dayStart { hoy.append(item) }
            else if item.timestamp >= yesterdayStart { ayer.append(item) }
            else { ant.append(item) }
        }
        var result: [(String, [NotifItem])] = []
        if !hoy.isEmpty  { result.append(("Hoy", hoy)) }
        if !ayer.isEmpty { result.append(("Ayer", ayer)) }
        if !ant.isEmpty  { result.append(("Anterior", ant)) }
        return result
    }
}

// MARK: - Notification Row
struct NotifRow: View {
    let item: NotifItem
    @State private var showMap = false

    private var iconColor: Color {
        item.isHighlighted ? Color.errorRed : Color.primaryBlue
    }
    private var bgColor: Color {
        item.isHighlighted ? Color.warningSoft : Color.white
    }

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            // Icon circle
            ZStack {
                Circle()
                    .fill(iconColor.opacity(0.12))
                    .frame(width: 44, height: 44)
                Image(systemName: item.icon)
                    .font(.system(size: 18))
                    .foregroundColor(iconColor)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(item.title)
                    .font(.manropeSemiBold(size: 14))
                    .foregroundColor(Color.textPrimary)
                Text(item.body)
                    .font(.manrope(size: 13))
                    .foregroundColor(Color.textSecondary)
                    .lineLimit(2)

                if item.lat != 0 || item.lng != 0 {
                    Button(action: {
                        let url = URL(string: "https://maps.apple.com/?q=\(item.lat),\(item.lng)")!
                        UIApplication.shared.open(url)
                    }) {
                        Label("Ver en mapa", systemImage: "map.fill")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(Color.primaryBlue)
                    }
                    .padding(.top, 2)
                }
            }

            Spacer()

            Text(item.time)
                .font(.system(size: 11))
                .foregroundColor(Color.textHint)
        }
        .padding(.horizontal, Spacing.xl)
        .padding(.vertical, 14)
        .background(bgColor)
        .overlay(
            Divider().padding(.leading, 78),
            alignment: .bottom
        )
    }
}

#endif
