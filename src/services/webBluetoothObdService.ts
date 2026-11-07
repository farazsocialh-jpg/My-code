/**
 * Web Bluetooth API Service Module for ELM327 OBD2 Adapters
 * 
 * Provides:
 * - Device discovery & pairing via navigator.bluetooth.requestDevice
 * - GATT server connection & lifecycle handling (reconnection, disconnect detection)
 * - Transparent bidirectional communication stream (TX write / RX notifications)
 * - Serialized command transmission with prompt ('>') boundary resolution
 * - Stream subscription hooks for incoming OBD2 responses
 */

export interface BluetoothDeviceInfo {
  id: string;
  name: string;
  connected: boolean;
  serviceUuid?: string;
  rxCharacteristicUuid?: string;
  txCharacteristicUuid?: string;
}

export type BluetoothConnectionStatus = 
  | 'IDLE' 
  | 'REQUESTING_DEVICE' 
  | 'CONNECTING_GATT' 
  | 'DISCOVERING_SERVICES' 
  | 'STARTING_NOTIFICATIONS' 
  | 'CONNECTED' 
  | 'DISCONNECTED' 
  | 'ERROR';

export interface BluetoothPacket {
  direction: 'TX' | 'RX' | 'INFO' | 'ERR';
  timestamp: number;
  payload: string;
}

export type StatusListener = (status: BluetoothConnectionStatus, message?: string) => void;
export type PacketListener = (packet: BluetoothPacket) => void;
export type RawStreamListener = (chunk: string) => void;

/**
 * Standard and proprietary Bluetooth Low Energy GATT Services
 * commonly implemented by ELM327 / OBD2 wireless dongles.
 */
export const KNOWN_OBD2_BLE_SERVICES = [
  // 1. Vgate iCar Pro BLE / Veepeak OBDCheck BLE / Generic ELM327 BLE (FFF0)
  '0000fff0-0000-1000-8000-00805f9b34fb',
  // 2. HM-10 / CC2540 / CC2541 Serial Pass-through (FFE0)
  '0000ffe0-0000-1000-8000-00805f9b34fb',
  // 3. Nordic UART Service (NUS) - OBDLink CX, Carista, NexPeak (6E400001)
  '6e400001-b5a3-f393-e0a9-e50e24dcca9e',
  // 4. Alternative ELM327 BLE Profile (18F0)
  '000018f0-0000-1000-8000-00805f9b34fb',
  // 5. Microchip IS1678 / BM70 Transparent UART (ISSC)
  '49535343-fe7d-4ae5-8fa9-9fafd205e455',
  // 6. Carista Proprietary BLE Service
  'e7810a71-73ae-499d-8c15-faa9aef0c3f2'
];

export class WebBluetoothObdService {
  private status: BluetoothConnectionStatus = 'IDLE';
  private device: any = null;
  private gattServer: any = null;
  private rxCharacteristic: any = null; // Incoming data notification characteristic
  private txCharacteristic: any = null; // Outgoing command write characteristic

  private statusListeners: Set<StatusListener> = new Set();
  private packetListeners: Set<PacketListener> = new Set();
  private rawStreamListeners: Set<RawStreamListener> = new Set();

  private textDecoder = new TextDecoder('utf-8');
  private textEncoder = new TextEncoder();

  // Internal stream buffer for multi-chunk ELM responses ending with '>'
  private rxBuffer: string = '';
  private activeCommandResolver: ((response: string) => void) | null = null;
  private commandTimeoutId: any = null;

  constructor() {}

  /**
   * Checks if the Web Bluetooth API is supported by the current browser/environment.
   */
  public isSupported(): boolean {
    return typeof navigator !== 'undefined' && !!(navigator as any).bluetooth;
  }

  public getStatus(): BluetoothConnectionStatus {
    return this.status;
  }

  public getDeviceInfo(): BluetoothDeviceInfo | null {
    if (!this.device) return null;
    return {
      id: this.device.id,
      name: this.device.name || 'Unnamed ELM327 Adapter',
      connected: !!(this.gattServer && this.gattServer.connected),
      serviceUuid: this.rxCharacteristic?.service?.uuid,
      rxCharacteristicUuid: this.rxCharacteristic?.uuid,
      txCharacteristicUuid: this.txCharacteristic?.uuid
    };
  }

