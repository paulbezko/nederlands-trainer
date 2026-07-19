package com.paulbezko.dutchtrainer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class PlaybackService extends Service {

    public interface StateCallback { void onState(JSONObject s); }
    public static StateCallback CALLBACK;

    public static final String ACTION_START = "nl.START";
    public static final String ACTION_PAUSE = "nl.PAUSE";
    public static final String ACTION_RESUME = "nl.RESUME";
    public static final String ACTION_TOGGLE = "nl.TOGGLE";
    public static final String ACTION_NEXT = "nl.NEXT";
    public static final String ACTION_PREV = "nl.PREV";
    public static final String ACTION_STOP = "nl.STOP";

    private static final String CHANNEL_ID = "nl_playback";
    private static final int NOTIF_ID = 4200;

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private JSONObject pendingConfig;

    private MediaSession session;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // playback state
    private final List<String[]> queue = new ArrayList<>(); // [nl, en]
    private final List<String> plan = new ArrayList<>();     // "nl"/"en"
    private int pos = 0;
    private int stepIdx = 0;
    private boolean running = false;
    private boolean paused = false;
    private boolean loop = true;
    private long repGapMs = 500;
    private long itemGapMs = 1500;
    private float rate = 0.9f;
    private String nlLang = "nl-NL";
    private String enLang = "en-US";

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        createChannel();
        setupSession();
        tts = new TextToSpeech(this, status -> {
            ttsReady = (status == TextToSpeech.SUCCESS);
            if (ttsReady) {
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {}
                    @Override public void onError(String id) { handler.post(() -> onStepDone()); }
                    @Override public void onDone(String id) { handler.post(() -> onStepDone()); }
                });
                if (pendingConfig != null) {
                    JSONObject c = pendingConfig; pendingConfig = null; startPlayback(c);
                }
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (action == null) return START_NOT_STICKY;
        switch (action) {
            case ACTION_START:
                try {
                    JSONObject cfg = new JSONObject(intent.getStringExtra("config"));
                    if (ttsReady) startPlayback(cfg); else pendingConfig = cfg;
                } catch (Exception e) {}
                break;
            case ACTION_PAUSE: doPause(); break;
            case ACTION_RESUME: doResume(); break;
            case ACTION_TOGGLE: if (paused) doResume(); else doPause(); break;
            case ACTION_NEXT: doNext(); break;
            case ACTION_PREV: doPrev(); break;
            case ACTION_STOP: doStop(); break;
        }
        return START_NOT_STICKY;
    }

    private void startPlayback(JSONObject c) {
        queue.clear(); plan.clear();
        try {
            JSONArray items = c.getJSONArray("items");
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.getJSONObject(i);
                queue.add(new String[]{ it.optString("nl", ""), it.optString("en", "") });
            }
            JSONArray p = c.getJSONArray("plan");
            for (int i = 0; i < p.length(); i++) plan.add(p.getString(i));
            repGapMs = Math.round(c.optDouble("repGap", 0.5) * 1000);
            itemGapMs = Math.round(c.optDouble("itemGap", 1.5) * 1000);
            rate = (float) c.optDouble("rate", 0.9);
            nlLang = c.optString("nlLang", "nl-NL");
            enLang = c.optString("enLang", "en-US");
            loop = c.optBoolean("loop", true);
            pos = c.optInt("startPos", 0);
        } catch (Exception e) { return; }
        if (queue.isEmpty() || plan.isEmpty()) return;
        requestFocus();
        running = true; paused = false; stepIdx = 0;
        startForegroundNotif();
        tts.setSpeechRate(rate);
        playWord();
    }

    private void playWord() {
        if (!running || paused) return;
        if (pos >= queue.size()) {
            if (loop) pos = 0; else { finish(); return; }
        }
        stepIdx = 0;
        speakStep();
    }

    private void speakStep() {
        if (!running || paused) return;
        if (stepIdx >= plan.size()) { pos++; handler.postDelayed(this::playWord, itemGapMs); return; }
        String lang = plan.get(stepIdx);
        String[] item = queue.get(pos);
        String text = lang.equals("nl") ? item[0] : item[1];
        Locale loc = toLocale(lang.equals("nl") ? nlLang : enLang);
        try { tts.setLanguage(loc); } catch (Exception e) {}
        emitState(lang);
        updateSession(item, lang);
        HashMap<String, String> params = new HashMap<>();
        params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "u" + pos + "_" + stepIdx);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
    }

    private void onStepDone() {
        if (!running || paused) return;
        if (stepIdx < plan.size() - 1) {
            stepIdx++;
            handler.postDelayed(this::speakStep, repGapMs);
        } else {
            pos++;
            handler.postDelayed(this::playWord, itemGapMs);
        }
    }

    private void doPause() {
        if (!running || paused) return;
        paused = true;
        handler.removeCallbacksAndMessages(null);
        if (tts != null) tts.stop();
        updatePlaybackState();
        emitState(null);
        updateNotif();
    }

    private void doResume() {
        if (!running || !paused) return;
        paused = false;
        requestFocus();
        updatePlaybackState();
        playWord();
        updateNotif();
    }

    private void doNext() {
        if (!running) return;
        handler.removeCallbacksAndMessages(null);
        if (tts != null) tts.stop();
        paused = false; pos++;
        playWord();
    }

    private void doPrev() {
        if (!running) return;
        handler.removeCallbacksAndMessages(null);
        if (tts != null) tts.stop();
        paused = false; pos = Math.max(0, pos - 1);
        playWord();
    }

    private void finish() {
        running = false; paused = false;
        handler.removeCallbacksAndMessages(null);
        updatePlaybackState();
        emitState(null);
        stopForegroundCompat();
        abandonFocus();
        stopSelf();
    }

    private void doStop() { finish(); }

    // ---- audio focus ----
    private void requestFocus() {
        if (audioManager == null) return;
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(change -> {
                        if (change == AudioManager.AUDIOFOCUS_LOSS) doPause();
                    }).build();
            audioManager.requestAudioFocus(focusRequest);
        } else {
            audioManager.requestAudioFocus(change -> {
                if (change == AudioManager.AUDIOFOCUS_LOSS) doPause();
            }, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
    }

    private void abandonFocus() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest);
        }
    }

    // ---- media session ----
    private void setupSession() {
        session = new MediaSession(this, "NederlandsTrainer");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { if (paused) doResume(); }
            @Override public void onPause() { doPause(); }
            @Override public void onSkipToNext() { doNext(); }
            @Override public void onSkipToPrevious() { doPrev(); }
            @Override public void onStop() { doStop(); }
        });
        session.setActive(true);
        updatePlaybackState();
    }

    private void updatePlaybackState() {
        if (session == null) return;
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT
                | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_STOP;
        int state = !running ? PlaybackState.STATE_STOPPED
                : (paused ? PlaybackState.STATE_PAUSED : PlaybackState.STATE_PLAYING);
        PlaybackState ps = new PlaybackState.Builder()
                .setActions(actions)
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f).build();
        session.setPlaybackState(ps);
    }

    private void updateSession(String[] item, String lang) {
        if (session == null) return;
        android.media.MediaMetadata md = new android.media.MediaMetadata.Builder()
                .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, item[0])
                .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, item[1])
                .putString(android.media.MediaMetadata.METADATA_KEY_ALBUM, "Nederlands").build();
        session.setMetadata(md);
        updatePlaybackState();
    }

    // ---- notification ----
    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private PendingIntent cmd(String action) {
        Intent i = new Intent(this, PlaybackService.class).setAction(action);
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
        return PendingIntent.getService(this, action.hashCode(), i, flags);
    }

    private Notification buildNotif() {
        String title = "Nederlands";
        String text = "Audio trainer";
        if (pos < queue.size()) { title = queue.get(pos)[0]; text = queue.get(pos)[1]; }
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        b.setContentTitle(title).setContentText(text)
                .setSmallIcon(getApplicationInfo().icon)
                .setOngoing(running && !paused)
                .setVisibility(Notification.VISIBILITY_PUBLIC);
        b.addAction(new Notification.Action.Builder(null, "Prev", cmd(ACTION_PREV)).build());
        b.addAction(new Notification.Action.Builder(null, paused ? "Play" : "Pause",
                cmd(ACTION_TOGGLE)).build());
        b.addAction(new Notification.Action.Builder(null, "Next", cmd(ACTION_NEXT)).build());
        Notification.MediaStyle style = new Notification.MediaStyle().setMediaSession(session.getSessionToken());
        style.setShowActionsInCompactView(0, 1, 2);
        b.setStyle(style);
        return b.build();
    }

    private void startForegroundNotif() {
        Notification n = buildNotif();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private void updateNotif() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null && running) nm.notify(NOTIF_ID, buildNotif());
    }

    private void stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(Service.STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
    }

    // ---- helpers ----
    private void emitState(String lang) {
        try {
            JSONObject s = new JSONObject();
            s.put("running", running);
            s.put("paused", paused);
            s.put("pos", pos);
            s.put("total", queue.size());
            if (pos < queue.size()) { s.put("nl", queue.get(pos)[0]); s.put("en", queue.get(pos)[1]); }
            if (lang != null) s.put("lang", lang);
            if (CALLBACK != null) CALLBACK.onState(s);
        } catch (Exception e) {}
    }

    private Locale toLocale(String tag) {
        String[] parts = tag.split("-");
        if (parts.length >= 2) return new Locale(parts[0], parts[1]);
        return new Locale(tag);
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (session != null) { session.setActive(false); session.release(); }
        abandonFocus();
        super.onDestroy();
    }
}
