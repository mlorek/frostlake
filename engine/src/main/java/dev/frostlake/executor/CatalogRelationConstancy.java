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
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.types.NumericType;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link OuterNameConstancy} over a statement's catalog tables, read from their stored columns: the least and
 * the greatest value of an exact NUMBER column, and whether it holds a NULL, and for every other scalar family
 * whether its rows hold one value (see {@link StoredColumnValues}). A VARIANT, ARRAY or OBJECT column is never one
 * value, and neither is a column of a table the statement reaches through an outer join, whose caller passes no
 * constancy at all.
 */
final class CatalogRelationConstancy implements OuterNameConstancy {

    private final QueryExecutor executor;
    private final StoredColumnValues values;
    private final Map<String, Table> relations = new HashMap<>();
    private final Map<String, Boolean> answered = new HashMap<>();

    /**
     * @param executor  the executor whose storage the columns are read from
     * @param relations the statement's relations keyed by the name it gives each
     */
    CatalogRelationConstancy(final QueryExecutor executor, final Map<String, Table> relations) {
        this.executor = executor;
        this.values = new StoredColumnValues(executor);
        for (final Map.Entry<String, Table> relation : relations.entrySet()) {
            if (relation.getValue() != null) {
                this.relations.put(relation.getKey().toUpperCase(), relation.getValue());
            }
        }
    }

    @Override
    public boolean holdsOneValue(final String qualifier, final String column) {
        final String key = qualifier == null ? column : qualifier + "." + column;
        final Boolean known = answered.get(key);
        if (known != null) {
            return known.booleanValue();
        }
        boolean holds;
        try {
            holds = pinned(owner(qualifier, column), column);
        } catch (final RuntimeException unreadable) {
            holds = false;
        }
        answered.put(key, Boolean.valueOf(holds));
        return holds;
    }

    /** The relation a name reads: the one its qualifier names, else the one relation carrying it. */
    private Table owner(final String qualifier, final String column) {
        if (qualifier != null) {
            return relations.get(qualifier);
        }
        Table owner = null;
        for (final Table relation : relations.values()) {
            if (!relation.hasColumn(column) || relation == owner) {
                continue;
            }
            if (owner != null) {
                return null;
            }
            owner = relation;
        }
        return owner;
    }

    private boolean pinned(final Table owner, final String column) {
        if (owner == null || owner.residentSource() == null || !owner.hasColumn(column)) {
            return false;
        }
        final TableColumn declared = owner.getColumn(column);
        if (declared.getDataType() instanceof NumericType && !NumericType.isApproximate(declared.getDataType())) {
            return OuterNameConstancy.pinsOneValue(executor.columnValueRange(owner.residentSource(), declared.getName()));
        }
        return values.holdsOneValue(owner, declared.getName());
    }
}
