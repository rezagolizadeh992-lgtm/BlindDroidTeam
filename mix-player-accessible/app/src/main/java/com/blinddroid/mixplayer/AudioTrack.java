package com.blinddroid.mixplayer;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import java.util.Locale;

final class AudioTrack {
    final Uri uri;
    final String title;
    final String artist;
    final long durationMs;
    final long modifiedTimeMs;

    AudioTrack(Uri uri, String title, String artist, long durationMs) {
        this(uri, title, artist, durationMs, 0);
    }

    AudioTrack(Uri uri, String title, String artist, long durationMs, long modifiedTimeMs) {
        this.uri = uri;
        this.title = title == null || title.trim().isEmpty() ? "بدون نام" : title;
        this.artist = artist == null || artist.trim().isEmpty() ? "خواننده ناشناس" : artist;
        this.durationMs = Math.max(0, durationMs);
        this.modifiedTimeMs = Math.max(0, modifiedTimeMs);
    }

    String spokenLabel() {
        return title + "، " + artist + (durationMs > 0 ? "، " + formatTime(durationMs) : "") + "، برای پخش دو بار ضربه بزنید";
    }

    static AudioTrack fromUri(Context context, Uri uri) {
        String filename = uri.getLastPathSegment();
        if (filename == null || filename.trim().isEmpty()) filename = "آهنگ انتخاب‌شده";
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf(':'));
        if (slash >= 0 && slash + 1 < filename.length()) filename = filename.substring(slash + 1);
        int dot = filename.lastIndexOf('.');
        if (dot > 0) filename = filename.substring(0, dot);
        String title = filename;
        String artist = "خواننده ناشناس";
        long duration = 0;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            String tagTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String tagArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String tagDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (tagTitle != null && !tagTitle.trim().isEmpty()) title = tagTitle;
            if (tagArtist != null && !tagArtist.trim().isEmpty()) artist = tagArtist;
            if (tagDuration != null) duration = Long.parseLong(tagDuration);
        } catch (Exception ignored) {
            // Filename fallback keeps malformed or unsupported tags from blocking playback.
        } finally {
            try { retriever.release(); } catch (Exception ignored) { }
        }
        return new AudioTrack(uri, title, artist, duration);
    }

    static String formatTime(long millis) {
        long seconds = Math.max(0, millis / 1000);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remaining = seconds % 60;
        if (hours > 0) return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remaining);
        return String.format(Locale.ROOT, "%d:%02d", minutes, remaining);
    }
}
