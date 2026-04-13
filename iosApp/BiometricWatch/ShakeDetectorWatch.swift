// ShakeDetectorWatch.swift - Transcripción de ShakeDetector.kt
// Detecta agitado del Apple Watch usando acelerómetro CoreMotion

import Foundation
import CoreMotion

class ShakeDetectorWatch {
    
    private let motionManager = CMMotionManager()
    private let operationQueue = OperationQueue()
    private let onShake: () -> Void
    
    // Calibración para muñeca (equivalente a Android)
    private let shakeThresholdGravity: Double = 1.8
    private let shakeSlopTimeMs: Int = 300
    private let shakeCountResetTimeMs: Int = 2000
    
    private var shakeTimestamp: Int64 = 0
    private var shakeCount: Int = 0
    
    init(onShake: @escaping () -> Void) {
        self.onShake = onShake
        self.operationQueue.maxConcurrentOperationCount = 1
    }
    
    func start() {
        guard motionManager.isAccelerometerAvailable else {
            print("[ShakeDetector] Accelerometer not available")
            return
        }
        
        motionManager.accelerometerUpdateInterval = 0.1 // 10 Hz (similar a SENSOR_DELAY_UI)
        
        motionManager.startAccelerometerUpdates(to: operationQueue) { [weak self] (data, error) in
            guard let self = self,
                  let accelerometerData = data else { return }
            
            let x = accelerometerData.acceleration.x
            let y = accelerometerData.acceleration.y
            let z = accelerometerData.acceleration.z
            
            // Calcular magnitud del vector (normalizado por gravedad)
            let gX = x
            let gY = y
            let gZ = z
            
            let gForce = sqrt(gX * gX + gY * gY + gZ * gZ)
            
            if gForce > self.shakeThresholdGravity {
                let now = Int64(Date().timeIntervalSince1970 * 1000) // millis
                
                // Ignorar movimientos demasiado cercanos
                if self.shakeTimestamp + Int64(self.shakeSlopTimeMs) > now {
                    return
                }
                
                // Resetear contador si ha pasado mucho tiempo
                if self.shakeTimestamp + Int64(self.shakeCountResetTimeMs) < now {
                    self.shakeCount = 0
                }
                
                self.shakeTimestamp = now
                self.shakeCount += 1
                
                // 3 agitadas consecutivas = trigger
                if self.shakeCount >= 3 {
                    self.shakeCount = 0
                    DispatchQueue.main.async {
                        self.onShake()
                    }
                }
            }
        }
        
        print("[ShakeDetector] Started")
    }
    
    func stop() {
        motionManager.stopAccelerometerUpdates()
        print("[ShakeDetector] Stopped")
    }
}
