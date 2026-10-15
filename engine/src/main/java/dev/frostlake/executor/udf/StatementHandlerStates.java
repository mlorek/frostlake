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

package dev.frostlake.executor.udf;

import dev.frostlake.executor.expressions.SourcePosition;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * What handler runtimes keep while a statement runs, and for no longer. An account runs each call of a handler
 * function written in a statement in an environment of its own, made for that statement (live-verified): the
 * globals a JavaScript function sets are there for the statement's later rows, and are gone with the statement;
 * another function, another call of the same function in the same statement (see {@link CallSite}), a later
 * statement and another session never see them. A runtime therefore asks here for the state of the call being
 * evaluated, and gets one that lives as long as the statement on this thread does.
 *
 * <p>A statement that runs inside another — one a procedure or a block runs — has states of its own, and ending
 * the outer statement ends whatever an inner one left behind; so does each evaluation of a block's own
 * expression, which an account runs as a query of its own.
 */
public final class StatementHandlerStates {

    private static final ThreadLocal<Deque<HandlerStateScope>> SCOPES = new ThreadLocal<Deque<HandlerStateScope>>() {
        @Override
        protected Deque<HandlerStateScope> initialValue() {
            return new ArrayDeque<>();
        }
    };

    private static final ThreadLocal<Object> SITE = new ThreadLocal<Object>();

    private StatementHandlerStates() {
    }

    /**
     * A statement starts on this thread.
     *
     * @return its scope, to hand to {@link #end}
     */
    public static HandlerStateScope begin() {
        final HandlerStateScope scope = new HandlerStateScope();
        SCOPES.get().push(scope);
        return scope;
    }

    /**
     * The statement {@link #begin} answered this scope for has ended: its states, and those of any statement
     * begun inside it that never ended, are closed.
     *
     * @param scope the statement's scope
     */
    public static void end(final HandlerStateScope scope) {
        final Deque<HandlerStateScope> scopes = SCOPES.get();
        if (scope == null || !scopes.contains(scope)) {
            return;
        }
        HandlerStateScope ended;
        do {
            ended = scopes.pop();
            ended.close();
        } while (ended != scope);
    }

    /**
     * The call of a routine starts being evaluated. Evaluated inside the body of another call — a SQL function's
     * — it is placed in that call as well.
     *
     * @param call    the call as parsed
     * @param name    the routine's name
     * @param ordinal the call's count among the calls of that name in the text it was parsed from, or 0
     * @param origin  where the fragment of the statement holding the call begins, or null when unknown
     * @return the place evaluated until now, to hand to {@link #exitSite}
     */
    public static Object enterCall(final Object call, final String name, final int ordinal,
                                   final SourcePosition origin) {
        final Object outer = SITE.get();
        SITE.set(new CallSite(outer instanceof CallSite ? (CallSite) outer : null, origin, call,
            name == null ? null : name.toUpperCase(Locale.ROOT), ordinal));
        return outer;
    }

    /**
     * The call {@link #enterCall} was told of has been evaluated.
     *
     * @param outer what {@link #enterCall} answered
     */
    public static void exitSite(final Object outer) {
        if (outer == null) {
            SITE.remove();
        } else {
            SITE.set(outer);
        }
    }

    /**
     * The state of the call being evaluated, made on its first use in the running statement.
     *
     * @param owner   the routine
     * @param variant what else the state was made for, compared by value
     * @param factory makes the state
     * @return the state, or null when no statement runs on this thread, and the caller keeps none
     */
    public static AutoCloseable state(final Object owner, final Object variant, final HandlerStateFactory factory) {
        final HandlerStateScope scope = SCOPES.get().peek();
        if (scope == null) {
            return null;
        }
        return scope.get(new HandlerStateKey(owner, SITE.get(), variant), factory);
    }
}
