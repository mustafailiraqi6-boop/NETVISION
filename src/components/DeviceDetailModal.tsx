import React from 'react';
import { X, Wifi, Server, Activity, ShieldCheck, Cpu, HardDrive, Clock, ArrowDownUp } from 'lucide-react';
import { NetworkDevice, AppLanguage } from '../types/network';

interface DeviceDetailModalProps {
  device: NetworkDevice | null;
  onClose: () => void;
  language: AppLanguage;
}

export const DeviceDetailModal: React.FC<DeviceDetailModalProps> = ({ device, onClose, language }) => {
  if (!device) return null;

  const isArabic = language === 'ar';

  const vendorColor =
    device.manufacturer === 'UBIQUITI'
      ? 'text-[#0091FF]'
      : device.manufacturer === 'MIKROTIK'
      ? 'text-[#00E5FF]'
      : 'text-sky-400';

  const statusBg =
    device.status === 'ONLINE'
      ? 'bg-emerald-500/20 text-emerald-400 border-emerald-500/30'
      : device.status === 'WARNING'
      ? 'bg-amber-500/20 text-amber-400 border-amber-500/30'
      : 'bg-rose-500/20 text-rose-400 border-rose-500/30';

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="bg-[#111A2E] border border-slate-800 rounded-2xl w-full max-w-md overflow-hidden shadow-2xl text-slate-100 flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="p-4 border-b border-slate-800/80 flex items-center justify-between bg-[#0A0F1D]/50">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-slate-800/80 flex items-center justify-center border border-slate-700/60">
              {device.deviceType === 'ACCESS_POINT' ? (
                <Wifi className="w-5 h-5 text-[#00E5FF]" />
              ) : (
                <Server className="w-5 h-5 text-[#0091FF]" />
              )}
            </div>
            <div>
              <h3 className="text-sm font-semibold text-white leading-tight">{device.name}</h3>
              <p className={`text-xs ${vendorColor} font-mono mt-0.5`}>
                {device.manufacturer} • {device.model}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="w-8 h-8 rounded-lg bg-slate-800/60 hover:bg-slate-700/60 text-slate-400 hover:text-white flex items-center justify-center transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Scrollable Body */}
        <div className="p-4 space-y-4 overflow-y-auto text-start">
          {/* Status Bar */}
          <div className="flex items-center justify-between p-3 rounded-xl bg-[#17233D]/60 border border-slate-800">
            <span className="text-xs text-slate-400">
              {isArabic ? 'حالة الجهاز التشغيلية' : 'Operational Status'}
            </span>
            <span className={`text-xs font-semibold px-2.5 py-0.5 rounded-full border ${statusBg}`}>
              {isArabic
                ? device.status === 'ONLINE'
                  ? 'متصل'
                  : device.status === 'WARNING'
                  ? 'تأخير مرتفع'
                  : 'غير متصل'
                : device.status}
            </span>
          </div>

          {/* Network Addressing */}
          <div className="p-3 rounded-xl bg-[#17233D]/60 border border-slate-800 space-y-2">
            <div className="flex justify-between text-xs">
              <span className="text-slate-400">{isArabic ? 'عنوان IPv4' : 'IPv4 Address'}</span>
              <span className="font-mono text-[#00E5FF] select-all font-medium">{device.ipAddress}</span>
            </div>
            <div className="flex justify-between text-xs border-t border-slate-800/60 pt-2">
              <span className="text-slate-400">{isArabic ? 'عنوان MAC' : 'MAC Address'}</span>
              <span className="font-mono text-slate-200 select-all">{device.macAddress}</span>
            </div>
            <div className="flex justify-between text-xs border-t border-slate-800/60 pt-2">
              <span className="text-slate-400">{isArabic ? 'إصدار البرنامج الثابت' : 'Firmware / OS'}</span>
              <span className="text-slate-200 font-mono">{device.firmwareVersion}</span>
            </div>
            <div className="flex justify-between text-xs border-t border-slate-800/60 pt-2">
              <span className="text-slate-400">{isArabic ? 'فئة ونوع الجهاز' : 'Device Category'}</span>
              <span className="text-slate-200">
                {isArabic
                  ? device.deviceType === 'ACCESS_POINT'
                    ? 'نقطة وصول'
                    : device.deviceType === 'GATEWAY'
                    ? 'بوابة حماية'
                    : device.deviceType === 'SWITCH'
                    ? 'سويتش'
                    : 'راوتر'
                  : device.deviceType}
              </span>
            </div>
          </div>

          {/* Live Telemetry Overview */}
          <div className="grid grid-cols-2 gap-2">
            <div className="p-3 rounded-xl bg-[#17233D]/60 border border-slate-800">
              <div className="flex items-center gap-1.5 text-slate-400 text-xs mb-1">
                <Clock className="w-3.5 h-3.5 text-[#00E5FF]" />
                <span>{isArabic ? 'مدة التشغيل' : 'Uptime'}</span>
              </div>
              <p className="text-sm font-semibold text-white">{device.metrics.uptimeFormatted}</p>
            </div>

            <div className="p-3 rounded-xl bg-[#17233D]/60 border border-slate-800">
              <div className="flex items-center gap-1.5 text-slate-400 text-xs mb-1">
                <Activity className="w-3.5 h-3.5 text-emerald-400" />
                <span>{isArabic ? 'زمن الاستجابة' : 'Ping Latency'}</span>
              </div>
              <p className="text-sm font-semibold text-emerald-400 font-mono">
                {device.metrics.pingLatencyMs} ms
              </p>
            </div>

            <div className="p-3 rounded-xl bg-[#17233D]/60 border border-slate-800">
              <div className="flex items-center gap-1.5 text-slate-400 text-xs mb-1">
                <Cpu className="w-3.5 h-3.5 text-amber-400" />
                <span>{isArabic ? 'المعالج' : 'CPU Load'}</span>
              </div>
              <p className="text-sm font-semibold text-white">{device.metrics.cpuUsagePercent}%</p>
            </div>

            <div className="p-3 rounded-xl bg-[#17233D]/60 border border-slate-800">
              <div className="flex items-center gap-1.5 text-slate-400 text-xs mb-1">
                <HardDrive className="w-3.5 h-3.5 text-sky-400" />
                <span>{isArabic ? 'الذاكرة' : 'RAM Usage'}</span>
              </div>
              <p className="text-sm font-semibold text-white">{device.metrics.memoryUsagePercent}%</p>
            </div>
          </div>

          {/* Bandwidth Throughput */}
          <div className="p-3 rounded-xl bg-[#17233D]/60 border border-slate-800">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs text-slate-400 flex items-center gap-1">
                <ArrowDownUp className="w-3.5 h-3.5 text-[#00E5FF]" />
                {isArabic ? 'عرض النطاق الترددي المباشر' : 'Live Interface Throughput'}
              </span>
              <span className="text-[10px] text-slate-500 font-mono">eth0 / sfp-sfpplus1</span>
            </div>
            <div className="flex items-center justify-between text-xs">
              <span className="text-slate-300">
                {isArabic ? 'إرسال: ' : 'TX: '}
                <strong className="text-[#0091FF] font-mono">{device.metrics.txRateMbps} Mbps</strong>
              </span>
              <span className="text-slate-300">
                {isArabic ? 'استقبال: ' : 'RX: '}
                <strong className="text-[#00E5FF] font-mono">{device.metrics.rxRateMbps} Mbps</strong>
              </span>
            </div>
          </div>

          {/* Integration Interface Readiness */}
          <div className="p-3 rounded-xl bg-[#0A0F1D] border border-slate-800/80">
            <div className="flex items-center gap-2 mb-1.5">
              <ShieldCheck className="w-4 h-4 text-emerald-400" />
              <span className="text-xs font-medium text-slate-200">
                {isArabic ? 'جاهزية واجهات الخدمات البرمجية' : 'Prepared Service Interfaces'}
              </span>
            </div>
            <p className="text-[11px] text-slate-400 leading-relaxed">
              {isArabic
                ? device.manufacturer === 'UBIQUITI'
                  ? 'مرتبط بخدمة UbiquitiDeviceService (استماع لإعلانات UDP 10001 وواجهة UniFi OS Controller).'
                  : device.manufacturer === 'MIKROTIK'
                  ? 'مرتبط بخدمة MikroTikDeviceService (استماع لإعلانات MNDP UDP 5678 وواجهة RouterOS REST API).'
                  : 'مرتبط بخدمة SnmpMonitoringService (استعلامات RFC 1213 / SNMP v2c MIB).'
                : device.manufacturer === 'UBIQUITI'
                ? 'Bound to UbiquitiDeviceService (UDP port 10001 beacon discovery & UniFi OS Controller API).'
                : device.manufacturer === 'MIKROTIK'
                ? 'Bound to MikroTikDeviceService (MNDP UDP port 5678 listener & RouterOS REST client).'
                : 'Bound to SnmpMonitoringService (Standard RFC 1213 / SNMP v2c MIB queries).'}
            </p>
          </div>
        </div>

        {/* Footer */}
        <div className="p-4 border-t border-slate-800 bg-[#0A0F1D]/50 flex justify-end">
          <button
            onClick={onClose}
            className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-xs font-medium text-white rounded-xl transition-colors"
          >
            {isArabic ? 'إغلاق' : 'Close'}
          </button>
        </div>
      </div>
    </div>
  );
};
