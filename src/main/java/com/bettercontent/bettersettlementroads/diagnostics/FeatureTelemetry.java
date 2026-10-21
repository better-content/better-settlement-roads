package com.bettercontent.bettersettlementroads.diagnostics;

/** Pure observation of the owning feature's existing outcome, with no native reads. */
public final class FeatureTelemetry {
    private FeatureTelemetry() {}
    public static void placement(PlaytestLog log, PlaytestLog.Context context, int segments, int blocks, int walls) {
        log.debug("placement", "roads.placement", context, "segments_marked", "batch_complete", () -> PlaytestLog.fields("segments", segments, "placed_blocks", blocks, "placed_wall_blocks", walls));
    }
}
