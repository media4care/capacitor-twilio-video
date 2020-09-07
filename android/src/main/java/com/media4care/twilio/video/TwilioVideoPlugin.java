package com.media4care.twilio.video;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.getcapacitor.JSObject;
import com.getcapacitor.NativePlugin;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;

@NativePlugin
public class TwilioVideoPlugin extends Plugin {
    private final String TWILIO_PLUGIN_EVENT = "twilio-event";

    @Override
    public void load() {
        super.load();
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(MessageReceiver, new IntentFilter(TwilioVideoActivity.SEND_EVENT));
    }

    @PluginMethod
    public void joinTwilioRoom(PluginCall call) {
        Intent intent = new Intent(getContext(), TwilioVideoActivity.class);
        intent.putExtra("roomName", call.getString("roomName"));
        intent.putExtra("accessToken", call.getString("accessToken"));
        intent.putExtra("options", call.getObject("options").toString());

        getContext().startActivity(intent);
        call.success();
    }

    @PluginMethod
    public void leaveTwilioRoom(PluginCall call) {
        Intent intent = new Intent(TwilioVideoActivity.CLOSE_EVENT);
        LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent);
        call.success();
    }

    private BroadcastReceiver MessageReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            notifyListeners(TWILIO_PLUGIN_EVENT, extractJsonFromIntentExtras(intent));
        }
    };

    private JSObject extractJsonFromIntentExtras(Intent intent) {
        JSObject data = new JSObject();
        Bundle extras = intent.getExtras();

        if (extras != null) {
            for (String key : extras.keySet()) {
                data.put(key, extras.get(key));
            }
        }

        return data;
    }
}
