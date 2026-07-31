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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** ARRAY_SORT(array [, sort_ascending [, nulls_first]]). */
public class ArraySortTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void ascendingByDefault() {
        assertEquals("[1,2,3]", scalar("SELECT ARRAY_SORT(ARRAY_CONSTRUCT(3, 1, 2))").toString());
    }

    @Test
    public void descendingWhenNotAscending() {
        assertEquals("[3,2,1]", scalar("SELECT ARRAY_SORT(ARRAY_CONSTRUCT(3, 1, 2), FALSE)").toString());
    }

    @Test
    public void undefinedLastWhenAscending() {
        // A SQL NULL array ELEMENT is the VARIANT `undefined`, and nulls_first defaults to FALSE for
        // ascending order, so it sorts to the end — live-verified:
        // ARRAY_SORT(ARRAY_CONSTRUCT(2, NULL, 1)) is [1,2,undefined].
        assertEquals("[1,2,undefined]", scalar("SELECT ARRAY_SORT(ARRAY_CONSTRUCT(2, NULL, 1))").toString());
    }

    @Test
    public void undefinedFirstWhenNullsFirstRequested() {
        // nulls_first moves the `undefined` — live: ARRAY_SORT(ARRAY_CONSTRUCT(2,NULL,1), TRUE, TRUE)
        // is [undefined,1,2] and ARRAY_SORT(ARRAY_CONSTRUCT(2,NULL,1), FALSE, FALSE) is [2,1,undefined].
        assertEquals("[undefined,1,2]",
            scalar("SELECT ARRAY_SORT(ARRAY_CONSTRUCT(2, NULL, 1), TRUE, TRUE)").toString());
        assertEquals("[2,1,undefined]",
            scalar("SELECT ARRAY_SORT(ARRAY_CONSTRUCT(2, NULL, 1), FALSE, FALSE)").toString());
    }

    @Test
    public void jsonNullSortsAsAValueAndIgnoresNullsFirst() {
        // A JSON null is a VALUE, ranked above every other variant type, and nulls_first does NOT move it
        // — live-verified: ARRAY_SORT(PARSE_JSON('[2,null,1]')) is [1,2,null], the same call
        // with nulls_first => TRUE is STILL [1,2,null], and descending is [null,2,1]. With both nulls
        // present, ARRAY_SORT(ARRAY_CONSTRUCT(2,NULL,PARSE_JSON('null'),1)) is [1,2,null,undefined].
        assertEquals("[1,2,null]", scalar("SELECT ARRAY_SORT(PARSE_JSON('[2,null,1]'))").toString());
        assertEquals("[1,2,null]", scalar("SELECT ARRAY_SORT(PARSE_JSON('[2,null,1]'), TRUE, TRUE)").toString());
        assertEquals("[null,2,1]", scalar("SELECT ARRAY_SORT(PARSE_JSON('[2,null,1]'), FALSE)").toString());
        assertEquals("[1,2,null,undefined]",
            scalar("SELECT ARRAY_SORT(ARRAY_CONSTRUCT(2, NULL, PARSE_JSON('null'), 1))").toString());
        assertEquals("[undefined,1,2,null]",
            scalar("SELECT ARRAY_SORT(ARRAY_CONSTRUCT(2, NULL, PARSE_JSON('null'), 1), TRUE, TRUE)").toString());
    }

    @Test
    public void jsonNullRanksAboveStringsAndObjects() {
        // Live: ARRAY_SORT(['z', PARSE_JSON('null')]) is ["z",null] — a lexical comparison of the text
        // "null" would have put it FIRST — and ARRAY_SORT([{'a':1}, 1, null]) is [1,{"a":1},null].
        assertEquals("[\"z\",null]",
            scalar("SELECT ARRAY_SORT(ARRAY_CONSTRUCT('z', PARSE_JSON('null')))").toString());
        assertEquals("[1,{\"a\":1},null]",
            scalar("SELECT ARRAY_SORT(ARRAY_CONSTRUCT(OBJECT_CONSTRUCT('a', 1), 1, PARSE_JSON('null')))")
                .toString());
    }

    @Test
    public void nullInputIsNull() {
        assertNull(scalar("SELECT ARRAY_SORT(NULL)"));
    }
}
