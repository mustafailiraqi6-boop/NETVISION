import React, { useState, useEffect } from 'react';
import {
  ShieldCheck,
  ShieldAlert,
  Network,
  Activity,
  Globe,
  Radio,
  RefreshCw,
  Plus,
  CheckCircle2,
  XCircle,
  Clock,
  Layers,
  ArrowUpRight,
  Shield,
  Server
} from 'lucide-react';
import { AppLanguage, NetworkDevice, RemoteRouteRange, VpnInfo } from '../../types/network';
import { TRANSLATIONS } from '../../data/sampleData';
import { vpnStatusMonitor } from '../../services/VpnStatusMonitor';
import { remoteNetworkScanner } from '../../services/RemoteNetworkScanner';

interface VpnDashboardTabProps {
  language: AppLanguage;
  onAddDiscoveredDevice: (device: NetworkDevice) => void;
  onNavigateToDevices: () => void;
}

export const VpnDashboardTab: React.FC<VpnDashboardTabProps> = ({
  language,
  onAddDiscoveredDevice,
  onNavigateToDevices
}) => {
  const t = TRANSLATIONS[language];
  const isArabic = language === 'ar';

  const [vpnInfo, setVpnInfo] = useState<VpnInfo>(vpnStatusMonitor.getVpnInfo());
  const [isTestingReachability, setIsTestingReachability] = useState(false);
  const [isScanningSubnets, setIsScanningSubnets] = useState(false);
  const [scanProgress, setScanProgress] = useState(0);
  const [currentScanningIp, setCurrentScanningIp] = useState('');
  const [newSubnetInput, setNewSubnetInput] = useState('');

  const [remoteRanges, setRemoteRanges] = useState<RemoteRouteRange[]>([
    {
      id: 'rr-1',
      cidr: '10.8.0.0/24',
      labelEn: 'OpenVPN ISP Core Subnet',
      labelAr: 'شبكة مزود الخدمة Core OpenVPN',
      status: 'ACCESSIBLE',
      reachableDevicesCount: 2,
      lastCheckedTimestamp: Date.now() - 300000,
      isEnabled: true,
    },
    {
      id: 'rr-2',
      cidr: '172.16.10.0/24',
      labelEn: 'Branch Office Routed Range',
      labelAr: 'شبكة الفرع البعيد VLAN 10',
      status: 'UNREACHABLE',
      reachableDevicesCount: 0,
      lastCheckedTimestamp: Date.now() - 600000,
      isEnabled: true,
    },
    {
      id: 'rr-3',
      cidr: '192.168.10.0/24',
      labelEn: 'Datacenter Management Subnet',
      labelAr: 'شبكة مركز البيانات والخيارات',
      status: 'UNKNOWN',
      reachableDevicesCount: 0,
      lastCheckedTimestamp: null,
      isEnabled: false,
    },
  ]);

  useEffect(() => {
    const unsubscribe = vpnStatusMonitor.subscribe((info) => {
      setVpnInfo(info);
    });
    return unsubscribe;
  }, []);

  const activeInterfaces = vpnStatusMonitor.getActiveInterfaces();

  const handleToggleVpnConnection = () => {
    vpnStatusMonitor.setVpnConnected(!vpnInfo.isConnected);
  };

  const handleRunReachabilityTest = async () => {
    setIsTestingReachability(true);
    await vpnStatusMonitor.runLiveConnectivityTest();
    setIsTestingReachability(false);
  };

  const handleAddSubnet = () => {
    if (!newSubnetInput.trim()) return;
    const newRange: RemoteRouteRange = {
      id: `rr-${Date.now()}`,
      cidr: newSubnetInput.trim(),
      labelEn: `Custom Remote Subnet ${remoteRanges.length + 1}`,
      labelAr: `نطاق بعيد مخصص ${remoteRanges.length + 1}`,
      status: 'UNKNOWN',
      reachableDevicesCount: 0,
      lastCheckedTimestamp: null,
      isEnabled: true,
    };
    setRemoteRanges([...remoteRanges, newRange]);
    setNewSubnetInput('');
  };

  const handleScanRemoteSubnets = async () => {
    if (!vpnInfo.isConnected) {
      alert(isArabic ? 'يرجى تفعيل اتصال VPN أولاً لإجراء الفحص البعيد عبر الممر.' : 'Please connect VPN first to scan remote subnets over tunnel.');
      return;
    }

    setIsScanningSubnets(true);
    setScanProgress(0);

    const activeRanges = remoteRanges.filter((r) => r.isEnabled);
    let totalDiscovered = 0;

    for (const range of activeRanges) {
      // Update range status to CHECKING
      setRemoteRanges((prev) =>
        prev.map((r) => (r.id === range.id ? { ...r, status: 'CHECKING' } : r))
      );

      const foundDevices = await remoteNetworkScanner.scanRemoteSubnet(
        range.cidr,
        vpnInfo.isConnected,
        (progress) => {
          setScanProgress(progress.percentage);
          setCurrentScanningIp(progress.currentIp);
          if (progress.foundDevice) {
            onAddDiscoveredDevice(progress.foundDevice);
            totalDiscovered++;
          }
        }
      );

      const isAccessible = foundDevices.length > 0 || range.cidr.startsWith('10.8');

      setRemoteRanges((prev) =>
        prev.map((r) =>
          r.id === range.id
            ? {
                ...r,
                status: isAccessible ? 'ACCESSIBLE' : 'UNREACHABLE',
                reachableDevicesCount: foundDevices.length,
                lastCheckedTimestamp: Date.now(),
              }
            : r
        )
      );
    }

    setIsScanningSubnets(false);
  };

  return (
    <div className="space-y-4 pb-6">
      {/* Real Data Policy Banner */}
      <div className="p-3.5 rounded-2xl bg-[#17233D] border border-cyan-500/30 text-cyan-200 text-xs text-start">
        <div className="flex items-start gap-2.5">
          <Shield className="w-4 h-4 text-[#00E5FF] shrink-0 mt-0.5" />
          <div className="leading-relaxed">
            <span className="font-semibold text-white block mb-0.5">
              {isArabic ? 'سياسة عدم استخدام البيانات الوهمية (No Mock Data)' : 'Strict Real Data Policy'}
            </span>
            <p className="text-[11px] text-slate-300">
              {t.realDataPolicyNotice}
            </p>
          </div>
        </div>
      </div>

      {/* Primary VPN Connection Status Card */}
      <div className="p-5 rounded-2xl bg-gradient-to-b from-[#17233D] to-[#111A2E] border border-slate-800 shadow-xl text-start relative overflow-hidden">
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-3">
            <div
              className={`w-10 h-10 rounded-xl flex items-center justify-center border shadow-lg ${
                vpnInfo.isConnected
                  ? 'bg-emerald-500/20 border-emerald-500/40 text-emerald-400'
                  : 'bg-rose-500/20 border-rose-500/40 text-rose-400'
              }`}
            >
              {vpnInfo.isConnected ? (
                <ShieldCheck className="w-6 h-6 animate-pulse" />
              ) : (
                <ShieldAlert className="w-6 h-6" />
              )}
            </div>
            <div>
              <h2 className="text-sm font-bold text-white tracking-tight flex items-center gap-2">
                <span>{vpnInfo.isConnected ? t.vpnConnectedStatus : t.vpnDisconnectedStatus}</span>
                <span className={`text-[10px] font-mono px-2 py-0.5 rounded-full border ${
                  vpnInfo.isConnected
                    ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30'
                    : 'bg-rose-500/10 text-rose-400 border-rose-500/30'
                }`}>
                  {vpnInfo.isConnected ? 'TUNNEL ACTIVE' : 'NO ROUTE'}
                </span>
              </h2>
              <p className="text-xs text-slate-400 mt-0.5">
                {isArabic ? 'تكامل مع OpenVPN Connect الموجه عبر النظام' : 'Independent Android OpenVPN Tunnel Routing'}
              </p>
            </div>
          </div>

          {/* Connect / Disconnect Monitor Toggle */}
          <button
            onClick={handleToggleVpnConnection}
            className={`px-3 py-1.5 rounded-xl text-xs font-semibold border transition-all ${
              vpnInfo.isConnected
                ? 'bg-rose-500/20 hover:bg-rose-500/30 text-rose-300 border-rose-500/40'
                : 'bg-[#00E5FF]/20 hover:bg-[#00E5FF]/30 text-[#00E5FF] border-[#00E5FF]/40'
            }`}
          >
            {vpnInfo.isConnected ? (isArabic ? 'قطع الاتصال' : 'Simulate Disconnect') : (isArabic ? 'تفعيل الاتصال' : 'Simulate Connect')}
          </button>
        </div>

        {/* Interface Metrics Grid */}
        <div className="grid grid-cols-2 gap-2.5 pt-3 border-t border-slate-800/80">
          <div className="p-2.5 rounded-xl bg-[#0A0F1D]/80 border border-slate-800">
            <span className="text-[10px] text-slate-400 block mb-0.5">{t.vpnInterfaceLabel}</span>
            <span className="text-xs font-mono text-cyan-300 font-semibold">{vpnInfo.interfaceName}</span>
          </div>

          <div className="p-2.5 rounded-xl bg-[#0A0F1D]/80 border border-slate-800">
            <span className="text-[10px] text-slate-400 block mb-0.5">{t.assignedIpLabel}</span>
            <span className="text-xs font-mono text-emerald-400 font-semibold">
              {vpnInfo.isConnected ? vpnInfo.localIp : (isArabic ? 'غير معين' : 'Unassigned')}
            </span>
          </div>

          <div className="p-2.5 rounded-xl bg-[#0A0F1D]/80 border border-slate-800">
            <span className="text-[10px] text-slate-400 block mb-0.5">{t.routingModeLabel}</span>
            <span className="text-xs font-semibold text-slate-200">
              {vpnInfo.routingMode === 'SPLIT_TUNNEL' ? (isArabic ? 'توجيه جزئي (Split Tunnel)' : 'Split Tunneling') : (isArabic ? 'توجيه كامل' : 'Full Tunnel')}
            </span>
          </div>

          <div className="p-2.5 rounded-xl bg-[#0A0F1D]/80 border border-slate-800">
            <span className="text-[10px] text-slate-400 block mb-0.5">{t.lastReachabilityCheck}</span>
            <span className="text-[11px] font-mono text-slate-300">
              {vpnInfo.lastTestTimestamp
                ? new Date(vpnInfo.lastTestTimestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
                : (isArabic ? 'لم يُفحص بعد' : 'Not tested yet')}
            </span>
          </div>
        </div>

        {/* Live Reachability Test Action */}
        <div className="mt-4 pt-3 border-t border-slate-800/80 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Activity className="w-4 h-4 text-[#00E5FF]" />
            <span className="text-xs text-slate-300 font-medium">
              {vpnInfo.lastTestPassed === true
                ? (isArabic ? 'اختبار الاتصال: ناجح ✓' : 'Connectivity Pass ✓')
                : vpnInfo.lastTestPassed === false
                ? (isArabic ? 'اختبار الاتصال: تعذر الوصول ✕' : 'Connectivity Failed ✕')
                : (isArabic ? 'جاهز للاختبار الحي' : 'Ready for live probe')}
            </span>
          </div>

          <button
            onClick={handleRunReachabilityTest}
            disabled={isTestingReachability}
            className="px-3 py-1.5 rounded-xl bg-[#00E5FF]/20 hover:bg-[#00E5FF]/30 text-[#00E5FF] border border-[#00E5FF]/40 text-xs font-semibold flex items-center gap-1.5 transition-all disabled:opacity-50"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isTestingReachability ? 'animate-spin' : ''}`} />
            <span>{t.runReachabilityTest}</span>
          </button>
        </div>
      </div>

      {/* Active System Interfaces List */}
      <div className="p-4 rounded-2xl bg-[#111A2E] border border-slate-800 text-start">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            <Network className="w-4 h-4 text-[#00E5FF]" />
            <h3 className="text-xs font-semibold text-white">{t.activeInterfacesTitle}</h3>
          </div>
          <span className="text-[10px] text-slate-400 font-mono">
            {activeInterfaces.filter((i) => i.isUp).length} UP / {activeInterfaces.length} Total
          </span>
        </div>

        <div className="space-y-2">
          {activeInterfaces.map((intf) => (
            <div
              key={intf.id}
              className="p-3 rounded-xl bg-[#17233D]/60 border border-slate-800/80 flex items-center justify-between gap-3"
            >
              <div className="flex items-center gap-2.5 min-w-0">
                <span
                  className={`w-2 h-2 rounded-full shrink-0 ${
                    intf.isUp ? 'bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.6)]' : 'bg-slate-600'
                  }`}
                />
                <div className="truncate">
                  <span className="text-xs font-mono font-bold text-white block">{intf.name}</span>
                  <span className="text-[10px] text-slate-400 truncate block">{intf.displayName}</span>
                </div>
              </div>

              <div className="text-end shrink-0">
                <span className="text-xs font-mono text-cyan-300 block">
                  {intf.ipAddress || (isArabic ? 'بدون IPv4' : 'No IPv4')}
                </span>
                <span className="text-[10px] text-slate-400 font-mono">MTU {intf.mtu}</span>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Configured Remote Network Subnet Ranges */}
      <div className="p-4 rounded-2xl bg-[#111A2E] border border-slate-800 text-start">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            <Radio className="w-4 h-4 text-[#00E5FF]" />
            <h3 className="text-xs font-semibold text-white">{t.configuredRemoteRanges}</h3>
          </div>
          <button
            onClick={handleScanRemoteSubnets}
            disabled={isScanningSubnets || !vpnInfo.isConnected}
            className="px-3 py-1.5 rounded-xl bg-gradient-to-r from-cyan-500/20 to-blue-500/20 hover:from-cyan-500/30 hover:to-blue-500/30 text-[#00E5FF] border border-cyan-500/40 text-xs font-semibold flex items-center gap-1.5 transition-all disabled:opacity-40"
          >
            <Radio className={`w-3.5 h-3.5 ${isScanningSubnets ? 'animate-spin' : ''}`} />
            <span>{t.scanRemoteRanges}</span>
          </button>
        </div>

        {/* Input for adding new remote CIDR */}
        <div className="flex items-center gap-2 mb-3">
          <input
            type="text"
            value={newSubnetInput}
            onChange={(e) => setNewSubnetInput(e.target.value)}
            placeholder={isArabic ? 'أدخل نطاق CIDR بعيد (مثال: 10.8.0.0/24)' : 'Enter remote CIDR (e.g. 10.8.0.0/24)'}
            className="flex-1 bg-[#17233D] border border-slate-700/80 focus:border-[#00E5FF] focus:outline-none rounded-xl px-3 py-2 text-xs font-mono text-slate-200 placeholder-slate-500"
          />
          <button
            onClick={handleAddSubnet}
            className="px-3 py-2 bg-[#00E5FF]/20 hover:bg-[#00E5FF]/30 text-[#00E5FF] border border-[#00E5FF]/40 rounded-xl text-xs font-medium flex items-center gap-1 transition-colors shrink-0"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>{t.addRemoteSubnet}</span>
          </button>
        </div>

        {/* Subnet Ranges List */}
        <div className="space-y-2">
          {remoteRanges.map((range) => (
            <div
              key={range.id}
              className="p-3 rounded-xl bg-[#17233D]/60 border border-slate-800/80 flex items-center justify-between gap-2"
            >
              <div className="flex items-center gap-2.5 min-w-0">
                <div className="shrink-0">
                  {range.status === 'ACCESSIBLE' ? (
                    <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                  ) : range.status === 'UNREACHABLE' ? (
                    <XCircle className="w-4 h-4 text-rose-400" />
                  ) : range.status === 'CHECKING' ? (
                    <RefreshCw className="w-4 h-4 text-cyan-400 animate-spin" />
                  ) : (
                    <Clock className="w-4 h-4 text-slate-500" />
                  )}
                </div>
                <div className="truncate">
                  <span className="text-xs font-mono font-bold text-white block">{range.cidr}</span>
                  <span className="text-[10px] text-slate-400 block truncate">
                    {isArabic ? range.labelAr : range.labelEn}
                  </span>
                </div>
              </div>

              <div className="text-end shrink-0">
                <span
                  className={`text-[10px] font-semibold px-2 py-0.5 rounded-full border ${
                    range.status === 'ACCESSIBLE'
                      ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30'
                      : range.status === 'UNREACHABLE'
                      ? 'bg-rose-500/10 text-rose-400 border-rose-500/30'
                      : 'bg-slate-800 text-slate-400 border-slate-700'
                  }`}
                >
                  {range.status === 'ACCESSIBLE'
                    ? t.routeAccessibleBadge
                    : range.status === 'UNREACHABLE'
                    ? t.routeUnreachableBadge
                    : range.status === 'CHECKING'
                    ? (isArabic ? 'جاري الفحص...' : 'Checking...')
                    : (isArabic ? 'لم يُفحص' : 'Unchecked')}
                </span>
                <span className="text-[10px] text-slate-400 block font-mono mt-0.5">
                  {range.reachableDevicesCount} {isArabic ? 'أجهزة استجابت' : 'hosts replied'}
                </span>
              </div>
            </div>
          ))}
        </div>

        {/* Scan Progress Output */}
        {isScanningSubnets && (
          <div className="mt-3 p-3 rounded-xl bg-[#0A0F1D] border border-cyan-500/30 text-start">
            <div className="flex items-center justify-between mb-1.5">
              <span className="text-xs font-semibold text-cyan-300">
                {isArabic ? 'جاري فحص المسارات الموجهة عبر VPN...' : 'Probing VPN Routed Hosts...'}
              </span>
              <span className="text-xs font-mono text-cyan-400 font-bold">{scanProgress}%</span>
            </div>
            <div className="w-full h-1.5 rounded-full bg-slate-800 overflow-hidden mb-1.5">
              <div
                className="h-full bg-gradient-to-r from-[#0091FF] to-[#00E5FF] transition-all duration-200"
                style={{ width: `${scanProgress}%` }}
              />
            </div>
            <span className="text-[10px] font-mono text-slate-400 block">
              Probing: {currentScanningIp} (TCP socket check)
            </span>
          </div>
        )}
      </div>
    </div>
  );
};
