package com.replyai.app;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.core.app.NotificationCompat;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * FloatingService - shows a small draggable bubble on screen.
 * When tapped: opens a bottom panel with auto-filled tweet text,
 * tone/recipient chips, and a Generate button.
 * Uses claude-haiku (fastest model) for near-instant replies.
 */
public class FloatingService extends Service {

    private WindowManager wm;
    private FrameLayout bubbleView;
    private View panelView;
    private WindowManager.LayoutParams bubbleParams;
    private boolean panelOpen = false;

    private String apiKey;
    private String selectedTone = "Casual";
    private String selectedRecipient = "Friend";
    private String autoTweetText = "";   // filled by accessibility service

    private static final String CHANNEL_ID = "replyai_overlay";
    private static final String[] TONES = {"Casual","Playful","Humorous","Supportive","Professional","Romantic","Flirty","Sarcastic","Neutral"};
    private static final String[] RECIPIENTS = {"Friend","Boss","Crush","Family","Colleague","Stranger"};

    // Receives tweet text broadcast from ReplyAccessibilityService
    private final BroadcastReceiver tweetReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            String tweet = intent.getStringExtra(ReplyAccessibilityService.EXTRA_TWEET_TEXT);
            if (tweet != null && !tweet.isEmpty()) {
                autoTweetText = tweet;
                // If panel is open, update the text field
                if (panelOpen && panelView != null) {
                    EditText et = panelView.findViewWithTag("msgInput");
                    if (et != null && et.getText().toString().isEmpty()) {
                        et.setText(tweet);
                    }
                }
                // Flash bubble to show tweet was detected
                flashBubble();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        apiKey = getSharedPreferences("replyai", MODE_PRIVATE).getString("api_key", "");
        createNotificationChannel();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, buildNotification());
        }
        createBubble();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(tweetReceiver,
                new IntentFilter(ReplyAccessibilityService.ACTION_TWEET_DETECTED), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(tweetReceiver,
                new IntentFilter(ReplyAccessibilityService.ACTION_TWEET_DETECTED));
        }
    }

    // ──────────────────────────────────────────────
    //  NOTIFICATION
    // ──────────────────────────────────────────────
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Reply AI Overlay", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        Intent stop = new Intent(this, FloatingService.class);
        stop.setAction("STOP");
        PendingIntent pi = PendingIntent.getService(this, 0, stop,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Reply AI is active")
            .setContentText("Open Twitter/X — bubble will auto-read tweets")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .addAction(android.R.drawable.ic_delete, "Stop", pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    // ──────────────────────────────────────────────
    //  BUBBLE
    // ──────────────────────────────────────────────
    private void createBubble() {
        bubbleView = new FrameLayout(this);
        int size = dp(58);

        // Purple circle
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.parseColor("#5B4FE8"));
        bg.setStroke(dp(2), Color.WHITE);
        bubbleView.setBackground(bg);
        bubbleView.setElevation(dp(10));

        // Emoji label
        TextView icon = new TextView(this);
        icon.setText("✨");
        icon.setTextSize(22);
        icon.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(size, size);
        bubbleView.addView(icon, iconLp);

        bubbleParams = new WindowManager.LayoutParams(
            size, size,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = dp(16);
        bubbleParams.y = dp(220);

        setupDragAndClick();
        wm.addView(bubbleView, bubbleParams);
    }

    private void flashBubble() {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (bubbleView == null) return;
            GradientDrawable flash = new GradientDrawable();
            flash.setShape(GradientDrawable.OVAL);
            flash.setColor(Color.parseColor("#22C55E")); // green flash
            flash.setStroke(dp(2), Color.WHITE);
            bubbleView.setBackground(flash);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (bubbleView == null) return;
                GradientDrawable normal = new GradientDrawable();
                normal.setShape(GradientDrawable.OVAL);
                normal.setColor(Color.parseColor("#5B4FE8"));
                normal.setStroke(dp(2), Color.WHITE);
                bubbleView.setBackground(normal);
            }, 600);
        });
    }

    private void setupDragAndClick() {
        final int[] initX = {0}, initY = {0};
        final int[] initTouchX = {0}, initTouchY = {0};
        final boolean[] moved = {false};

        bubbleView.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initX[0] = bubbleParams.x;
                    initY[0] = bubbleParams.y;
                    initTouchX[0] = (int) e.getRawX();
                    initTouchY[0] = (int) e.getRawY();
                    moved[0] = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    int nx = initX[0] + (int) e.getRawX() - initTouchX[0];
                    int ny = initY[0] + (int) e.getRawY() - initTouchY[0];
                    if (Math.abs(nx - initX[0]) > 8 || Math.abs(ny - initY[0]) > 8) moved[0] = true;
                    bubbleParams.x = nx;
                    bubbleParams.y = ny;
                    wm.updateViewLayout(bubbleView, bubbleParams);
                    break;
                case MotionEvent.ACTION_UP:
                    if (!moved[0]) {
                        if (panelOpen) closePanel();
                        else openPanel();
                    }
                    break;
            }
            return true;
        });
    }

    // ──────────────────────────────────────────────
    //  PANEL
    // ──────────────────────────────────────────────
    private void openPanel() {
        panelOpen = true;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(20));
        root.setBackground(roundRect(Color.WHITE, dp(22)));
        root.setElevation(dp(16));

        // ── Header row ──
        LinearLayout hdr = hrow();
        TextView title = tv("✨ Reply AI", 17, Color.parseColor("#5B4FE8"), true);
        hdr.addView(title, new LinearLayout.LayoutParams(0, WRAP, 1f));

        // Detected tweet badge
        TextView badge = tv("", 11, Color.parseColor("#22C55E"), true);
        if (!autoTweetText.isEmpty()) badge.setText("● Tweet detected");
        hdr.addView(badge);

        Button xBtn = chip("✕", false);
        xBtn.setOnClickListener(v -> closePanel());
        hdr.addView(xBtn);
        root.addView(hdr);
        root.addView(divider());

        // ── Message input ──
        root.addView(lbl("Tweet / Message:"));
        EditText etMsg = new EditText(this);
        etMsg.setTag("msgInput");
        etMsg.setHint("Auto-filled from screen or paste manually...");
        etMsg.setHintTextColor(Color.parseColor("#9090A8"));
        etMsg.setTextColor(Color.parseColor("#1A1A2E"));
        etMsg.setTextSize(13);
        etMsg.setBackground(roundRect(Color.parseColor("#F5F5FA"), dp(12)));
        etMsg.setPadding(dp(12), dp(10), dp(12), dp(10));
        etMsg.setMinLines(2);
        etMsg.setMaxLines(5);
        etMsg.setGravity(Gravity.TOP);
        if (!autoTweetText.isEmpty()) etMsg.setText(autoTweetText);
        root.addView(etMsg);
        root.addView(space(4));

        // Paste button
        LinearLayout pasteRow = new LinearLayout(this);
        pasteRow.setOrientation(LinearLayout.HORIZONTAL);
        pasteRow.setGravity(Gravity.END);
        Button pasteBtn = chip("📋 Paste", false);
        pasteBtn.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() != false && cm.getPrimaryClip() != null) {
                CharSequence txt = cm.getPrimaryClip().getItemAt(0).getText();
                if (txt != null) { etMsg.setText(txt); etMsg.setSelection(txt.length()); }
            }
        });
        pasteRow.addView(pasteBtn);
        root.addView(pasteRow);
        root.addView(space(10));

        // ── Recipient ──
        root.addView(lbl("Reply to:"));
        root.addView(chipScroll(RECIPIENTS, selectedRecipient, chosen -> selectedRecipient = chosen));
        root.addView(space(10));

        // ── Tone ──
        root.addView(lbl("Tone:"));
        root.addView(chipScroll(TONES, selectedTone, chosen -> selectedTone = chosen));
        root.addView(space(14));

        // ── Generate button ──
        Button genBtn = new Button(this);
        genBtn.setText("⚡ Generate 3 Replies");
        genBtn.setAllCaps(false);
        genBtn.setTextSize(15);
        genBtn.setTypeface(null, Typeface.BOLD);
        genBtn.setBackground(roundRect(Color.parseColor("#5B4FE8"), dp(14)));
        genBtn.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams genLp = new LinearLayout.LayoutParams(MATCH, dp(50));
        genBtn.setLayoutParams(genLp);
        root.addView(genBtn);
        root.addView(space(10));

        // ── Results ──
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        root.addView(results);

        genBtn.setOnClickListener(v -> {
            String msg = etMsg.getText().toString().trim();
            if (msg.isEmpty()) { toast("Paste a tweet or message first"); return; }

            genBtn.setEnabled(false);
            genBtn.setText("Generating...");
            results.removeAllViews();

            ProgressBar pb = new ProgressBar(this);
            pb.setIndeterminate(true);
            LinearLayout pbRow = new LinearLayout(this);
            pbRow.setGravity(Gravity.CENTER);
            pbRow.addView(pb);
            results.addView(pbRow);

            generate(msg, selectedRecipient, selectedTone, replies -> {
                new Handler(Looper.getMainLooper()).post(() -> {
                    results.removeAllViews();
                    genBtn.setEnabled(true);
                    genBtn.setText("⚡ Generate 3 Replies");
                    if (replies == null || replies.isEmpty()) {
                        results.addView(errView("Failed. Check your API key in the app."));
                        return;
                    }
                    TextView rl = tv("✨ Generated Replies", 12, Color.parseColor("#9090A8"), true);
                    rl.setPadding(0, 0, 0, dp(6));
                    results.addView(rl);
                    for (String r : replies) {
                        results.addView(replyCard(r));
                        results.addView(space(8));
                    }
                });
            });
        });

        scroll.addView(root);
        panelView = scroll;

        int screenW = getResources().getDisplayMetrics().widthPixels;
        WindowManager.LayoutParams pp = new WindowManager.LayoutParams(
            (int)(screenW * 0.94f), WRAP,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        );
        pp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        pp.y = dp(72);
        pp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        wm.addView(panelView, pp);
    }

    private void closePanel() {
        if (panelView != null) {
            try { wm.removeView(panelView); } catch (Exception ignored) {}
            panelView = null;
        }
        panelOpen = false;
    }

    // ──────────────────────────────────────────────
    //  REPLY CARD
    // ──────────────────────────────────────────────
    private View replyCard(String text) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundRect(Color.parseColor("#F5F5FF"), dp(14)));
        card.setPadding(dp(14), dp(12), dp(14), dp(10));

        // Reply text
        TextView tv = tv(text, 13, Color.parseColor("#1A1A2E"), false);
        tv.setLineSpacing(dp(3), 1f);
        card.addView(tv);
        card.addView(space(10));

        // Action row
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);

        // Copy — main action
        Button copy = new Button(this);
        copy.setText("📋 Copy Reply");
        copy.setAllCaps(false);
        copy.setTextSize(13);
        copy.setTypeface(null, Typeface.BOLD);
        copy.setBackground(roundRect(Color.parseColor("#5B4FE8"), dp(10)));
        copy.setTextColor(Color.WHITE);
        copy.setPadding(dp(14), dp(6), dp(14), dp(6));
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("reply", text));
            toast("✅ Copied! Now paste in Twitter reply box");
            copy.setText("✅ Copied!");
            copy.setBackground(roundRect(Color.parseColor("#22C55E"), dp(10)));
        });

        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(WRAP, dp(38));
        copy.setLayoutParams(copyLp);
        row.addView(copy);

        card.addView(row);
        return card;
    }

    // ──────────────────────────────────────────────
    //  AI GENERATION  (claude-haiku = fastest)
    // ──────────────────────────────────────────────
    interface CB { void done(List<String> r); }

    private void generate(String msg, String recipient, String tone, CB cb) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String prompt =
                    "You are a reply assistant. Generate exactly 3 humanized, engaging replies.\n\n" +
                    "Tweet/Message:\n\"" + msg + "\"\n\n" +
                    "Replying to: " + recipient + "\n" +
                    "Tone: " + tone + "\n\n" +
                    "Rules:\n" +
                    "- Sound like a real person, not AI\n" +
                    "- Great English, natural flow\n" +
                    "- Under 200 characters each\n" +
                    "- All 3 must be different in style and wording\n" +
                    "- No hashtags unless naturally appropriate\n" +
                    "- Be engaging, not generic\n\n" +
                    "Respond ONLY with JSON array: [\"reply1\",\"reply2\",\"reply3\"]";

                JSONObject body = new JSONObject();
                body.put("model", "claude-3-haiku-20240307");
                body.put("max_tokens", 500);
                JSONArray msgs = new JSONArray();
                JSONObject m = new JSONObject();
                m.put("role", "user"); m.put("content", prompt);
                msgs.put(m);
                body.put("messages", msgs);

                URL url = new URL("https://api.anthropic.com/v1/messages");
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "application/json");
                c.setRequestProperty("x-api-key", apiKey);
                c.setRequestProperty("anthropic-version", "2023-06-01");
                c.setDoOutput(true);
                c.setConnectTimeout(6000);
                c.setReadTimeout(12000);

                try (OutputStream os = c.getOutputStream()) {
                    os.write(body.toString().getBytes("UTF-8"));
                }

                int code = c.getResponseCode();
                InputStream is = code == 200 ? c.getInputStream() : c.getErrorStream();
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
                    String line; while ((line = br.readLine()) != null) sb.append(line);
                }

                JSONObject resp = new JSONObject(sb.toString());
                if (!resp.has("content")) {
                    cb.done(null);
                    return;
                }
                String content = resp.getJSONArray("content").getJSONObject(0).getString("text").trim();
                int s = content.indexOf('['), e = content.lastIndexOf(']') + 1;
                if (s == -1 || e == 0) {
                    cb.done(null);
                    return;
                }
                JSONArray arr = new JSONArray(content.substring(s, e));
                List<String> out = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) out.add(arr.getString(i));
                cb.done(out);

            } catch (Exception ex) { cb.done(null); }
        });
    }

    // ──────────────────────────────────────────────
    //  UI HELPERS
    // ──────────────────────────────────────────────
    private static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;

    interface OnChip { void pick(String v); }

    private HorizontalScrollView chipScroll(String[] opts, String sel, OnChip cb) {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(2), 0, dp(4));
        List<Button> btns = new ArrayList<>();

        for (String o : opts) {
            Button b = chip(o, o.equals(sel));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, dp(34));
            lp.setMarginEnd(dp(6));
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> {
                cb.pick(o);
                for (Button x : btns) { x.setBackground(roundRect(Color.WHITE, dp(50))); x.setTextColor(Color.parseColor("#333333")); }
                b.setBackground(roundRect(Color.parseColor("#5B4FE8"), dp(50)));
                b.setTextColor(Color.WHITE);
            });
            btns.add(b);
            row.addView(b);
        }
        hsv.addView(row);
        return hsv;
    }

    private Button chip(String text, boolean active) {
        Button b = new Button(this);
        b.setText(text); b.setAllCaps(false); b.setTextSize(12);
        b.setBackground(roundRect(active ? Color.parseColor("#5B4FE8") : Color.WHITE, dp(50)));
        b.setTextColor(active ? Color.WHITE : Color.parseColor("#333333"));
        b.setPadding(dp(12), dp(4), dp(12), dp(4));
        return b;
    }

    private TextView tv(String text, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private TextView lbl(String text) {
        TextView t = tv(text, 12, Color.parseColor("#333333"), true);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private LinearLayout hrow() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(0, 0, 0, dp(6));
        return l;
    }

    private View divider() {
        View v = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, dp(1));
        lp.setMargins(0, dp(4), 0, dp(10));
        v.setLayoutParams(lp);
        v.setBackgroundColor(Color.parseColor("#EEEEEE"));
        return v;
    }

    private View space(int d) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(MATCH, dp(d)));
        return v;
    }

    private View errView(String msg) {
        TextView t = tv(msg, 13, Color.RED, false);
        t.setPadding(0, dp(8), 0, dp(8));
        return t;
    }

    private GradientDrawable roundRect(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(color); g.setCornerRadius(radius);
        return g;
    }

    private int dp(int val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }

    private void toast(String msg) {
        new Handler(Looper.getMainLooper()).post(() ->
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    // ──────────────────────────────────────────────
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) stopSelf();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(tweetReceiver); } catch (Exception ignored) {}
        try { if (bubbleView != null) wm.removeView(bubbleView); } catch (Exception ignored) {}
        closePanel();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
