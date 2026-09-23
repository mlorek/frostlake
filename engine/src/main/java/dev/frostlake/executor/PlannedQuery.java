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

import dev.frostlake.storage.ResultSet;

/**
 * A SELECT compiled but not yet run: its shape — the result's columns, with the statistics a relation built
 * over it carries — and the pipeline that answers its rows when {@link #run()} is called. A derived table, a
 * view body or a set operation's arm is planned this way, so the query reading it is planned over a known
 * shape and the rows flow only when that query's pipeline runs. Running re-enters every scope that was in
 * force while planning.
 */
abstract class PlannedQuery {

    /** The result's columns and no row, carrying the relation statistics the result will carry. */
    final ResultSet shape;
    /** The plan's stages as text. */
    final String description;

    /**
     * @param shape       the result's columns and no row
     * @param description the plan's stages as text
     */
    PlannedQuery(final ResultSet shape, final String description) {
        this.shape = shape;
        this.description = description;
    }

    /**
     * Runs the plan.
     *
     * @return the result
     */
    abstract ResultSet run();
}
