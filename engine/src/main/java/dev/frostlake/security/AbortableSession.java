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
package dev.frostlake.security;

/**
 * A session SYSTEM$ABORT_SESSION can end: a connection or an HTTP session, registered with the engine's
 * {@link SessionDirectory} under the number CURRENT_SESSION() answers.
 */
public interface AbortableSession {

    /**
     * Mark the session aborted. Called from inside the aborting statement, on its thread and under whatever lock
     * it holds, so an implementation only records the fact and touches nothing of the engine.
     */
    void abort();

    /**
     * End what the aborted session left behind: roll back its open transaction and release its state. Called
     * between statements, by a front end, never from inside one.
     */
    void endAborted();
}
