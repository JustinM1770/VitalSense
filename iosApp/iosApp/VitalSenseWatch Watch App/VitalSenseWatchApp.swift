import SwiftUI
import FirebaseCore

@main
struct VitalSenseWatch_Watch_AppApp: App {
    
    init() {
        FirebaseApp.configure()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
