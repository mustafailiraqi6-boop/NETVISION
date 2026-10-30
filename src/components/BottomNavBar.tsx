import React from 'react';
import { LayoutDashboard, Network, ShieldCheck, Radar, Activity, Settings } from 'lucide-react';
import { TabKey } from '../types/network';

interface BottomNavBarProps {
  currentTab: TabKey;
  onTabChange: (tab: TabKey) => void;
  labels: {
    dashboard: string;
    devices: string;
    vpn: string;
    scanner: string;
    monitoring: string;
    settings: string;
  };
}

export const BottomNavBar: React.FC<BottomNavBarProps> = ({ currentTab, onTabChange, labels }) => {
  const tabs: { key: TabKey; label: string; icon: React.ComponentType<{ className?: string }> }[] = [
    { key: 'dashboard', label: labels.dashboard, icon: LayoutDashboard },
    { key: 'devices', label: labels.devices, icon: Network },
    { key: 'vpn', label: labels.vpn, icon: ShieldCheck },
    { key: 'scanner', label: labels.scanner, icon: Radar },
    { key: 'monitoring', label: labels.monitoring, icon: Activity },
    { key: 'settings', label: labels.settings, icon: Settings },
  ];

  return (
    <nav className="shrink-0 h-16 border-t border-slate-800/80 bg-[#111A2E]/95 backdrop-blur-md px-2 flex items-center justify-around z-30 select-none">
      {tabs.map((tab) => {
        const Icon = tab.icon;
        const isActive = currentTab === tab.key;
        return (
          <button
            key={tab.key}
            onClick={() => onTabChange(tab.key)}
            className={`flex flex-col items-center justify-center min-w-[56px] py-1 transition-all duration-200 relative ${
              isActive ? 'text-[#00E5FF]' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <div
              className={`flex items-center justify-center w-12 h-7 rounded-full transition-all duration-200 ${
                isActive ? 'bg-[#00E5FF]/20 text-[#00E5FF]' : 'text-slate-400'
              }`}
            >
              <Icon className="w-5 h-5" />
            </div>
            <span className={`text-[10px] mt-0.5 tracking-tight font-medium ${isActive ? 'font-semibold text-[#00E5FF]' : 'text-slate-400'}`}>
              {tab.label}
            </span>
          </button>
        );
      })}
    </nav>
  );
};
