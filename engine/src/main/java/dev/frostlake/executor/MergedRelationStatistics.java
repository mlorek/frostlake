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
import dev.frostlake.values.RelationStatistics;

/**
 * The statistics of a derived relation the account's plan merges into the query over it — a SELECT projecting
 * one relation's rows under at most a WHERE — which also know the relation it reads and the select item each of
 * its columns projects, so a column of it can be read as the item it stands for.
 */
public interface MergedRelationStatistics extends RelationStatistics {

    /**
     * The relation this one reads: the catalog table, or a relation over one that keeps its statistics.
     *
     * @return the relation read
     */
    Table sourceRelation();

    /**
     * The select item the relation's column at {@code index} projects, as written and read against
     * {@link #sourceRelation()} — the column the account's plan puts in its place when it merges the relation
     * into the query over it.
     *
     * @param index the column's position in the relation
     * @return the item's text, or null where the column projects no item known here — among them the column a
     *     relation passing every column of its source through holds at the same position
     */
    String columnSource(int index);
}
