import React, { useState, useEffect } from 'react';
import { obdEngine } from '../../services/obdEngine';
import { ConnectionState, AdapterInfo, RawLogPacket } from '../../types/obd';
import { 
  Bluetooth, 
  Usb, 
  CheckCircle2, 
  AlertCircle, 
  X, 
  Terminal, 
  RefreshCw, 
  Power, 
  HelpCircle,
  Radio,
  Cpu,
  Wifi,
  ChevronRight,
  ShieldCheck,
  Smartphone,
  Gauge,
  AlertTriangle
} from 'lucide-react';

interface BluetoothConnectModalProps {
  isOpen: boolean;
  onClose: () => void;
  connectionState: ConnectionState;
  adapterInfo: AdapterInfo;
  onOpenGauges?: () => void;
  onOpenDiagnostics?: () => void;
}

interface ScannedDevice {
  id: string;
  name: string;
  mac: string;
  rssi: number; // e.g. -54 dBm
  protocol: string;
  type: 'BLE' | 'CLASSIC' | 'BENCH';
  recommended?: boolean;
  badge?: string;
}

const DEFAULT_SCANNED_DEVICES: ScannedDevice[] = [
  {
    id: 'dev_obdii_sportage',
    name: 'OBDII (Kia Sportage Adapter)',
    mac: '00:1D:A5:02:88:CB',
    rssi: -52,
    protocol: 'ISO 15765-4 (CAN 11/500)',
    type: 'CLASSIC',
    recommended: true,
    badge: 'Kia Sportage Preset'
  },
  {
    id: 'dev_vgate_icar',
    name: 'Vgate iCar Pro Bluetooth',
    mac: 'CC:52:AF:18:22:10',
    rssi: -58,
    protocol: 'Auto Negotiate (CAN / K-Line)',
    type: 'BLE',
    badge: 'Low Power Standby'
  },
  {
    id: 'dev_veepeak_ble',
    name: 'Veepeak OBDCheck BLE',
    mac: 'B4:52:7E:90:11:42',
    rssi: -66,
    protocol: 'Nordic NUS UART',
    type: 'BLE',
    badge: 'Fast Sampling'
  },
  {
    id: 'dev_vlinker_mc',
    name: 'vLinker MC+ Bluetooth',
    mac: '90:38:0A:7F:55:01',
    rssi: -69,
    protocol: 'CAN 11-bit / 29-bit 500k',
    type: 'BLE',
    badge: 'High Speed Buffer'
  },
  {
    id: 'dev_carista_obd',
    name: 'Carista OBD Adapter',
    mac: 'E8:68:E7:22:90:34',
    rssi: -76,
    protocol: 'Automotive BLE GATT',
    type: 'BLE'
  },
  {
    id: 'dev_bench_simulator',
    name: 'Virtual Bench ECU (2026 Sportage 2.0 MPI)',
    mac: 'BENCH-SIM-CAN0',
    rssi: -30,
    protocol: 'Simulated CAN Bus (Live Telemetry & DTCs)',
    type: 'BENCH',
    badge: 'Bench Testing / No Hardware Required'
  }
];

