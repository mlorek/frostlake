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

package dev.frostlake.rt.py;

/**
 * What a statement a Python handler issued came to: its result, or the engine's refusal as text. The refusal goes
 * back into Python as a value, never as a host exception: an exception thrown across into Python while another
 * language's frame lies below it on the thread (a JavaScript procedure's {@code snowflake.execute} calling the
 * procedure) failed on the polyglot runtime's own sharing check the second time it happened.
 */
public final class SnowparkOutcome {

    private final PythonResultSet result;

    private final String refusal;

    private SnowparkOutcome(final PythonResultSet result, final String refusal) {
        this.result = result;
        this.refusal = refusal;
    }

    /**
     * @param result what the statement answered
     * @return the outcome of a statement that ran
     */
    static SnowparkOutcome of(final PythonResultSet result) {
        return new SnowparkOutcome(result, null);
    }

    /**
     * @param refusal the engine's refusal, as Python's {@code str()} of the exception reads it
     * @return the outcome of a statement the engine refused
     */
    static SnowparkOutcome refusedWith(final String refusal) {
        return new SnowparkOutcome(null, refusal);
    }

    /** @return whether the engine refused the statement */
    public boolean refused() {
        return refusal != null;
    }

    /** @return the engine's refusal, or null when the statement ran */
    public String refusal() {
        return refusal;
    }

    /** @return what the statement answered, or null when it was refused */
    public PythonResultSet result() {
        return result;
    }
}
