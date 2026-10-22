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


package dev.frostlake.executor;

/**
 * What an operand of date arithmetic is to {@link NullDateOperations}: a variable bound into the statement, by its
 * declared type and whether it holds NULL, or a literal of its kind.
 */
enum DateOperandKind {
    /** A DATE variable holding NULL. */
    NULL_DATE,
    /** A DATE variable holding a date, or a DATE literal. */
    DATE,
    /** An exact-number variable holding NULL. */
    NULL_NUMBER,
    /** An exact-number variable holding a number, or a number literal. */
    NUMBER,
    /** A TIME or timestamp variable holding NULL. */
    NULL_TEMPORAL,
    /** An INTERVAL literal. */
    INTERVAL
}
