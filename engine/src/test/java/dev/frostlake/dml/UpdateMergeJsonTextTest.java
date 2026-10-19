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

package dev.frostlake.dml;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * An UPDATE's SET and a MERGE's branches write a text of constants alone - a literal, a call over literals, a
 * cast, a block's bind, a session variable - to a VARIANT, ARRAY or OBJECT column by reading it as JSON when the
 * row is written: the document's value is stored whatever the column's kind ('1' is the number 1 in an ARRAY
 * column too), an empty text is SQL NULL and 'null' the JSON null, and a text that is no JSON fails the write on
 * its column in the reader's words, a MERGE's and an UPDATE ... FROM's too. A text that reads a column stays
 * refused while compiling, and an INSERT refuses every text (all live-verified).
 */
public class UpdateMergeJsonTextTest extends MeasuredCellsTestSupport {

    @Test
    public void aConstantTextIsReadAsJsonWhenTheRowIsWritten() {
        engine.execute("CREATE OR REPLACE TABLE tv (id INT, v VARIANT, a ARRAY, o OBJECT, c VARCHAR(5))");
        engine.execute("INSERT INTO tv (id, c) VALUES (1, 'x')");
        engine.execute("CREATE OR REPLACE TABLE src (id INT, s VARCHAR(10))");
        engine.execute("INSERT INTO src VALUES (1, 'q'), (2, 'r')");
        assertAnswer("1|0", "UPDATE tv SET v = '1'");
        assertAnswer("1|INTEGER|null|null|null|null|false",
            "SELECT v, TYPEOF(v), a, TYPEOF(a), o, TYPEOF(o), v IS NULL FROM tv");
        assertAnswer("1|0", "UPDATE tv SET a = '1'");
        assertAnswer("1|INTEGER|1|INTEGER|null|null|false",
            "SELECT v, TYPEOF(v), a, TYPEOF(a), o, TYPEOF(o), v IS NULL FROM tv");
        assertAnswer("1|0", "UPDATE tv SET o = '1'");
        assertAnswer("1|INTEGER|1|INTEGER|1|INTEGER|false",
            "SELECT v, TYPEOF(v), a, TYPEOF(a), o, TYPEOF(o), v IS NULL FROM tv");
        assertAnswer("1|0", "UPDATE tv SET v = '{\"k\":1}', a = '[1,2]', o = '{\"k\":1}'");
        assertAnswer("{\"k\":1}|OBJECT|[1,2]|ARRAY|{\"k\":1}|OBJECT|false",
            "SELECT v, TYPEOF(v), a, TYPEOF(a), o, TYPEOF(o), v IS NULL FROM tv");
        assertAnswer("1|0", "UPDATE tv SET a = '{\"k\":1}', o = '[1]'");
        assertAnswer("{\"k\":1}|OBJECT|{\"k\":1}|OBJECT|[1]|ARRAY|false",
            "SELECT v, TYPEOF(v), a, TYPEOF(a), o, TYPEOF(o), v IS NULL FROM tv");
        assertAnswer("1|0", "UPDATE tv SET v = ''");
        assertAnswer("null|null|{\"k\":1}|OBJECT|[1]|ARRAY|true",
            "SELECT v, TYPEOF(v), a, TYPEOF(a), o, TYPEOF(o), v IS NULL FROM tv");
        assertAnswer("1|0", "UPDATE tv SET v = 'null'");
        assertAnswer("null|NULL_VALUE|{\"k\":1}|OBJECT|[1]|ARRAY|false",
            "SELECT v, TYPEOF(v), a, TYPEOF(a), o, TYPEOF(o), v IS NULL FROM tv");
        assertAnswer("1|0", "UPDATE tv SET v = ' 1 '");
        assertAnswer("1|INTEGER|{\"k\":1}|OBJECT|[1]|ARRAY|false",
            "SELECT v, TYPEOF(v), a, TYPEOF(a), o, TYPEOF(o), v IS NULL FROM tv");
        assertAnswer("1|0", "UPDATE tv SET v = '\"x\"'");
        assertAnswer("x|VARCHAR|{\"k\":1}|OBJECT|[1]|ARRAY|false",
            "SELECT v, TYPEOF(v), a, TYPEOF(a), o, TYPEOF(o), v IS NULL FROM tv");
        assertAnswer("1|0", "UPDATE tv SET v = '1.50'");
        assertAnswer("1.5|DECIMAL|{\"k\":1}|OBJECT|[1]|ARRAY|false",
            "SELECT v, TYPEOF(v), a, TYPEOF(a), o, TYPEOF(o), v IS NULL FROM tv");
        assertAnswer("1|0", "UPDATE tv SET v = 'true'");
        assertAnswer("true|BOOLEAN|{\"k\":1}|OBJECT|[1]|ARRAY|false",
            "SELECT v, TYPEOF(v), a, TYPEOF(a), o, TYPEOF(o), v IS NULL FROM tv");
        assertAnswer("REFUSED DML operation to table TV failed on column V with error: Error parsing JSON: unknown "
                + "keyword \"abc\", pos 4",
            "UPDATE tv SET v = 'abc'");
        assertAnswer("REFUSED DML operation to table TV failed on column V with error: Error parsing JSON: incomplete "
                + "array value, pos 3",
            "UPDATE tv SET v = '[1'");
        assertAnswer("REFUSED DML operation to table TV failed on column V with error: Error parsing JSON: misplaced "
                + "}, pos 6",
            "UPDATE tv SET v = '{\"a\":}'");
        assertAnswer("0|0", "UPDATE tv SET v = 'abc' WHERE FALSE");
        assertAnswer("1|0", "UPDATE tv SET v = NULL::VARCHAR(10), a = NULL::VARCHAR(10), o = NULL::VARCHAR(10)");
        assertAnswer("null|null|null|null|null|null|true",
            "SELECT v, TYPEOF(v), a, TYPEOF(a), o, TYPEOF(o), v IS NULL FROM tv");
        assertAnswer("1|0", "UPDATE tv SET v = TO_VARCHAR(1)");
        assertAnswer("1|INTEGER|null|null|null|null|false",
            "SELECT v, TYPEOF(v), a, TYPEOF(a), o, TYPEOF(o), v IS NULL FROM tv");
        assertAnswer("1|0", "UPDATE tv SET v = '1' || '2'");
        assertAnswer("12|INTEGER|null|null|null|null|false",
            "SELECT v, TYPEOF(v), a, TYPEOF(a), o, TYPEOF(o), v IS NULL FROM tv");
        assertAnswer("1|0", "UPDATE tv SET v = UPPER('true')");
        assertAnswer("true|BOOLEAN|null|null|null|null|false",
            "SELECT v, TYPEOF(v), a, TYPEOF(a), o, TYPEOF(o), v IS NULL FROM tv");
        assertAnswer("REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "VARIANT but got VARCHAR(5) for column V",
            "UPDATE tv SET v = c");
        assertAnswer("REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "VARIANT but got VARCHAR(6) for column V",
            "UPDATE tv SET v = c || ''");
        assertAnswer("REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "ARRAY but got VARCHAR(5) for column A",
            "UPDATE tv SET a = c");
        assertAnswer("REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "ARRAY but got VARCHAR(5) for column A",
            "UPDATE tv SET v = 'abc', a = c");
        assertAnswer("1|0", "UPDATE tv SET v = '2' WHERE id = 1");
        assertAnswer("2|INTEGER|null|null|null|null|false",
            "SELECT v, TYPEOF(v), a, TYPEOF(a), o, TYPEOF(o), v IS NULL FROM tv");
        assertAnswer("REFUSED DML operation to table TV failed on column V with error: Error parsing JSON: unknown "
                + "keyword \"abc\", pos 4",
            "MERGE INTO tv USING src ON tv.id = src.id WHEN NOT MATCHED THEN INSERT (id, v) VALUES (src.id, 'abc')");
        assertAnswer("1",
            "MERGE INTO tv USING src ON tv.id = src.id WHEN NOT MATCHED THEN INSERT (id, v, a) VALUES (src.id, '7', "
                + "'8')");
        assertAnswer("1|2|INTEGER|null|null ;; 2|7|INTEGER|8|INTEGER",
            "SELECT id, v, TYPEOF(v), a, TYPEOF(a) FROM tv ORDER BY id");
        assertAnswer("REFUSED DML operation to table TV failed on column V with error: Error parsing JSON: unknown "
                + "keyword \"abc\", pos 4",
            "MERGE INTO tv USING src ON tv.id = src.id WHEN MATCHED THEN UPDATE SET v = 'abc'");
        assertAnswer("2", "MERGE INTO tv USING src ON tv.id = src.id WHEN MATCHED THEN UPDATE SET v = '9'");
        assertAnswer("1|9|INTEGER ;; 2|9|INTEGER", "SELECT id, v, TYPEOF(v) FROM tv ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "VARIANT but got VARCHAR(10) for column V",
            "MERGE INTO tv USING src ON tv.id = src.id WHEN MATCHED THEN UPDATE SET v = src.s");
        assertAnswer("ok", "DECLARE s VARCHAR(10); BEGIN UPDATE tv SET v = :s, a = :s, o = :s; RETURN 'ok'; END;");
        assertAnswer("1|true|true|true ;; 2|true|true|true",
            "SELECT id, v IS NULL, a IS NULL, o IS NULL FROM tv ORDER BY id");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 43 : DML operation "
                + "to table TV failed on column V with error: Error parsing JSON: unknown keyword \"abc\", pos 4",
            "DECLARE s VARCHAR(10) DEFAULT 'abc'; BEGIN UPDATE tv SET v = :s; RETURN 'ok'; END;");
        assertAnswer("ok", "DECLARE s VARCHAR(10) DEFAULT '5'; BEGIN UPDATE tv SET v = :s, a = :s; RETURN 'ok'; END;");
        assertAnswer("1|5|INTEGER|5|INTEGER ;; 2|5|INTEGER|5|INTEGER",
            "SELECT id, v, TYPEOF(v), a, TYPEOF(a) FROM tv ORDER BY id");
        assertAnswer("2|0", "UPDATE tv SET v = '12345678901234567890123'");
        assertAnswer("12345678901234567890123|INTEGER", "SELECT v, TYPEOF(v) FROM tv WHERE id = 1");
        assertAnswer("2|0", "UPDATE tv SET v = '1e2'");
        assertAnswer("100.0|DOUBLE", "SELECT v, TYPEOF(v) FROM tv WHERE id = 1");
        assertAnswer("2|0", "UPDATE tv SET o = 'null'");
        assertAnswer("null|NULL_VALUE|false", "SELECT o, TYPEOF(o), o IS NULL FROM tv WHERE id = 1");
        assertAnswer("2|0", "UPDATE tv SET a = ''");
        assertAnswer("true", "SELECT a IS NULL FROM tv WHERE id = 1");
        assertAnswer("Statement executed successfully.", "SET sv_json = '3'");
        assertAnswer("1|0", "UPDATE tv SET v = $sv_json WHERE id = 1");
        assertAnswer("3|INTEGER", "SELECT v, TYPEOF(v) FROM tv WHERE id = 1");
        assertAnswer("1|0", "UPDATE tv SET v = IFF(TRUE, '4', '5') WHERE id = 1");
        assertAnswer("4|INTEGER", "SELECT v, TYPEOF(v) FROM tv WHERE id = 1");
        assertAnswer("1|0", "UPDATE tv SET v = TO_VARIANT('abc') WHERE id = 1");
        assertAnswer("abc|VARCHAR", "SELECT v, TYPEOF(v) FROM tv WHERE id = 1");
        assertAnswer("1|0", "UPDATE tv SET v = 'abc'::VARIANT WHERE id = 1");
        assertAnswer("abc|VARCHAR", "SELECT v, TYPEOF(v) FROM tv WHERE id = 1");
        assertAnswer("1|0", "UPDATE tv SET v = PARSE_JSON('{\"k\":\"s\"}'):k WHERE id = 1");
        assertAnswer("s|VARCHAR", "SELECT v, TYPEOF(v) FROM tv WHERE id = 1");
        assertAnswer("2|0", "UPDATE tv SET v = '[1,2]' FROM src WHERE tv.id = src.id");
        assertAnswer("1|[1,2]|ARRAY ;; 2|[1,2]|ARRAY", "SELECT id, v, TYPEOF(v) FROM tv ORDER BY id");
        assertAnswer("REFUSED DML operation to table TV failed on column V with error: Error parsing JSON: unknown "
                + "keyword \"abc\", pos 4",
            "UPDATE tv SET v = 'abc' FROM src WHERE tv.id = src.id");
        assertAnswer("REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "VARIANT but got VARCHAR(1) for column V",
            "INSERT INTO tv (id, v) VALUES (9, '1')");
        assertAnswer("REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "VARIANT but got VARCHAR(1) for column V",
            "INSERT INTO tv (id, v) SELECT 9, '1'");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 43 : DML operation "
                + "to table TV failed on column V with error: Error parsing JSON: unknown keyword \"abc\", pos 4",
            "DECLARE s VARCHAR(10) DEFAULT 'abc'; BEGIN MERGE INTO tv USING src ON tv.id = src.id WHEN MATCHED THEN "
                + "UPDATE SET v = :s; RETURN 'ok'; END;");
    }
}
