package com.bettercontent.bettersettlementroads.diagnostics;

/** Pure observation of the owning feature's existing outcome, with no native reads. */
public final class FeatureTelemetry {
    private FeatureTelemetry() {}
    public static void placement(PlaytestLog log, int segments, int blocks, int walls) {
        log.event("placement", "roads.placement", "server", "segments_marked", "batch_complete", () -> PlaytestLog.fields("segments", segments, "placed_blocks", blocks, "placed_wall_blocks", walls));
    }
}
