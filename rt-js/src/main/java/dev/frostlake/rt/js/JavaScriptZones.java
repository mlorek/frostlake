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
import java.util.HashMap;
import java.util.Map;

/**
 * The session's zone as the JavaScript engine is told it, so that {@code Intl} names the zone the way the
 * account's engine does (live-verified): UTC by that name, and a zone known by a newer name under the older one
 * its zone database keeps - {@code Asia/Kolkata} is {@code Asia/Calcutta}, {@code Europe/Kyiv} is
 * {@code Europe/Kiev}. Every name here was read from the account; a zone not listed keeps its own.
 */
final class JavaScriptZones {

    private static final Map<String, String> CANONICAL = new HashMap<>();

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
            if ("UTC".equals(id) || "Etc/UTC".equals(id)) {
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
}
