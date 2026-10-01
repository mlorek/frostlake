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

package dev.frostlake.executor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * How SHOW USERS and DESCRIBE USER spell a user's countdowns and key timestamps, and which details a user's type
 * hides.
 *
 * <p>A countdown is stored as the moment it ends and read against the statement's clock, so it counts down as an
 * account's does: {@code days_to_expiry} is the whole seconds left, as a fraction of a day
 * ({@code 29.999988425925928} a second after {@code DAYS_TO_EXPIRY = 30}, {@code -1.0} for an expired user), and
 * {@code mins_to_unlock} / {@code mins_to_bypass_mfa} are the whole minutes left ({@code 9} just after
 * {@code MINS_TO_UNLOCK = 10}, {@code 0} in its last minute). The end instants themselves stay listed after they
 * pass ({@code expires_at_time}, {@code locked_until_time}).
 */
final class UserStatusText {

    private static final DateTimeFormatter SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final double SECONDS_PER_DAY = 86_400.0;
    private static final String SERVICE = "SERVICE";
    private static final String LEGACY_SERVICE = "LEGACY_SERVICE";

    private UserStatusText() {
    }

    /**
     * Whether a user's type hides the details of a person: a SERVICE or LEGACY_SERVICE user reads no first, middle or
     * last name and no MFA bypass, and reads them again once its type is PERSON, the bypass still counting down.
     */
    static boolean hidesPersonalDetails(final String userType) {
        return SERVICE.equalsIgnoreCase(userType) || LEGACY_SERVICE.equalsIgnoreCase(userType);
    }

    /**
     * Whether a user's type hides its password: a SERVICE user reads no password and no need to change one, while a
     * LEGACY_SERVICE user keeps both.
     */
    static boolean hidesPassword(final String userType) {
        return SERVICE.equalsIgnoreCase(userType);
    }

    /**
     * Days until the user expires, or null for a permanent user. An expiry before 1970 reads null too, as it does
     * on an account.
     */
    static String daysToExpiry(final Instant expiresAt, final Instant now) {
        if (expiresAt == null || expiresAt.toEpochMilli() < 0L) {
            return null;
        }
        final long seconds = (expiresAt.toEpochMilli() - now.toEpochMilli()) / 1000L;
        return Double.toString(seconds / SECONDS_PER_DAY);
    }

    /**
     * Whole minutes until a lock lifts or a bypass ends, counted toward zero: {@code 0} in its last minute and
     * through the first minute after it ended, then null — as it is when none was set.
     */
    static String minutesLeft(final Instant endsAt, final Instant now) {
        if (endsAt == null) {
            return null;
        }
        final long minutes = (endsAt.toEpochMilli() - now.toEpochMilli()) / 60_000L;
        return minutes < 0L ? null : Long.toString(minutes);
    }

    /** Whether a lock or a bypass is still running. */
    static boolean running(final Instant endsAt, final Instant now) {
        return endsAt != null && endsAt.isAfter(now);
    }

    /**
     * A key's last-set time as DESCRIBE USER prints it: UTC whatever the session's zone, to the millisecond with
     * trailing zeros dropped ({@code 2025-03-01 12:00:05.11}, {@code …:05.0} on a whole second).
     */
    static String setTime(final Instant instant) {
        final LocalDateTime utc = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        String millis = String.format(Locale.ROOT, "%03d", utc.getNano() / 1_000_000);
        while (millis.length() > 1 && millis.endsWith("0")) {
            millis = millis.substring(0, millis.length() - 1);
        }
        return SECONDS.format(utc) + "." + millis;
    }
}
