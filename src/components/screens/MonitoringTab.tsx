import React, { useState, useEffect } from 'react';
import {
  Activity,
  Wifi,
  Clock,
  Cpu,
  HardDrive,
  ArrowDownUp,
  Radio,
  Server,
  Zap,
  RefreshCw,
  Info
} from 'lucide-react';
import { NetworkDevice, AppLanguage } from '../../types/network';
import { TRANSLATIONS } from '../../data/sampleData';

interface MonitoringTabProps {
  devices: NetworkDevice[];
  language: AppLanguage;
  onSelectDevice: (device: NetworkDevice) => void;
}

export const MonitoringTab: React.FC<MonitoringTabProps> = ({ devices, language, onSelectDevice }) => {
  const t = TRANSLATIONS[language];
  const isArabic = language === 'ar';

  const [selectedDeviceId, setSelectedDeviceId] = useState<string>(
    devices[0]?.id || 'ubnt-udm-01'
  );

  const selectedDevice = devices.find((d) => d.id === selectedDeviceId) || devices[0];

  // Minor telemetry jitter for prototype realism
  const [liveJitter, setLiveJitter] = useState({ tx: 0, rx: 0, ping: 0 });

  useEffect(() => {
    const interval = setInterval(() => {
      setLiveJitter({
        tx: +(Math.random() * 4 - 2).toFixed(1),
        rx: +(Math.random() * 6 - 3).toFixed(1),
        ping: Math.floor(Math.random() * 3 - 1),
      });
    }, 3000);
    return () => clearInterval(interval);
  }, []);

  if (!selectedDevice) return null;

  const currentPing = Math.max(1, selectedDevice.metrics.pingLatencyMs + liveJitter.ping);
  const currentTx = Math.max(0, +(selectedDevice.metrics.txRateMbps + liveJitter.tx).toFixed(1));
  const currentRx = Math.max(0, +(selectedDevice.metrics.rxRateMbps + liveJitter.rx).toFixed(1));

  return (
    <div className="space-y-4 pb-6">
      {/* Title & SNMP Notice */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-base font-semibold text-white tracking-tight">
            {t.deviceTelemetry}
          </h2>
          <p className="text-xs text-slate-400 mt-0.5">{t.snmpIndicators}</p>
        </div>
        <div className="flex items-center gap-1.5 px-2 py-1 rounded-lg bg-[#111A2E] border border-slate-800 text-[11px] text-cyan-400 font-mono">
          <Zap className="w-3.5 h-3.5 text-[#00E5FF]" />
          <span>SNMP v2c</span>
        </div>
      </div>

      {/* Clearly Identified Placeholder Values Notice */}
      <div className="p-3 rounded-xl bg-[#111A2E] border border-cyan-500/30 text-xs text-slate-300 flex items-start gap-2.5">
        <Info className="w-4 h-4 text-[#00E5FF] shrink-0 mt-0.5" />
        <div className="leading-relaxed">
          <span className="font-semibold text-white block mb-0.5">
            {isArabic ? 'قيم قياس نموذجية للاختبار' : 'Prototype SNMP Telemetry'}
          </span>
          <p className="text-[11px] text-slate-400">{t.snmpPlaceholderNotice}</p>
        </div>
      </div>

      {/* Monitored Device Selector Tabs */}
      <div>
        <label className="text-[11px] font-medium text-slate-400 block mb-1.5">
          {t.selectDevice}
        </label>
        <div className="flex items-center gap-2 overflow-x-auto no-scrollbar pb-1">
          {devices.map((device) => {
            const isSelected = device.id === selectedDevice.id;
            const statusDot =
              device.status === 'ONLINE'
                ? 'bg-emerald-400'
                : device.status === 'WARNING'
                ? 'bg-amber-400'
                : 'bg-rose-400';

            return (
              <button
                key={device.id}
                onClick={() => setSelectedDeviceId(device.id)}
                className={`px-3 py-2 rounded-xl text-xs font-medium whitespace-nowrap transition-all border flex items-center gap-2 ${
                  isSelected
                    ? 'bg-[#00E5FF]/20 text-[#00E5FF] border-[#00E5FF]/50 shadow-sm'
                    : 'bg-[#111A2E] text-slate-400 border-slate-800 hover:text-slate-200'
                }`}
              >
                <span className={`w-2 h-2 rounded-full ${statusDot}`} />
                <span>{device.name}</span>
              </button>
            );
          })}
        </div>
      </div>

      {/* Monitored Hardware Hero Card */}
      <div className="p-4 rounded-2xl bg-gradient-to-br from-[#17233D] to-[#111A2E] border border-slate-800 shadow-md">
        <div className="flex items-start justify-between">
          <div className="flex items-start gap-3">
            <div className="w-10 h-10 rounded-xl bg-[#0A0F1D] border border-slate-700/80 flex items-center justify-center text-[#00E5FF]">
              {selectedDevice.deviceType === 'ACCESS_POINT' ? (
                <Wifi className="w-5 h-5" />
              ) : (
                <Server className="w-5 h-5" />
              )}
            </div>
            <div>
              <h3 className="text-sm font-semibold text-white leading-tight">
                {selectedDevice.name}
              </h3>
              <p className="text-xs text-[#00E5FF] font-mono mt-0.5">
                {selectedDevice.manufacturer} • {selectedDevice.model}
              </p>
              <p className="text-[11px] text-slate-400 font-mono mt-1">
                IP: {selectedDevice.ipAddress} · MAC: {selectedDevice.macAddress}
              </p>
            </div>
          </div>
          <button
            onClick={() => onSelectDevice(selectedDevice)}
            className="text-[11px] text-slate-300 hover:text-white px-2.5 py-1 rounded-lg bg-[#111A2E] border border-slate-700 font-medium transition-colors"
          >
            {t.deviceDetailsBtn}
          </button>
        </div>
      </div>

      {/* Telemetry Metric Cards */}
      <div className="grid grid-cols-2 gap-3 text-start">
        {/* Ping Latency Card */}
        <div className="p-3.5 rounded-2xl bg-[#111A2E] border border-slate-800">
          <div className="flex items-center justify-between text-slate-400 mb-2">
            <span className="text-[11px] font-medium">{t.pingLatency}</span>
            <Activity className="w-4 h-4 text-emerald-400" />
          </div>
          <div className="text-2xl font-bold font-mono text-emerald-400">
            {currentPing} <span className="text-xs font-normal text-slate-400">ms</span>
          </div>
          <span className="text-[10px] text-slate-400 mt-1 block">
            {t.pingSubtext}
          </span>
        </div>

        {/* Uptime Card */}
        <div className="p-3.5 rounded-2xl bg-[#111A2E] border border-slate-800">
          <div className="flex items-center justify-between text-slate-400 mb-2">
            <span className="text-[11px] font-medium">{t.uptime}</span>
            <Clock className="w-4 h-4 text-[#0091FF]" />
          </div>
          <div className="text-base font-bold font-mono text-white mt-1">
            {selectedDevice.metrics.uptimeFormatted}
          </div>
          <span className="text-[10px] text-slate-400 mt-1 block">
            {t.uptimeSubtext}
          </span>
        </div>

        {/* CPU Usage Card */}
        <div className="p-3.5 rounded-2xl bg-[#111A2E] border border-slate-800">
          <div className="flex items-center justify-between text-slate-400 mb-2">
            <span className="text-[11px] font-medium">{t.cpuLoad}</span>
            <Cpu className="w-4 h-4 text-amber-400" />
          </div>
          <div className="text-2xl font-bold font-mono text-white">
            {selectedDevice.metrics.cpuUsagePercent}%
          </div>
          {/* Micro Progress Bar */}
          <div className="w-full bg-slate-800 h-1.5 rounded-full mt-2 overflow-hidden">
            <div
              className="bg-amber-400 h-full rounded-full transition-all duration-300"
              style={{ width: `${selectedDevice.metrics.cpuUsagePercent}%` }}
            />
          </div>
          <span className="text-[10px] text-slate-400 mt-1 block">
            {t.cpuSubtext}
          </span>
        </div>

        {/* Memory Usage Card */}
        <div className="p-3.5 rounded-2xl bg-[#111A2E] border border-slate-800">
          <div className="flex items-center justify-between text-slate-400 mb-2">
            <span className="text-[11px] font-medium">{t.memoryUsage}</span>
            <HardDrive className="w-4 h-4 text-sky-400" />
          </div>
          <div className="text-2xl font-bold font-mono text-white">
            {selectedDevice.metrics.memoryUsagePercent}%
          </div>
          {/* Micro Progress Bar */}
          <div className="w-full bg-slate-800 h-1.5 rounded-full mt-2 overflow-hidden">
            <div
              className="bg-sky-400 h-full rounded-full transition-all duration-300"
              style={{ width: `${selectedDevice.metrics.memoryUsagePercent}%` }}
            />
          </div>
          <span className="text-[10px] text-slate-400 mt-1 block">
            {t.memorySubtext}
          </span>
        </div>
      </div>

      {/* TX / RX Traffic Throughput Card */}
      <div className="p-4 rounded-2xl bg-[#111A2E] border border-slate-800 text-start">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            <ArrowDownUp className="w-4 h-4 text-[#00E5FF]" />
            <h4 className="text-xs font-semibold text-white">{t.trafficTxRx}</h4>
          </div>
          <span className="text-[10px] text-slate-400">{t.bandwidthCounters}</span>
        </div>

        <div className="grid grid-cols-2 gap-4 pt-2 border-t border-slate-800/60">
          <div>
            <span className="text-[10px] text-slate-400 block">{t.transmitTx}</span>
            <div className="text-xl font-bold font-mono text-[#0091FF] mt-0.5">
              {currentTx} <span className="text-xs font-normal text-slate-400">Mbps</span>
            </div>
            <div className="w-full bg-slate-800 h-1 rounded-full mt-2 overflow-hidden">
              <div
                className="bg-[#0091FF] h-full rounded-full"
                style={{ width: `${Math.min(100, (currentTx / 500) * 100)}%` }}
              />
            </div>
          </div>

          <div>
            <span className="text-[10px] text-slate-400 block">{t.receiveRx}</span>
            <div className="text-xl font-bold font-mono text-[#00E5FF] mt-0.5">
              {currentRx} <span className="text-xs font-normal text-slate-400">Mbps</span>
            </div>
            <div className="w-full bg-slate-800 h-1 rounded-full mt-2 overflow-hidden">
              <div
                className="bg-[#00E5FF] h-full rounded-full"
                style={{ width: `${Math.min(100, (currentRx / 800) * 100)}%` }}
              />
            </div>
          </div>
        </div>
      </div>

      {/* Wireless Signal Strength (RF dBm) Card */}
      <div className="p-4 rounded-2xl bg-[#111A2E] border border-slate-800 flex items-center justify-between text-start">
        <div>
          <span className="text-[11px] font-medium text-slate-400 block">{t.rfSignal}</span>
          <div className="text-lg font-bold font-mono text-white mt-0.5">
            {selectedDevice.metrics.signalDbm} <span className="text-xs font-normal text-slate-400">dBm</span>
          </div>
          <span className="text-[10px] text-emerald-400 mt-0.5 block">
            {t.rfSignalSubtext}
          </span>
        </div>
        <div className="w-10 h-10 rounded-xl bg-[#17233D] flex items-center justify-center text-emerald-400 border border-slate-700/60">
          <Radio className="w-5 h-5" />
        </div>
      </div>
    </div>
  );
};
