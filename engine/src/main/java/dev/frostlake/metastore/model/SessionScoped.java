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

import dev.frostlake.metastore.StatementSession;

/**
 * An object that may be TEMPORARY: one a session creates for itself, which no other session reaches or lists
 * (live-verified).
 */
public interface SessionScoped {

    /**
     * Whether the object is temporary.
     *
     * @return whether it is
     */
    boolean isTemporary();

    /**
     * The session that created the object.
     *
     * @return the session's id, or null for one the engine's own session created
     */
    Long getCreatedInSession();

    /**
     * Whether an object is another session's temporary one: one a session created, read in another session. The
     * engine's own session, which serves no bound session, reaches every one.
     *
     * @param object the object
     * @return whether the current session does not reach it
     */
    static boolean othersTemporary(final SessionScoped object) {
        final Long current = StatementSession.current();
        return object.isTemporary() && current != null && object.getCreatedInSession() != null
            && !current.equals(object.getCreatedInSession());
    }
}
