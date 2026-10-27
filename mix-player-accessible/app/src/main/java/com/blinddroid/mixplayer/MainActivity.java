package com.blinddroid.mixplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.ContextCompat;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQUEST_FOLDER = 101;
    private static final int REQUEST_FILES = 102;
    private static final int REQUEST_NOTIFICATION = 103;
    private static final int INK = 0xFF142B35;
    private static final int MUTED = 0xFF475C66;
    private static final int PAPER = 0xFFF4F7F8;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLUE = 0xFF005B9A;
    private static final int BLUE_LIGHT = 0xFFDCEEFF;
    private static final int GOLD = 0xFFFFD166;
    private static final int LINE = 0xFFCFD9DE;

    private final ArrayList<AudioTrack> allTracks = new ArrayList<>();
    private final ArrayList<AudioTrack> visibleTracks = new ArrayList<>();
    private final Set<String> favorites = new HashSet<>();
    private final ExecutorService scanner = Executors.newSingleThreadExecutor();
    private final android.os.Handler mainHandler = new android.os.Handler();
    private SharedPreferences prefs;
    private Uri selectedFolder;
    private boolean favoritesOnly;
    private boolean announceTracks = true;
    private boolean userSeeking;
    private long skipMs = 15_000;
    private String currentUri = "";
    private int sortMode;
    private String lastAnnouncedTitle = "";
    private int audioScene = AudioSceneEffects.SCENE_OFF;
    private int audioSceneIntensity = 75;
    private float displayedSpeed = 1f;
    private float displayedPitch = 1f;
    private int displayedRepeat;
    private boolean displayedShuffle;
    private int displayedIndex = -1;
    private boolean receiverRegistered;

    private LinearLayout root;
    private ListView songList;
    private EditText search;
    private TextView status;
    private TextView count;
    private TextView nowTitle;
    private TextView nowArtist;
    private TextView timeLabel;
    private TextView speedLabel;
    private TextView pitchLabel;
    private TextView repeatButton;
    private TextView shuffleButton;
    private TextView sleepButton;
    private Button audioEffectsButton;
    private TextView playPauseButton;
    private TextView favoriteNowButton;
    private Button resumeButton;
    private Button backControl;
    private Button forwardControl;
    private SeekBar seekBar;
    private SongAdapter adapter;

    private final BroadcastReceiver playbackReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!PlaybackService.BROADCAST_STATE.equals(intent.getAction())) return;
            String announcement = intent.getStringExtra("announcement");
            if (announcement != null) announce(announcement);
            String title = intent.getStringExtra("title");
            String artist = intent.getStringExtra("artist");
            currentUri = intent.getStringExtra("currentUri") == null ? "" : intent.getStringExtra("currentUri");
            if (title != null) nowTitle.setText(title);
            if (artist != null) nowArtist.setText(artist);
            boolean playing = intent.getBooleanExtra("playing", false);
            playPauseButton.setText(playing ? "مکث" : "پخش");
            playPauseButton.setContentDescription(playing ? "مکث پخش موسیقی" : "ادامهٔ پخش موسیقی");
            int duration = intent.getIntExtra("duration", 0);
            int position = intent.getIntExtra("position", 0);
            if (!userSeeking) {
                seekBar.setMax(Math.max(1, duration));
                seekBar.setProgress(Math.max(0, Math.min(position, Math.max(1, duration))));
            }
            timeLabel.setText(AudioTrack.formatTime(position) + " از " + AudioTrack.formatTime(duration));
            displayedSpeed = intent.getFloatExtra("speed", displayedSpeed);
            displayedPitch = intent.getFloatExtra("pitch", displayedPitch);
            displayedRepeat = intent.getIntExtra("repeat", displayedRepeat);
            displayedShuffle = intent.getBooleanExtra("shuffle", displayedShuffle);
            long sleepRemaining = intent.getLongExtra("sleepRemaining", 0);
            boolean sleepAtEnd = intent.getBooleanExtra("sleepAtEnd", false);
            String sleepDescription = sleepAtEnd ? "تایمر خواب: توقف در پایان آهنگ فعلی"
                    : (sleepRemaining > 0 ? "تایمر خواب: " + Math.max(1, (sleepRemaining + 59_999) / 60_000) + " دقیقه مانده" : "تایمر خواب خاموش");
            if (sleepButton != null && !sleepDescription.equals(sleepButton.getContentDescription())) {
                sleepButton.setContentDescription(sleepDescription + "؛ برای تغییر دو بار ضربه بزنید");
                sleepButton.setText(sleepRemaining > 0 ? "خواب: روشن" : (sleepAtEnd ? "خواب: پایان آهنگ" : "تایمر خواب"));
            }
            int index = intent.getIntExtra("index", -1);
            if (index >= 0 && index != displayedIndex) {
                displayedIndex = index;
                if (intent.getBooleanExtra("announce", false) && announceTracks) {
                    String spoken = title == null ? "آهنگ جدید" : "در حال پخش: " + title + (artist == null || artist.trim().isEmpty() ? "" : "، " + artist);
                    if (!spoken.equals(lastAnnouncedTitle)) {
                        announce(spoken);
                        lastAnnouncedTitle = spoken;
                    }
                }
            }
            String error = intent.getStringExtra("error");
            if (error != null && !error.trim().isEmpty()) status.setText(error);
            refreshOptionLabels();
            refreshNowFavorite();
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(PAPER);
        int systemUi = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) systemUi |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(systemUi);
        prefs = getSharedPreferences("mix_player_accessible", MODE_PRIVATE);
        skipMs = prefs.getInt("skip_ms", 15_000);
        sortMode = prefs.getInt("sort_mode", 0);
        announceTracks = prefs.getBoolean("announce_tracks", true);
        audioScene = prefs.getInt("audio_scene", AudioSceneEffects.SCENE_OFF);
        if (audioScene < AudioSceneEffects.SCENE_OFF || audioScene > AudioSceneEffects.SCENE_CAR) {
            audioScene = AudioSceneEffects.SCENE_OFF;
        }
        audioSceneIntensity = Math.max(0, Math.min(100, prefs.getInt("audio_scene_intensity", 75)));
        loadFavorites();
        String saved = prefs.getString("folder_uri", "");
        if (!saved.trim().isEmpty()) selectedFolder = Uri.parse(saved);
        buildInterface();
        updateSkipButtons();
        updateList();
        if (selectedFolder != null) scanFolder(selectedFolder, true);
        handleIncomingIntent(getIntent());
    }

    private void buildInterface() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(PAPER);
        root.setPadding(dp(14), dp(8), dp(14), dp(6));
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(dp(14) + bars.left, dp(8) + bars.top, dp(14) + bars.right, dp(6) + bars.bottom);
                return insets;
            });
            root.requestApplyInsets();
        }

        LinearLayout header = row();
        TextView brand = text("Mix Player\nپخش موسیقی دسترس‌پذیر", 21, INK, true);
        if (Build.VERSION.SDK_INT >= 28) brand.setAccessibilityHeading(true);
        brand.setContentDescription("Mix Player، پخش‌کنندهٔ موسیقی دسترس‌پذیر");
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(58), 1));
        Button pickFolder = button("انتخاب پوشه", "انتخاب پوشهٔ موسیقی از حافظهٔ دستگاه", BLUE, WHITE);
        pickFolder.setOnClickListener(v -> chooseFolder());
        header.addView(pickFolder, new LinearLayout.LayoutParams(0, dp(56), 1));
        root.addView(header, margin(0, 0, 0, 4));

        LinearLayout importRow = row();
        Button pickFiles = button("انتخاب آهنگ", "انتخاب یک یا چند فایل صوتی", WHITE, BLUE);
        pickFiles.setOnClickListener(v -> chooseFiles());
        importRow.addView(pickFiles, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button sort = button("مرتب‌سازی", "انتخاب شیوهٔ مرتب‌کردن فهرست آهنگ‌ها", WHITE, INK);
        sort.setOnClickListener(v -> showSortDialog());
        importRow.addView(sort, new LinearLayout.LayoutParams(0, dp(52), 1));
        root.addView(importRow, margin(0, 0, 0, 5));

        LinearLayout filterRow = row();
        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(16);
        search.setTextColor(INK);
        search.setHintTextColor(MUTED);
        search.setHint("جست‌وجوی نام آهنگ یا خواننده");
        search.setContentDescription("جست‌وجو در آهنگ‌ها بر اساس نام آهنگ یا خواننده");
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        search.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        search.setPadding(dp(12), 0, dp(12), 0);
        search.setBackground(round(WHITE, 14));
        filterRow.addView(search, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button favoritesFilter = button("علاقه‌مندی‌ها", "نمایش یا پنهان‌کردن آهنگ‌های موردعلاقه", BLUE_LIGHT, BLUE);
        favoritesFilter.setOnClickListener(v -> {
            favoritesOnly = !favoritesOnly;
            favoritesFilter.setText(favoritesOnly ? "همهٔ آهنگ‌ها" : "علاقه‌مندی‌ها");
            favoritesFilter.setContentDescription(favoritesOnly ? "نمایش همهٔ آهنگ‌ها" : "نمایش آهنگ‌های موردعلاقه");
            updateList();
            announce(favoritesOnly ? "فهرست علاقه‌مندی‌ها" : "همهٔ آهنگ‌ها");
        });
        filterRow.addView(favoritesFilter, margin(7, 0, 0, 0));
        root.addView(filterRow, margin(0, 0, 0, 4));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateList(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        search.setOnEditorActionListener((v, actionId, event) -> {
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(), 0);
            search.clearFocus();
            return true;
        });

        LinearLayout summary = row();
        count = text("۰ آهنگ", 14, MUTED, true);
        summary.addView(count, new LinearLayout.LayoutParams(0, dp(36), 1));
        status = text("برای شروع، پوشهٔ موسیقی یا فایل آهنگ را انتخاب کنید.", 13, MUTED, false);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        summary.addView(status, new LinearLayout.LayoutParams(0, dp(36), 2));
        root.addView(summary);

        resumeButton = button("ادامه از آهنگ قبلی", "ادامه از آهنگ و موقعیت ذخیره‌شدهٔ قبلی", GOLD, INK);
        resumeButton.setVisibility(View.GONE);
        root.addView(resumeButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        songList = new ListView(this);
        songList.setDividerHeight(dp(1));
        songList.setDivider(new ColorDrawable(LINE));
        songList.setBackground(round(WHITE, 16));
        if (Build.VERSION.SDK_INT >= 28) songList.setAccessibilityHeading(true);
        songList.setContentDescription("فهرست آهنگ‌ها؛ برای پخش روی نام آهنگ دوبار ضربه بزنید");
        songList.setItemsCanFocus(true);
        adapter = new SongAdapter(this);
        songList.setAdapter(adapter);
        songList.setOnItemClickListener((parent, view, position, id) -> {
            AudioTrack track = visibleTracks.get(position);
            playTrack(track);
        });
        root.addView(songList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        addPlayerPanel();
    }

    private void addPlayerPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        panel.setPadding(dp(13), dp(9), dp(13), dp(7));
        panel.setBackground(round(WHITE, 18));
        panel.setElevation(dp(3));
        if (Build.VERSION.SDK_INT >= 28) panel.setAccessibilityPaneTitle("کنترل‌های پخش موسیقی");

        nowTitle = text("آهنگی پخش نمی‌شود", 17, INK, true);
        nowTitle.setSingleLine(true);
        nowTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (Build.VERSION.SDK_INT >= 28) nowTitle.setAccessibilityHeading(true);
        panel.addView(nowTitle);
        nowArtist = text("برای پخش، از فهرست آهنگ انتخاب کنید", 13, MUTED, false);
        panel.addView(nowArtist);

        seekBar = new SeekBar(this);
        seekBar.setMax(1);
        seekBar.setContentDescription("موقعیت پخش؛ برای جابه‌جایی در آهنگ از کلیدهای جهت‌دار استفاده کنید");
        seekBar.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(SeekBar.class.getName());
                info.setContentDescription("موقعیت پخش، " + timeLabel.getText());
                info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                        AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 0, Math.max(1, seekBar.getMax()), seekBar.getProgress()));
            }
        });
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) timeLabel.setText(AudioTrack.formatTime(progress) + " از " + AudioTrack.formatTime(bar.getMax()));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                userSeeking = false;
                sendService(PlaybackService.ACTION_SEEK).putExtra("position", bar.getProgress());
                startService(lastServiceIntent);
                announce("رفتن به " + AudioTrack.formatTime(bar.getProgress()));
            }
        });
        panel.addView(seekBar);
        timeLabel = text("۰:۰۰ از ۰:۰۰", 12, MUTED, false);
        timeLabel.setContentDescription("زمان سپری‌شده و مدت کامل آهنگ");
        panel.addView(timeLabel);

        LinearLayout controls = row();
        Button previous = button("قبلی", "پخش آهنگ قبلی", WHITE, INK);
        previous.setOnClickListener(v -> sendAction(PlaybackService.ACTION_PREVIOUS));
        controls.addView(previous, equalButton());
        Button back = button("عقب ۱۵", "رفتن ۱۵ ثانیه به عقب", WHITE, INK);
        backControl = back;
        back.setOnClickListener(v -> sendAction(PlaybackService.ACTION_BACK));
        controls.addView(back, equalButton());
        playPauseButton = button("پخش", "ادامهٔ پخش موسیقی", GOLD, INK);
        playPauseButton.setOnClickListener(v -> sendAction(PlaybackService.ACTION_PLAY_PAUSE));
        controls.addView(playPauseButton, equalButton());
        Button forward = button("جلو ۱۵", "رفتن ۱۵ ثانیه به جلو", WHITE, INK);
        forwardControl = forward;
        forward.setOnClickListener(v -> sendAction(PlaybackService.ACTION_FORWARD));
        controls.addView(forward, equalButton());
        Button next = button("بعدی", "پخش آهنگ بعدی", WHITE, INK);
        next.setOnClickListener(v -> sendAction(PlaybackService.ACTION_NEXT));
        controls.addView(next, equalButton());
        panel.addView(controls, margin(0, dp(3), 0, 0));

        LinearLayout toggles = row();
        shuffleButton = button("تصادفی: خاموش", "فعال‌کردن پخش تصادفی", BLUE_LIGHT, BLUE);
        shuffleButton.setOnClickListener(v -> {
            Intent command = sendService(PlaybackService.ACTION_SHUFFLE).putExtra("value", !displayedShuffle);
            startService(command);
        });
        toggles.addView(shuffleButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        repeatButton = button("تکرار: خاموش", "تغییر حالت تکرار", BLUE_LIGHT, BLUE);
        repeatButton.setOnClickListener(v -> {
            Intent command = sendService(PlaybackService.ACTION_REPEAT).putExtra("value", (displayedRepeat + 1) % 3);
            startService(command);
        });
        toggles.addView(repeatButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        favoriteNowButton = button("افزودن به علاقه‌مندی‌ها", "افزودن یا حذف آهنگ در حال پخش از علاقه‌مندی‌ها", WHITE, BLUE);
        favoriteNowButton.setOnClickListener(v -> toggleNowFavorite());
        toggles.addView(favoriteNowButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        panel.addView(toggles, margin(0, dp(3), 0, 0));

        LinearLayout adjustments = row();
        Button speedDown = button("سرعت −", "کاهش سرعت پخش", WHITE, INK);
        speedDown.setOnClickListener(v -> changeRate(PlaybackService.ACTION_SPEED, Math.max(.5f, displayedSpeed - .1f)));
        adjustments.addView(speedDown, new LinearLayout.LayoutParams(0, dp(48), 1));
        speedLabel = text("سرعت ۱ برابر", 12, MUTED, true);
        speedLabel.setGravity(Gravity.CENTER);
        speedLabel.setContentDescription("سرعت فعلی پخش");
        adjustments.addView(speedLabel, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button speedUp = button("سرعت +", "افزایش سرعت پخش", WHITE, INK);
        speedUp.setOnClickListener(v -> changeRate(PlaybackService.ACTION_SPEED, Math.min(2f, displayedSpeed + .1f)));
        adjustments.addView(speedUp, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button pitchDown = button("زیر و بمی −", "کاهش زیر و بمی صدا", WHITE, INK);
        pitchDown.setOnClickListener(v -> changeRate(PlaybackService.ACTION_PITCH, Math.max(.5f, displayedPitch - .1f)));
        adjustments.addView(pitchDown, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button pitchUp = button("زیر و بمی +", "افزایش زیر و بمی صدا", WHITE, INK);
        pitchUp.setOnClickListener(v -> changeRate(PlaybackService.ACTION_PITCH, Math.min(2f, displayedPitch + .1f)));
        adjustments.addView(pitchUp, new LinearLayout.LayoutParams(0, dp(48), 1));
        panel.addView(adjustments, margin(0, dp(3), 0, 0));
        pitchLabel = text("زیر و بمی ۱ برابر", 12, MUTED, false);
        pitchLabel.setGravity(Gravity.CENTER);
        panel.addView(pitchLabel);

        audioEffectsButton = button(audioSceneButtonText(), audioSceneButtonDescription(), BLUE_LIGHT, BLUE);
        audioEffectsButton.setOnClickListener(v -> showAudioEffectsDialog());
        panel.addView(audioEffectsButton, margin(0, dp(4), 0, 0));

        LinearLayout lastRow = row();
        TextView skip = text("جلو/عقب: ۱۵ ثانیه", 12, MUTED, false);
        skip.setGravity(Gravity.CENTER);
        skip.setContentDescription("مدت پرش جلو و عقب. برای تغییر دو بار ضربه بزنید.");
        skip.setFocusable(true);
        skip.setOnClickListener(v -> showSkipDialog());
        lastRow.addView(skip, new LinearLayout.LayoutParams(0, dp(40), 1));
        Button sleep = button("تایمر خواب", "تنظیم تایمر توقف پخش", WHITE, INK);
        sleepButton = sleep;
        sleep.setOnClickListener(v -> showSleepDialog());
        lastRow.addView(sleep, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button help = button("راهنمای دسترس‌پذیری", "راهنمای استفاده با صفحه‌خوان و صفحه‌کلید", WHITE, BLUE);
        help.setOnClickListener(v -> showHelp());
        lastRow.addView(help, new LinearLayout.LayoutParams(0, dp(44), 1));
        panel.addView(lastRow);

        root.addView(panel, margin(0, dp(5), 0, 0));
    }

    private Intent lastServiceIntent;

    private Intent sendService(String action) {
        Intent intent = new Intent(this, PlaybackService.class).setAction(action);
        lastServiceIntent = intent;
        return intent;
    }

    private void sendAction(String action) { startService(sendService(action)); }

    private void changeRate(String action, float value) {
        sendService(action).putExtra("value", value);
        startService(lastServiceIntent);
        announce((action.equals(PlaybackService.ACTION_SPEED) ? "سرعت پخش " : "زیر و بمی صدا ")
                + String.format(Locale.forLanguageTag("fa-IR"), "%.1f", value));
    }

    private void chooseFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(intent, REQUEST_FOLDER); }
        catch (Exception e) { announce("انتخاب پوشه در این دستگاه در دسترس نیست"); }
    }

    private void chooseFiles() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(intent, REQUEST_FILES); }
        catch (Exception e) { announce("انتخاب فایل صوتی در این دستگاه در دسترس نیست"); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == REQUEST_FOLDER && data.getData() != null) {
            Uri folder = data.getData();
            try { getContentResolver().takePersistableUriPermission(folder, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (Exception ignored) { }
            selectedFolder = folder;
            prefs.edit().putString("folder_uri", folder.toString()).apply();
            scanFolder(folder, false);
        } else if (requestCode == REQUEST_FILES) {
            ArrayList<AudioTrack> selected = new ArrayList<>();
            ClipData clips = data.getClipData();
            if (clips != null) {
                for (int i = 0; i < clips.getItemCount(); i++) addSelectedUri(selected, clips.getItemAt(i).getUri());
            } else if (data.getData() != null) addSelectedUri(selected, data.getData());
            if (!selected.isEmpty()) {
                Collections.sort(selected, titleComparator());
                allTracks.clear();
                allTracks.addAll(selected);
                favoritesOnly = false;
                updateList();
                playTrack(selected.get(0));
                announce(selected.size() + " آهنگ انتخاب شد");
            }
        }
    }

    private void addSelectedUri(List<AudioTrack> target, Uri uri) {
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (Exception ignored) { }
        target.add(new AudioTrack(uri, displayName(uri), "خواننده ناشناس", 0));
    }

    private String displayName(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null) return "آهنگ انتخاب‌شده";
        int colon = name.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < name.length()) name = name.substring(colon + 1);
        int slash = name.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < name.length()) name = name.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private void scanFolder(Uri folder, boolean initial) {
        status.setText("در حال بررسی آهنگ‌ها…");
        announce("در حال بررسی پوشهٔ موسیقی");
        scanner.execute(() -> {
            List<AudioTrack> result = LibraryScanner.scan(getContentResolver(), folder);
            runOnUiThread(() -> {
                allTracks.clear();
                allTracks.addAll(result);
                sortTracks();
                updateList();
                status.setText(result.isEmpty() ? "در این پوشه آهنگی پیدا نشد. پوشهٔ دیگری انتخاب کنید." : result.size() + " آهنگ آمادهٔ پخش است");
                resumeButton.setVisibility(View.GONE);
                if (!result.isEmpty()) {
                    announce(result.size() + " آهنگ پیدا شد");
                    String lastUri = prefs.getString("last_uri", "");
                    for (AudioTrack track : result) {
                        if (track.uri.toString().equals(lastUri)) {
                            resumeButton.setVisibility(View.VISIBLE);
                            resumeButton.setOnClickListener(v -> playTrack(track));
                            break;
                        }
                    }
                } else if (initial) announce("برای شروع، پوشهٔ موسیقی یا فایل آهنگ را انتخاب کنید");
            });
        });
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        Uri uri = intent.getData();
        if (uri == null && Intent.ACTION_SEND.equals(intent.getAction())) {
            if (Build.VERSION.SDK_INT >= 33) uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
            else uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        if (uri != null && (Intent.ACTION_VIEW.equals(intent.getAction()) || Intent.ACTION_SEND.equals(intent.getAction()))) {
            AudioTrack track = new AudioTrack(uri, displayName(uri), "خواننده ناشناس", 0);
            allTracks.clear();
            allTracks.add(track);
            updateList();
            playTrack(track);
        }
    }

    private void playTrack(AudioTrack track) {
        ArrayList<String> queue = new ArrayList<>();
        ArrayList<AudioTrack> source = new ArrayList<>(visibleTracks);
        if (source.isEmpty()) source.addAll(allTracks);
        int selectedIndex = 0;
        for (int i = 0; i < source.size(); i++) {
            queue.add(source.get(i).uri.toString());
            if (source.get(i).uri.equals(track.uri)) selectedIndex = i;
        }
        if (queue.isEmpty()) {
            queue.add(track.uri.toString());
            selectedIndex = 0;
        }
        Intent intent = sendService(PlaybackService.ACTION_QUEUE)
                .putStringArrayListExtra("queue", queue).putExtra("index", selectedIndex);
        startService(intent);
        maybeRequestNotificationPermission();
        nowTitle.setText(track.title);
        nowArtist.setText(track.artist);
        status.setText("در حال آماده‌کردن: " + track.title);
        lastAnnouncedTitle = "در حال پخش: " + track.title + "، " + track.artist;
        announce(lastAnnouncedTitle);
    }

    private void updateList() {
        visibleTracks.clear();
        String query = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        for (AudioTrack track : allTracks) {
            if (favoritesOnly && !favorites.contains(track.uri.toString())) continue;
            String searchable = (track.title + " " + track.artist).toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !searchable.contains(query)) continue;
            visibleTracks.add(track);
        }
        if (adapter != null) adapter.notifyDataSetChanged();
        if (count != null) count.setText(visibleTracks.size() + " آهنگ");
        if (status != null && allTracks.isEmpty()) status.setText("برای شروع، پوشهٔ موسیقی یا فایل آهنگ را انتخاب کنید.");
    }

    private Comparator<AudioTrack> titleComparator() {
        Collator collator = Collator.getInstance(new Locale("fa", "IR"));
        return (a, b) -> collator.compare(a.title, b.title);
    }

    private void sortTracks() {
        if (sortMode == 1) allTracks.sort((a, b) -> Long.compare(b.modifiedTimeMs, a.modifiedTimeMs));
        else if (sortMode == 2) allTracks.sort(Comparator.comparingLong(a -> a.modifiedTimeMs));
        else Collections.sort(allTracks, titleComparator());
    }

    private void showSortDialog() {
        String[] items = {"نام آهنگ، الفبایی", "تازه‌ترین فایل‌ها", "قدیمی‌ترین فایل‌ها"};
        new AlertDialog.Builder(this).setTitle("مرتب‌سازی فهرست آهنگ‌ها")
                .setItems(items, (dialog, which) -> {
                    sortMode = which;
                    prefs.edit().putInt("sort_mode", sortMode).apply();
                    sortTracks();
                    updateList();
                    announce("فهرست مرتب شد: " + items[which]);
                }).setNegativeButton("بستن", null).show();
    }

    private void showSkipDialog() {
        String[] choices = {"۵ ثانیه", "۱۰ ثانیه", "۱۵ ثانیه", "۳۰ ثانیه"};
        long[] values = {5_000, 10_000, 15_000, 30_000};
        int current = 2;
        for (int i = 0; i < values.length; i++) if (values[i] == skipMs) current = i;
        new AlertDialog.Builder(this).setTitle("میزان رفتن به جلو یا عقب")
                .setSingleChoiceItems(choices, current, (dialog, which) -> {
                    skipMs = values[which];
                    prefs.edit().putInt("skip_ms", (int) skipMs).apply();
                    Intent command = sendService(PlaybackService.ACTION_SET_SKIP).putExtra("value", (int) skipMs);
                    startService(command);
                    updateSkipButtons();
                    dialog.dismiss();
                    announce("پرش جلو و عقب روی " + choices[which] + " تنظیم شد");
                }).setNegativeButton("بستن", null).show();
    }

    private void updateSkipButtons() {
        String value = String.valueOf(skipMs / 1000);
        backControl.setText("عقب " + value);
        backControl.setContentDescription("رفتن " + value + " ثانیه به عقب");
        forwardControl.setText("جلو " + value);
        forwardControl.setContentDescription("رفتن " + value + " ثانیه به جلو");
    }

    private void showSleepDialog() {
        String[] choices = {"خاموش", "۱۵ دقیقه", "۳۰ دقیقه", "۴۵ دقیقه", "۶۰ دقیقه", "پایان آهنگ فعلی"};
        new AlertDialog.Builder(this).setTitle("تایمر خواب")
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) {
                        Intent command = sendService(PlaybackService.ACTION_SLEEP).putExtra("minutes", 0);
                        startService(command);
                        announce("تایمر خواب خاموش شد");
                    } else if (which == choices.length - 1) {
                        sendAction(PlaybackService.ACTION_SLEEP_END);
                        announce("پخش در پایان آهنگ فعلی متوقف می‌شود");
                    } else {
                        int minutes = which * 15;
                        Intent command = sendService(PlaybackService.ACTION_SLEEP).putExtra("minutes", minutes);
                        startService(command);
                        announce("تایمر خواب روی " + choices[which] + " تنظیم شد");
                    }
                }).setNegativeButton("بستن", null).show();
    }

    private void showAudioEffectsDialog() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        content.setPadding(dp(20), dp(8), dp(20), dp(4));

        TextView note = text("سالن کنسرت: بازتاب محیطی برای شبیه‌سازی فضای سالن. خودرو: رنگ‌آمیزی تُن صدا بر پایهٔ پاسخ معمول بلندگوهای کابین. این‌ها شبیه‌سازی‌اند، نه ضبط صدای یک سالن یا خودروی مشخص. اگر گوشی افکت نشست پخش را پشتیبانی نکند، صدا بدون افکت و بدون قطع‌شدن پخش می‌شود.", 14, MUTED, false);
        note.setContentDescription(note.getText().toString());
        content.addView(note, margin(0, 0, 0, dp(8)));

        Button off = button("", "خاموش‌کردن افکت صوتی", WHITE, INK);
        Button concert = button("", "فعال‌کردن افکت سالن کنسرت", WHITE, BLUE);
        Button car = button("", "فعال‌کردن افکت خودرو", WHITE, BLUE);
        content.addView(off, margin(0, dp(2), 0, dp(2)));
        content.addView(concert, margin(0, dp(2), 0, dp(2)));
        content.addView(car, margin(0, dp(2), 0, dp(8)));

        TextView intensityLabel = text("شدت افکت: " + audioSceneIntensity + " درصد", 14, INK, true);
        intensityLabel.setContentDescription("شدت افکت " + audioSceneIntensity + " درصد");
        content.addView(intensityLabel);
        SeekBar intensity = new SeekBar(this);
        intensity.setMax(100);
        intensity.setProgress(audioSceneIntensity);
        intensity.setContentDescription("تنظیم شدت افکت سالن کنسرت یا خودرو از صفر تا صد درصد");
        content.addView(intensity, margin(0, 0, 0, dp(8)));

        Runnable updateChoices = () -> {
            off.setText(audioScene == AudioSceneEffects.SCENE_OFF ? "فعال: خاموش" : "خاموش");
            concert.setText(audioScene == AudioSceneEffects.SCENE_CONCERT ? "فعال: سالن کنسرت" : "سالن کنسرت");
            car.setText(audioScene == AudioSceneEffects.SCENE_CAR ? "فعال: خودرو" : "خودرو");
            off.setContentDescription(audioScene == AudioSceneEffects.SCENE_OFF ? "افکت صوتی اکنون خاموش است" : "خاموش‌کردن افکت صوتی");
            concert.setContentDescription(audioScene == AudioSceneEffects.SCENE_CONCERT ? "افکت سالن کنسرت اکنون انتخاب شده است" : "فعال‌کردن افکت سالن کنسرت");
            car.setContentDescription(audioScene == AudioSceneEffects.SCENE_CAR ? "افکت خودرو اکنون انتخاب شده است" : "فعال‌کردن افکت خودرو");
        };
        updateChoices.run();
        off.setOnClickListener(v -> { selectAudioScene(AudioSceneEffects.SCENE_OFF); updateChoices.run(); });
        concert.setOnClickListener(v -> { selectAudioScene(AudioSceneEffects.SCENE_CONCERT); updateChoices.run(); });
        car.setOnClickListener(v -> { selectAudioScene(AudioSceneEffects.SCENE_CAR); updateChoices.run(); });
        intensity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                intensityLabel.setText("شدت افکت: " + progress + " درصد");
                intensityLabel.setContentDescription("شدت افکت " + progress + " درصد");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                audioSceneIntensity = seekBar.getProgress();
                prefs.edit().putInt("audio_scene_intensity", audioSceneIntensity).apply();
                sendAudioSceneToService();
            }
        });

        new AlertDialog.Builder(this).setTitle("افکت‌های صوتی")
                .setView(content).setPositiveButton("بستن", null).show();
    }

    private void selectAudioScene(int scene) {
        audioScene = scene;
        prefs.edit().putInt("audio_scene", audioScene)
                .putInt("audio_scene_intensity", audioSceneIntensity).apply();
        if (audioEffectsButton != null) {
            audioEffectsButton.setText(audioSceneButtonText());
            audioEffectsButton.setContentDescription(audioSceneButtonDescription());
        }
        sendAudioSceneToService();
    }

    private void sendAudioSceneToService() {
        Intent command = sendService(PlaybackService.ACTION_AUDIO_SCENE)
                .putExtra("scene", audioScene).putExtra("intensity", audioSceneIntensity);
        startService(command);
    }

    private String audioSceneButtonText() {
        return "افکت صوتی: " + audioSceneName(audioScene);
    }

    private String audioSceneButtonDescription() {
        return "افکت صوتی فعلی: " + audioSceneName(audioScene) + "؛ برای انتخاب افکت سالن کنسرت، خودرو یا خاموش دوبار ضربه بزنید";
    }

    private static String audioSceneName(int scene) {
        if (scene == AudioSceneEffects.SCENE_CONCERT) return "سالن کنسرت";
        if (scene == AudioSceneEffects.SCENE_CAR) return "خودرو";
        return "خاموش";
    }

    private void showHelp() {
        String message = "همهٔ کنترل‌ها نام فارسی دارند و با TalkBack قابل پیمایش‌اند. روی نام آهنگ دو بار ضربه بزنید تا پخش شود. برای حرکت در آهنگ، نوار موقعیت را با لمس یا کلیدهای جهت‌دار تنظیم کنید. دکمه‌های قبلی و بعدی، جلو و عقب، علاقه‌مندی، پخش تصادفی، تکرار، سرعت و زیر و بمی صدا توضیح شنیداری دارند. پوشهٔ موسیقی را از انتخاب‌گر خود Android انتخاب کنید؛ برنامه فقط به همان پوشه دسترسی می‌گیرد. برای صفحه‌کلید، Space پخش/مکث و کلیدهای رسانه‌ای قبلی/بعدی را به‌کار ببرید.";
        new AlertDialog.Builder(this).setTitle("راهنمای دسترس‌پذیری")
                .setMessage(message)
                .setNeutralButton(announceTracks ? "خاموش‌کردن اعلان خودکار آهنگ" : "روشن‌کردن اعلان خودکار آهنگ", (dialog, which) -> {
                    announceTracks = !announceTracks;
                    prefs.edit().putBoolean("announce_tracks", announceTracks).apply();
                    announce(announceTracks ? "اعلان خودکار آهنگ‌ها روشن شد" : "اعلان خودکار آهنگ‌ها خاموش شد");
                })
                .setPositiveButton("متوجه شدم", null).show();
    }

    private void loadFavorites() {
        Set<String> saved = prefs.getStringSet("favorites", new HashSet<>());
        favorites.clear();
        if (saved != null) favorites.addAll(saved);
    }

    private void persistFavorites() {
        prefs.edit().putStringSet("favorites", new HashSet<>(favorites)).apply();
        refreshNowFavorite();
        updateList();
    }

    private void toggleFavorite(AudioTrack track) {
        boolean added;
        if (favorites.contains(track.uri.toString())) {
            favorites.remove(track.uri.toString());
            added = false;
        } else {
            favorites.add(track.uri.toString());
            added = true;
        }
        persistFavorites();
        announce(added ? "به علاقه‌مندی‌ها اضافه شد: " + track.title : "از علاقه‌مندی‌ها حذف شد: " + track.title);
    }

    private void toggleNowFavorite() {
        for (AudioTrack track : allTracks) {
            if (track.uri.toString().equals(currentUri)) { toggleFavorite(track); return; }
        }
        announce("ابتدا یک آهنگ از فهرست انتخاب کنید");
    }

    private void refreshNowFavorite() {
        if (favoriteNowButton == null || nowTitle == null) return;
        boolean found = !currentUri.isEmpty() && favorites.contains(currentUri);
        favoriteNowButton.setText(found ? "حذف از علاقه‌مندی" : "افزودن به علاقه‌مندی‌ها");
        favoriteNowButton.setContentDescription(found ? "حذف آهنگ در حال پخش از علاقه‌مندی‌ها" : "افزودن آهنگ در حال پخش به علاقه‌مندی‌ها");
    }

    private void refreshOptionLabels() {
        speedLabel.setText("سرعت " + String.format(Locale.forLanguageTag("fa-IR"), "%.1f", displayedSpeed) + " برابر");
        pitchLabel.setText("زیر و بمی " + String.format(Locale.forLanguageTag("fa-IR"), "%.1f", displayedPitch) + " برابر");
        String[] repeat = {"خاموش", "همه", "یک آهنگ"};
        repeatButton.setText("تکرار: " + repeat[Math.max(0, Math.min(2, displayedRepeat))]);
        repeatButton.setContentDescription("حالت تکرار " + repeat[Math.max(0, Math.min(2, displayedRepeat))] + "؛ برای تغییر دو بار ضربه بزنید");
        shuffleButton.setText(displayedShuffle ? "تصادفی: روشن" : "تصادفی: خاموش");
        shuffleButton.setContentDescription(displayedShuffle ? "خاموش‌کردن پخش تصادفی" : "روشن‌کردن پخش تصادفی");
    }

    private void registerPlaybackReceiver() {
        if (receiverRegistered) return;
        IntentFilter filter = new IntentFilter(PlaybackService.BROADCAST_STATE);
        ContextCompat.registerReceiver(this, playbackReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;
    }

    private void maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                || prefs.getBoolean("notification_prompted", false)) return;
        prefs.edit().putBoolean("notification_prompted", true).apply();
        new AlertDialog.Builder(this).setTitle("کنترل پخش در اعلان‌ها")
                .setMessage("اجازهٔ اعلان کمک می‌کند نام آهنگ و دکمه‌های پخش، مکث و آهنگ بعدی هنگام کار با برنامه‌های دیگر در دسترس باشند. می‌توانید ادامه دهید و اجازه ندهید؛ پخش در خود برنامه همچنان کار می‌کند.")
                .setPositiveButton("درخواست اجازه", (dialog, which) -> requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION))
                .setNegativeButton("ادامه بدون اعلان", null).show();
    }

    @Override protected void onStart() {
        super.onStart();
        registerPlaybackReceiver();
        startService(sendService(PlaybackService.ACTION_REFRESH));
    }

    @Override protected void onStop() {
        if (receiverRegistered) {
            unregisterReceiver(playbackReceiver);
            receiverRegistered = false;
        }
        super.onStop();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        View focus = getCurrentFocus();
        boolean editing = focus instanceof EditText;
        if (!editing && (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
            sendAction(PlaybackService.ACTION_PLAY_PAUSE);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) { sendAction(PlaybackService.ACTION_NEXT); return true; }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) { sendAction(PlaybackService.ACTION_PREVIOUS); return true; }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onDestroy() {
        scanner.shutdownNow();
        super.onDestroy();
    }

    private void announce(String message) {
        if (status == null || message == null || message.trim().isEmpty()) return;
        status.setText(message);
        status.announceForAccessibility(message);
        status.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return r;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(size);
        tv.setTextColor(color);
        tv.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        tv.setTextDirection(View.TEXT_DIRECTION_RTL);
        tv.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        tv.setLineSpacing(dp(2), 1f);
        return tv;
    }

    private Button button(String label, String spoken, int background, int foreground) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(13);
        b.setTextColor(foreground);
        b.setContentDescription(spoken);
        b.setAllCaps(false);
        b.setMinHeight(dp(48));
        b.setMinimumHeight(dp(48));
        b.setMinimumWidth(dp(48));
        b.setPadding(dp(5), dp(3), dp(5), dp(3));
        b.setBackground(round(background, 12));
        b.setFocusable(true);
        b.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        b.setStateListAnimator(null);
        return b;
    }

    private LinearLayout.LayoutParams equalButton() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(52), 1);
        p.setMargins(dp(2), 0, dp(2), 0);
        return p;
    }

    private LinearLayout.LayoutParams margin(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(left, top, right, bottom);
        return p;
    }

    private android.graphics.drawable.GradientDrawable round(int color, int radius) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }

    private final class SongAdapter extends BaseAdapter {
        private final Context context;
        SongAdapter(Context context) { this.context = context; }
        @Override public int getCount() { return visibleTracks.size(); }
        @Override public Object getItem(int position) { return visibleTracks.get(position); }
        @Override public long getItemId(int position) { return visibleTracks.get(position).uri.toString().hashCode(); }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            AudioTrack track = visibleTracks.get(position);
            LinearLayout item = new LinearLayout(context);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            item.setPadding(dp(12), dp(5), dp(10), dp(5));
            item.setMinimumHeight(dp(64));
            item.setFocusable(true);
            item.setClickable(true);
            item.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            item.setContentDescription(track.spokenLabel());

            LinearLayout names = new LinearLayout(context);
            names.setOrientation(LinearLayout.VERTICAL);
            names.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            TextView title = text(track.title, 16, INK, true);
            title.setMaxLines(2);
            title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            names.addView(title);
            TextView artist = text(track.artist, 13, MUTED, false);
            artist.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            names.addView(artist);
            item.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            boolean favorite = favorites.contains(track.uri.toString());
            Button star = button(favorite ? "★" : "☆", favorite ? "حذف از علاقه‌مندی‌ها" : "افزودن به علاقه‌مندی‌ها",
                    favorite ? GOLD : BLUE_LIGHT, INK);
            star.setTextSize(20);
            star.setContentDescription((favorite ? "آهنگ موردعلاقه است؛ حذف از علاقه‌مندی‌ها: " : "افزودن به علاقه‌مندی‌ها: ") + track.title);
            star.setOnClickListener(v -> toggleFavorite(track));
            item.addView(star, new LinearLayout.LayoutParams(dp(58), dp(54)));
            return item;
        }
    }
}
