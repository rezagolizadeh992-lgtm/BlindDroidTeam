package ir.panah.safety;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int INK = Color.rgb(24, 50, 48);
    private static final int MUTED = Color.rgb(92, 112, 108);
    private static final int PAPER = Color.rgb(245, 247, 242);
    private static final int WHITE = Color.WHITE;
    private static final int TEAL = Color.rgb(23, 107, 98);
    private static final int TEAL_LIGHT = Color.rgb(220, 239, 234);
    private static final int CORAL = Color.rgb(185, 67, 54);
    private static final int CORAL_LIGHT = Color.rgb(252, 233, 228);
    private static final int GOLD = Color.rgb(241, 197, 106);
    private static final int LINE = Color.rgb(224, 232, 226);

    private LinearLayout root;
    private LinearLayout content;
    private LinearLayout nav;
    private ScrollView scroll;
    private SharedPreferences prefs;
    private String page = "home";
    private String selectedHazard = "earthquake";

    private static final String[] KIT_KEYS = {
            "kit_water", "kit_food", "kit_radio", "kit_light", "kit_firstaid",
            "kit_meds", "kit_whistle", "kit_power", "kit_documents", "kit_clothes",
            "kit_hygiene", "kit_cash", "kit_children", "kit_pets", "kit_refresh"
    };
    private static final String[] KIT_LABELS = {
            "آب آشامیدنی و بهداشتی برای چند روز",
            "خوراکی ماندگار و دربازکن دستی",
            "رادیوی باتری‌خور یا هندلی و باتری اضافه",
            "چراغ‌قوه یا چراغ پیشانی",
            "جعبه کمک‌های اولیه و ماسک گردوغبار",
            "داروهای ضروری، عینک و نسخه‌ها",
            "سوت برای درخواست کمک",
            "شارژر، پاوربانک و کابل گوشی",
            "مدارک مهم در پوشش ضدآب و کپی دیجیتال امن",
            "لباس مناسب، کفش محکم و پتوی سبک",
            "اقلام بهداشت فردی، دستمال و کیسه زباله",
            "مقداری پول نقد و فهرست شماره‌های ضروری",
            "نیازهای نوزادان، سالمندان یا افراد دارای معلولیت",
            "آب، غذا و داروی حیوان خانگی",
            "بازبینی تاریخ مصرف و محل نگهداری کیف"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(PAPER);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        prefs = getSharedPreferences("panah_local", MODE_PRIVATE);
        buildShell();
        renderPage();
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(PAPER);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(10), dp(20), dp(10));
        header.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView brand = text("پَناه", 23, INK, true);
        brand.setLetterSpacing(0.02f);
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView call = pill("تماس اضطراری ۱۱۲", CORAL_LIGHT, CORAL, true);
        call.setContentDescription("تماس با شماره امداد و نجات ۱۱۲ در ایران");
        call.setOnClickListener(v -> dial("112"));
        header.addView(call);
        root.addView(header);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        content.setPadding(dp(18), dp(8), dp(18), dp(26));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(WHITE);
        nav.setElevation(dp(8));
        nav.setPadding(dp(8), dp(6), dp(8), dp(8));
        String[][] tabs = {{"home", "خانه"}, {"guides", "راهنماها"}, {"kit", "کیف نجات"}, {"plan", "برنامه من"}};
        for (String[] tab : tabs) {
            TextView item = text(tab[1], 12, MUTED, true);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(6), dp(10), dp(6), dp(10));
            item.setOnClickListener(v -> {
                page = tab[0];
                renderPage();
            });
            nav.addView(item, new LinearLayout.LayoutParams(0, dp(46), 1));
        }
        root.addView(nav);
    }

    private void renderPage() {
        content.removeAllViews();
        scroll.post(() -> scroll.scrollTo(0, 0));
        if ("home".equals(page)) renderHome();
        else if ("guides".equals(page)) renderGuides();
        else if ("kit".equals(page)) renderKit();
        else renderPlan();
    }

    private void renderHome() {
        addText("برای روزهای سخت،\nیک راهنمای آرام و روشن.", 27, INK, true, 0, 8);
        addText("دستورالعمل‌های فوری را آفلاین ببین و از قبل آماده شو.", 15, MUTED, false, 0, 16);

        LinearLayout urgent = panel(CORAL_LIGHT, 18);
        addTo(urgent, "اگر همین حالا خطر هست", 16, CORAL, true, 0, 4);
        addTo(urgent, "اول به محل امن برو. به ساختمان آسیب‌دیده، سیم برق افتاده یا آب سیلاب نزدیک نشو. برای امداد در ایران با ۱۱۲ تماس بگیر.", 15, INK, false, 0, 12);
        TextView emergencyButton = button("تماس با امداد و نجات ۱۱۲", CORAL, WHITE);
        emergencyButton.setOnClickListener(v -> dial("112"));
        urgent.addView(emergencyButton);
        content.addView(urgent, margin(0, 0, 0, 14));

        LinearLayout quake = panel(TEAL, 18);
        addTo(quake, "زلزله همین حالا؟", 18, WHITE, true, 0, 5);
        addTo(quake, "بنشین، پناه بگیر، محکم نگه دار. داخل ساختمان ندو بیرون.", 15, WHITE, false, 0, 12);
        TextView quakeMore = pill("راهنمای فوری زلزله ←", WHITE, TEAL, true);
        quakeMore.setOnClickListener(v -> openHazard("earthquake"));
        quake.addView(quakeMore);
        content.addView(quake, margin(0, 0, 0, 20));

        sectionTitle("راهنمای بلایا");
        addText("برای دیدن کارهای قبل، هنگام و بعد از هر حادثه انتخاب کن.", 14, MUTED, false, 0, 10);
        String[][] cards = {
                {"زمین‌لرزه", "earthquake", "↕"}, {"سیل و آب‌گرفتگی", "flood", "≈"},
                {"آتش‌سوزی طبیعی", "wildfire", "♨"}, {"طوفان و رعدوبرق", "storm", "☁"},
                {"رانش زمین", "landslide", "▲"}, {"گرمای شدید", "heat", "☀"}
        };
        for (String[] card : cards) {
            LinearLayout item = panel(WHITE, 15);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            TextView icon = pill(card[2], TEAL_LIGHT, TEAL, true);
            row.addView(icon, margin(0, 0, 12, 0));
            TextView label = text(card[0], 16, INK, true);
            row.addView(label, new LinearLayout.LayoutParams(0, dp(44), 1));
            TextView arrow = text("‹", 22, MUTED, true);
            row.addView(arrow);
            item.addView(row);
            item.setOnClickListener(v -> openHazard(card[1]));
            content.addView(item, margin(0, 0, 0, 9));
        }

        sectionTitle("قدم کوچک امروز");
        LinearLayout prep = panel(WHITE, 16);
        addTo(prep, "یک کیف آماده، زمان تصمیم‌گیری را کمتر می‌کند.", 15, INK, true, 0, 10);
        TextView goKit = pill("فهرست وسایل کیف نجات ←", TEAL_LIGHT, TEAL, true);
        goKit.setOnClickListener(v -> { page = "kit"; renderPage(); });
        prep.addView(goKit);
        content.addView(prep, margin(0, 0, 0, 12));

        addText("پناه هشدار زنده یا تشخیص زلزله ندارد. برای هشدارها و دستور تخلیه، اطلاعیه‌های رسمی محل زندگی‌ات را دنبال کن.", 12, MUTED, false, 0, 12);
        addSourcesButton();
    }

    private void openHazard(String hazard) {
        selectedHazard = hazard;
        page = "guides";
        renderPage();
    }

    private void renderGuides() {
        sectionTitle("راهنمای سریع");
        addText("در خطر فوری، ابتدا از خودت محافظت کن و سپس از خدمات امدادی کمک بگیر.", 14, MUTED, false, 0, 12);
        String[][] cards = {
                {"زمین‌لرزه", "earthquake"}, {"سیل و آب‌گرفتگی", "flood"},
                {"آتش‌سوزی طبیعی", "wildfire"}, {"طوفان و رعدوبرق", "storm"},
                {"رانش زمین", "landslide"}, {"گرمای شدید", "heat"}
        };
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.VERTICAL);
        for (String[] card : cards) {
            TextView chip = pill(card[0] + (selectedHazard.equals(card[1]) ? "  •" : ""),
                    selectedHazard.equals(card[1]) ? TEAL_LIGHT : WHITE,
                    selectedHazard.equals(card[1]) ? TEAL : INK, true);
            chip.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
            chip.setOnClickListener(v -> openHazard(card[1]));
            chips.addView(chip, margin(0, 0, 0, 7));
        }
        content.addView(chips, margin(0, 0, 0, 12));
        renderHazardDetail(selectedHazard);
    }

    private void renderHazardDetail(String key) {
        String title;
        String[] before;
        String[] during;
        String[] after;
        switch (key) {
            case "flood":
                title = "سیل و آب‌گرفتگی";
                before = new String[]{"راه‌های خروج و محل مرتفع امن را از قبل بشناس.", "مدارک، داروها و وسایل ضروری را در پوشش ضدآب نگه دار.", "هشدارهای رسمی هواشناسی و مدیریت بحران محل را دنبال کن."};
                during = new String[]{"اگر دستور تخلیه داده شد، زود و از مسیر امن خارج شو.", "در آب جاری راه نرو، شنا نکن و رانندگی نکن؛ از موانع عبور نکن.", "به طبقه بالاتر برو و از پل روی آب تند دور بمان.", "به وسایل برقی خیس یا سیم افتاده دست نزن."};
                after = new String[]{"تا اعلام رسمی ایمن‌بودن، به خانه برنگرد.", "آب سیلاب ممکن است آلوده یا برق‌دار باشد؛ وارد آن نشو.", "برای پاکسازی دستکش و کفش مناسب بپوش و از خطوط برق دور بمان."};
                break;
            case "wildfire":
                title = "آتش‌سوزی طبیعی";
                before = new String[]{"راه‌های خروج و مقصد جایگزین را بشناس و برای افراد و حیوانات برنامه داشته باش.", "گوشی را شارژ و کیف ضروری را دم دست نگه دار.", "هشدارهای رسمی و کیفیت هوا را پیگیری کن."};
                during = new String[]{"با دستور مقام‌های محلی فوراً تخلیه کن؛ منتظر نزدیک‌شدن آتش نمان.", "اگر تخلیه نشده‌ای اما دود هست، در فضای بسته امن بمان و پنجره‌ها را ببند.", "تا حد امکان از دود دور شو؛ ماسک مناسب ذرات را فقط اگر در دسترس است استفاده کن.", "اگر گرفتار شدی با امداد تماس بگیر و مکانت را دقیق بگو."};
                after = new String[]{"تا اعلام رسمی، به منطقه تخلیه‌شده برنگرد.", "از خاکستر داغ، درخت سوخته و بقایای دودکننده فاصله بگیر.", "هنگام پاکسازی از دستکش، کفش محکم و پوشش تنفسی مناسب استفاده کن."};
                break;
            case "storm":
                title = "طوفان و رعدوبرق";
                before = new String[]{"هشدارهای هواشناسی محل را دنبال کن و پناهگاه سرپوشیده محکم را بشناس.", "وسایل بیرونی و اشیای سبک را ایمن کن.", "برای سیلاب ناگهانی هم آماده باش."};
                during = new String[]{"با شنیدن رعد، به ساختمان محکم یا خودروی سقف‌دار برو.", "از دستگاه‌های متصل به برق و استفاده از آب لوله‌کشی دوری کن.", "در جاده آب‌گرفته رانندگی نکن و از آب جاری عبور نکن.", "به دستورهای رسمی توجه کن."};
                after = new String[]{"تا روشن‌شدن وضعیت، پیش‌بینی و هشدارهای رسمی را دنبال کن.", "به سیم‌های برق و درختان افتاده نزدیک نشو و خطر را گزارش کن.", "مراقب آب‌گرفتگی پس از باران باش."};
                break;
            case "landslide":
                title = "رانش زمین";
                before = new String[]{"در زمان بارندگی شدید، سیلاب یا پس از آتش‌سوزی دامنه، هشدارها را جدی بگیر.", "صدای غرش، ترک‌های تازه، کج‌شدن درخت یا تغییر ناگهانی آبراه را نشانه خطر بدان.", "مسیر خروج از دره و دامنه را از قبل بشناس."};
                during = new String[]{"اگر دستور تخلیه صادر شد یا ماندن ناامن است، فوراً خارج شو.", "از دره، آبراه و مسیر گل‌ولای دور شو؛ از جاده یا پل دارای جریان عبور نکن.", "اگر در مسیر رانش گیر افتادی، تا می‌توانی سریع به سمت بالادست و جای بلندتر برو.", "به دستورهای نیروهای محلی گوش بده."};
                after = new String[]{"به محدوده رانش نزدیک نشو؛ رانش ثانویه ممکن است رخ دهد.", "افراد آسیب‌دیده را فقط از محل امن بررسی کن و محلشان را به امداد بگو.", "از ساختمان آسیب‌دیده و خطوط خدماتی صدمه‌دیده دور بمان."};
                break;
            case "heat":
                title = "گرمای شدید";
                before = new String[]{"مکان خنک نزدیک خانه و راه دسترسی به آن را پیدا کن.", "برای سالمندان، کودکان، بیماران و حیوانات خانگی برنامه تماس و مراقبت داشته باش.", "هشدارهای رسمی گرما را دنبال کن."};
                during = new String[]{"تا حد امکان در جای خنک بمان و فعالیت بیرون را به ساعات خنک‌تر منتقل کن.", "آب کافی بنوش، لباس سبک بپوش و در سایه استراحت کن.", "از خودرو برای پناه‌گرفتن استفاده نکن و هیچ فرد یا حیوانی را در خودروی گرم تنها نگذار.", "حال همسایه‌ها و افراد آسیب‌پذیر را پیگیری کن."};
                after = new String[]{"اگر کسی بدحال شد، او را به جای خنک ببرید و برای کمک پزشکی فوری تماس بگیرید.", "تا بهترشدن هوا، توصیه‌های بهداشتی و هشدارهای محلی را دنبال کن.", "آب و داروهای ضروری را دوباره بررسی و تکمیل کن."};
                break;
            default:
                title = "زمین‌لرزه";
                before = new String[]{"وسایل سنگین و شکستنی را محکم کن و اشیای سنگین را در طبقه‌های پایین بگذار.", "محل پناه‌گرفتن و راه‌های خروج امن را با خانواده تمرین کن.", "کیف ضروری، داروها و شماره تماس اعضای خانواده را آماده نگه دار."};
                during = new String[]{"داخل ساختمان بمان؛ برای بیرون‌دویدن از پله یا آسانسور استفاده نکن.", "بنشین، زیر میز محکم پناه بگیر و پایه آن را نگه دار؛ سر و گردنت را بپوشان.", "اگر پناهی نیست، کنار دیوار داخلی و دور از پنجره جمع شو.", "در خودرو، کنار بزن و ترمز دستی را بکش؛ در فضای باز از ساختمان و سیم برق فاصله بگیر."};
                after = new String[]{"برای پس‌لرزه آماده باش؛ دوباره پناه بگیر و محکم نگه دار.", "از ساختمان آسیب‌دیده خارج شو و وارد آن نشو؛ از سیم‌های افتاده دور بمان.", "اگر کنار دریا هستی و لرزش شدید را حس کردی، پس از پایان لرزش به بلندی و دور از ساحل برو.", "اگر زیر آوار گیر افتادی، پیامک بفرست یا به لوله/دیوار ضربه بزن؛ دهان را بپوشان و در صورت امکان سوت بزن.", "به افراد دیگر فقط تا جایی که برای خودت امن است کمک کن؛ مصدوم بدحال را بی‌دلیل جابه‌جا نکن."};
        }
        LinearLayout box = panel(WHITE, 18);
        addTo(box, title, 21, INK, true, 0, 5);
        addTo(box, "راهنمای آفلاین • دستور مقام‌های محلی در اولویت است", 12, MUTED, false, 0, 15);
        addBullets(box, "پیش از حادثه", before, TEAL);
        addBullets(box, "هنگام حادثه", during, CORAL);
        addBullets(box, "پس از حادثه", after, TEAL);
        TextView contact = button("در ایران: تماس با امداد ۱۱۲", CORAL, WHITE);
        contact.setOnClickListener(v -> dial("112"));
        box.addView(contact, margin(0, dp(10), 0, 0));
        content.addView(box, margin(0, 0, 0, 14));
        addText("اگر خارج از ایران هستی، شماره خدمات امدادی همان کشور را از قبل در تلفن ذخیره کن.", 12, MUTED, false, 0, 10);
    }

    private void renderKit() {
        sectionTitle("کیف نجات");
        addText("یک‌به‌یک علامت بزن. پیشرفت این فهرست فقط روی همین دستگاه ذخیره می‌شود.", 14, MUTED, false, 0, 14);
        int done = 0;
        for (String key : KIT_KEYS) if (prefs.getBoolean(key, false)) done++;
        TextView progress = text("آماده‌سازی: " + done + " از " + KIT_KEYS.length, 14, TEAL, true);
        content.addView(progress, margin(2, 0, 0, 10));
        LinearLayout list = panel(WHITE, 18);
        for (int i = 0; i < KIT_KEYS.length; i++) {
            final String key = KIT_KEYS[i];
            final String label = KIT_LABELS[i];
            boolean checked = prefs.getBoolean(key, false);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            TextView check = pill(checked ? "✓" : "○", checked ? TEAL_LIGHT : PAPER,
                    checked ? TEAL : MUTED, true);
            row.addView(check, margin(0, 0, 11, 0));
            TextView name = text(label, 14, INK, false);
            name.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
            row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.setPadding(0, dp(9), 0, dp(9));
            row.setOnClickListener(v -> {
                boolean next = !prefs.getBoolean(key, false);
                prefs.edit().putBoolean(key, next).apply();
                int count = 0;
                for (String item : KIT_KEYS) if (prefs.getBoolean(item, false)) count++;
                progress.setText("آماده‌سازی: " + count + " از " + KIT_KEYS.length);
                check.setText(next ? "✓" : "○");
                check.setBackground(round(next ? TEAL_LIGHT : PAPER, 18));
                check.setTextColor(next ? TEAL : MUTED);
            });
            list.addView(row);
            if (i < KIT_KEYS.length - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(LINE);
                list.addView(divider, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
            }
        }
        content.addView(list, margin(0, 0, 0, 14));
        addText("مقدار آب و داروها را با نیاز افراد خانه و توصیه‌های رسمی محل زندگی‌ات تنظیم کن. لازم نیست همه‌چیز را یک‌باره تهیه کنی.", 13, MUTED, false, 0, 12);
        TextView reset = pill("پاک‌کردن علامت‌های فهرست", CORAL_LIGHT, CORAL, true);
        reset.setOnClickListener(v -> {
            for (String key : KIT_KEYS) prefs.edit().putBoolean(key, false).apply();
            renderPage();
        });
        content.addView(reset, margin(0, 0, 0, 12));
        addSourcesButton();
    }

    private void renderPlan() {
        sectionTitle("برنامه خانوادگی");
        addText("اطلاعات را روی همین دستگاه ثبت کن تا هنگام جدایی، قرار و راه تماس مشخص باشد.", 14, MUTED, false, 0, 14);
        LinearLayout form = panel(WHITE, 18);
        EditText meeting = input("محل قرار خانواده", "meeting");
        EditText contact = input("نام و شماره تماس فرد رابط", "contact");
        EditText medical = input("نیاز مهم پزشکی یا داروی ضروری", "medical");
        form.addView(label("محل قرار امن"));
        form.addView(meeting, margin(0, 6, 0, 13));
        form.addView(label("فرد رابط بیرون از محل حادثه"));
        form.addView(contact, margin(0, 6, 0, 13));
        form.addView(label("یادداشت پزشکی ضروری"));
        form.addView(medical, margin(0, 6, 0, 14));
        meeting.setText(prefs.getString("meeting", ""));
        contact.setText(prefs.getString("contact", ""));
        medical.setText(prefs.getString("medical", ""));
        TextView save = button("ذخیره روی همین گوشی", TEAL, WHITE);
        save.setOnClickListener(v -> {
            prefs.edit().putString("meeting", meeting.getText().toString().trim())
                    .putString("contact", contact.getText().toString().trim())
                    .putString("medical", medical.getText().toString().trim()).apply();
            Toast.makeText(this, "برنامه روی همین گوشی ذخیره شد.", Toast.LENGTH_SHORT).show();
        });
        form.addView(save);
        content.addView(form, margin(0, 0, 0, 14));

        sectionTitle("شماره‌های اضطراری ایران");
        addText("این شماره‌ها مربوط به ایران‌اند. خارج از ایران شماره اضطراری محل خودت را بررسی کن.", 13, MUTED, false, 0, 9);
        addDialRow("۱۱۲", "هلال احمر؛ امداد و نجات", "112");
        addDialRow("۱۱۵", "اورژانس پزشکی", "115");
        addDialRow("۱۲۵", "آتش‌نشانی", "125");
        addDialRow("۱۱۰", "پلیس", "110");
        LinearLayout note = panel(TEAL_LIGHT, 16);
        addTo(note, "یادت باشد", 16, TEAL, true, 0, 5);
        addTo(note, "در تماس بگو چه اتفاقی افتاده، چند نفر کمک می‌خواهند و نشانی یا نشانه‌های دقیق محل چیست. تماس اضطراری را فقط برای وضعیت واقعی خطر به‌کار ببر.", 14, INK, false, 0, 0);
        content.addView(note, margin(0, 8, 0, 16));
        addText("برنامه آفلاین است و موقعیت مکانی تو را ارسال یا ذخیره نمی‌کند. اطلاعاتی که در فرم می‌نویسی روی همین دستگاه می‌ماند.", 12, MUTED, false, 0, 12);
        addSourcesButton();
    }

    private void addDialRow(String number, String description, String raw) {
        LinearLayout row = panel(WHITE, 14);
        LinearLayout inner = new LinearLayout(this);
        inner.setGravity(Gravity.CENTER_VERTICAL);
        inner.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.addView(text(number, 18, INK, true));
        textCol.addView(text(description, 12, MUTED, false));
        inner.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView dial = pill("شماره‌گیری", CORAL_LIGHT, CORAL, true);
        dial.setOnClickListener(v -> dial(raw));
        inner.addView(dial);
        row.addView(inner);
        content.addView(row, margin(0, 0, 0, 8));
    }

    private void addSourcesButton() {
        TextView sources = pill("منابع و دربارهٔ برنامه", PAPER, MUTED, true);
        sources.setOnClickListener(v -> showSources());
        content.addView(sources, margin(0, dp(4), 0, dp(8)));
    }

    private void showSources() {
        LinearLayout box = panel(WHITE, 18);
        addTo(box, "منابع راهنمای ایمنی", 20, INK, true, 0, 10);
        addTo(box, "محتوا از راهنماهای FEMA / Ready.gov و جمعیت هلال احمر ایران تنظیم شده است. هشدار زنده، پیش‌بینی یا تشخیص خودکار در این برنامه وجود ندارد. دستور مقام‌های رسمی محل همیشه مقدم است.", 14, MUTED, false, 0, 12);
        addSourceLink(box, "زلزله • Ready.gov", "https://www.ready.gov/earthquakes");
        addSourceLink(box, "سیل • Ready.gov", "https://www.ready.gov/floods");
        addSourceLink(box, "کیف اضطراری • Ready.gov", "https://www.ready.gov/kit");
        addSourceLink(box, "آتش‌سوزی طبیعی • Ready.gov", "https://www.ready.gov/wildfires");
        addSourceLink(box, "طوفان و رعدوبرق • Ready.gov", "https://www.ready.gov/thunderstorms-lightning");
        addSourceLink(box, "رانش زمین • Ready.gov", "https://www.ready.gov/landslides-debris-flow");
        addSourceLink(box, "گرمای شدید • Ready.gov", "https://www.ready.gov/kids/disaster-facts/extreme-heat");
        addSourceLink(box, "شماره ۱۱۲ • هلال احمر ایران", "https://learn.rcs.ir/article/%D8%B4%D9%85%D8%A7%D8%B1%D9%87-%D8%AA%D9%85%D8%A7%D8%B3-112-%D9%85%D8%B1%DA%A9%D8%B2-%D9%BE%D8%A7%D8%B3%D8%AE%DA%AF%D9%88%DB%8C%DB%8C-%D8%A8%D9%87-%D8%AD%D9%88%D8%A7%D8%AF%D8%AB-%D9%88-%D8%B3%D9%88-929519");
        content.addView(box, margin(0, 0, 0, 12));
    }

    private void addSourceLink(LinearLayout target, String label, String url) {
        TextView link = pill(label + "  ↗", TEAL_LIGHT, TEAL, true);
        link.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        link.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ignored) {
                Toast.makeText(this, "مرورگری برای بازکردن پیوند پیدا نشد.", Toast.LENGTH_SHORT).show();
            }
        });
        target.addView(link, margin(0, 0, 0, 7));
    }

    private EditText input(String hint, String key) {
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setTextSize(14);
        edit.setTextColor(INK);
        edit.setHintTextColor(MUTED);
        edit.setHint(hint);
        edit.setInputType(key.equals("medical") ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE : InputType.TYPE_CLASS_TEXT);
        edit.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        edit.setPadding(dp(12), dp(9), dp(12), dp(9));
        edit.setBackground(round(PAPER, 12));
        return edit;
    }

    private TextView label(String value) {
        return text(value, 13, MUTED, true);
    }

    private void addBullets(LinearLayout parent, String heading, String[] items, int accent) {
        TextView title = text(heading, 15, accent, true);
        parent.addView(title, margin(0, dp(4), 0, dp(5)));
        for (String item : items) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.TOP);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            TextView dot = text("•", 18, accent, true);
            row.addView(dot, margin(0, 0, 7, 0));
            TextView body = text(item, 14, INK, false);
            body.setGravity(Gravity.TOP | Gravity.RIGHT);
            row.addView(body, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            parent.addView(row, margin(0, 0, 0, 8));
        }
    }

    private void sectionTitle(String value) {
        addText(value, 20, INK, true, 0, 8);
    }

    private void addText(String value, int size, int color, boolean bold, int top, int bottom) {
        TextView view = text(value, size, color, bold);
        content.addView(view, margin(0, dp(top), 0, dp(bottom)));
    }

    private void addTo(LinearLayout parent, String value, int size, int color, boolean bold, int top, int bottom) {
        TextView view = text(value, size, color, bold);
        parent.addView(view, margin(0, dp(top), 0, dp(bottom)));
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        view.setTextDirection(View.TEXT_DIRECTION_RTL);
        view.setLineSpacing(dp(3), 1.0f);
        view.setIncludeFontPadding(true);
        return view;
    }

    private LinearLayout panel(int color, int radius) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(dp(16), dp(15), dp(16), dp(15));
        box.setBackground(round(color, radius));
        return box;
    }

    private TextView pill(String value, int background, int foreground, boolean bold) {
        TextView view = text(value, 13, foreground, bold);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), dp(9), dp(12), dp(9));
        view.setBackground(round(background, 999));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private TextView button(String value, int background, int foreground) {
        TextView view = text(value, 15, foreground, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(14), dp(13), dp(14), dp(13));
        view.setBackground(round(background, 14));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private LinearLayout.LayoutParams margin(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void dial(String number) {
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number)));
        } catch (Exception e) {
            Toast.makeText(this, "امکان بازکردن شماره‌گیر وجود ندارد.", Toast.LENGTH_LONG).show();
        }
    }
}
