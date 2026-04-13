#if os(iOS)
import SwiftUI
import FirebaseDatabase

struct PatientDetailView: View {
    let patient: VitalsDataiOS
    @State private var history: [VitalsSnapshotiOS] = []
    @Environment(\.dismiss) var dismiss

    var body: some View {
        ZStack {
            Color(hex: "#F0F2F5").ignoresSafeArea()
            ScrollView {
                VStack(spacing: 20) {
                    // Vitals grid
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 16) {
                        DetailCard(title: "Frecuencia Cardíaca", value: "\(patient.heartRate)",
                                   unit: "bpm", color: Color(hex: "#FF4560"), icon: "heart.fill")
                        DetailCard(title: "Glucosa", value: "\(Int(patient.glucose))",
                                   unit: "mg/dL", color: .orange, icon: "drop.fill")
                        DetailCard(title: "SpO2", value: "\(patient.spo2)",
                                   unit: "%", color: .green, icon: "lungs.fill")
                        DetailCard(title: "Estado", value: patient.hasAlert ? "Alerta" : "Normal",
                                   unit: "", color: patient.hasAlert ? .orange : .green,
                                   icon: patient.hasAlert ? "exclamationmark.triangle.fill" : "checkmark.circle.fill")
                    }
                    .padding(.horizontal)

                    // History chart
                    if history.count >= 2 {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Historial FC (últimas lecturas)")
                                .font(.system(size: 16, weight: .bold))
                                .padding(.horizontal)
                            HeartRateChartView(snapshots: history)
                                .frame(height: 160)
                                .padding(.horizontal)
                                .background(Color.white)
                                .cornerRadius(16)
                                .padding(.horizontal)
                                .vsShadow(.medium)
                        }
                    }
                }
                .padding(.vertical)
            }
        }
        .navigationTitle(patient.patientName)
        .navigationBarTitleDisplayMode(.large)
        .onAppear { loadHistory() }
    }

    private func loadHistory() {
        Database.database().reference()
            .child("patients/\(patient.patientId)/history")
            .queryLimited(toLast: 10)
            .observeSingleEvent(of: .value) { snap in
                var snaps: [VitalsSnapshotiOS] = []
                for child in snap.children {
                    if let s = child as? DataSnapshot, let d = s.value as? [String: Any] {
                        let glucose = d["glucose"] as? Double ?? Double(d["glucose"] as? Int ?? 0)
                        snaps.append(VitalsSnapshotiOS(
                            heartRate: d["heartRate"] as? Int ?? 0,
                            glucose: glucose,
                            spo2: d["spo2"] as? Int ?? 0,
                            timestamp: d["timestamp"] as? TimeInterval ?? 0
                        ))
                    }
                }
                history = snaps
            }
    }
}

struct DetailCard: View {
    let title: String; let value: String; let unit: String; let color: Color; let icon: String
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: icon).foregroundColor(color)
                Text(title).font(.system(size: 12)).foregroundColor(.secondary)
            }
            Text(value).font(.system(size: 32, weight: .bold)).foregroundColor(color)
            if !unit.isEmpty {
                Text(unit).font(.system(size: 12)).foregroundColor(.secondary)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white)
        .cornerRadius(16)
        .vsShadow(.medium)
    }
}

struct HeartRateChartView: View {
    let snapshots: [VitalsSnapshotiOS]
    var body: some View {
        GeometryReader { geo in
            let values = snapshots.map { Double($0.heartRate) }
            let minVal = (values.min() ?? 0) - 5
            let maxVal = (values.max() ?? 1) + 5
            let w = geo.size.width; let h = geo.size.height
            let pts = values.enumerated().map { i, v in
                CGPoint(
                    x: w * CGFloat(i) / CGFloat(max(values.count - 1, 1)),
                    y: h * CGFloat(1 - (v - minVal) / (maxVal - minVal))
                )
            }
            ZStack {
                // Gradient fill
                Path { path in
                    guard let first = pts.first else { return }
                    path.move(to: CGPoint(x: first.x, y: h))
                    path.addLine(to: first)
                    for pt in pts.dropFirst() { path.addLine(to: pt) }
                    path.addLine(to: CGPoint(x: pts.last?.x ?? w, y: h))
                    path.closeSubpath()
                }
                .fill(LinearGradient(colors: [Color(hex: "#FF4560").opacity(0.3), .clear],
                                     startPoint: .top, endPoint: .bottom))

                // Line
                Path { path in
                    guard let first = pts.first else { return }
                    path.move(to: first)
                    for pt in pts.dropFirst() { path.addLine(to: pt) }
                }
                .stroke(Color(hex: "#FF4560"), lineWidth: 2)

                // Dots
                ForEach(pts.indices, id: \.self) { i in
                    Circle()
                        .fill(Color(hex: "#FF4560"))
                        .frame(width: 6, height: 6)
                        .position(pts[i])
                }
            }
        }
        .padding(12)
    }
}

#endif
