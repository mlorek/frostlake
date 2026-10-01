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

package dev.frostlake.executor.expressions;

import dev.frostlake.types.IntervalQualifier;

/**
 * An interval VALUE — compared, deduplicated and hashed by its span — that knows the fields its expression
 * declares: a unit-suffixed literal ({@code INTERVAL '1' DAY}) or the result of arithmetic on one. The fields
 * decide its text ({@code +1} for a DAY, {@code +1.000000000} for a SECOND) and the number it casts to.
 */
interface IntervalLiteral {

    /** @return the fields the value spans */
    IntervalQualifier qualifier();
}
