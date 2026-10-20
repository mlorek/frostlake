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
 * A MAP is read by a key of its own key type, judged as the statement compiles (live-verified): a NUMBER-keyed map
 * takes a fixed-point number of scale 0, or one written out with a fraction of zero, and reads the member the number
 * names, m[1], m[2.0] and m[i] alike; a text, a FLOAT, a VARIANT, a BOOLEAN, NULL and a number with a fraction are
 * refused naming both types, as a text-keyed map refuses a number, by GET, a subscript, a path and MAP_CONTAINS_KEY
 * alike. MAP_KEYS answers the keys as the map declares them, numbers for a NUMBER-keyed map.
 */
public class MapKeyReadTest extends MeasuredCellsTestSupport {

    /** Each read of a NUMBER-keyed map and its answer. */
    @Test
    public void aNumberKeyedMapIsReadByItsNumbers() {
        engine.execute("CREATE OR REPLACE TABLE mk_t (m MAP(INT, VARCHAR), i INT, s VARCHAR)");
        engine.execute("INSERT INTO mk_t SELECT OBJECT_CONSTRUCT('1','one','2','two')::MAP(INT,VARCHAR), 1, '2'");
        assertCells(new String[][] {
            {"SELECT OBJECT_CONSTRUCT('1','one','2','two')::MAP(INT,VARCHAR) AS m, m[1], m['1'], GET(m, 1), m[1.0]",
                "REFUSED SQL compilation error: | Function GET cannot be used with arguments of types "
                + "MAP(NUMBER(38,0), VARCHAR(134217728)) and VARCHAR(1)"},
        });
        assertShape("REFUSED SQL compilation error: | Function GET cannot be used with arguments of types "
                    + "MAP(NUMBER(38,0), VARCHAR(134217728)) and VARCHAR(1)",
            "SELECT OBJECT_CONSTRUCT('1','one','2','two')::MAP(INT,VARCHAR) AS m, m[1], m['1'], GET(m, 1), m[1.0]");
        assertCells(new String[][] {
            {"SELECT m[1], m[i], GET(m, 1), m['1'], GET(m, '1'), m[i + 1] FROM mk_t",
                "REFUSED SQL compilation error: | Function GET cannot be used with arguments of types "
                + "MAP(NUMBER(38,0), VARCHAR(16777216)) and VARCHAR(1)"},
        });
        assertShape("REFUSED SQL compilation error: | Function GET cannot be used with arguments of types "
                    + "MAP(NUMBER(38,0), VARCHAR(16777216)) and VARCHAR(1)",
            "SELECT m[1] AS a, m[i] AS b, GET(m, 1) AS c, m['1'] AS d FROM mk_t");
        assertCells(new String[][] {
            {"SELECT m[s], GET(m, s) FROM mk_t",
                "REFUSED SQL compilation error: | Function GET cannot be used with arguments of types "
                + "MAP(NUMBER(38,0), VARCHAR(16777216)) and VARCHAR(16777216)"},
            {"SELECT m[2.0], m[1.5], m[-1], m[NULL] FROM mk_t",
                "REFUSED SQL compilation error: | Function GET cannot be used with arguments of types "
                + "MAP(NUMBER(38,0), VARCHAR(16777216)) and NUMBER(2,1)"},
            {"SELECT m['x'] FROM mk_t",
                "REFUSED SQL compilation error: | Function GET cannot be used with arguments of types "
                + "MAP(NUMBER(38,0), VARCHAR(16777216)) and VARCHAR(1)"},
            {"SELECT GET(m, 'x') FROM mk_t",
                "REFUSED SQL compilation error: | Function GET cannot be used with arguments of types "
                + "MAP(NUMBER(38,0), VARCHAR(16777216)) and VARCHAR(1)"},
            {"SELECT COUNT(*) FROM mk_t WHERE m[i] = 'one'",
                "1"},
            {"SELECT SYSTEM$TYPEOF(m[1]), SYSTEM$TYPEOF(m[i]), SYSTEM$TYPEOF(GET(m, 1)) FROM mk_t",
                "VARCHAR(16777216)[LOB]|VARCHAR(16777216)[LOB]|VARCHAR(16777216)[LOB]"},
            {"SELECT MAP_KEYS(m), SYSTEM$TYPEOF(MAP_KEYS(m)) FROM mk_t",
                "[1,2]|ARRAY(NUMBER(38,0) NOT NULL)[LOB]"},
        });
        assertShape("K:ARRAY",
            "SELECT MAP_KEYS(m) AS k FROM mk_t");
        assertCells(new String[][] {
            {"SELECT {'1':'one'}::MAP(VARCHAR,VARCHAR)[1], GET({'1':'one'}::MAP(VARCHAR,VARCHAR), 1)",
                "REFUSED SQL compilation error: | Function GET cannot be used with arguments of types "
                + "MAP(VARCHAR(134217728), VARCHAR(134217728)) and NUMBER(1,0)"},
            {"SELECT {'5':'five'}::MAP(NUMBER(10,0),VARCHAR)[5], {'5':'five'}::MAP(NUMBER(10,0),VARCHAR)[5.0]",
                "five|five"},
            {"SELECT m[1] FROM mk_t WHERE 1 = 0",
                ""},
            {"SELECT MAP_CONTAINS_KEY(1, m), MAP_CONTAINS_KEY(i, m) FROM mk_t",
                "true|true"},
        });
    }

