//
//  iosAppApp.swift
//  iosApp
//
//  Created by Macbook Air de Justin on 19/03/26.
//

import SwiftUI
import FirebaseCore
import GoogleSignIn

@main
struct iosAppApp: App {

    init() {
        FirebaseApp.configure()
        WatchConnectivitySender.shared.setup()
    }

    var body: some Scene {
        WindowGroup {
            AppNavigation()
                .onOpenURL { url in
                    GIDSignIn.sharedInstance.handle(url)
                }
                .task {
                    await NotificationService.shared.requestAuthorization()
                }
        }
    }
}
