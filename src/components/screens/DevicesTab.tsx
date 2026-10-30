import React, { useState, useMemo } from 'react';
import {
  Search,
  SlidersHorizontal,
  Wifi,
  Server,
  Router as RouterIcon,
  Shield,
  Layers,
  ArrowUpDown,
  X,
  ChevronRight
} from 'lucide-react';
import { NetworkDevice, Manufacturer, DeviceStatus, DeviceType, AppLanguage } from '../../types/network';
import { TRANSLATIONS } from '../../data/sampleData';

interface DevicesTabProps {
  devices: NetworkDevice[];
  language: AppLanguage;
  initialVendorFilter?: string | null;
  onSelectDevice: (device: NetworkDevice) => void;
}

export const DevicesTab: React.FC<DevicesTabProps> = ({
  devices,
  language,
  initialVendorFilter,
  onSelectDevice,
}) => {
  const t = TRANSLATIONS[language];
  const isArabic = language === 'ar';

  const [searchQuery, setSearchQuery] = useState('');
  const [selectedVendor, setSelectedVendor] = useState<Manufacturer | 'ALL'>(
    (initialVendorFilter as Manufacturer) || 'ALL'
  );
  const [selectedStatus, setSelectedStatus] = useState<DeviceStatus | 'ALL'>('ALL');
  const [selectedType, setSelectedType] = useState<DeviceType | 'ALL'>('ALL');
  const [sortBy, setSortBy] = useState<'name' | 'ip' | 'latency'>('name');

  const filteredDevices = useMemo(() => {
    return devices
      .filter((device) => {
        const matchesQuery =
          searchQuery.trim() === '' ||
          device.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
          device.ipAddress.includes(searchQuery) ||
          device.macAddress.toLowerCase().includes(searchQuery.toLowerCase()) ||
          device.model.toLowerCase().includes(searchQuery.toLowerCase());

        const matchesVendor = selectedVendor === 'ALL' || device.manufacturer === selectedVendor;
        const matchesStatus = selectedStatus === 'ALL' || device.status === selectedStatus;
        const matchesType = selectedType === 'ALL' || device.deviceType === selectedType;

        return matchesQuery && matchesVendor && matchesStatus && matchesType;
      })
      .sort((a, b) => {
        if (sortBy === 'ip') {
          return a.ipAddress.localeCompare(b.ipAddress, undefined, { numeric: true });
        }
        if (sortBy === 'latency') {
          return a.metrics.pingLatencyMs - b.metrics.pingLatencyMs;
        }
        return a.name.localeCompare(b.name);
      });
  }, [devices, searchQuery, selectedVendor, selectedStatus, selectedType, sortBy]);

  const getDeviceIcon = (type: DeviceType) => {
    switch (type) {
      case 'ACCESS_POINT':
        return Wifi;
      case 'GATEWAY':
        return Shield;
      case 'SWITCH':
        return Layers;
      case 'ROUTER':
      default:
        return RouterIcon;
    }
  };

  const getDeviceTypeLabel = (type: DeviceType) => {
    if (!isArabic) return type;
    switch (type) {
      case 'ACCESS_POINT':
        return 'نقطة وصول';
      case 'GATEWAY':
        return 'بوابة حماية';
      case 'SWITCH':
        return 'سويتش';
      case 'ROUTER':
        return 'راوتر';
      case 'CONTROLLER':
        return 'وحدة تحكم';
      default:
        return type;
    }
  };

  const getStatusLabel = (status: DeviceStatus) => {
    if (!isArabic) return status;
    switch (status) {
      case 'ONLINE':
        return 'متصل';
      case 'WARNING':
        return 'تأخير مرتفع';
      case 'OFFLINE':
        return 'غير متصل';
      default:
        return status;
    }
  };

  return (
    <div className="space-y-3 pb-6">
      {/* Title & Count */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-base font-semibold text-white tracking-tight">
            {isArabic ? 'معدات وأجهزة الشبكة' : 'Discovered Network Equipment'}
          </h2>
          <p className="text-xs text-slate-400 mt-0.5">
            {t.showingDevices
              .replace('{count}', String(filteredDevices.length))
              .replace('{total}', String(devices.length))}
          </p>
        </div>
      </div>

      {/* Search Bar */}
      <div className="relative">
        <Search className={`w-4 h-4 absolute top-3 text-cyan-400 ${isArabic ? 'right-3' : 'left-3'}`} />
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder={t.searchPlaceholder}
          className={`w-full bg-[#111A2E] border border-slate-800 focus:border-[#00E5FF] focus:outline-none rounded-xl py-2.5 text-xs text-slate-200 placeholder-slate-500 transition-colors ${
            isArabic ? 'pr-9 pl-8' : 'pl-9 pr-8'
          }`}
        />
        {searchQuery && (
          <button
            onClick={() => setSearchQuery('')}
            className={`absolute top-2.5 text-slate-400 hover:text-white ${isArabic ? 'left-3' : 'right-3'}`}
          >
            <X className="w-4 h-4" />
          </button>
        )}
      </div>

      {/* Filter Horizontal Carousel */}
      <div className="space-y-2">
        {/* Vendor Filter Buttons */}
        <div className="flex items-center gap-1.5 overflow-x-auto no-scrollbar py-0.5">
          {(['ALL', 'UBIQUITI', 'MIKROTIK', 'CISCO'] as const).map((vendor) => {
            const isSelected = selectedVendor === vendor;
            const label =
              vendor === 'ALL'
                ? t.allVendors
                : vendor === 'UBIQUITI'
                ? 'Ubiquiti (UBNT)'
                : vendor === 'MIKROTIK'
                ? 'MikroTik'
                : 'Cisco';

            return (
              <button
                key={vendor}
                onClick={() => setSelectedVendor(vendor)}
                className={`px-3 py-1.5 rounded-lg text-xs font-medium whitespace-nowrap transition-all border ${
                  isSelected
                    ? 'bg-[#00E5FF]/20 text-[#00E5FF] border-[#00E5FF]/50 shadow-sm'
                    : 'bg-[#111A2E] text-slate-400 border-slate-800 hover:text-slate-200'
                }`}
              >
                {label}
              </button>
            );
          })}
        </div>

        {/* Secondary Filter & Sort Bar */}
        <div className="flex items-center justify-between text-xs text-slate-400 pt-1 border-t border-slate-800/60">
          <div className="flex items-center gap-2">
            <span className="text-[11px] text-slate-500">{t.sortBy}:</span>
            <button
              onClick={() => setSortBy('name')}
              className={`px-2 py-0.5 rounded text-[11px] ${
                sortBy === 'name' ? 'text-[#00E5FF] font-semibold' : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              {t.sortByName}
            </button>
            <span>·</span>
            <button
              onClick={() => setSortBy('ip')}
              className={`px-2 py-0.5 rounded text-[11px] ${
                sortBy === 'ip' ? 'text-[#00E5FF] font-semibold' : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              {t.sortByIp}
            </button>
            <span>·</span>
            <button
              onClick={() => setSortBy('latency')}
              className={`px-2 py-0.5 rounded text-[11px] ${
                sortBy === 'latency' ? 'text-[#00E5FF] font-semibold' : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              {t.sortByLatency}
            </button>
          </div>

          <div className="flex items-center gap-1.5">
            {(['ALL', 'ONLINE', 'WARNING', 'OFFLINE'] as const).map((status) => {
              if (status === 'ALL') return null;
              const isActive = selectedStatus === status;
              const statusDisplay =
                status === 'ONLINE' ? t.online : status === 'WARNING' ? t.warning : t.offline;

              return (
                <button
                  key={status}
                  onClick={() => setSelectedStatus(selectedStatus === status ? 'ALL' : status)}
                  className={`text-[10px] px-2 py-0.5 rounded-full border transition-all ${
                    isActive
                      ? status === 'ONLINE'
                        ? 'bg-emerald-500/20 text-emerald-400 border-emerald-500/40'
                        : status === 'WARNING'
                        ? 'bg-amber-500/20 text-amber-400 border-amber-500/40'
                        : 'bg-rose-500/20 text-rose-400 border-rose-500/40'
                      : 'bg-transparent text-slate-500 border-slate-800'
                  }`}
                >
                  {statusDisplay}
                </button>
              );
            })}
          </div>
        </div>
      </div>

      {/* Device List */}
      <div className="space-y-2.5">
        {filteredDevices.length === 0 ? (
          <div className="p-8 text-center rounded-2xl bg-[#111A2E] border border-slate-800 text-slate-400 text-xs">
            <Server className="w-8 h-8 text-slate-600 mx-auto mb-2" />
            <p className="font-medium text-slate-300">
              {isArabic ? 'لم يتم العثور على أجهزة مطابقة للبحث' : 'No devices match current filters'}
            </p>
            <p className="text-[11px] text-slate-500 mt-1">
              {isArabic ? 'حاول تغيير معايير التصفية أو مسح عبارة البحث' : 'Try clearing your search query or reset vendor filters'}
            </p>
          </div>
        ) : (
          filteredDevices.map((device) => {
            const Icon = getDeviceIcon(device.deviceType);
            const statusDot =
              device.status === 'ONLINE'
                ? 'bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.6)]'
                : device.status === 'WARNING'
                ? 'bg-amber-400 shadow-[0_0_8px_rgba(251,191,36,0.6)]'
                : 'bg-rose-400';

            const vendorColor =
              device.manufacturer === 'UBIQUITI'
                ? 'text-[#0091FF]'
                : device.manufacturer === 'MIKROTIK'
                ? 'text-[#00E5FF]'
                : 'text-sky-400';

            return (
              <div
                key={device.id}
                onClick={() => onSelectDevice(device)}
                className="p-3.5 rounded-2xl bg-[#111A2E] border border-slate-800/90 hover:border-cyan-500/50 hover:bg-[#142038] cursor-pointer transition-all shadow-sm group"
              >
                <div className="flex items-start justify-between gap-3">
                  {/* Left: Device Icon & Core Identity */}
                  <div className="flex items-start gap-3">
                    <div className="w-9 h-9 rounded-xl bg-[#17233D] border border-slate-700/60 flex items-center justify-center shrink-0 mt-0.5 group-hover:border-[#00E5FF]/40 transition-colors">
                      <Icon className="w-4 h-4 text-[#00E5FF]" />
                    </div>
                    <div>
                      <div className="flex items-center gap-2">
                        <span className={`w-2 h-2 rounded-full ${statusDot}`} />
                        <h3 className="text-xs font-semibold text-white group-hover:text-[#00E5FF] transition-colors leading-snug">
                          {device.name}
                        </h3>
                      </div>
                      <div className="flex items-center gap-2 mt-1 text-[11px] text-slate-400">
                        <span className={`font-medium ${vendorColor}`}>{device.manufacturer}</span>
                        <span>·</span>
                        <span>{device.model}</span>
                        <span>·</span>
                        <span>{getDeviceTypeLabel(device.deviceType)}</span>
                      </div>
                    </div>
                  </div>

                  {/* Right: Ping Latency & Mirrored Chevron */}
                  <div className="flex items-center gap-2 shrink-0">
                    <div className="text-end">
                      {device.status !== 'OFFLINE' ? (
                        <>
                          <span
                            className={`text-xs font-mono font-semibold ${
                              device.metrics.pingLatencyMs > 30 ? 'text-amber-400' : 'text-emerald-400'
                            }`}
                          >
                            {device.metrics.pingLatencyMs} ms
                          </span>
                          <span className="block text-[10px] text-slate-500 font-mono">
                            CPU {device.metrics.cpuUsagePercent}%
                          </span>
                        </>
                      ) : (
                        <span className="text-[11px] text-rose-400 font-medium">{getStatusLabel('OFFLINE')}</span>
                      )}
                    </div>
                    <ChevronRight className="w-4 h-4 text-slate-600 group-hover:text-slate-300 transition-colors rtl:rotate-180" />
                  </div>
                </div>

                {/* Network IP & MAC Footer Strip */}
                <div className="mt-3 pt-2 border-t border-slate-800/70 flex items-center justify-between text-[11px] font-mono text-slate-400">
                  <div className="flex items-center gap-3">
                    <span>
                      IP: <strong className="text-slate-200 font-medium">{device.ipAddress}</strong>
                    </span>
                    <span className="hidden sm:inline">·</span>
                    <span className="hidden sm:inline">MAC: {device.macAddress}</span>
                  </div>
                  <span className="text-[10px] text-slate-500 font-sans">{device.firmwareVersion}</span>
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
};
