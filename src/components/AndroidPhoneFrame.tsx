import React from 'react';
import { Wifi, BatteryMedium, Signal, Smartphone, Monitor } from 'lucide-react';

interface AndroidPhoneFrameProps {
  children: React.ReactNode;
  isPhoneView: boolean;
  onToggleView: () => void;
  isArabic: boolean;
  theme: 'dark' | 'light';
}

export const AndroidPhoneFrame: React.FC<AndroidPhoneFrameProps> = ({
  children,
  isPhoneView,
  onToggleView,
  isArabic,
  theme,
}) => {
  const currentTime = '09:41';

  return (
    <div className="flex-1 flex flex-col items-center justify-center p-2 sm:p-4 overflow-hidden relative">
      {/* Top Device Mode Toolbar */}
      <div className="w-full max-w-5xl mb-2 flex items-center justify-between px-2 text-xs">
        <div className="flex items-center gap-2">
          <span className="font-semibold text-white">NETVISION</span>
          <span className="text-slate-500">·</span>
          <span className="text-slate-400 text-[11px]">
            {isPhoneView
              ? (isArabic ? 'محاكاة جهاز أندرويد 15' : 'Android 15 Device Emulation')
              : (isArabic ? 'بيئة العرض المتجاوبة / تابلت' : 'Adaptive Tablet / Studio Mode')}
          </span>
        </div>

        <button
          onClick={onToggleView}
          className="px-2.5 py-1 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 flex items-center gap-1.5 text-[11px] font-medium border border-slate-700 transition-colors"
        >
          {isPhoneView ? (
            <>
              <Monitor className="w-3.5 h-3.5 text-cyan-400" />
              <span>{isArabic ? 'عرض الشاشة الكاملة' : 'Full Viewport'}</span>
            </>
          ) : (
            <>
              <Smartphone className="w-3.5 h-3.5 text-cyan-400" />
              <span>{isArabic ? 'عرض الهاتف' : 'Phone Frame'}</span>
            </>
          )}
        </button>
      </div>

      {/* Frame Container */}
      {isPhoneView ? (
        <div className="w-full max-w-[420px] h-[840px] max-h-[92vh] bg-[#0A0F1D] border-4 border-slate-800 rounded-[44px] shadow-[0_25px_60px_-15px_rgba(0,0,0,0.8)] flex flex-col overflow-hidden relative transition-all">
          {/* Top Status Bar (Android 15 style) */}
          <div className="h-9 px-6 bg-[#0A0F1D] flex items-center justify-between text-slate-300 text-xs font-medium select-none shrink-0 z-40 border-b border-slate-900/40">
            <span className="font-semibold text-[11px] font-mono tracking-tight">{currentTime}</span>

            {/* Front Camera Cutout */}
            <div className="w-3.5 h-3.5 rounded-full bg-black border border-slate-800 flex items-center justify-center">
              <div className="w-1.5 h-1.5 rounded-full bg-slate-900" />
            </div>

            <div className="flex items-center gap-1.5 text-slate-300">
              <Signal className="w-3 h-3 text-cyan-400" />
              <Wifi className="w-3 h-3 text-cyan-400" />
              <BatteryMedium className="w-3.5 h-3.5" />
            </div>
          </div>

          {/* Phone Screen Canvas */}
          <div className="flex-1 flex flex-col overflow-hidden bg-[#0A0F1D] relative">
            {children}
          </div>

          {/* Android Bottom Navigation Pill */}
          <div className="h-4 bg-[#111A2E] flex items-center justify-center shrink-0 z-40">
            <div className="w-28 h-1 bg-slate-600 rounded-full" />
          </div>
        </div>
      ) : (
        <div className="w-full max-w-5xl h-[860px] max-h-[92vh] bg-[#0A0F1D] border border-slate-800 rounded-2xl shadow-2xl flex flex-col overflow-hidden">
          {/* Studio Top Info Bar */}
          <div className="h-10 px-4 bg-[#111A2E] border-b border-slate-800 flex items-center justify-between text-xs text-slate-300 shrink-0">
            <div className="flex items-center gap-2">
              <div className="flex gap-1.5">
                <div className="w-2.5 h-2.5 rounded-full bg-rose-500/80" />
                <div className="w-2.5 h-2.5 rounded-full bg-amber-500/80" />
                <div className="w-2.5 h-2.5 rounded-full bg-emerald-500/80" />
              </div>
              <span className="text-slate-400 text-[11px] font-mono ml-2">
                NETVISION · Android Compose Studio
              </span>
            </div>
            <div className="flex items-center gap-3 text-[11px] font-mono text-cyan-400">
              <span>UDP 10001 (UBNT)</span>
              <span>·</span>
              <span>UDP 5678 (MNDP)</span>
              <span>·</span>
              <span>SNMP v2c</span>
            </div>
          </div>

          {/* Screen Content Canvas */}
          <div className="flex-1 flex flex-col overflow-hidden bg-[#0A0F1D] relative">
            {children}
          </div>
        </div>
      )}
    </div>
  );
};
