package io.github.lnyocly.ai4j.ambient.heartbeat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Quiet-hours routing policy for {@link AgenticHeartbeat} and {@link SupervisoryAgenda}.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class QuietHoursPolicy {

    @Builder.Default
    private boolean enabled = false;

    /** Inclusive local hour [0..23] when quiet hours start (e.g. 23 for 23:00). */
    @Builder.Default
    private int startHour = 23;

    /** Exclusive local hour [0..23] when quiet hours end (e.g. 8 for 08:00). */
    @Builder.Default
    private int endHour = 8;

    @Builder.Default
    private String zoneId = "UTC";

    @Builder.Default
    private boolean bypassForCritical = true;

    @Builder.Default
    private boolean deferNonCriticalMainWakeup = true;

    public static QuietHoursPolicy disabled() {
        return QuietHoursPolicy.builder().enabled(false).build();
    }

    public static QuietHoursPolicy ofHours(int startHour, int endHour, ZoneId zone) {
        return QuietHoursPolicy.builder()
                .enabled(true)
                .startHour(normalizeHour(startHour))
                .endHour(normalizeHour(endHour))
                .zoneId(zone == null ? "UTC" : zone.getId())
                .build();
    }

    public boolean isQuietAt(Instant instant) {
        if (!enabled || instant == null) {
            return false;
        }
        int start = normalizeHour(startHour);
        int end = normalizeHour(endHour);
        if (start == end) {
            return false;
        }
        ZoneId zone = zoneId == null || zoneId.trim().isEmpty()
                ? ZoneId.of("UTC")
                : ZoneId.of(zoneId);
        int hour = ZonedDateTime.ofInstant(instant, zone).getHour();
        if (start < end) {
            return hour >= start && hour < end;
        }
        return hour >= start || hour < end;
    }

    private static int normalizeHour(int hour) {
        int mod = hour % 24;
        return mod < 0 ? mod + 24 : mod;
    }
}
