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

package dev.frostlake.testkit;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * One execution target for the portable test suites. The same JSON definitions run against every
 * backend — the in-process engine, the JDBC driver, the HTTP endpoint, or a live account over JDBC. A
 * backend declares its {@link Capability capabilities}; a check it cannot express is recorded rather
 * than failed. A driver repository implements the same contract in its own language (see SCHEMA.md).
 */
public interface Backend extends AutoCloseable {

    /**
     * What resets the context before every test: a session that lets one request hold any number of
     * statements, then a fresh {@code test_db.test_schema}, made current. Several cases send a script
     * of their own, and a session answers only single statements until it is told otherwise.
     */
    List<String> RESET_CONTEXT = Collections.unmodifiableList(Arrays.asList(
        "ALTER SESSION SET MULTI_STATEMENT_COUNT = 0",
        "CREATE OR REPLACE DATABASE test_db", "USE DATABASE test_db",
        "CREATE OR REPLACE SCHEMA test_schema", "USE SCHEMA test_schema"));

    /** @return the backend's name, as a suite's {@code skip.backends} spells it */
    String name();

    /** @return what the transport can report */
    Set<Capability> capabilities();

    /**
     * Run one statement in the backend's current session. A SQL refusal comes back in the result; only
     * a transport failure throws.
     *
     * @param sql the statement
     * @return its outcome
     * @throws Exception when the transport itself fails
     */
    ExecResult execute(String sql) throws Exception;

    /**
     * Reset to a clean context before a test — {@link #RESET_CONTEXT}, in order.
     *
     * @throws Exception when the reset cannot run
     */
    void resetContext() throws Exception;

    @Override
    void close() throws Exception;
}
