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

import java.util.List;

/**
 * The rows of a relation read when a stage runs rather than when it is planned: a join's right side that is
 * itself a planned relation, such as a parenthesized join group. Rows already in hand are wrapped by
 * {@link #of(List)}.
 */
public interface RowsProvider {

    /**
     * The relation's rows.
     *
     * @return the rows
     */
    List<Row> rows();

    /**
     * How the relation reads in a plan, or null for rows already in hand.
     *
     * @return the description, or null
     */
    String describe();

    /**
     * Rows already in hand.
     *
     * @param rows the rows
     * @return a provider answering them
     */
    static RowsProvider of(final List<Row> rows) {
        return new RowsProvider() {
            @Override
            public List<Row> rows() {
                return rows;
            }

            @Override
            public String describe() {
                return null;
            }
        };
    }
}
