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
package dev.frostlake.security;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The sessions of one engine that SYSTEM$ABORT_SESSION can end, by the number CURRENT_SESSION() answers. A front
 * end registers each session it serves and takes it out again when the session closes.
 *
 * <p>Aborting is two steps, because the function runs inside a statement of ANOTHER session: {@link #abort} only
 * marks the session, and the front ends call {@link #endAborted} between statements to roll back what it left
 * open. The aborted session itself refuses its next statement whether or not that second step has run yet.
 *
 * <p>A session is held weakly: whoever registers it keeps it alive, so a connection that is dropped without being
 * closed leaves nothing here once it is collected.
 */
public final class SessionDirectory {

    private final Map<Long, SessionReference> sessions = new ConcurrentHashMap<Long, SessionReference>();
    private final ReferenceQueue<AbortableSession> collected = new ReferenceQueue<AbortableSession>();
    private final Queue<AbortableSession> aborted = new ConcurrentLinkedQueue<AbortableSession>();

    /**
     * Make a session abortable under its number; a number already taken keeps its first session.
     *
     * @param sessionNumber the number CURRENT_SESSION() answers in the session
     * @param session       the session
     */
    public void register(final long sessionNumber, final AbortableSession session) {
        forgetCollected();
        final Long number = Long.valueOf(sessionNumber);
        sessions.putIfAbsent(number, new SessionReference(number, session, collected));
    }

    /** Drop the entries of sessions that were collected without being closed. */
    private void forgetCollected() {
        Reference<? extends AbortableSession> gone = collected.poll();
        while (gone != null) {
            sessions.remove(((SessionReference) gone).sessionNumber(), gone);
            gone = collected.poll();
        }
    }

    /**
     * Forget a session that closed.
     *
     * @param sessionNumber the session's number
     */
    public void unregister(final long sessionNumber) {
        sessions.remove(Long.valueOf(sessionNumber));
    }

    /**
     * Mark the session of that number aborted, when the engine serves one.
     *
     * @param sessionNumber the number given to SYSTEM$ABORT_SESSION
     * @return whether a session was marked
     */
    public boolean abort(final long sessionNumber) {
        final SessionReference entry = sessions.remove(Long.valueOf(sessionNumber));
        final AbortableSession session = entry == null ? null : entry.get();
        if (session == null) {
            return false;
        }
        session.abort();
        aborted.add(session);
        return true;
    }

    /** End every session marked since the last call. Safe to call when none is. */
    public void endAborted() {
        AbortableSession session = aborted.poll();
        while (session != null) {
            session.endAborted();
            session = aborted.poll();
        }
    }
}
