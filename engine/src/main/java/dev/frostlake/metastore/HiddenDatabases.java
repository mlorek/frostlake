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

import java.util.Collections;
import java.util.Set;

/**
 * The databases the statement running on this thread names but does not see: to its SQL references each is a
 * database that does not exist (see {@link Catalog#databaseExact}). The outermost statement names them for its whole
 * run; a statement it runs in turn keeps them.
 */
public final class HiddenDatabases {

    private static final ThreadLocal<Set<String>> CURRENT = new ThreadLocal<Set<String>>();

    private HiddenDatabases() {
    }

    /** @return whether a statement on this thread has named the databases it does not see */
    public static boolean installed() {
        return CURRENT.get() != null;
    }

    /**
     * Names the databases the running statement does not see.
     *
     * @param names the databases' canonical names
     * @return the names named before, to give back to {@link #end}
     */
    public static Set<String> begin(final Set<String> names) {
        final Set<String> displaced = CURRENT.get();
        CURRENT.set(names == null ? Collections.<String>emptySet() : names);
        return displaced;
    }

    /**
     * Gives back the names named before a statement's.
     *
     * @param displaced what {@link #begin} answered
     */
    public static void end(final Set<String> displaced) {
        if (displaced == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(displaced);
        }
    }

    /** Whether the running statement does not see a database of this canonical name. */
    static boolean hides(final String name) {
        final Set<String> names = CURRENT.get();
        return names != null && name != null && names.contains(name);
    }
}
