//
//  iosAppApp.swift
//  iosApp
//
//  Created by Macbook Air de Justin on 19/03/26.
//

import SwiftUI
import FirebaseCore

@main
struct iosAppApp: App {

    init() {
        FirebaseApp.configure()
    }

    var body: some Scene {
        WindowGroup {
            AppNavigation()
        }
    }
}
