import React, { useState, useEffect } from 'react';
import { obdEngine } from '../../services/obdEngine';
import { 
  LiveTelemetry, 
  ConnectionState, 
  AdapterInfo, 
  LauncherSettings 
} from '../../types/obd';
import { DEFAULT_LAUNCHER_APPS, launchApp } from '../../services/launcherApps';
import { SplitGaugesDisplay } from '../cockpit/SplitGaugesDisplay';
import { SleekNavigationCockpit } from '../navigation/SleekNavigationCockpit';
import { LiveGraphsAndSensorsView } from '../cockpit/LiveGraphsAndSensorsView';
import { TripComputerView } from '../cockpit/TripComputerView';
import { SettingsView } from '../cockpit/SettingsView';
import { BluetoothConnectModal } from '../modals/BluetoothConnectModal';
import { DiagnosticsModal } from '../modals/DiagnosticsModal';
import { ApkExportModal } from '../modals/ApkExportModal';
import { InCarAudioDock } from '../media/InCarAudioDock';
import { inCarAudioService } from '../../services/inCarAudioService';
import { 
  Home, 
  Grid, 
  Navigation, 
  Radio, 
  Gauge, 
  Sliders, 
  Settings, 
  Bluetooth, 
  Play, 
  Pause, 
  SkipBack, 
  SkipForward, 
  Music, 
  Info, 
  Sparkles, 
  Layers, 
  Maximize2, 
  Minimize2, 
  Smartphone,
  AlertTriangle,
  Zap,
  Monitor
} from 'lucide-react';

export type ScreenResolutionPreset = 
  | 'FLUID' 
  | 'HU_1024_600'   // 7" & 9" standard Chinese head units
  | 'HU_1280_720'   // 9" & 10.1" 7862 / TS10 HD head units
  | 'HU_1920_720';  // 10.25" / 12.3" wide Android screens

