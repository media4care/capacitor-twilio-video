package com.media4care.twilio.video;

import com.getcapacitor.JSObject;
import com.getcapacitor.NativePlugin;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;

@NativePlugin
public class TwilioVideoPlugin extends Plugin {

    @PluginMethod
    public void joinTwilioRoom(PluginCall call) {
        Intent intent = new Intent(getContext(), TwilioVideoActivity.class);
        intent.putExtra("roomName", call.getString("roomName"));
        intent.putExtra("accessToken", call.getString("accessToken"));
        intent.putExtra("options", call.getObject("options").toString());

        getContext().startActivity(intent);
        call.success();
    }


        JSObject ret = new JSObject();
        ret.put("value", value);
        call.success(ret);
    }
}
