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

import dev.frostlake.executor.procedural.AsyncJob;
import dev.frostlake.executor.procedural.Cursor;
import dev.frostlake.executor.procedural.UserDefinedException;

import java.util.List;
import java.util.Map;

/**
 * What a calling statement's side of the procedural executor held when a procedure's body started: the depth its
 * own blocks count from, the place and kind of a failure it was unwinding, the exceptions its handlers were
 * handling, and the exceptions and the cursors its blocks have declared. The body runs with a frame of its own,
 * and this one comes back when it ends.
 */
public final class ProcedureFrame {

    private final int base;
    private final int failureLine;
    private final int failurePosition;
    private final String failureKind;
    private final List<Exception> handled;
    private final int scopedBase;
    private final List<Map<String, UserDefinedException>> exceptionScopes;
    private final List<Map<String, Cursor>> cursorScopes;
    private final HandlerLets handlerLets;
    private final List<AsyncJob> asyncJobs;

    ProcedureFrame(final int base, final int failureLine, final int failurePosition, final String failureKind,
                   final List<Exception> handled, final int scopedBase,
                   final List<Map<String, UserDefinedException>> exceptionScopes,
                   final List<Map<String, Cursor>> cursorScopes, final HandlerLets handlerLets,
                   final List<AsyncJob> asyncJobs) {
        this.exceptionScopes = exceptionScopes;
        this.cursorScopes = cursorScopes;
        this.handlerLets = handlerLets;
        this.asyncJobs = asyncJobs;
        this.base = base;
        this.failureLine = failureLine;
        this.failurePosition = failurePosition;
        this.failureKind = failureKind;
        this.handled = handled;
        this.scopedBase = scopedBase;
    }

    /** The caller's mark of a body whose transaction scope its CALL opened (see ProceduralExecutor#markBodyScoped). */
    int getScopedBase() {
        return scopedBase;
    }

    /** The asynchronous jobs the caller started and has not awaited. */
    List<AsyncJob> getAsyncJobs() {
        return asyncJobs;
    }

    /** What the caller's running handlers may not read (see HandlerLets). */
    HandlerLets getHandlerLets() {
        return handlerLets;
    }

    /** The cursors the caller's running blocks have declared, the innermost block's first. */
    List<Map<String, Cursor>> getCursorScopes() {
        return cursorScopes;
    }

    /** The exceptions the caller's running blocks have declared, the innermost block's first. */
    List<Map<String, UserDefinedException>> getExceptionScopes() {
        return exceptionScopes;
    }

    /** The exceptions the caller was handling, innermost first: the body it calls handles none of them. */
    List<Exception> getHandled() {
        return handled;
    }

    int getBase() {
        return base;
    }

    int getFailureLine() {
        return failureLine;
    }

    int getFailurePosition() {
        return failurePosition;
    }

    String getFailureKind() {
        return failureKind;
    }
}
