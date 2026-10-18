package com.psshop.arabic;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.media.AudioManager;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private LinearLayout main;
    private final ArrayList<Device> devices = new ArrayList<>();
    private final ArrayList<CountDownTimer> timers = new ArrayList<>();
    private static class Device {
        String name; boolean match; long left=420000; int half=1; boolean running=false;
        TextView timeView, statusView;
        Button startButton, halfButton;
        CountDownTimer timer;
        Device(String n, boolean m){name=n;match=m;}
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs=getSharedPreferences("psshop", MODE_PRIVATE);
        if (!prefs.getBoolean("passwordSet", false)) { createPassword(); }
        else { askPassword(); }
    }

    private void createPassword() {
        final EditText p = new EditText(this); p.setHint("أنشئ كلمة مرور"); p.setInputType(129);
        new AlertDialog.Builder(this).setTitle("حماية البرنامج").setMessage("أنشئ كلمة مرور للبرنامج")
          .setView(p).setCancelable(false).setPositiveButton("حفظ", (d,w)->{
              String v=p.getText().toString();
              if(v.trim().length()<4){ Toast.makeText(this,"اجعل كلمة المرور 4 أحرف أو أرقام على الأقل",Toast.LENGTH_LONG).show(); createPassword(); }
              else {prefs.edit().putString("password",v).putBoolean("passwordSet",true).apply(); showApp();}
          }).show();
    }
    private void askPassword() {
        final EditText p = new EditText(this); p.setHint("كلمة المرور"); p.setInputType(129);
        new AlertDialog.Builder(this).setTitle("تسجيل الدخول").setView(p).setCancelable(false)
          .setPositiveButton("دخول",(d,w)->{
              if(p.getText().toString().equals(prefs.getString("password",""))) showApp();
              else { Toast.makeText(this,"كلمة المرور غير صحيحة",Toast.LENGTH_LONG).show(); askPassword(); }
          }).show();
    }

    private void showApp() {
        main = new LinearLayout(this); main.setOrientation(1); main.setPadding(20,18,20,20);
        main.setBackgroundColor(Color.rgb(245,247,250));
        ScrollView scroll=new ScrollView(this); scroll.addView(main); setContentView(scroll);
        TextView title=new TextView(this); title.setText("🎮 إدارة محل البلايستيشن"); title.setTextSize(24); title.setTextColor(Color.rgb(25,45,75)); title.setGravity(Gravity.CENTER); title.setPadding(0,12,0,20); main.addView(title);
        TextView note=new TextView(this); note.setText("المباريات: الشوط 7 دقائق • ابدأ الشوط الثاني يدويًا"); note.setTextSize(14); note.setGravity(Gravity.CENTER); note.setPadding(0,0,0,14); main.addView(note);
        devices.clear();
        for(int i=1;i<=6;i++){
            boolean match=(i==1||i==5);
            String defaultName=match ? "جهاز "+i+" - مباريات" : "جهاز "+i;
            devices.add(new Device(prefs.getString("name"+i,defaultName),match));
        }
        for(int i=0;i<devices.size();i++) addDeviceCard(devices.get(i),i+1);
        Button add=new Button(this); add.setText("＋ إضافة جهاز"); add.setOnClickListener(v->addDevice()); main.addView(add);
        Button settings=new Button(this); settings.setText("⚙ تغيير كلمة المرور"); settings.setOnClickListener(v->changePassword()); main.addView(settings);
        Button reset=new Button(this); reset.setText("إعادة ضبط مؤقتات الأجهزة"); reset.setOnClickListener(v->{for(Device d:devices){if(d.timer!=null)d.timer.cancel();d.running=false;d.half=1;d.left=420000; refresh(d);} Toast.makeText(this,"تمت إعادة ضبط المؤقتات",Toast.LENGTH_SHORT).show();}); main.addView(reset);
    }
    private void addDeviceCard(Device d,int id) {
        LinearLayout card=new LinearLayout(this); card.setOrientation(1); card.setPadding(16,10,16,12); card.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2); cp.setMargins(0,0,0,14); main.addView(card,cp);
        TextView name=new TextView(this); name.setText(d.name+(d.match?"  ⚽":"")); name.setTextSize(19); name.setTextColor(Color.rgb(20,40,70)); card.addView(name);
        d.timeView=new TextView(this); d.timeView.setTextSize(27); d.timeView.setGravity(Gravity.CENTER); card.addView(d.timeView);
        d.statusView=new TextView(this); d.statusView.setGravity(Gravity.CENTER); card.addView(d.statusView);
        LinearLayout row=new LinearLayout(this); row.setOrientation(0); card.addView(row);
        d.startButton=new Button(this); d.startButton.setText("ابدأ"); row.addView(d.startButton,new LinearLayout.LayoutParams(0,-2,1));
        Button rename=new Button(this); rename.setText("تغيير الاسم"); row.addView(rename,new LinearLayout.LayoutParams(0,-2,1));
        d.halfButton=new Button(this); d.halfButton.setText("بدء الشوط الثاني"); if(d.match) card.addView(d.halfButton);
        d.startButton.setOnClickListener(v->{
            if(d.running){ if(d.timer!=null)d.timer.cancel(); d.running=false; refresh(d); }
            else startTimer(d);
        });
        rename.setOnClickListener(v->renameDevice(d,id,name));
        d.halfButton.setOnClickListener(v->{if(d.timer!=null)d.timer.cancel();d.running=false;d.half=2;d.left=420000;startTimer(d);});
        refresh(d);
    }
    private void refresh(Device d) {
        if(d.timeView==null)return;
        long s=d.left/1000;
        d.timeView.setText(String.format(Locale.getDefault(),"%02d:%02d",s/60,s%60));
        d.statusView.setText(d.running?"الحالة: مشغول":"الحالة: متاح / متوقف");
        d.startButton.setText(d.running?"إيقاف مؤقت":"ابدأ الوقت");
        d.halfButton.setEnabled(d.match && !d.running && d.half==1);
    }
    private void startTimer(Device d) {
        d.running=true; refresh(d);
        d.timer=new CountDownTimer(d.left,1000){
            public void onTick(long ms){d.left=ms;refresh(d);}
            public void onFinish(){d.left=0;d.running=false;refresh(d);alertEnd(d);}
        }.start();
    }
    private void alertEnd(Device d) {
        try { android.media.ToneGenerator tone=new android.media.ToneGenerator(AudioManager.STREAM_ALARM,100); tone.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD,1200); }
        catch(Exception ignored){}
        try { Vibrator vb=(Vibrator)getSystemService(Context.VIBRATOR_SERVICE); if(vb!=null && vb.hasVibrator()){if(android.os.Build.VERSION.SDK_INT>=26)vb.vibrate(VibrationEffect.createOneShot(700,VibrationEffect.DEFAULT_AMPLITUDE));else vb.vibrate(700);} } catch(Exception ignored){}
        new AlertDialog.Builder(this).setTitle("انتهى الوقت").setMessage(d.name+" — انتهى الشوط "+d.half).setPositiveButton("حسنًا",(x,y)->{}).show();
    }
    private void renameDevice(Device d,int id,TextView view) {
        EditText e=new EditText(this);e.setText(d.name);
        new AlertDialog.Builder(this).setTitle("تغيير اسم الجهاز").setView(e).setPositiveButton("حفظ",(x,y)->{
            String n=e.getText().toString().trim(); if(!n.isEmpty()){d.name=n;prefs.edit().putString("name"+id,n).apply();view.setText(n+(d.match?"  ⚽":""));}
        }).setNegativeButton("إلغاء",null).show();
    }
    private void addDevice() {
        final EditText e=new EditText(this);e.setHint("اسم الجهاز الجديد");
        new AlertDialog.Builder(this).setTitle("إضافة جهاز").setView(e).setPositiveButton("إضافة",(d,w)->{
            String n=e.getText().toString().trim();if(n.isEmpty())n="جهاز إضافي "+(devices.size()+1);
            Device dev=new Device(n,false);devices.add(dev);addDeviceCard(dev,devices.size());
        }).setNegativeButton("إلغاء",null).show();
    }
    private void changePassword() {
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);
        EditText old=new EditText(this);old.setHint("كلمة المرور الحالية");old.setInputType(129);box.addView(old);
        EditText nw=new EditText(this);nw.setHint("كلمة المرور الجديدة");nw.setInputType(129);box.addView(nw);
        new AlertDialog.Builder(this).setTitle("تغيير كلمة المرور").setView(box).setPositiveButton("حفظ",(d,w)->{
            if(!old.getText().toString().equals(prefs.getString("password","")))Toast.makeText(this,"كلمة المرور الحالية غير صحيحة",Toast.LENGTH_LONG).show();
            else if(nw.getText().toString().trim().length()<4)Toast.makeText(this,"كلمة المرور 4 أحرف أو أرقام على الأقل",Toast.LENGTH_LONG).show();
            else {prefs.edit().putString("password",nw.getText().toString()).apply();Toast.makeText(this,"تم تغيير كلمة المرور",Toast.LENGTH_SHORT).show();}
        }).setNegativeButton("إلغاء",null).show();
    }
    @Override public void onBackPressed() { super.onBackPressed(); }
}
