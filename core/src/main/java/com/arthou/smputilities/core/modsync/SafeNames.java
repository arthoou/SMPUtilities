package com.arthou.smputilities.core.modsync;

import java.util.regex.Pattern;

/** File names accepted from a server. Anything else is refused before touching the disk. */
public final class SafeNames {
    private static final Pattern JAR = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,127}\\.jar");
    private static final Pattern SHA512 = Pattern.compile("[0-9a-f]{128}");

    private SafeNames() {
    }

    public static boolean isSafeJarName(String name) {
        return name != null && JAR.matcher(name).matches() && !name.contains("..");
    }

    public static boolean isSha512(String hash) {
        return hash != null && SHA512.matcher(hash).matches();
    }
}
