import SwiftUI

struct DashboardView: View {
    @StateObject private var vm = DashboardViewModel()

    var body: some View {
        NavigationStack {
            ZStack(alignment: .top) {
                Color(hex: "#F7F9FC").ignoresSafeArea()

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 24) {
                        DashboardHeaderView()
                            .padding(.horizontal, 24)
                            .padding(.top, 8)

                        DashboardSearchBarView()
                            .padding(.horizontal, 24)

                        VStack(alignment: .leading, spacing: 20) {
                            DashboardSectionHeader(title: "Esta semana")

                            SleepSummaryCardView(patients: vm.patients)

                            DashboardSectionHeader(title: "Metricas de Salud")

                            if vm.isLoading {
                                ProgressView("Cargando vitales...")
                                    .frame(maxWidth: .infinity, alignment: .center)
                                    .padding(.vertical, 16)
                            } else if vm.patients.isEmpty {
                                EmptyPatientsCardView()
                            } else {
                                VStack(spacing: 14) {
                                    ForEach(vm.patients) { patient in
                                        NavigationLink(destination: PatientDetailView(patient: patient)) {
                                            PatientCard(patient: patient)
                                        }
                                        .buttonStyle(.plain)
                                    }
                                }
                            }
                        }
                        .padding(24)
                        .background(Color(hex: "#90C2F9"))
                        .clipShape(
                            RoundedRectangle(cornerRadius: 40, style: .continuous)
                        )
                    }
                    .padding(.top, 20)
                    .padding(.bottom, 32)
                }

                if let alert = vm.alertMessage {
                    HStack(spacing: 10) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .foregroundColor(.orange)
                        Text(alert)
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(Color(hex: "#795548"))
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(Color(hex: "#FFF8E1"))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color(hex: "#FFB300"), lineWidth: 1)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .padding(.horizontal, 24)
                    .padding(.top, 8)
                    .transition(.move(edge: .top).combined(with: .opacity))
                }
            }
            .navigationBarHidden(true)
        }
        .onAppear { vm.startObserving() }
        .onDisappear { vm.stopObserving() }
        .animation(.easeInOut, value: vm.alertMessage)
    }
}

struct DashboardHeaderView: View {
    var body: some View {
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color(hex: "#1169FF"))
                .frame(width: 56, height: 56)
                .overlay(
                    Image(systemName: "person.fill")
                        .foregroundColor(.white)
                        .font(.system(size: 22, weight: .semibold))
                )

            VStack(alignment: .leading, spacing: 2) {
                Text("Bienvenido")
                    .font(.system(size: 14))
                    .foregroundColor(Color(hex: "#7F8C8D"))
                Text("VitalSense")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundColor(Color(hex: "#221F1F"))
            }

            Spacer()

            ZStack(alignment: .topTrailing) {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(Color.white)
                    .frame(width: 48, height: 48)
                    .shadow(color: .black.opacity(0.08), radius: 3, x: 0, y: 1)
                    .overlay(
                        Image(systemName: "bell.fill")
                            .foregroundColor(Color(hex: "#221F1F"))
                    )

                Circle()
                    .fill(Color.red)
                    .frame(width: 8, height: 8)
                    .offset(x: -8, y: 8)
            }
        }
    }
}

struct DashboardSearchBarView: View {
    var body: some View {
        HStack(spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(Color(hex: "#7F8C8D"))
                Text("Buscar paciente...")
                    .font(.system(size: 15))
                    .foregroundColor(Color(hex: "#7F8C8D"))
                Spacer()
            }
            .padding(.horizontal, 16)
            .frame(height: 56)
            .background(Color.white.opacity(0.55))
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))

            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color(hex: "#1169FF"))
                .frame(width: 56, height: 56)
                .overlay(
                    Image(systemName: "slider.horizontal.3")
                        .foregroundColor(.white)
                )
        }
    }
}

struct DashboardSectionHeader: View {
    let title: String

    var body: some View {
        HStack(spacing: 8) {
            Text(title)
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(Color(hex: "#221F1F"))
            Image(systemName: "arrow.right")
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(Color(hex: "#221F1F"))
        }
    }
}

struct SleepSummaryCardView: View {
    let patients: [VitalsDataiOS]

    private var averageSpo2: Int {
        guard !patients.isEmpty else { return 0 }
        let total = patients.reduce(0) { $0 + $1.spo2 }
        return total / patients.count
    }

    private var statusText: String {
        switch averageSpo2 {
        case 96...100: return "Estable"
        case 90..<96: return "Moderado"
        case 1..<90: return "Bajo"
        default: return "Sin datos"
        }
    }

    var body: some View {
        HStack(spacing: 14) {
            ZStack {
                Circle()
                    .stroke(Color(hex: "#10B981").opacity(0.2), lineWidth: 6)
                    .frame(width: 60, height: 60)
                Circle()
                    .trim(from: 0, to: CGFloat(min(max(Double(averageSpo2) / 100.0, 0), 1)))
                    .stroke(
                        Color(hex: "#10B981"),
                        style: StrokeStyle(lineWidth: 6, lineCap: .round)
                    )
                    .rotationEffect(.degrees(-90))
                    .frame(width: 60, height: 60)
                Text("\(averageSpo2)%")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(Color(hex: "#221F1F"))
            }

            Text("Sueno")
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(Color(hex: "#10B981"))

            Spacer()

            VStack(alignment: .trailing, spacing: 2) {
                Text("Promedio de hoy")
                    .font(.system(size: 11))
                    .foregroundColor(Color(hex: "#7F8C8D"))
                Text(statusText)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(Color(hex: "#10B981"))
            }
        }
        .padding(16)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

struct EmptyPatientsCardView: View {
    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: "heart.slash")
                .font(.system(size: 34))
                .foregroundColor(Color(hex: "#7F8C8D"))
            Text("Sin pacientes registrados")
                .foregroundColor(Color(hex: "#7F8C8D"))
                .font(.system(size: 15, weight: .medium))
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 24)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

struct PatientCard: View {
    let patient: VitalsDataiOS

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Circle()
                    .fill(Color(hex: "#1169FF").opacity(0.15))
                    .frame(width: 40, height: 40)
                    .overlay(
                        Text(String(patient.patientName.prefix(1)))
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(Color(hex: "#1169FF"))
                    )
                Text(patient.patientName)
                    .font(.system(size: 16, weight: .bold))
                Spacer()
                if patient.hasAlert {
                    Image(systemName: "exclamationmark.circle.fill")
                        .foregroundColor(.orange)
                }
            }

            HStack(spacing: 0) {
                VitalBadge(icon: "heart.fill", value: "\(patient.heartRate)", unit: "bpm", color: Color(hex: "#FF4560"))
                Spacer()
                VitalBadge(icon: "drop.fill", value: "\(Int(patient.glucose))", unit: "mg/dL", color: .orange)
                Spacer()
                VitalBadge(icon: "lungs.fill", value: "\(patient.spo2)", unit: "%", color: .green)
            }
        }
        .padding(16)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .shadow(color: .black.opacity(0.08), radius: 8, x: 0, y: 2)
    }
}

struct VitalBadge: View {
    let icon: String
    let value: String
    let unit: String
    let color: Color

    var body: some View {
        VStack(spacing: 4) {
            Image(systemName: icon).foregroundColor(color).font(.title3)
            Text(value).font(.system(size: 18, weight: .bold))
            Text(unit).font(.system(size: 10)).foregroundColor(.secondary)
        }
    }
}
