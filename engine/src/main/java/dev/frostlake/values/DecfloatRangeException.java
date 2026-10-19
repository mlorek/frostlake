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

package dev.frostlake.values;

/**
 * A DECFLOAT past the type's range: its last digit's exponent above 16384 with more than 38 digits left to hold it.
 * The operation that produced it names itself in the refusal the caller raises —
 * {@code Decfloat out of representable range, operation: 1e10000 * 1e10000}, a text's cast
 * {@code Numeric value '1e20000' is not recognized} (live-verified).
 */
public final class DecfloatRangeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DecfloatRangeException() {
        super("Decfloat out of representable range");
    }

    /**
     * The refusal naming the operation.
     *
     * @param operation the operation as the account writes it, such as {@code EXP(100000)}
     * @return the refusal
     */
    public static RuntimeException naming(final String operation) {
        return new RuntimeException("Decfloat out of representable range, operation: " + operation);
    }
}
