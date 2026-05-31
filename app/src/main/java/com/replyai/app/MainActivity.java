package com.replyai.app;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.method.PasswordTransformationMethod;
import android.view.*;
import android.view.accessibility.AccessibilityManager;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private EditText etKey;
    private TextView tvKeyStatus, tvOverlayStatus, tvAccessStatus;
    private static final int REQ_OVERLAY = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("replyai", MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFFF5F5FA);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(30), dp(20), dp(40));

        // Title
        TextView title = new TextView(this);
        title.setText("✨ Reply AI");
        title.setTextSize(28);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(0xFF5B4FE8);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(4));
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("AI replies for Twitter/X in seconds");
        sub.setTextSize(13);
        sub.setTextColor(0xFF9090A8);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 0, 0, dp(24));
        root.addView(sub);

        // ── Step 1: API Key ──
        root.addView(stepCard("Step 1 — Anthropic API Key",
            "Get a free key at console.anthropic.com", () -> {
                LinearLayout inner = new LinearLayout(this);
                inner.setOrientation(LinearLayout.VERTICAL);

                etKey = new EditText(this);
                etKey.setHint("sk-ant-...");
                etKey.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                etKey.setBackground(roundRect(0xFFEEEEFF, dp(10)));
                etKey.setPadding(dp(12), dp(10), dp(12), dp(10));
                etKey.setTextColor(0xFF1A1A2E);
                String saved = prefs.getString("api_key", "");
                if (!saved.isEmpty()) etKey.setText(saved);
                inner.addView(etKey);
                inner.addView(space(8));

                LinearLayout btnRow = new LinearLayout(this);
                btnRow.setOrientation(LinearLayout.HORIZONTAL);
                btnRow.setGravity(Gravity.END);

                Button save = btn("💾 Save Key", 0xFF5B4FE8, 0xFFFFFFFF);
                save.setOnClickListener(v -> {
                    String k = etKey.getText().toString().trim();
                    if (k.isEmpty()) { toast("Enter your API key first"); return; }
                    prefs.edit().putString("api_key", k).apply();
                    toast("✅ API Key saved!");
                    updateStatuses();
                });

                Button open = btn("🌐 Get Key", 0xFFEEEEFF, 0xFF5B4FE8);
                open.setOnClickListener(v -> {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://console.anthropic.com")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                });

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, dp(40));
                lp.setMarginStart(dp(8));
                open.setLayoutParams(lp);
                save.setLayoutParams(new LinearLayout.LayoutParams(WRAP, dp(40)));

                btnRow.addView(open);
                btnRow.addView(save);
                inner.addView(btnRow);
                inner.addView(space(8));

                tvKeyStatus = new TextView(this);
                tvKeyStatus.setTextSize(12);
                inner.addView(tvKeyStatus);
                return inner;
            }
        ));

        root.addView(space(12));

        // ── Step 2: Overlay permission ──
        root.addView(stepCard("Step 2 — Floating Window",
            "Allow Reply AI to show a bubble over other apps", () -> {
                LinearLayout inner = new LinearLayout(this);
                inner.setOrientation(LinearLayout.VERTICAL);
                tvOverlayStatus = new TextView(this);
                tvOverlayStatus.setTextSize(12);
                inner.addView(tvOverlayStatus);
                inner.addView(space(8));

                Button b = btn("🔓 Grant Overlay Permission", 0xFF5B4FE8, 0xFFFFFFFF);
                b.setOnClickListener(v -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        startActivityForResult(new Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())), REQ_OVERLAY);
                    }
                });
                inner.addView(b);
                return inner;
            }
        ));

        root.addView(space(12));

        // ── Step 3: Accessibility ──
        root.addView(stepCard("Step 3 — Screen Reader",
            "Allows the app to read tweet text automatically from Twitter/X", () -> {
                LinearLayout inner = new LinearLayout(this);
                inner.setOrientation(LinearLayout.VERTICAL);

                TextView note = new TextView(this);
                note.setText("⚠️ In the next screen, find 'Reply AI Screen Reader' and enable it. Your data is never stored or sent anywhere except the AI API.");
                note.setTextSize(12);
                note.setTextColor(0xFF666666);
                note.setLineSpacing(dp(3), 1f);
                inner.addView(note);
                inner.addView(space(8));

                tvAccessStatus = new TextView(this);
                tvAccessStatus.setTextSize(12);
                inner.addView(tvAccessStatus);
                inner.addView(space(8));

                Button b = btn("♿ Open Accessibility Settings", 0xFF5B4FE8, 0xFFFFFFFF);
                b.setOnClickListener(v -> startActivity(
                    new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
                inner.addView(b);
                return inner;
            }
        ));

        root.addView(space(20));

        // ── Start button ──
        Button startBtn = new Button(this);
        startBtn.setText("▶ Start Floating Bubble");
        startBtn.setAllCaps(false);
        startBtn.setTextSize(16);
        startBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        startBtn.setBackground(roundRect(0xFF5B4FE8, dp(14)));
        startBtn.setTextColor(0xFFFFFFFF);
        startBtn.setElevation(dp(6));
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(MATCH, dp(54));
        startBtn.setLayoutParams(startLp);
        startBtn.setOnClickListener(v -> {
            if (prefs.getString("api_key","").isEmpty()) { toast("Save your API key first (Step 1)"); return; }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                toast("Grant overlay permission first (Step 2)"); return;
            }
            ContextCompat.startForegroundService(this, new Intent(this, FloatingService.class));
            toast("✅ Bubble started! Open Twitter/X now.");
            moveTaskToBack(true);
        });
        root.addView(startBtn);

        root.addView(space(8));

        Button stopBtn = new Button(this);
        stopBtn.setText("⏹ Stop Bubble");
        stopBtn.setAllCaps(false);
        stopBtn.setTextSize(14);
        stopBtn.setBackground(roundRect(0xFFEEEEEE, dp(14)));
        stopBtn.setTextColor(0xFF666666);
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(MATCH, dp(44));
        stopBtn.setLayoutParams(stopLp);
        stopBtn.setOnClickListener(v -> {
            stopService(new Intent(this, FloatingService.class));
            toast("Bubble stopped");
        });
        root.addView(stopBtn);

        root.addView(space(12));
        TextView hint = new TextView(this);
        hint.setText("💡 How it works:\n1. Start the bubble\n2. Open Twitter/X\n3. Tap a tweet's reply button (so you're on the reply screen)\n4. Tap the ✨ bubble — tweet is auto-filled!\n5. Pick tone → Generate → Copy → Paste");
        hint.setTextSize(12);
        hint.setTextColor(0xFF666666);
        hint.setLineSpacing(dp(3), 1f);
        hint.setBackground(roundRect(0xFFEEEEFF, dp(12)));
        hint.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.addView(hint);

        scroll.addView(root);
        setContentView(scroll);
    }

    interface CardContent { LinearLayout make(); }

    private View stepCard(String title, String desc, CardContent content) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundRect(0xFFFFFFFF, dp(16)));
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setElevation(dp(4));

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(15);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setTextColor(0xFF1A1A2E);
        card.addView(t);

        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextSize(12);
        d.setTextColor(0xFF9090A8);
        d.setPadding(0, dp(2), 0, dp(10));
        card.addView(d);

        card.addView(content.make());
        return card;
    }

    private void updateStatuses() {
        if (tvKeyStatus != null) {
            boolean hasKey = !prefs.getString("api_key","").isEmpty();
            tvKeyStatus.setText(hasKey ? "✅ API Key connected" : "⚠️ Not set");
            tvKeyStatus.setTextColor(hasKey ? 0xFF22C55E : 0xFFFF9800);
        }
        if (tvOverlayStatus != null) {
            boolean ok = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
            tvOverlayStatus.setText(ok ? "✅ Overlay permission granted" : "⚠️ Not granted yet");
            tvOverlayStatus.setTextColor(ok ? 0xFF22C55E : 0xFFFF9800);
        }
        if (tvAccessStatus != null) {
            boolean ok = isAccessibilityEnabled();
            tvAccessStatus.setText(ok ? "✅ Screen reader enabled" : "⚠️ Not enabled yet");
            tvAccessStatus.setTextColor(ok ? 0xFF22C55E : 0xFFFF9800);
        }
    }

    private boolean isAccessibilityEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (am == null) return false;
        List<AccessibilityServiceInfo> services = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo info : services) {
            if (info.getId().contains(getPackageName())) return true;
        }
        return false;
    }

    @Override
    protected void onResume() { super.onResume(); updateStatuses(); }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_OVERLAY) updateStatuses();
    }

    private android.graphics.drawable.GradientDrawable roundRect(int color, int radius) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        g.setColor(color); g.setCornerRadius(radius);
        return g;
    }

    private Button btn(String text, int bg, int fg) {
        Button b = new Button(this);
        b.setText(text); b.setAllCaps(false); b.setTextSize(13);
        b.setBackground(roundRect(bg, dp(10))); b.setTextColor(fg);
        b.setPadding(dp(14), dp(6), dp(14), dp(6));
        return b;
    }

    private View space(int d) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(MATCH, dp(d)));
        return v;
    }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;
}
