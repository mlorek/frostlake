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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The object a SYSTEM$REFERENCE token stands for, by its domain and canonical name, the privileges it was made with,
 * the session that made it, and the statement it lasts for where it is no SESSION reference.
 */
final class ObjectReference {

    private final String domain;
    private final String[] parts;
    private final List<String> privileges;
    private final Instant statement;
    private final Long session;

    /**
     * @param domain     the domain the reference was made for, upper-cased: TABLE, VIEW, STREAM, FUNCTION or
     *                   PROCEDURE
     * @param parts      the object's canonical name, one part per level
     * @param privileges the privileges the reference carries, upper-cased
     * @param statement  the start of the outermost statement the reference lasts for, or null for one that lasts the
     *                   session
     * @param session    the session that made it, or null for the engine's own
     */
    ObjectReference(final String domain, final String[] parts, final List<String> privileges,
                    final Instant statement, final Long session) {
        this.domain = domain;
        this.parts = parts.clone();
        this.privileges = new ArrayList<>(privileges);
        this.statement = statement;
        this.session = session;
    }

    /**
     * Whether the reference still stands for its object while the given statement of the given session runs: a
     * reference is its own session's alone, another session reading the token reads no object (live-verified).
     *
     * @param running        the start of the outermost statement running now, or null outside one
     * @param runningSession the session running it, or null for the engine's own
     * @return true for a session's reference, and for one made by the statement running
     */
    boolean lastsFor(final Instant running, final Long runningSession) {
        return Objects.equals(session, runningSession) && (statement == null || statement.equals(running));
    }

    /** @return the domain the reference was made for */
    String getDomain() {
        return domain;
    }

    /** @return the object's canonical name parts */
    String[] getParts() {
        return parts.clone();
    }

    /** @return the privileges the reference carries */
    List<String> getPrivileges() {
        return new ArrayList<>(privileges);
    }
}
