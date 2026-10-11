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

package dev.frostlake.metastore;

/**
 * The session whose statement this thread is running, by the number CURRENT_SESSION() answers in it, for what an
 * object has to remember of the session that created it: a temporary object lives as long as that session and is
 * dropped when it ends. A thread that serves no bound session - the embedded engine's own - names none, and what
 * it creates lives as long as the engine.
 */
public final class StatementSession {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<Long>();

    private StatementSession() {
    }

    /**
     * Run this thread's statements as a session's until {@link #unbind}.
     *
     * @param sessionNumber the session's number, or null for the engine's own
     */
    public static void bind(final Long sessionNumber) {
        if (sessionNumber == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(sessionNumber);
        }
    }

    /** Back to the engine's own session on this thread. */
    public static void unbind() {
        CURRENT.remove();
    }

    /**
     * The session this thread's statement runs in.
     *
     * @return its number, or null for the engine's own
     */
    public static Long current() {
        return CURRENT.get();
    }
}
