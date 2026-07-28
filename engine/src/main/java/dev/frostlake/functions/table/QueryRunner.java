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

package dev.frostlake.functions.table;

import dev.frostlake.storage.ResultSet;

/**
 * Callback a table function uses to run a SQL query text against the engine — the seam that lets
 * {@link ToQuery} execute its argument without depending on the executor class itself.
 * Replaces {@code Function<String, ResultSet>}.
 */
public interface QueryRunner {

    /**
     * Execute the given SQL query and return its result set.
     *
     * @param sql the query text
     * @return the query's result set
     */
    ResultSet runQuery(final String sql);
}
