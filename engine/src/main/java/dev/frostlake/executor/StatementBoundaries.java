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

/**
 * What happens between the statements of a request that holds several. The account runs each statement of such
 * a request as a statement of its own: under autocommit it commits as it completes, and a later statement's
 * failure undoes only that statement, inside an explicit transaction too. The query executor finds the
 * boundaries while it walks the request; whoever owns the transaction and the write-ahead log acts on them.
 */
public interface StatementBoundaries {

    /** A top-level statement of the request is about to run. */
    void begin();

    /**
     * That statement completed.
     *
     * @param statementText the statement's own source text, as the write-ahead log records it
     */
    void complete(String statementText);

    /** That statement failed: its own writes are undone before the failure propagates. */
    void fail();
}
