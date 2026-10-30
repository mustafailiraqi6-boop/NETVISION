/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect } from 'react';
import { NetworkDevice, TabKey, AppLanguage, AppTheme } from './types/network';
import { SAMPLE_DEVICES, SAMPLE_ACTIVITIES, TRANSLATIONS } from './data/sampleData';
import { BottomNavBar } from './components/BottomNavBar';
import { AndroidPhoneFrame } from './components/AndroidPhoneFrame';
import { DashboardTab } from './components/screens/DashboardTab';
import { DevicesTab } from './components/screens/DevicesTab';
import { ScannerTab } from './components/screens/ScannerTab';
import { MonitoringTab } from './components/screens/MonitoringTab';
import { SettingsTab } from './components/screens/SettingsTab';
import { VpnDashboardTab } from './components/screens/VpnDashboardTab';
import { DeviceDetailModal } from './components/DeviceDetailModal';
import { AndroidCodeExplorer } from './components/AndroidCodeExplorer';
import { Code2, Globe, Moon, Sun, Layers, ShieldCheck } from 'lucide-react';

export default function App() {
  const [currentTab, setCurrentTab] = useState<TabKey>('dashboard');
  const [language, setLanguage] = useState<AppLanguage>('ar');
  const [theme, setTheme] = useState<AppTheme>('dark');
  const [devices, setDevices] = useState<NetworkDevice[]>(SAMPLE_DEVICES);
  const [activities, setActivities] = useState(SAMPLE_ACTIVITIES);
  const [selectedDevice, setSelectedDevice] = useState<NetworkDevice | null>(null);
  const [vendorFilter, setVendorFilter] = useState<string | null>(null);
  const [isPhoneView, setIsPhoneView] = useState(true);
  const [isCodeExplorerOpen, setIsCodeExplorerOpen] = useState(false);

  const t = TRANSLATIONS[language];
  const isArabic = language === 'ar';

  const handleAddDiscoveredDevice = (newDevice: NetworkDevice) => {
    setDevices((prev) => {
      if (prev.some((d) => d.ipAddress === newDevice.ipAddress)) {
        return prev;
      }
      return [...prev, newDevice];
    });
  };

  // Sync RTL / LTR layout direction to document
  useEffect(() => {
    document.documentElement.dir = isArabic ? 'rtl' : 'ltr';
    document.documentElement.lang = language;
  }, [language, isArabic]);

  // Sync Dark / Light theme classes to document
  useEffect(() => {
    if (theme === 'dark') {
      document.documentElement.classList.add('dark');
    } else {
      document.documentElement.classList.remove('dark');
    }
  }, [theme]);

  const handleNavigateToDevices = (filterVendor?: string) => {
    if (filterVendor) {
      setVendorFilter(filterVendor);
    } else {
      setVendorFilter(null);
    }
    setCurrentTab('devices');
  };

  return (
    <div
      dir={isArabic ? 'rtl' : 'ltr'}
      className="min-h-screen bg-[#070B14] text-slate-100 flex flex-col font-sans select-none antialiased"
    >
      {/* Top Application Bar */}
      <header className="h-14 border-b border-slate-800 bg-[#0A0F1D]/90 backdrop-blur-md px-4 sm:px-8 flex items-center justify-between gap-4 z-40 shrink-0">
        {/* Brand Zone */}
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-xl bg-gradient-to-tr from-[#0091FF] to-[#00E5FF] flex items-center justify-center text-[#0A0F1D] shadow-md shadow-cyan-500/20 font-black text-sm">
            NV
          </div>
          <div>
            <span className="text-sm font-bold tracking-tight text-white flex items-center gap-2">
              <span>NETVISION</span>
              <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-[#00E5FF]/10 text-[#00E5FF] border border-[#00E5FF]/20 font-normal">
                {t.brandBadge}
              </span>
            </span>
          </div>
        </div>

        {/* Global Controls */}
        <div className="flex items-center gap-2">
          {/* Android Code Explorer Button */}
          <button
            onClick={() => setIsCodeExplorerOpen(true)}
            className="px-3 py-1.5 rounded-xl bg-gradient-to-r from-blue-900/40 to-cyan-900/40 border border-cyan-500/40 hover:border-cyan-400 text-cyan-300 text-xs font-semibold flex items-center gap-1.5 transition-all shadow-sm"
          >
            <Code2 className="w-3.5 h-3.5 text-[#00E5FF]" />
            <span className="hidden sm:inline">{t.exploreCode}</span>
          </button>

          {/* Quick Language Toggle */}
          <button
            onClick={() => setLanguage(language === 'en' ? 'ar' : 'en')}
            className="px-2.5 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 hover:text-white border border-slate-700/80 text-xs font-medium flex items-center gap-1.5 transition-colors"
            title={isArabic ? 'تبديل إلى الإنجليزية' : 'Switch to Arabic'}
          >
            <Globe className="w-3.5 h-3.5 text-cyan-400" />
            <span className="font-sans text-[11px] font-semibold">
              {language === 'ar' ? 'English' : 'العربية'}
            </span>
          </button>

          {/* Quick Theme Toggle */}
          <button
            onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
            className="p-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white border border-slate-700/80 transition-colors"
            title={isArabic ? 'تبديل المظهر الداكن / الفاتح' : 'Toggle Dark / Light Theme'}
          >
            {theme === 'dark' ? (
              <Moon className="w-4 h-4 text-[#00E5FF]" />
            ) : (
              <Sun className="w-4 h-4 text-amber-400" />
            )}
          </button>
        </div>
      </header>

      {/* Main Container with Android Emulator Frame */}
      <main className="flex-1 flex overflow-hidden">
        <AndroidPhoneFrame
          isPhoneView={isPhoneView}
          onToggleView={() => setIsPhoneView(!isPhoneView)}
          isArabic={isArabic}
          theme={theme}
        >
          {/* Mobile Screen App Bar */}
          <div className="px-4 py-3 bg-[#111A2E]/95 backdrop-blur-md border-b border-slate-800/80 flex items-center justify-between shrink-0 z-20">
            <div>
              <h1 className="text-sm font-bold text-white tracking-tight flex items-center gap-2">
                <span>NETVISION</span>
                <span className="text-[10px] text-[#00E5FF] font-mono">
                  {currentTab.toUpperCase()}
                </span>
              </h1>
              <p className="text-[10px] text-slate-400">
                {t.subtitle}
              </p>
            </div>
            <div className="flex items-center gap-1.5">
              <span className="w-2 h-2 rounded-full bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.6)]" />
              <span className="text-[10px] font-mono text-emerald-400">UBNT / MNDP</span>
            </div>
          </div>

          {/* Scrollable Screen Content */}
          <div className="flex-1 overflow-y-auto p-4 overscroll-contain">
            {currentTab === 'dashboard' && (
              <DashboardTab
                devices={devices}
                activities={activities}
                language={language}
                onNavigateToDevices={handleNavigateToDevices}
                onSelectDevice={setSelectedDevice}
              />
            )}

            {currentTab === 'devices' && (
              <DevicesTab
                devices={devices}
                language={language}
                initialVendorFilter={vendorFilter}
                onSelectDevice={setSelectedDevice}
              />
            )}

            {currentTab === 'vpn' && (
              <VpnDashboardTab
                language={language}
                onAddDiscoveredDevice={handleAddDiscoveredDevice}
                onNavigateToDevices={() => setCurrentTab('devices')}
              />
            )}

            {currentTab === 'scanner' && (
              <ScannerTab
                language={language}
                onSelectDevice={(dev) => {
                  handleAddDiscoveredDevice(dev);
                  setSelectedDevice(dev);
                }}
              />
            )}

            {currentTab === 'monitoring' && (
              <MonitoringTab
                devices={devices}
                language={language}
                onSelectDevice={setSelectedDevice}
              />
            )}

            {currentTab === 'settings' && (
              <SettingsTab
                language={language}
                onLanguageChange={setLanguage}
                theme={theme}
                onThemeChange={setTheme}
                onOpenCodeExplorer={() => setIsCodeExplorerOpen(true)}
              />
            )}
          </div>

          {/* Bottom Navigation Bar */}
          <BottomNavBar
            currentTab={currentTab}
            onTabChange={(tab) => {
              setVendorFilter(null);
              setCurrentTab(tab);
            }}
            labels={{
              dashboard: t.navDashboard,
              devices: t.navDevices,
              vpn: t.navVpn,
              scanner: t.navScanner,
              monitoring: t.navMonitoring,
              settings: t.navSettings,
            }}
          />
        </AndroidPhoneFrame>
      </main>

      {/* Device Details Modal */}
      <DeviceDetailModal
        device={selectedDevice}
        onClose={() => setSelectedDevice(null)}
        language={language}
      />

      {/* Native Android Project & Code Explorer */}
      <AndroidCodeExplorer
        isOpen={isCodeExplorerOpen}
        onClose={() => setIsCodeExplorerOpen(false)}
        isArabic={isArabic}
      />
    </div>
  );
}
