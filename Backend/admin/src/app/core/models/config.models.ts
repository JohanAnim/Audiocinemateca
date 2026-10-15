export interface VersionConfig {
  version: string;
  versionCode: number;
  name: string;
  changelog: string;
  downloadUrl: string;
  minRequiredVersion: string;
  forceUpdate: boolean;
  updatedAt: string;
  downloadCount: number;
}

export interface BannerConfig {
  enabled: boolean;
  id: string;
  title: string;
  message: string;
  type: 'info' | 'warning' | 'alert' | 'update';
  dismissible: boolean;
  actionUrl: string | null;
  actionText: string | null;
}

export interface MaintenanceConfig {
  enabled: boolean;
  message: string;
  estimatedEndTime: string | null;
}

export interface ChatConfig {
  enabled: boolean;
  slowModeSeconds: number;
  maxMessageLength: number;
  welcomeMessage: string;
}

export interface DonationConfig {
  paypal: string;
  paypalMe: string;
  supportEmail: string;
}

export interface AppConfigItem {
  key: string;
  value: any;
  description: string | null;
  updatedAt: string;
}
