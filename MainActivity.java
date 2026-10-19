package com.psshop.arabic;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_BUILTIN_TONE = 4101;
    private static final int REQ_PHONE_TONE = 4102;

    private SharedPreferences prefs;
    private LinearLayout main;
    private final ArrayList<Device> devices = new ArrayList<>();
    private MediaPlayer alarmPlayer;
    private android.media.ToneGenerator fallbackTone;

    private static class Device {
        String name;
        boolean match;
        long left = 420000;
        long durationMs = 420000;
        int half = 1;
        boolean running = false;
        TextView timeView, statusView;
        Button startButton, halfButton, durationButton;
        CountDownTimer timer;

        Device(String n, boolean m) {
            name = n;
            match = m;
        }
    }

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("psshop", MODE_PRIVATE);
        if (!prefs.getBoolean("passwordSet", false)) createPassword();
        else askPassword();
    }

    private void createPassword() {
        final EditText p = new EditText(this);
        p.setHint("أنشئ كلمة مرور");
        p.setInputType(129);
        new AlertDialog.Builder(this).setTitle("حماية البرنامج")
            .setMessage("أنشئ كلمة مرور للبرنامج").setView(p).setCancelable(false)
            .setPositiveButton("حفظ", (d, w) -> {
                String v = p.getText().toString();
                if (v.trim().length() < 4) {
                    Toast.makeText(this, "اجعل كلمة المرور 4 أحرف أو أرقام على الأقل", Toast.LENGTH_LONG).show();
                    createPassword();
                } else {
                    prefs.edit().putString("password", v).putBoolean("passwordSet", true).apply();
                    showApp();
                }
            }).show();
    }

    private void askPassword() {
        final EditText p = new EditText(this);
        p.setHint("كلمة المرور");
        p.setInputType(129);
        new AlertDialog.Builder(this).setTitle("تسجيل الدخول").setView(p).setCancelable(false)
            .setPositiveButton("دخول", (d, w) -> {
                if (p.getText().toString().equals(prefs.getString("password", ""))) showApp();
                else {
                    Toast.makeText(this, "كلمة المرور غير صحيحة", Toast.LENGTH_LONG).show();
                    askPassword();
                }
            }).show();
    }

    private void showApp() {
        main = new LinearLayout(this);
        main.setOrientation(1);
        main.setPadding(20, 18, 20, 20);
        main.setBackgroundColor(Color.rgb(245, 247, 250));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(main);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("🎮 إدارة محل البلايستيشن");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(25, 45, 75));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 12, 0, 20);
        main.addView(title);

        TextView note = new TextView(this);
        note.setText("المباريات: 7 دقائق افتراضيًا • يمكنك تغيير مدة كل جهاز");
        note.setTextSize(14);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, 0, 0, 14);
        main.addView(note);

        devices.clear();
        for (int i = 1; i <= 6; i++) {
            boolean match = (i == 1 || i == 5);
            String defaultName = match ? "جهاز " + i + " - مباريات" : "جهاز " + i;
            Device device = new Device(prefs.getString("name" + i, defaultName), match);
            long savedDuration = prefs.getLong("duration" + i, 420000L);
            if (savedDuration >= 60000L && savedDuration <= 86400000L) {
                device.durationMs = savedDuration;
                device.left = savedDuration;
            }
            devices.add(device);
        }
        for (int i = 0; i < devices.size(); i++) addDeviceCard(devices.get(i), i + 1);

        Button add = new Button(this);
        add.setText("＋ إضافة جهاز");
        add.setOnClickListener(v -> addDevice());
        main.addView(add);

        Button alarmSettings = new Button(this);
        alarmSettings.setText("🔔 إعدادات نغمة المنبّه");
        alarmSettings.setOnClickListener(v -> showAlarmSettings());
        main.addView(alarmSettings);

        Button settings = new Button(this);
        settings.setText("⚙ تغيير كلمة المرور");
        settings.setOnClickListener(v -> changePassword());
        main.addView(settings);

        Button reset = new Button(this);
        reset.setText("إعادة ضبط مؤقتات الأجهزة");
        reset.setOnClickListener(v -> {
            for (Device d : devices) {
                if (d.timer != null) d.timer.cancel();
                d.running = false;
                d.half = 1;
                d.left = d.durationMs;
                refresh(d);
            }
            Toast.makeText(this, "تمت إعادة ضبط المؤقتات", Toast.LENGTH_SHORT).show();
        });
        main.addView(reset);
    }

    private void addDeviceCard(Device d, int id) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(1);
        card.setPadding(16, 10, 16, 12);
        card.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(0, 0, 0, 14);
        main.addView(card, cp);

        TextView name = new TextView(this);
        name.setText(d.name + (d.match ? "  ⚽" : ""));
        name.setTextSize(19);
        name.setTextColor(Color.rgb(20, 40, 70));
        card.addView(name);

        d.timeView = new TextView(this);
        d.timeView.setTextSize(27);
        d.timeView.setGravity(Gravity.CENTER);
        card.addView(d.timeView);

        d.statusView = new TextView(this);
        d.statusView.setGravity(Gravity.CENTER);
        card.addView(d.statusView);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(0);
        card.addView(row);

        d.startButton = new Button(this);
        d.startButton.setText("ابدأ");
        row.addView(d.startButton, new LinearLayout.LayoutParams(0, -2, 1));

        Button rename = new Button(this);
        rename.setText("تغيير الاسم");
        row.addView(rename, new LinearLayout.LayoutParams(0, -2, 1));

        d.durationButton = new Button(this);
        d.durationButton.setText("تحديد المدة");
        card.addView(d.durationButton);

        d.halfButton = new Button(this);
        d.halfButton.setText("بدء الشوط الثاني");
        if (d.match) card.addView(d.halfButton);

        d.startButton.setOnClickListener(v -> {
            if (d.running) {
                if (d.timer != null) d.timer.cancel();
                d.running = false;
                refresh(d);
            } else startTimer(d);
        });
        rename.setOnClickListener(v -> renameDevice(d, id, name));
        d.durationButton.setOnClickListener(v -> setDuration(d, id));
        d.halfButton.setOnClickListener(v -> {
            if (d.timer != null) d.timer.cancel();
            d.running = false;
            d.half = 2;
            d.left = d.durationMs;
            startTimer(d);
        });
        refresh(d);
    }

    private void refresh(Device d) {
        if (d.timeView == null) return;
        long s = d.left / 1000;
        d.timeView.setText(String.format(Locale.getDefault(), "%02d:%02d", s / 60, s % 60));
        d.statusView.setText(d.running ? "الحالة: مشغول" : "الحالة: متاح / متوقف");
        d.startButton.setText(d.running ? "إيقاف مؤقت" : "ابدأ الوقت");
        d.durationButton.setText("تحديد المدة (" + (d.durationMs / 60000L) + " دقيقة)");
        if (d.match) d.halfButton.setEnabled(!d.running && d.half == 1);
    }

    private void startTimer(Device d) {
        if (d.left <= 0) d.left = d.durationMs;
        d.running = true;
        refresh(d);
        d.timer = new CountDownTimer(d.left, 1000) {
            public void onTick(long ms) {
                d.left = ms;
                refresh(d);
            }
            public void onFinish() {
                d.left = 0;
                d.running = false;
                refresh(d);
                alertEnd(d);
            }
        }.start();
    }

    private void setDuration(Device d, int id) {
        final EditText input = new EditText(this);
        input.setHint("المدة بالدقائق (مثال: 7)");
        input.setInputType(2);
        input.setText(String.valueOf(d.durationMs / 60000L));
        new AlertDialog.Builder(this).setTitle("تحديد مدة اللعب")
            .setMessage("أدخل عدد الدقائق من 1 إلى 1440. تقبل الأرقام العربية والإنجليزية.")
            .setView(input)
            .setPositiveButton("حفظ", (dialog, which) -> {
                String digits = normalizeDigits(input.getText().toString()).trim();
                try {
                    int minutes = Integer.parseInt(digits);
                    if (minutes < 1 || minutes > 1440) {
                        Toast.makeText(this, "أدخل مدة من دقيقة واحدة إلى 1440 دقيقة", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (d.timer != null) d.timer.cancel();
                    d.running = false;
                    d.durationMs = minutes * 60000L;
                    d.left = d.durationMs;
                    prefs.edit().putLong("duration" + id, d.durationMs).apply();
                    refresh(d);
                } catch (NumberFormatException ex) {
                    Toast.makeText(this, "أدخل أرقامًا صحيحة فقط", Toast.LENGTH_LONG).show();
                }
            }).setNegativeButton("إلغاء", null).show();
    }

    private String normalizeDigits(String value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '\u0660' && c <= '\u0669') c = (char) ('0' + (c - '\u0660'));
            else if (c >= '\u06F0' && c <= '\u06F9') c = (char) ('0' + (c - '\u06F0'));
            result.append(c);
        }
        return result.toString();
    }

    private void showAlarmSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(1);
        box.setPadding(24, 8, 24, 4);

        CheckBox soundEnabled = new CheckBox(this);
        soundEnabled.setText("تشغيل صوت المنبّه");
        soundEnabled.setChecked(prefs.getBoolean("alarmSoundEnabled", true));
        box.addView(soundEnabled);

        TextView volumeLabel = new TextView(this);
        volumeLabel.setText("مستوى صوت المنبّه داخل التطبيق");
        box.addView(volumeLabel);

        SeekBar volume = new SeekBar(this);
        volume.setMax(100);
        volume.setProgress(prefs.getInt("alarmVolume", 100));
        box.addView(volume);

        CheckBox vibration = new CheckBox(this);
        vibration.setText("الاهتزاز عند انتهاء الوقت");
        vibration.setChecked(prefs.getBoolean("alarmVibration", true));
        box.addView(vibration);

        Button builtIn = new Button(this);
        builtIn.setText("اختيار نغمة من نغمات الهاتف");
        builtIn.setOnClickListener(v -> chooseBuiltInTone());
        box.addView(builtIn);

        Button phoneFile = new Button(this);
        phoneFile.setText("اختيار ملف صوتي من الهاتف");
        phoneFile.setOnClickListener(v -> choosePhoneTone());
        box.addView(phoneFile);

        String saved = prefs.getString("alarmUri", "");
        TextView current = new TextView(this);
        current.setPadding(0, 8, 0, 4);
        current.setText(saved.isEmpty() ? "النغمة الحالية: نغمة المنبّه الافتراضية" :
            ("النغمة الحالية: " + ("phone".equals(prefs.getString("alarmSource", "")) ? "ملف من الهاتف" : "نغمة مختارة")));
        box.addView(current);

        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("إعدادات المنبّه")
            .setView(box).setPositiveButton("حفظ الإعدادات", null)
            .setNegativeButton("إلغاء", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w -> {
            prefs.edit()
                .putBoolean("alarmSoundEnabled", soundEnabled.isChecked())
                .putInt("alarmVolume", volume.getProgress())
                .putBoolean("alarmVibration", vibration.isChecked())
                .apply();
            Toast.makeText(this, "تم حفظ إعدادات المنبّه", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void chooseBuiltInTone() {
        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "اختر نغمة المنبّه");
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        String existing = prefs.getString("alarmUri", "");
        if (!existing.isEmpty()) intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(existing));
        startActivityForResult(intent, REQ_BUILTIN_TONE);
    }

    private void choosePhoneTone() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        try {
            startActivityForResult(intent, REQ_PHONE_TONE);
        } catch (Exception ex) {
            Toast.makeText(this, "تعذر فتح ملفات الصوت في هذا الهاتف", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == REQ_BUILTIN_TONE) {
            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            SharedPreferences.Editor editor = prefs.edit().putString("alarmSource", "builtin");
            if (uri == null) editor.putString("alarmUri", "");
            else editor.putString("alarmUri", uri.toString());
            editor.apply();
            Toast.makeText(this, "تم اختيار نغمة الهاتف", Toast.LENGTH_SHORT).show();
        } else if (requestCode == REQ_PHONE_TONE) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) { }
                prefs.edit().putString("alarmUri", uri.toString()).putString("alarmSource", "phone").apply();
                Toast.makeText(this, "تم اختيار الملف الصوتي", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void alertEnd(Device d) {
        if (prefs.getBoolean("alarmVibration", true)) {
            try {
                Vibrator vb = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (vb != null && vb.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= 26) vb.vibrate(VibrationEffect.createOneShot(900, VibrationEffect.DEFAULT_AMPLITUDE));
                    else vb.vibrate(900);
                }
            } catch (Exception ignored) { }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("انتهى الوقت")
            .setMessage(d.name + " — انتهى الشوط " + d.half)
            .setPositiveButton("إيقاف المنبّه", (x, y) -> stopAlarm())
            .setCancelable(false)
            .create();
        dialog.setOnDismissListener(x -> stopAlarm());
        dialog.show();
        startAlarmSound();
    }

    private void startAlarmSound() {
        stopAlarm();
        if (!prefs.getBoolean("alarmSoundEnabled", true)) return;

        String saved = prefs.getString("alarmUri", "");
        Uri uri;
        if (!saved.isEmpty()) uri = Uri.parse(saved);
        else uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        try {
            alarmPlayer = new MediaPlayer();
            alarmPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            alarmPlayer.setDataSource(this, uri);
            alarmPlayer.setOnPreparedListener(mp -> {
                mp.setLooping(true);
                float level = prefs.getInt("alarmVolume", 100) / 100f;
                mp.setVolume(level, level);
                mp.start();
            });
            alarmPlayer.setOnErrorListener((mp, what, extra) -> {
                stopAlarm();
                playFallbackTone();
                return true;
            });
            alarmPlayer.prepareAsync();
        } catch (Exception ex) {
            stopAlarm();
            playFallbackTone();
        }
    }

    private void playFallbackTone() {
        try {
            fallbackTone = new android.media.ToneGenerator(AudioManager.STREAM_ALARM, prefs.getInt("alarmVolume", 100));
            fallbackTone.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 5000);
        } catch (Exception ignored) { }
    }

    private void stopAlarm() {
        try {
            if (alarmPlayer != null) {
                if (alarmPlayer.isPlaying()) alarmPlayer.stop();
                alarmPlayer.release();
                alarmPlayer = null;
            }
        } catch (Exception ignored) { alarmPlayer = null; }
        try {
            if (fallbackTone != null) {
                fallbackTone.stopTone();
                fallbackTone.release();
                fallbackTone = null;
            }
        } catch (Exception ignored) { fallbackTone = null; }
    }

    private void renameDevice(Device d, int id, TextView view) {
        EditText e = new EditText(this);
        e.setText(d.name);
        new AlertDialog.Builder(this).setTitle("تغيير اسم الجهاز").setView(e)
            .setPositiveButton("حفظ", (x, y) -> {
                String n = e.getText().toString().trim();
                if (!n.isEmpty()) {
                    d.name = n;
                    prefs.edit().putString("name" + id, n).apply();
                    view.setText(n + (d.match ? "  ⚽" : ""));
                }
            }).setNegativeButton("إلغاء", null).show();
    }

    private void addDevice() {
        final EditText e = new EditText(this);
        e.setHint("اسم الجهاز الجديد");
        new AlertDialog.Builder(this).setTitle("إضافة جهاز").setView(e)
            .setPositiveButton("إضافة", (d, w) -> {
                String n = e.getText().toString().trim();
                if (n.isEmpty()) n = "جهاز إضافي " + (devices.size() + 1);
                Device dev = new Device(n, false);
                devices.add(dev);
                addDeviceCard(dev, devices.size());
            }).setNegativeButton("إلغاء", null).show();
    }

    private void changePassword() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(1);
        EditText old = new EditText(this);
        old.setHint("كلمة المرور الحالية");
        old.setInputType(129);
        box.addView(old);
        EditText nw = new EditText(this);
        nw.setHint("كلمة المرور الجديدة");
        nw.setInputType(129);
        box.addView(nw);
        new AlertDialog.Builder(this).setTitle("تغيير كلمة المرور").setView(box)
            .setPositiveButton("حفظ", (d, w) -> {
                if (!old.getText().toString().equals(prefs.getString("password", "")))
                    Toast.makeText(this, "كلمة المرور الحالية غير صحيحة", Toast.LENGTH_LONG).show();
                else if (nw.getText().toString().trim().length() < 4)
                    Toast.makeText(this, "كلمة المرور 4 أحرف أو أرقام على الأقل", Toast.LENGTH_LONG).show();
                else {
                    prefs.edit().putString("password", nw.getText().toString()).apply();
                    Toast.makeText(this, "تم تغيير كلمة المرور", Toast.LENGTH_SHORT).show();
                }
            }).setNegativeButton("إلغاء", null).show();
    }

    @Override
    protected void onDestroy() {
        stopAlarm();
        for (Device d : devices) if (d.timer != null) d.timer.cancel();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}
