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

package dev.frostlake.types;

/**
 * How a BINARY's width is spelled where the plan names the type — {@code SYSTEM$TYPEOF} and the
 * argument-type refusals (live-verified):
 *
 * <pre>
 *                                                   SYSTEM$TYPEOF         refusal
 *   DECLARED  a column, X'..', CAST(x AS BINARY(n))  BINARY(n)             BINARY(n)
 *   UNSIZED   TO_BINARY, CAST(x AS BINARY), DECRYPT  BINARY                BINARY(67108864)
 *   MAXIMUM   COALESCE(b5, TO_BINARY(s))             BINARY(67108864)      BINARY(67108864)
 * </pre>
 *
 * <p>The spelling is carried beside the width a binary is held at, which it never changes.
 */
public enum BinaryWidthSpelling {
    /** Sized at its own width. */
    DECLARED,
    /** Never sized by the plan: the result of a conversion, a cast to a bare BINARY, a SQL UDF. */
    UNSIZED,
    /** Sized at the 64MB maximum: a fold that met an unsized branch after a sized one. */
    MAXIMUM;

    /**
     * The spelling a fold takes where a branch spelled {@code later} follows branches spelled this one.
     * The lead decides: an unsized lead keeps the fold unsized, and a sized lead meeting anything unsized
     * takes it to the maximum — live, {@code IFF(c, TO_BINARY(s), X'00')} is bare BINARY while
     * {@code IFF(c, X'00', TO_BINARY(s))} is BINARY(67108864).
     *
     * @param later the following branch's spelling
     * @return the folded spelling
     */
    public BinaryWidthSpelling meeting(final BinaryWidthSpelling later) {
        if (this != DECLARED) {
            return this;
        }
        return later == DECLARED ? DECLARED : MAXIMUM;
    }
}
