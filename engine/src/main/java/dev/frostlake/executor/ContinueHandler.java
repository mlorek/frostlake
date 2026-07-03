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
 * A block's {@code WHEN … CONTINUE THEN} exception handling, consulted by loop executors so that a
 * loop-body error matching a CONTINUE handler resumes at the next loop iteration (Snowflake CONTINUE
 * semantics). Implemented by the BEGIN…END handler and pushed onto {@link ProceduralExecutor} for the
 * duration of a block that declares an EXCEPTION section.
 */
public interface ContinueHandler {

    /**
     * Offer a raised exception to this block. If a matching {@code CONTINUE} handler exists, run its
     * statements and return {@code true} — the caller then proceeds past the failure (for a loop body,
     * the next iteration). Return {@code false} if no CONTINUE handler matches, so the caller
     * propagates the exception (an EXIT handler / no handler unwinds as usual).
     */
    boolean tryHandleAsContinue(Exception e);
}
