package net.kdt.pojavlaunch.utils;

import android.content.Context;

/** Stub. resolve() can throw, because a deleted driver folder is read from disk. */
public final class TurnipDrivers {
    public static final String CHOICE_SYSTEM = "system";
    public static boolean LOAD = true;
    public static boolean THROW = false;

    public static Resolved resolve(Context context, String choice) {
        if (THROW) throw new RuntimeException("driver folder unreadable");
        if (CHOICE_SYSTEM.equals(choice)) return new Resolved(false, null, null);
        return new Resolved(LOAD, null, null);
    }

    public static final class Resolved {
        public final boolean loadTurnip;
        public final String directory;
        public final String libraryName;

        public Resolved(boolean loadTurnip, String directory, String libraryName) {
            this.loadTurnip = loadTurnip;
            this.directory = directory;
            this.libraryName = libraryName;
        }
    }
}
