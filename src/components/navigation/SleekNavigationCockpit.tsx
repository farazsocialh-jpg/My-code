import React, { useState, useEffect } from 'react';
import { 
  Navigation, 
  MapPin, 
  ArrowUpRight, 
  CornerUpRight, 
  Compass, 
  LocateFixed, 
  Search,
  X,
  Check,
  Locate
} from 'lucide-react';

interface SleekNavigationCockpitProps {
  speedKmH: number | null;
}

export const SleekNavigationCockpit: React.FC<SleekNavigationCockpitProps> = ({
  speedKmH
}) => {
  // Device GPS coordinates (defaults to Doha GCC baseline while acquiring)
  const [coords, setCoords] = useState<{ lat: number; lon: number }>({
    lat: 25.2854,
    lon: 51.5310
  });
  // Stable map coordinates to avoid iframe reload churn during active driving
  const [mapCenter, setMapCenter] = useState<{ lat: number; lon: number }>({
    lat: 25.2854,
    lon: 51.5310
  });
  const [heading, setHeading] = useState<number>(34);
  const [speedAlertThreshold, setSpeedAlertThreshold] = useState<number>(100);

  // Active navigation route state - starts with NO route selected!
  const [activeDestination, setActiveDestination] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [isInputFocused, setIsInputFocused] = useState<boolean>(false);

  // Watch device GPS
  useEffect(() => {
    if (typeof navigator !== 'undefined' && navigator.geolocation) {
      const watchId = navigator.geolocation.watchPosition(
        (pos) => {
          const lat = Number(pos.coords.latitude.toFixed(5));
          const lon = Number(pos.coords.longitude.toFixed(5));
          setCoords({ lat, lon });
          if (pos.coords.heading !== null && !isNaN(pos.coords.heading)) {
            setHeading(Math.round(pos.coords.heading));
          }
        },
        () => {},
        { enableHighAccuracy: true, maximumAge: 3000 }
      );
      return () => navigator.geolocation.clearWatch(watchId);
    }
  }, []);

  const handleStartRoute = (dest: string) => {
    const trimmed = dest.trim();
    if (!trimmed) return;
    setActiveDestination(trimmed);
    const url = `https://waze.com/ul?q=${encodeURIComponent(trimmed)}&navigate=yes`;
    window.open(url, '_blank', 'noopener,noreferrer');
  };

  const handleClearRoute = () => {
    setActiveDestination(null);
    setSearchQuery('');
  };

  const openFullWaze = () => {
    let url: string;
    if (activeDestination) {
      url = `https://waze.com/ul?q=${encodeURIComponent(activeDestination)}&navigate=yes`;
    } else {
      url = `https://waze.com/ul?ll=${coords.lat},${coords.lon}&navigate=yes`;
    }
    window.open(url, '_blank', 'noopener,noreferrer');
  };

  // Embed Live Waze map centered on stable coordinates (re-centered only on explicit click)
  const wazeEmbedUrl = `https://embed.waze.com/iframe?zoom=15&lat=${mapCenter.lat}&lon=${mapCenter.lon}&ct=livemap&pin=1`;

  const displaySpeed = speedKmH !== null ? speedKmH : 0;
  const isOverspeed = speedAlertThreshold > 0 && displaySpeed > speedAlertThreshold;

  return (
    <div className="w-full h-full flex flex-col bg-[#0B1017] border border-slate-800 rounded-2xl overflow-hidden shadow-2xl relative select-none">
      
      {/* 1. TOP HEADER: Clean Search Bar (Idle) OR Active Turn-by-Turn Banner (When Routed) */}
      {activeDestination ? (
        /* ACTIVE ROUTE BANNER (Only shows when a route is actually selected!) */
        <div className="bg-[#1967D2] text-white px-3.5 py-2.5 flex items-center justify-between shadow-lg z-20 shrink-0">
          <div className="flex items-center gap-3 overflow-hidden">
            <div className="w-9 h-9 rounded-xl bg-white/20 flex items-center justify-center text-white shrink-0">
              <CornerUpRight className="w-5 h-5 stroke-[2.5]" />
            </div>
            <div className="overflow-hidden">
              <div className="flex items-baseline gap-2">
                <span className="text-xl font-black font-['Chakra_Petch'] leading-none">
                  450 m
                </span>
                <span className="text-[10px] font-mono opacity-80 uppercase tracking-wider">
                  Keep Right
                </span>
              </div>
              <p className="text-xs font-semibold truncate text-white/95 mt-0.5">
                {activeDestination}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-1.5 shrink-0 ml-2">
            <button
              onClick={openFullWaze}
              className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-white/20 hover:bg-white/30 text-white font-bold text-xs transition-colors font-mono"
              title="Open turn-by-turn in native Waze app"
            >
              <span>Waze</span>
              <ArrowUpRight className="w-3.5 h-3.5" />
            </button>
            <button
              onClick={handleClearRoute}
              className="p-1 rounded-lg bg-white/10 hover:bg-white/25 text-white/80 hover:text-white transition-colors"
              title="Clear active route"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>
      ) : (
        /* IDLE / NO ROUTE SELECTED: Clean, Flush Destination Search Header */
        <div className="bg-[#0E1722] border-b border-slate-800 p-2.5 flex flex-col gap-1.5 z-20 shrink-0">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <div className="w-6 h-6 rounded-lg bg-[#33CCFF]/20 flex items-center justify-center text-[#33CCFF]">
                <Navigation className="w-3.5 h-3.5 fill-current" />
              </div>
              <span className="text-xs font-bold font-['Chakra_Petch'] text-slate-200 uppercase tracking-wide">
                WAZE NAVIGATION
              </span>
            </div>

            <button
              onClick={openFullWaze}
              className="flex items-center gap-1 text-[11px] font-mono text-[#33CCFF] hover:underline"
            >
              <span>Open Waze App</span>
              <ArrowUpRight className="w-3 h-3" />
            </button>
          </div>

          {/* Cleanly aligned search bar (Flush in header - NO awkward floating overlay!) */}
          <form
            onSubmit={(e) => {
              e.preventDefault();
              handleStartRoute(searchQuery);
            }}
            className="flex items-center gap-1.5 w-full mt-0.5"
          >
            <div className="flex-1 flex items-center gap-2 bg-[#080C14] border border-slate-700/80 rounded-xl px-3 py-1.5 focus-within:border-cyan-400 transition-colors">
              <Search className="w-3.5 h-3.5 text-slate-400 shrink-0" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                onFocus={() => setIsInputFocused(true)}
                placeholder="Where to? (e.g. Airport, Mall, Office)..."
                className="bg-transparent text-xs text-slate-100 outline-none w-full placeholder:text-slate-500 font-medium font-mono"
              />
              {searchQuery && (
                <button
                  type="button"
                  onClick={() => setSearchQuery('')}
                  className="text-slate-500 hover:text-slate-300"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              )}
            </div>

            <button
              type="submit"
              disabled={!searchQuery.trim()}
              className="px-3.5 py-1.5 rounded-xl bg-[#33CCFF] hover:bg-[#20bceb] disabled:opacity-40 text-slate-950 font-bold font-mono text-xs transition-colors shrink-0 shadow-sm"
            >
              Route
            </button>
          </form>

          {/* Quick Frequent Destination Chips */}
          <div className="flex items-center gap-1.5 pt-0.5 overflow-x-auto no-scrollbar font-mono text-[10px]">
            <span className="text-slate-500 shrink-0">Quick:</span>
            {['Home', 'Work Office', 'Gas Station', 'Airport', 'Mall'].map((place) => (
              <button
                key={place}
                onClick={() => {
                  setSearchQuery(place);
                  handleStartRoute(place);
                }}
                className="px-2 py-0.5 rounded-md bg-[#131D2A] hover:bg-slate-700 text-slate-300 hover:text-cyan-300 border border-slate-700/60 transition-colors shrink-0"
              >
                {place}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* 2. LIVE WAZE MAP BODY WITH FLOATING SPEED & COMPASS HUD */}
      <div className="relative flex-1 w-full bg-[#070B12] overflow-hidden min-h-[220px]">
        {/* Real Waze Live Map iframe */}
        <iframe
          src={wazeEmbedUrl}
          title="Waze Navigation Map"
          className="w-full h-full border-0 absolute inset-0 opacity-95 hover:opacity-100 transition-opacity"
          allow="geolocation"
        />

        {/* Floating Top-Left Speed Limit Sign & Vehicle Speed Badge */}
        <div className="absolute top-2.5 left-2.5 z-10 flex flex-col gap-2 pointer-events-auto">
          {/* Circular Road Speed Alert Sign */}
          <div 
            onClick={() => setSpeedAlertThreshold(t => (t === 80 ? 100 : t === 100 ? 120 : t === 120 ? 140 : t === 140 ? 0 : 80))}
            className="w-11 h-11 rounded-full bg-white border-[3.5px] border-[#E02424] flex items-center justify-center shadow-xl cursor-pointer hover:scale-105 transition-transform"
            title="Speed Alert Threshold (Tap to cycle: 80 / 100 / 120 / 140 km/h / OFF)"
          >
            <span className="text-sm font-black font-['Chakra_Petch'] text-slate-950">
              {speedAlertThreshold > 0 ? speedAlertThreshold : '--'}
            </span>
          </div>

          {/* Current Speed Badge */}
          <div className={`w-11 h-11 rounded-full flex flex-col items-center justify-center shadow-xl border ${
            isOverspeed 
              ? 'bg-[#E02424] text-white border-white animate-pulse' 
              : 'bg-[#0B131E]/95 text-cyan-300 border-slate-700/80 backdrop-blur'
          }`}>
            <span className="text-sm font-black font-['Chakra_Petch'] leading-none">
              {displaySpeed}
            </span>
            <span className="text-[7px] font-mono uppercase font-bold text-slate-400">
              km/h
            </span>
          </div>
        </div>

        {/* Floating Top-Right Map Controls: Compass & Recenter */}
        <div className="absolute top-2.5 right-2.5 z-10 flex flex-col gap-2">
          <div
            className="w-8 h-8 rounded-full bg-[#0E1722]/90 border border-slate-700/80 text-cyan-400 flex items-center justify-center shadow-lg"
            title={`Heading: ${heading}°`}
          >
            <Compass className="w-4 h-4" />
          </div>

          <button
            onClick={() => setMapCenter({ lat: coords.lat, lon: coords.lon })}
            className="w-8 h-8 rounded-full bg-[#0E1722]/90 hover:bg-slate-800 border border-slate-700/80 text-cyan-400 flex items-center justify-center shadow-lg transition-transform hover:scale-105"
            title="Re-center Map on Vehicle GPS"
          >
            <LocateFixed className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* 3. BOTTOM BAR: Live GPS Coordinates (Cruising) OR Trip ETA (Active Route) */}
      <div className="bg-[#0E1722] border-t border-slate-800 px-3.5 py-2 flex items-center justify-between text-xs font-mono shrink-0 z-20">
        {activeDestination ? (
          /* ACTIVE ROUTE ETA */
          <>
            <div className="flex items-center gap-2.5 text-xs">
              <div className="flex items-center gap-1.5 text-emerald-400 font-bold">
                <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
                <span>14 min</span>
              </div>
              <span className="text-slate-600">·</span>
              <span className="text-slate-300">12.4 km</span>
              <span className="text-slate-600">·</span>
              <span className="text-slate-400">ETA 22:45</span>
            </div>

            <button
              onClick={openFullWaze}
              className="flex items-center gap-1 px-3 py-1 rounded-lg bg-[#33CCFF] hover:bg-[#20bceb] text-slate-950 font-bold text-xs font-['Chakra_Petch'] transition-all shadow-md"
            >
              <Navigation className="w-3.5 h-3.5 fill-current" />
              <span>Start in Waze</span>
            </button>
          </>
        ) : (
          /* CRUISING / NO ROUTE SELECTED: Real GPS Location & Status */
          <>
            <div className="flex items-center gap-2 text-[11px] text-slate-400">
              <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
              <span className="font-semibold text-slate-300">GPS Live:</span>
              <span>{coords.lat.toFixed(4)}°N, {coords.lon.toFixed(4)}°E</span>
              <span className="text-slate-600">·</span>
              <span>Heading {heading}°</span>
            </div>

            <button
              onClick={openFullWaze}
              className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-slate-800 hover:bg-slate-700 text-cyan-300 text-[11px] transition-colors"
            >
              <span>Explore in Waze</span>
              <ArrowUpRight className="w-3 h-3" />
            </button>
          </>
        )}
      </div>
    </div>
  );
};
