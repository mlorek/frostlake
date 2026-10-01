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

/**
 * Which statements a query-history table function lists: every one the tracker holds, or those of one
 * session, one user or one warehouse. The scoped three are INFORMATION_SCHEMA table functions of their own,
 * each taking the value it filters by as a named argument and defaulting it to the caller's.
 */
public enum QueryHistoryScope {

    /** {@code QUERY_HISTORY()} — every statement. */
    ALL("QUERY_HISTORY", null),

    /** {@code QUERY_HISTORY_BY_SESSION()} — one session's statements, the caller's by default. */
    SESSION("QUERY_HISTORY_BY_SESSION", "SESSION_ID"),

    /** {@code QUERY_HISTORY_BY_USER()} — one user's statements, the caller's by default. */
    USER("QUERY_HISTORY_BY_USER", "USER_NAME"),

    /** {@code QUERY_HISTORY_BY_WAREHOUSE()} — one warehouse's statements, the current one by default. */
    WAREHOUSE("QUERY_HISTORY_BY_WAREHOUSE", "WAREHOUSE_NAME");

    private final String functionName;
    private final String parameter;

    QueryHistoryScope(final String functionName, final String parameter) {
        this.functionName = functionName;
        this.parameter = parameter;
    }

    /** The table function's name. */
    public String functionName() {
        return functionName;
    }

    /** The named argument the scope filters by, or null for the unscoped listing. */
    public String parameter() {
        return parameter;
    }
}
