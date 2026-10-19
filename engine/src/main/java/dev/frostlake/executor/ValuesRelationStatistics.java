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

import dev.frostlake.values.RelationStatistics;

/**
 * What a VALUES clause carries in place of a table's statistics: nothing a COUNT, a MIN or a MAX could be answered
 * from — it has no row count and no stored column — and yet a mark that a relation over it, a subquery or a WITH
 * clause projecting its rows, is merged into the query over it as one over a table is: {@code SUM(x)} over
 * {@code (SELECT column1 + 1 x FROM VALUES (1.5), (2.5))} is rewritten as {@code SUM(column1 + 1)} is
 * (live-verified).
 */
final class ValuesRelationStatistics implements RelationStatistics {

    /** The one instance: the clause's rows tell the plan nothing. */
    static final ValuesRelationStatistics INSTANCE = new ValuesRelationStatistics();

    private ValuesRelationStatistics() {
    }

    @Override
    public boolean readsWithinStatistics() {
        return false;
    }

    @Override
    public Long sourceRowCount() {
        return null;
    }

    @Override
    public boolean passesStoredColumn(final int index) {
        return false;
    }
}