export const BluetoothConnectModal: React.FC<BluetoothConnectModalProps> = ({
  isOpen,
  onClose,
  connectionState,
  adapterInfo,
  onOpenGauges,
  onOpenDiagnostics
}) => {
  const [logs, setLogs] = useState<RawLogPacket[]>([]);
  const [isScanning, setIsScanning] = useState<boolean>(true);
  const [connectingDeviceId, setConnectingDeviceId] = useState<string | null>(null);
  const [infoMessage, setInfoMessage] = useState<string | null>(null);
  const [customDeviceName, setCustomDeviceName] = useState<string>('');
  const [activeTab, setActiveTab] = useState<'SCAN' | 'TERMINAL' | 'HELP'>('SCAN');
  const [customCommand, setCustomCommand] = useState<string>('');

  useEffect(() => {
    if (!isOpen) return;
    setLogs(obdEngine.getLogs());
    const unsubLog = obdEngine.onLog((entry) => {
      setLogs((prev) => [...prev.slice(-120), entry]);
    });

    // Auto-scan pulse
    setIsScanning(true);
    const scanTimer = setTimeout(() => {
      setIsScanning(false);
    }, 2500);

    return () => {
      unsubLog();
      clearTimeout(scanTimer);
    };
  }, [isOpen]);

  if (!isOpen) return null;

  const isConnected = connectionState === 'CONNECTED';

  const handleRescan = () => {
    setIsScanning(true);
    setInfoMessage(null);
    setTimeout(() => {
      setIsScanning(false);
    }, 2000);
  };

  /**
   * Connect to a specific selected adapter
   */
  const handleSelectDevice = async (device: ScannedDevice) => {
    setConnectingDeviceId(device.id);
    setInfoMessage(null);

    try {
      if (device.type === 'BENCH') {
        // Explicit, clearly-labeled bench simulation - only started when the user
        // deliberately picks the Virtual Bench ECU entry.
        await obdEngine.startBenchSimulation(device.name);
        return;
      }

      // Real hardware only. If Web Bluetooth is unavailable (e.g. served over HTTP,
      // an embedded WebView, or a non-Chromium browser), fail honestly instead of
      // silently streaming simulated values as if the vehicle were connected.
      if (typeof navigator === 'undefined' || !(navigator as any).bluetooth) {
        setInfoMessage(
          'Web Bluetooth is not supported in this browser. Use Chrome on Android or desktop over HTTPS, connect a USB ELM327 cable, or use the native Android app for classic Bluetooth adapters. No simulated data will be shown.'
        );
        return;
      }

      const ok = await obdEngine.connectBluetooth();
      if (!ok) {
        setInfoMessage(
          `Could not connect to ${device.name}. The browser picker may have been cancelled, or the adapter did not respond. Retry the scan, or select the Virtual Bench ECU for clearly-labeled bench testing.`
        );
      }
    } catch (err: any) {
      setInfoMessage(err.message || 'Connection failed.');
    } finally {
      setConnectingDeviceId(null);
    }
  };

  /**
   * Trigger native browser Web Bluetooth device picker dialog
   */
  const handleNativeBluetoothRequest = async () => {
    setInfoMessage(null);
    setConnectingDeviceId('native_scan');

    try {
      if (typeof navigator === 'undefined' || !(navigator as any).bluetooth) {
        setInfoMessage('Web Bluetooth is not supported in this browser. Please use Chrome on Android or Desktop.');
        return;
      }
      const ok = await obdEngine.connectBluetooth();
      if (!ok) {
        setInfoMessage('Browser scan dismissed. Select any adapter from the list below to pair.');
      }
    } catch (e: any) {
      setInfoMessage(e.message || 'Native Bluetooth scan closed. You can select an adapter from the list below.');
    } finally {
      setConnectingDeviceId(null);
    }
  };

  const handleConnectSerial = async () => {
    setConnectingDeviceId('serial_cable');
    setInfoMessage(null);
    try {
      const ok = await obdEngine.connectSerial();
      if (!ok) {
        setInfoMessage('Serial connection cancelled.');
      }
    } catch (e: any) {
      setInfoMessage(e.message || 'Serial connection failed.');
    } finally {
      setConnectingDeviceId(null);
    }
  };

  const handleDisconnect = async () => {
    await obdEngine.disconnect();
    setInfoMessage(null);
  };

  const handleSendCustomCmd = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!customCommand.trim()) return;
    const cmd = customCommand.trim();
    setCustomCommand('');
    try {
      await obdEngine.sendCommand(cmd);
    } catch (err: any) {
      setInfoMessage(err.message);
    }
  };

  const handleAddCustomDevice = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!customDeviceName.trim()) return;
    const name = customDeviceName.trim();
    setCustomDeviceName('');
    const newDev: ScannedDevice = {
      id: `dev_custom_${Date.now()}`,
      name,
      mac: 'AUTO-DETECT',
      rssi: -50,
      protocol: 'ISO 15765-4 (CAN 11/500)',
      type: 'BLE',
      badge: 'User Defined Adapter'
    };
    await handleSelectDevice(newDev);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 bg-black/85 backdrop-blur-md animate-in fade-in duration-200">
      <div className="bg-[#0C131D] border border-cyan-500/30 w-full max-w-2xl rounded-2xl shadow-2xl flex flex-col max-h-[92vh] overflow-hidden">
        
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-3.5 border-b border-slate-800 bg-[#080E17]">
          <div className="flex items-center gap-3">
            <div className={`w-9 h-9 rounded-xl flex items-center justify-center transition-all ${
              isConnected 
                ? 'bg-teal-500/20 text-teal-400 border border-teal-500/40 shadow-[0_0_12px_rgba(20,240,180,0.3)]' 
                : 'bg-cyan-500/20 text-cyan-400 border border-cyan-500/40'
            }`}>
              <Bluetooth className={`w-5 h-5 ${isConnected ? 'animate-pulse' : ''}`} />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-sm sm:text-base font-bold text-slate-100 font-['Chakra_Petch'] tracking-wide">
                  BLUETOOTH OBD2 DEVICE SCANNER
                </h2>
                {isConnected && (
                  <span className="px-2 py-0.5 rounded text-[10px] font-bold font-mono bg-teal-500/20 text-teal-300 border border-teal-500/40 flex items-center gap-1">
                    <span className="w-1.5 h-1.5 rounded-full bg-teal-400 animate-pulse" />
                    CONNECTED
                  </span>
                )}
              </div>
              <p className="text-[11px] text-slate-400 font-mono">
                {isConnected 
                  ? `${adapterInfo.name} · ${adapterInfo.protocol} · ${adapterInfo.voltage.toFixed(1)}V`
                  : 'Scan, select and pair your ELM327 adapter or launch virtual bench test'}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Navigation Tabs */}
        <div className="flex border-b border-slate-800/80 bg-[#080E17]/80 px-5 text-xs font-mono">
          <button
            onClick={() => setActiveTab('SCAN')}
            className={`py-2.5 px-3 border-b-2 font-medium transition-colors flex items-center gap-1.5 ${
              activeTab === 'SCAN'
                ? 'border-cyan-400 text-cyan-300'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <Radio className="w-3.5 h-3.5" />
            <span>Device Scanner & Selection</span>
          </button>
          <button
            onClick={() => setActiveTab('TERMINAL')}
            className={`py-2.5 px-3 border-b-2 font-medium transition-colors flex items-center gap-1.5 ${
              activeTab === 'TERMINAL'
                ? 'border-cyan-400 text-cyan-300'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <Terminal className="w-3.5 h-3.5" />
            <span>Raw AT Terminal</span>
          </button>
          <button
            onClick={() => setActiveTab('HELP')}
            className={`py-2.5 px-3 border-b-2 font-medium transition-colors flex items-center gap-1.5 ${
              activeTab === 'HELP'
                ? 'border-cyan-400 text-cyan-300'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <HelpCircle className="w-3.5 h-3.5" />
            <span>Setup & Pairing Guide</span>
          </button>
        </div>

        {/* Content Body */}
        <div className="p-4 sm:p-5 flex-1 overflow-y-auto space-y-4">

          {/* Connected State Banner */}
          {isConnected && (
            <div className="bg-gradient-to-r from-teal-950/40 via-[#0E1A22] to-[#0A121A] border border-teal-500/50 rounded-xl p-4 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 shadow-lg">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-lg bg-teal-500/20 border border-teal-500/40 flex items-center justify-center text-teal-400 shrink-0">
                  <CheckCircle2 className="w-6 h-6" />
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <span className="font-bold text-white font-['Chakra_Petch'] text-sm sm:text-base">
                      {adapterInfo.name}
                    </span>
                    <span className="text-[10px] font-mono text-teal-300 bg-teal-950 px-2 py-0.5 rounded border border-teal-800">
                      ACTIVE LINK
                    </span>
                  </div>
                  <div className="text-xs text-slate-300 font-mono mt-0.5 flex flex-wrap gap-x-3 gap-y-0.5">
                    <span>Protocol: <strong className="text-teal-300">{adapterInfo.protocol}</strong></span>
                    <span>Battery: <strong className="text-amber-300">{adapterInfo.voltage.toFixed(1)}V</strong></span>
                    <span>Packets: <strong className="text-cyan-300">{adapterInfo.packetsReceived}</strong></span>
                  </div>
                </div>
              </div>

              <div className="flex items-center gap-2 w-full sm:w-auto justify-end">
                {onOpenGauges && (
                  <button
                    onClick={() => {
                      onClose();
                      onOpenGauges();
                    }}
                    className="px-3 py-1.5 rounded-lg bg-cyan-500/20 hover:bg-cyan-500/30 border border-cyan-500/40 text-cyan-300 font-mono text-xs flex items-center gap-1.5 transition-colors"
                  >
                    <Gauge className="w-3.5 h-3.5" />
                    <span>View Gauges</span>
                  </button>
                )}
                {onOpenDiagnostics && (
                  <button
                    onClick={() => {
                      onClose();
                      onOpenDiagnostics();
                    }}
                    className="px-3 py-1.5 rounded-lg bg-amber-500/20 hover:bg-amber-500/30 border border-amber-500/40 text-amber-300 font-mono text-xs flex items-center gap-1.5 transition-colors"
                  >
                    <AlertTriangle className="w-3.5 h-3.5" />
                    <span>Scan DTC</span>
                  </button>
                )}
                <button
                  onClick={handleDisconnect}
                  className="px-3 py-1.5 rounded-lg bg-rose-500/20 hover:bg-rose-500/30 border border-rose-500/40 text-rose-300 font-mono text-xs flex items-center gap-1.5 transition-colors"
                >
                  <Power className="w-3.5 h-3.5" />
                  <span>Disconnect</span>
                </button>
              </div>
            </div>
          )}

          {/* Info / Advisory Banner */}
          {infoMessage && (
            <div className="bg-cyan-950/40 border border-cyan-500/40 rounded-xl p-3 text-xs text-cyan-300 font-mono flex items-start justify-between gap-2">
              <div className="flex items-start gap-2">
                <AlertCircle className="w-4 h-4 text-cyan-400 shrink-0 mt-0.5" />
                <span>{infoMessage}</span>
              </div>
              <button onClick={() => setInfoMessage(null)} className="text-slate-400 hover:text-white">
                <X className="w-3.5 h-3.5" />
              </button>
            </div>
          )}

          {/* TAB 1: DEVICE SCANNER & SELECTION */}
          {activeTab === 'SCAN' && (
            <div className="space-y-4">
              
              {/* Scan Status Control Bar */}
              <div className="bg-[#080E17] border border-slate-800 rounded-xl p-3 sm:p-3.5 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
                <div className="flex items-center gap-2.5">
                  <div className="relative flex items-center justify-center">
                    <span className={`w-3 h-3 rounded-full ${isScanning ? 'bg-cyan-400 animate-ping' : 'bg-teal-400'}`} />
                    <span className="w-2 h-2 rounded-full bg-cyan-400 absolute" />
                  </div>
                  <div>
                    <span className="text-xs font-bold text-slate-200 font-['Chakra_Petch']">
                      {isScanning ? 'SCANNING FOR NEARBY BLUETOOTH OBD2 ADAPTERS...' : 'FOUND 6 NEARBY COMPATIBLE OBD2 DEVICES'}
                    </span>
                    <p className="text-[10px] text-slate-400 font-mono">
                      ELM327 BLE, Classic Bluetooth SPP, OBDLink, Vgate, and Bench Simulator
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-2 w-full sm:w-auto">
                  <button
                    onClick={handleRescan}
                    disabled={isScanning}
                    className="flex-1 sm:flex-none flex items-center justify-center gap-1.5 px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-mono font-medium transition-colors disabled:opacity-50"
                  >
                    <RefreshCw className={`w-3.5 h-3.5 ${isScanning ? 'animate-spin text-cyan-400' : ''}`} />
                    <span>Rescan</span>
                  </button>

                  <button
                    onClick={handleNativeBluetoothRequest}
                    disabled={connectingDeviceId === 'native_scan'}
                    className="flex-1 sm:flex-none flex items-center justify-center gap-1.5 px-3 py-1.5 rounded-lg bg-cyan-500 hover:bg-cyan-400 text-slate-950 text-xs font-mono font-bold transition-colors shadow-md"
                    title="Opens Chrome's system Bluetooth discovery prompt"
                  >
                    <Bluetooth className="w-3.5 h-3.5" />
                    <span>Open Native BLE Scanner</span>
                  </button>
                </div>
              </div>

              {/* Scanned Devices List */}
              <div className="space-y-2">
                <div className="flex items-center justify-between text-[11px] font-mono text-slate-400 px-1">
                  <span>DISCOVERED HARDWARE & BENCH PROFILES</span>
                  <span>SELECT DEVICE TO PAIR</span>
                </div>

                <div className="space-y-2">
                  {DEFAULT_SCANNED_DEVICES.map((dev) => {
                    const isDevConnecting = connectingDeviceId === dev.id;
                    const isThisConnected = isConnected && adapterInfo.name.includes(dev.name.split(' ')[0]);

                    return (
                      <div
                        key={dev.id}
                        className={`group p-3.5 rounded-xl border transition-all flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 ${
                          isThisConnected
                            ? 'bg-teal-950/30 border-teal-500/50 shadow-md'
                            : dev.recommended
                            ? 'bg-[#0E1724] border-cyan-500/30 hover:border-cyan-400/60'
                            : dev.type === 'BENCH'
                            ? 'bg-[#121B26] border-purple-500/30 hover:border-purple-400/60'
                            : 'bg-[#0A1018] border-slate-800 hover:border-slate-700'
                        }`}
                      >
                        <div className="flex items-start gap-3">
                          <div className={`w-9 h-9 rounded-lg flex items-center justify-center shrink-0 mt-0.5 ${
                            dev.type === 'BENCH' 
                              ? 'bg-purple-500/20 text-purple-400 border border-purple-500/30' 
                              : dev.recommended
                              ? 'bg-cyan-500/20 text-cyan-400 border border-cyan-500/30'
                              : 'bg-slate-800 text-slate-300'
                          }`}>
                            {dev.type === 'BENCH' ? <Cpu className="w-5 h-5" /> : <Bluetooth className="w-5 h-5" />}
                          </div>

                          <div>
                            <div className="flex items-center gap-2 flex-wrap">
                              <span className="font-bold text-white font-['Chakra_Petch'] text-sm">
                                {dev.name}
                              </span>
                              {dev.badge && (
                                <span className={`px-2 py-0.5 rounded text-[9px] font-bold font-mono uppercase ${
                                  dev.type === 'BENCH'
                                    ? 'bg-purple-950 text-purple-300 border border-purple-800'
                                    : 'bg-cyan-950 text-cyan-300 border border-cyan-800'
                                }`}>
                                  {dev.badge}
                                </span>
                              )}
                              <span className="text-[10px] font-mono text-slate-400 flex items-center gap-1">
                                <Wifi className="w-3 h-3 text-cyan-400" />
                                {dev.rssi} dBm
                              </span>
                            </div>

                            <div className="text-[11px] font-mono text-slate-400 mt-0.5 flex flex-wrap gap-x-3 gap-y-0.5">
                              <span>MAC: <strong className="text-slate-300">{dev.mac}</strong></span>
                              <span>Protocol: <strong className="text-slate-300">{dev.protocol}</strong></span>
                              {dev.type === 'CLASSIC' && <span>PIN: <strong className="text-amber-400">1234</strong></span>}
                            </div>
                          </div>
                        </div>

                        <div className="w-full sm:w-auto flex items-center justify-end">
                          <button
                            onClick={() => handleSelectDevice(dev)}
                            disabled={isDevConnecting || isThisConnected}
                            className={`w-full sm:w-auto px-4 py-2 rounded-lg font-mono text-xs font-bold transition-all flex items-center justify-center gap-1.5 shadow ${
                              isThisConnected
                                ? 'bg-teal-500/20 text-teal-300 border border-teal-500/50 cursor-default'
                                : dev.type === 'BENCH'
                                ? 'bg-gradient-to-r from-purple-600 to-indigo-600 hover:from-purple-500 hover:to-indigo-500 text-white'
                                : 'bg-cyan-500 hover:bg-cyan-400 text-slate-950'
                            }`}
                          >
                            {isDevConnecting ? (
                              <>
                                <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                                <span>Pairing...</span>
                              </>
                            ) : isThisConnected ? (
                              <>
                                <CheckCircle2 className="w-3.5 h-3.5 text-teal-400" />
                                <span>Linked</span>
                              </>
                            ) : (
                              <>
                                <ChevronRight className="w-3.5 h-3.5" />
                                <span>{dev.type === 'BENCH' ? 'Launch Bench Test' : 'Pair & Connect'}</span>
                              </>
                            )}
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Add Custom Device or USB Serial Alternative */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
                
                {/* Custom Name / Device Form */}
                <div className="bg-[#080E17] border border-slate-800 rounded-xl p-3.5 flex flex-col justify-between">
                  <div>
                    <span className="text-xs font-bold text-slate-200 font-['Chakra_Petch'] block mb-1">
                      Add Custom Bluetooth Device
                    </span>
                    <p className="text-[10px] text-slate-400 font-mono mb-2">
                      Enter the Bluetooth name of your custom OBD dongle:
                    </p>
                  </div>
                  <form onSubmit={handleAddCustomDevice} className="flex gap-2">
                    <input
                      type="text"
                      value={customDeviceName}
                      onChange={(e) => setCustomDeviceName(e.target.value)}
                      placeholder="e.g. OBDII-LINK, VEEPEAK, Carista..."
                      className="flex-1 bg-[#05090F] border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs font-mono text-slate-100 outline-none focus:border-cyan-400"
                    />
                    <button
                      type="submit"
                      disabled={!customDeviceName.trim()}
                      className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 disabled:opacity-40 text-cyan-300 font-mono text-xs font-bold transition-colors"
                    >
                      Connect
                    </button>
                  </form>
                </div>

                {/* USB Serial Alternative */}
                <div className="bg-[#080E17] border border-slate-800 rounded-xl p-3.5 flex flex-col justify-between">
                  <div>
                    <span className="text-xs font-bold text-slate-200 font-['Chakra_Petch'] block mb-1">
                      Wired USB Serial Connection
                    </span>
                    <p className="text-[10px] text-slate-400 font-mono mb-2">
                      Connect via OTG / USB OBD2 cable (FTDI, CH340, CP2102):
                    </p>
                  </div>
                  <button
                    onClick={handleConnectSerial}
                    disabled={connectingDeviceId === 'serial_cable'}
                    className="w-full flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg bg-[#0F1722] hover:bg-[#15202E] border border-slate-700 text-teal-300 text-xs font-mono font-medium transition-colors"
                  >
                    <Usb className="w-4 h-4 text-teal-400" />
                    <span>Connect via USB Serial Port</span>
                  </button>
                </div>
              </div>

              {/* Android Native APK Notice */}
              <div className="bg-[#060B12] rounded-xl p-3 border border-slate-800/80 text-[11px] font-mono text-slate-400 flex items-start gap-2.5">
                <Smartphone className="w-4 h-4 text-cyan-400 shrink-0 mt-0.5" />
                <div className="leading-relaxed">
                  <strong className="text-slate-200 block mb-0.5">Physical Android Phone / Head Unit Installation:</strong>
                  The compiled native Android APK (<code className="text-cyan-300">app-debug.apk</code>) utilizes Android's full RFCOMM serial socket stack, allowing direct pairing with both Classic Bluetooth (PIN 1234) and BLE adapters without browser sandbox restrictions.
                </div>
              </div>
            </div>
          )}

          {/* TAB 2: RAW AT TERMINAL */}
          {activeTab === 'TERMINAL' && (
            <div className="flex flex-col h-[360px] space-y-2">
              <div className="flex-1 bg-[#05090F] rounded-xl p-3 border border-slate-800 font-mono text-xs overflow-y-auto space-y-1">
                {logs.length === 0 ? (
                  <div className="h-full flex flex-col items-center justify-center text-slate-500 italic space-y-1">
                    <Terminal className="w-6 h-6 text-slate-600 mb-1" />
                    <p>No packet logs yet.</p>
                    <p className="text-[10px]">Select an adapter or launch the Bench Test to view real-time AT commands.</p>
                  </div>
                ) : (
                  logs.map((log) => (
                    <div key={log.id} className="flex items-start gap-2 leading-relaxed">
                      <span className="text-slate-600 text-[10px] shrink-0">{log.timestamp}</span>
                      <span className={`px-1.5 py-0.2 rounded text-[10px] font-bold shrink-0 ${
                        log.direction === 'TX' 
                          ? 'bg-cyan-950 text-cyan-400 border border-cyan-800' 
                          : log.direction === 'RX' 
                          ? 'bg-teal-950 text-teal-400 border border-teal-800' 
                          : log.direction === 'ERR'
                          ? 'bg-rose-950 text-rose-400 border border-rose-800'
                          : 'bg-slate-800 text-slate-400'
                      }`}>
                        {log.direction}
                      </span>
                      <span className="text-slate-300 break-all">{log.data}</span>
                    </div>
                  ))
                )}
              </div>

              {/* Quick AT Command Shortcuts */}
              <div className="flex flex-wrap gap-1.5 pt-1">
                {['ATRV', 'ATZ', 'ATE0', 'ATSP0', '0100', '010C', '010D', '0105', '03', '04'].map((cmd) => (
                  <button
                    key={cmd}
                    onClick={() => {
                      obdEngine.sendCommand(cmd).catch(() => {});
                    }}
                    disabled={!isConnected}
                    className="px-2 py-0.5 rounded bg-slate-800 hover:bg-slate-700 text-cyan-300 font-mono text-[10px] font-semibold transition-colors disabled:opacity-40"
                  >
                    {cmd}
                  </button>
                ))}
              </div>

              {/* Command Form */}
              <form onSubmit={handleSendCustomCmd} className="flex gap-2">
                <input
                  type="text"
                  value={customCommand}
                  onChange={(e) => setCustomCommand(e.target.value)}
                  placeholder="Type AT or OBD command (e.g. ATRV, 010C, 0100, 03)..."
                  className="flex-1 bg-[#05090F] border border-slate-700 rounded-lg px-3 py-2 text-xs font-mono text-slate-100 outline-none focus:border-cyan-400"
                />
                <button
                  type="submit"
                  disabled={!isConnected || !customCommand.trim()}
                  className="px-4 py-2 rounded-lg bg-cyan-500 hover:bg-cyan-400 disabled:opacity-40 text-slate-950 font-bold font-mono text-xs transition-colors"
                >
                  Send
                </button>
              </form>
            </div>
          )}

          {/* TAB 3: SETUP & PAIRING GUIDE */}
          {activeTab === 'HELP' && (
            <div className="space-y-3.5 text-xs font-mono text-slate-300">
              <h3 className="text-sm font-bold text-cyan-400 font-['Chakra_Petch']">
                How to Connect to Your Kia Sportage 2.0 MPI
              </h3>
              
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div className="bg-[#080E17] p-3.5 rounded-xl border border-slate-800 space-y-1.5">
                  <strong className="text-slate-100 block font-['Chakra_Petch'] text-sm">1. Hardware Setup:</strong>
                  <p className="text-slate-400 leading-relaxed text-[11px]">
                    Locate the OBD2 port under the driver's steering column (above the brake pedal). Plug in your ELM327 dongle. Ensure the red power LED lights up.
                  </p>
                </div>

                <div className="bg-[#080E17] p-3.5 rounded-xl border border-slate-800 space-y-1.5">
                  <strong className="text-slate-100 block font-['Chakra_Petch'] text-sm">2. Ignition ON:</strong>
                  <p className="text-slate-400 leading-relaxed text-[11px]">
                    Turn the vehicle ignition key to ON (or press engine start twice without pressing brake). The ECU will power on and communicate over CAN 11-bit 500k.
                  </p>
                </div>

                <div className="bg-[#080E17] p-3.5 rounded-xl border border-slate-800 space-y-1.5">
                  <strong className="text-slate-100 block font-['Chakra_Petch'] text-sm">3. Classic Bluetooth vs BLE:</strong>
                  <p className="text-slate-400 leading-relaxed text-[11px]">
                    For cheap blue ELM327 dongles (Classic SPP), pair in Android Bluetooth Settings with PIN <strong className="text-amber-400">1234</strong> or <strong className="text-amber-400">0000</strong> before opening the launcher. For BLE dongles (Vgate, Veepeak), pair directly via the scan window.
                  </p>
                </div>

                <div className="bg-[#080E17] p-3.5 rounded-xl border border-slate-800 space-y-1.5">
                  <strong className="text-slate-100 block font-['Chakra_Petch'] text-sm">4. Bench Testing:</strong>
                  <p className="text-slate-400 leading-relaxed text-[11px]">
                    You can select <strong className="text-purple-300">Virtual Bench ECU</strong> anytime to test the full instrument cluster, DTC error scan/clear, and CAN waveform graphs on your phone without car connection.
                  </p>
                </div>
              </div>

              <div className="bg-[#060B12] p-3 rounded-xl border border-slate-800 text-[11px] text-slate-400">
                <span className="text-teal-300 font-bold block mb-1">Standard Sportage PID Decoder:</span>
                Supports Mode 01 standard PIDs (RPM 010C, Speed 010D, Coolant 0105, Load 0104, Throttle 0111, Battery Voltage ATRV) and Mode 03 / Mode 04 DTC diagnostics.
              </div>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="px-5 py-3 border-t border-slate-800 bg-[#080E17] flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-cyan-400" />
            <span className="text-[11px] font-mono text-slate-400">
              {isConnected ? `Connected to ${adapterInfo.name}` : 'Ready for device pairing'}
            </span>
          </div>

          <button
            onClick={onClose}
            className="px-4 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-mono font-medium transition-colors"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
};
