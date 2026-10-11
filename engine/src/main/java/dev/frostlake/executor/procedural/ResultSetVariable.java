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

package dev.frostlake.executor.procedural;

import dev.frostlake.storage.ResultSet;

public class ResultSetVariable {
    private final String selectQuery;
    private ResultSet resultSet;
    private AsyncJob job;
    private String name;

    public ResultSetVariable(final String selectQuery) {
        this.selectQuery = selectQuery;
    }

    /** The name the RESULTSET was declared or assigned under, upper-cased, or null. */
    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    /** Whether nothing has been given to the RESULTSET: no statement, no job, no rows. */
    public boolean isEmpty() {
        return selectQuery == null && resultSet == null && job == null;
    }

    /** The asynchronous job that fills this RESULTSET, or null when a statement filled it where it stands. */
    public AsyncJob getJob() {
        return job;
    }

    public void setJob(final AsyncJob job) {
        this.job = job;
    }

    public String getSelectQuery() {
        return selectQuery;
    }

    public ResultSet getResultSet() {
        return resultSet;
    }

    public void setResultSet(final ResultSet resultSet) {
        this.resultSet = resultSet;
    }
}
