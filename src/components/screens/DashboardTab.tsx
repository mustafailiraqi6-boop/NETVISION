import React from 'react';
import {
  Layers,
  CheckCircle2,
  AlertOctagon,
  Activity,
  ArrowUpRight,
  TrendingUp,
  Cpu,
  Clock,
  Radio,
  Server
} from 'lucide-react';
import { NetworkDevice, RecentDeviceActivity, AppLanguage } from '../../types/network';
import { TRANSLATIONS } from '../../data/sampleData';

interface DashboardTabProps {
  devices: NetworkDevice[];
  activities: RecentDeviceActivity[];
  language: AppLanguage;
  onNavigateToDevices: (filterVendor?: string) => void;
  onSelectDevice: (device: NetworkDevice) => void;
}

export const DashboardTab: React.FC<DashboardTabProps> = ({
  devices,
  activities,
  language,
  onNavigateToDevices,
  onSelectDevice,
}) => {
  const t = TRANSLATIONS[language];
  const isArabic = language === 'ar';

  const totalCount = devices.length;
  const onlineCount = devices.filter((d) => d.status === 'ONLINE').length;
  const offlineCount = devices.filter((d) => d.status === 'OFFLINE').length;
  const ubntCount = devices.filter((d) => d.manufacturer === 'UBIQUITI').length;
  const mtkCount = devices.filter((d) => d.manufacturer === 'MIKROTIK').length;

  return (
    <div className="space-y-4 pb-6">
      {/* Top Banner Notice: Initial UI Prototype with Sample Data */}
      <div className="p-3 rounded-xl bg-gradient-to-r from-[#17233D] to-[#111A2E] border border-cyan-500/20 text-xs text-slate-300 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <div className="w-2 h-2 rounded-full bg-[#00E5FF] animate-pulse" />
          <span className="font-medium text-slate-200">
            {t.sampleDataNotice}
          </span>
          <span className="text-slate-400 hidden sm:inline">·</span>
          <span className="text-slate-400 text-[11px] hidden sm:inline">
            {t.sampleDataDesc}
          </span>
        </div>
        <span className="px-2 py-0.5 rounded-full text-[10px] font-mono font-semibold bg-[#00E5FF]/10 text-[#00E5FF] border border-[#00E5FF]/30">
          {t.versionBadge}
        </span>
      </div>

      {/* Network Health Overview Card */}
      <div className="p-4 rounded-2xl bg-[#111A2E] border border-slate-800/80 shadow-lg relative overflow-hidden">
        <div className="flex items-center justify-between mb-3">
          <div>
            <h2 className="text-sm font-semibold text-white tracking-tight">
              {t.networkHealth}
            </h2>
            <p className="text-[11px] text-slate-400 mt-0.5">
              {t.healthDesc}
            </p>
          </div>
          <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-cyan-500/10 border border-cyan-500/20 text-cyan-400 text-xs font-medium">
            <span className="w-1.5 h-1.5 rounded-full bg-cyan-400 animate-pulse" />
            <span>{totalCount > 0 ? t.optimal : (isArabic ? 'جاهز للفحص الحقيقي' : 'Ready for Real Scan')}</span>
          </div>
        </div>

        {/* 3 Metric Pillars */}
        <div className="grid grid-cols-3 gap-2 pt-2 border-t border-slate-800/60 text-start">
          <div className="p-2.5 rounded-xl bg-[#17233D]/60 border border-slate-800/80">
            <span className="text-[10px] text-slate-400 block">{t.avgLatency}</span>
            <span className="text-base font-bold font-mono text-[#00E5FF] mt-0.5 block">
              {totalCount > 0 ? '3.8 ms' : '0.0 ms'}
            </span>
            <span className="text-[10px] text-slate-400 font-medium">
              {totalCount > 0 ? t.jitter : (isArabic ? 'لم يُقس بعد' : 'Not yet measured')}
            </span>
          </div>

          <div className="p-2.5 rounded-xl bg-[#17233D]/60 border border-slate-800/80">
            <span className="text-[10px] text-slate-400 block">{t.packetLoss}</span>
            <span className="text-base font-bold font-mono text-emerald-400 mt-0.5 block">
              0.00 %
            </span>
            <span className="text-[10px] text-slate-400">
              {totalCount > 0 ? t.packetLossDetail : (isArabic ? '0 حزم مفقودة' : '0 drops')}
            </span>
          </div>

          <div className="p-2.5 rounded-xl bg-[#17233D]/60 border border-slate-800/80">
            <span className="text-[10px] text-slate-400 block">{t.totalFlow}</span>
            <span className="text-base font-bold font-mono text-[#0091FF] mt-0.5 block">
              {totalCount > 0 ? '1.42 Gbps' : '0.00 Mbps'}
            </span>
            <span className="text-[10px] text-slate-400">
              {totalCount > 0 ? t.peakFlow : (isArabic ? 'الذروة 0 Mbps' : 'Peak 0 Mbps')}
            </span>
          </div>
        </div>
      </div>

      {/* KPI Counters: Total, Online, Offline */}
      <div className="grid grid-cols-3 gap-2.5">
        <button
          onClick={() => onNavigateToDevices()}
          className="p-3 rounded-2xl bg-[#111A2E] border border-slate-800 hover:border-cyan-500/40 transition-all text-start group"
        >
          <div className="flex items-center justify-between text-slate-400 mb-1">
            <span className="text-[11px] font-medium">{t.totalDevices}</span>
            <Layers className="w-3.5 h-3.5 text-[#00E5FF] group-hover:scale-110 transition-transform" />
          </div>
          <div className="text-2xl font-bold font-mono text-white mt-0.5">{totalCount}</div>
          <span className="text-[10px] text-slate-500 mt-1 block">
            {t.tapToViewAll}
          </span>
        </button>

        <button
          onClick={() => onNavigateToDevices()}
          className="p-3 rounded-2xl bg-[#111A2E] border border-slate-800 hover:border-emerald-500/40 transition-all text-start group"
        >
          <div className="flex items-center justify-between text-slate-400 mb-1">
            <span className="text-[11px] font-medium">{t.online}</span>
            <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 group-hover:scale-110 transition-transform" />
          </div>
          <div className="text-2xl font-bold font-mono text-emerald-400 mt-0.5">{onlineCount}</div>
          <span className="text-[10px] text-emerald-500/80 mt-1 block">
            {totalCount > 0 ? `${Math.round((onlineCount / totalCount) * 100)}% ${t.operationalRate}` : '0%'}
          </span>
        </button>

        <button
          onClick={() => onNavigateToDevices()}
          className="p-3 rounded-2xl bg-[#111A2E] border border-slate-800 hover:border-rose-500/40 transition-all text-start group"
        >
          <div className="flex items-center justify-between text-slate-400 mb-1">
            <span className="text-[11px] font-medium">{t.offline}</span>
            <AlertOctagon className="w-3.5 h-3.5 text-rose-400 group-hover:scale-110 transition-transform" />
          </div>
          <div className="text-2xl font-bold font-mono text-rose-400 mt-0.5">{offlineCount}</div>
          <span className="text-[10px] text-rose-400/80 mt-1 block">
            {offlineCount > 0 ? t.needsAttention : t.allClean}
          </span>
        </button>
      </div>

      {/* Target Vendor Breakdown (Ubiquiti & MikroTik) */}
      <div className="grid grid-cols-2 gap-3">
        {/* Ubiquiti Card */}
        <div
          onClick={() => onNavigateToDevices('UBIQUITI')}
          className="p-3.5 rounded-2xl bg-gradient-to-br from-[#17233D] to-[#111A2E] border border-blue-500/30 hover:border-blue-400 cursor-pointer transition-all shadow-md group text-start"
        >
          <div className="flex items-center justify-between mb-2">
            <div className="flex items-center gap-1.5">
              <div className="w-2 h-2 rounded-full bg-[#0091FF]" />
              <span className="text-xs font-semibold text-white tracking-tight">Ubiquiti (UBNT)</span>
            </div>
            <ArrowUpRight className="w-3.5 h-3.5 text-[#0091FF] group-hover:translate-x-0.5 group-hover:-translate-y-0.5 transition-transform rtl:group-hover:-translate-x-0.5" />
          </div>
          <div className="text-xl font-bold font-mono text-[#0091FF]">{ubntCount}</div>
          <div className="text-[10px] text-slate-400 mt-1">
            <span>{t.vendorUbiquitiSub}</span>
          </div>
          <div className="mt-2 pt-2 border-t border-slate-800/80 flex items-center justify-between text-[10px] text-slate-400">
            <span>{t.protocolUbntDiscovery}</span>
            <span className="text-blue-400 font-medium">{t.statusReady}</span>
          </div>
        </div>

        {/* MikroTik Card */}
        <div
          onClick={() => onNavigateToDevices('MIKROTIK')}
          className="p-3.5 rounded-2xl bg-gradient-to-br from-[#17233D] to-[#111A2E] border border-cyan-500/30 hover:border-cyan-400 cursor-pointer transition-all shadow-md group text-start"
        >
          <div className="flex items-center justify-between mb-2">
            <div className="flex items-center gap-1.5">
              <div className="w-2 h-2 rounded-full bg-[#00E5FF]" />
              <span className="text-xs font-semibold text-white tracking-tight">MikroTik</span>
            </div>
            <ArrowUpRight className="w-3.5 h-3.5 text-[#00E5FF] group-hover:translate-x-0.5 group-hover:-translate-y-0.5 transition-transform rtl:group-hover:-translate-x-0.5" />
          </div>
          <div className="text-xl font-bold font-mono text-[#00E5FF]">{mtkCount}</div>
          <div className="text-[10px] text-slate-400 mt-1">
            <span>{t.vendorMikroTikSub}</span>
          </div>
          <div className="mt-2 pt-2 border-t border-slate-800/80 flex items-center justify-between text-[10px] text-slate-400">
            <span>{t.protocolMndp}</span>
            <span className="text-cyan-400 font-medium">{t.statusReady}</span>
          </div>
        </div>
      </div>

      {/* Recent Device Activity / Empty State */}
      <div className="rounded-2xl bg-[#111A2E] border border-slate-800/80 p-4 text-start">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            <Clock className="w-4 h-4 text-[#00E5FF]" />
            <h3 className="text-sm font-semibold text-white">{t.recentActivity}</h3>
          </div>
          {activities.length > 0 && (
            <button
              onClick={() => onNavigateToDevices()}
              className="text-xs text-[#00E5FF] hover:underline font-medium"
            >
              {t.viewAll}
            </button>
          )}
        </div>

        {activities.length === 0 ? (
          <div className="p-6 rounded-xl bg-[#17233D]/40 border border-slate-800 border-dashed text-center">
            <Radio className="w-8 h-8 text-cyan-400/60 mx-auto mb-2" />
            <h4 className="text-xs font-bold text-white mb-1">{t.emptyDevicesTitle}</h4>
            <p className="text-[11px] text-slate-400 max-w-xs mx-auto leading-relaxed mb-3">
              {t.emptyDevicesDesc}
            </p>
          </div>
        ) : (
          <div className="space-y-2.5">
            {activities.map((act) => {
              const dotColor =
                act.severity === 'SUCCESS'
                  ? 'bg-emerald-400'
                  : act.severity === 'WARNING'
                  ? 'bg-amber-400'
                  : act.severity === 'ALERT'
                  ? 'bg-rose-400'
                  : 'bg-[#0091FF]';

              return (
                <div
                  key={act.id}
                  className="p-3 rounded-xl bg-[#17233D]/50 border border-slate-800/80 hover:bg-[#17233D] transition-colors"
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="flex items-start gap-2.5">
                      <span className={`w-2 h-2 rounded-full mt-1 shrink-0 ${dotColor}`} />
                      <div>
                        <p className="text-xs font-semibold text-white">{act.deviceName}</p>
                        <p className="text-[11px] text-slate-400 mt-0.5 leading-relaxed">
                          {isArabic ? act.messageAr : act.messageEn}
                        </p>
                      </div>
                    </div>
                    <span className="text-[10px] text-slate-500 font-mono shrink-0">
                      {isArabic ? act.timeAgoAr : act.timeAgoEn}
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};
