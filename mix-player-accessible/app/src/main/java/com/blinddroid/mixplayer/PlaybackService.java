package com.blinddroid.mixplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

public final class PlaybackService extends Service {
    public static final String ACTION_QUEUE = "com.blinddroid.mixplayer.accessible.QUEUE";
    public static final String ACTION_PLAY_PAUSE = "com.blinddroid.mixplayer.accessible.PLAY_PAUSE";
    public static final String ACTION_PLAY = "com.blinddroid.mixplayer.accessible.PLAY";
    public static final String ACTION_PAUSE = "com.blinddroid.mixplayer.accessible.PAUSE";
    public static final String ACTION_NEXT = "com.blinddroid.mixplayer.accessible.NEXT";
    public static final String ACTION_PREVIOUS = "com.blinddroid.mixplayer.accessible.PREVIOUS";
    public static final String ACTION_FORWARD = "com.blinddroid.mixplayer.accessible.FORWARD";
    public static final String ACTION_BACK = "com.blinddroid.mixplayer.accessible.BACK";
    public static final String ACTION_SEEK = "com.blinddroid.mixplayer.accessible.SEEK";
    public static final String ACTION_SET_SKIP = "com.blinddroid.mixplayer.accessible.SET_SKIP";
    public static final String ACTION_REFRESH = "com.blinddroid.mixplayer.accessible.REFRESH";
    public static final String ACTION_SPEED = "com.blinddroid.mixplayer.accessible.SPEED";
    public static final String ACTION_PITCH = "com.blinddroid.mixplayer.accessible.PITCH";
    public static final String ACTION_REPEAT = "com.blinddroid.mixplayer.accessible.REPEAT";
    public static final String ACTION_SHUFFLE = "com.blinddroid.mixplayer.accessible.SHUFFLE";
    public static final String ACTION_SLEEP = "com.blinddroid.mixplayer.accessible.SLEEP";
    public static final String ACTION_SLEEP_END = "com.blinddroid.mixplayer.accessible.SLEEP_END";
    public static final String ACTION_STOP = "com.blinddroid.mixplayer.accessible.STOP";
    public static final String BROADCAST_STATE = "com.blinddroid.mixplayer.accessible.STATE";

