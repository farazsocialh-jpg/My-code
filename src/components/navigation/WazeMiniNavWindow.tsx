import React, { useState, useEffect } from 'react';
import { 
  Navigation, 
  MapPin, 
  ExternalLink, 
  Search, 
  LocateFixed, 
  Compass, 
  Layers,
  ArrowUpRight,
  Maximize2
} from 'lucide-react';

interface WazeMiniNavWindowProps {
  destination?: string;
  onDestinationChange?: (dest: string) => void;
}

export const WazeMiniNavWindow: React.FC<WazeMiniNavWindowProps> = ({
  destination: propDest = '',
  onDestinationChange
}) => {
  // Default to GCC / Doha baseline (25.2854, 51.5310) if GPS is acquiring
  const [coords, setCoords] = useState<{ lat: number; lon: number }>({
    lat: 25.2854,
    lon: 51.5310
  });
  const [heading, setHeading] = useState<number | null>(null);
  const [speedGps, setSpeedGps] = useState<number | null>(null);
  const [gpsStatus, setGpsStatus] = useState<'ACQUIRING' | 'LOCKED' | 'DENIED'>('ACQUIRING');
  const [searchQuery, setSearchQuery] = useState<string>(propDest || '');
  const [isIframeLoaded, setIsIframeLoaded] = useState<boolean>(false);

  // Real device GPS location tracking
  useEffect(() => {
    if (typeof navigator === 'undefined' || !navigator.geolocation) {
      setGpsStatus('DENIED');
      return;
    }

    const watchId = navigator.geolocation.watchPosition(
      (pos) => {
        setCoords({
          lat: Number(pos.coords.latitude.toFixed(5)),
          lon: Number(pos.coords.longitude.toFixed(5))
        });
        if (pos.coords.heading !== null && !isNaN(pos.coords.heading)) {
          setHeading(Math.round(pos.coords.heading));
        }
        if (pos.coords.speed !== null && !isNaN(pos.coords.speed)) {
          setSpeedGps(Math.round(pos.coords.speed * 3.6)); // m/s to km/h
        }
        setGpsStatus('LOCKED');
      },
      (err) => {
        // Fallback to default GCC coordinates without crashing
        setGpsStatus(err.code === 1 ? 'DENIED' : 'ACQUIRING');
      },
      {
        enableHighAccuracy: true,
        maximumAge: 1000,
        timeout: 10000
      }
    );

    return () => {
      navigator.geolocation.clearWatch(watchId);
    };
  }, []);

  const openFullWazeApp = () => {
    const dest = searchQuery.trim();
    let url: string;
    if (dest) {
      url = `https://waze.com/ul?q=${encodeURIComponent(dest)}&navigate=yes`;
    } else {
      url = `https://waze.com/ul?ll=${coords.lat},${coords.lon}&navigate=yes`;
    }
    window.open(url, '_blank', 'noopener,noreferrer');
  };

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (onDestinationChange && searchQuery) {
      onDestinationChange(searchQuery);
    }
    openFullWazeApp();
  };

  // Official Waze LiveMap embed URL with dynamic GPS coordinates
  const wazeEmbedUrl = `https://embed.waze.com/iframe?zoom=15&lat=${coords.lat}&lon=${coords.lon}&ct=livemap&pin=1`;

  return (
    <div className="w-full h-full flex flex-col bg-[#14212B] border border-slate-800 rounded-2xl overflow-hidden shadow-2xl relative">
      
      {/* Top Header of Mini Nav Window */}
      <div className="px-3.5 py-2.5 bg-[#0E1721] border-b border-slate-800 flex items-center justify-between gap-2 z-10 shrink-0">
        <div className="flex items-center gap-2">
          <div className="w-6 h-6 rounded-md bg-[#33CCFF]/20 flex items-center justify-center text-[#33CCFF]">
            <Navigation className="w-3.5 h-3.5 fill-current" />
          </div>
          <div>
            <div className="flex items-center gap-1.5">
              <span className="font-bold text-xs font-['Chakra_Petch'] text-slate-100 tracking-wide">
                WAZE LIVE NAVIGATION
              </span>
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
            </div>
            <p className="text-[10px] font-mono text-slate-400">
              {coords.lat.toFixed(4)}°, {coords.lon.toFixed(4)}° {heading !== null ? `· ${heading}°` : ''}
            </p>
          </div>
        </div>

        <button
          onClick={openFullWazeApp}
          className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-[#33CCFF]/15 hover:bg-[#33CCFF]/25 border border-[#33CCFF]/40 text-[#33CCFF] text-[11px] font-mono font-semibold transition-colors"
          title="Open in Full Waze App"
        >
          <span>Full Waze</span>
          <ArrowUpRight className="w-3 h-3" />
        </button>
      </div>

      {/* Destination Quick Input Bar */}
      <form onSubmit={handleSearchSubmit} className="p-2 bg-[#0E1721]/80 border-b border-slate-800/80 flex items-center gap-1.5 z-10 shrink-0">
        <div className="flex-1 flex items-center gap-1.5 bg-[#090F14] px-2.5 py-1.5 rounded-lg border border-slate-700/80">
          <Search className="w-3.5 h-3.5 text-slate-400 shrink-0" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search destination in Waze..."
            className="bg-transparent text-xs text-slate-100 outline-none w-full placeholder:text-slate-500 font-medium font-mono"
          />
        </div>
        <button
          type="submit"
          className="px-2.5 py-1.5 rounded-lg bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-bold font-mono text-xs transition-colors shrink-0"
        >
          Go
        </button>
      </form>

      {/* Embedded Live Waze Map Window Container */}
      <div className="flex-1 w-full min-h-[220px] relative bg-[#090F14] overflow-hidden">
        {/* Live Waze Embed Iframe */}
        <iframe
          src={wazeEmbedUrl}
          title="Waze Live Map"
          className="w-full h-full border-0 absolute inset-0 z-0 opacity-90 hover:opacity-100 transition-opacity"
          allow="geolocation"
          onLoad={() => setIsIframeLoaded(true)}
        />

        {/* Floating In-Window HUD Overlay: Live GPS Fix & Location Pin */}
        <div className="absolute top-2 left-2 z-10 bg-[#0B1118]/85 border border-slate-700/80 rounded-lg px-2.5 py-1.5 backdrop-blur shadow-lg flex items-center gap-2 text-[10px] font-mono text-slate-300 pointer-events-none">
          <span className="w-2 h-2 rounded-full bg-emerald-400 animate-ping" />
          <span>GPS Active · Live Location</span>
          {speedGps !== null && speedGps > 0 && (
            <span className="text-cyan-400 font-bold">· {speedGps} km/h GPS</span>
          )}
        </div>

        {/* Floating Quick Action: Open Turn-by-Turn in Waze */}
        <div className="absolute bottom-2 right-2 z-10">
          <button
            onClick={openFullWazeApp}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-[#33CCFF] hover:bg-[#20bceb] text-slate-950 font-bold text-xs shadow-lg font-['Chakra_Petch'] transition-transform hover:scale-105"
          >
            <Navigation className="w-3.5 h-3.5 fill-current" />
            <span>Navigate in Waze</span>
          </button>
        </div>
      </div>

      {/* Bottom Mini-Window Info Bar */}
      <div className="px-3 py-1.5 bg-[#0E1721] border-t border-slate-800 text-[10px] font-mono text-slate-400 flex items-center justify-between z-10 shrink-0">
        <span className="flex items-center gap-1">
          <LocateFixed className="w-3 h-3 text-cyan-400" />
          <span>Live Tracking Active</span>
        </span>
        <span className="text-slate-500">Continuous In-Dash Map</span>
      </div>
    </div>
  );
};
