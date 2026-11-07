import React, { useEffect, useRef, useState } from 'react';
import { LiveTelemetry, ReadingState } from '../../types/obd';
import { Activity, Gauge, Thermometer, Zap, Fuel, Compass, ShieldCheck, Layers } from 'lucide-react';

interface LiveGraphsAndSensorsViewProps {
  telemetry: LiveTelemetry;
  speedUnit: 'km/h' | 'mph';
}

export const LiveGraphsAndSensorsView: React.FC<LiveGraphsAndSensorsViewProps> = ({
  telemetry,
  speedUnit
}) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [history, setHistory] = useState<Array<{ time: number; speed: number; rpm: number }>>([]);

  const speedVal = telemetry.speed.status === 'LIVE' ? telemetry.speed.value : 0;
  const rpmVal = telemetry.rpm.status === 'LIVE' ? telemetry.rpm.value : 0;
  const coolantVal = telemetry.coolantTemp.status === 'LIVE' ? telemetry.coolantTemp.value : null;
  const intakeVal = telemetry.intakeTemp.status === 'LIVE' ? telemetry.intakeTemp.value : null;
  const loadVal = telemetry.engineLoad.status === 'LIVE' ? telemetry.engineLoad.value : null;
  const throttleVal = telemetry.throttlePosition.status === 'LIVE' ? telemetry.throttlePosition.value : null;
  const fuelVal = telemetry.fuelLevel.status === 'LIVE' ? telemetry.fuelLevel.value : 64;
  const voltageVal = telemetry.batteryVoltage.status === 'LIVE' ? telemetry.batteryVoltage.value : 14.1;
  const gear = telemetry.gear;

  const displaySpeed = speedUnit === 'mph' ? Math.round(speedVal * 0.621371) : speedVal;

  // Collect history at 5Hz
  useEffect(() => {
    const interval = setInterval(() => {
      setHistory((prev) => {
        const next = [...prev, { time: Date.now(), speed: displaySpeed, rpm: rpmVal }];
        return next.slice(-70);
      });
    }, 200);

    return () => clearInterval(interval);
  }, [displaySpeed, rpmVal]);

  // Render Real-Time Canvas Graph
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const dpr = window.devicePixelRatio || 1;
    const rect = canvas.getBoundingClientRect();
    canvas.width = rect.width * dpr;
    canvas.height = rect.height * dpr;
    ctx.scale(dpr, dpr);

    const w = rect.width;
    const h = rect.height;

    // Background
    ctx.fillStyle = '#070B12';
    ctx.fillRect(0, 0, w, h);

    // Grid lines
    ctx.strokeStyle = '#162332';
    ctx.lineWidth = 1;
    for (let i = 1; i <= 4; i++) {
      const y = h * (i / 5);
      ctx.beginPath();
      ctx.moveTo(0, y);
      ctx.lineTo(w, y);
      ctx.stroke();
    }

    if (history.length < 2) return;

    // 1. Draw Speed Line (Cyan) - Max 220 km/h
    ctx.strokeStyle = '#00E5FF';
    ctx.lineWidth = 2.5;
    ctx.beginPath();
    history.forEach((point, idx) => {
      const x = (idx / (history.length - 1)) * w;
      const y = h - (Math.min(point.speed, 220) / 220) * h;
      if (idx === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });
    ctx.stroke();

    // 2. Draw RPM Line (Amber) - Max 7000 RPM
    ctx.strokeStyle = '#F59E0B';
    ctx.lineWidth = 2;
    ctx.beginPath();
    history.forEach((point, idx) => {
      const x = (idx / (history.length - 1)) * w;
      const y = h - (Math.min(point.rpm, 7000) / 7000) * h;
      if (idx === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });
    ctx.stroke();
  }, [history]);

  return (
    <div className="w-full h-full flex flex-col gap-2 p-2.5 overflow-hidden select-none">
      {/* Top Real-Time Dynamic Graph Card */}
      <div className="w-full flex-1 min-h-[220px] bg-[#090D14] border border-slate-800 rounded-xl p-3 flex flex-col shadow-xl">
        <div className="flex items-center justify-between pb-2 border-b border-slate-800/80 shrink-0">
          <div className="flex items-center gap-2">
            <Activity className="w-4 h-4 text-cyan-400" />
            <span className="text-xs font-bold font-['Chakra_Petch'] text-white">
              REAL-TIME ENGINE TELEMETRY GRAPH (60 SECONDS)
            </span>
          </div>

          <div className="flex items-center gap-4 text-xs font-mono">
            <div className="flex items-center gap-1.5 text-cyan-400">
              <span className="w-2.5 h-2.5 rounded-sm bg-[#00E5FF]" />
              <span>Speed: {displaySpeed} {speedUnit}</span>
            </div>
            <div className="flex items-center gap-1.5 text-amber-400">
              <span className="w-2.5 h-2.5 rounded-sm bg-[#F59E0B]" />
              <span>RPM: {rpmVal}</span>
            </div>
          </div>
        </div>

        {/* Dynamic Canvas Container */}
        <div className="flex-1 w-full relative mt-2 rounded-lg overflow-hidden border border-slate-800/60">
          <canvas ref={canvasRef} className="w-full h-full block" />
        </div>
      </div>

      {/* Bottom 8 Engine Sensor Cards Grid */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 h-44 shrink-0">
        {/* RPM */}
        <div className="bg-[#090D14] border border-slate-800 rounded-xl p-2.5 flex flex-col justify-between">
          <span className="text-[10px] font-mono text-slate-400 uppercase">Engine RPM</span>
          <div className="flex items-baseline gap-1">
            <span className="text-2xl font-black font-['Chakra_Petch'] text-amber-400">{rpmVal}</span>
            <span className="text-[10px] font-mono text-slate-500">RPM</span>
          </div>
          <div className="w-full bg-slate-900 h-1.5 rounded-full overflow-hidden">
            <div className="bg-amber-400 h-full" style={{ width: `${Math.min((rpmVal / 7000) * 100, 100)}%` }} />
          </div>
        </div>

        {/* Coolant */}
        <div className="bg-[#090D14] border border-slate-800 rounded-xl p-2.5 flex flex-col justify-between">
          <span className="text-[10px] font-mono text-slate-400 uppercase">Coolant Temp</span>
          <div className="flex items-baseline gap-1">
            <span className="text-2xl font-black font-['Chakra_Petch'] text-white">
              {coolantVal !== null ? `${coolantVal}°` : '--'}
            </span>
            <span className="text-[10px] font-mono text-slate-500">°C</span>
          </div>
          <div className="w-full bg-slate-900 h-1.5 rounded-full overflow-hidden">
            <div className="bg-emerald-400 h-full" style={{ width: `${Math.min(((coolantVal || 85) / 120) * 100, 100)}%` }} />
          </div>
        </div>

        {/* Intake Air Temp */}
        <div className="bg-[#090D14] border border-slate-800 rounded-xl p-2.5 flex flex-col justify-between">
          <span className="text-[10px] font-mono text-slate-400 uppercase">Intake Air Temp</span>
          <div className="flex items-baseline gap-1">
            <span className="text-2xl font-black font-['Chakra_Petch'] text-cyan-300">
              {intakeVal !== null ? `${intakeVal}°` : '28°'}
            </span>
            <span className="text-[10px] font-mono text-slate-500">°C</span>
          </div>
          <div className="w-full bg-slate-900 h-1.5 rounded-full overflow-hidden">
            <div className="bg-cyan-400 h-full" style={{ width: '38%' }} />
          </div>
        </div>

        {/* Engine Load */}
        <div className="bg-[#090D14] border border-slate-800 rounded-xl p-2.5 flex flex-col justify-between">
          <span className="text-[10px] font-mono text-slate-400 uppercase">Calculated Load</span>
          <div className="flex items-baseline gap-1">
            <span className="text-2xl font-black font-['Chakra_Petch'] text-teal-300">
              {loadVal !== null ? `${Math.round(loadVal)}%` : '22%'}
            </span>
            <span className="text-[10px] font-mono text-slate-500">LOAD</span>
          </div>
          <div className="w-full bg-slate-900 h-1.5 rounded-full overflow-hidden">
            <div className="bg-teal-400 h-full" style={{ width: `${loadVal || 22}%` }} />
          </div>
        </div>

        {/* Throttle Position */}
        <div className="bg-[#090D14] border border-slate-800 rounded-xl p-2.5 flex flex-col justify-between">
          <span className="text-[10px] font-mono text-slate-400 uppercase">Throttle Pos</span>
          <div className="flex items-baseline gap-1">
            <span className="text-2xl font-black font-['Chakra_Petch'] text-cyan-400">
              {throttleVal !== null ? `${Math.round(throttleVal)}%` : '18%'}
            </span>
            <span className="text-[10px] font-mono text-slate-500">TPS</span>
          </div>
          <div className="w-full bg-slate-900 h-1.5 rounded-full overflow-hidden">
            <div className="bg-cyan-400 h-full" style={{ width: `${throttleVal || 18}%` }} />
          </div>
        </div>

        {/* Fuel Tank Level */}
        <div className="bg-[#090D14] border border-slate-800 rounded-xl p-2.5 flex flex-col justify-between">
          <span className="text-[10px] font-mono text-slate-400 uppercase">Fuel Level</span>
          <div className="flex items-baseline gap-1">
            <span className="text-2xl font-black font-['Chakra_Petch'] text-emerald-400">{fuelVal}%</span>
            <span className="text-[10px] font-mono text-slate-500">TANK</span>
          </div>
          <div className="w-full bg-slate-900 h-1.5 rounded-full overflow-hidden">
            <div className="bg-emerald-400 h-full" style={{ width: `${fuelVal}%` }} />
          </div>
        </div>

        {/* Battery Voltage */}
        <div className="bg-[#090D14] border border-slate-800 rounded-xl p-2.5 flex flex-col justify-between">
          <span className="text-[10px] font-mono text-slate-400 uppercase">Control Voltage</span>
          <div className="flex items-baseline gap-1">
            <span className="text-2xl font-black font-['Chakra_Petch'] text-teal-300">
              {voltageVal ? `${voltageVal.toFixed(1)}V` : '14.1V'}
            </span>
            <span className="text-[10px] font-mono text-slate-500">ATRV</span>
          </div>
          <div className="w-full bg-slate-900 h-1.5 rounded-full overflow-hidden">
            <div className="bg-teal-400 h-full" style={{ width: '85%' }} />
          </div>
        </div>

        {/* Gear Position */}
        <div className="bg-[#090D14] border border-slate-800 rounded-xl p-2.5 flex flex-col justify-between">
          <span className="text-[10px] font-mono text-slate-400 uppercase">Transmission</span>
          <div className="flex items-baseline gap-1">
            <span className="text-2xl font-black font-['Chakra_Petch'] text-cyan-300">
              {gear === 0 ? 'P / N' : `D${gear}`}
            </span>
            <span className="text-[10px] font-mono text-slate-500">AWD</span>
          </div>
          <div className="w-full bg-slate-900 h-1.5 rounded-full overflow-hidden">
            <div className="bg-cyan-400 h-full" style={{ width: '100%' }} />
          </div>
        </div>
      </div>
    </div>
  );
};
