import React, { useState, useEffect } from 'react';
import { obdEngine } from '../../services/obdEngine';
import { 
  LiveTelemetry, 
  ConnectionState, 
  AdapterInfo, 
  LauncherSettings,
  LauncherApp 
} from '../../types/obd';
import { DEFAULT_LAUNCHER_APPS, launchApp } from '../../services/launcherApps';
import { BluetoothConnectModal } from '../modals/BluetoothConnectModal';
import { DiagnosticsModal } from '../modals/DiagnosticsModal';
import { ApkExportModal } from '../modals/ApkExportModal';
import { WazeMiniNavWindow } from '../navigation/WazeMiniNavWindow';
import { 
  Bluetooth, 
  Navigation, 
  Music, 
  Phone, 
  AlertTriangle, 
  Maximize2, 
  Minimize2, 
  Search, 
  MapPin, 
  Radio, 
  Zap, 
  Thermometer, 
  Gauge, 
  Grid, 
  Terminal, 
  SlidersHorizontal, 
  Play, 
  Pause, 
  SkipForward, 
  SkipBack, 
  ArrowUpRight,
  ShieldCheck,
  Power,
  Smartphone
} from 'lucide-react';

export const CarLauncherDashboard: React.FC = () => {
  const [telemetry, setTelemetry] = useState<LiveTelemetry>(obdEngine.getTelemetry());
  const [connectionState, setConnectionState] = useState<ConnectionState>(obdEngine.getConnectionState());
  const [adapterInfo, setAdapterInfo] = useState<AdapterInfo>(obdEngine.getAdapterInfo());
  
  // Modals
  const [isConnectModalOpen, setIsConnectModalOpen] = useState<boolean>(false);
  const [isDiagnosticsModalOpen, setIsDiagnosticsModalOpen] = useState<boolean>(false);
  const [isApkModalOpen, setIsApkModalOpen] = useState<boolean>(false);
  const [isAppsDrawerOpen, setIsAppsDrawerOpen] = useState<boolean>(false);

  // Launcher state
  const [isFullscreen, setIsFullscreen] = useState<boolean>(false);
  const [isPlayingMusic, setIsPlayingMusic] = useState<boolean>(false);
  const [currentTime, setCurrentTime] = useState<string>('');
  const [currentDate, setCurrentDate] = useState<string>('');
  
  const [settings, setSettings] = useState<LauncherSettings>({
    speedUnit: 'km/h',
    tempUnit: '°C',
    keepScreenOn: true,
    pollingIntervalMs: 100,
    defaultNavApp: 'waze',
    themeAccent: 'cyan'
  });

  // Real-time telemetry subscriptions
  useEffect(() => {
    const unsubTelem = obdEngine.onTelemetry((t) => setTelemetry(t));
    const unsubConn = obdEngine.onConnection((s, a) => {
      setConnectionState(s);
      if (a) setAdapterInfo(a);
    });

    const updateClock = () => {
      const now = new Date();
      setCurrentTime(now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }));
      setCurrentDate(now.toLocaleDateString([], { weekday: 'short', month: 'short', day: 'numeric' }));
    };
    updateClock();
    const clockTimer = setInterval(updateClock, 1000);

    return () => {
      unsubTelem();
      unsubConn();
      clearInterval(clockTimer);
    };
  }, []);

  // Fullscreen toggle for Android head-unit / vehicle in-dash experience
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

  // Helper values
  const isConnected = connectionState === 'CONNECTED';
  const speedVal = telemetry.speed.status === 'LIVE' ? telemetry.speed.value : null;
  const displaySpeed = speedVal !== null ? (settings.speedUnit === 'mph' ? Math.round(speedVal * 0.621371) : speedVal) : '--';

  const rpmVal = telemetry.rpm.status === 'LIVE' ? telemetry.rpm.value : null;
  const coolantVal = telemetry.coolantTemp.status === 'LIVE' ? telemetry.coolantTemp.value : null;
  const throttleVal = telemetry.throttlePosition.status === 'LIVE' ? telemetry.throttlePosition.value : null;
  const loadVal = telemetry.engineLoad.status === 'LIVE' ? telemetry.engineLoad.value : null;
  const batteryVal = telemetry.batteryVoltage.status === 'LIVE' ? telemetry.batteryVoltage.value : (adapterInfo.voltage || null);
  const gear = telemetry.gear;

  return (
    <div className="w-screen h-screen bg-[#0B1118] text-[#EAF5FA] font-sans flex flex-col justify-between overflow-hidden select-none">
      
      {/* 1. TOP AUTOMOTIVE LAUNCHER STATUS BAR */}
      <header className="w-full bg-[#0E1721] border-b border-slate-800/90 px-4 py-2 flex items-center justify-between gap-3 shrink-0 z-20">
        {/* Left: Digital Clock & Date */}
        <div className="flex items-center gap-3">
          <div className="flex flex-col">
            <span className="text-xl sm:text-2xl font-bold font-['Chakra_Petch'] tracking-wide text-white leading-none">
              {currentTime}
            </span>
            <span className="text-[11px] font-mono text-slate-400 mt-0.5">
              {currentDate} · GPS Connected
            </span>
          </div>

          <div className="h-6 w-px bg-slate-800 hidden sm:block" />

          {/* Vehicle Profile Pill */}
          <div className="hidden md:flex items-center gap-2">
            <span className="font-bold text-xs font-['Chakra_Petch'] text-cyan-400 uppercase tracking-wider">
              KIA SPORTAGE 2.0 MPI AWD
            </span>
            <span className="text-slate-600">·</span>
            <span className="text-[11px] font-mono text-slate-400">
              {isConnected ? adapterInfo.protocol : 'OBD2 Standby'}
            </span>
          </div>
        </div>

        {/* Center: Real Bluetooth Connection Pill */}
        <div className="flex items-center gap-2">
          {isConnected ? (
            <button
              onClick={() => setIsConnectModalOpen(true)}
              className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-teal-500/15 border border-teal-500/40 text-teal-300 text-xs font-mono font-medium hover:bg-teal-500/25 transition-all shadow-sm"
              title="Click to view adapter telemetry & raw terminal"
            >
              <span className="w-2 h-2 rounded-full bg-teal-400 animate-pulse" />
              <span className="font-bold">ELM327 CONNECTED</span>
              {batteryVal && <span className="text-teal-200">· {batteryVal.toFixed(1)}V</span>}
            </button>
          ) : (
            <button
              onClick={() => setIsConnectModalOpen(true)}
              className={`flex items-center gap-2 px-3.5 py-1.5 rounded-full text-xs font-mono font-semibold transition-all shadow-md ${
                connectionState === 'CONNECTING' || connectionState === 'PAIRING' || connectionState === 'INITIALIZING_ADAPTER'
                  ? 'bg-amber-500/20 border border-amber-500/40 text-amber-300 animate-pulse'
                  : 'bg-cyan-500/20 hover:bg-cyan-500/30 border border-cyan-500/50 text-cyan-300 hover:scale-105'
              }`}
            >
              <Bluetooth className="w-4 h-4 text-cyan-400 animate-bounce" />
              <span>
                {connectionState === 'PAIRING'
                  ? 'PAIRING ADAPTER...'
                  : connectionState === 'CONNECTING'
                  ? 'CONNECTING...'
                  : connectionState === 'INITIALIZING_ADAPTER'
                  ? 'INITIALIZING ELM...'
                  : 'CONNECT BLUETOOTH OBD2'}
              </span>
            </button>
          )}
        </div>

        {/* Right: Quick Controls & Modals */}
        <div className="flex items-center gap-2">
          {/* APK Export Button */}
          <button
            onClick={() => setIsApkModalOpen(true)}
            className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg bg-[#14212B] hover:bg-slate-800 border border-teal-500/30 text-teal-300 text-xs font-mono transition-colors"
            title="Download / Build Android APK"
          >
            <Smartphone className="w-3.5 h-3.5 text-teal-400" />
            <span className="hidden sm:inline font-bold">Android APK</span>
          </button>

          {/* Quick DTC Scanner Shortcut */}
          <button
            onClick={() => setIsDiagnosticsModalOpen(true)}
            className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg bg-[#14212B] hover:bg-slate-800 border border-slate-800 text-slate-300 text-xs font-mono transition-colors"
            title="Scan vehicle diagnostic trouble codes"
          >
            <AlertTriangle className="w-3.5 h-3.5 text-amber-400" />
            <span className="hidden sm:inline">DTC Scan</span>
          </button>

          {/* Fullscreen Button */}
          <button
            onClick={toggleFullscreen}
            className="p-1.5 rounded-lg bg-[#14212B] hover:bg-slate-800 border border-slate-800 text-slate-300 hover:text-white transition-colors"
            title={isFullscreen ? 'Exit Fullscreen' : 'Enter In-Dash Fullscreen'}
          >
            {isFullscreen ? <Minimize2 className="w-4 h-4" /> : <Maximize2 className="w-4 h-4" />}
          </button>
        </div>
      </header>

      {/* 2. MAIN COCKPIT DASHBOARD WIDGETS */}
      <main className="flex-1 w-full p-3 sm:p-4 grid grid-cols-1 lg:grid-cols-12 gap-3 sm:gap-4 overflow-y-auto">
        
        {/* Left Widget: All-the-time Small Live Waze Navigation Window (4 cols) */}
        <div className="lg:col-span-5 h-[340px] lg:h-full min-h-[300px]">
          <WazeMiniNavWindow />
        </div>

        {/* Center Widget: Live Speedometer & Tachometer Cockpit (4 cols) */}
        <div className="lg:col-span-4 bg-gradient-to-b from-[#14212B] to-[#0E1721] border border-slate-800 rounded-2xl p-5 flex flex-col justify-between shadow-2xl relative overflow-hidden min-h-[300px]">
          
          {/* Top Dial Info */}
          <div className="flex items-center justify-between border-b border-slate-800/80 pb-2">
            <span className="text-xs font-mono font-semibold uppercase text-slate-400">
              VEHICLE VELOCITY
            </span>
            <div className="flex items-center gap-2">
              <span className="px-2 py-0.5 rounded text-[10px] font-mono bg-cyan-950/80 text-cyan-300 border border-cyan-500/30">
                PID 010D
              </span>
              <button
                onClick={() => setSettings(s => ({ ...s, speedUnit: s.speedUnit === 'km/h' ? 'mph' : 'km/h' }))}
                className="px-2 py-0.5 rounded text-[10px] font-mono bg-slate-800 hover:bg-slate-700 text-slate-300 transition-colors"
                title="Toggle km/h and mph"
              >
                {settings.speedUnit.toUpperCase()}
              </button>
            </div>
          </div>

          {/* Central Glanceable Speed Display */}
          <div className="my-auto py-3 flex flex-col items-center justify-center text-center">
            {isConnected ? (
              <>
                <div className="flex items-baseline justify-center gap-2">
                  <span className="text-7xl sm:text-8xl font-black font-['Chakra_Petch'] text-white tracking-tighter drop-shadow-[0_0_25px_rgba(92,220,244,0.3)]">
                    {displaySpeed}
                  </span>
                  <span className="text-xl sm:text-2xl font-bold font-mono text-cyan-400 uppercase">
                    {settings.speedUnit}
                  </span>
                </div>

                {/* 6-Speed Automatic Gear Indicator */}
                <div className="mt-1 flex items-center gap-2">
                  <span className="px-3 py-1 rounded-lg bg-[#0E1721] border border-slate-700 font-['Chakra_Petch'] text-sm font-bold text-cyan-300">
                    {gear === 0 ? 'GEAR: P / N' : `GEAR: D${gear}`}
                  </span>
                  <span className="text-xs font-mono text-slate-400">
                    {rpmVal !== null ? `${rpmVal} RPM` : ''}
                  </span>
                </div>
              </>
            ) : (
              <div className="flex flex-col items-center justify-center py-4">
                <span className="text-6xl sm:text-7xl font-bold font-mono text-slate-600 tracking-wider">
                  --
                </span>
                <span className="text-sm font-mono text-slate-400 uppercase mt-1">
                  {settings.speedUnit}
                </span>
                <div className="mt-3 px-3 py-1.5 rounded-xl bg-slate-900 border border-slate-800 flex items-center gap-2 text-xs font-mono text-slate-400">
                  <Bluetooth className="w-4 h-4 text-cyan-400 animate-pulse" />
                  <span>Awaiting Bluetooth Connection</span>
                </div>
              </div>
            )}

            {/* Dynamic Sweeping RPM Bar with Redline */}
            <div className="w-full mt-3 space-y-1">
              <div className="flex justify-between text-[10px] font-mono text-slate-400">
                <span>0</span>
                <span>2000</span>
                <span>4000</span>
                <span className="text-amber-400">6000</span>
                <span className="text-rose-400 font-bold">6500 Redline</span>
              </div>
              <div className="w-full h-2.5 bg-slate-950 rounded-full p-0.5 border border-slate-800 overflow-hidden">
                <div
                  className={`h-full rounded-full transition-all duration-150 ${
                    rpmVal && rpmVal > 6000
                      ? 'bg-rose-500'
                      : rpmVal && rpmVal > 4500
                      ? 'bg-amber-400'
                      : 'bg-gradient-to-r from-teal-500 via-cyan-400 to-cyan-300'
                  }`}
                  style={{ width: `${rpmVal ? Math.min(100, (rpmVal / 7000) * 100) : 0}%` }}
                />
              </div>
            </div>
          </div>

          {/* Quick Telemetry Footnote */}
          <div className="grid grid-cols-3 gap-1.5 pt-2 border-t border-slate-800/80 font-mono text-xs">
            <div className="bg-[#0E1721] p-1.5 rounded-lg border border-slate-800 text-center">
              <span className="text-[10px] text-slate-500 uppercase block">Coolant</span>
              <span className="font-bold text-slate-200 text-xs">
                {coolantVal !== null ? `${coolantVal}°C` : '--'}
              </span>
            </div>
            <div className="bg-[#0E1721] p-1.5 rounded-lg border border-slate-800 text-center">
              <span className="text-[10px] text-slate-500 uppercase block">Throttle</span>
              <span className="font-bold text-slate-200 text-xs">
                {throttleVal !== null ? `${throttleVal}%` : '--'}
              </span>
            </div>
            <div className="bg-[#0E1721] p-1.5 rounded-lg border border-slate-800 text-center">
              <span className="text-[10px] text-slate-500 uppercase block">Load</span>
              <span className="font-bold text-slate-200 text-xs">
                {loadVal !== null ? `${loadVal}%` : '--'}
              </span>
            </div>
          </div>
        </div>

        {/* Right Widget: Media Player & Live Sensor Monitor (3 cols) */}
        <div className="lg:col-span-3 flex flex-col gap-3 min-h-[300px]">
          
          {/* Media / Audio Player Card */}
          <div className="bg-[#14212B] border border-slate-800 rounded-2xl p-4 flex flex-col justify-between shadow-xl flex-1">
            <div>
              <div className="flex items-center justify-between mb-3">
                <div className="flex items-center gap-2">
                  <Music className="w-4 h-4 text-emerald-400" />
                  <span className="font-bold text-xs uppercase tracking-wider text-slate-300 font-['Chakra_Petch']">
                    MEDIA PLAYER
                  </span>
                </div>
                <button
                  onClick={() => launchApp(DEFAULT_LAUNCHER_APPS.find(a => a.id === 'spotify')!)}
                  className="px-2 py-0.5 rounded text-[10px] font-mono bg-emerald-500/15 border border-emerald-500/30 text-emerald-300 hover:bg-emerald-500/25 transition-colors"
                >
                  Open Spotify
                </button>
              </div>

              {/* Album art / Track Info placeholder */}
              <div className="bg-[#0E1721] p-3 rounded-xl border border-slate-800 flex items-center gap-3">
                <div className="w-11 h-11 rounded-lg bg-emerald-950/80 border border-emerald-500/30 flex items-center justify-center text-emerald-400 shrink-0">
                  <Radio className="w-5 h-5" />
                </div>
                <div className="overflow-hidden">
                  <p className="text-xs font-bold text-slate-100 truncate">Sportage Acoustic Sound</p>
                  <p className="text-[10px] text-slate-400 truncate font-mono">Bluetooth Audio</p>
                </div>
              </div>
            </div>

            {/* Media playback buttons */}
            <div className="flex items-center justify-center gap-3 my-2">
              <button 
                onClick={() => {}}
                className="p-1.5 rounded-full hover:bg-slate-800 text-slate-400 hover:text-white transition-colors"
              >
                <SkipBack className="w-4 h-4" />
              </button>
              <button 
                onClick={() => setIsPlayingMusic(!isPlayingMusic)}
                className="w-9 h-9 rounded-full bg-emerald-500 hover:bg-emerald-400 text-slate-950 flex items-center justify-center font-bold transition-transform hover:scale-105 shadow-md"
              >
                {isPlayingMusic ? <Pause className="w-4 h-4 fill-current" /> : <Play className="w-4 h-4 fill-current ml-0.5" />}
              </button>
              <button 
                onClick={() => {}}
                className="p-1.5 rounded-full hover:bg-slate-800 text-slate-400 hover:text-white transition-colors"
              >
                <SkipForward className="w-4 h-4" />
              </button>
            </div>

            {/* Quick Media Apps launch */}
            <div className="grid grid-cols-3 gap-1 pt-2 border-t border-slate-800/80 font-mono text-[10px]">
              <button
                onClick={() => launchApp(DEFAULT_LAUNCHER_APPS.find(a => a.id === 'spotify')!)}
                className="p-1.5 rounded bg-[#0E1721] hover:bg-slate-800 text-center text-emerald-400 truncate"
              >
                Spotify
              </button>
              <button
                onClick={() => launchApp(DEFAULT_LAUNCHER_APPS.find(a => a.id === 'yt_music')!)}
                className="p-1.5 rounded bg-[#0E1721] hover:bg-slate-800 text-center text-rose-400 truncate"
              >
                YT Music
              </button>
              <button
                onClick={() => launchApp(DEFAULT_LAUNCHER_APPS.find(a => a.id === 'radio')!)}
                className="p-1.5 rounded bg-[#0E1721] hover:bg-slate-800 text-center text-purple-400 truncate"
              >
                FM Radio
              </button>
            </div>
          </div>

          {/* Quick Vehicle Telemetry Stats */}
          <div className="bg-[#14212B] border border-slate-800 rounded-2xl p-3 font-mono text-xs space-y-1.5">
            <div className="flex justify-between items-center text-slate-400">
              <span className="flex items-center gap-1.5 text-[11px]">
                <Zap className="w-3 h-3 text-amber-400" /> Battery Alternator
              </span>
              <strong className="text-slate-100">{batteryVal ? `${batteryVal.toFixed(1)}V` : '--'}</strong>
            </div>
            <div className="flex justify-between items-center text-slate-400">
              <span className="flex items-center gap-1.5 text-[11px]">
                <Thermometer className="w-3 h-3 text-cyan-400" /> Coolant Thermostat
              </span>
              <strong className="text-slate-100">{coolantVal ? `${coolantVal}°C` : '--'}</strong>
            </div>
            <div className="flex justify-between items-center text-slate-400">
              <span className="flex items-center gap-1.5 text-[11px]">
                <Gauge className="w-3 h-3 text-teal-400" /> OBD Packets
              </span>
              <strong className="text-teal-300">{adapterInfo.packetsReceived}</strong>
            </div>
          </div>
        </div>
      </main>

      {/* 3. BOTTOM CAR LAUNCHER DOCK */}
      <footer className="w-full bg-[#0E1721] border-t border-slate-800/90 px-4 py-2 flex items-center justify-between shrink-0 z-20">
        
        {/* Left: All Apps Drawer button */}
        <button
          onClick={() => setIsAppsDrawerOpen(true)}
          className="flex items-center gap-2 px-3 py-2 rounded-xl bg-slate-800/80 hover:bg-slate-700 text-slate-200 text-xs font-mono font-medium transition-colors"
        >
          <Grid className="w-4 h-4 text-cyan-400" />
          <span className="hidden sm:inline">All Apps</span>
        </button>

        {/* Center: In-Car App Quick Dock */}
        <div className="flex items-center gap-2 sm:gap-3">
          {/* Navigation (Waze) */}
          <button
            onClick={() => window.open('https://waze.com/ul', '_blank')}
            className="p-2.5 rounded-xl bg-[#33CCFF]/15 hover:bg-[#33CCFF]/30 border border-[#33CCFF]/40 text-[#33CCFF] transition-transform hover:scale-110 shadow-lg"
            title="Launch Waze Navigation"
          >
            <Navigation className="w-5 h-5 fill-current" />
          </button>

          {/* Google Maps */}
          <button
            onClick={() => window.open('https://maps.google.com', '_blank')}
            className="p-2.5 rounded-xl bg-[#4285F4]/15 hover:bg-[#4285F4]/30 border border-[#4285F4]/40 text-[#4285F4] transition-transform hover:scale-110 shadow-lg"
            title="Google Maps"
          >
            <MapPin className="w-5 h-5" />
          </button>

          {/* Spotify */}
          <button
            onClick={() => launchApp(DEFAULT_LAUNCHER_APPS.find(a => a.id === 'spotify')!)}
            className="p-2.5 rounded-xl bg-[#1DB954]/15 hover:bg-[#1DB954]/30 border border-[#1DB954]/40 text-[#1DB954] transition-transform hover:scale-110 shadow-lg"
            title="Spotify"
          >
            <Music className="w-5 h-5" />
          </button>

          {/* Phone */}
          <button
            onClick={() => launchApp(DEFAULT_LAUNCHER_APPS.find(a => a.id === 'phone')!)}
            className="p-2.5 rounded-xl bg-[#10B981]/15 hover:bg-[#10B981]/30 border border-[#10B981]/40 text-[#10B981] transition-transform hover:scale-110 shadow-lg"
            title="Phone / Hands-free Call"
          >
            <Phone className="w-5 h-5" />
          </button>

          <div className="h-6 w-px bg-slate-800" />

          {/* Vehicle Diagnostics */}
          <button
            onClick={() => setIsDiagnosticsModalOpen(true)}
            className="p-2.5 rounded-xl bg-[#F59E0B]/15 hover:bg-[#F59E0B]/30 border border-[#F59E0B]/40 text-[#F59E0B] transition-transform hover:scale-110 shadow-lg"
            title="ECU Fault Diagnostics (DTC)"
          >
            <AlertTriangle className="w-5 h-5" />
          </button>

          {/* ELM327 Bluetooth Adapter Settings */}
          <button
            onClick={() => setIsConnectModalOpen(true)}
            className="p-2.5 rounded-xl bg-cyan-500/15 hover:bg-cyan-500/30 border border-cyan-500/40 text-cyan-400 transition-transform hover:scale-110 shadow-lg"
            title="ELM327 Bluetooth Link & Raw Terminal"
          >
            <Terminal className="w-5 h-5" />
          </button>
        </div>

        {/* Right: Bluetooth status quick icon */}
        <button
          onClick={() => setIsConnectModalOpen(true)}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#14212B] hover:bg-slate-800 border border-slate-800 text-xs font-mono transition-colors"
        >
          <Bluetooth className={`w-4 h-4 ${isConnected ? 'text-teal-400' : 'text-slate-400'}`} />
          <span className="hidden sm:inline text-slate-300">{isConnected ? 'ELM327 Active' : 'Setup Link'}</span>
        </button>
      </footer>

      {/* MODAL 1: Real Bluetooth / Serial ELM327 Link & Raw Terminal */}
      <BluetoothConnectModal
        isOpen={isConnectModalOpen}
        onClose={() => setIsConnectModalOpen(false)}
        connectionState={connectionState}
        adapterInfo={adapterInfo}
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
          <div className="bg-[#14212B] border border-slate-700 w-full max-w-3xl rounded-2xl shadow-2xl p-6 flex flex-col max-h-[85vh] overflow-hidden">
            <div className="flex items-center justify-between border-b border-slate-800 pb-4 mb-4">
              <div className="flex items-center gap-2">
                <Grid className="w-5 h-5 text-cyan-400" />
                <h2 className="text-base font-bold font-['Chakra_Petch'] text-white">
                  IN-CAR APPLICATIONS & LAUNCHER
                </h2>
              </div>
              <button
                onClick={() => setIsAppsDrawerOpen(false)}
                className="px-3 py-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-mono"
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
                  className="bg-[#0E1721] hover:bg-slate-800/80 border border-slate-800 hover:border-slate-700 rounded-xl p-4 flex flex-col items-center text-center transition-all hover:scale-105 group shadow-lg"
                >
                  <div 
                    className="w-12 h-12 rounded-xl flex items-center justify-center mb-3 shadow-md group-hover:scale-110 transition-transform"
                    style={{ backgroundColor: `${app.color}25`, color: app.color, borderColor: `${app.color}50` }}
                  >
                    {app.id === 'waze' && <Navigation className="w-6 h-6 fill-current" />}
                    {app.id === 'google_maps' && <MapPin className="w-6 h-6" />}
                    {app.id === 'spotify' && <Music className="w-6 h-6" />}
                    {app.id === 'yt_music' && <Play className="w-6 h-6 fill-current" />}
                    {app.id === 'phone' && <Phone className="w-6 h-6" />}
                    {app.id === 'radio' && <Radio className="w-6 h-6" />}
                    {app.id === 'dashcam' && <Zap className="w-6 h-6" />}
                    {app.id === 'settings' && <SlidersHorizontal className="w-6 h-6" />}
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
