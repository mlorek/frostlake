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
 * A STRUCTURED array type — {@code ARRAY(INT)} — as opposed to the plain semi-structured
 * {@link ArrayType}. Live-verified: {@code SYSTEM$TYPEOF(CAST([1,2] AS ARRAY(INT)))} is
 * {@code ARRAY(NUMBER(38,0))[LOB]} while {@code SYSTEM$TYPEOF([1,2])} is plain {@code ARRAY[LOB]},
 * and only the structured one may carry the {@code RENAME FIELDS} / {@code ADD FIELDS} cast
 * modifiers.
 */
public class StructuredArrayType extends ArrayType {

    public StructuredArrayType(final DataType elementType) {
        super(elementType);
    }

    @Override
    public String toString() {
        return StructuredTypes.describe(this);
    }
}
