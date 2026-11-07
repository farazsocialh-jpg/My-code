import { LauncherApp } from '../types/obd';

export const DEFAULT_LAUNCHER_APPS: LauncherApp[] = [
  {
    id: 'waze',
    name: 'Waze',
    category: 'Navigation',
    icon: 'Navigation',
    urlScheme: 'waze://',
    webFallback: 'https://www.waze.com/ul',
    color: '#33CCFF',
    description: 'Real-time GPS navigation, live traffic alerts and police traps'
  },
  {
    id: 'google_maps',
    name: 'Google Maps',
    category: 'Navigation',
    icon: 'MapPin',
    urlScheme: 'google.navigation:q=',
    webFallback: 'https://maps.google.com',
    color: '#4285F4',
    description: 'Satellite maps, turn-by-turn directions, and local POIs'
  },
  {
    id: 'spotify',
    name: 'Spotify',
    category: 'Media',
    icon: 'Music',
    urlScheme: 'spotify://',
    webFallback: 'https://open.spotify.com',
    color: '#1DB954',
    description: 'Stream music, driving playlists, and car podcasts'
  },
  {
    id: 'yt_music',
    name: 'YouTube Music',
    category: 'Media',
    icon: 'PlayCircle',
    urlScheme: 'youtubemusic://',
    webFallback: 'https://music.youtube.com',
    color: '#FF0000',
    description: 'Official music videos, albums, and personalized car mixes'
  },
  {
    id: 'phone',
    name: 'Phone / Dialer',
    category: 'Communication',
    icon: 'Phone',
    urlScheme: 'tel:',
    webFallback: 'tel:',
    color: '#10B981',
    description: 'Hands-free phone calling and quick contacts'
  },
  {
    id: 'radio',
    name: 'FM / Web Radio',
    category: 'Media',
    icon: 'Radio',
    urlScheme: 'tunein://',
    webFallback: 'https://tunein.com',
    color: '#8B5CF6',
    description: 'Live local radio broadcasts and sports stations'
  },
  {
    id: 'dashcam',
    name: 'Dashcam Viewer',
    category: 'Utility',
    icon: 'Video',
    urlScheme: 'dashcam://',
    webFallback: '',
    color: '#F59E0B',
    description: 'Live vehicle camera feed and incident recording'
  },
  {
    id: 'settings',
    name: 'Car Settings',
    category: 'Utility',
    icon: 'Settings',
    urlScheme: '',
    webFallback: '',
    color: '#64748B',
    description: 'System preferences, display brightness, and OBD2 config'
  }
];

export function launchApp(app: LauncherApp) {
  // Try intent / URL scheme first, fall back to web link
  if (app.urlScheme) {
    try {
      window.location.href = app.urlScheme;
      // Check if the scheme was handled by an app; if not, fall back to web
      // We use a Promise-based approach: try the scheme, wait briefly, then open web fallback
      const schemePromise = new Promise<void>((resolve) => {
        // After a short delay, assume the scheme wasn't handled and open web fallback
        const schemeCheck = setTimeout(() => {
          resolve(undefined);
        }, 500);
        // If the URL scheme is handled by an app, the navigation will complete and
        // we can clear the timeout. If not, the browser will fall through to open
        // the web fallback after the promise resolves.
        // Note: We can't directly detect if scheme was handled, so we use the delay approach.
      });

      // Wait briefly then open web fallback
      schemePromise.then(() => {
        if (app.webFallback) {
          window.open(app.webFallback, '_blank', 'noopener,noreferrer');
        }
      });
      return;
    } catch (e) {
      // Fallback
    }
  }

  if (app.webFallback) {
    window.open(app.webFallback, '_blank', 'noopener,noreferrer');
  }
}
