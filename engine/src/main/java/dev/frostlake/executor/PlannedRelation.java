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

import dev.frostlake.executor.operators.Operator;
import dev.frostlake.metastore.model.Table;

/**
 * A planned stage that reshapes the relation flowing through the pipeline — a LATERAL item, a PIVOT, an
 * UNPIVOT — together with the shape it answers, known while planning so the stages after it can be planned
 * over it before any row flows.
 */
final class PlannedRelation {

    final Operator stage;
    final Table table;
    final String alias;

    /**
     * @param stage the stage that produces the relation's rows
     * @param table the relation's shape
     * @param alias the alias the FROM clause gives it, or null
     */
    PlannedRelation(final Operator stage, final Table table, final String alias) {
        this.stage = stage;
        this.table = table;
        this.alias = alias;
    }
}
