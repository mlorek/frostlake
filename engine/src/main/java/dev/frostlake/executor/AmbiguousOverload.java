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
 * A call written with named arguments that several of a routine's overloads take equally well: the account refuses
 * it while the statement compiles, over a table with no rows too — {@code More than one function of OV are
 * resolved in prepare state.}, the routine named as the call writes it (live-verified). The same arguments
 * written by position run the first overload in the order of the signatures (see
 * {@link OverloadConversionCosts}).
 */
public final class AmbiguousOverload extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param spelledName the routine's name as the call writes it, an unquoted part upper-cased
     */
    public AmbiguousOverload(final String spelledName) {
        super(SqlCompilationError.of("More than one function of " + spelledName
            + " are resolved in prepare state."));
    }
}
