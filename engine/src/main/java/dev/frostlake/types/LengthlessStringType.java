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
 * A VARCHAR the plan carries with no length of its own — the KEY and PATH columns of FLATTEN and the
 * VALUE column of SPLIT_TO_TABLE — which SYSTEM$TYPEOF spells as the bare word {@code VARCHAR} while a
 * table built over it declares the full-width VARCHAR(16777216) (live-verified). It IS a full-width
 * string everywhere a width is asked for; only its spelling in a type description differs.
 */
public class LengthlessStringType extends StringType {

    public LengthlessStringType() {
        super("VARCHAR", 16777216);
    }
}
