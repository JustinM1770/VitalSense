import SwiftUI

struct SplashView: View {
    let onFinish: () -> Void
    @State private var opacity = 0.0

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color.white, Color(hex: "#B6D8FF")],
                startPoint: .top, endPoint: .bottom
            )
            .ignoresSafeArea()

            VStack(spacing: 12) {
                Image(systemName: "eye.circle.fill")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 80, height: 80)
                    .foregroundColor(Color(hex: "#1169FF"))

                HStack(spacing: 0) {
                    Text("Vital")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(Color(hex: "#0A2540"))
                    Text("Sense")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(Color(hex: "#52A2C4"))
                }
            }
            .opacity(opacity)
        }
        .onAppear {
            withAnimation(.easeIn(duration: 0.8)) { opacity = 1.0 }
            DispatchQueue.main.asyncAfter(deadline: .now() + 2) { onFinish() }
        }
    }
}
