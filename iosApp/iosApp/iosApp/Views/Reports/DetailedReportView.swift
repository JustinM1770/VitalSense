#if os(iOS)
import SwiftUI

struct DetailedReportView: View {
    @StateObject private var vm = DailyReportViewModel()

    var body: some View {
        ZStack {
            Color(hex: "#F0F2F5").ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(spacing: 16) {
                    // Filter chips
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(["Hoy", "Ayer", "7 días", "Mes"], id: \.self) { f in
                                Button(action: { vm.selectedFilter = f }) {
                                    Text(f)
                                        .font(.system(size: 13, weight: .semibold))
                                        .foregroundColor(vm.selectedFilter == f ? .white : Color(hex: "#0D1B2A"))
                                        .padding(.horizontal, 16).padding(.vertical, 8)
                                        .background(vm.selectedFilter == f ? Color.primaryBlue : Color.white)
                                        .clipShape(Capsule())
                                        .shadow(color: .black.opacity(0.05), radius: 3, x: 0, y: 1)
                                }
                            }
                        }
                        .padding(.horizontal, Spacing.xl)
                    }
                    .padding(.top, 8)

                    if vm.heartRates.isEmpty && vm.spo2Values.isEmpty {
                        ProgressView("Cargando datos...")
                            .frame(maxWidth: .infinity).padding(.vertical, 32)
                    }

                    // Heart Rate Card
                    DetailedMetricCard(
                        title: "Ritmo Cardíaco",
                        subtitle: "Promedio del periodo",
                        value: vm.avgHR > 0 ? "\(Int(vm.avgHR)) BPM" : "-- BPM",
                        color: Color(hex: "#FF4560"),
                        icon: "heart.fill",
                        values: vm.heartRates
                    )

                    // SpO2 Card
                    DetailedMetricCard(
                        title: "Oxigenación (SpO₂)",
                        subtitle: "Promedio del periodo",
                        value: vm.avgSpo2 > 0 ? "\(Int(vm.avgSpo2))%" : "--%",
                        color: Color(hex: "#4CAF50"),
                        icon: "lungs.fill",
                        values: vm.spo2Values
                    )

                    // Glucose Card
                    DetailedMetricCard(
                        title: "Glucosa",
                        subtitle: "Promedio del periodo",
                        value: vm.avgGlucose > 0 ? "\(Int(vm.avgGlucose)) mg/dL" : "-- mg/dL",
                        color: Color(hex: "#7B61FF"),
                        icon: "drop.fill",
                        values: vm.glucoseValues
                    )

                    Spacer(minLength: 90)
                }
            }
        }
        .navigationTitle("Reporte Detallado")
        .navigationBarTitleDisplayMode(.large)
        .onAppear { vm.load() }
    }
}

struct DetailedMetricCard: View {
    let title: String; let subtitle: String; let value: String
    let color: Color; let icon: String; let values: [Double]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                Image(systemName: icon).foregroundColor(color).font(.system(size: 20))
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.system(size: 15, weight: .bold)).foregroundColor(Color(hex: "#0D1B2A"))
                    Text(subtitle).font(.system(size: 12)).foregroundColor(Color(hex: "#6B7A8D"))
                }
                Spacer()
                Text(value).font(.system(size: 18, weight: .bold)).foregroundColor(color)
            }

            if values.count > 1 {
                LineChartView(values: values, color: color).frame(height: 90)
            } else {
                Text("Sin datos suficientes").font(.system(size: 12)).foregroundColor(Color(hex: "#6B7A8D"))
                    .frame(maxWidth: .infinity, alignment: .center).frame(height: 90)
            }
        }
        .padding(20)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .vsShadow(.medium)
        .padding(.horizontal, Spacing.xl)
    }
}

#endif
