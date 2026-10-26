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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * MAP_PICK and MAP_DELETE over several keys judge the one type the keys' values fold to, as a conditional's
 * branches fold, and refuse it as GET refuses a key of another type: 'a' beside 2 folds to NUMBER(18,5), no key of
 * a text map, and 1 beside 2.5 to NUMBER(2,1), no key of an INT map, while 'a' beside TRUE is a text and 1 beside
 * '2' a number (all live-verified).
 */
public class MapKeyListTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void severalKeys() {
        assertAnswer("REFUSED SQL compilation error: | Function MAP_PICK cannot be used with arguments of types "
                + "MAP(VARCHAR(134217728), NUMBER(38,0)) and NUMBER(18,5)",
            "SELECT MAP_PICK({'a':1,'b':2}::MAP(VARCHAR,INT), 'a', 2)");
        assertAnswer("REFUSED SQL compilation error: | Function MAP_DELETE cannot be used with arguments of types "
                + "MAP(VARCHAR(134217728), NUMBER(38,0)) and NUMBER(18,5)",
            "SELECT MAP_DELETE({'a':1,'b':2}::MAP(VARCHAR,INT), 'a', 2)");
        assertAnswer("REFUSED SQL compilation error: | Function MAP_PICK cannot be used with arguments of types "
                + "MAP(VARCHAR(134217728), NUMBER(38,0)) and NUMBER(18,5)",
            "SELECT MAP_PICK({'a':1,'b':2}::MAP(VARCHAR,INT), 2, 'a')");
        assertAnswer("{\"a\":1,\"b\":2}", "SELECT MAP_PICK({'a':1,'b':2}::MAP(VARCHAR,INT), 'a', 'b', NULL)");
        assertAnswer("{\"b\":2}", "SELECT MAP_DELETE({'a':1,'b':2}::MAP(VARCHAR,INT), 'a', NULL)");
        assertAnswer("REFUSED SQL compilation error: | Function MAP_PICK cannot be used with arguments of types "
                + "MAP(VARCHAR(134217728), NUMBER(38,0)) and DATE",
            "SELECT MAP_PICK({'a':1,'b':2}::MAP(VARCHAR,INT), 'a', DATE '2020-01-01')");
        assertAnswer("{\"a\":1}", "SELECT MAP_PICK({'a':1,'b':2}::MAP(VARCHAR,INT), 'a', TRUE)");
        assertAnswer("REFUSED SQL compilation error: | Function MAP_PICK cannot be used with arguments of types "
                + "MAP(VARCHAR(134217728), NUMBER(38,0)) and NUMBER(1,0)",
            "SELECT MAP_PICK({'a':1,'b':2}::MAP(VARCHAR,INT), 1, 2)");
        assertAnswer("REFUSED SQL compilation error: | Function MAP_PICK cannot be used with arguments of types "
                + "MAP(VARCHAR(134217728), NUMBER(38,0)) and NUMBER(18,5)",
            "SELECT MAP_PICK({'a':1,'b':2}::MAP(VARCHAR,INT), 'a', 2.5)");
        assertAnswer("REFUSED SQL compilation error: | Function MAP_PICK cannot be used with arguments of types "
                + "MAP(VARCHAR(134217728), NUMBER(38,0)) and NUMBER(18,5)",
            "SELECT MAP_PICK({'a':1,'b':2}::MAP(VARCHAR,INT), 'a', 2, 'b')");
        assertAnswer("{\"a\":1}", "SELECT MAP_PICK({'a':1,'b':2}::MAP(VARCHAR,INT), 'a', 'bb')");
        assertAnswer("REFUSED SQL compilation error: | Function MAP_PICK cannot be used with arguments of types "
                + "MAP(NUMBER(38,0), NUMBER(38,0)) and NUMBER(2,1)",
            "SELECT MAP_PICK(OBJECT_CONSTRUCT('1', 1, '2', 2)::MAP(INT, INT), 1, 2.5)");
        assertAnswer("{\"1\":1,\"2\":2}", "SELECT MAP_PICK(OBJECT_CONSTRUCT('1', 1, '2', 2)::MAP(INT, INT), 1, '2')");
        assertAnswer("REFUSED SQL compilation error: | Function MAP_PICK cannot be used with arguments of types "
                + "MAP(NUMBER(38,0), NUMBER(38,0)) and NUMBER(18,5)",
            "SELECT MAP_PICK(OBJECT_CONSTRUCT('1', 1, '2', 2)::MAP(INT, INT), 1, 'x')");
        assertAnswer("{}", "SELECT MAP_DELETE(OBJECT_CONSTRUCT('1', 1, '2', 2)::MAP(INT, INT), 1, '2')");
        assertAnswer("{\"1\":1,\"2\":2}", "SELECT MAP_PICK(OBJECT_CONSTRUCT('1', 1, '2', 2)::MAP(INT, INT), 1, 2)");
        assertAnswer("{\"1\":1}", "SELECT MAP_PICK(OBJECT_CONSTRUCT('1', 1, '2', 2)::MAP(INT, INT), 1, NULL)");
        assertAnswer("NUMBER(18,5)[SB4]", "SELECT SYSTEM$TYPEOF(IFF(FALSE, 'a', 2))");
        assertAnswer("NUMBER(18,5)[SB4]", "SELECT SYSTEM$TYPEOF(IFF(FALSE, 'a', 2.5))");
        assertAnswer("DATE[SB4]", "SELECT SYSTEM$TYPEOF(IFF(FALSE, 'a', DATE '2020-01-01'))");
        assertAnswer("VARCHAR(134217728)[LOB]", "SELECT SYSTEM$TYPEOF(IFF(FALSE, 'a', TRUE))");
        assertAnswer("NUMBER(2,1)[SB1]", "SELECT SYSTEM$TYPEOF(IFF(FALSE, 1, 2.5))");
    }
}
