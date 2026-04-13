import SwiftUI

struct ContentView: View {
    @StateObject private var viewModel = WatchViewModel()
    
    var body: some View {
        VStack {
            if viewModel.isPaired {
                // Pantalla de Monitoreo
                VStack(spacing: 8) {
                    Image(systemName: "heart.fill")
                        .font(.system(size: 40))
                        .foregroundColor(.red)
                        .symbolEffect(.pulse, isActive: viewModel.heartRate > 0)
                    
                    Text("\(Int(viewModel.heartRate))")
                        .font(.system(size: 50, weight: .bold, design: .rounded))
                    
                    Text("BPM")
                        .font(.caption)
                        .foregroundColor(.gray)
                    
                    Button("Desvincular") {
                        withAnimation {
                            viewModel.unpair()
                        }
                    }
                    .font(.footnote)
                    .tint(.red)
                    .padding(.top, 5)
                    
                    Button("🚨 Modo SOS") {
                        withAnimation {
                            viewModel.isSOSMode.toggle()
                        }
                    }
                    .font(.footnote)
                    .tint(.orange)
                }
            } else {
                // Pantalla de Vinculación
                VStack(spacing: 12) {
                    Text("Código de Registro")
                        .font(.footnote)
                        .foregroundColor(.gray)
                    
                    Text(viewModel.pairingCode.isEmpty ? "Cargando..." : viewModel.pairingCode)
                        .font(.title2.bold())
                        .foregroundColor(.blue)
                    
                    Text("Ingresa este código en tu iPhone")
                        .font(.system(size: 10))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black)
        .alert(isPresented: $viewModel.showSOSConfirmation) {
            Alert(
                title: Text("¿Emergencia SOS?"),
                message: Text("Se detectó un movimiento brusco. ¿Deseas activar el modo SOS y alertar a tus contactos?"),
                primaryButton: .destructive(Text("Activar SOS")) {
                    withAnimation {
                        viewModel.confirmSOS()
                    }
                },
                secondaryButton: .cancel(Text("Falsa Alarma"))
            )
        }
        .sheet(isPresented: $viewModel.isSOSMode) {
            // Pantalla SOS (Código QR)
            VStack {
                Text("CÓDIGO SOS")
                    .font(.headline)
                    .foregroundColor(.red)
                    .padding(.top, 5)
                
                Image(uiImage: viewModel.generateQRCode())
                    .resizable()
                    .interpolation(.none)
                    .scaledToFit()
                    .frame(width: 130, height: 130)
                    .cornerRadius(8)
                
                Text("Escanea para ver historial médico")
                    .font(.system(size: 10))
                    .foregroundColor(.gray)
                    .padding(.top, 2)
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(action: { viewModel.isSOSMode = false }) {
                        Text("Cerrar")
                    }
                }
            }
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
