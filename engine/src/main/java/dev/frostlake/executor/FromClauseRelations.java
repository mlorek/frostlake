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

import dev.frostlake.metastore.model.Table;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * A query's FROM-clause relations by the key a column reference qualifies them with — the alias when one was
 * written, else the table's own name — remembering which keys are aliases. An alias replaces the table's whole
 * name, its database and schema included: live refuses {@code P.PUBLIC.T.x} over {@code FROM P.PUBLIC.T t} as an
 * invalid identifier, even where the alias folds to the table's own name, while over an unaliased
 * {@code FROM P.PUBLIC.T} the same reference reads the column.
 */
public final class FromClauseRelations extends HashMap<String, Table> {

    private static final long serialVersionUID = 1L;

    private final Set<String> aliases = new HashSet<String>();

    public FromClauseRelations() {
    }

    /**
     * A copy: the same relations under the same keys, remembering which keys are aliases.
     *
     * @param source the relations to copy
     */
    public FromClauseRelations(final FromClauseRelations source) {
        super(source);
        aliases.addAll(source.aliases);
    }

    /**
     * Registers a relation under the key a reference names it by.
     *
     * @param alias the alias written for it, or null when none was
     * @param table the relation
     */
    public void putRelation(final String alias, final Table table) {
        if (alias != null) {
            aliases.add(alias);
        }
        put(alias != null ? alias : table.getName(), table);
    }

    /**
     * Marks a key as an alias the query wrote.
     *
     * @param key the key
     */
    public void markAlias(final String key) {
        aliases.add(key);
    }

    /**
     * Whether the key is an alias the query wrote rather than a table's own name.
     *
     * @param key the key, exactly as registered
     * @return true for an alias
     */
    public boolean isAlias(final String key) {
        return aliases.contains(key);
    }

    @Override
    public void clear() {
        aliases.clear();
        super.clear();
    }
}
