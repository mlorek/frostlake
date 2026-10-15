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
package dev.frostlake.executor.operators;

import dev.frostlake.storage.Row;

import java.util.ArrayList;
import java.util.List;

/**
 * Puts each joined row's relations back in the order the FROM clause wrote them, after joins that ran in another:
 * a legacy {@code (+)} outer join runs each null-supplied relation after the relation it joins to, while
 * {@code SELECT *} and a positional reference read the relations as written — live lists
 * {@code FROM projects p, departments d WHERE p.department_id(+) = d.department_id} with the project's columns
 * first (live-verified).
 */
public final class RelationOrderOperator extends StageOperator {

    /** For each column of a row out, the position it reads in the row in. */
    private final int[] sources;

    /**
     * @param sources   for each column out, the position it reads in the row in
     * @param relations the relations as written, for the plan's description
     */
    public RelationOrderOperator(final int[] sources, final String relations) {
        super("REORDER[" + relations + "]");
        this.sources = sources.clone();
    }

    @Override
    protected List<Row> apply(final List<Row> input) {
        final List<Row> output = new ArrayList<>(input.size());
        for (final Row row : input) {
            final List<Object> values = new ArrayList<>(sources.length);
            for (final int source : sources) {
                values.add(row.getValue(source));
            }
            output.add(Row.of(values));
        }
        return output;
    }
}
