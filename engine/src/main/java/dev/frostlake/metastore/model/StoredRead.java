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

/**
 * How a column's values are read out of a stored table, which decides whether an element's VARIANT number converts
 * to a DATE, a TIME or a TIMESTAMP_NTZ (see {@code VariantRowElements}). An element or a path read straight off a
 * stored table's column converts, through any number of derived relations, CTEs and views that project the column or
 * the element as it is, with filters, joins and windows beside it. One read off a value that a grouping, a DISTINCT,
 * a row limit or a set operation computed does not, nor one read off an expression over the column (live-verified):
 *
 * <pre>
 *   a[0]::DATE FROM t                                      1970-01-02
 *   a[0]::DATE FROM (SELECT a FROM t), FROM a view of t    1970-01-02
 *   x::DATE FROM (SELECT a[0] AS x FROM t)                 1970-01-02
 *   a[0]::DATE FROM (SELECT a FROM t GROUP BY a)           Failed to cast variant value 1 to DATE
 *   COALESCE(a, a)[0]::DATE FROM t                         Failed to cast variant value 1 to DATE
 * </pre>
 */
public enum StoredRead {
    /** A value computed, or read off a relation that computed it. */
    NONE,
    /** A stored table's column, read whole. */
    COLUMN,
    /** An element or a path of a stored table's column. */
    ELEMENT
}
