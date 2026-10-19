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

import dev.frostlake.metastore.QualifiedName;

import java.util.Set;

/**
 * One change to the names privileges are recorded under: each recorded name that lies under {@code from} becomes the
 * same name under {@code to}, for the kinds given, moved or copied. A name lies under {@code from} when it equals it
 * or names something inside it: a table inside a schema, a schema inside a database.
 */
public final class GrantRemap {

    /** Only the name {@code from} itself: one object. */
    public static final int EXACT = 0;
    /** Only the names inside {@code from}: a container's members. */
    public static final int MEMBERS = 1;
    /** {@code from} and every name inside it: a container and its members. */
    public static final int ALL = 2;

    private final Set<String> kinds;
    private final String[] from;
    private final String[] to;
    private final boolean copy;
    private final boolean exceptOwnership;
    private final int reach;

    /**
     * @param kinds           the kinds affected, upper-case as grants record them
     * @param from            the canonical name parts the affected names start with
     * @param to              the parts that replace them
     * @param copy            true to copy the privileges, keeping the originals; false to move them
     * @param exceptOwnership true to leave OWNERSHIP behind
     * @param reach           which names under {@code from} are affected: {@link #EXACT}, {@link #MEMBERS} or
     *                        {@link #ALL}
     */
    public GrantRemap(final Set<String> kinds, final String[] from, final String[] to, final boolean copy,
                      final boolean exceptOwnership, final int reach) {
        this.kinds = kinds;
        this.from = from.clone();
        this.to = to.clone();
        this.copy = copy;
        this.exceptOwnership = exceptOwnership;
        this.reach = reach;
    }

    /**
     * The name a recorded name becomes.
     *
     * @param kind the kind it is recorded under
     * @param name the recorded name
     * @return the new name, canonical as grants record names, or null when this change leaves it alone
     */
    public String mapped(final String kind, final String name) {
        if (!kinds.contains(kind)) {
            return null;
        }
        final String[] parts = QualifiedName.parse(name).parts();
        if (parts.length < from.length || (reach == MEMBERS && parts.length == from.length)
                || (reach == EXACT && parts.length != from.length)) {
            return null;
        }
        for (int i = 0; i < from.length; i++) {
            if (!parts[i].equals(from[i])) {
                return null;
            }
        }
        final String[] mapped = new String[to.length + parts.length - from.length];
        System.arraycopy(to, 0, mapped, 0, to.length);
        System.arraycopy(parts, from.length, mapped, to.length, parts.length - from.length);
        return QualifiedName.join(mapped);
    }

    /** Whether the privileges are copied, the originals kept. */
    public boolean copies() {
        return copy;
    }

    /** Whether OWNERSHIP stays where it is. */
    public boolean exceptsOwnership() {
        return exceptOwnership;
    }
}
