import React, { useState } from 'react';
import { 
  Smartphone, 
  Download, 
  Terminal, 
  Check, 
  Copy, 
  X, 
  ExternalLink,
  ShieldCheck,
  Cpu
} from 'lucide-react';

interface ApkExportModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const ApkExportModal: React.FC<ApkExportModalProps> = ({ isOpen, onClose }) => {
  const [copied, setCopied] = useState<boolean>(false);

  if (!isOpen) return null;

  const buildCommand = `cd android\n./gradlew assembleDebug\n# Output APK generated at: android/app/build/outputs/apk/debug/app-debug.apk`;

  const copyToClipboard = () => {
    navigator.clipboard.writeText(buildCommand);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 bg-black/80 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="bg-[#14212B] border border-slate-700 w-full max-w-xl rounded-2xl shadow-2xl flex flex-col max-h-[90vh] overflow-hidden font-sans">
        
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-800 bg-[#0E1721]">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-teal-500/20 flex items-center justify-center text-teal-400">
              <Smartphone className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-base font-bold text-slate-100 font-['Chakra_Petch'] tracking-wide">
                NATIVE ANDROID APK BUILD & PACKAGING
              </h2>
              <p className="text-xs text-slate-400 font-mono">
                Samsung Galaxy S24 Ultra & Android Head Unit Target
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content */}
        <div className="p-5 flex-1 overflow-y-auto space-y-4 font-mono text-xs">
          {/* Quick Notice */}
          <div className="bg-[#0E1721] border border-teal-500/30 rounded-xl p-3.5 space-y-2">
            <div className="flex items-center gap-2 text-teal-300 font-bold text-sm font-['Chakra_Petch']">
              <ShieldCheck className="w-4 h-4 text-teal-400" />
              <span>Full Android Studio Kotlin Project Ready</span>
            </div>
            <p className="text-slate-300 leading-relaxed text-[11px]">
              The native Android application has been generated in the <code className="text-cyan-400">/android</code> directory with Jetpack Compose, Bluetooth SPP/BLE stack, and <code className="text-teal-300">CATEGORY_HOME</code> intent filter to act as your car head unit launcher.
            </p>
          </div>

          {/* 1-Step Terminal Build Command */}
          <div className="space-y-1.5">
            <div className="flex items-center justify-between text-slate-400">
              <span className="font-semibold uppercase text-[11px]">Compile Debug APK via Gradle:</span>
              <button
                onClick={copyToClipboard}
                className="flex items-center gap-1 text-cyan-400 hover:text-cyan-300 transition-colors text-[11px]"
              >
                {copied ? <Check className="w-3.5 h-3.5 text-teal-400" /> : <Copy className="w-3.5 h-3.5" />}
                <span>{copied ? 'Copied' : 'Copy Command'}</span>
              </button>
            </div>

            <div className="bg-[#090F14] border border-slate-800 rounded-lg p-3 text-cyan-300 font-mono text-xs overflow-x-auto">
              <pre>{buildCommand}</pre>
            </div>
          </div>

          {/* Sideload & Installation Steps */}
          <div className="space-y-2 pt-2 border-t border-slate-800">
            <h4 className="font-bold text-slate-200 text-xs font-['Chakra_Petch'] uppercase">
              Installation onto Galaxy S24 Ultra / Car Screen:
            </h4>
            <ol className="list-decimal list-inside space-y-1.5 text-slate-400 leading-relaxed text-[11px]">
              <li>
                Run <code className="text-slate-200 bg-slate-900 px-1 py-0.5 rounded">./gradlew assembleDebug</code> in the <code className="text-slate-200">android/</code> directory.
              </li>
              <li>
                Install directly using ADB: <code className="text-slate-200 bg-slate-900 px-1 py-0.5 rounded">adb install -r android/app/build/outputs/apk/debug/app-debug.apk</code>
              </li>
              <li>
                Or copy <strong className="text-slate-200">app-debug.apk</strong> to your device and tap to install.
              </li>
              <li>
                Set as Default Launcher: Go to <strong>Settings -&gt; Apps -&gt; Default apps -&gt; Home app</strong> and choose <strong>Sportage Launcher</strong>.
              </li>
            </ol>
          </div>
        </div>

        {/* Footer */}
        <div className="px-5 py-3 border-t border-slate-800 bg-[#0E1721] flex justify-end">
          <button
            onClick={onClose}
            className="px-4 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-mono font-medium transition-colors"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
};
