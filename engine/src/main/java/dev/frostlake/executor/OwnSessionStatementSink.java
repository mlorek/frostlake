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
 * Where a statement a block or a procedure runs goes when it committed work in a session of its own: the
 * write-ahead log, which keeps it in a record of its own rather than in the enclosing statement's (see
 * {@link ApartRecording}).
 */
public interface OwnSessionStatementSink {

    /**
     * Logs a statement that committed work in a session of its own, apart from the statement that ran it.
     *
     * @param statementText the statement, as it ran
     * @param recorded      what it drew, read and answered
     * @return whether it was logged; when not, the enclosing statement is logged with it as before
     */
    boolean logApart(String statementText, ApartRecording recorded);
}
