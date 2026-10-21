package com.arthou.smputilities.core.modsync;

import java.util.List;

/**
 * @param download files missing or different from the server's copy
 * @param remove   files this system installed earlier that the server no longer lists
 * @param rejected entries refused for safety (bad name, bad hash, host not allowed)
 */
public record SyncPlan(List<ModEntry> download, List<String> remove, List<String> rejected) {
    public boolean upToDate() {
        return download.isEmpty() && remove.isEmpty();
    }
}
