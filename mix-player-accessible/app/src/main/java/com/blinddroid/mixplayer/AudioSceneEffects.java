package com.blinddroid.mixplayer;

import android.media.audiofx.AudioEffect;
import android.media.audiofx.EnvironmentalReverb;
import android.media.audiofx.Equalizer;

/**
 * Session-bound, best-effort effects for the app's MediaPlayer output.
 * These effects are attached to the real MediaPlayer audio session; failures
 * are reported to the caller and never interrupt normal audio playback.
 */
final class AudioSceneEffects {
    static final int SCENE_OFF = 0;
    static final int SCENE_CONCERT = 1;
    static final int SCENE_CAR = 2;

    private EnvironmentalReverb reverb;
    private Equalizer equalizer;

    String apply(int audioSessionId, int scene, int intensity) {
        release();
        if (scene == SCENE_OFF) return "افکت صوتی خاموش شد";
        if (audioSessionId <= 0) return unsupported(scene);

        int strength = clamp(intensity, 0, 100);
        try {
            if (scene == SCENE_CONCERT) {
                reverb = new EnvironmentalReverb(0, audioSessionId);
                if (!reverb.hasControl()) return failAndRelease(scene);
                EnvironmentalReverb.Settings settings = reverb.getProperties();
                settings.roomLevel = (short) (-3_600 + strength * 28);
                settings.roomHFLevel = (short) (-2_200 + strength * 17);
                settings.decayTime = 850 + strength * 19;
                settings.decayHFRatio = (short) (520 + strength * 3);
                settings.reflectionsLevel = (short) (-5_200 + strength * 18);
                settings.reflectionsDelay = 25 + strength / 2;
                settings.reverbLevel = (short) (-4_600 + strength * 34);
                settings.reverbDelay = 45 + strength / 2;
                settings.diffusion = (short) (720 + strength * 2);
                settings.density = (short) (720 + strength * 2);
                reverb.setProperties(settings);
                if (reverb.setEnabled(true) != AudioEffect.SUCCESS) return failAndRelease(scene);
                return "افکت سالن کنسرت فعال شد";
            }

            if (scene == SCENE_CAR) {
                equalizer = new Equalizer(0, audioSessionId);
                if (!equalizer.hasControl()) return failAndRelease(scene);
                short bandCount = equalizer.getNumberOfBands();
                if (bandCount <= 0) return failAndRelease(scene);
                short[] range = equalizer.getBandLevelRange();
                for (short band = 0; band < bandCount; band++) {
                    int centerHz = equalizer.getCenterFreq(band) / 1_000;
                    int desired = carToneMillibels(centerHz, strength);
                    short bounded = (short) clamp(desired, range[0], range[1]);
                    equalizer.setBandLevel(band, bounded);
                }
                if (equalizer.setEnabled(true) != AudioEffect.SUCCESS) return failAndRelease(scene);
                return "افکت صدای خودرو فعال شد";
            }
        } catch (RuntimeException e) {
            return failAndRelease(scene);
        }
        return unsupported(scene);
    }

    void release() {
        if (reverb != null) {
            try { reverb.setEnabled(false); } catch (RuntimeException ignored) { }
            try { reverb.release(); } catch (RuntimeException ignored) { }
            reverb = null;
        }
        if (equalizer != null) {
            try { equalizer.setEnabled(false); } catch (RuntimeException ignored) { }
            try { equalizer.release(); } catch (RuntimeException ignored) { }
            equalizer = null;
        }
    }

    private static int carToneMillibels(int centerHz, int intensity) {
        int target;
        if (centerHz <= 160) target = 650;
        else if (centerHz <= 500) target = 250;
        else if (centerHz <= 1_500) target = -220;
        else if (centerHz <= 4_000) target = 380;
        else if (centerHz <= 8_000) target = -80;
        else target = -360;
        return target * clamp(intensity, 0, 100) / 100;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String failAndRelease(int scene) {
        release();
        return unsupported(scene);
    }

    private static String unsupported(int scene) {
        return "پشتیبانی افکت " + sceneName(scene) + " در نشست صدای این دستگاه در دسترس نیست؛ پخش عادی ادامه دارد";
    }

    private static String sceneName(int scene) {
        return scene == SCENE_CONCERT ? "سالن کنسرت" : "خودرو";
    }
}
