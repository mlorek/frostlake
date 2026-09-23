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

package dev.frostlake.parser;

import org.antlr.v4.runtime.Token;

/**
 * A fault {@link SignatureRecovery} meets while it reads a signature the way the account does: the token the account
 * names, or — when {@code unmodelled} — a shape that reading does not cover, which abandons it.
 */
final class SignatureFault extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient Token token;
    private final boolean unmodelled;

    SignatureFault(final Token token, final boolean unmodelled) {
        super(null, null, false, false);
        this.token = token;
        this.unmodelled = unmodelled;
    }

    /** The token the account names for this fault, or null for an unmodelled shape. */
    Token token() {
        return token;
    }

    /** Whether the reading met a shape it does not cover. */
    boolean unmodelled() {
        return unmodelled;
    }
}
