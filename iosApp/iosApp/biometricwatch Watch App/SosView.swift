// SosView — fiel al Figma "Confirmacion SOS"
// Fondo negro, texto blanco: "AGITA TU MANO / PARA CONFIRMAR", size=24

import SwiftUI
import Combine

struct SosView: View {
    let sosId: String
    let userId: String
    @EnvironmentObject var vm: WatchViewModel

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            VStack(spacing: 14) {
                Spacer()

                Text("AGITA TU MANO\nPARA CONFIRMAR")
                    .font(.system(size: 24, weight: .regular))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .lineSpacing(4)

                Spacer()

                // Cancelar — pequeño y discreto abajo
                Button("Cancelar SOS") { vm.dismissSOS() }
                    .font(.system(size: 13, weight: .regular))
                    .foregroundColor(Color(white: 0.45))
                    .padding(.bottom, 8)
            }
            .padding(.horizontal, 10)
        }
    }
}