    private static final String CHANNEL_ID = "mix_player_playback";
    private static final int NOTIFICATION_ID = 42;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            publishState(false);
            if (player != null) {
                try {
                    if (Math.abs(player.getCurrentPosition() - lastSavedPosition) >= 10_000) saveResumePoint();
                } catch (Exception ignored) { }
            }
            if (player != null) handler.postDelayed(this, 700);
        }
    };

    private final ArrayList<String> queue = new ArrayList<>();
    private int index = -1;
    private MediaPlayer player;
    private MediaSession mediaSession;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private String title = "آهنگی پخش نمی‌شود";
    private String artist = "";
    private String error = "";
    private float speed = 1f;
    private float pitch = 1f;
    private int repeatMode = 0; // 0: off, 1: all, 2: one
    private int skipMs = 15_000;
    private int pendingResumeMs;
    private int lastSavedPosition;
    private boolean shuffle;
    private long sleepAtElapsed = 0;
    private boolean sleepAtTrackEnd;

    @Override public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        android.content.SharedPreferences saved = getSharedPreferences("mix_player_accessible", MODE_PRIVATE);
        speed = saved.getFloat("speed", 1f);
        pitch = saved.getFloat("pitch", 1f);
        repeatMode = saved.getInt("repeat", 0);
        shuffle = saved.getBoolean("shuffle", false);
        skipMs = saved.getInt("skip_ms", 15_000);
        createNotificationChannel();
        mediaSession = new MediaSession(this, "BlindDroid Mix Player");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { play(); }
            @Override public void onPause() { pause(); }
            @Override public void onSkipToNext() { next(); }
            @Override public void onSkipToPrevious() { previous(); }
            @Override public void onFastForward() { seekBy(skipMs); }
            @Override public void onRewind() { seekBy(-skipMs); }
            @Override public void onSeekTo(long pos) { seekTo((int) pos); }
        });
        mediaSession.setActive(true);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_QUEUE.equals(action)) {
            ArrayList<String> incoming = intent.getStringArrayListExtra("queue");
            if (incoming != null) {
                queue.clear();
                queue.addAll(incoming);
            }
            int requested = intent.getIntExtra("index", 0);
            if (!queue.isEmpty()) {
                startForeground(NOTIFICATION_ID, buildNotification());
                playAt(Math.max(0, Math.min(requested, queue.size() - 1)));
            }
            return START_NOT_STICKY;
        }
        if (ACTION_STOP.equals(action)) {
            stopPlayback(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_PLAY_PAUSE.equals(action)) togglePlayback();
        else if (ACTION_PLAY.equals(action)) play();
        else if (ACTION_PAUSE.equals(action)) pause();
        else if (ACTION_NEXT.equals(action)) next();
        else if (ACTION_PREVIOUS.equals(action)) previous();
        else if (ACTION_FORWARD.equals(action)) seekBy(skipMs);
        else if (ACTION_BACK.equals(action)) seekBy(-skipMs);
        else if (ACTION_SEEK.equals(action)) seekTo(intent.getIntExtra("position", 0));
        else if (ACTION_SET_SKIP.equals(action)) setSkip(intent.getIntExtra("value", 15_000));
        else if (ACTION_REFRESH.equals(action)) publishState(true);
        else if (ACTION_SPEED.equals(action)) setSpeed(intent.getFloatExtra("value", 1f));
        else if (ACTION_PITCH.equals(action)) setPitch(intent.getFloatExtra("value", 1f));
        else if (ACTION_REPEAT.equals(action)) setRepeat(intent.getIntExtra("value", 0));
        else if (ACTION_SHUFFLE.equals(action)) setShuffle(intent.getBooleanExtra("value", false));
        else if (ACTION_SLEEP.equals(action)) setSleep(intent.getIntExtra("minutes", 0));
        else if (ACTION_SLEEP_END.equals(action)) setSleepAtEnd();
        return START_NOT_STICKY;
    }

    private void playAt(int newIndex) {
        if (queue.isEmpty() || newIndex < 0 || newIndex >= queue.size()) return;
        String nextUri = queue.get(newIndex);
        android.content.SharedPreferences saved = getSharedPreferences("mix_player_accessible", MODE_PRIVATE);
        pendingResumeMs = nextUri.equals(saved.getString("last_uri", ""))
                ? Math.max(0, saved.getInt("last_position", 0)) : 0;
        saveResumePoint();
        abandonAudioFocus();
        releasePlayer();
        index = newIndex;
        error = "";
        Uri uri = Uri.parse(queue.get(index));
        AudioTrack track = AudioTrack.fromUri(this, uri);
        title = track.title;
        artist = track.artist;
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            player.setDataSource(this, uri);
            player.setOnPreparedListener(mp -> {
                applyPlaybackParams();
                if (pendingResumeMs > 0 && pendingResumeMs < mp.getDuration()) mp.seekTo(pendingResumeMs);
                pendingResumeMs = 0;
                requestAudioFocus();
                mp.start();
                updateSessionMetadata();
                updateNotification();
                publishState(true);
                handler.removeCallbacks(ticker);
                handler.post(ticker);
            });
            player.setOnCompletionListener(mp -> onTrackCompleted());
            player.setOnErrorListener((mp, what, extra) -> {
                error = "این فایل قابل پخش نیست";
                publishState(true);
                releasePlayer();
                updateNotification();
                return true;
            });
            player.prepareAsync();
            updateSessionMetadata();
            publishState(true);
            updateNotification();
        } catch (Exception e) {
            error = "دسترسی به این فایل از دست رفته است؛ پوشه را دوباره انتخاب کنید";
            releasePlayer();
            publishState(true);
        }
    }

    private void onTrackCompleted() {
        if (sleepAtTrackEnd) {
            setSleep(0);
            pause();
            announceState("پخش در پایان آهنگ متوقف شد");
            return;
        }
        if (repeatMode == 2) {
            playAt(index);
            return;
        }
        if (index < queue.size() - 1 || repeatMode == 1 || shuffle) next();
        else {
            if (player != null) player.seekTo(0);
            pause();
        }
    }

    private void togglePlayback() {
        if (player == null) {
            if (!queue.isEmpty()) playAt(Math.max(index, 0));
            return;
        }
        if (player.isPlaying()) pause(); else play();
    }

    private void play() {
        if (player == null) {
            if (!queue.isEmpty()) playAt(Math.max(index, 0));
            return;
        }
        try {
            requestAudioFocus();
            player.start();
            updateNotification();
            publishState(true);
            handler.removeCallbacks(ticker);
            handler.post(ticker);
        } catch (Exception ignored) { }
    }

    private void pause() {
        if (player != null && player.isPlaying()) {
            try { player.pause(); } catch (Exception ignored) { }
        }
        saveResumePoint();
        publishState(true);
        updateNotification();
    }

    private void next() {
        if (queue.isEmpty()) return;
        int newIndex;
        if (shuffle && queue.size() > 1) {
            do { newIndex = random.nextInt(queue.size()); } while (newIndex == index);
        } else if (index < queue.size() - 1) newIndex = index + 1;
        else if (repeatMode == 1) newIndex = 0;
        else return;
        playAt(newIndex);
    }

    private void previous() {
        if (queue.isEmpty()) return;
        int newIndex;
        if (shuffle && queue.size() > 1) {
            do { newIndex = random.nextInt(queue.size()); } while (newIndex == index);
        } else if (index > 0) newIndex = index - 1;
        else if (repeatMode == 1) newIndex = queue.size() - 1;
        else newIndex = 0;
        playAt(newIndex);
    }

    private void seekBy(int delta) {
        if (player == null) return;
        try { seekTo(player.getCurrentPosition() + delta); } catch (Exception ignored) { }
    }

    private void seekTo(int requested) {
        if (player == null) return;
        try {
            int duration = player.getDuration();
            player.seekTo(Math.max(0, Math.min(requested, duration)));
            publishState(false);
        } catch (Exception ignored) { }
    }

    private void setSpeed(float requested) {
        speed = Math.max(0.5f, Math.min(2f, requested));
        getSharedPreferences("mix_player_accessible", MODE_PRIVATE).edit().putFloat("speed", speed).apply();
        applyPlaybackParams();
        publishState(true);
    }

    private void setPitch(float requested) {
        pitch = Math.max(0.5f, Math.min(2f, requested));
        getSharedPreferences("mix_player_accessible", MODE_PRIVATE).edit().putFloat("pitch", pitch).apply();
        applyPlaybackParams();
        publishState(true);
    }

    private void applyPlaybackParams() {
        if (player == null || Build.VERSION.SDK_INT < 23) return;
        try { player.setPlaybackParams(new PlaybackParams().setSpeed(speed).setPitch(pitch)); }
        catch (Exception ignored) { }
    }

    private void setRepeat(int value) {
        repeatMode = Math.floorMod(value, 3);
        getSharedPreferences("mix_player_accessible", MODE_PRIVATE).edit().putInt("repeat", repeatMode).apply();
        publishState(true);
    }

    private void setShuffle(boolean enabled) {
        shuffle = enabled;
        getSharedPreferences("mix_player_accessible", MODE_PRIVATE).edit().putBoolean("shuffle", shuffle).apply();
        publishState(true);
    }

    private void setSkip(int value) {
        skipMs = Math.max(5_000, Math.min(30_000, value));
        getSharedPreferences("mix_player_accessible", MODE_PRIVATE).edit().putInt("skip_ms", skipMs).apply();
        publishState(true);
    }

    private void setSleep(int minutes) {
        sleepAtElapsed = minutes <= 0 ? 0 : SystemClock.elapsedRealtime() + minutes * 60_000L;
        sleepAtTrackEnd = false;
        handler.removeCallbacks(sleepRunnable);
        if (minutes > 0) handler.postDelayed(sleepRunnable, minutes * 60_000L);
        publishState(true);
    }

    private void setSleepAtEnd() {
        sleepAtElapsed = 0;
        sleepAtTrackEnd = true;
        handler.removeCallbacks(sleepRunnable);
        publishState(true);
    }

    private final Runnable sleepRunnable = () -> {
        sleepAtElapsed = 0;
        pause();
        announceState("تایمر خواب: پخش متوقف شد");
    };

    private void stopPlayback(boolean removeNotification) {
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(sleepRunnable);
        saveResumePoint();
        releasePlayer();
        abandonAudioFocus();
        if (mediaSession != null) {
            mediaSession.setPlaybackState(new PlaybackState.Builder().setState(PlaybackState.STATE_STOPPED, 0, 0).build());
            mediaSession.setActive(false);
        }
        if (removeNotification) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(NOTIFICATION_ID);
        }
        publishState(true);
    }

    private void saveResumePoint() {
        if (player == null || index < 0 || index >= queue.size()) return;
        try {
            lastSavedPosition = player.getCurrentPosition();
            getSharedPreferences("mix_player_accessible", MODE_PRIVATE).edit()
                    .putString("last_uri", queue.get(index))
                    .putInt("last_position", lastSavedPosition).apply();
        } catch (Exception ignored) { }
    }

    private void releasePlayer() {
        handler.removeCallbacks(ticker);
        if (player != null) {
            try { player.reset(); player.release(); } catch (Exception ignored) { }
            player = null;
        }
    }

    private void requestAudioFocus() {
        if (audioManager == null) return;
        AudioManager.OnAudioFocusChangeListener listener = focusChange -> {
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pause();
            else if (focusChange == AudioManager.AUDIOFOCUS_GAIN && player != null) play();
        };
        if (Build.VERSION.SDK_INT >= 26) {
            if (focusRequest == null) focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setOnAudioFocusChangeListener(listener).setWillPauseWhenDucked(true).build();
            audioManager.requestAudioFocus(focusRequest);
        } else {
            audioManager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
    }

    private void abandonAudioFocus() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= 26 && focusRequest != null) audioManager.abandonAudioFocusRequest(focusRequest);
    }

    private void updateSessionMetadata() {
        if (mediaSession == null) return;
        long duration = 0;
        try { if (player != null) duration = player.getDuration(); } catch (Exception ignored) { }
        mediaSession.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, duration).build());
        publishState(false);
    }

    private void publishState(boolean announceChange) {
        boolean playing = player != null && safeIsPlaying();
        long position = 0, duration = 0;
        if (player != null) {
            try { position = player.getCurrentPosition(); duration = player.getDuration(); } catch (Exception ignored) { }
        }
        if (mediaSession != null) {
            long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                    | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                    | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_FAST_FORWARD | PlaybackState.ACTION_REWIND;
            mediaSession.setPlaybackState(new PlaybackState.Builder().setActions(actions)
                    .setState(playing ? PlaybackState.STATE_PLAYING : (player == null ? PlaybackState.STATE_STOPPED : PlaybackState.STATE_PAUSED),
                            position, playing ? speed : 0f).build());
        }
        Intent state = new Intent(BROADCAST_STATE).setPackage(getPackageName());
        state.putExtra("playing", playing).putExtra("position", (int) position).putExtra("duration", (int) duration)
                .putExtra("index", index).putExtra("count", queue.size()).putExtra("title", title)
                .putExtra("currentUri", index >= 0 && index < queue.size() ? queue.get(index) : "")
                .putExtra("artist", artist).putExtra("error", error).putExtra("speed", speed)
                .putExtra("pitch", pitch).putExtra("repeat", repeatMode).putExtra("shuffle", shuffle)
                .putExtra("sleepRemaining", sleepAtElapsed > 0 ? Math.max(0, sleepAtElapsed - SystemClock.elapsedRealtime()) : 0)
                .putExtra("sleepAtEnd", sleepAtTrackEnd).putExtra("announce", announceChange);
        sendBroadcast(state);
    }

    private boolean safeIsPlaying() {
        try { return player != null && player.isPlaying(); } catch (Exception ignored) { return false; }
    }

    private void announceState(String message) {
        Intent state = new Intent(BROADCAST_STATE).setPackage(getPackageName()).putExtra("announcement", message);
        sendBroadcast(state);
    }

    private void updateNotification() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification());
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        boolean playing = safeIsPlaying();
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_player_notification)
                .setContentTitle(title).setContentText(artist.trim().isEmpty() ? "Mix Player" : artist)
                .setContentIntent(content).setOngoing(playing).setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(action(android.R.drawable.ic_media_previous, "آهنگ قبلی", ACTION_PREVIOUS, 2))
                .addAction(action(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        playing ? "مکث" : "پخش", playing ? ACTION_PAUSE : ACTION_PLAY, 3))
                .addAction(action(android.R.drawable.ic_media_next, "آهنگ بعدی", ACTION_NEXT, 4));
        if (Build.VERSION.SDK_INT >= 21 && mediaSession != null) {
            builder.setStyle(new Notification.MediaStyle().setMediaSession(mediaSession.getSessionToken()).setShowActionsInCompactView(0, 1, 2));
        }
        return builder.build();
    }

    private Notification.Action action(int icon, String label, String action, int requestCode) {
        Intent intent = new Intent(this, PlaybackService.class).setAction(action);
        PendingIntent pending = PendingIntent.getService(this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Action.Builder(icon, label, pending).build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "کنترل پخش موسیقی", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("پخش پس‌زمینه و دکمه‌های کنترل Mix Player");
            channel.setShowBadge(false);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        stopPlayback(true);
        if (mediaSession != null) { mediaSession.release(); mediaSession = null; }
        super.onDestroy();
    }
}
