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
 * A column's values, or its files, that {@code KIND => 'ICEBERG'} cannot hold as one type:
 * {@code Incompatible data types detected: TEXT and FIXED.} A file is read to its end before its first such
 * conflict refuses the call, so a value no Iceberg column takes or a malformed record later in the file is refused
 * instead.
 */
public final class InferenceConflict extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * A conflict of two types, the newcomer named first.
     *
     * @param newcomer the name of the type that did not fit
     * @param held the name of the type the column held
     */
    InferenceConflict(final String newcomer, final String held) {
        super("Incompatible data types detected: " + newcomer + " and " + held + ".");
    }
}