export const ModernCarLauncher: React.FC = () => {
  const [telemetry, setTelemetry] = useState<LiveTelemetry>(obdEngine.getTelemetry());
  const [connectionState, setConnectionState] = useState<ConnectionState>(obdEngine.getConnectionState());
  const [adapterInfo, setAdapterInfo] = useState<AdapterInfo>(obdEngine.getAdapterInfo());

  // Screen resolution preset for testing Chinese Android head units
  const [resolutionPreset, setResolutionPreset] = useState<ScreenResolutionPreset>('FLUID');
  const [speedUnit, setSpeedUnit] = useState<'km/h' | 'mph'>('km/h');

  // Modals
  const [isConnectModalOpen, setIsConnectModalOpen] = useState<boolean>(false);
  const [isDiagnosticsModalOpen, setIsDiagnosticsModalOpen] = useState<boolean>(false);
  const [isApkModalOpen, setIsApkModalOpen] = useState<boolean>(false);
  const [isAppsDrawerOpen, setIsAppsDrawerOpen] = useState<boolean>(false);

  // Media Player State
  const [isPlaying, setIsPlaying] = useState<boolean>(true);
  const [isFullscreen, setIsFullscreen] = useState<boolean>(false);

  // Subscriptions to real OBD engine
  useEffect(() => {
    const unsubTelem = obdEngine.onTelemetry((t) => setTelemetry(t));
    const unsubConn = obdEngine.onConnection((s, a) => {
      setConnectionState(s);
      if (a) setAdapterInfo(a);
    });

    return () => {
      unsubTelem();
      unsubConn();
    };
  }, []);

  const toggleFullscreen = () => {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen().catch(() => {});
      setIsFullscreen(true);
    } else {
      if (document.exitFullscreen) {
        document.exitFullscreen().catch(() => {});
      }
      setIsFullscreen(false);
    }
  };

  const isConnected = connectionState === 'CONNECTED';
  const speedVal = telemetry.speed.status === 'LIVE' ? telemetry.speed.value : null;
  const rpmVal = telemetry.rpm.status === 'LIVE' ? telemetry.rpm.value : null;
  const coolantVal = telemetry.coolantTemp.status === 'LIVE' ? telemetry.coolantTemp.value : null;
  const fuelVal = telemetry.fuelLevel.status === 'LIVE' ? telemetry.fuelLevel.value : 62;
  const batteryVal = telemetry.batteryVoltage.status === 'LIVE' ? telemetry.batteryVoltage.value : (adapterInfo.voltage || null);
  const loadVal = telemetry.engineLoad.status === 'LIVE' ? telemetry.engineLoad.value : null;
  const throttleVal = telemetry.throttlePosition.status === 'LIVE' ? telemetry.throttlePosition.value : null;
  const gear = telemetry.gear;

  // Active Main Cockpit Page ('HOME' | 'GAUGES' | 'TRIP' | 'SETTINGS')
  const [activePage, setActivePage] = useState<'HOME' | 'GAUGES' | 'TRIP' | 'SETTINGS'>('HOME');

  // Container styling based on Chinese Android Head Unit resolution preset
  const getContainerStyle = () => {
    switch (resolutionPreset) {
      case 'HU_1024_600':
        return 'w-[1024px] h-[600px] border border-cyan-500/30 rounded-xl shadow-2xl overflow-hidden my-auto mx-auto';
      case 'HU_1280_720':
        return 'w-[1280px] h-[720px] border border-cyan-500/30 rounded-xl shadow-2xl overflow-hidden my-auto mx-auto';
      case 'HU_1920_720':
        return 'w-[1920px] h-[720px] border border-cyan-500/30 rounded-xl shadow-2xl overflow-hidden my-auto mx-auto';
      default:
        return 'w-screen h-screen overflow-hidden';
    }
  };

  return (
    <div className="w-screen h-screen bg-[#05070B] flex flex-col items-center justify-center overflow-hidden select-none">
      
      {/* Outer Shell respecting Head Unit Resolution */}
      <div className={`bg-[#070A0F] text-[#EAF5FA] font-sans flex flex-col justify-between ${getContainerStyle()}`}>
        
        {/* Main Content Area: Left Slim Dock + Main Cockpit Viewport */}
        <div className="flex-1 w-full flex overflow-hidden">
          
          {/* 1. Left Vertical In-Car Dock */}
          <aside className="w-14 sm:w-16 bg-[#090D14] border-r border-slate-800/90 flex flex-col items-center justify-between py-2 shrink-0 z-20">
            <div className="flex flex-col items-center gap-1.5 w-full">
              {/* Home (Permanent Split: Live Map + OBD Telemetry) */}
              <button
                onClick={() => setActivePage('HOME')}
                className={`w-11 h-11 rounded-xl flex flex-col items-center justify-center transition-all shadow-md ${
                  activePage === 'HOME'
                    ? 'bg-cyan-500/20 text-cyan-400 border border-cyan-500/40'
                    : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
                }`}
                title="Permanent Home (Live Map + Gauges)"
              >
                <Home className="w-5 h-5" />
                <span className="text-[8px] font-['Chakra_Petch'] font-bold">Home</span>
              </button>

              {/* Vehicle Telemetry & Live Graphs */}
              <button
                onClick={() => setActivePage('GAUGES')}
                className={`w-11 h-11 rounded-xl flex flex-col items-center justify-center transition-all ${
                  activePage === 'GAUGES'
                    ? 'bg-cyan-500/20 text-cyan-400 border border-cyan-500/40'
                    : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
                }`}
                title="Live Graphs & All 8 Sensors"
              >
                <Gauge className="w-5 h-5" />
                <span className="text-[8px] font-['Chakra_Petch'] font-bold">Gauges</span>
              </button>

              {/* ECU Diagnostics (DTC) */}
              <button
                onClick={() => setIsDiagnosticsModalOpen(true)}
                className="w-11 h-11 rounded-xl text-amber-400 hover:bg-amber-500/10 flex flex-col items-center justify-center transition-all"
                title="Scan Fault Codes (DTC)"
              >
                <AlertTriangle className="w-5 h-5" />
                <span className="text-[8px] font-['Chakra_Petch'] font-bold">Diag</span>
              </button>

              {/* Trip Computer */}
              <button
                onClick={() => setActivePage('TRIP')}
                className={`w-11 h-11 rounded-xl flex flex-col items-center justify-center transition-all ${
                  activePage === 'TRIP'
                    ? 'bg-teal-500/20 text-teal-300 border border-teal-500/40'
                    : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
                }`}
                title="Trip Computer & Odometer"
              >
                <Navigation className="w-5 h-5" />
                <span className="text-[8px] font-['Chakra_Petch'] font-bold">Trip</span>
              </button>

              {/* Waze Full App Launch */}
              <button
                onClick={() => window.open('https://waze.com/ul', '_blank')}
                className="w-11 h-11 rounded-xl text-[#33CCFF] hover:bg-[#33CCFF]/10 flex flex-col items-center justify-center transition-all"
                title="Launch Waze External Navigation"
              >
                <Navigation className="w-5 h-5 fill-current" />
                <span className="text-[8px] font-['Chakra_Petch'] font-bold">Waze</span>
              </button>

              {/* FM Radio */}
              <button
                onClick={() => {
                  inCarAudioService.setSource('RADIO');
                  inCarAudioService.launchNativeRadioApp();
                }}
                className="w-11 h-11 rounded-xl text-purple-400 hover:bg-purple-500/10 flex flex-col items-center justify-center transition-all"
                title="FM / AM Radio Tuner"
              >
                <Radio className="w-5 h-5" />
                <span className="text-[8px] font-['Chakra_Petch'] font-bold">Radio</span>
              </button>

              {/* Setup & Preferences */}
              <button
                onClick={() => setActivePage('SETTINGS')}
                className={`w-11 h-11 rounded-xl flex flex-col items-center justify-center transition-all ${
                  activePage === 'SETTINGS'
                    ? 'bg-cyan-500/20 text-cyan-400 border border-cyan-500/40'
                    : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
                }`}
                title="Launcher Preferences & Default Setup"
              >
                <Settings className="w-5 h-5" />
                <span className="text-[8px] font-['Chakra_Petch'] font-bold">Setup</span>
              </button>
            </div>

            {/* Bottom Dock Controls: Bluetooth Link + APK Build */}
            <div className="flex flex-col items-center gap-1.5 w-full">
              {/* Real Bluetooth ELM327 Link Button */}
              <button
                onClick={() => setIsConnectModalOpen(true)}
                className={`w-11 h-11 rounded-xl flex flex-col items-center justify-center transition-all ${
                  isConnected
                    ? 'bg-teal-500/20 text-teal-300 border border-teal-500/50 shadow-[0_0_10px_rgba(0,229,163,0.3)]'
                    : 'text-slate-400 hover:text-cyan-300 hover:bg-slate-800/60'
                }`}
                title={isConnected ? 'ELM327 Active · Tap for Terminal' : 'Pair Bluetooth ELM327'}
              >
                <div className="relative">
                  <Bluetooth className={`w-4 h-4 ${isConnected ? 'text-teal-400' : 'text-slate-400'}`} />
                  <span className={`absolute -top-1 -right-1 w-2 h-2 rounded-full ${isConnected ? 'bg-teal-400 animate-pulse' : 'bg-rose-500'}`} />
                </div>
                <span className="text-[8px] font-mono mt-0.5">{isConnected ? 'OBD' : 'BT'}</span>
              </button>

              {/* Android APK Modal */}
              <button
                onClick={() => setIsApkModalOpen(true)}
                className="w-11 h-11 rounded-xl text-slate-400 hover:text-teal-300 hover:bg-slate-800/60 flex flex-col items-center justify-center transition-all"
                title="Android APK Build Guide"
              >
                <Smartphone className="w-4 h-4 text-teal-400" />
                <span className="text-[8px] font-mono mt-0.5">APK</span>
              </button>
            </div>
          </aside>

          {/* 2. MAIN COCKPIT VIEWPORT (Page Switcher) */}
          <div className="flex-1 h-full flex flex-col overflow-hidden">
            {activePage === 'HOME' && (
              /* PERMANENT SPLIT COCKPIT (Dominant Live Waze Map + Authoritative Gauges) */
              <div className="flex-1 h-full flex flex-row gap-2 p-2 overflow-hidden">
                {/* DISPLAY 1 (LEFT 50%): AUTHORITATIVE SPEEDOMETER & REAL OBD2 GAUGES */}
                <div className="w-1/2 h-full flex flex-col overflow-hidden">
                  <SplitGaugesDisplay
                    speed={speedVal}
                    rpm={rpmVal}
                    coolant={coolantVal}
                    fuelLevel={fuelVal}
                    voltage={batteryVal}
                    load={loadVal}
                    throttle={throttleVal}
                    gear={gear}
                    speedUnit={speedUnit}
                    onToggleSpeedUnit={() => setSpeedUnit(u => u === 'km/h' ? 'mph' : 'km/h')}
                    isConnected={isConnected}
                    protocol={adapterInfo.protocol}
                  />
                </div>

                {/* DISPLAY 2 (RIGHT 50%): DEDICATED LIVE WAZE NAVIGATION & TURN-BY-TURN MAP */}
                <div className="w-1/2 h-full flex flex-col overflow-hidden">
                  <SleekNavigationCockpit speedKmH={speedVal} />
                </div>
              </div>
            )}

            {activePage === 'GAUGES' && (
              <LiveGraphsAndSensorsView
                telemetry={telemetry}
                speedUnit={speedUnit}
              />
            )}

            {activePage === 'TRIP' && (
              <TripComputerView
                currentSpeed={speedVal || 0}
                speedUnit={speedUnit}
              />
            )}

            {activePage === 'SETTINGS' && (
              <SettingsView
                speedUnit={speedUnit}
                onToggleSpeedUnit={() => setSpeedUnit(u => u === 'km/h' ? 'mph' : 'km/h')}
                onOpenBluetoothModal={() => setIsConnectModalOpen(true)}
                onOpenApkModal={() => setIsApkModalOpen(true)}
              />
            )}
          </div>
        </div>

        {/* 3. BOTTOM CAR LAUNCHER BAR: Audio Player + Head Unit Resolution Switcher */}
        <footer className="w-full bg-[#080C14] border-t border-slate-800/80 px-3 py-1.5 flex items-center justify-between shrink-0 z-20 text-xs font-mono">
          
          {/* Left/Center: Universal In-Car Audio Dock (Local Music vs FM Radio Tuner) */}
          <div className="flex-1 mr-3 overflow-hidden">
            <InCarAudioDock />
          </div>

          {/* Center/Right: Head Unit Resolution / Preset Switcher */}
          <div className="flex items-center gap-2">
            {/* Screen Preset Switcher for Chinese Android Head Units */}
            <div className="flex items-center gap-1 bg-[#0E1520] p-0.5 rounded-lg border border-slate-800 text-[10px]">
              <span className="text-slate-400 px-1.5 flex items-center gap-1 font-semibold">
                <Monitor className="w-3 h-3 text-cyan-400" /> Screen:
              </span>
              <button
                onClick={() => setResolutionPreset('FLUID')}
                className={`px-1.5 py-0.5 rounded transition-colors ${
                  resolutionPreset === 'FLUID' ? 'bg-cyan-500 text-slate-950 font-bold' : 'text-slate-400 hover:text-white'
                }`}
                title="Fill 100% of current browser or car display"
              >
                Auto Fluid
              </button>
              <button
                onClick={() => setResolutionPreset('HU_1024_600')}
                className={`px-1.5 py-0.5 rounded transition-colors ${
                  resolutionPreset === 'HU_1024_600' ? 'bg-cyan-500 text-slate-950 font-bold' : 'text-slate-400 hover:text-white'
                }`}
                title="7-inch / 9-inch 1024x600 Chinese Android Head Unit"
              >
                1024x600
              </button>
              <button
                onClick={() => setResolutionPreset('HU_1280_720')}
                className={`px-1.5 py-0.5 rounded transition-colors ${
                  resolutionPreset === 'HU_1280_720' ? 'bg-cyan-500 text-slate-950 font-bold' : 'text-slate-400 hover:text-white'
                }`}
                title="9-inch / 10.1-inch 1280x720 7862/TS10 HD Head Unit"
              >
                1280x720 HD
              </button>
              <button
                onClick={() => setResolutionPreset('HU_1920_720')}
                className={`px-1.5 py-0.5 rounded transition-colors ${
                  resolutionPreset === 'HU_1920_720' ? 'bg-cyan-500 text-slate-950 font-bold' : 'text-slate-400 hover:text-white'
                }`}
                title="10.25-inch / 12.3-inch Ultra-Wide Display"
              >
                1920x720 Wide
              </button>
            </div>

            {/* In-Dash Fullscreen Button */}
            <button
              onClick={toggleFullscreen}
              className="p-1.5 rounded-lg bg-[#0E1520] hover:bg-slate-800 border border-slate-800 text-slate-300 hover:text-white transition-colors"
              title={isFullscreen ? 'Exit Fullscreen' : 'Enter Fullscreen In-Dash Launcher'}
            >
              {isFullscreen ? <Minimize2 className="w-3.5 h-3.5" /> : <Maximize2 className="w-3.5 h-3.5" />}
            </button>
          </div>
        </footer>
      </div>

      {/* MODAL 1: Real Bluetooth / Serial ELM327 Link & Raw Terminal */}
      <BluetoothConnectModal
        isOpen={isConnectModalOpen}
        onClose={() => setIsConnectModalOpen(false)}
        connectionState={connectionState}
        adapterInfo={adapterInfo}
        onOpenGauges={() => setActivePage('GAUGES')}
        onOpenDiagnostics={() => setIsDiagnosticsModalOpen(true)}
      />

      {/* MODAL 2: Vehicle Diagnostics & DTC Code Reader */}
      <DiagnosticsModal
        isOpen={isDiagnosticsModalOpen}
        onClose={() => setIsDiagnosticsModalOpen(false)}
        connectionState={connectionState}
      />

      {/* MODAL 3: Native Android APK Build & Packaging Guide */}
      <ApkExportModal
        isOpen={isApkModalOpen}
        onClose={() => setIsApkModalOpen(false)}
      />

      {/* MODAL 4: In-Car All Apps Drawer */}
      {isAppsDrawerOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/85 backdrop-blur-md animate-in fade-in duration-200">
          <div className="bg-[#0E1520] border border-slate-700 w-full max-w-3xl rounded-2xl shadow-2xl p-6 flex flex-col max-h-[85vh] overflow-hidden">
            <div className="flex items-center justify-between border-b border-slate-800 pb-4 mb-4">
              <div className="flex items-center gap-2">
                <Grid className="w-5 h-5 text-cyan-400" />
                <h2 className="text-base font-bold font-['Chakra_Petch'] text-white">
                  ALL IN-CAR APPLICATIONS
                </h2>
              </div>
              <button
                onClick={() => setIsAppsDrawerOpen(false)}
                className="px-3 py-1 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-mono"
              >
                Close
              </button>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3.5 overflow-y-auto p-1">
              {DEFAULT_LAUNCHER_APPS.map((app) => (
                <button
                  key={app.id}
                  onClick={() => {
                    launchApp(app);
                    setIsAppsDrawerOpen(false);
                  }}
                  className="bg-[#080C14] hover:bg-slate-800/80 border border-slate-800 hover:border-slate-700 rounded-xl p-4 flex flex-col items-center text-center transition-all hover:scale-105 group shadow-lg"
                >
                  <div 
                    className="w-12 h-12 rounded-xl flex items-center justify-center mb-3 shadow-md group-hover:scale-110 transition-transform"
                    style={{ backgroundColor: `${app.color}25`, color: app.color, borderColor: `${app.color}50` }}
                  >
                    {app.id === 'waze' && <Navigation className="w-6 h-6 fill-current" />}
                    {app.id === 'google_maps' && <Navigation className="w-6 h-6" />}
                    {app.id === 'spotify' && <Music className="w-6 h-6" />}
                    {app.id === 'yt_music' && <Play className="w-6 h-6 fill-current" />}
                    {app.id === 'phone' && <Grid className="w-6 h-6" />}
                    {app.id === 'radio' && <Radio className="w-6 h-6" />}
                    {app.id === 'dashcam' && <Zap className="w-6 h-6" />}
                    {app.id === 'settings' && <Sliders className="w-6 h-6" />}
                  </div>
                  <span className="font-bold text-xs font-['Chakra_Petch'] text-slate-200">
                    {app.name}
                  </span>
                  <span className="text-[10px] text-slate-500 font-mono mt-1 line-clamp-2">
                    {app.description}
                  </span>
                </button>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
