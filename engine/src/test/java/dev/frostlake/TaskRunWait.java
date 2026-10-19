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
 * Waits for a task's run to be told by the history functions. The embedded engine runs a task inside the statement
 * that starts it, so the first look finds it; an account runs it a moment later, so a live run looks again until
 * it does.
 */
public final class TaskRunWait {

    private static final int LIVE_LOOKS = 90;
    private static final long PAUSE_MILLIS = 2000L;

    private TaskRunWait() {
    }

    /**
     * Looks until a query answers at least a number of rows.
     *
     * @param engine the engine under test
     * @param sql    the query
     * @param rows   the rows waited for
     */
    public static void untilRows(final DatabaseEngine engine, final String sql, final int rows) {
        final int looks = LiveSnowflake.enabled() ? LIVE_LOOKS : 1;
        for (int look = 0; look < looks; look++) {
            if (engine.executeQuery(sql).getRows().size() >= rows) {
                return;
            }
            if (look + 1 < looks) {
                pause();
            }
        }
        throw new AssertionError("fewer than " + rows + " row(s) told by: " + sql);
    }

    private static void pause() {
        try {
            Thread.sleep(PAUSE_MILLIS);
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
