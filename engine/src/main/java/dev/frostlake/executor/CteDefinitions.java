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

import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The definition each common table expression's result was computed from. A subquery read again from its own text
 * holds no WITH of the statement around it, so a one-part name of the statement's CTEs is found here through the
 * result the statement put in scope: {@code (SELECT c + o.k FROM t)} under {@code WITH t AS (SELECT 1 AS c)} reads
 * one row, as the same body written as a derived table does (live-verified).
 */
public final class CteDefinitions {

    private static final Map<ResultSet, FrostlakeParser.CteDefinitionContext> DEFINED =
        Collections.synchronizedMap(new WeakHashMap<ResultSet, FrostlakeParser.CteDefinitionContext>());

    private CteDefinitions() {
    }

    /**
     * Note the definition a CTE's result was computed from.
     *
     * @param result     the result the statement puts in scope
     * @param definition the CTE's definition
     */
    static void record(final ResultSet result, final FrostlakeParser.CteDefinitionContext definition) {
        if (result != null && definition != null) {
            DEFINED.put(result, definition);
        }
    }

    /**
     * The definition a CTE's result was computed from.
     *
     * @param result the result in scope
     * @return its definition, or null where none was noted
     */
    static FrostlakeParser.CteDefinitionContext of(final ResultSet result) {
        return result == null ? null : DEFINED.get(result);
    }
}
