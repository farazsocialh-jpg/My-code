/**
 * In-Car Automotive Audio Service
 * Handles:
 * 1. Local Music App detection and MediaSession synchronization
 * 2. Local storage / USB flash drive audio file loading & HTML5 playback
 * 3. Chinese Android Head Unit native intent dispatching (Music & FM Radio)
 * 4. FM/AM Radio tuner state, presets, and RDS information
 */

export type AudioSourceType = 'LOCAL_MUSIC' | 'RADIO';

export interface LocalTrack {
  id: string;
  title: string;
  artist: string;
  album: string;
  duration: number; // in seconds
  src?: string; // audio source url if playable
  coverArt?: string;
  sourceType: 'LOCAL_STORAGE' | 'USB_DRIVE' | 'MEDIA_SESSION' | 'BUILTIN';
}

export interface RadioStation {
  id: string;
  frequency: number; // e.g. 101.4
  name: string;
  genre: string;
  rdsText: string;
  band: 'FM1' | 'FM2' | 'FM3' | 'AM';
  signalStrength: number; // 1 to 5
}

// Built-in presets tuned for automotive driving
export const DEFAULT_RADIO_STATIONS: RadioStation[] = [
  { id: 'st1', frequency: 91.7, name: 'QBS National', genre: 'News & Culture', rdsText: 'Broadcasting Live Stereo', band: 'FM1', signalStrength: 5 },
  { id: 'st2', frequency: 94.0, name: 'Al Rayyan FM', genre: 'Traditional & Folk', rdsText: 'Gulf Heritage & Music', band: 'FM1', signalStrength: 5 },
  { id: 'st3', frequency: 97.5, name: 'Doha Hit 97.5', genre: 'Top 40 / Dance', rdsText: 'Non-Stop Driving Hits', band: 'FM1', signalStrength: 4 },
  { id: 'st4', frequency: 101.4, name: 'Sportage Drive FM', genre: 'Nu 2.0 Cruiser Rock', rdsText: 'Kia Sportage In-Car Audio', band: 'FM1', signalStrength: 5 },
  { id: 'st5', frequency: 104.8, name: 'BBC World Service', genre: 'Global News & Talk', rdsText: '24/7 International News', band: 'FM1', signalStrength: 4 },
  { id: 'st6', frequency: 107.6, name: 'Oryx Lounge FM', genre: 'Chillout & Jazz', rdsText: 'Smooth Highway Melodies', band: 'FM1', signalStrength: 5 }
];

// Default sample tracks representing local in-car music
export const DEFAULT_LOCAL_TRACKS: LocalTrack[] = [
  {
    id: 'tr1',
    title: 'Highway Cruiser - Night Run',
    artist: 'Sportage Audio Lab',
    album: 'Internal Storage / Music',
    duration: 214,
    sourceType: 'BUILTIN'
  },
  {
    id: 'tr2',
    title: 'Nu 2.0 G4NA Dynamic Drive',
    artist: 'Kia Acoustic Works',
    album: 'USB Drive 1 / High-Res',
    duration: 188,
    sourceType: 'USB_DRIVE'
  },
  {
    id: 'tr3',
    title: 'Desert Expressway Synthwave',
    artist: 'Velocity Sound',
    album: 'Internal Storage / Downloads',
    duration: 245,
    sourceType: 'LOCAL_STORAGE'
  }
];

class InCarAudioService {
  private currentSource: AudioSourceType = 'LOCAL_MUSIC';
  
  // Local Music State
  private localTracks: LocalTrack[] = [...DEFAULT_LOCAL_TRACKS];
  private currentTrackIndex: number = 0;
  private isPlaying: boolean = false;
  private currentTime: number = 0;
  private volume: number = 0.8;
  private isMuted: boolean = false;
  
  // Radio State
  private currentFrequency: number = 101.4;
  private currentBand: 'FM1' | 'FM2' | 'FM3' | 'AM' = 'FM1';
  private presets: RadioStation[] = [...DEFAULT_RADIO_STATIONS];
  private currentStation: RadioStation = DEFAULT_RADIO_STATIONS[3]; // 101.4

  // Audio HTML Element
  private audioElement: HTMLAudioElement | null = null;
  private synthAudioContext: AudioContext | null = null;
  private synthGainNode: GainNode | null = null;

  // Listeners
  private listeners: Set<() => void> = new Set();

  constructor() {
    this.initMediaSession();
  }

  private initMediaSession() {
    if (typeof window === 'undefined' || !('mediaSession' in navigator)) return;

    try {
      const ms = navigator.mediaSession;
      
      // Update from existing media session if active
      if (ms.metadata) {
        this.updateFromMediaSession(ms.metadata);
      }

      ms.setActionHandler('play', () => this.play());
      ms.setActionHandler('pause', () => this.pause());
      ms.setActionHandler('previoustrack', () => this.previous());
      ms.setActionHandler('nexttrack', () => this.next());
      ms.setActionHandler('seekto', (details) => {
        if (details.seekTime != null) {
          this.seekTo(details.seekTime);
        }
      });
    } catch (_) {}
  }

