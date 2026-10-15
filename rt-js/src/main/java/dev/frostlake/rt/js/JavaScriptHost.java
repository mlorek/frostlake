/*
 * Copyright 2026 MLorek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.frostlake.rt.js;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.TimeZone;

/**
 * What a handler's JavaScript world asks the engine for: the name of the session's zone at an instant, as a date
 * prints it, and why a text is not JSON. One host serves one script engine, and remembers the last JSON fault it
 * told so the sentence an uncaught one reaches SQL with can quote the text it is about. The world gives the host
 * one thing back: the function that tells a value that left a handler uncaught.
 */
public final class JavaScriptHost {

    private final ZoneId zone;
    private V8JsonFault lastJsonFault;
    private Value describer;

    JavaScriptHost(final ZoneId zone) {
        this.zone = zone;
    }

    /**
     * The zone's name at an instant, in full: {@code Pacific Daylight Time}, {@code Greenwich Mean Time}. The
     * daylight name is given while the zone's clocks are set forward and the standard one otherwise.
     *
     * @param epochMillis the instant
     * @return the name
     */
    @HostAccess.Export
    public String zoneName(final double epochMillis) {
        final boolean daylight = !Double.isNaN(epochMillis)
            && zone.getRules().isDaylightSavings(Instant.ofEpochMilli((long) epochMillis));
        return TimeZone.getTimeZone(zone).getDisplayName(daylight, TimeZone.LONG, Locale.US);
    }

    /**
     * The name a formatter tells for the zone it resolved to (see {@link JavaScriptZones#told}).
     *
     * @param resolved the zone's name as the engine resolved it
     * @return the name as the account's engine tells it
     */
    @HostAccess.Export
    public String zoneId(final String resolved) {
        return JavaScriptZones.told(resolved, zone);
    }

    /**
     * Why a text is not JSON.
     *
     * @param text the text {@code JSON.parse} was given
     * @return the sentence, or null when the text is JSON
     */
    @HostAccess.Export
    public String jsonFault(final String text) {
        final V8JsonFault fault = V8JsonFault.of(text);
        if (fault == null) {
            return null;
        }
        lastJsonFault = fault;
        return fault.message();
    }

    /**
     * Takes the function that tells an uncaught value; called once, by the world's own setup.
     *
     * @param function the function
     */
    @HostAccess.Export
    public void describer(final Value function) {
        if (describer == null) {
            describer = function;
        }
    }

    /** What a value that left a handler uncaught is told as, or null when the world cannot tell. */
    String describe(final Value thrown) {
        if (describer == null || thrown == null) {
            return null;
        }
        try {
            final Value told = describer.execute(thrown);
            return told.isString() ? told.asString() : null;
        } catch (final RuntimeException untold) {
            return null;
        }
    }

    /** The JSON fault a sentence was last told for, or null when the sentence is not that fault's. */
    V8JsonFault jsonFaultOf(final String sentence) {
        return lastJsonFault != null && lastJsonFault.message().equals(sentence) ? lastJsonFault : null;
    }
}
