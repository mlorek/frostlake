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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.SqlCompilationError;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * One property per statement. Frostlake's grammar spells DDL option lists as {@code option*} —
 * which is right, because the ORDER of those options is free on a real account — so the "given
 * twice" refusal belongs here, over the collected names, rather than in the grammar.
 *
 * <p>Not every repeat is refused, so the caller decides what to hand over: live accepts a repeated
 * {@code CLUSTER BY}, a repeated {@code COMMENT} on a stage or a routine, and {@code NOT NULL}
 * twice on a column, while refusing a repeated table property, view comment, sequence bound,
 * warehouse property or task option.
 */
final class PropertyDuplicates {

    private PropertyDuplicates() {
    }

    /** Refuse the second sighting of a name, in live's wording, comparing case-insensitively. */
    static void reject(final List<String> names) {
        final Set<String> seen = new HashSet<>();
        for (final String name : names) {
            final String canonical = name.toUpperCase(Locale.ROOT);
            if (!seen.add(canonical)) {
                throw new RuntimeException(SqlCompilationError.duplicateProperty(canonical));
            }
        }
    }
}
