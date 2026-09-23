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

package dev.frostlake.parser;

/**
 * The shape of a name with an empty middle part that its position does not take — which decides the lines
 * live stacks around the refusal (see {@link EmptySchemaPartSyntax}).
 */
public enum EmptyPartShape {

    /** An object name, a fifth column part or a SHOW scope: refused on its own, and ending the report. */
    NAME,

    /** A three-part column reference, {@code t..c}, refused at the token after it. */
    COLUMN,

    /** A three-part column reference leading an argument of a select item's call: a second line at its dot. */
    COLUMN_IN_CALL,

    /** A three-part column reference that is a select item's CAST operand: its line twice. */
    COLUMN_IN_CAST,

    /** A qualified star over {@code db..t.c}: refused at the dot before the star and at what follows it. */
    STAR
}
