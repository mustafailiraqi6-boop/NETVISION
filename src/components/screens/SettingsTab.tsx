import React, { useState } from 'react';
import {
  Globe,
  Moon,
  Sun,
  Sliders,
  Clock,
  Info,
  Check,
  Code2,
  FolderTree,
  Shield,
  Layers,
  Sparkles
} from 'lucide-react';
import { AppLanguage, AppTheme } from '../../types/network';
import { TRANSLATIONS } from '../../data/sampleData';

interface SettingsTabProps {
  language: AppLanguage;
  onLanguageChange: (lang: AppLanguage) => void;
  theme: AppTheme;
  onThemeChange: (theme: AppTheme) => void;
  onOpenCodeExplorer: () => void;
}

export const SettingsTab: React.FC<SettingsTabProps> = ({
  language,
  onLanguageChange,
  theme,
  onThemeChange,
  onOpenCodeExplorer,
}) => {
  const t = TRANSLATIONS[language];
  const isArabic = language === 'ar';

  const [defaultSubnet, setDefaultSubnet] = useState('192.168.1.0/24');
  const [probeTimeout, setProbeTimeout] = useState('1200');
  const [refreshInterval, setRefreshInterval] = useState('5');

  const [protoUbnt, setProtoUbnt] = useState(true);
  const [protoMikrotik, setProtoMikrotik] = useState(true);
  const [protoSnmp, setProtoSnmp] = useState(true);

  return (
    <div className="space-y-4 pb-8">
      {/* Title */}
      <div>
        <h2 className="text-base font-semibold text-white tracking-tight">
          {t.settingsTitle}
        </h2>
        <p className="text-xs text-slate-400 mt-0.5">{t.settingsDesc}</p>
      </div>

      {/* Code Inspector Quick Action Button */}
      <button
        onClick={onOpenCodeExplorer}
        className="w-full p-3.5 rounded-2xl bg-gradient-to-r from-blue-900/40 via-cyan-900/30 to-slate-900 border border-cyan-500/40 hover:border-cyan-400 flex items-center justify-between text-start transition-all group shadow-md"
      >
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-[#00E5FF]/20 text-[#00E5FF] flex items-center justify-center border border-[#00E5FF]/30 shrink-0">
            <Code2 className="w-5 h-5" />
          </div>
          <div>
            <h4 className="text-xs font-semibold text-white group-hover:text-[#00E5FF] transition-colors">
              {t.codeExplorerBtnTitle}
            </h4>
            <p className="text-[11px] text-slate-400 mt-0.5">
              {t.codeExplorerBtnDesc}
            </p>
          </div>
        </div>
        <span className="text-xs font-mono font-semibold text-[#00E5FF] px-2.5 py-1 rounded-lg bg-[#00E5FF]/10 border border-[#00E5FF]/30 shrink-0">
          {t.codeExplorerBtnBadge}
        </span>
      </button>

      {/* Group 1: Language & Theme */}
      <div className="p-4 rounded-2xl bg-[#111A2E] border border-slate-800 space-y-4 text-start">
        <div className="flex items-center gap-2 text-xs font-semibold text-white border-b border-slate-800/80 pb-2">
          <Globe className="w-4 h-4 text-[#00E5FF]" />
          <span>{t.languageDisplay}</span>
        </div>

        {/* Language Selection */}
        <div className="flex items-center justify-between">
          <div>
            <span className="text-xs font-medium text-slate-200 block">{t.appLanguage}</span>
            <span className="text-[11px] text-slate-400">
              {isArabic ? 'العربية (RTL) / English (LTR)' : 'English (LTR) / Arabic (RTL)'}
            </span>
          </div>
          <div className="flex items-center gap-1.5 bg-[#17233D] p-1 rounded-xl border border-slate-700/60">
            <button
              onClick={() => onLanguageChange('ar')}
              className={`px-3 py-1 rounded-lg text-xs font-medium transition-colors ${
                language === 'ar'
                  ? 'bg-[#00E5FF] text-[#0A0F1D] font-bold shadow-sm'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              العربية
            </button>
            <button
              onClick={() => onLanguageChange('en')}
              className={`px-3 py-1 rounded-lg text-xs font-medium transition-colors ${
                language === 'en'
                  ? 'bg-[#00E5FF] text-[#0A0F1D] font-bold shadow-sm'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              English
            </button>
          </div>
        </div>

        {/* Theme Mode */}
        <div className="flex items-center justify-between pt-2 border-t border-slate-800/60">
          <div>
            <span className="text-xs font-medium text-slate-200 block">{t.themeMode}</span>
            <span className="text-[11px] text-slate-400">
              {theme === 'dark' ? t.dark : t.light}
            </span>
          </div>
          <div className="flex items-center gap-1.5 bg-[#17233D] p-1 rounded-xl border border-slate-700/60">
            <button
              onClick={() => onThemeChange('dark')}
              className={`px-3 py-1 rounded-lg text-xs font-medium flex items-center gap-1.5 transition-colors ${
                theme === 'dark'
                  ? 'bg-[#00E5FF] text-[#0A0F1D] font-bold shadow-sm'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              <Moon className="w-3.5 h-3.5" />
              <span>{t.dark.split(' ')[0]}</span>
            </button>
            <button
              onClick={() => onThemeChange('light')}
              className={`px-3 py-1 rounded-lg text-xs font-medium flex items-center gap-1.5 transition-colors ${
                theme === 'light'
                  ? 'bg-[#00E5FF] text-[#0A0F1D] font-bold shadow-sm'
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              <Sun className="w-3.5 h-3.5" />
              <span>{t.light.split(' ')[0]}</span>
            </button>
          </div>
        </div>
      </div>

      {/* Group 2: Network Scan Preferences */}
      <div className="p-4 rounded-2xl bg-[#111A2E] border border-slate-800 space-y-3 text-start">
        <div className="flex items-center gap-2 text-xs font-semibold text-white border-b border-slate-800/80 pb-2">
          <Sliders className="w-4 h-4 text-[#00E5FF]" />
          <span>{t.scanPreferences}</span>
        </div>

        <div className="space-y-1">
          <label className="text-[11px] text-slate-400">{t.defaultSubnet}</label>
          <input
            type="text"
            value={defaultSubnet}
            onChange={(e) => setDefaultSubnet(e.target.value)}
            className="w-full bg-[#17233D] border border-slate-700 focus:border-[#00E5FF] focus:outline-none rounded-xl px-3 py-2 text-xs font-mono text-slate-200"
          />
        </div>

        <div className="space-y-1">
          <label className="text-[11px] text-slate-400">{t.probeTimeout}</label>
          <input
            type="text"
            value={probeTimeout}
            onChange={(e) => setProbeTimeout(e.target.value)}
            className="w-full bg-[#17233D] border border-slate-700 focus:border-[#00E5FF] focus:outline-none rounded-xl px-3 py-2 text-xs font-mono text-slate-200"
          />
        </div>

        <div className="pt-2">
          <span className="text-[11px] font-medium text-slate-400 block mb-2">
            {t.discoveryProtocols}
          </span>
          <div className="space-y-2">
            {/* Ubiquiti Protocol */}
            <div
              onClick={() => setProtoUbnt(!protoUbnt)}
              className="flex items-center justify-between p-2.5 rounded-xl bg-[#17233D]/60 border border-slate-800 cursor-pointer"
            >
              <span className="text-xs text-slate-300">{t.ubntProto}</span>
              <div
                className={`w-5 h-5 rounded-md flex items-center justify-center border transition-colors ${
                  protoUbnt
                    ? 'bg-[#0091FF] border-[#0091FF] text-white'
                    : 'border-slate-600 bg-transparent'
                }`}
              >
                {protoUbnt && <Check className="w-3.5 h-3.5" />}
              </div>
            </div>

            {/* MikroTik MNDP */}
            <div
              onClick={() => setProtoMikrotik(!protoMikrotik)}
              className="flex items-center justify-between p-2.5 rounded-xl bg-[#17233D]/60 border border-slate-800 cursor-pointer"
            >
              <span className="text-xs text-slate-300">{t.mikrotikMndp}</span>
              <div
                className={`w-5 h-5 rounded-md flex items-center justify-center border transition-colors ${
                  protoMikrotik
                    ? 'bg-[#00E5FF] border-[#00E5FF] text-[#0A0F1D]'
                    : 'border-slate-600 bg-transparent'
                }`}
              >
                {protoMikrotik && <Check className="w-3.5 h-3.5" />}
              </div>
            </div>

            {/* SNMP */}
            <div
              onClick={() => setProtoSnmp(!protoSnmp)}
              className="flex items-center justify-between p-2.5 rounded-xl bg-[#17233D]/60 border border-slate-800 cursor-pointer"
            >
              <span className="text-xs text-slate-300">{t.snmpAuto}</span>
              <div
                className={`w-5 h-5 rounded-md flex items-center justify-center border transition-colors ${
                  protoSnmp
                    ? 'bg-emerald-500 border-emerald-500 text-white'
                    : 'border-slate-600 bg-transparent'
                }`}
              >
                {protoSnmp && <Check className="w-3.5 h-3.5" />}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Group 3: Monitoring Refresh Interval */}
      <div className="p-4 rounded-2xl bg-[#111A2E] border border-slate-800 space-y-3 text-start">
        <div className="flex items-center gap-2 text-xs font-semibold text-white border-b border-slate-800/80 pb-2">
          <Clock className="w-4 h-4 text-[#00E5FF]" />
          <span>{t.refreshInterval}</span>
        </div>
        <p className="text-[11px] text-slate-400">{t.intervalDesc}</p>
        <div className="grid grid-cols-4 gap-2">
          {['5', '15', '30', '60'].map((val) => {
            const isSelected = refreshInterval === val;
            return (
              <button
                key={val}
                onClick={() => setRefreshInterval(val)}
                className={`py-2 rounded-xl text-xs font-medium transition-all border ${
                  isSelected
                    ? 'bg-[#00E5FF]/20 text-[#00E5FF] border-[#00E5FF]/60 shadow-sm'
                    : 'bg-[#17233D] text-slate-400 border-slate-800 hover:text-white'
                }`}
              >
                {val} {t.secondsUnit}
              </button>
            );
          })}
        </div>
      </div>

      {/* Group 4: About NETVISION */}
      <div className="p-4 rounded-2xl bg-[#111A2E] border border-slate-800 space-y-2 text-start">
        <div className="flex items-center gap-2 text-xs font-semibold text-white border-b border-slate-800/80 pb-2">
          <Info className="w-4 h-4 text-[#00E5FF]" />
          <span>{t.aboutApp}</span>
        </div>

        <div>
          <h4 className="text-xs font-bold text-[#00E5FF] tracking-tight">
            NETVISION – {t.subtitle}
          </h4>
          <span className="text-[10px] text-slate-400 font-mono">
            Version 1.0.0-PROTOTYPE · Minimum Android API 26 (Android 8.0 Oreo)
          </span>
        </div>

        <p className="text-xs text-slate-300 leading-relaxed pt-1">{t.aboutText}</p>

        <div className="p-2.5 rounded-xl bg-[#0A0F1D] border border-slate-800 text-[11px] text-slate-400 font-mono">
          {t.architectureDetails}
        </div>
      </div>
    </div>
  );
};

