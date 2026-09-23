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

package dev.frostlake.executor.copy;

/**
 * The family of type INFER_SCHEMA settles on for a staged column. Each family carries the name the account
 * gives it in its schema-inference refusals ({@code FIXED detected instead of an OBJECT},
 * {@code Incompatible data types detected: TEXT and FIXED.}), which is not always its SQL spelling.
 */
public enum InferredKind {
    /** A NUMBER(p, s), read from a plain decimal of at most 38 digits. */
    NUMBER("FIXED"),
    /** A REAL, read from a number in scientific notation, a hexadecimal one, NaN, inf, or a plain one past 38 digits. */
    REAL("REAL"),
    /** A BOOLEAN. */
    BOOLEAN("BOOLEAN"),
    /** A DATE. */
    DATE("DATE"),
    /** A TIME. */
    TIME("TIME"),
    /** A timestamp of any flavour, which inference always reports as TIMESTAMP_NTZ. */
    TIMESTAMP("TIMESTAMP_NTZ"),
    /** A text: what anything else, and any mixture a column cannot hold as one type, falls back to. */
    TEXT("TEXT"),
    /** A JSON array. */
    ARRAY("ARRAY"),
    /** A JSON object. */
    OBJECT("OBJECT"),
    /** A VARIANT: what a self-describing format's conflicting column types fall back to. */
    VARIANT("VARIANT"),
    /** A type a self-describing file declares itself, carried as the account spells it. */
    DECLARED("DECLARED");

    private final String internalName;

    InferredKind(final String internalName) {
        this.internalName = internalName;
    }

    /**
     * The family's name in a schema-inference refusal.
     *
     * @return the name
     */
    public String internalName() {
        return internalName;
    }
}
