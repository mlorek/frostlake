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

import java.util.Map;

/**
 * How the catalog's name-keyed maps are read. Every map holds an object under its name EXACTLY as stored: an
 * unquoted name arrives upper-cased and a quoted one verbatim, so two objects whose names differ only in case
 * are two objects, each reachable by its own spelling. SQL resolution matches that stored name exactly.
 */
public final class NameKeys {

    private NameKeys() {
    }

    /**
     * The key a map holds that name under. The name itself when it is stored; otherwise the one stored name
     * that matches it ignoring case. That fallback serves the engine's own Java callers, which pass a name as
     * a person wrote it; it answers only when exactly one stored name matches, so it can never choose between
     * two that coexist. A name nothing holds comes back unchanged, and the caller's lookup misses.
     *
     * @param map  the map to resolve against
     * @param name the name as the caller spells it
     * @return the stored key, or the name itself when none matches
     */
    public static String keyFor(final Map<String, ?> map, final String name) {
        if (name == null || map.containsKey(name)) {
            return name;
        }
        String found = null;
        for (final String key : map.keySet()) {
            if (key.equalsIgnoreCase(name)) {
                if (found != null) {
                    return name;
                }
                found = key;
            }
        }
        return found != null ? found : name;
    }
}
