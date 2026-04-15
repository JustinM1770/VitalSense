// FallDetectorWatch.swift — Detección de caída para Apple Watch SE 2
// Algoritmo: caída libre (g < 0.4) seguida de impacto (g > 2.5) en ventana de 1.5s
// Apple Watch SE 2 no tiene fall detection nativa (requiere Series 4+), esto lo implementa por software.

import Foundation
import CoreMotion
import OSLog

private let logger = Logger(subsystem: "mx.ita.vitalsense.ios.watchkitapp", category: "FallDetector")

class FallDetectorWatch {

    private let motionManager  = CMMotionManager()
    private let queue          = OperationQueue()
    private let onFall: () -> Void

    // Umbrales
    private let freeFallThreshold: Double = 0.45  // g — por debajo = caída libre
    private let impactThreshold:   Double = 2.50  // g — por encima = impacto
    private let windowSeconds:     Double = 1.5   // ventana máxima caída→impacto

    private var freeFallTime: Date? = nil
    private var detectionCooldown   = false

    init(onFall: @escaping () -> Void) {
        self.onFall = onFall
        queue.maxConcurrentOperationCount = 1
    }

    // MARK: - Public

    func start() {
        guard motionManager.isAccelerometerAvailable else {
            logger.warning("Accelerometer not available")
            return
        }

        motionManager.accelerometerUpdateInterval = 0.05  // 20 Hz

        motionManager.startAccelerometerUpdates(to: queue) { [weak self] data, _ in
            guard let self, let data else { return }

            let x = data.acceleration.x
            let y = data.acceleration.y
            let z = data.acceleration.z
            let g = sqrt(x*x + y*y + z*z)

            let now = Date()

            // Fase 1: detectar caída libre
            if g < self.freeFallThreshold && self.freeFallTime == nil {
                self.freeFallTime = now
                logger.debug("Free fall detected, g=\(String(format: "%.2f", g))")
                return
            }

            // Fase 2: detectar impacto después de caída libre
            if let fallStart = self.freeFallTime {
                let elapsed = now.timeIntervalSince(fallStart)

                if elapsed > self.windowSeconds {
                    // Ventana expirada sin impacto → falso positivo, resetear
                    self.freeFallTime = nil
                    return
                }

                if g > self.impactThreshold && !self.detectionCooldown {
                    self.freeFallTime = nil
                    self.detectionCooldown = true

                    logger.warning("FALL DETECTED — g=\(String(format: "%.2f", g)) elapsed=\(String(format: "%.2f", elapsed))s")

                    DispatchQueue.main.async { self.onFall() }

                    // Cooldown de 30s para evitar múltiples triggers
                    DispatchQueue.main.asyncAfter(deadline: .now() + 30) {
                        self.detectionCooldown = false
                    }
                }
            }
        }

        logger.info("Started")
    }

    func stop() {
        motionManager.stopAccelerometerUpdates()
        freeFallTime = nil
        logger.info("Stopped")
    }
}
