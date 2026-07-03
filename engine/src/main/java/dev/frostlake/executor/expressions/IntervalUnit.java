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

/**
 * The unit of an {@link IntervalExpression} (e.g. {@code DAY}, {@code HOUR}). Singular and plural spellings are
 * distinct constants so the parsed form round-trips.
 */
public enum IntervalUnit {
    YEAR, YEARS,
    MONTH, MONTHS,
    DAY, DAYS,
    HOUR, HOURS,
    MINUTE, MINUTES,
    SECOND, SECONDS
}