    /** Each key form and its answer. */
    @Test
    public void eachKeyFormIsJudgedByItsType() {
        engine.execute("CREATE OR REPLACE TABLE mq_t (m MAP(INT, VARCHAR), i INT, s VARCHAR)");
        engine.execute("INSERT INTO mq_t SELECT OBJECT_CONSTRUCT('1','one','2','two')::MAP(INT,VARCHAR), 1, '2'");
        assertCells(new String[][] {
            {"SELECT m[-1] FROM mq_t",
                "null"},
            {"SELECT m[NULL] FROM mq_t",
                "REFUSED SQL compilation error: | Function GET cannot be used with arguments of types "
                + "MAP(NUMBER(38,0), VARCHAR(16777216)) and NULL"},
            {"SELECT m[2.0] FROM mq_t",
                "two"},
            {"SELECT m[1.5] FROM mq_t",
                "REFUSED SQL compilation error: | Function GET cannot be used with arguments of types "
                + "MAP(NUMBER(38,0), VARCHAR(16777216)) and NUMBER(2,1)"},
            {"SELECT m[1::FLOAT] FROM mq_t",
                "REFUSED SQL compilation error: | Function GET cannot be used with arguments of types "
                + "MAP(NUMBER(38,0), VARCHAR(16777216)) and FLOAT"},
            {"SELECT m[1::NUMBER(10,0)] FROM mq_t",
                "one"},
            {"SELECT m[1::NUMBER(37,1)] FROM mq_t",
                "REFUSED SQL compilation error: | Function GET cannot be used with arguments of types "
                + "MAP(NUMBER(38,0), VARCHAR(16777216)) and NUMBER(37,1)"},
            {"SELECT m[1::NUMBER(38,1)] FROM mq_t",
                "REFUSED SQL compilation error: | Function GET cannot be used with arguments of types "
                + "MAP(NUMBER(38,0), VARCHAR(16777216)) and NUMBER(38,1)"},
            {"SELECT GET(m, 1.0) FROM mq_t",
                "one"},
            {"SELECT m[TRUE] FROM mq_t",
                "REFUSED SQL compilation error: | Function GET cannot be used with arguments of types "
                + "MAP(NUMBER(38,0), VARCHAR(16777216)) and BOOLEAN"},
            {"SELECT m['1'::INT] FROM mq_t",
                "one"},
            {"SELECT m[TO_VARIANT(1)] FROM mq_t",
                "REFUSED SQL compilation error: | Function GET cannot be used with arguments of types "
                + "MAP(NUMBER(38,0), VARCHAR(16777216)) and VARIANT"},
            {"SELECT m[3] FROM mq_t",
                "null"},
            {"SELECT m[i], SYSTEM$TYPEOF(m[i]) FROM mq_t",
                "one|VARCHAR(16777216)[LOB]"},
            {"SELECT m[i + 1] FROM mq_t",
                "two"},
            {"SELECT {'5':'five'}::MAP(NUMBER(10,0),VARCHAR)[5.5]",
                "REFUSED SQL compilation error: | Function GET cannot be used with arguments of types "
                + "MAP(NUMBER(10,0), VARCHAR(134217728)) and NUMBER(2,1)"},
            {"SELECT {'5':'five'}::MAP(NUMBER(10,0),VARCHAR)['5']",
                "REFUSED SQL compilation error: | Function GET cannot be used with arguments of types "
                + "MAP(NUMBER(10,0), VARCHAR(134217728)) and VARCHAR(1)"},
            {"SELECT {'5':'five'}::MAP(NUMBER(10,0),VARCHAR)[5::FLOAT]",
                "REFUSED SQL compilation error: | Function GET cannot be used with arguments of types "
                + "MAP(NUMBER(10,0), VARCHAR(134217728)) and FLOAT"},
            {"SELECT {'5':'five'}::MAP(NUMBER(10,0),VARCHAR)[5.00]",
                "five"},
            {"SELECT {'5':'five'}::MAP(NUMBER(37,0),VARCHAR)[5.0]",
                "five"},
            {"SELECT {'5':'five'}::MAP(NUMBER(37,0),VARCHAR)[5.00]",
                "five"},
            {"SELECT {'5':'five'}::MAP(NUMBER(36,0),VARCHAR)[5.00]",
                "five"},
            {"SELECT {'a':'x'}::MAP(VARCHAR,VARCHAR)['a'], {'a':'x'}::MAP(VARCHAR,VARCHAR)[NULL]",
                "REFUSED SQL compilation error: | Function GET cannot be used with arguments of types "
                + "MAP(VARCHAR(134217728), VARCHAR(134217728)) and NULL"},
            {"SELECT {'a':'x'}::MAP(VARCHAR,VARCHAR)[TO_VARIANT('a')]",
                "REFUSED SQL compilation error: | Function GET cannot be used with arguments of types "
                + "MAP(VARCHAR(134217728), VARCHAR(134217728)) and VARIANT"},
            {"SELECT {'a':'x'}::MAP(VARCHAR,VARCHAR)[1.5]",
                "REFUSED SQL compilation error: | Function GET cannot be used with arguments of types "
                + "MAP(VARCHAR(134217728), VARCHAR(134217728)) and NUMBER(2,1)"},
            {"SELECT MAP_KEYS({'a':'x'}::MAP(VARCHAR,VARCHAR)), SYSTEM$TYPEOF(MAP_KEYS({'a':'x'}::MAP(VARCHAR,VARCHAR)"
                + "))",
                "[\"a\"]|ARRAY(VARCHAR NOT NULL)[LOB]"},
            {"SELECT MAP_KEYS({'5':'five'}::MAP(NUMBER(10,0),VARCHAR)), SYSTEM$TYPEOF(MAP_KEYS({'5':'five'}::MAP(NUMBE"
                + "R(10,0),VARCHAR)))",
                "[5]|ARRAY(NUMBER(10,0) NOT NULL)[LOB]"},
            {"SELECT MAP_KEYS(m)[0] + 1, SYSTEM$TYPEOF(MAP_KEYS(m)[0]) FROM mq_t",
                "2|NUMBER(38,0)[SB16]"},
        });
        assertShape("K1:NUMBER(38,0)",
            "SELECT MAP_KEYS(m)[0] + 1 AS k1 FROM mq_t");
        assertCells(new String[][] {
            {"SELECT GET(m, '1') FROM mq_t",
                "REFUSED SQL compilation error: | Function GET cannot be used with arguments of types "
                + "MAP(NUMBER(38,0), VARCHAR(16777216)) and VARCHAR(1)"},
            {"SELECT m['1'] FROM mq_t WHERE 1 = 0",
                "REFUSED SQL compilation error: | Function GET cannot be used with arguments of types "
                + "MAP(NUMBER(38,0), VARCHAR(16777216)) and VARCHAR(1)"},
            {"SELECT MAP_CONTAINS_KEY('1', m) FROM mq_t",
                "REFUSED SQL compilation error: | Function MAP_CONTAINS_KEY cannot be used with arguments of types "
                + "VARCHAR(1) and MAP(NUMBER(38,0), VARCHAR(16777216))"},
            {"SELECT m:\"1\" FROM mq_t",
                "REFUSED SQL compilation error: | Function GET cannot be used with arguments of types "
                + "MAP(NUMBER(38,0), VARCHAR(16777216)) and VARCHAR(1)"},
            {"SELECT GET_PATH(m, '1') FROM mq_t",
                "REFUSED Invalid extraction path '1': invalid field at position 0."},
        });
    }
}
