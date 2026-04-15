#if os(iOS)
import CoreBluetooth
import Combine

// MARK: - UUIDs idénticos al Android BleUUIDs object
enum BLEUUIDs {
    static let biometricAIService = CBUUID(string: "4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    static let heartRateChar     = CBUUID(string: "beb5483e-36e1-4688-b7f5-ea07361b26a8")
    static let glucoseChar       = CBUUID(string: "beb5483e-36e1-4688-b7f5-ea07361b26a9")
    static let spo2Char          = CBUUID(string: "beb5483e-36e1-4688-b7f5-ea07361b26aa")
    static let cccd              = CBUUID(string: "00002902-0000-1000-8000-00805f9b34fb")
    static let stdHRService      = CBUUID(string: "0000180d-0000-1000-8000-00805f9b34fb")
    static let stdHRChar         = CBUUID(string: "00002a37-0000-1000-8000-00805f9b34fb")
}

// MARK: - Modelos idénticos a BleDevice, BleVitals, BleConnectionState

struct BLEDeviceInfo: Identifiable, Equatable {
    let id: String   // peripheral.identifier.uuidString
    let name: String
    let rssi: Int
    let peripheral: CBPeripheral

    static func == (lhs: BLEDeviceInfo, rhs: BLEDeviceInfo) -> Bool { lhs.id == rhs.id }
}

struct BLEVitals {
    var heartRate: Int?      = nil
    var glucose: Double?     = nil
    var spo2: Int?           = nil
    var timestamp: Double?   = nil
}

enum BLEConnectionState: Equatable {
    case disconnected
    case connecting
    case connected(deviceName: String)
    case error(message: String)
}

// MARK: - BLEService (equivalente a BleRepository.kt)
// Usa CBCentralManager en DispatchQueue.main para que todos los callbacks
// sean en hilo principal — equivalente al Handler/Looper de Android BLE.

class BLEService: NSObject, ObservableObject {

    @Published var connectionState: BLEConnectionState = .disconnected
    @Published var vitals   = BLEVitals()
    @Published var devices: [BLEDeviceInfo] = []

    private var centralManager: CBCentralManager!
    private var connectedPeripheral: CBPeripheral?

    override init() {
        super.init()
        centralManager = CBCentralManager(delegate: self, queue: DispatchQueue.main)
    }

    // MARK: - Escaneo (equivalente a scanDevices())

    func startScan() {
        devices = []
        guard centralManager.state == .poweredOn else { return }
        centralManager.scanForPeripherals(withServices: nil,
                                          options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])
    }

    func stopScan() {
        centralManager.stopScan()
    }

    // MARK: - Conexión (equivalente a connect() / disconnect())

    func connect(_ device: BLEDeviceInfo) {
        connectionState = .connecting
        centralManager.stopScan()
        connectedPeripheral = device.peripheral
        centralManager.connect(device.peripheral, options: nil)
    }

    func disconnect() {
        if let p = connectedPeripheral { centralManager.cancelPeripheralConnection(p) }
        connectedPeripheral = nil
        connectionState = .disconnected
        vitals = BLEVitals()
    }

    // MARK: - Métodos de control (equivalente a setConnecting/setConnected/updateVitals)

    func setConnecting()                         { connectionState = .connecting }
    func setDisconnected()                       { connectionState = .disconnected }
    func setConnected(deviceName: String)        { connectionState = .connected(deviceName: deviceName) }
    func updateVitals(_ newVitals: BLEVitals)   { vitals = newVitals }

    // MARK: - Parseo de características (idéntico a parseCharacteristic() en Android)

