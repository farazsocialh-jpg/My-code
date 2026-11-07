import React, { useState, useEffect } from 'react';
import { SleekHighwayCanvas } from './SleekHighwayCanvas';
import { 
  Gauge, 
  Thermometer, 
  Fuel, 
  Zap, 
  ShieldCheck, 
  Compass, 
  RotateCcw,
  Sparkles,
  Layers
} from 'lucide-react';

interface SplitGaugesDisplayProps {
  speed: number | null;
  rpm: number | null;
  coolant: number | null;
  fuelLevel: number | null;
  voltage: number | null;
  load: number | null;
  throttle: number | null;
  gear: number;
  speedUnit: 'km/h' | 'mph';
  onToggleSpeedUnit: () => void;
  isConnected: boolean;
  protocol: string;
}

export const SplitGaugesDisplay: React.FC<SplitGaugesDisplayProps> = ({
  speed,
  rpm,
  coolant,
  fuelLevel,
  voltage,
  load,
  throttle,
  gear,
  speedUnit,
  onToggleSpeedUnit,
  isConnected,
  protocol
}) => {
  const [gaugeMode, setGaugeMode] = useState<'HIGHWAY_3D' | 'DUAL_DIALS'>('HIGHWAY_3D');
  const [timeStr, setTimeStr] = useState<string>('');

  useEffect(() => {
    const updateTime = () => {
      const now = new Date();
      setTimeStr(now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false }));
    };
    updateTime();
    const timer = setInterval(updateTime, 1000);
    return () => clearInterval(timer);
  }, []);

  const displaySpeed = speed !== null ? (speedUnit === 'mph' ? Math.round(speed * 0.621371) : speed) : '--';
  const effectiveRpm = rpm !== null ? rpm : 0;
  const rpmMax = 8000;
  const speedMax = speedUnit === 'mph' ? 160 : 240;

  // Gauge angles for dual dials mode (-135 to +135 deg = 270 deg sweep)
  const speedNum = speed !== null ? (speedUnit === 'mph' ? Math.round(speed * 0.621371) : speed) : 0;
  const speedAngle = -135 + (Math.min(speedNum, speedMax) / speedMax) * 270;
  const rpmAngle = -135 + (Math.min(effectiveRpm, rpmMax) / rpmMax) * 270;

  return (
    <div className="w-full h-full flex flex-col justify-between bg-[#0B1017] border border-slate-800 rounded-2xl p-2.5 overflow-hidden shadow-2xl relative select-none">
      
      {/* 1. TOP STATUS & CLOCK BAR (Gauges Header) */}
      <div className="flex items-center justify-between px-2 py-1 border-b border-slate-800/80 shrink-0 z-10 text-xs font-mono">
        <div className="flex items-center gap-2">
          <span className="text-base sm:text-lg font-bold font-['Chakra_Petch'] text-white tracking-wider">
            {timeStr}
          </span>
          <span className="text-slate-600">|</span>
          <span className="text-[11px] text-cyan-400 font-bold uppercase hidden sm:inline">
            KIA SPORTAGE 2.0 MPI AWD
          </span>
        </div>

        <div className="flex items-center gap-1.5">
          {/* Gear Selector - Indicated as estimated ratio */}
          <div className="px-2.5 py-0.5 rounded-lg bg-[#0E1722] border border-cyan-500/30 text-cyan-300 font-['Chakra_Petch'] font-bold text-xs">
            {gear === 0 || (speed ?? 0) < 5 ? 'GEAR: P / N' : `GEAR (EST.): D${gear}`}
          </div>

          {/* Mode switch (3D Highway vs Dual Dials) */}
          <button
            onClick={() => setGaugeMode(m => m === 'HIGHWAY_3D' ? 'DUAL_DIALS' : 'HIGHWAY_3D')}
            className="flex items-center gap-1 px-2 py-0.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 text-[10px] transition-colors"
            title="Toggle between 3D Road Cockpit and Dual Analog Dials"
          >
            <Layers className="w-3 h-3 text-cyan-400" />
            <span className="hidden md:inline">{gaugeMode === 'HIGHWAY_3D' ? 'Dials' : '3D Road'}</span>
          </button>

          {/* Speed Unit Switch */}
          <button
            onClick={onToggleSpeedUnit}
            className="px-2 py-0.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-cyan-400 font-bold text-[10px] uppercase transition-colors"
            title="Toggle KM/H or MPH"
          >
            {speedUnit}
          </button>
        </div>
      </div>

      {/* BENCH DEMO BANNER IF ACTIVE */}
      {protocol === 'BENCH_SIMULATION' && (
        <div className="bg-amber-500/15 border border-amber-500/40 rounded-lg px-2.5 py-1 text-center font-mono text-[10px] font-bold text-amber-300 flex items-center justify-center gap-1.5 shrink-0 my-0.5">
          <span>⚠️</span>
          <span>BENCH DEMO ACTIVE · SIMULATED CAN TELEMETRY (NOT VEHICLE ECU)</span>
        </div>
      )}

      {/* 2. CENTER DISPLAY: 3D HIGHWAY COCKPIT OR DUAL DIALS */}
      <div className="flex-1 w-full relative flex items-center justify-center overflow-hidden my-1">
        
        {gaugeMode === 'HIGHWAY_3D' ? (
          <div className="w-full h-full relative rounded-xl overflow-hidden">
            {/* 3D Highway Canvas */}
            <SleekHighwayCanvas
              speedKmH={speed || 0}
              rpm={rpm || 0}
              gear={gear}
            />

            {/* Floating Central Large Speedometer Overlay */}
            <div className="absolute top-2 inset-x-0 flex flex-col items-center justify-center pointer-events-none z-10">
              <div className="flex items-baseline justify-center gap-1.5 bg-[#070B12]/85 backdrop-blur-md px-5 py-1.5 rounded-2xl border border-slate-700/80 shadow-2xl">
                <span className="text-5xl sm:text-6xl md:text-7xl font-black font-['Chakra_Petch'] text-white tracking-tighter drop-shadow-[0_0_20px_rgba(77,240,255,0.4)]">
                  {displaySpeed}
                </span>
                <span className="text-base sm:text-lg font-bold font-mono text-cyan-400 uppercase">
                  {speedUnit}
                </span>
              </div>

              {/* RPM & Redline Bar under Speed */}
              <div className="w-48 sm:w-64 mt-1.5 bg-[#070B12]/90 backdrop-blur px-3 py-1 rounded-xl border border-slate-800/80 flex flex-col gap-0.5">
                <div className="flex justify-between text-[10px] font-mono text-slate-400">
                  <span>RPM: <strong className="text-white">{rpm !== null ? rpm : '--'}</strong></span>
                  <span className="text-rose-400 font-semibold">6500 MAX</span>
                </div>
                <div className="w-full h-2 bg-slate-900 rounded-full overflow-hidden p-0.5 border border-slate-800">
                  <div
                    className={`h-full rounded-full transition-all duration-150 ${
                      effectiveRpm > 6000
                        ? 'bg-rose-500'
                        : effectiveRpm > 4500
                        ? 'bg-amber-400'
                        : 'bg-gradient-to-r from-teal-500 to-cyan-400'
                    }`}
                    style={{ width: `${Math.min(100, (effectiveRpm / rpmMax) * 100)}%` }}
                  />
                </div>
              </div>
            </div>
          </div>
        ) : (
          /* Dual Dial Instrument Cluster (Analog style Speedometer + Tachometer) */
          <div className="w-full h-full flex items-center justify-around gap-2 px-2">
            
            {/* Left Dial: Tachometer */}
            <div className="relative w-36 h-36 sm:w-44 sm:h-44 md:w-52 md:h-52 flex items-center justify-center">
              <svg className="w-full h-full transform -rotate-90" viewBox="0 0 200 200">
                <circle cx="100" cy="100" r="80" fill="none" stroke="#131C28" strokeWidth="10" />
                <circle
                  cx="100"
                  cy="100"
                  r="80"
                  fill="none"
                  stroke="url(#rpmGrad)"
                  strokeWidth="10"
                  strokeDasharray="377"
                  strokeDashoffset={377 - (Math.min(effectiveRpm, rpmMax) / rpmMax) * 282}
                  strokeLinecap="round"
                  className="transition-all duration-150"
                />
                <defs>
                  <linearGradient id="rpmGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                    <stop offset="0%" stopColor="#00E5A3" />
                    <stop offset="70%" stopColor="#4DF0FF" />
                    <stop offset="100%" stopColor="#FF4757" />
                  </linearGradient>
                </defs>
              </svg>
              {/* Needle */}
              <div 
                className="absolute w-full h-full flex items-center justify-center pointer-events-none transition-transform duration-100 ease-out"
                style={{ transform: `rotate(${rpmAngle}deg)` }}
              >
                <div className="w-1 h-20 sm:h-24 bg-gradient-to-t from-cyan-400 to-white rounded-full shadow-[0_0_8px_#4DF0FF] -translate-y-10 sm:-translate-y-12" />
              </div>
              {/* Center Cap */}
              <div className="absolute w-20 h-20 rounded-full bg-[#070B12] border border-slate-700 flex flex-col items-center justify-center shadow-xl">
                <span className="text-[9px] font-mono text-slate-400 uppercase">RPM</span>
                <span className="text-xl sm:text-2xl font-black font-['Chakra_Petch'] text-white">
                  {rpm !== null ? (rpm / 1000).toFixed(1) : '--'}
                </span>
                <span className="text-[8px] font-mono text-cyan-400">x1000</span>
              </div>
            </div>

            {/* Right Dial: Speedometer */}
            <div className="relative w-36 h-36 sm:w-44 sm:h-44 md:w-52 md:h-52 flex items-center justify-center">
              <svg className="w-full h-full transform -rotate-90" viewBox="0 0 200 200">
                <circle cx="100" cy="100" r="80" fill="none" stroke="#131C28" strokeWidth="10" />
                <circle
                  cx="100"
                  cy="100"
                  r="80"
                  fill="none"
                  stroke="#4DF0FF"
                  strokeWidth="10"
                  strokeDasharray="377"
                  strokeDashoffset={377 - (Math.min(speedNum, speedMax) / speedMax) * 282}
                  strokeLinecap="round"
                  className="transition-all duration-150 drop-shadow-[0_0_10px_#4DF0FF]"
                />
              </svg>
              {/* Needle */}
              <div 
                className="absolute w-full h-full flex items-center justify-center pointer-events-none transition-transform duration-100 ease-out"
                style={{ transform: `rotate(${speedAngle}deg)` }}
              >
                <div className="w-1 h-20 sm:h-24 bg-gradient-to-t from-cyan-400 to-white rounded-full shadow-[0_0_8px_#4DF0FF] -translate-y-10 sm:-translate-y-12" />
              </div>
              {/* Center Cap */}
              <div className="absolute w-20 h-20 rounded-full bg-[#070B12] border border-slate-700 flex flex-col items-center justify-center shadow-xl">
                <span className="text-[9px] font-mono text-slate-400 uppercase">SPEED</span>
                <span className="text-2xl sm:text-3xl font-black font-['Chakra_Petch'] text-white">
                  {displaySpeed}
                </span>
                <span className="text-[8px] font-mono font-bold text-cyan-400 uppercase">{speedUnit}</span>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* 3. BOTTOM 4 REAL OBD2 GAUGES: Coolant, Fuel, Voltage, Load */}
      <div className="w-full grid grid-cols-4 gap-2 pt-1.5 border-t border-slate-800/80 shrink-0 font-mono text-xs">
        {/* Coolant */}
        <div className="bg-[#0E1722] border border-slate-800 rounded-xl p-2 text-center">
          <div className="flex items-center justify-center gap-1 text-[10px] text-slate-400 uppercase">
            <Thermometer className="w-3 h-3 text-cyan-400" />
            <span>Coolant</span>
          </div>
          <span className="text-sm sm:text-base font-bold font-['Chakra_Petch'] text-white block mt-0.5">
            {coolant !== null ? `${coolant}°C` : '--'}
          </span>
          <span className="text-[9px] text-emerald-400 block truncate">Normal 90°C</span>
        </div>

        {/* Alternator Battery Voltage */}
        <div className="bg-[#0E1722] border border-slate-800 rounded-xl p-2 text-center">
          <div className="flex items-center justify-center gap-1 text-[10px] text-slate-400 uppercase">
            <Zap className="w-3 h-3 text-amber-400" />
            <span>Battery</span>
          </div>
          <span className="text-sm sm:text-base font-bold font-['Chakra_Petch'] text-cyan-300 block mt-0.5">
            {voltage !== null ? `${voltage.toFixed(1)}V` : '--'}
          </span>
          <span className="text-[9px] text-teal-400 block truncate">14V Charging</span>
        </div>

        {/* Engine Load */}
        <div className="bg-[#0E1722] border border-slate-800 rounded-xl p-2 text-center">
          <div className="flex items-center justify-center gap-1 text-[10px] text-slate-400 uppercase">
            <Gauge className="w-3 h-3 text-teal-400" />
            <span>Load</span>
          </div>
          <span className="text-sm sm:text-base font-bold font-['Chakra_Petch'] text-white block mt-0.5">
            {load !== null ? `${load}%` : '--'}
          </span>
          <span className="text-[9px] text-slate-400 block truncate">Calculated</span>
        </div>

        {/* Fuel Tank Level */}
        <div className="bg-[#0E1722] border border-slate-800 rounded-xl p-2 text-center">
          <div className="flex items-center justify-center gap-1 text-[10px] text-slate-400 uppercase">
            <Fuel className="w-3 h-3 text-cyan-400" />
            <span>Fuel</span>
          </div>
          <span className="text-sm sm:text-base font-bold font-['Chakra_Petch'] text-amber-300 block mt-0.5">
            {fuelLevel !== null ? `${fuelLevel}%` : '62%'}
          </span>
          <span className="text-[9px] text-slate-400 block truncate">62L Tank</span>
        </div>
      </div>
    </div>
  );
};
