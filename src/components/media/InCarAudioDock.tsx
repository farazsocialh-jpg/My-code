import React, { useState, useEffect, useRef } from 'react';
import { 
  Play, 
  Pause, 
  SkipBack, 
  SkipForward, 
  Radio, 
  Music, 
  Disc, 
  Volume2, 
  VolumeX,
  ExternalLink,
  FolderOpen,
  Signal,
  RotateCcw,
  Sliders
} from 'lucide-react';
import { 
  inCarAudioService, 
  AudioSourceType, 
  LocalTrack, 
  RadioStation 
} from '../../services/inCarAudioService';

interface InCarAudioDockProps {
  onOpenSettings?: () => void;
}

export const InCarAudioDock: React.FC<InCarAudioDockProps> = () => {
  const [source, setSource] = useState<AudioSourceType>(inCarAudioService.getSource());
  const [isPlaying, setIsPlaying] = useState<boolean>(inCarAudioService.getIsPlaying());
  
  // Local Music
  const [currentTrack, setCurrentTrack] = useState<LocalTrack>(inCarAudioService.getCurrentTrack());
  const [currentTime, setCurrentTime] = useState<number>(inCarAudioService.getCurrentTime());
  
  // Radio
  const [frequency, setFrequency] = useState<number>(inCarAudioService.getFrequency());
  const [station, setStation] = useState<RadioStation>(inCarAudioService.getStation());
  const [band, setBand] = useState<'FM1' | 'FM2' | 'FM3' | 'AM'>(inCarAudioService.getBand());
  const [presets, setPresets] = useState<RadioStation[]>(inCarAudioService.getPresets());

  const fileInputRef = useRef<HTMLInputElement>(null);

  // Subscribe to inCarAudioService
  useEffect(() => {
    const unsub = inCarAudioService.subscribe(() => {
      setSource(inCarAudioService.getSource());
      setIsPlaying(inCarAudioService.getIsPlaying());
      setCurrentTrack(inCarAudioService.getCurrentTrack());
      setCurrentTime(inCarAudioService.getCurrentTime());
      setFrequency(inCarAudioService.getFrequency());
      setStation(inCarAudioService.getStation());
      setBand(inCarAudioService.getBand());
      setPresets(inCarAudioService.getPresets());
    });

    return () => unsub();
  }, []);

  // Format time mm:ss
  const formatTime = (secs: number) => {
    const m = Math.floor(secs / 60);
    const s = Math.floor(secs % 60);
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  };

  const handleTogglePlay = () => {
    inCarAudioService.togglePlay();
  };

  const handlePrev = () => {
    inCarAudioService.previous();
  };

  const handleNext = () => {
    inCarAudioService.next();
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      inCarAudioService.loadUserFiles(e.target.files);
    }
  };

  return (
    <div className="w-full flex items-center justify-between gap-2 overflow-hidden select-none font-mono text-xs">
      
      {/* 1. Mode Switcher (Local Music <-> FM Radio) */}
      <div className="flex items-center gap-1 bg-[#0A0E17] p-1 rounded-xl border border-slate-800 shrink-0">
        <button
          onClick={() => inCarAudioService.setSource('LOCAL_MUSIC')}
          className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[11px] font-bold font-['Chakra_Petch'] transition-all ${
            source === 'LOCAL_MUSIC'
              ? 'bg-cyan-500 text-slate-950 shadow-[0_0_12px_rgba(6,182,212,0.5)]'
              : 'text-slate-400 hover:text-white hover:bg-slate-800/40'
          }`}
          title="Play from Local Android Music App or USB Drive"
        >
          <Music className="w-3.5 h-3.5" />
          <span>Local Music</span>
        </button>

        <button
          onClick={() => inCarAudioService.setSource('RADIO')}
          className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[11px] font-bold font-['Chakra_Petch'] transition-all ${
            source === 'RADIO'
              ? 'bg-cyan-500 text-slate-950 shadow-[0_0_12px_rgba(6,182,212,0.5)]'
              : 'text-slate-400 hover:text-white hover:bg-slate-800/40'
          }`}
          title="Head Unit FM / AM Tuner & Radio Stations"
        >
          <Radio className="w-3.5 h-3.5" />
          <span>FM Radio</span>
        </button>
      </div>

      {/* 2. Media Controls: Prev, Play/Pause, Next */}
      <div className="flex items-center gap-1 shrink-0">
        <button
          onClick={handlePrev}
          className="p-1.5 rounded-lg bg-[#0E1520] hover:bg-slate-800 text-slate-300 hover:text-white border border-slate-800 transition-colors"
          title={source === 'RADIO' ? 'Tune -0.1 MHz' : 'Previous Track'}
        >
          <SkipBack className="w-4 h-4" />
        </button>

        <button
          onClick={handleTogglePlay}
          className="w-8 h-8 rounded-full bg-cyan-400 hover:bg-cyan-300 text-slate-950 flex items-center justify-center font-bold shadow-[0_0_12px_rgba(77,240,255,0.45)] transition-transform hover:scale-105 active:scale-95"
          title={isPlaying ? 'Pause' : 'Play'}
        >
          {isPlaying ? (
            <Pause className="w-3.5 h-3.5 fill-current" />
          ) : (
            <Play className="w-3.5 h-3.5 fill-current ml-0.5" />
          )}
        </button>

        <button
          onClick={handleNext}
          className="p-1.5 rounded-lg bg-[#0E1520] hover:bg-slate-800 text-slate-300 hover:text-white border border-slate-800 transition-colors"
          title={source === 'RADIO' ? 'Tune +0.1 MHz' : 'Next Track'}
        >
          <SkipForward className="w-4 h-4" />
        </button>
      </div>

      {/* 3. Center In-Car Display: Dynamic between Local Music & FM Radio */}
      <div className="flex-1 min-w-0 bg-[#0A0E17] px-3 py-1.5 rounded-xl border border-slate-800/90 flex items-center justify-between gap-3 overflow-hidden">
        
        {source === 'RADIO' ? (
          /* ================= FM RADIO STATION DISPLAY ================= */
          <div className="w-full flex items-center justify-between gap-2 overflow-hidden">
            
            {/* Left: Frequency Digit & RDS Station Info */}
            <div className="flex items-center gap-2.5 overflow-hidden">
              {/* Frequency Badge */}
              <div className="flex items-baseline gap-1 px-2.5 py-1 rounded-lg bg-cyan-950/70 border border-cyan-500/40 text-cyan-300 font-bold font-['Chakra_Petch'] text-sm tracking-wide shrink-0 shadow-inner">
                <span className="text-base text-cyan-300">{frequency.toFixed(1)}</span>
                <span className="text-[10px] text-cyan-500 font-mono">MHz</span>
              </div>

              {/* Station Name & RDS Radio Text */}
              <div className="overflow-hidden min-w-0">
                <div className="flex items-center gap-2">
                  <span className="text-xs font-bold text-white truncate font-['Chakra_Petch']">
                    {station.name}
                  </span>
                  <span className="px-1 py-0.2 rounded text-[9px] bg-cyan-950 text-cyan-400 border border-cyan-500/30 shrink-0 font-mono">
                    {band}
                  </span>
                  {/* Signal bars */}
                  <div className="flex items-end gap-0.5 h-3 shrink-0" title="Signal Strength: 5/5 Full">
                    <span className="w-0.5 h-1.5 bg-emerald-400 rounded-sm" />
                    <span className="w-0.5 h-2 bg-emerald-400 rounded-sm" />
                    <span className="w-0.5 h-2.5 bg-emerald-400 rounded-sm" />
                    <span className="w-0.5 h-3 bg-emerald-400 rounded-sm" />
                  </div>
                </div>
                <p className="text-[10px] text-slate-400 truncate font-mono">
                  {station.genre} · {station.rdsText}
                </p>
              </div>
            </div>

            {/* Right: Radio Tuning Controls & Preset Pills */}
            <div className="flex items-center gap-1.5 shrink-0">
              {/* Seek Down */}
              <button
                onClick={() => inCarAudioService.seek('DOWN')}
                className="px-1.5 py-1 rounded bg-[#0E1520] hover:bg-slate-800 text-[10px] text-slate-400 hover:text-white border border-slate-800"
                title="Auto Seek Down"
              >
                &laquo; Seek
              </button>

              {/* Preset Buttons */}
              <div className="hidden sm:flex items-center gap-1 font-mono text-[10px]">
                {presets.slice(0, 5).map((p, idx) => (
                  <button
                    key={p.id}
                    onClick={() => inCarAudioService.selectPreset(p)}
                    className={`px-1.5 py-1 rounded text-[10px] font-bold transition-all ${
                      Math.abs(p.frequency - frequency) < 0.15
                        ? 'bg-cyan-500 text-slate-950 shadow-sm'
                        : 'bg-[#0E1520] text-slate-400 hover:text-white border border-slate-800/80'
                    }`}
                    title={`P${idx + 1}: ${p.name} (${p.frequency.toFixed(1)} MHz)`}
                  >
                    {p.frequency.toFixed(1)}
                  </button>
                ))}
              </div>

              {/* Seek Up */}
              <button
                onClick={() => inCarAudioService.seek('UP')}
                className="px-1.5 py-1 rounded bg-[#0E1520] hover:bg-slate-800 text-[10px] text-slate-400 hover:text-white border border-slate-800"
                title="Auto Seek Up"
              >
                Seek &raquo;
              </button>

              {/* Native Android Radio App Button */}
              <button
                onClick={() => inCarAudioService.launchNativeRadioApp()}
                className="p-1 rounded bg-[#0E1520] hover:bg-cyan-500/20 text-cyan-400 border border-cyan-500/30 ml-1 transition-colors"
                title="Launch Android Head Unit Native FM Radio App"
              >
                <ExternalLink className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        ) : (
          /* ================= LOCAL MUSIC DISPLAY ================= */
          <div className="w-full flex items-center justify-between gap-3 overflow-hidden">
            
            {/* Left: Animated Album Art / Disc + Track Metadata */}
            <div className="flex items-center gap-2.5 overflow-hidden flex-1 min-w-0">
              <div className="w-8 h-8 rounded-lg bg-cyan-950/70 border border-cyan-500/30 flex items-center justify-center text-cyan-400 shrink-0">
                <Disc className={`w-4 h-4 ${isPlaying ? 'animate-spin' : ''}`} style={{ animationDuration: '4s' }} />
              </div>

              <div className="overflow-hidden min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <span className="text-xs font-bold text-white truncate font-['Chakra_Petch']">
                    {currentTrack.title}
                  </span>
                  <span className="text-[9px] text-cyan-400 px-1 py-0.2 rounded bg-cyan-950/80 border border-cyan-500/30 shrink-0">
                    {currentTrack.sourceType === 'USB_DRIVE' ? 'USB Drive' : 'Local Music App'}
                  </span>
                </div>
                <p className="text-[10px] text-slate-400 truncate font-mono">
                  {currentTrack.artist} · {currentTrack.album}
                </p>
              </div>
            </div>

            {/* Right: Local Actions (Load USB/MP3 Files + Open Native Music App) */}
            <div className="flex items-center gap-1.5 shrink-0">
              {/* Hidden file input for USB/Storage audio loading */}
              <input
                ref={fileInputRef}
                type="file"
                accept="audio/*,.mp3,.flac,.wav,.m4a"
                multiple
                className="hidden"
                onChange={handleFileChange}
              />

              {/* Load local files button */}
              <button
                onClick={() => fileInputRef.current?.click()}
                className="flex items-center gap-1 px-2 py-1 rounded bg-[#0E1520] hover:bg-slate-800 text-[10px] text-slate-300 hover:text-white border border-slate-800 transition-colors"
                title="Select MP3s from USB Flash Drive or Internal Storage"
              >
                <FolderOpen className="w-3.5 h-3.5 text-cyan-400" />
                <span className="hidden sm:inline">Load USB / MP3</span>
              </button>

              {/* Open Android Head Unit Native Music App */}
              <button
                onClick={() => inCarAudioService.launchNativeLocalMusicApp()}
                className="p-1.5 rounded bg-[#0E1520] hover:bg-cyan-500/20 text-cyan-400 border border-cyan-500/30 transition-colors"
                title="Launch Android Head Unit Native Music App"
              >
                <ExternalLink className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
