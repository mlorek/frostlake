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

import java.util.HashSet;
import java.util.Set;

/**
 * The names one SELECT inside a subquery brings into scope: its relations' qualifiers, the columns of the
 * relations the catalog can list, and its select aliases. A relation whose columns cannot be listed — a
 * derived table, a table function, a CTE, a pivot — may carry any bare name, so every bare name resolves
 * here once one is present.
 */
final class CorrelationScope {

    private final Set<String> qualifiers = new HashSet<>();
    private final Set<String> columns = new HashSet<>();
    private final Set<String> aliases = new HashSet<>();
    private boolean opaque;

    void addQualifier(final String qualifier) {
        qualifiers.add(qualifier);
    }

    void addColumn(final String column) {
        columns.add(column);
    }

    void addAlias(final String alias) {
        aliases.add(alias);
    }

    void markOpaque() {
        opaque = true;
    }

    /** Whether a qualifier names one of this SELECT's relations, by alias or by unaliased name. */
    boolean hasQualifier(final String qualifier) {
        return qualifiers.contains(qualifier);
    }

    /** Whether a bare name resolves in this SELECT. */
    boolean resolvesBare(final String name) {
        return opaque || columns.contains(name) || aliases.contains(name);
    }
}
