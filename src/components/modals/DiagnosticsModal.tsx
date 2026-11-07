import React, { useState } from 'react';
import { obdEngine } from '../../services/obdEngine';
import { DtcFault, ConnectionState } from '../../types/obd';
import { 
  AlertTriangle, 
  CheckCircle2, 
  Trash2, 
  RefreshCw, 
  X, 
  ShieldAlert, 
  Info 
} from 'lucide-react';

interface DiagnosticsModalProps {
  isOpen: boolean;
  onClose: () => void;
  connectionState: ConnectionState;
}

export const DiagnosticsModal: React.FC<DiagnosticsModalProps> = ({
  isOpen,
  onClose,
  connectionState
}) => {
  const [dtcs, setDtcs] = useState<DtcFault[]>([]);
  const [isScanning, setIsScanning] = useState<boolean>(false);
  const [isClearing, setIsClearing] = useState<boolean>(false);
  const [showClearConfirm, setShowClearConfirm] = useState<boolean>(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleScanDtcs = async () => {
    if (connectionState !== 'CONNECTED') {
      setStatusMessage('Please connect your Bluetooth ELM327 adapter before scanning.');
      return;
    }
    setIsScanning(true);
    setStatusMessage('Querying ECM Mode 03 for stored diagnostic trouble codes...');
    try {
      const results = await obdEngine.readDtcCodes();
      setDtcs(results);
      if (results.length === 0) {
        setStatusMessage('No fault codes reported by ECM! System is healthy.');
      } else {
        setStatusMessage(`Found ${results.length} active fault code(s).`);
      }
    } catch (e: any) {
      setStatusMessage(`Scan failed: ${e.message}`);
    } finally {
      setIsScanning(false);
    }
  };

  const handleConfirmClear = async () => {
    setIsClearing(true);
    setStatusMessage('Dispatching Mode 04 clear command to ECM...');
    try {
      const ok = await obdEngine.clearDtcCodes();
      if (ok) {
        setDtcs([]);
        setStatusMessage('Fault codes and readiness flags have been successfully reset by ECM.');
        setShowClearConfirm(false);
      } else {
        setStatusMessage('Failed to clear DTCs or no response from ECM.');
      }
    } catch (e: any) {
      setStatusMessage(`Error: ${e.message}`);
    } finally {
      setIsClearing(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 bg-black/80 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="bg-[#14212B] border border-slate-700 w-full max-w-xl rounded-xl shadow-2xl flex flex-col max-h-[85vh] overflow-hidden">
        
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-800 bg-[#0E1721]">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded bg-rose-500/20 flex items-center justify-center text-rose-400">
              <AlertTriangle className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-base font-bold text-slate-100 font-['Chakra_Petch'] tracking-wide">
                VEHICLE DIAGNOSTICS (DTC SCANNER)
              </h2>
              <p className="text-xs text-slate-400 font-mono">
                SAE J1979 Mode 03 Stored Trouble Codes & Mode 04 Reset
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
        <div className="p-5 flex-1 overflow-y-auto space-y-4">
          {/* Action Bar */}
          <div className="flex items-center justify-between gap-3">
            <button
              onClick={handleScanDtcs}
              disabled={isScanning || connectionState !== 'CONNECTED'}
              className="flex items-center gap-2 px-4 py-2 rounded-lg bg-cyan-500 hover:bg-cyan-400 disabled:opacity-40 text-slate-950 font-bold font-mono text-xs transition-colors shadow-md"
            >
              <RefreshCw className={`w-4 h-4 ${isScanning ? 'animate-spin' : ''}`} />
              <span>{isScanning ? 'Querying ECM...' : 'Scan DTC Codes'}</span>
            </button>

            {dtcs.length > 0 && !showClearConfirm && (
              <button
                onClick={() => setShowClearConfirm(true)}
                disabled={connectionState !== 'CONNECTED'}
                className="flex items-center gap-1.5 px-3.5 py-2 rounded-lg bg-rose-950/80 hover:bg-rose-900 border border-rose-500/40 text-rose-300 font-mono text-xs font-semibold transition-colors"
              >
                <Trash2 className="w-3.5 h-3.5" />
                <span>Clear Codes</span>
              </button>
            )}
          </div>

          {statusMessage && (
            <div className="bg-[#0E1721] border border-slate-800 rounded-lg p-3 text-xs font-mono text-cyan-300 flex items-center gap-2">
              <Info className="w-4 h-4 text-cyan-400 shrink-0" />
              <span>{statusMessage}</span>
            </div>
          )}

          {/* Clear Confirmation Prompt */}
          {showClearConfirm && (
            <div className="bg-rose-950/60 border border-rose-500/50 rounded-xl p-4 text-xs font-mono space-y-3">
              <div className="flex items-center gap-2 text-rose-300 font-bold text-sm">
                <ShieldAlert className="w-5 h-5 text-rose-400" />
                <span>CONFIRM CLEAR EMISSIONS DATA</span>
              </div>
              <p className="text-rose-200 leading-relaxed">
                Clearing codes will reset all stored trouble codes, freeze frame snapshots, and I/M readiness monitors on your Kia Sportage. Only perform when the vehicle is parked.
              </p>
              <div className="flex items-center gap-2 pt-1">
                <button
                  onClick={handleConfirmClear}
                  disabled={isClearing}
                  className="px-4 py-2 rounded bg-rose-600 hover:bg-rose-500 text-white font-bold transition-colors"
                >
                  {isClearing ? 'Clearing...' : 'Yes, Clear Codes Now'}
                </button>
                <button
                  onClick={() => setShowClearConfirm(false)}
                  className="px-3 py-2 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 transition-colors"
                >
                  Cancel
                </button>
              </div>
            </div>
          )}

          {/* DTC List Display */}
          {dtcs.length === 0 ? (
            <div className="bg-[#0E1721] rounded-xl p-8 border border-slate-800/80 text-center flex flex-col items-center justify-center">
              <CheckCircle2 className="w-12 h-12 text-teal-400/80 mb-2" />
              <p className="text-sm font-bold text-slate-200 font-['Chakra_Petch']">
                No Diagnostic Trouble Codes
              </p>
              <p className="text-xs text-slate-500 font-mono mt-1 max-w-sm">
                {connectionState === 'CONNECTED'
                  ? 'Tap "Scan DTC Codes" above to read any pending or active faults from your car ECU.'
                  : 'Connect your Bluetooth ELM327 adapter to run an active scan.'}
              </p>
            </div>
          ) : (
            <div className="space-y-3">
              {dtcs.map((dtc) => (
                <div key={dtc.code} className="bg-[#0E1721] border border-rose-500/40 rounded-lg p-4 font-mono text-xs space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-lg font-bold text-rose-400 font-['Chakra_Petch']">{dtc.code}</span>
                    <span className="px-2 py-0.5 rounded bg-rose-950 text-rose-300 font-semibold uppercase text-[10px] border border-rose-500/30">
                      {dtc.status}
                    </span>
                  </div>
                  <p className="text-slate-300 font-medium">{dtc.description}</p>
                </div>
              ))}
            </div>
          )}
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
