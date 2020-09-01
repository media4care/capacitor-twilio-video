declare module '@capacitor/core' {
  interface PluginRegistry {
    TwilioVideoPlugin: TwilioVideoPluginPlugin;
  }
}

export interface TwilioVideoPluginPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
