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

import java.util.ArrayList;
import java.util.List;

/**
 * A relation one SELECT reads in its FROM list, as an EXPLAIN plan resolves names against it: the name it is referred
 * to by and, once it is planned, the columns it answers — none known for a source whose columns the plan does not
 * read, such as a table function.
 */
final class PlanFromItem {

    private final String key;
    private List<PlanColumn> columns;

    /**
     * @param key the name the relation is referred to by, upper-cased
     */
    PlanFromItem(final String key) {
        this.key = key;
    }

    /**
     * Records the columns the relation answers, each owned by this relation.
     *
     * @param answered the columns, in order
     */
    void bind(final List<PlanColumn> answered) {
        columns = new ArrayList<>();
        for (final PlanColumn column : answered) {
            columns.add(column.ownedBy(key));
        }
    }

    String getKey() {
        return key;
    }

    /** The columns the relation answers, in order; null where they are not known. */
    List<PlanColumn> getColumns() {
        return columns;
    }

    /** The column of a name; null where the relation answers none so named or its columns are not known. */
    PlanColumn column(final String name) {
        if (columns == null) {
            return null;
        }
        for (final PlanColumn column : columns) {
            if (name.equals(column.getName())) {
                return column;
            }
        }
        return null;
    }
}
