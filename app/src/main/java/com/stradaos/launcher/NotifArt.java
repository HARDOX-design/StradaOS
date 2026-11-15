package com.stradaos.launcher;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.graphics.Bitmap;
import android.os.Parcelable;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Lee SOLO la notificación de Open Headunit (pista + carátula de Android Auto).
 * Hay que activarlo una vez en Ajustes → Accesibilidad → "Carátula de Android Auto".
 */
public class NotifArt extends AccessibilityService {

    public interface Listener { void onAaNotification(Bitmap art, List<String> texts); }

    static Listener listener;
    static Bitmap lastArt;
    static List<String> lastTexts = new ArrayList<String>();
    static long lastAt;
    static boolean running;

    /*
     * Pista leída de la notificación multimedia de Open Headunit: título, artista, «mm:ss / mm:ss» y el botón
     * del medio («Pausa» = sonando). Sirve cuando el celular de Android Auto NO está emparejado por Bluetooth:
     * ahí la notificación es la única fuente de la canción.
     */
    static String lastTitle, lastArtist;
    static long lastPosMs = -1, lastDurMs = -1;
    static Boolean lastPlaying;

    private static final java.util.regex.Pattern PROGRESS =
            java.util.regex.Pattern.compile("^(\\d{1,3}):(\\d{2})(?:\\s*/\\s*(\\d{1,3}):(\\d{2}))?$");
    private static final java.util.regex.Pattern PROGRESS_FULL =
            java.util.regex.Pattern.compile("^\\d{1,3}:\\d{2}\\s*/\\s*\\d{1,3}:\\d{2}$");
    // Textos del botón del medio en los idiomas de Open Headunit.
    private static final String[] PAUSE = {"pause", "pausa", "pausar", "pauze", "pauza", "pauză", "пауза", "pozastavit",
            "szünet", "jeda", "duraklat", "tạm dừng", "暫停", "暂停", "一時停止", "일시중지", "إيقاف مؤقت", "रोकें",
            "หยุดชั่วคราว", "პაუზა"};
    private static final String[] PLAY = {"play", "reproducir", "wiedergabe", "lecture", "riproduci", "afspelen",
            "odtwórz", "reproduzir", "redare", "воспроизвести", "відтворити", "přehrát", "lejátszás", "putar", "oynat",
            "phát", "播放", "再生", "재생", "تشغيل", "चलाएं", "เล่น", "დაკვრა"};

    /** Lo que se saca de los textos de la notificación. */
    private static class Parsed {
        String title, sub;
        final List<String> others = new ArrayList<String>();
        final List<String> buttons = new ArrayList<String>();
    }

    @Override
    protected void onServiceConnected() {
        running = true;
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        running = false;
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        try {
            if (e.getEventType() != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) return;
            if (!"com.andrerinas.headunitrevived".equals(String.valueOf(e.getPackageName()))) return;
            Parcelable p = e.getParcelableData();
            if (!(p instanceof Notification)) return;
            Notification n = (Notification) p;
            List<String> texts = new ArrayList<String>();
            Parsed pr = new Parsed();
            for (CharSequence cs : e.getText()) if (cs != null && cs.length() > 0) texts.add(cs.toString());
            collect(n.contentView, texts, pr);
            try { collect(n.bigContentView, texts, pr); } catch (Throwable ignored) { }
            if (n.tickerText != null) texts.add(n.tickerText.toString());
            readExtras(n, pr);
            // Open Headunit también avisa la navegación, el arranque, etc. Solo sirve la de música:
            // trae carátula, el tiempo «mm:ss / mm:ss» o los botones anterior/pausa/siguiente.
            String[] act = actionTitles(n);
            boolean progress = false;
            for (String t : texts) if (PROGRESS_FULL.matcher(t.trim()).matches()) progress = true;
            if (pr.sub != null && PROGRESS.matcher(pr.sub.trim()).matches()) progress = true;
            if (n.largeIcon == null && !progress && act.length < 3 && pr.buttons.size() < 3) return;
            parse(pr, texts, act);
            lastArt = n.largeIcon;
            lastTexts = texts;
            lastAt = android.os.SystemClock.elapsedRealtime();
            if (listener != null) listener.onAaNotification(lastArt, texts);
        } catch (Throwable ignored) { }
    }

    private void collect(RemoteViews rv, List<String> out, Parsed pr) {
        if (rv == null) return;
        try {
            FrameLayout parent = new FrameLayout(this);
            View v = rv.apply(this, parent);
            walk(v, out, pr);
        } catch (Throwable ignored) { }
    }

    private static int sysId(String name) {
        try { return android.content.res.Resources.getSystem().getIdentifier(name, "id", "android"); }
        catch (Throwable e) { return 0; }
    }