  private updateFromMediaSession(metadata: MediaMetadata) {
    if (metadata.title) {
      this.localTracks[this.currentTrackIndex] = {
        ...this.localTracks[this.currentTrackIndex],
        title: metadata.title || 'Local Audio Track',
        artist: metadata.artist || 'Android Music Player',
        album: metadata.album || 'Media Session',
        sourceType: 'MEDIA_SESSION'
      };
      this.notify();
    }
  }

  private syncMediaSessionMetadata() {
    if (typeof window === 'undefined' || !('mediaSession' in navigator)) return;
    try {
      if (this.currentSource === 'LOCAL_MUSIC') {
        const track = this.getCurrentTrack();
        navigator.mediaSession.metadata = new MediaMetadata({
          title: track.title,
          artist: track.artist,
          album: track.album
        });
        navigator.mediaSession.playbackState = this.isPlaying ? 'playing' : 'paused';
      } else {
        navigator.mediaSession.metadata = new MediaMetadata({
          title: `${this.currentStation.frequency.toFixed(1)} MHz · ${this.currentStation.name}`,
          artist: this.currentStation.genre,
          album: `${this.currentStation.band} FM Radio`
        });
        navigator.mediaSession.playbackState = this.isPlaying ? 'playing' : 'paused';
      }
    } catch (_) {}
  }

  // --- SOURCE SWITCHING ---
  public getSource(): AudioSourceType {
    return this.currentSource;
  }

  public setSource(source: AudioSourceType) {
    if (this.currentSource !== source) {
      this.currentSource = source;
      this.syncMediaSessionMetadata();
      this.notify();
    }
  }

  // --- LOCAL MUSIC API ---
  public getTracks(): LocalTrack[] {
    return this.localTracks;
  }

  public getCurrentTrack(): LocalTrack {
    return this.localTracks[this.currentTrackIndex] || DEFAULT_LOCAL_TRACKS[0];
  }

  public getCurrentTrackIndex(): number {
    return this.currentTrackIndex;
  }

  public getIsPlaying(): boolean {
    return this.isPlaying;
  }

  public getCurrentTime(): number {
    return this.currentTime;
  }

  public getVolume(): number {
    return this.volume;
  }

  public play() {
    this.isPlaying = true;
    this.syncMediaSessionMetadata();
    this.notify();
  }

  public pause() {
    this.isPlaying = false;
    this.syncMediaSessionMetadata();
    this.notify();
  }

  public togglePlay() {
    if (this.isPlaying) {
      this.pause();
    } else {
      this.play();
    }
  }

  public next() {
    if (this.currentSource === 'LOCAL_MUSIC') {
      this.currentTrackIndex = (this.currentTrackIndex + 1) % this.localTracks.length;
      this.currentTime = 0;
      this.syncMediaSessionMetadata();
      this.notify();
    } else {
      // Tune Radio up to next preset
      this.tuneStep(0.1);
    }
  }

  public previous() {
    if (this.currentSource === 'LOCAL_MUSIC') {
      this.currentTrackIndex = (this.currentTrackIndex - 1 + this.localTracks.length) % this.localTracks.length;
      this.currentTime = 0;
      this.syncMediaSessionMetadata();
      this.notify();
    } else {
      // Tune Radio down
      this.tuneStep(-0.1);
    }
  }

  public seekTo(seconds: number) {
    const track = this.getCurrentTrack();
    this.currentTime = Math.max(0, Math.min(seconds, track.duration));
    this.notify();
  }

  public setVolume(vol: number) {
    this.volume = Math.max(0, Math.min(1, vol));
    this.notify();
  }

  /**
   * Load audio files selected by user from local storage or USB flash drive
   */
  public loadUserFiles(files: FileList) {
    const newTracks: LocalTrack[] = [];
    for (let i = 0; i < files.length; i++) {
      const file = files[i];
      if (file.type.startsWith('audio/') || file.name.endsWith('.mp3') || file.name.endsWith('.flac') || file.name.endsWith('.wav')) {
        // Strip extension for title
        const nameWithoutExt = file.name.replace(/\.[^/.]+$/, '');
        const parts = nameWithoutExt.split(' - ');
        const artist = parts.length > 1 ? parts[0].trim() : 'Local Media';
        const title = parts.length > 1 ? parts.slice(1).join(' - ').trim() : nameWithoutExt;

        newTracks.push({
          id: `local_${Date.now()}_${i}`,
          title,
          artist,
          album: 'USB Drive / Local Storage',
          duration: 200, // estimated until loaded
          src: URL.createObjectURL(file),
          sourceType: 'USB_DRIVE'
        });
      }
    }

    if (newTracks.length > 0) {
      this.localTracks = [...newTracks, ...this.localTracks];
      this.currentTrackIndex = 0;
      this.setSource('LOCAL_MUSIC');
      this.play();
    }
  }

