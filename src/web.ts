import { WebPlugin } from '@capacitor/core';
import { TwilioVideoPluginPlugin } from './definitions';

export class TwilioVideoPluginWeb extends WebPlugin implements TwilioVideoPluginPlugin {
  constructor() {
    super({
      name: 'TwilioVideoPlugin',
      platforms: ['web'],
    });
  }

  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}

const TwilioVideoPlugin = new TwilioVideoPluginWeb();

export { TwilioVideoPlugin };

import { registerWebPlugin } from '@capacitor/core';
registerWebPlugin(TwilioVideoPlugin);
