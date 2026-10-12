package com.ocarius.autohub.testing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Horloge de test qu'on fait avancer a la main : "31 minutes plus tard"
 * s'ecrit {@code clock.advance(Duration.ofMinutes(31))}, sans attente reelle.
 *
 * <p>Necessaire pour les cas d'usage, qui recoivent leur horloge a la
 * construction : un {@code Clock.fixed} ne permettrait pas de faire passer le
 * temps entre deux appels.
 */
public final class MutableClock extends Clock {

    private Instant now;
    private final ZoneId zone;

    /**
     * @param start instant initial
     * @param zone  fuseau de l'horloge (celui de l'agence en production)
     */
    public MutableClock(Instant start, ZoneId zone) {
        this.now = start;
        this.zone = zone;
    }

    /** Fait avancer le temps. */
    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(now, zone);
    }

    @Override
    public Instant instant() {
        return now;
    }
}
