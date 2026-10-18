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

/** The shape of value a security object's property takes: a network rule's, a policy's or a secret's. */
public enum SecurityPropertyType {

    /** A string literal. */
    TEXT,
    /** An integer literal. */
    INTEGER,
    /** An object name, written as an identifier or as a string. */
    NAME,
    /** A parenthesised list of string literals, possibly empty. */
    TEXT_LIST,
    /** One word of a fixed set, written bare or as a string. */
    CHOICE,
    /** TRUE or FALSE. */
    BOOLEAN,
    /** A parenthesised list of words of a fixed set, written as strings. */
    CHOICE_LIST,
    /**
     * A Restricted Session Scope: one of the predefined names or a fully qualified name, written as a word or a
     * string, or NULL.
     */
    SESSION_SCOPE
}
