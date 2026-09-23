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
import java.util.List;

/**
 * The object a SHOW GRANTS ON names, as live reports it there: its kind (a view named with TABLE is still
 * a VIEW), its name in full (a routine's with its argument types), the role that owns it, and when it was
 * created, which is when that ownership began.
 */
public final class GrantedObject {

    private final String kind;
    private final String name;
    private final String owner;
    private final Instant createdTime;
    private final List<String> spellings;

    /**
     * @param kind        the object's kind as live reports it
     * @param name        its full name as live reports it
     * @param owner       the role that owns it
     * @param createdTime when it was created
     * @param spellings   every name a statement can reach it by from where it lives: bare, with its schema,
     *                    and in full
     */
    public GrantedObject(final String kind, final String name, final String owner, final Instant createdTime,
                         final List<String> spellings) {
        this.kind = kind;
        this.name = name;
        this.owner = owner;
        this.createdTime = createdTime;
        this.spellings = spellings;
    }

    public String getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public String getOwner() {
        return owner;
    }

    public Instant getCreatedTime() {
        return createdTime;
    }

    public List<String> getSpellings() {
        return spellings;
    }

    /** Its own name, without its database, schema or argument types: the first of its spellings. */
    public String getBareName() {
        return spellings.get(0);
    }
}
