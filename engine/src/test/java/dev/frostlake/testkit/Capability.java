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

/**
 * What a backend's transport can report. An expectation that needs a capability the backend lacks is
 * recorded as skipped — the transport's missing-API list — instead of failing the test.
 */
public enum Capability {
    /** An error code and SQLSTATE on a failure, not just a message. */
    ERROR_CODE,
    /** A DML row count reported out of band — a JDBC update count, the engine's rows affected. */
    UPDATE_COUNT,
    /** The result's column names. */
    COLUMN_NAMES,
    /** Session state — USE, variables, an open transaction — persisting across statements. */
    SESSION
}
