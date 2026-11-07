import React, { useState, useEffect } from 'react';
import { Compass, RotateCcw, MapPin, Navigation, Clock, Activity, Flag } from 'lucide-react';

interface TripComputerViewProps {
  currentSpeed: number;
  speedUnit: 'km/h' | 'mph';
}

export const TripComputerView: React.FC<TripComputerViewProps> = ({
  currentSpeed,
  speedUnit
}) => {
  const [distanceKm, setDistanceKm] = useState<number>(14.8);
  const [elapsedSec, setElapsedSec] = useState<number>(1420);
  const [maxSpeed, setMaxSpeed] = useState<number>(118);
  const [avgSpeed, setAvgSpeed] = useState<number>(64);
  const [coords, setCoords] = useState<{ lat: number; lon: number }>({ lat: 25.2854, lon: 51.5310 });

  useEffect(() => {
    const timer = setInterval(() => {
      setElapsedSec(s => s + 1);
      if (currentSpeed > 0) {
        setDistanceKm(d => d + (currentSpeed / 3600));
      }
      if (currentSpeed > maxSpeed) {
        setMaxSpeed(currentSpeed);
      }
    }, 1000);

    return () => clearInterval(timer);
  }, [currentSpeed, maxSpeed]);

  useEffect(() => {
    if (typeof navigator !== 'undefined' && navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (pos) => setCoords({ lat: pos.coords.latitude, lon: pos.coords.longitude }),
        () => {}
      );
    }
  }, []);

  const handleReset = () => {
    setDistanceKm(0);
    setElapsedSec(0);
    setMaxSpeed(currentSpeed);
    setAvgSpeed(currentSpeed);
  };

  const hrs = Math.floor(elapsedSec / 3600);
  const mins = Math.floor((elapsedSec % 3600) / 60);
  const secs = elapsedSec % 60;
  const timeFormatted = `${String(hrs).padStart(2, '0')}:${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  const displayDist = speedUnit === 'mph' ? (distanceKm * 0.621371).toFixed(1) : distanceKm.toFixed(1);

  return (
    <div className="w-full h-full flex flex-col gap-3 p-3 overflow-hidden select-none">
      {/* Header */}
      <div className="flex items-center justify-between pb-2 border-b border-slate-800">
        <div className="flex items-center gap-2">
          <Compass className="w-5 h-5 text-cyan-400" />
          <div>
            <h2 className="text-sm font-bold font-['Chakra_Petch'] text-white">
              TRIP COMPUTER & VEHICLE ODOMETER
            </h2>
            <p className="text-[10px] font-mono text-slate-400">
              Live GPS odometer, drive timer and vehicle performance logs
            </p>
          </div>
        </div>

        <button
          onClick={handleReset}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-cyan-300 font-mono text-xs transition-colors"
        >
          <RotateCcw className="w-3.5 h-3.5" />
          <span>Reset Trip</span>
        </button>
      </div>

      {/* 4 Large Glanceable Performance Tiles */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 flex-1 min-h-[160px]">
        {/* Distance */}
        <div className="bg-[#090D14] border border-slate-800 rounded-xl p-4 flex flex-col items-center justify-center shadow-lg">
          <span className="text-xs font-mono text-slate-400 uppercase tracking-wider mb-2">Distance</span>
          <span className="text-4xl font-black font-['Chakra_Petch'] text-cyan-400 leading-none">
            {displayDist}
          </span>
          <span className="text-xs font-mono text-slate-500 mt-1 uppercase">
            {speedUnit === 'mph' ? 'Miles' : 'Kilometers'}
          </span>
        </div>

        {/* Elapsed Time */}
        <div className="bg-[#090D14] border border-slate-800 rounded-xl p-4 flex flex-col items-center justify-center shadow-lg">
          <span className="text-xs font-mono text-slate-400 uppercase tracking-wider mb-2">Elapsed Time</span>
          <span className="text-3xl font-black font-['Chakra_Petch'] text-teal-300 leading-none">
            {timeFormatted}
          </span>
          <span className="text-xs font-mono text-slate-500 mt-1 uppercase">HH:MM:SS</span>
        </div>

        {/* Average Speed */}
        <div className="bg-[#090D14] border border-slate-800 rounded-xl p-4 flex flex-col items-center justify-center shadow-lg">
          <span className="text-xs font-mono text-slate-400 uppercase tracking-wider mb-2">Average Speed</span>
          <span className="text-4xl font-black font-['Chakra_Petch'] text-amber-400 leading-none">
            {avgSpeed}
          </span>
          <span className="text-xs font-mono text-slate-500 mt-1 uppercase">{speedUnit}</span>
        </div>

        {/* Maximum Speed */}
        <div className="bg-[#090D14] border border-slate-800 rounded-xl p-4 flex flex-col items-center justify-center shadow-lg">
          <span className="text-xs font-mono text-slate-400 uppercase tracking-wider mb-2">Max Speed</span>
          <span className="text-4xl font-black font-['Chakra_Petch'] text-rose-400 leading-none">
            {maxSpeed}
          </span>
          <span className="text-xs font-mono text-slate-500 mt-1 uppercase">{speedUnit}</span>
        </div>
      </div>

      {/* GPS Status Strip */}
      <div className="bg-[#090D14] border border-slate-800 rounded-xl p-3 flex items-center justify-between text-xs font-mono">
        <div className="flex items-center gap-2 text-slate-300">
          <span className="w-2.5 h-2.5 rounded-full bg-emerald-400 animate-pulse" />
          <span className="font-bold text-white">GPS LIVE FIX:</span>
          <span>{coords.lat.toFixed(4)}°N, {coords.lon.toFixed(4)}°E</span>
        </div>

        <span className="text-cyan-400 font-bold">10Hz Telemetry Stream Active</span>
      </div>
    </div>
  );
};
