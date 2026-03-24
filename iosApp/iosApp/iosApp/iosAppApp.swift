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
    }

    var body: some Scene {
        WindowGroup {
            AppNavigation()
                .onOpenURL { url in
                    GIDSignIn.sharedInstance.handle(url)
                }
        }
    }
}
