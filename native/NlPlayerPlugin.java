package com.paulbezko.dutchtrainer;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONObject;

@CapacitorPlugin(name = "NlPlayer")
public class NlPlayerPlugin extends Plugin {

    @Override
    public void load() {
        PlaybackService.CALLBACK = s -> {
            try {
                JSObject jo = new JSObject();
                java.util.Iterator<String> keys = s.keys();
                while (keys.hasNext()) { String k = keys.next(); jo.put(k, s.get(k)); }
                notifyListeners("state", jo);
            } catch (Exception e) {}
        };
    }

    @PluginMethod
    public void start(PluginCall call) {
        maybeAskNotifications();
        Intent i = new Intent(getContext(), PlaybackService.class).setAction(PlaybackService.ACTION_START);
        i.putExtra("config", call.getData().toString());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getContext().startForegroundService(i);
        else getContext().startService(i);
        call.resolve();
    }

    private void command(PluginCall call, String action) {
        Intent i = new Intent(getContext(), PlaybackService.class).setAction(action);
        try { getContext().startService(i); } catch (Exception e) {}
        call.resolve();
    }

    @PluginMethod public void pause(PluginCall call) { command(call, PlaybackService.ACTION_PAUSE); }
    @PluginMethod public void resume(PluginCall call) { command(call, PlaybackService.ACTION_RESUME); }
    @PluginMethod public void next(PluginCall call) { command(call, PlaybackService.ACTION_NEXT); }
    @PluginMethod public void prev(PluginCall call) { command(call, PlaybackService.ACTION_PREV); }
    @PluginMethod public void stop(PluginCall call) { command(call, PlaybackService.ACTION_STOP); }

    private void maybeAskNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && getActivity() != null) {
            String perm = "android.permission.POST_NOTIFICATIONS";
            if (getActivity().checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                try { getActivity().requestPermissions(new String[]{ perm }, 9911); } catch (Exception e) {}
            }
        }
    }
}
