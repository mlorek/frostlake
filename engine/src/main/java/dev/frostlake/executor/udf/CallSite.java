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

import java.util.Objects;

/**
 * The place in a statement that calls a routine, by which a handler runtime keeps its state (see
 * {@link StatementHandlerStates}). An account gives each call written in a statement a state of its own, kept for
 * the statement's rows (live-verified): two calls of one text in two select items, in two arms of a UNION, in two
 * common table expressions or in a view and its query are apart, and so is a call written once in a SQL function's
 * body for each place that calls the function, while the rows one place reads share its state.
 *
 * <p>A call is placed by where the fragment of the statement holding it begins and by the call itself. A call in a
 * SQL function's body is placed in the call of that function too, and is told by its name and its count among the
 * calls of that name in the body (see {@code CallOrdinalWalk}): the body is read anew for each row's arguments, so
 * the call is another node on each row while its count stays.
 */
public final class CallSite {

    /** The call whose body holds this one, or null for a call written in the statement itself. */
    private final CallSite enclosing;
    /** The line the fragment holding the call begins on, or 0 when its place is unknown. */
    private final int line;
    /** The column that fragment begins at. */
    private final int column;
    /** The call as parsed. */
    private final Object call;
    /** The function's name, upper-cased. */
    private final String name;
    /** The call's count among the calls of its name, or 0 when not counted. */
    private final int ordinal;

    CallSite(final CallSite enclosing, final SourcePosition origin, final Object call, final String name,
             final int ordinal) {
        this.enclosing = enclosing;
        this.line = origin == null ? 0 : origin.getLine();
        this.column = origin == null ? 0 : origin.getCharPositionInLine();
        this.call = call;
        this.name = name == null ? "" : name;
        this.ordinal = ordinal;
    }

    /** Whether the call is told by its name and count rather than by the parsed node. */
    private boolean countsAlone() {
        return enclosing != null && ordinal > 0;
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof CallSite)) {
            return false;
        }
        final CallSite that = (CallSite) other;
        if (line != that.line || column != that.column || !Objects.equals(enclosing, that.enclosing)) {
            return false;
        }
        if (countsAlone() && that.countsAlone()) {
            return ordinal == that.ordinal && name.equals(that.name);
        }
        return call == that.call;
    }

    @Override
    public int hashCode() {
        final int placed = 31 * (31 * Objects.hashCode(enclosing) + line) + column;
        return 31 * placed + (countsAlone() ? 31 * name.hashCode() + ordinal : System.identityHashCode(call));
    }
}
