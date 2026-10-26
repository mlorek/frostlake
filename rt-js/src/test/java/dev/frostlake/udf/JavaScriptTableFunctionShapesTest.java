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

package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A JavaScript table function's argumentInfo holds no constValue for a typed NULL constant and a null one for a
 * JSON null; a table function joined ON TRUE, or ON a column of its own, reads each left row, and one whose
 * arguments read the left side is refused with an ON; OVER partitions by a constant key; a long report is cut as
 * every failed statement's message is, past 16384 bytes (all live-verified).
 */
public class JavaScriptTableFunctionShapesTest extends MeasuredCellsTestSupport {

    @Test
    public void argumentsJoinsAndPartitions() {
        assertAnswer("Function AI655 successfully created.",
            "CREATE OR REPLACE FUNCTION ai655(g VARCHAR) RETURNS TABLE (info VARCHAR) LANGUAGE JAVASCRIPT AS $${ "
                + "initialize: function (argumentInfo, context) { this.info = "
                + "JSON.stringify(argumentInfo).replace(/[-:]/g, '_'); }, processRow: function (row, rowWriter, "
                + "context) { rowWriter.writeRow({INFO: this.info}); } }$$");
        assertAnswer("Function AV655 successfully created.",
            "CREATE OR REPLACE FUNCTION av655(a VARIANT) RETURNS TABLE (info VARCHAR) LANGUAGE JAVASCRIPT AS $${ "
                + "initialize: function (argumentInfo, context) { this.info = "
                + "JSON.stringify(argumentInfo).replace(/[-:]/g, '_'); }, processRow: function (row, rowWriter, "
                + "context) { rowWriter.writeRow({INFO: this.info}); } }$$");
        assertAnswer("{\"G\"_{\"constValue\"_\"x\",\"isNullable\"_true,\"isConst\"_true,\"type\"_\"text\"}}",
            "SELECT * FROM TABLE(ai655('x'))");
        assertAnswer("{\"G\"_{\"isNullable\"_true,\"isConst\"_true,\"type\"_\"text\"}}",
            "SELECT * FROM TABLE(ai655(NULL::VARCHAR))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for "
                + "function 'AI655': (NULL)",
            "SELECT * FROM TABLE(ai655(NULL))");
        assertAnswer("{\"A\"_{\"isNullable\"_true,\"isConst\"_true,\"type\"_\"variant\"}}",
            "SELECT * FROM TABLE(av655(NULL::VARIANT))");
        assertAnswer("{\"A\"_{\"constValue\"_null,\"isNullable\"_true,\"isConst\"_true,\"type\"_\"variant\"}}",
            "SELECT * FROM TABLE(av655(PARSE_JSON('null')))");
        assertAnswer("1|{\"G\"_{\"constValue\"_\"x\",\"isNullable\"_true,\"isConst\"_true,\"type\"_\"text\"}}",
            "SELECT t.a, f.info FROM (SELECT 1 AS a) t JOIN TABLE(ai655('x')) f ON TRUE");
        assertAnswer("1|{\"G\"_{\"constValue\"_\"x\",\"isNullable\"_true,\"isConst\"_true,\"type\"_\"text\"}}",
            "SELECT t.a, f.info FROM (SELECT 1 AS a) t JOIN TABLE(ai655('x')) f ON f.info IS NOT NULL");
        assertAnswer("1|{\"G\"_{\"constValue\"_\"x\",\"isNullable\"_true,\"isConst\"_true,\"type\"_\"text\"}}",
            "SELECT t.a, f.info FROM (SELECT 1 AS a) t LEFT JOIN TABLE(ai655('x')) f ON TRUE");
        assertAnswer("REFUSED Unsupported feature 'lateral table function called with OUTER JOIN syntax or a join "
                + "predicate (ON clause)'.",
            "SELECT t.a, f.info FROM (SELECT 1 AS a) t JOIN TABLE(ai655(t.a::VARCHAR)) f ON TRUE");
        assertAnswer("Function SQ655 successfully created.",
            "CREATE OR REPLACE FUNCTION sq655(a INT) RETURNS TABLE (b INT) AS 'SELECT a'");
        assertAnswer("1|1", "SELECT * FROM (SELECT 1 AS a) t, TABLE(sq655(t.a) OVER (PARTITION BY 1))");
        assertAnswer("1|null|[0]|0|1|[1,2] ;; 1|null|[1]|1|2|[1,2]",
            "SELECT * FROM TABLE(FLATTEN(INPUT => [1, 2]) OVER (PARTITION BY 1))");
        assertAnswer("1|{\"G\"_{\"isNullable\"_true,\"isConst\"_false,\"type\"_\"text\"}}",
            "SELECT t.a, f.info FROM (SELECT 1 AS a) t, TABLE(ai655(t.a::VARCHAR) OVER (PARTITION BY 1)) f");
        assertAnswer("1|{\"G\"_{\"isNullable\"_true,\"isConst\"_false,\"type\"_\"text\"}}",
            "SELECT t.a, f.info FROM (SELECT 1 AS a) t, TABLE(ai655(t.a::VARCHAR) OVER (PARTITION BY 'k')) f");
        assertAnswer("Function JS655 successfully created.",
            "CREATE OR REPLACE FUNCTION js655(n FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ throw new "
                + "Error('a'.repeat(N)); $$");
        assertAnswer("REFUSED JavaScript execution error: Uncaught Error: "
                + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa "
                + "in JS655 at ' throw new Error('a'.repeat(N)); ' position 1 | stackstrace:  | JS655 line: 1",
            "SELECT js655(100)");
    }

    private static final String CUT = "... (Original error message truncated since it exceeded ERROR_MSG_MAX_SIZE).";

    /** The report of an Error a one-line body throws, as the account words it. */
    private static String report(final String function, final int letters) {
        return "JavaScript execution error: Uncaught Error: " + "a".repeat(letters) + " in " + function
            + " at ' throw new Error('a'.repeat(N)); ' position 1\nstackstrace: \n" + function + " line: 1";
    }

    @Test
    public void aLongReportIsCutAsEveryMessageIs() {
        engine.execute("CREATE OR REPLACE FUNCTION js655(n FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS "
            + "$$ throw new Error('a'.repeat(N)); $$");
        assertAnswer("REFUSED " + report("JS655", 5000).replace("\n", " | "), "SELECT js655(5000)");
        assertAnswer("REFUSED " + report("JS655", 20000).substring(0, 16384) + CUT, "SELECT js655(20000)");
    }
}
