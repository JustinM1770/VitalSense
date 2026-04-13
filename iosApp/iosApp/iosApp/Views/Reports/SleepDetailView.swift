#if os(iOS)
import SwiftUI

struct SleepDetailView: View {
    let score: Int
    let horas: Double
    let estado: String

    private var ringColor: Color {
        if score >= 85 { return Color(hex: "#10B981") }
        if score >= 70 { return Color(hex: "#22C55E") }
        if score >= 50 { return Color(hex: "#F59E0B") }
        return Color(hex: "#EF4444")
    }

    private var deepHoras:  Double { horas * 0.22 }
    private var remHoras:   Double { horas * 0.20 }
    private var lightHoras: Double { horas * 0.58 }

    var body: some View {
        ZStack {
            Color(hex: "#F8FAFC").ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(spacing: 20) {
                    // Quality Score Card
                    VStack(spacing: 16) {
                        HStack {
                            Text("Calidad del sueño")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundColor(Color(hex: "#0F172A"))
                            Spacer()
                        }

                        HStack(spacing: 24) {
                            // Ring
                            ZStack {
                                Circle()
                                    .stroke(Color(hex: "#E2E8F0"), lineWidth: 7)
                                    .frame(width: 80, height: 80)
                                Circle()
                                    .trim(from: 0, to: CGFloat(score) / 100)
                                    .stroke(ringColor, style: StrokeStyle(lineWidth: 7, lineCap: .round))
                                    .rotationEffect(.degrees(-90))
                                    .frame(width: 80, height: 80)
                                VStack(spacing: 1) {
                                    Text("\(score)%").font(.system(size: 20, weight: .bold)).foregroundColor(ringColor)
                                    Text(String(format: "%.1fh", horas)).font(.system(size: 11)).foregroundColor(Color(hex: "#64748B"))
                                }
                            }

                            VStack(alignment: .leading, spacing: 6) {
                                Text(estado).font(.system(size: 18, weight: .bold)).foregroundColor(ringColor)
                                Text(qualityDescription).font(.system(size: 13)).foregroundColor(Color(hex: "#334155"))
                            }
                            Spacer()
                        }
                    }
                    .padding(20)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                    .vsShadow(.medium)

                    // Sleep Stages Card
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Etapas del sueño")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(Color(hex: "#0F172A"))

                        SleepStageRow(name: "Sueño Profundo", hours: deepHoras,
                                      fraction: deepHoras / horas, color: Color(hex: "#1D4ED8"))
                        SleepStageRow(name: "Sueño REM", hours: remHoras,
                                      fraction: remHoras / horas, color: Color(hex: "#7C3AED"))
                        SleepStageRow(name: "Sueño Ligero", hours: lightHoras,
                                      fraction: lightHoras / horas, color: Color(hex: "#0EA5E9"))
                    }
                    .padding(20)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                    .vsShadow(.medium)

                    // Clinical Summary
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Resumen clínico")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(Color(hex: "#0F172A"))

                        ForEach(clinicalPoints, id: \.self) { point in
                            HStack(alignment: .top, spacing: 10) {
                                Circle().fill(ringColor).frame(width: 6, height: 6).padding(.top, 6)
                                Text(point).font(.system(size: 13)).foregroundColor(Color(hex: "#334155"))
                            }
                        }
                    }
                    .padding(20)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                    .vsShadow(.medium)

                    Spacer(minLength: 90)
                }
                .padding(20)
            }
        }
        .navigationTitle("Sueño detallado")
        .navigationBarTitleDisplayMode(.large)
    }

    private var qualityDescription: String {
        if score >= 85 { return "Excelente calidad de sueño" }
        if score >= 70 { return "Buena calidad de sueño" }
        if score >= 50 { return "Calidad de sueño regular" }
        return "Calidad de sueño baja"
    }

    private var clinicalPoints: [String] {
        var pts = [qualityDescription + "."]
        if horas < 7 { pts.append("Se recomiendan 7-9 horas de sueño por noche.") }
        else { pts.append("Duración de sueño dentro del rango recomendado (7-9 horas).") }
        pts.append("Evita pantallas al menos 60 minutos antes de dormir.")
        if score < 70 { pts.append("Considera mantener un horario de sueño consistente.") }
        return pts
    }
}

struct SleepStageRow: View {
    let name: String; let hours: Double; let fraction: Double; let color: Color
    var body: some View {
        VStack(spacing: 6) {
            HStack {
                Text(name).font(.system(size: 13, weight: .semibold)).foregroundColor(Color(hex: "#334155"))
                Spacer()
                Text(String(format: "%.1fh • %.0f%%", hours, fraction * 100))
                    .font(.system(size: 12)).foregroundColor(Color(hex: "#64748B"))
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(Color(hex: "#E2E8F0")).frame(height: 8)
                    Capsule().fill(color).frame(width: geo.size.width * max(0, min(1, fraction)), height: 8)
                }
            }
            .frame(height: 8)
        }
    }
}

#endif
