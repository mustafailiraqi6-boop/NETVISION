import React, { useState, useEffect, useRef } from 'react';
import {
  Play,
  Square,
  Radar,
  Radio,
  Plus,
  Trash2,
  Check,
  AlertTriangle,
  Server,
  Wifi,
  ChevronRight,
  ShieldAlert
} from 'lucide-react';
import { NetworkDevice, NetworkRange, AppLanguage } from '../../types/network';
import { TRANSLATIONS, PRESET_RANGES, SAMPLE_DEVICES } from '../../data/sampleData';

interface ScannerTabProps {
  language: AppLanguage;
  onSelectDevice: (device: NetworkDevice) => void;
}

export const ScannerTab: React.FC<ScannerTabProps> = ({ language, onSelectDevice }) => {
  const t = TRANSLATIONS[language];
  const isArabic = language === 'ar';

  const [ranges, setRanges] = useState<NetworkRange[]>(PRESET_RANGES);
  const [newCidrInput, setNewCidrInput] = useState('192.168.1.0/24');
  const [isScanning, setIsScanning] = useState(false);
  const [scanProgress, setScanProgress] = useState(0);
  const [currentScannedIp, setCurrentScannedIp] = useState('192.168.1.1');
  const [discoveredResults, setDiscoveredResults] = useState<NetworkDevice[]>([]);

  const timerRef = useRef<NodeJS.Timeout | null>(null);

  const handleStartScan = () => {
    setIsScanning(true);
    setScanProgress(0);
    setDiscoveredResults([]);

    let step = 0;
    const totalSteps = 100;

    if (timerRef.current) clearInterval(timerRef.current);

    timerRef.current = setInterval(() => {
      step += 1;
      setScanProgress(step);
      const hostPart = Math.floor(step * 2.54);
      setCurrentScannedIp(`192.168.1.${hostPart}`);

      // Incremental discovery simulation
      if (step === 12) {
        setDiscoveredResults((prev) => [...prev, SAMPLE_DEVICES[0]]); // UDM Pro
      } else if (step === 28) {
        setDiscoveredResults((prev) => [...prev, SAMPLE_DEVICES[1]]); // CCR2004
      } else if (step === 48) {
        setDiscoveredResults((prev) => [...prev, SAMPLE_DEVICES[4]]); // USW Pro 24
      } else if (step === 72) {
        setDiscoveredResults((prev) => [...prev, SAMPLE_DEVICES[2]]); // U6 Enterprise AP
      } else if (step === 90) {
        setDiscoveredResults((prev) => [...prev, SAMPLE_DEVICES[3]]); // hEX S
      }

      if (step >= totalSteps) {
        if (timerRef.current) clearInterval(timerRef.current);
        setIsScanning(false);
      }
    }, 70);
  };

  const handleStopScan = () => {
    if (timerRef.current) clearInterval(timerRef.current);
    setIsScanning(false);
  };

  useEffect(() => {
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, []);

  const toggleRange = (id: string) => {
    setRanges((prev) =>
      prev.map((r) => (r.id === id ? { ...r, isEnabled: !r.isEnabled } : r))
    );
  };

  const addCustomRange = () => {
    if (!newCidrInput.trim()) return;
    const newRange: NetworkRange = {
      id: `range-${Date.now()}`,
      cidr: newCidrInput.trim(),
      label: `Subnet ${ranges.length + 1}`,
      labelEn: `Subnet ${ranges.length + 1}`,
      labelAr: `نطاق فرعي ${ranges.length + 1}`,
      isEnabled: true,
    };
    setRanges([...ranges, newRange]);
    setNewCidrInput('');
  };

  return (
    <div className="space-y-4 pb-6">
      {/* Disclaimer Notice: Mock Scanner Prototype */}
      <div className="p-3.5 rounded-2xl bg-[#17233D]/90 border border-amber-500/30 text-amber-200 text-xs text-start">
        <div className="flex items-start gap-2.5">
          <ShieldAlert className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />
          <div className="leading-relaxed">
            <span className="font-semibold text-amber-300 block mb-0.5">
              {t.mockScannerNoticeTitle}
            </span>
            <p className="text-[11px] text-amber-200/80">
              {t.mockScannerNotice}
            </p>
          </div>
        </div>
      </div>

      {/* Target Subnet Ranges Configuration */}
      <div className="p-4 rounded-2xl bg-[#111A2E] border border-slate-800 text-start">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            <Radio className="w-4 h-4 text-[#00E5FF]" />
            <h3 className="text-xs font-semibold text-white">{t.targetRanges}</h3>
          </div>
          <span className="text-[10px] text-slate-400">
            {t.selectedSubnets.replace('{count}', String(ranges.filter((r) => r.isEnabled).length))}
          </span>
        </div>

        {/* Input for adding new subnet CIDR */}
        <div className="flex items-center gap-2 mb-3">
          <input
            type="text"
            value={newCidrInput}
            onChange={(e) => setNewCidrInput(e.target.value)}
            placeholder={t.enterCidr}
            className="flex-1 bg-[#17233D] border border-slate-700/80 focus:border-[#00E5FF] focus:outline-none rounded-xl px-3 py-2 text-xs font-mono text-slate-200 placeholder-slate-500"
          />
          <button
            onClick={addCustomRange}
            className="px-3 py-2 bg-[#00E5FF]/20 hover:bg-[#00E5FF]/30 text-[#00E5FF] border border-[#00E5FF]/40 rounded-xl text-xs font-medium flex items-center gap-1 transition-colors shrink-0"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>{t.addRange}</span>
          </button>
        </div>

        {/* Active Subnets Toggle Chips */}
        <div className="flex flex-wrap gap-2">
          {ranges.map((range: any) => {
            const rangeLabel = isArabic ? (range.labelAr || range.label) : (range.labelEn || range.label);
            return (
              <button
                key={range.id}
                onClick={() => toggleRange(range.id)}
                className={`px-3 py-1.5 rounded-xl text-xs transition-all flex items-center gap-2 border ${
                  range.isEnabled
                    ? 'bg-[#0091FF]/20 border-[#0091FF]/50 text-white shadow-sm'
                    : 'bg-[#17233D]/60 border-slate-800 text-slate-400 hover:text-slate-300'
                }`}
              >
                <span className={`w-1.5 h-1.5 rounded-full ${range.isEnabled ? 'bg-[#00E5FF]' : 'bg-slate-600'}`} />
                <span className="font-mono">{range.cidr}</span>
                <span className="text-[10px] text-slate-400 font-sans">({rangeLabel})</span>
              </button>
            );
          })}
        </div>
      </div>

      {/* Scanner Radar & Control Panel */}
      <div className="p-5 rounded-2xl bg-gradient-to-b from-[#17233D] to-[#111A2E] border border-slate-800 shadow-xl text-center relative overflow-hidden">
        {/* Radar Visual Effect */}
        <div className="relative w-28 h-28 mx-auto my-2 flex items-center justify-center">
          <div className="absolute inset-0 rounded-full border border-cyan-500/20" />
          <div className="absolute inset-3 rounded-full border border-cyan-500/30" />
          <div className="absolute inset-7 rounded-full border border-cyan-500/40" />

          {isScanning ? (
            <>
              {/* Radar beam animation */}
              <div className="absolute inset-0 rounded-full bg-gradient-to-tr from-transparent via-[#00E5FF]/20 to-transparent animate-spin origin-center duration-1000" />
              <div className="w-4 h-4 rounded-full bg-[#00E5FF] shadow-[0_0_16px_#00E5FF] animate-ping" />
            </>
          ) : (
            <Radar className="w-10 h-10 text-cyan-400/80" />
          )}
        </div>

        {/* Status text */}
        <div className="mt-2">
          <h4 className="text-sm font-semibold text-white">
            {isScanning ? t.scanningStatus : t.scannerReady}
          </h4>
          <p className="text-xs font-mono text-[#00E5FF] mt-0.5">
            {isScanning
              ? t.probingHost.replace('{ip}', currentScannedIp)
              : t.scanTargetHint.replace('{subnet}', ranges.find((r) => r.isEnabled)?.cidr || '192.168.1.0/24')}
          </p>
        </div>

        {/* Scan Progress Bar */}
        {(isScanning || scanProgress > 0) && (
          <div className="mt-4 max-w-xs mx-auto">
            <div className="h-2 w-full bg-slate-800 rounded-full overflow-hidden">
              <div
                className="h-full bg-gradient-to-r from-[#0091FF] to-[#00E5FF] transition-all duration-150"
                style={{ width: `${scanProgress}%` }}
              />
            </div>
            <div className="flex justify-between items-center text-[10px] text-slate-400 mt-1 font-mono">
              <span>{t.percentCompleted.replace('{percent}', String(scanProgress))}</span>
              <span className="text-[#00E5FF] font-semibold">
                {t.devicesDiscoveredCount.replace('{count}', String(discoveredResults.length))}
              </span>
            </div>
          </div>
        )}

        {/* Start / Stop Buttons */}
        <div className="mt-5 flex items-center justify-center gap-3">
          {!isScanning ? (
            <button
              onClick={handleStartScan}
              className="px-6 py-2.5 rounded-xl bg-gradient-to-r from-[#00E5FF] to-[#0091FF] text-[#0A0F1D] font-bold text-xs flex items-center gap-2 shadow-lg shadow-cyan-500/20 hover:brightness-110 active:scale-95 transition-all"
            >
              <Play className="w-4 h-4 fill-current rtl:rotate-180" />
              <span>{t.startScan}</span>
            </button>
          ) : (
            <button
              onClick={handleStopScan}
              className="px-6 py-2.5 rounded-xl bg-rose-500 hover:bg-rose-600 text-white font-bold text-xs flex items-center gap-2 shadow-lg shadow-rose-500/20 active:scale-95 transition-all"
            >
              <Square className="w-4 h-4 fill-current" />
              <span>{t.stopScan}</span>
            </button>
          )}
        </div>
      </div>

      {/* Discovered Scan Results List */}
      <div className="space-y-2 text-start">
        <div className="flex items-center justify-between">
          <h3 className="text-xs font-semibold text-white tracking-tight">
            {t.discoveredEquipment} ({discoveredResults.length})
          </h3>
          <span className="text-[10px] text-slate-500 font-mono">
            {discoveredResults.length > 0 ? t.liveStream : t.idleScanner}
          </span>
        </div>

        {discoveredResults.length === 0 ? (
          <div className="p-8 text-center rounded-2xl bg-[#111A2E] border border-slate-800 text-slate-400 text-xs">
            <Radar className="w-8 h-8 text-slate-600 mx-auto mb-2" />
            <p className="font-medium text-slate-300">{t.tapStartScan}</p>
            <p className="text-[11px] text-slate-500 mt-1">
              {t.scannerProtocolDetail}
            </p>
          </div>
        ) : (
          discoveredResults.map((dev) => (
            <div
              key={dev.id}
              onClick={() => onSelectDevice(dev)}
              className="p-3 rounded-xl bg-[#111A2E] border border-cyan-500/30 hover:border-cyan-400 cursor-pointer transition-all flex items-center justify-between group"
            >
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-lg bg-[#17233D] flex items-center justify-center text-[#00E5FF]">
                  {dev.deviceType === 'ACCESS_POINT' ? <Wifi className="w-4 h-4" /> : <Server className="w-4 h-4" />}
                </div>
                <div>
                  <h4 className="text-xs font-semibold text-white group-hover:text-[#00E5FF] transition-colors">
                    {dev.name}
                  </h4>
                  <p className="text-[11px] font-mono text-slate-400 mt-0.5">
                    {dev.ipAddress} · <span className="text-slate-500">{dev.macAddress}</span>
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-2">
                <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-[#00E5FF]/10 text-[#00E5FF] border border-[#00E5FF]/20">
                  {dev.manufacturer}
                </span>
                <ChevronRight className="w-4 h-4 text-slate-600 group-hover:text-slate-300 transition-colors rtl:rotate-180" />
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
};