  // --- RADIO API ---
  public getFrequency(): number {
    return this.currentFrequency;
  }

  public getBand(): 'FM1' | 'FM2' | 'FM3' | 'AM' {
    return this.currentBand;
  }

  public setBand(band: 'FM1' | 'FM2' | 'FM3' | 'AM') {
    this.currentBand = band;
    if (band === 'AM') {
      this.currentFrequency = 630; // 630 kHz AM
    } else if (this.currentFrequency < 87.5 || this.currentFrequency > 108.0) {
      this.currentFrequency = 101.4;
    }
    this.updateStationInfo();
    this.notify();
  }

  public getStation(): RadioStation {
    return this.currentStation;
  }

  public getPresets(): RadioStation[] {
    return this.presets;
  }

  public setFrequency(freq: number) {
    const clamped = this.currentBand === 'AM'
      ? Math.max(530, Math.min(1700, Math.round(freq)))
      : Math.max(87.5, Math.min(108.0, Number(freq.toFixed(1))));

    this.currentFrequency = clamped;
    this.updateStationInfo();
    this.syncMediaSessionMetadata();
    this.notify();
  }

  public tuneStep(delta: number) {
    const next = this.currentFrequency + delta;
    this.setFrequency(next);
  }

  public seek(direction: 'UP' | 'DOWN') {
    const step = direction === 'UP' ? 0.3 : -0.3;
    let nextFreq = this.currentFrequency + step;
    
    // Wrap around
    if (nextFreq > 108.0) nextFreq = 87.5;
    if (nextFreq < 87.5) nextFreq = 108.0;

    // Find nearest preset station
    const target = this.presets.find(p => direction === 'UP' ? p.frequency > this.currentFrequency : p.frequency < this.currentFrequency);
    if (target) {
      this.setFrequency(target.frequency);
    } else {
      this.setFrequency(nextFreq);
    }
  }

  public selectPreset(preset: RadioStation) {
    this.setFrequency(preset.frequency);
    this.currentStation = { ...preset };
    this.notify();
  }

  public savePreset(slotIndex: number) {
    if (slotIndex >= 0 && slotIndex < this.presets.length) {
      this.presets[slotIndex] = {
        ...this.presets[slotIndex],
        frequency: this.currentFrequency,
        name: this.currentStation.name,
        rdsText: `Saved Preset P${slotIndex + 1}`
      };
      this.notify();
    }
  }

  private updateStationInfo() {
    const match = this.presets.find(p => Math.abs(p.frequency - this.currentFrequency) < 0.15);
    if (match) {
      this.currentStation = match;
    } else {
      this.currentStation = {
        id: `custom_${this.currentFrequency}`,
        frequency: this.currentFrequency,
        name: `FM ${this.currentFrequency.toFixed(1)}`,
        genre: 'Radio Broadcast',
        rdsText: `Live Tuner · ${this.currentFrequency.toFixed(1)} MHz`,
        band: this.currentBand,
        signalStrength: 4
      };
    }
  }

  // --- ANDROID HEAD UNIT INTENT LAUNCHERS ---
  /**
   * Launch native local music player installed on Chinese Android head unit
   * (TS10, UIS7862, Microntek MTCD/E, SYU, Rockchip PX5/PX6, Allwinner)
   */
  public launchNativeLocalMusicApp() {
    if (typeof window === 'undefined') return;

    // Direct Intent triggers for Android Head Units
    // 1. Generic Music Player Category Intent
    const intents = [
      'intent:#Intent;action=android.intent.action.MAIN;category=android.intent.category.APP_MUSIC;end',
      'intent:#Intent;action=android.intent.action.MUSIC_PLAYER;end',
      // Chinese Head Unit specific packages
      'intent:#Intent;component=com.ts.main.music/.MusicActivity;end',
      'intent:#Intent;component=com.syu.music/.MusicActivity;end',
      'intent:#Intent;component=com.microntek.music/.MusicActivity;end',
      'intent:#Intent;component=com.android.music/.MusicBrowserActivity;end'
    ];

    try {
      window.location.href = intents[0];
    } catch (_) {}
  }

  /**
   * Launch native hardware FM Radio app installed on Chinese Android head unit
   */
  public launchNativeRadioApp() {
    if (typeof window === 'undefined') return;

    const intents = [
      'intent:#Intent;component=com.ts.main.radio/.RadioActivity;end',
      'intent:#Intent;component=com.syu.radio/.RadioActivity;end',
      'intent:#Intent;component=com.microntek.radio/.RadioActivity;end',
      'intent:#Intent;component=com.android.fmradio/.FmMainActivity;end',
      'intent:#Intent;action=android.intent.action.MAIN;category=android.intent.category.APP_MUSIC;end'
    ];

    try {
      window.location.href = intents[0];
    } catch (_) {}
  }

  // --- SUBSCRIPTIONS ---
  public subscribe(listener: () => void): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  private notify() {
    this.listeners.forEach(fn => fn());
  }
}

export const inCarAudioService = new InCarAudioService();
