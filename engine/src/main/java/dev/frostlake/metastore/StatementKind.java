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

package dev.frostlake.metastore;

/**
 * The coarse kind of a tracked statement, classified from its leading keyword (see
 * {@code QueryHistory.determineQueryType}). {@link #OTHER} covers recognized-but-uncategorized statements and
 * {@link #UNKNOWN} a null/blank query; the enum name is the value shown in query history / SHOW output.
 */
public enum StatementKind {
    SELECT, INSERT, UPDATE, DELETE, MERGE, COPY, CREATE, DROP, ALTER, TRUNCATE,
    GRANT, REVOKE, SHOW, DESCRIBE, USE, BEGIN, COMMIT, ROLLBACK, CALL, OTHER, UNKNOWN
}
