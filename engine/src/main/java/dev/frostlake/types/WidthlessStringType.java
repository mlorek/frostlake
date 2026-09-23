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
 * An expression's string that has no width at all: a cast to a bare {@code VARCHAR}, the functions that declare a
 * bare VARCHAR result ({@code TO_CHAR}, {@code REPLACE}, {@code CURRENT_DATABASE}, …) and what carries such a
 * string through. SYSTEM$TYPEOF spells it as the bare word {@code VARCHAR}, while a refusal sentence and the plan
 * spell the width it counts as, {@code VARCHAR(134217728)}, and a table built over it stores the full-width
 * VARCHAR(16777216) (live-verified).
 */
public final class WidthlessStringType extends StringType {

    /** The one widthless string type. */
    public static final WidthlessStringType WIDTHLESS = new WidthlessStringType();

    private WidthlessStringType() {
        super("VARCHAR", StringResultWidths.UNBOUNDED);
    }

    /**
     * A widthless string LEADING a fold stays widthless — {@code IFF(TRUE, 'a'::VARCHAR, 'bb')} and
     * {@code NVL(NULL::VARCHAR, 'x')} are bare VARCHAR — where a sized one leading it widens to the width nothing
     * bounds, {@code IFF(TRUE, 'bb', 'a'::VARCHAR)} being VARCHAR(134217728) (live-verified).
     */
    @Override
    public DataType getCommonType(final DataType other) {
        if (other instanceof StringType) {
            return this;
        }
        return super.getCommonType(other);
    }
}