    private void walk(View v, List<String> out, Parsed pr) {
        if (v instanceof TextView) {
            CharSequence t = ((TextView) v).getText();
            if (t != null && t.length() > 0) {
                String s = t.toString();
                out.add(s);
                int id = v.getId();
                if (v instanceof android.widget.Button) pr.buttons.add(s);
                else if (id == android.R.id.title) { if (pr.title == null) pr.title = s; }
                else if (id != 0 && id == sysId("time")) { /* hora del sistema */ }
                else if (v.getClass().getName().contains("DateTimeView") || v instanceof android.widget.Chronometer) { }
                else pr.others.add(s);
            }
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) walk(g.getChildAt(i), out, pr);
        }
    }

    /** Android 4.4+: título, texto y subtexto vienen ordenados en los extras. */
    private static void readExtras(Notification n, Parsed pr) {
        try {
            android.os.Bundle b = null;
            if (android.os.Build.VERSION.SDK_INT >= 19) b = n.extras;
            else {
                java.lang.reflect.Field f = Notification.class.getDeclaredField("extras");
                f.setAccessible(true);
                b = (android.os.Bundle) f.get(n);
            }
            if (b == null) return;
            CharSequence ti = b.getCharSequence("android.title"), tx = b.getCharSequence("android.text"),
                    su = b.getCharSequence("android.subText");
            if (ti != null && ti.length() > 0) pr.title = ti.toString();
            if (tx != null && tx.length() > 0) pr.others.add(0, tx.toString());
            if (su != null && su.length() > 0) pr.sub = su.toString();
        } catch (Throwable ignored) { }
    }

    /** Títulos de los botones de la notificación (en Android 4.1–4.3 el campo existe pero está oculto). */
    private static String[] actionTitles(Notification n) {
        try {
            java.lang.reflect.Field f = Notification.class.getDeclaredField("actions");
            f.setAccessible(true);
            Object[] a = (Object[]) f.get(n);
            if (a == null) return new String[0];
            String[] out = new String[a.length];
            for (int i = 0; i < a.length; i++) {
                java.lang.reflect.Field tf = a[i].getClass().getDeclaredField("title");
                tf.setAccessible(true);
                Object t = tf.get(a[i]);
                out[i] = t != null ? t.toString() : null;
            }
            return out;
        } catch (Throwable e) {
            return new String[0];
        }
    }

    private static boolean isClock(String s) {
        String x = s.trim().toLowerCase(java.util.Locale.ROOT);
        return x.matches("^\\d{1,2}:\\d{2}(\\s*(a|p)\\.?\\s*m\\.?)?$");
    }

    private static int labelState(String s) {
        if (s == null) return 0;
        String x = s.trim().toLowerCase(java.util.Locale.ROOT);
        for (String p : PAUSE) if (x.equals(p)) return 1;
        for (String p : PLAY) if (x.equals(p)) return 2;
        return 0;
    }

    private static void parse(Parsed pr, List<String> texts, String[] act) {
        String title = pr.title;
        String prog = null;
        if (pr.sub != null && PROGRESS.matcher(pr.sub.trim()).matches()) prog = pr.sub.trim();
        if (prog == null) for (String t : texts) if (PROGRESS_FULL.matcher(t.trim()).matches()) { prog = t.trim(); break; }
        if (prog == null) for (String t : pr.others) if (PROGRESS.matcher(t.trim()).matches() && !isClock(t)) { prog = t.trim(); break; }
        java.util.Set<String> labels = new java.util.HashSet<String>(pr.buttons);
        for (String a : act) if (a != null) labels.add(a);
        String artist = null;
        for (String t : pr.others) {
            String x = t.trim();
            if (x.length() == 0 || labels.contains(t) || x.equals(prog) || PROGRESS.matcher(x).matches()) continue;
            if (title == null) { title = x; continue; }
            if (x.equalsIgnoreCase(title.trim())) continue;
            artist = x;
            break;
        }
        long pos = -1, dur = -1;
        if (prog != null) {
            java.util.regex.Matcher m = PROGRESS.matcher(prog);
            if (m.matches()) {
                pos = (Long.parseLong(m.group(1)) * 60 + Long.parseLong(m.group(2))) * 1000;
                if (m.group(3) != null) dur = (Long.parseLong(m.group(3)) * 60 + Long.parseLong(m.group(4))) * 1000;
            }
        }
        // El botón del medio (anterior · pausa/reproducir · siguiente).
        int st = 0;
        if (act.length >= 3) st = labelState(act[1]);
        if (st == 0) for (String a : act) if ((st = labelState(a)) != 0) break;
        if (st == 0) for (String b : pr.buttons) if ((st = labelState(b)) != 0) break;
        lastTitle = title;
        lastArtist = artist;
        lastPosMs = pos;
        lastDurMs = dur;
        lastPlaying = st == 1 ? Boolean.TRUE : st == 2 ? Boolean.FALSE : null;
    }

    @Override
    public void onInterrupt() { }
}
