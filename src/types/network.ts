export type DeviceType = 'ROUTER' | 'SWITCH' | 'ACCESS_POINT' | 'GATEWAY' | 'CONTROLLER';

export type Manufacturer = 'UBIQUITI' | 'MIKROTIK' | 'CISCO' | 'GENERIC';

export type DeviceStatus = 'ONLINE' | 'OFFLINE' | 'WARNING';

export type ActivitySeverity = 'INFO' | 'SUCCESS' | 'WARNING' | 'ALERT';

export interface NetworkMetrics {
  signalDbm: number;
  txRateMbps: number;
  rxRateMbps: number;
  uptimeFormatted: string;
  cpuUsagePercent: number;
  memoryUsagePercent: number;
  pingLatencyMs: number;
}

export interface NetworkDevice {
  id: string;
  name: string;
  ipAddress: string;
  macAddress: string;
  manufacturer: Manufacturer;
  model: string;
  deviceType: DeviceType;
  status: DeviceStatus;
  firmwareVersion: string;
  lastSeenTimestamp: number;
  metrics: NetworkMetrics;
}

export interface NetworkRange {
  id: string;
  cidr: string;
  label?: string;
  labelEn: string;
  labelAr: string;
  isEnabled: boolean;
}

export interface RecentDeviceActivity {
  id: string;
  deviceName: string;
  ipAddress: string;
  messageEn: string;
  messageAr: string;
  timeAgoEn: string;
  timeAgoAr: string;
  severity: ActivitySeverity;
}

export type TabKey = 'dashboard' | 'devices' | 'vpn' | 'scanner' | 'monitoring' | 'settings';
export type AppLanguage = 'en' | 'ar';
export type AppTheme = 'dark' | 'light';

export type VpnConnectionStatus = 'CONNECTED' | 'DISCONNECTED' | 'CHECKING';

export interface VpnInfo {
  isConnected: boolean;
  interfaceName: string;
  localIp: string;
  routingMode: 'SPLIT_TUNNEL' | 'FULL_TUNNEL' | 'UNKNOWN';
  lastTestTimestamp: number | null;
  lastTestPassed: boolean | null;
  assignedGateway?: string;
}

export interface NetworkInterfaceItem {
  id: string;
  name: string;
  displayName: string;
  type: 'VPN' | 'WIFI' | 'CELLULAR' | 'ETHERNET';
  ipAddress: string | null;
  mtu: number;
  isUp: boolean;
}

export interface RemoteRouteRange {
  id: string;
  cidr: string;
  labelEn: string;
  labelAr: string;
  status: 'ACCESSIBLE' | 'UNREACHABLE' | 'CHECKING' | 'UNKNOWN';
  reachableDevicesCount: number;
  lastCheckedTimestamp: number | null;
  isEnabled: boolean;
}
