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

package dev.frostlake.metastore.model;

/** What a {@link PropertyValue} holds. */
public enum PropertyValueKind {
    /** A string literal. */
    TEXT,
    /** An integer literal. */
    NUMBER,
    /** TRUE or FALSE. */
    BOOLEAN,
    /** A bare word or a name, canonical. */
    WORD,
    /** A parenthesised list of values. */
    LIST,
    /** A parenthesised list of nested {@code KEY = value} properties. */
    PROPERTIES,
    /** A parenthesised list of {@code 'key' = 'value'} string pairs. */
    PAIRS
}
