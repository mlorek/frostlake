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

import dev.frostlake.http.SessionContext;
import dev.frostlake.security.AbortableSession;

/**
 * An HTTP session as SYSTEM$ABORT_SESSION ends it: the way a released one ends, its open transaction rolled back
 * and its state dropped, so that its next request names no live session.
 */
final class AbortableHttpSession implements AbortableSession {

    private final ConcurrentDatabaseEngine engine;
    private final SessionContext session;

    AbortableHttpSession(final ConcurrentDatabaseEngine engine, final SessionContext session) {
        this.engine = engine;
        this.session = session;
    }

    /** The number CURRENT_SESSION() answers in the session. */
    long sessionNumber() {
        return session.getSettings().getSessionNumber();
    }

    @Override
    public void abort() {
        // The session ends as soon as the aborting request is over. What it runs now ends at its next checkpoint, and
        // a statement it is waiting in for a table lock at once, rather than going on once the lock is free (see
        // StatementCancellation and TableLockWait) - unless what it runs now is the abort itself: a session that
        // aborts itself gets its answer first.
        if (engine.getEngine().getSecurityManager().getSessionContext().getBoundSettings() != session.getSettings()) {
            session.getSettings().markAborted();
        }
    }

    @Override
    public void endAborted() {
        // A session that aborted itself was not marked while its request ran; it is now, so that its context, kept by
        // a client, runs nothing more either (see ConcurrentDatabaseEngine#execute).
        session.getSettings().markAborted();
        engine.endSession(session);
    }
}