  /**
   * Step 1: Initiates device scan & pairing prompt via navigator.bluetooth.requestDevice.
   * Step 2: Establishes GATT connection.
   * Step 3: Discovers UART serial characteristics & starts notification stream.
   */
  public async requestAndConnect(): Promise<BluetoothDeviceInfo> {
    if (!this.isSupported()) {
      const err = 'Web Bluetooth API is not available in this browser. Please use Chrome (Desktop/Android) or Edge.';
      this.updateStatus('ERROR', err);
      throw new Error(err);
    }

    try {
      this.updateStatus('REQUESTING_DEVICE', 'Awaiting device selection by user...');
      this.logPacket('INFO', 'Opening Web Bluetooth scan dialog for ELM327 OBD2 adapters...');

      // Request device with both service filters and fallback acceptAllDevices
      const bluetooth = (navigator as any).bluetooth;
      const device = await bluetooth.requestDevice({
        acceptAllDevices: true,
        optionalServices: KNOWN_OBD2_BLE_SERVICES
      });

      this.device = device;
      this.logPacket('INFO', `Selected Bluetooth Device: ${device.name || 'ELM327'} (${device.id})`);

      // Register disconnect listener
      device.addEventListener('gattserverdisconnected', this.onGattDisconnected.bind(this));

      // Connect to GATT Server
      this.updateStatus('CONNECTING_GATT', `Connecting to GATT server on ${device.name || 'device'}...`);
      const gattServer = await device.gatt.connect();
      this.gattServer = gattServer;
      this.logPacket('INFO', 'GATT Server connection established.');

      // Discover UART Characteristics
      this.updateStatus('DISCOVERING_SERVICES', 'Searching for OBD2 serial communication stream...');
      await this.discoverSerialCharacteristics(gattServer);

      // Start notifications for incoming stream
      this.updateStatus('STARTING_NOTIFICATIONS', 'Enabling GATT notifications for ECU stream...');
      await this.rxCharacteristic.startNotifications();
      this.rxCharacteristic.addEventListener('characteristicvaluechanged', this.handleGattNotification.bind(this));
      this.logPacket('INFO', `GATT Notifications active on characteristic: ${this.rxCharacteristic.uuid}`);

      this.updateStatus('CONNECTED', `Connected to ${device.name || 'ELM327'}`);
      this.logPacket('INFO', 'Bidirectional OBD2 communication stream is ready.');

      return this.getDeviceInfo()!;
    } catch (error: any) {
      this.updateStatus('ERROR', error.message || 'Bluetooth connection failed');
      this.logPacket('ERR', `Connection failed: ${error.message || error}`);
      throw error;
    }
  }

  /**
   * Traverses primary GATT services and characteristics to find
   * the notify/indicate channel (RX) and write channel (TX).
   */
  private async discoverSerialCharacteristics(server: any): Promise<void> {
    const services = await server.getPrimaryServices();
    let foundRx = false;
    let foundTx = false;

    for (const service of services) {
      try {
        const characteristics = await service.getCharacteristics();
        for (const char of characteristics) {
          const props = char.properties;

          // Check if characteristic can receive incoming notifications
          if ((props.notify || props.indicate) && !foundRx) {
            this.rxCharacteristic = char;
            foundRx = true;
          }

          // Check if characteristic can transmit outgoing commands
          if ((props.write || props.writeWithoutResponse) && !foundTx) {
            this.txCharacteristic = char;
            foundTx = true;
          }
        }

        // If both characteristics found in the same or compatible service, we can proceed
        if (foundRx && foundTx) {
          break;
        }
      } catch (e) {
        // Continue searching next service
      }
    }

    if (!this.rxCharacteristic || !this.txCharacteristic) {
      throw new Error(
        'Could not identify compatible serial UART characteristics on this Bluetooth device. Ensure device is an ELM327 BLE adapter.'
      );
    }
  }

  /**
   * Internal handler for incoming GATT notifications.
   * Decodes incoming binary bytes into UTF-8 text and handles ELM327 prompt ('>') termination.
   */
  private handleGattNotification(event: any): void {
    const value = event.target.value;
    if (!value) return;

    const chunk = this.textDecoder.decode(value);
    this.rxBuffer += chunk;

    // Dispatch raw chunk to stream listeners
    this.rawStreamListeners.forEach(cb => cb(chunk));

    // ELM327 completes a response with '>' (ASCII 62)
    if (this.rxBuffer.includes('>')) {
      const fullResponse = this.rxBuffer;
      this.rxBuffer = '';

      // Clean carriage returns, linefeeds, and prompt symbol
      const cleanResponse = fullResponse
        .replace(/\r/g, ' ')
        .replace(/\n/g, ' ')
        .replace(/>/g, '')
        .trim();

      this.logPacket('RX', cleanResponse);

      if (this.activeCommandResolver) {
        if (this.commandTimeoutId) {
          clearTimeout(this.commandTimeoutId);
          this.commandTimeoutId = null;
        }
        const resolve = this.activeCommandResolver;
        this.activeCommandResolver = null;
        resolve(cleanResponse);
      }
    }
  }

