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

package dev.frostlake.metastore.model;

/**
 * The trigger of the dynamic table refreshes running on this thread: a creation's, the scheduler's, or by default an
 * ALTER DYNAMIC TABLE … REFRESH's. The refreshes a refresh runs first, of the tables it reads, carry its trigger.
 */
public final class RefreshTriggers {

    private static final ThreadLocal<RefreshTrigger> CURRENT = new ThreadLocal<>();

    private RefreshTriggers() {
    }

    /**
     * Starts refreshes of a trigger on this thread.
     *
     * @param trigger the trigger
     * @return the trigger it displaces, for {@link #end}
     */
    public static RefreshTrigger begin(final RefreshTrigger trigger) {
        final RefreshTrigger previous = CURRENT.get();
        CURRENT.set(trigger);
        return previous;
    }

    /**
     * Puts back what {@link #begin} displaced.
     *
     * @param previous what it answered
     */
    public static void end(final RefreshTrigger previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }

    /**
     * The trigger of the refresh running on this thread.
     *
     * @return the trigger begun, MANUAL when none was
     */
    public static RefreshTrigger current() {
        final RefreshTrigger trigger = CURRENT.get();
        return trigger == null ? RefreshTrigger.MANUAL : trigger;
    }
}
