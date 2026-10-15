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

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The session's zone as the JavaScript engine is told it, so that {@code Intl} names the zone the way the
 * account's engine does (live-verified): UTC by that name, and a zone known by a newer name under the older one
 * its zone database keeps - {@code Asia/Kolkata} is {@code Asia/Calcutta}, {@code Europe/Kyiv} is
 * {@code Europe/Kiev}. Every name here was read from the account; a zone not listed keeps its own.
 *
 * <p>The other names of UTC are UTC - {@code Etc/GMT}, {@code Greenwich}, {@code Zulu}, {@code UCT},
 * {@code Universal} - for the session's zone and for one a formatter is given. One spelling stands apart: a
 * session whose TIMEZONE is {@code GMT} is told {@code +00:00}, where a formatter given {@code GMT} is told
 * {@code UTC}.
 */
final class JavaScriptZones {

    private static final Map<String, String> CANONICAL = new HashMap<>();

    /** What the zones equal to UTC are told as, and what the JavaScript engine is given for them. */
    private static final String UTC = "UTC";

    /** The one name of UTC a session's zone is told by its offset under. */
    private static final String SESSION_GMT = "GMT";

    /** What the JavaScript engine is given for a session whose zone is GMT. */
    private static final String SESSION_GMT_AS_GIVEN = "Etc/GMT0";

    private static final Set<String> UTC_NAMES = new HashSet<>(Arrays.asList(
        "UTC", "Etc/UTC", "GMT", "Etc/GMT", "Greenwich", "Etc/Greenwich", "GMT0", "Etc/GMT0", "Etc/GMT+0",
        "Etc/GMT-0", "Zulu", "Etc/Zulu", "UCT", "Etc/UCT", "Universal", "Etc/Universal"));

    static {
        final String[] pairs = {
            "Asia/Kolkata", "Asia/Calcutta", "Asia/Kathmandu", "Asia/Katmandu", "Europe/Kyiv", "Europe/Kiev",
            "Asia/Ho_Chi_Minh", "Asia/Saigon", "America/Argentina/Buenos_Aires", "America/Buenos_Aires",
            "US/Pacific", "America/Los_Angeles", "Asia/Yangon", "Asia/Rangoon", "Europe/Belfast", "Europe/London",
            "US/Eastern", "America/New_York", "Atlantic/Faroe", "Atlantic/Faeroe", "Africa/Asmara", "Africa/Asmera",
            "Pacific/Chuuk", "Pacific/Truk", "Pacific/Pohnpei", "Pacific/Ponape",
            "America/Indiana/Indianapolis", "America/Indianapolis", "America/Kentucky/Louisville",
            "America/Louisville", "Asia/Thimbu", "Asia/Thimphu", "Asia/Ulan_Bator", "Asia/Ulaanbaatar",
            "Asia/Macao", "Asia/Macau", "Asia/Dacca", "Asia/Dhaka", "America/Nuuk", "America/Godthab",
            "Pacific/Kanton", "Pacific/Enderbury", "Australia/ACT", "Australia/Sydney", "Japan", "Asia/Tokyo",
            "PRC", "Asia/Shanghai", "Singapore", "Asia/Singapore", "Canada/Eastern", "America/Toronto",
            "America/Montreal", "America/Toronto", "Asia/Istanbul", "Europe/Istanbul", "Turkey", "Europe/Istanbul",
            "EST", "Etc/GMT+5", "MST", "Etc/GMT+7", "HST", "Etc/GMT+10", "America/Atikokan",
            "America/Coral_Harbour",
        };
        for (int i = 0; i < pairs.length; i += 2) {
            CANONICAL.put(pairs[i], pairs[i + 1]);
        }
    }

    private JavaScriptZones() {
    }

    /**
     * The zone as the JavaScript engine's time zone option: a region by the name the account's engine knows it
     * under, UTC as the one spelling the engine names {@code UTC}, a whole-hour offset as its {@code Etc/GMT}
     * zone (whose sign runs the other way), and any other offset as UTC.
     *
     * @param zone the session's zone
     * @return the option's value
     */
    static String option(final ZoneId zone) {
        if (!(zone instanceof ZoneOffset)) {
            final String id = zone.getId();
            if (SESSION_GMT.equals(id)) {
                // The session's GMT is told by its offset: the engine is given a name of GMT a formatter is
                // hardly ever given, so the two can be told apart when the zone is told back.
                return SESSION_GMT_AS_GIVEN;
            }
            if (UTC_NAMES.contains(id)) {
                return "Z";
            }
            final String canonical = CANONICAL.get(id);
            return canonical != null ? canonical : id;
        }
        final int seconds = ((ZoneOffset) zone).getTotalSeconds();
        if (seconds == 0 || seconds % 3600 != 0) {
            return "Z";
        }
        final int hours = seconds / 3600;
        return "Etc/GMT" + (hours > 0 ? "-" + hours : "+" + (-hours));
    }

    /**
     * The name a formatter tells for the zone it resolved to: the older name the account's engine keeps for a
     * renamed zone, UTC for every other name of it, and the name itself for any other zone.
     *
     * @param resolved the zone's name as the JavaScript engine resolved it
     * @param session  the session's zone
     * @return the name as told
     */
    static String told(final String resolved, final ZoneId session) {
        if (resolved == null) {
            return null;
        }
        if (SESSION_GMT_AS_GIVEN.equals(resolved) && session != null && SESSION_GMT.equals(session.getId())) {
            return "+00:00";
        }
        if (UTC_NAMES.contains(resolved)) {
            return UTC;
        }
        final String canonical = CANONICAL.get(resolved);
        return canonical != null ? canonical : resolved;
    }
}
