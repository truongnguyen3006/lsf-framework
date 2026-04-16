package com.myorg.lsf.quota.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

public final class MutableClock extends Clock {

    private Instant current;
    private final ZoneId zone;

    public MutableClock(Instant current, ZoneId zone) {
        this.current = current;
        this.zone = zone;
    }

    public static MutableClock startingAt(Instant instant) {
        return new MutableClock(instant, ZoneId.of("UTC"));
    }

    public void advance(Duration duration) {
        current = current.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(current, zone);
    }

    @Override
    public Instant instant() {
        return current;
    }
}
