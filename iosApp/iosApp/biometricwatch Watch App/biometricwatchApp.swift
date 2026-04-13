//
//  biometricwatchApp.swift
//  biometricwatch Watch App
//
//  Created by Macbook Air de Justin on 02/04/26.
//

import SwiftUI

@main
struct biometricwatch_Watch_AppApp: App {
    @StateObject private var vm = WatchViewModel()

    // Activar WatchConnectivity al arrancar
    private let connectivity = WatchConnectivityManager.shared

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(vm)
        }
    }
}
