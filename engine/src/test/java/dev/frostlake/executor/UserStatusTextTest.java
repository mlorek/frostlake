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

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The countdown and key-time renderings of SHOW USERS and DESCRIBE USER, at fixed instants: the values an account
 * printed a known time after each property was set.
 */
public class UserStatusTextTest {

    private static final Instant NOW = Instant.parse("2026-09-26T07:23:37.500Z");

    @Test
    public void daysToExpiryIsTheWholeSecondsLeftAsAFractionOfADay() {
        // Half a second after DAYS_TO_EXPIRY = 30: 2591999 whole seconds left.
        assertEquals("29.999988425925928", UserStatusText.daysToExpiry(NOW.plusSeconds(2_592_000L).minusMillis(500),
            NOW));
        // Four seconds after DAYS_TO_EXPIRY = 1.
        assertEquals("0.9999537037037037", UserStatusText.daysToExpiry(NOW.plusSeconds(86_396L), NOW));
        // An expired user counts toward zero: under a second past DAYS_TO_EXPIRY = -1 still reads -1.0.
        assertEquals("-1.0", UserStatusText.daysToExpiry(NOW.minusSeconds(86_400L).minusMillis(400), NOW));
        assertEquals("-20000.0", UserStatusText.daysToExpiry(NOW.minusSeconds(20_000L * 86_400L), NOW));
        // An expiry before 1970 reads as none.
        assertNull(UserStatusText.daysToExpiry(Instant.parse("1969-03-29T07:28:11.650Z"), NOW));
        assertNull(UserStatusText.daysToExpiry(null, NOW));
    }

    @Test
    public void minutesLeftCountTowardZeroAndStopAMinuteAfterTheEnd() {
        assertEquals("9", UserStatusText.minutesLeft(NOW.plusSeconds(600L).minusMillis(1), NOW));
        assertEquals("10", UserStatusText.minutesLeft(NOW.plusSeconds(600L), NOW));
        assertEquals("0", UserStatusText.minutesLeft(NOW.plusSeconds(58L), NOW));
        assertEquals("0", UserStatusText.minutesLeft(NOW.minusSeconds(2L), NOW));
        assertNull(UserStatusText.minutesLeft(NOW.minusSeconds(61L), NOW));
        assertNull(UserStatusText.minutesLeft(null, NOW));
        assertTrue(UserStatusText.running(NOW.plusSeconds(1L), NOW));
        assertFalse(UserStatusText.running(NOW.minusSeconds(2L), NOW));
        assertFalse(UserStatusText.running(null, NOW));
    }

    @Test
    public void aKeySetTimeIsUtcWithTrailingZerosDropped() {
        assertEquals("2026-09-26 07:23:36.749", UserStatusText.setTime(Instant.parse("2026-09-26T07:23:36.749Z")));
        assertEquals("2026-09-26 07:26:46.11", UserStatusText.setTime(Instant.parse("2026-09-26T07:26:46.110Z")));
        assertEquals("2026-09-26 07:26:46.1", UserStatusText.setTime(Instant.parse("2026-09-26T07:26:46.100Z")));
        assertEquals("2026-09-26 07:26:46.0", UserStatusText.setTime(Instant.parse("2026-09-26T07:26:46Z")));
        assertEquals("2026-09-26 07:26:46.005", UserStatusText.setTime(Instant.parse("2026-09-26T07:26:46.005Z")));
    }
}
