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

import dev.frostlake.metastore.model.View;
import java.util.ArrayList;
import java.util.List;

/**
 * What a view's query reads once it is expanded through the views, the common table expressions and the FROM-clause
 * subqueries it reads, each once, in the order the expanded query reads them: the tables, in full, which a stream
 * reading the view whole lists as its base tables, and the views below the view's own query, whose change tracking
 * such a stream turns on (live-verified).
 */
final class WholeViewReads {

    private final List<String> tables = new ArrayList<>();
    private final List<View> views = new ArrayList<>();

    /**
     * Records a table the expanded query reads.
     *
     * @param fullName the table's name in full
     */
    void addTable(final String fullName) {
        if (!tables.contains(fullName)) {
            tables.add(fullName);
        }
    }

    /**
     * Records a view the expanded query reads below the view's own query.
     *
     * @param view the view
     */
    void addView(final View view) {
        for (final View known : views) {
            if (known == view) {
                return;
            }
        }
        views.add(view);
    }

    /** The tables the expanded query reads, in full. */
    List<String> tables() {
        return tables;
    }

    /** The views below the view's own query. */
    List<View> views() {
        return views;
    }
}
