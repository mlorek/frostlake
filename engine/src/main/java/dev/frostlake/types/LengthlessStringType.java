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
 * A VARCHAR the plan carries with no length of its own — the KEY and PATH columns of FLATTEN, the VALUE column
 * of SPLIT_TO_TABLE, and the name, comment and definition columns of the INFORMATION_SCHEMA views — which
 * SYSTEM$TYPEOF spells as the bare word {@code VARCHAR}, a driver's result metadata reads at the width nothing
 * bounds, VARCHAR(134217728), and a table built over it declares as VARCHAR(16777216) (live-verified). Like a
 * widthless string it stays itself when it leads a fold: {@code COALESCE(key, 'x')} is bare VARCHAR too.
 */
public class LengthlessStringType extends StringType {

    public LengthlessStringType() {
        super("VARCHAR", StringResultWidths.UNBOUNDED);
    }

    @Override
    public DataType getCommonType(final DataType other) {
        if (other instanceof StringType) {
            return this;
        }
        return super.getCommonType(other);
    }
}
