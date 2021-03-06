#import <Foundation/Foundation.h>
#import <Capacitor/Capacitor.h>

// Define the plugin using the CAP_PLUGIN Macro, and
// each method the plugin supports using the CAP_PLUGIN_METHOD macro.
CAP_PLUGIN(TwilioVideoPlugin, "TwilioVideoPlugin",
  CAP_PLUGIN_METHOD(joinTwilioRoom, CAPPluginReturnPromise);
  CAP_PLUGIN_METHOD(leaveTwilioRoom, CAPPluginReturnPromise);
)
