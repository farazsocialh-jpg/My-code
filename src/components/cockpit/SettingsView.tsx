import React from 'react';
import { Settings, Home, Gauge, Sliders, Smartphone, Bluetooth, Check, RefreshCw } from 'lucide-react';

interface SettingsViewProps {
  speedUnit: 'km/h' | 'mph';
  onToggleSpeedUnit: () => void;
  onOpenBluetoothModal: () => void;
  onOpenApkModal: () => void;
}

export const SettingsView: React.FC<SettingsViewProps> = ({
  speedUnit,
  onToggleSpeedUnit,
  onOpenBluetoothModal,
  onOpenApkModal
}) => {
  return (
    <div className="w-full h-full flex flex-col gap-3 p-4 overflow-y-auto select-none font-mono">
      {/* Header */}
      <div className="flex items-center gap-2 pb-2 border-b border-slate-800">
        <Settings className="w-5 h-5 text-cyan-400" />
        <div>
          <h2 className="text-sm font-bold font-['Chakra_Petch'] text-white">
            LAUNCHER CONFIGURATION & SYSTEM PREFERENCES
          </h2>
          <p className="text-[10px] text-slate-400">
            Configure default Android HOME behavior, display units, and OBD2 communications
          </p>
        </div>
      </div>

      {/* 1. Android Home Launcher Setup Card */}
      <div className="bg-[#090D14] border border-cyan-500/40 rounded-xl p-4 shadow-xl">
        <div className="flex items-start justify-between gap-4">
          <div className="flex items-start gap-3">
            <div className="w-9 h-9 rounded-xl bg-cyan-500/20 flex items-center justify-center text-cyan-400 shrink-0 mt-0.5">
              <Home className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-xs font-bold text-white uppercase tracking-wide">
                Permanent Android HOME Car Launcher Mode
              </h3>
              <p className="text-[11px] text-slate-400 mt-1 leading-relaxed">
                When installed as an APK on your Android Head Unit or phone, this app registers as a full system HOME replacement. Turn on your car ignition and the cockpit loads automatically.
              </p>
              <div className="flex items-center gap-2 mt-2 text-[10px] text-teal-400 font-bold">
                <Check className="w-3.5 h-3.5" />
                <span>Declared with android.intent.category.HOME & DEFAULT</span>
              </div>
            </div>
          </div>

          <button
            onClick={onOpenApkModal}
            className="px-3.5 py-2 rounded-xl bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-bold text-xs shrink-0 transition-colors shadow-md"
          >
            APK Build Guide
          </button>
        </div>
      </div>

      {/* 2. Units & Telemetry Preferences Card */}
      <div className="bg-[#090D14] border border-slate-800 rounded-xl p-4 flex flex-col gap-4 shadow-xl">
        {/* Speed Unit */}
        <div className="flex items-center justify-between pb-3 border-b border-slate-800/80">
          <div>
            <span className="text-xs font-bold text-white block">Speedometer & Distance Units</span>
            <span className="text-[10px] text-slate-400">Current setting: {speedUnit.toUpperCase()}</span>
          </div>

          <button
            onClick={onToggleSpeedUnit}
            className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-cyan-300 text-xs font-bold transition-colors"
          >
            {speedUnit === 'km/h' ? 'Switch to MPH' : 'Switch to KM/H'}
          </button>
        </div>

        {/* Polling Cadence */}
        <div className="flex items-center justify-between pb-3 border-b border-slate-800/80">
          <div>
            <span className="text-xs font-bold text-white block">ELM327 Polling Cadence</span>
            <span className="text-[10px] text-slate-400">High-speed PID polling over Bluetooth RFCOMM serial</span>
          </div>

          <span className="px-2.5 py-1 rounded bg-teal-500/15 border border-teal-500/40 text-teal-300 text-xs font-bold">
            10Hz (100ms) High Speed
          </span>
        </div>

        {/* Vehicle Protocol */}
        <div className="flex items-center justify-between">
          <div>
            <span className="text-xs font-bold text-white block">Target Vehicle Profile</span>
            <span className="text-[10px] text-slate-400">KIA Sportage 2.0 MPI AWD (GCC Edition)</span>
          </div>

          <span className="px-2.5 py-1 rounded bg-slate-800 text-slate-300 text-xs font-bold">
            ISO 15765-4 CAN (11/500K)
          </span>
        </div>
      </div>

      {/* 3. Bluetooth Adapter Quick Shortcut */}
      <div className="bg-[#090D14] border border-slate-800 rounded-xl p-4 flex items-center justify-between shadow-xl">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-teal-500/20 text-teal-300 flex items-center justify-center">
            <Bluetooth className="w-4 h-4" />
          </div>
          <div>
            <span className="text-xs font-bold text-white block">Bluetooth Adapter & Raw Terminal</span>
            <span className="text-[10px] text-slate-400">Inspect raw ELM327 AT command stream or connect hardware</span>
          </div>
        </div>

        <button
          onClick={onOpenBluetoothModal}
          className="px-3 py-1.5 rounded-lg bg-teal-500/20 hover:bg-teal-500/30 border border-teal-500/50 text-teal-300 text-xs font-bold transition-colors"
        >
          Open Interface
        </button>
      </div>
    </div>
  );
};
