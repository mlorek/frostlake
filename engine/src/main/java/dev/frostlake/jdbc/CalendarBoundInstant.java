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
package dev.frostlake.jdbc;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * A Timestamp bound with a Calendar, as the account's driver binds one: the wall clock the value shows in the
 * Calendar's zone, sent as that wall clock's UTC moment, a TIMESTAMP_LTZ. A TIMESTAMP_NTZ column then holds the
 * moment as the session's zone shows it — the Calendar's wall clock in a UTC session — and a zoned column the
 * moment itself (live-verified). See {@link JdbcMarshaling#formatLiteral}.
 */
final class CalendarBoundInstant {

    private static final DateTimeFormatter TEXT =
        DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSSSSSSSS").withZone(ZoneOffset.UTC);

    private final Instant moment;

    CalendarBoundInstant(final Instant moment) {
        this.moment = moment;
    }

    /** The moment as the literal spells it, its offset written: {@code 2024-01-01 05:30:00.000000000 +0000}. */
    String text() {
        return TEXT.format(moment) + " +0000";
    }

    @Override
    public String toString() {
        return text();
    }
}