    private func parseCharacteristic(uuid: CBUUID, bytes: [UInt8]) {
        guard !bytes.isEmpty else { return }
        switch uuid {

        case BLEUUIDs.heartRateChar, BLEUUIDs.stdHRChar:
            // Estándar BLE HR: byte0 = flags
            // Si bit0 = 0 → HR en byte1; si bit0 = 1 → HR en bytes 1-2 (little-endian)
            guard bytes.count >= 2 else { return }
            let hr: Int = (bytes[0] & 0x01 == 0)
                ? Int(bytes[1])
                : Int(bytes[1]) | (Int(bytes[2]) << 8)
            vitals.heartRate = hr
            vitals.timestamp = Date().timeIntervalSince1970 * 1000

        case BLEUUIDs.glucoseChar:
            // Valor Float IEEE 754 en 4 bytes little-endian (igual que Android intBitsToFloat)
            if bytes.count >= 4 {
                let bits: UInt32 = UInt32(bytes[0])
                                 | (UInt32(bytes[1]) << 8)
                                 | (UInt32(bytes[2]) << 16)
                                 | (UInt32(bytes[3]) << 24)
                vitals.glucose = Double(Float(bitPattern: bits))
            } else {
                vitals.glucose = Double(bytes[0])
            }

        case BLEUUIDs.spo2Char:
            vitals.spo2 = Int(bytes[0])

        default:
            break
        }
    }
}

// MARK: - CBCentralManagerDelegate

extension BLEService: CBCentralManagerDelegate {

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        // Bluetooth encendido/apagado — la UI puede reaccionar vía connectionState
    }

    func centralManager(_ central: CBCentralManager,
                        didDiscover peripheral: CBPeripheral,
                        advertisementData: [String: Any],
                        rssi RSSI: NSNumber) {
        guard let name = peripheral.name, !name.isEmpty else { return }

        let device = BLEDeviceInfo(
            id: peripheral.identifier.uuidString,
            name: name,
            rssi: RSSI.intValue,
            peripheral: peripheral
        )

        if let idx = devices.firstIndex(where: { $0.id == device.id }) {
            devices[idx] = device
        } else {
            devices.append(device)
        }
        devices.sort { $0.rssi > $1.rssi }  // ordenar por señal descendente
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        peripheral.delegate = self
        peripheral.discoverServices(nil)
    }

    func centralManager(_ central: CBCentralManager,
                        didDisconnectPeripheral peripheral: CBPeripheral,
                        error: Error?) {
        connectionState = .disconnected
        connectedPeripheral = nil
    }

    func centralManager(_ central: CBCentralManager,
                        didFailToConnect peripheral: CBPeripheral,
                        error: Error?) {
        connectionState = .error(message: "No se pudo conectar: \(error?.localizedDescription ?? "Error desconocido")")
    }
}

// MARK: - CBPeripheralDelegate

extension BLEService: CBPeripheralDelegate {

    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard error == nil, let services = peripheral.services else { return }

        let deviceName = peripheral.name ?? "BioMetric AI"
        connectionState = .connected(deviceName: deviceName)

        // Descubrir characteristics en servicio BioMetric AI y servicio estándar HR
        for service in services {
            if service.uuid == BLEUUIDs.biometricAIService ||
               service.uuid == BLEUUIDs.stdHRService {
                peripheral.discoverCharacteristics(nil, for: service)
            }
        }
    }

    func peripheral(_ peripheral: CBPeripheral,
                    didDiscoverCharacteristicsFor service: CBService,
                    error: Error?) {
        guard error == nil, let characteristics = service.characteristics else { return }

        let notifyUUIDs: Set<CBUUID> = [
            BLEUUIDs.heartRateChar,
            BLEUUIDs.glucoseChar,
            BLEUUIDs.spo2Char,
            BLEUUIDs.stdHRChar
        ]

        for char in characteristics where notifyUUIDs.contains(char.uuid) {
            peripheral.setNotifyValue(true, for: char)
        }
    }

    func peripheral(_ peripheral: CBPeripheral,
                    didUpdateValueFor characteristic: CBCharacteristic,
                    error: Error?) {
        guard error == nil, let data = characteristic.value else { return }
        parseCharacteristic(uuid: characteristic.uuid, bytes: [UInt8](data))
    }
}

#endif
