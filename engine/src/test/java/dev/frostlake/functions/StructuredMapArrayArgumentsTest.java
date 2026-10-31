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
 * The map and array functions over structured values: MAP_INSERT, MAP_PICK and MAP_DELETE take a key of the map's
 * key type and MAP_INSERT a value its value type converts, cast to it as the row is read; MAP_CAT takes two maps
 * of one family each, the second cast to the first's type; ARRAY_SORT, ARRAY_DISTINCT, ARRAY_REMOVE,
 * ARRAY_REMOVE_AT and ARRAY_REVERSE keep the array's type, ARRAYS_ZIP types its objects' fields, ARRAY_FLATTEN
 * takes no structured ARRAY and ARRAY_REMOVE a value of the elements' family alone (all live-verified).
 */
public class StructuredMapArrayArgumentsTest extends MeasuredCellsTestSupport {

    @Test
    public void mapArgumentsAndArrayTypes() {
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'MAP_INSERT': (MAP(NUMBER(38,0), ARRAY(VARCHAR(134217728))), NUMBER(10,0), ARRAY)",
            "SELECT MAP_INSERT({}::MAP(INT, ARRAY(VARCHAR)), 3000000000, ['a'])");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'MAP_INSERT': (MAP(NUMBER(38,0), ARRAY(VARCHAR(134217728))), NUMBER(1,0), ARRAY)",
            "SELECT MAP_INSERT({}::MAP(INT, ARRAY(VARCHAR)), 3, ['a'])");
        assertAnswer("{\"3000000000\":[\"a\"]}",
            "SELECT MAP_INSERT({}::MAP(INT, ARRAY(VARCHAR)), 3000000000, ['a']::ARRAY(VARCHAR))");
        assertAnswer("{\"3\":[\"a\"]}", "SELECT MAP_INSERT({}::MAP(INT, ARRAY(VARCHAR)), 3, ['a']::ARRAY(VARCHAR))");
        assertAnswer("{\"3000000000\":5}", "SELECT MAP_INSERT({}::MAP(INT, INT), 3000000000, 5)");
        assertAnswer("{\"3\":5}", "SELECT MAP_INSERT({}::MAP(INT, INT), 3, 5)");
        assertAnswer("{\"3\":5000000000}", "SELECT MAP_INSERT({}::MAP(INT, INT), 3, 5000000000)");
        assertAnswer("MAP(VARCHAR, NUMBER(38,0))[LOB]",
            "SELECT SYSTEM$TYPEOF(MAP_INSERT({'k':1}::MAP(VARCHAR,INT), 'j', 'x'))");
        assertAnswer("REFUSED Numeric value 'x' is not recognized",
            "SELECT MAP_INSERT({'k':1}::MAP(VARCHAR,INT), 'j', 'x')");
        assertAnswer("REFUSED SQL compilation error: | Function MAP_INSERT cannot be used with arguments of types "
                + "MAP(VARCHAR(134217728), NUMBER(38,0)) and NUMBER(1,0)",
            "SELECT MAP_INSERT({'k':1}::MAP(VARCHAR,INT), 5, 2)");
        assertAnswer("{\"j\":3,\"k\":1}", "SELECT MAP_INSERT({'k':1}::MAP(VARCHAR,INT), 'j', 2.5)");
        assertAnswer("{\"j\":2,\"k\":1}", "SELECT MAP_INSERT({'k':1}::MAP(VARCHAR,INT), 'j', PARSE_JSON('2'))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'MAP_INSERT': (MAP(VARCHAR(134217728), ARRAY(NUMBER(38,0))), VARCHAR(1), ARRAY)",
            "SELECT MAP_INSERT({'k':[1]}::MAP(VARCHAR,ARRAY(INT)), 'j', [2])");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'MAP_CAT': (MAP(VARCHAR(134217728), NUMBER(38,0)), MAP(VARCHAR(134217728), VARCHAR(134217728)))",
            "SELECT MAP_CAT({'a':1}::MAP(VARCHAR,INT), {'b':'x'}::MAP(VARCHAR,VARCHAR))");
        assertAnswer("MAP(VARCHAR, NUMBER(38,0))[LOB]",
            "SELECT SYSTEM$TYPEOF(MAP_CAT({'a':1}::MAP(VARCHAR,INT), {'b':2}::MAP(VARCHAR,NUMBER(10,2))))");
        assertAnswer("REFUSED SQL compilation error: | Function MAP_PICK cannot be used with arguments of types "
                + "MAP(VARCHAR(134217728), NUMBER(38,0)) and NUMBER(1,0)",
            "SELECT MAP_PICK({'a':1}::MAP(VARCHAR,INT), 1)");
        assertAnswer("REFUSED SQL compilation error: | Function MAP_DELETE cannot be used with arguments of types "
                + "MAP(VARCHAR(134217728), NUMBER(38,0)) and NUMBER(1,0)",
            "SELECT MAP_DELETE({'a':1}::MAP(VARCHAR,INT), 1)");
        assertAnswer("REFUSED SQL compilation error: | Function MAP_PICK cannot be used with arguments of types "
                + "MAP(NUMBER(38,0), NUMBER(38,0)) and VARCHAR(1)",
            "SELECT MAP_PICK(OBJECT_CONSTRUCT('1', 1)::MAP(INT, INT), 'a')");
        assertAnswer("REFUSED SQL compilation error: | Function MAP_DELETE cannot be used with arguments of types "
                + "MAP(NUMBER(38,0), NUMBER(38,0)) and VARCHAR(1)",
            "SELECT MAP_DELETE(OBJECT_CONSTRUCT('1', 1)::MAP(INT, INT), 'a')");
        assertAnswer("REFUSED SQL compilation error: | Function MAP_CONTAINS_KEY cannot be used with arguments of "
                + "types NUMBER(1,0) and MAP(VARCHAR(134217728), NUMBER(38,0))",
            "SELECT MAP_CONTAINS_KEY(1, {'a':1}::MAP(VARCHAR,INT))");
        assertAnswer("ARRAY(NUMBER(38,0))[LOB]", "SELECT SYSTEM$TYPEOF(ARRAY_SORT([3,1,2]::ARRAY(INT)))");
        assertAnswer("2", "SELECT ARRAY_SORT([3,1,2]::ARRAY(INT))[0] + 1");
        assertAnswer("ARRAY(NUMBER(38,0))[LOB]", "SELECT SYSTEM$TYPEOF(ARRAY_SORT([3,1,2]::ARRAY(INT), FALSE))");
        assertAnswer("ARRAY(NUMBER(38,0))[LOB]", "SELECT SYSTEM$TYPEOF(ARRAY_DISTINCT([1,1,2]::ARRAY(INT)))");
        assertAnswer("ARRAY(VARCHAR)[LOB]", "SELECT SYSTEM$TYPEOF(ARRAY_DISTINCT(['a','a']::ARRAY(VARCHAR)))");
        assertAnswer("ARRAY(NUMBER(38,0))[LOB]", "SELECT SYSTEM$TYPEOF(ARRAY_REMOVE([1,2]::ARRAY(INT), 1))");
        assertAnswer("ARRAY(NUMBER(38,0))[LOB]", "SELECT SYSTEM$TYPEOF(ARRAY_REMOVE_AT([1,2]::ARRAY(INT), 0))");
        assertAnswer("ARRAY(NUMBER(38,0))[LOB]", "SELECT SYSTEM$TYPEOF(ARRAY_REVERSE([1,2]::ARRAY(INT)))");
        assertAnswer("3", "SELECT ARRAY_REVERSE([1,2]::ARRAY(INT))[0] + 1");
        assertAnswer("ARRAY(NUMBER(38,0) NOT NULL)[LOB]", "SELECT SYSTEM$TYPEOF(ARRAY_COMPACT([1,NULL]::ARRAY(INT)))");
        assertAnswer("ARRAY(NUMBER(38,0))[LOB]", "SELECT SYSTEM$TYPEOF(ARRAY_SLICE([1,2,3]::ARRAY(INT), 0, 2))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 21 | Invalid argument types for "
                + "function 'ARRAY_FLATTEN': (ARRAY(ARRAY(NUMBER(38,0))))",
            "SELECT SYSTEM$TYPEOF(ARRAY_FLATTEN([[1],[2]]::ARRAY(ARRAY(INT))))");
        assertAnswer("ARRAY(NUMBER(38,0))[LOB]", "SELECT SYSTEM$TYPEOF(ARRAY_APPEND([1]::ARRAY(INT), 2))");
        assertAnswer("ARRAY(NUMBER(38,0))[LOB]", "SELECT SYSTEM$TYPEOF(ARRAY_PREPEND([1]::ARRAY(INT), 2))");
        assertAnswer("ARRAY(NUMBER(38,0))[LOB]", "SELECT SYSTEM$TYPEOF(ARRAY_INSERT([1]::ARRAY(INT), 0, 2))");
        assertAnswer("ARRAY(NUMBER(38,0))[LOB]",
            "SELECT SYSTEM$TYPEOF(ARRAY_EXCEPT([1,2]::ARRAY(INT), [1]::ARRAY(INT)))");
        assertAnswer("ARRAY(OBJECT($1 NUMBER(38,0), $2 NUMBER(38,0)))[LOB]",
            "SELECT SYSTEM$TYPEOF(ARRAYS_ZIP([1]::ARRAY(INT), [2]::ARRAY(INT)))");
        assertAnswer("REFUSED SQL compilation error: | Invalid type [VARCHAR(1)] for function 'ARRAY_REMOVE' at "
                + "position 2",
            "SELECT SYSTEM$TYPEOF(ARRAY_REMOVE([1,2]::ARRAY(INT), 'a'))");
        assertAnswer("REFUSED SQL compilation error: | Invalid type [VARCHAR(1)] for function 'ARRAY_REMOVE' at "
                + "position 2",
            "SELECT ARRAY_REMOVE([1,2]::ARRAY(INT), 'a')");
        assertAnswer("ARRAY(VARCHAR)[LOB]", "SELECT SYSTEM$TYPEOF(ARRAY_SORT(['b','a']::ARRAY(VARCHAR)))");
    }

    @Test
    public void edgesOfTheRules() {
        assertAnswer("[\"b\"]", "SELECT ARRAY_REMOVE(['a','b']::ARRAY(VARCHAR), 'a')");
        assertAnswer("[1,2]", "SELECT ARRAY_REMOVE([1,2]::ARRAY(INT), 1.5)");
        assertAnswer("REFUSED SQL compilation error: | Invalid type [VARIANT] for function 'ARRAY_REMOVE' at position "
                + "2",
            "SELECT ARRAY_REMOVE([1,2]::ARRAY(INT), PARSE_JSON('1'))");
        assertAnswer("REFUSED SQL compilation error: | Invalid type [BOOLEAN] for function 'ARRAY_REMOVE' at position "
                + "2",
            "SELECT ARRAY_REMOVE([1,2]::ARRAY(INT), TRUE)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'MAP_INSERT': (MAP(VARCHAR(134217728), OBJECT(x NUMBER(38,0))), VARCHAR(1), OBJECT)",
            "SELECT MAP_INSERT({'a':{'x':1}}::MAP(VARCHAR, OBJECT(x INT)), 'b', {'x':2})");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'MAP_INSERT': (MAP(VARCHAR(134217728), ARRAY(NUMBER(38,0))), VARCHAR(1), VARIANT)",
            "SELECT MAP_INSERT({'a':[1]}::MAP(VARCHAR, ARRAY(INT)), 'b', PARSE_JSON('[2]'))");
        assertAnswer("null", "SELECT MAP_INSERT({'a':1}::MAP(VARCHAR,INT), NULL, 2)");
        assertAnswer("{}", "SELECT MAP_PICK({'a':1}::MAP(VARCHAR,INT), NULL)");
        assertAnswer("{\"a\":1}", "SELECT MAP_DELETE({'a':1}::MAP(VARCHAR,INT), NULL)");
        assertAnswer("{\"a\":1}", "SELECT MAP_PICK({'a':1,'b':2}::MAP(VARCHAR,INT), ['a'])");
        assertAnswer("{}", "SELECT MAP_PICK({'a':1,'b':2}::MAP(VARCHAR,INT), [1])");
        assertAnswer("{\"a\":1,\"b\":2}", "SELECT MAP_PICK({'a':1,'b':2}::MAP(VARCHAR,INT), 'a', 'b')");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'MAP_CAT': (MAP(VARCHAR(134217728), NUMBER(38,0)), MAP(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT MAP_CAT({'a':1}::MAP(VARCHAR,INT), OBJECT_CONSTRUCT('1', 1)::MAP(INT,INT))");
        assertAnswer("{\"a\":1,\"b\":2}", "SELECT MAP_CAT({'a':1}::MAP(VARCHAR,INT), {'b':1.5}::MAP(VARCHAR,FLOAT))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'MAP_CAT': (MAP(VARCHAR(134217728), NUMBER(38,0)), MAP(VARCHAR(134217728), BOOLEAN))",
            "SELECT MAP_CAT({'a':1}::MAP(VARCHAR,INT), {'b':TRUE}::MAP(VARCHAR,BOOLEAN))");
        assertAnswer("{\"a\":\"x\",\"b\":\"5\"}", "SELECT MAP_INSERT({'a':'x'}::MAP(VARCHAR,VARCHAR), 'b', 5)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'MAP_INSERT': (MAP(VARCHAR(134217728), NUMBER(38,0)), VARCHAR(1), BOOLEAN)",
            "SELECT MAP_INSERT({'k':1}::MAP(VARCHAR,INT), 'j', TRUE)");
        assertAnswer("{\"j\":5,\"k\":1}", "SELECT MAP_INSERT({'k':1}::MAP(VARCHAR,INT), 'j', '5')");
        assertAnswer("{\"j\":2.3,\"k\":1}", "SELECT MAP_INSERT({'k':1}::MAP(VARCHAR,NUMBER(3,1)), 'j', 2.25)");
        assertAnswer("ARRAY(OBJECT($1 NUMBER(38,0), $2 VARCHAR))[LOB]",
            "SELECT SYSTEM$TYPEOF(ARRAYS_ZIP([1]::ARRAY(INT), ['a']::ARRAY(VARCHAR)))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 21 | Invalid argument types for "
                + "function 'ARRAYS_ZIP': (ARRAY(NUMBER(38,0)), ARRAY)",
            "SELECT SYSTEM$TYPEOF(ARRAYS_ZIP([1]::ARRAY(INT), [2]))");
        assertAnswer("ARRAY(OBJECT($1 NUMBER(38,0)))[LOB]", "SELECT SYSTEM$TYPEOF(ARRAYS_ZIP([1]::ARRAY(INT)))");
        assertAnswer("[{\"$1\":1,\"$2\":2}]", "SELECT ARRAYS_ZIP([1]::ARRAY(INT), [2]::ARRAY(INT))");
        assertAnswer("ARRAY[LOB]", "SELECT SYSTEM$TYPEOF(ARRAY_FLATTEN([[1],[2]]))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ARRAY_FLATTEN': (ARRAY(ARRAY(NUMBER(38,0))))",
            "SELECT ARRAY_FLATTEN([[1],[2]]::ARRAY(ARRAY(INT)))");
        assertAnswer("[1,2]", "SELECT ARRAY_FLATTEN([[1],[2]]::ARRAY(ARRAY(INT))::ARRAY)");
        assertAnswer("4", "SELECT ARRAY_SORT([3,1,2]::ARRAY(INT), FALSE, FALSE)[0] + 1");
        assertAnswer("3", "SELECT ARRAY_REMOVE_AT([1,2]::ARRAY(INT), 0)[0] + 1");
        assertAnswer("3", "SELECT ARRAY_DISTINCT([1,1,2]::ARRAY(INT))[1] + 1");
        assertAnswer("NUMBER(38,0)[SB16]", "SELECT SYSTEM$TYPEOF(ARRAY_SORT([3,1,2]::ARRAY(INT))[0])");
        assertAnswer("ARRAY(VARCHAR(5))[LOB]", "SELECT SYSTEM$TYPEOF(ARRAY_REVERSE(['a']::ARRAY(VARCHAR(5))))");
    }
}
