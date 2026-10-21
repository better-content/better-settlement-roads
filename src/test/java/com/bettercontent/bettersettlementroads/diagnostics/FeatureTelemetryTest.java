package com.bettercontent.bettersettlementroads.diagnostics;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class FeatureTelemetryTest {
    @Test public void nativeOutcomeIsNotMisreportedOrExpandedIntoPrivatePayload() {
        List<String> records = new ArrayList<>();
        PlaytestLog log = new PlaytestLog("better_settlement_roads", new PlaytestLog.Sink() {
            public boolean enabled(String category, String level) { return true; }
            public void write(String category, String level, String message) { records.add(message); }
        }, () -> 0L, new Properties());
        FeatureTelemetry.placement(log, PlaytestLog.Context.server("minecraft:overworld", 42), 1, 0, 0);
        if (records.size() != 1 || !records.get(0).contains("result=segments_marked reason=batch_complete")
                || !records.get(0).contains("dimension=minecraft:overworld") || !records.get(0).contains("tick=42")
                || records.get(0).contains("subject=") || records.get(0).contains("pos=")
                || records.get(0).contains("player=") || records.get(0).contains("payload=")) {
            throw new AssertionError(records.toString());
        }
    }
    public static void main(String[] args) { new FeatureTelemetryTest().nativeOutcomeIsNotMisreportedOrExpandedIntoPrivatePayload(); }
}
