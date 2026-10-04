package dev.xmine.config.hud;

import java.util.ArrayList;
import java.util.List;

/** Tiny toast queue shown by the Notifications HUD element. */
public final class NotificationManager {
    public static final class Entry {
        public final String text;
        public final long created;
        Entry(String t) { text = t; created = System.currentTimeMillis(); }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private NotificationManager() {}

    public static void push(String text) {
        if (ENTRIES.size() >= 8) ENTRIES.remove(0);
        ENTRIES.add(new Entry(text));
    }

    public static List<Entry> entries() { return ENTRIES; }

    /** Drop very old entries so the list can't grow while the HUD element is disabled. */
    public static void tick() {
        long now = System.currentTimeMillis();
        ENTRIES.removeIf(e -> now - e.created > 12_000);
    }
}