  /**
   * Sends a raw command string to the ELM327 device and waits for the response ending with '>'.
   * Automatically appends the required carriage return ('\r').
   * 
   * @param command Raw AT or OBD2 PID command (e.g. 'ATZ', '010C', 'ATRV')
   * @param timeoutMs Timeout in milliseconds before rejecting with 'NO DATA'
   */
  public async sendCommand(command: string, timeoutMs: number = 3000): Promise<string> {
    if (this.status !== 'CONNECTED' || !this.txCharacteristic) {
      throw new Error('ELM327 device is not connected.');
    }

    const formattedCommand = command.trim() + '\r';
    this.logPacket('TX', command.trim());

    return new Promise(async (resolve, reject) => {
      this.activeCommandResolver = resolve;

      this.commandTimeoutId = setTimeout(() => {
        if (this.activeCommandResolver) {
          this.activeCommandResolver = null;
          this.logPacket('ERR', `Command timed out (${timeoutMs}ms): ${command.trim()}`);
          resolve('NO DATA');
        }
      }, timeoutMs);

      try {
        const encoded = this.textEncoder.encode(formattedCommand);
        // Write to characteristic (prefer writeWithoutResponse if available for high speed)
        if (this.txCharacteristic.properties.writeWithoutResponse) {
          await this.txCharacteristic.writeValueWithoutResponse(encoded);
        } else {
          await this.txCharacteristic.writeValue(encoded);
        }
      } catch (err: any) {
        if (this.commandTimeoutId) {
          clearTimeout(this.commandTimeoutId);
          this.commandTimeoutId = null;
        }
        this.activeCommandResolver = null;
        this.logPacket('ERR', `GATT write error: ${err.message}`);
        reject(err);
      }
    });
  }

  /**
   * Disconnects the active Bluetooth link and releases GATT handles.
   */
  public async disconnect(): Promise<void> {
    if (this.commandTimeoutId) {
      clearTimeout(this.commandTimeoutId);
      this.commandTimeoutId = null;
    }
    this.activeCommandResolver = null;
    this.rxBuffer = '';

    if (this.rxCharacteristic) {
      try {
        await this.rxCharacteristic.stopNotifications();
      } catch (e) {
        // Ignore if already disconnected
      }
      this.rxCharacteristic = null;
    }
    this.txCharacteristic = null;

    if (this.gattServer && this.gattServer.connected) {
      this.gattServer.disconnect();
    }
    this.gattServer = null;

    if (this.device) {
      this.device.removeEventListener('gattserverdisconnected', this.onGattDisconnected);
      this.device = null;
    }

    this.updateStatus('DISCONNECTED', 'Bluetooth connection closed.');
    this.logPacket('INFO', 'Disconnected from ELM327 device.');
  }

  /**
   * GATT disconnect callback triggered when physical device disconnects.
   */
  private onGattDisconnected(): void {
    this.updateStatus('DISCONNECTED', 'Device disconnected from GATT server.');
    this.logPacket('INFO', 'Physical Bluetooth GATT link lost.');
    this.disconnect();
  }

  private updateStatus(newStatus: BluetoothConnectionStatus, message?: string): void {
    this.status = newStatus;
    this.statusListeners.forEach(cb => cb(newStatus, message));
  }

  private logPacket(direction: 'TX' | 'RX' | 'INFO' | 'ERR', payload: string): void {
    const packet: BluetoothPacket = {
      direction,
      timestamp: Date.now(),
      payload
    };
    this.packetListeners.forEach(cb => cb(packet));
  }

  // --- Public Event Listener Subscriptions ---

  public onStatusChange(callback: StatusListener): () => void {
    this.statusListeners.add(callback);
    return () => this.statusListeners.delete(callback);
  }

  public onPacket(callback: PacketListener): () => void {
    this.packetListeners.add(callback);
    return () => this.packetListeners.delete(callback);
  }

  public onRawStream(callback: RawStreamListener): () => void {
    this.rawStreamListeners.add(callback);
    return () => this.rawStreamListeners.delete(callback);
  }
}

// Export singleton instance for direct application usage
export const webBluetoothObdService = new WebBluetoothObdService();
