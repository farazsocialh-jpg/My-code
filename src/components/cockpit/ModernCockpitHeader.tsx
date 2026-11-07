import React, { useState, useEffect } from 'react';
import { Gauge, Thermometer, Fuel, Zap, Camera, ShieldCheck } from 'lucide-react';

interface ModernCockpitHeaderProps {
  rpm: number | null;
  coolant: number | null;
  fuelLevel: number | null;
  voltage: number | null;
  speed: number | null;
  gear: number;
}

export const ModernCockpitHeader: React.FC<ModernCockpitHeaderProps> = ({
  rpm,
  coolant,
  fuelLevel,
  voltage,
  speed,
  gear
}) => {
  const [timeStr, setTimeStr] = useState<string>('');
  const [seconds, setSeconds] = useState<number>(0);

  useEffect(() => {
    const updateTime = () => {
      const now = new Date();
      setTimeStr(now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false }));
      setSeconds(now.getSeconds());
    };
    updateTime();
    const timer = setInterval(updateTime, 1000);
    return () => clearInterval(timer);
  }, []);

  const secondAngle = (seconds / 60) * 360;

  return (
    <div className="w-full flex flex-col items-center justify-between p-3 select-none">
      
      {/* Top Chronograph Dial with Camera & ADAS Pill */}
      <div className="w-full flex items-center justify-between px-2">
        <div className="flex items-center gap-1.5 px-2 py-0.5 rounded-full bg-[#0E1722]/80 border border-slate-800 text-[10px] font-mono text-cyan-400">
          <ShieldCheck className="w-3 h-3 text-teal-400" />
          <span>SPORTAGE IQ</span>
        </div>

        {/* Center Glowing Minimal Chronograph Dial */}
        <div className="relative w-24 h-24 flex items-center justify-center">
          {/* Circular SVG Ring */}
          <svg className="w-full h-full transform -rotate-90" viewBox="0 0 100 100">
            {/* Background track */}
            <circle
              cx="50"
              cy="50"
              r="42"
              fill="none"
              stroke="#131C28"
              strokeWidth="3.5"
            />
            {/* Active seconds indicator arc */}
            <circle
              cx="50"
              cy="50"
              r="42"
              fill="none"
              stroke="#4DF0FF"
              strokeWidth="3.5"
              strokeDasharray="264"
              strokeDashoffset={264 - (seconds / 60) * 264}
              strokeLinecap="round"
              className="transition-all duration-300 drop-shadow-[0_0_8px_#4DF0FF]"
            />
          </svg>

          {/* Time Readout inside Ring */}
          <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
            <span className="text-xl font-bold font-['Chakra_Petch'] text-white tracking-wider">
              {timeStr || '22:31'}
            </span>
            <span className="text-[10px] font-mono text-cyan-400/80 -mt-0.5">
              {String(seconds).padStart(2, '0')}s
            </span>
          </div>
        </div>

        {/* Camera / ADAS button */}
        <button
          onClick={() => {}}
          className="p-2 rounded-full bg-[#0E1722]/80 hover:bg-slate-800 border border-slate-800 text-slate-400 hover:text-white transition-colors"
          title="Vehicle Camera / Dashcam View"
        >
          <Camera className="w-4 h-4" />
        </button>
      </div>

      {/* 4 Precision Telemetry Capsules: RPM, Coolant, Fuel, Voltage */}
      <div className="w-full grid grid-cols-4 gap-2 mt-2 px-1">
        {/* 1. RPM */}
        <div className="bg-[#0B131E]/90 border border-slate-800/90 hover:border-cyan-500/30 rounded-xl p-2 text-center transition-colors">
          <span className="text-sm sm:text-base font-bold font-['Chakra_Petch'] text-slate-100 block">
            {rpm !== null ? rpm.toLocaleString() : '--'}
          </span>
          <span className="text-[9px] font-mono text-slate-400 uppercase block truncate">
            RPM · nu 2.0
          </span>
        </div>

        {/* 2. Coolant */}
        <div className="bg-[#0B131E]/90 border border-slate-800/90 hover:border-cyan-500/30 rounded-xl p-2 text-center transition-colors">
          <span className="text-sm sm:text-base font-bold font-['Chakra_Petch'] text-teal-300 block">
            {coolant !== null ? `${coolant}°` : '--'}
          </span>
          <span className="text-[9px] font-mono text-slate-400 uppercase block truncate">
            Coolant °C
          </span>
        </div>

        {/* 3. Fuel Level / Engine Load */}
        <div className="bg-[#0B131E]/90 border border-slate-800/90 hover:border-cyan-500/30 rounded-xl p-2 text-center transition-colors">
          <span className="text-sm sm:text-base font-bold font-['Chakra_Petch'] text-amber-300 block">
            {fuelLevel !== null ? `${fuelLevel}%` : '62%'}
          </span>
          <span className="text-[9px] font-mono text-slate-400 uppercase block truncate">
            Fuel Tank
          </span>
        </div>

        {/* 4. Battery Alternator Voltage */}
        <div className="bg-[#0B131E]/90 border border-slate-800/90 hover:border-cyan-500/30 rounded-xl p-2 text-center transition-colors">
          <span className="text-sm sm:text-base font-bold font-['Chakra_Petch'] text-cyan-300 block">
            {voltage !== null ? `${voltage.toFixed(1)}V` : '--'}
          </span>
          <span className="text-[9px] font-mono text-slate-400 uppercase block truncate">
            Battery V
          </span>
        </div>
      </div>
    </div>
  );
};
