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

package dev.frostlake;

/**
 * The Frostlake-over-JDBC test switch. With {@code FL_JDBC=1} in the environment,
 * {@link BaseDatabaseTest} routes every statement through Frostlake's own JDBC driver (a
 * {@link dev.frostlake.jdbc.DirectConnection} over the test's engine) instead of calling the
 * engine API — the same suite, flipped by a switch, auditing the whole driver surface.
 * {@code SF_LIVE} takes precedence when both are set.
 */
public final class FrostlakeJdbc {

    private FrostlakeJdbc() {
    }

    /** Whether FL-over-JDBC mode is on ({@code FL_JDBC=1} and not overridden by {@code SF_LIVE}). */
    public static boolean enabled() {
        return "1".equals(System.getenv("FL_JDBC")) && !LiveSnowflake.enabled();
    }
}
