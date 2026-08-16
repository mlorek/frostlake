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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A multi-row VALUES folds each column's rows to one type in written order, as a UNION ALL folds its arms.
 * Numbers meet at their supertype, a leading BOOLEAN absorbs a number or a string, a string joins a number
 * or a temporal (a string literal as the number it spells, any other string as NUMBER(18,5)), and a
 * predicate folds with no other family. A row whose type does not fold is refused naming that row's type,
 * {@code Invalid data type [BOOLEAN] in VALUES clause}; the column's type match judges the folded type; and
 * every row converts to it before it is written, which is why {@code (1), (2.5)} writes 1.0 and
 * {@code (TRUE), (2)} fails its row. Every cell is live-verified.
 */
public class MultiRowValuesFoldTest extends BaseDatabaseTest {

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), refused.getMessage());
    }

    @Test
    public void aColumnFoldsItsRowsInWrittenOrder() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P462_DB");
            engine.execute("CREATE OR REPLACE TABLE tu (v VARCHAR, n NUMBER, f FLOAT, b BOOLEAN, d DATE)");
            assertRefused("INSERT INTO tu (v) VALUES (1), ('a')",
                "DML operation to table TU failed on column V with error: Numeric value 'a' is not recognized");
            assertRefused("INSERT INTO tu (v) VALUES ('2024-01-01'::DATE), ('x')",
                "DML operation to table TU failed on column V with error: Date 'x' is not recognized");
            assertRefused("INSERT INTO tu (v) VALUES (TRUE), ('x')",
                "DML operation to table TU failed on column V with error: Boolean value 'x' is not recognized");
            assertRefused("INSERT INTO tu (v) VALUES ('x'), (1)",
                "DML operation to table TU failed on column V with error: Numeric value 'x' is not recognized");
            engine.execute("INSERT INTO tu (v) VALUES ('x'), (TRUE)");
            assertRefused("INSERT INTO tu (v) VALUES (1), (TRUE)",
                "SQL compilation error:\nInvalid data type [BOOLEAN] in VALUES clause");
            engine.execute("INSERT INTO tu (v) VALUES (TRUE), (1)");
            assertRefused("INSERT INTO tu (v) VALUES (1), (CURRENT_DATE)",
                "SQL compilation error:\nInvalid data type [DATE] in VALUES clause");
            assertRefused("INSERT INTO tu (v) VALUES (CURRENT_DATE), (1)",
                "SQL compilation error:\nInvalid data type [NUMBER(1,0)] in VALUES clause");
            assertRefused("INSERT INTO tu (v) VALUES (1.5::FLOAT), ('a')",
                "DML operation to table TU failed on column V with error: Numeric value 'a' is not recognized");
            engine.execute("INSERT INTO tu (v) VALUES (NULL), ('a')");
            engine.execute("INSERT INTO tu (v) VALUES (1), (2.5)");
            assertRefused("INSERT INTO tu (v) VALUES (1), ('a'), ('b')",
                "DML operation to table TU failed on column V with error: Numeric value 'a' is not recognized");
            assertRefused("INSERT INTO tu (n) VALUES (1), (TRUE)",
                "SQL compilation error:\nInvalid data type [BOOLEAN] in VALUES clause");
            assertRefused("INSERT INTO tu (n) VALUES (TRUE), (1)",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got BOOLEAN for column N");
            assertRefused("INSERT INTO tu (n) VALUES (1), (CURRENT_DATE)",
                "SQL compilation error:\nInvalid data type [DATE] in VALUES clause");
            assertRefused("INSERT INTO tu (n) VALUES (CURRENT_DATE), (1)",
                "SQL compilation error:\nInvalid data type [NUMBER(1,0)] in VALUES clause");
            engine.execute("INSERT INTO tu (n) VALUES ('7'), (1)");
            engine.execute("INSERT INTO tu (n) VALUES (1), ('7')");
            assertRefused("INSERT INTO tu (n) VALUES ('x'), (1)",
                "DML operation to table TU failed on column N with error: Numeric value 'x' is not recognized");
            assertRefused("INSERT INTO tu (n) VALUES (1), ('x')",
                "DML operation to table TU failed on column N with error: Numeric value 'x' is not recognized");
            engine.execute("INSERT INTO tu (n) VALUES (1.5::FLOAT), (1)");
            engine.execute("INSERT INTO tu (n) VALUES (1), (1.5::FLOAT)");
            assertRefused("INSERT INTO tu (n) VALUES (NULL), (TRUE)",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got BOOLEAN for column N");
            assertRefused("INSERT INTO tu (n) VALUES (TRUE), (NULL)",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got BOOLEAN for column N");
            assertRefused("INSERT INTO tu (n) VALUES (1 = 1), (2)",
                "SQL compilation error:\nInvalid data type [NUMBER(1,0)] in VALUES clause");
            assertRefused("INSERT INTO tu (n) VALUES (2), (1 = 1)",
                "SQL compilation error:\nInvalid data type [BOOLEAN] in VALUES clause");
            assertRefused("INSERT INTO tu (n) VALUES (1), (2), (TRUE)",
                "SQL compilation error:\nInvalid data type [BOOLEAN] in VALUES clause");
            assertRefused("INSERT INTO tu (b) VALUES (1), (TRUE)",
                "SQL compilation error:\nInvalid data type [BOOLEAN] in VALUES clause");
            engine.execute("INSERT INTO tu (b) VALUES (TRUE), (1)");
            engine.execute("INSERT INTO tu (b) VALUES ('true'), (TRUE)");
            engine.execute("INSERT INTO tu (b) VALUES (TRUE), ('true')");
            assertRefused("INSERT INTO tu (b) VALUES (1), ('x')",
                "DML operation to table TU failed on column B with error: Numeric value 'x' is not recognized");
            assertRefused("INSERT INTO tu (b) VALUES (TRUE), ('x')",
                "DML operation to table TU failed on column B with error: Boolean value 'x' is not recognized");
            assertRefused("INSERT INTO tu (b) VALUES (TRUE), (1.5::FLOAT)",
                "DML operation to table TU failed on column B with error: Boolean value '1.5' is not recognized");
            assertRefused("INSERT INTO tu (b) VALUES (1.5::FLOAT), (TRUE)",
                "SQL compilation error:\nInvalid data type [BOOLEAN] in VALUES clause");
            engine.execute("INSERT INTO tu (d) VALUES ('2024-01-01'), ('2024-01-02'::DATE)");
            engine.execute("INSERT INTO tu (d) VALUES ('2024-01-02'::DATE), ('2024-01-01')");
            assertRefused("INSERT INTO tu (d) VALUES (CURRENT_DATE), (1)",
                "SQL compilation error:\nInvalid data type [NUMBER(1,0)] in VALUES clause");
            assertRefused("INSERT INTO tu (d) VALUES (1), (CURRENT_DATE)",
                "SQL compilation error:\nInvalid data type [DATE] in VALUES clause");
            engine.execute("INSERT INTO tu (d) VALUES (CURRENT_DATE), (CURRENT_TIMESTAMP)");
            engine.execute("INSERT INTO tu (d) VALUES (CURRENT_TIMESTAMP), (CURRENT_DATE)");
            assertRefused("INSERT INTO tu (d) VALUES (CURRENT_DATE), ('x')",
                "DML operation to table TU failed on column D with error: Date 'x' is not recognized");
            engine.execute("INSERT INTO tu (f) VALUES (1), (1.5::FLOAT)");
            engine.execute("INSERT INTO tu (f) VALUES (1.5::FLOAT), (1)");
            engine.execute("INSERT INTO tu (f) VALUES (1), ('2')");
            engine.execute("INSERT INTO tu (f) VALUES ('2'), (1)");
            assertRefused("INSERT INTO tu (f) VALUES (TRUE), (1.5::FLOAT)",
                "SQL compilation error:\nExpression type does not match column data type, expecting FLOAT but got BOOLEAN for column F");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P462_DB");
        }
    }

    @Test
    public void everyRowConvertsToTheFoldedType() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P462B_DB");
            engine.execute("CREATE OR REPLACE TABLE tu (v VARCHAR, n NUMBER, f FLOAT, b BOOLEAN, d DATE, t TIMESTAMP_NTZ, x VARIANT)");
            engine.execute("CREATE OR REPLACE TABLE tv (v VARCHAR)");
            engine.execute("INSERT INTO tu (v) VALUES (NOT FALSE), (1)");
            engine.execute("INSERT INTO tu (v) VALUES (TRUE::BOOLEAN), (1)");
            engine.execute("INSERT INTO tu (v) VALUES (TO_BOOLEAN('true')), (1)");
            assertRefused("INSERT INTO tu (v) VALUES (1 = 1), (1)",
                "SQL compilation error:\nInvalid data type [NUMBER(1,0)] in VALUES clause");
            assertRefused("INSERT INTO tu (b) VALUES (TRUE), (2)",
                "DML operation to table TU failed on column B with error: Boolean value '2' is not recognized");
            assertRefused("INSERT INTO tu (v) VALUES ('a'), (1), (TRUE)",
                "SQL compilation error:\nInvalid data type [BOOLEAN] in VALUES clause");
            assertRefused("INSERT INTO tu (n, b) VALUES (1, TRUE), (TRUE, 1)",
                "SQL compilation error:\nInvalid data type [BOOLEAN] in VALUES clause");
            assertRefused("INSERT INTO tu (v) VALUES (NULL), (1), (TRUE)",
                "SQL compilation error:\nInvalid data type [BOOLEAN] in VALUES clause");
            assertRefused("INSERT INTO tu (x) VALUES (1), ('a')",
                "DML operation to table TU failed on column X with error: Numeric value 'a' is not recognized");
            engine.execute("INSERT INTO tu (t) VALUES (CURRENT_DATE), (CURRENT_TIMESTAMP)");
            assertRefused("INSERT INTO tu (d) VALUES ('2024-01-01'), (1)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(18,5) for column D");
            engine.execute("INSERT INTO tu (v) VALUES (FALSE), (0)");
            assertRefused("INSERT INTO tu (b) VALUES (TRUE), (0.5)",
                "DML operation to table TU failed on column B with error: Boolean value '0.5' is not recognized");
            engine.execute("INSERT INTO tv VALUES (1), (2.5)");
            assertEquals("1.0|2.5",
                rows("SELECT LISTAGG(v, '|') WITHIN GROUP (ORDER BY v) FROM tv"));
            engine.execute("TRUNCATE TABLE tv");
            engine.execute("INSERT INTO tv VALUES ('x'), (TRUE)");
            assertEquals("TRUE|x",
                rows("SELECT LISTAGG(v, '|') WITHIN GROUP (ORDER BY v) FROM tv"));
            engine.execute("TRUNCATE TABLE tv");
            engine.execute("INSERT INTO tv VALUES (1.5::FLOAT), (2)");
            assertEquals("1.5|2",
                rows("SELECT LISTAGG(v, '|') WITHIN GROUP (ORDER BY v) FROM tv"));
            engine.execute("TRUNCATE TABLE tv");
            engine.execute("INSERT INTO tv VALUES ('2024-02-02'::DATE), ('2024-01-01 10:00:00')");
            assertEquals("2024-01-01|2024-02-02",
                rows("SELECT LISTAGG(v, '|') WITHIN GROUP (ORDER BY v) FROM tv"));
            engine.execute("TRUNCATE TABLE tv");
            engine.execute("INSERT INTO tv VALUES (1), (2), (3.25)");
            assertEquals("1.00|2.00|3.25",
                rows("SELECT LISTAGG(v, '|') WITHIN GROUP (ORDER BY v) FROM tv"));
            engine.execute("TRUNCATE TABLE tv");
            engine.execute("INSERT INTO tv VALUES (1e2), (5)");
            assertEquals("100|5",
                rows("SELECT LISTAGG(v, '|') WITHIN GROUP (ORDER BY v) FROM tv"));
            engine.execute("TRUNCATE TABLE tv");
            engine.execute("INSERT INTO tv VALUES (TRUE), (1)");
            assertEquals("true|true",
                rows("SELECT LISTAGG(v, '|') WITHIN GROUP (ORDER BY v) FROM tv"));
            engine.execute("TRUNCATE TABLE tv");
            engine.execute("INSERT INTO tv VALUES ('7'), (1)");
            assertEquals("1|7",
                rows("SELECT LISTAGG(v, '|') WITHIN GROUP (ORDER BY v) FROM tv"));
            engine.execute("TRUNCATE TABLE tv");
            engine.execute("INSERT INTO tv VALUES (1), ('07')");
            assertEquals("1|7",
                rows("SELECT LISTAGG(v, '|') WITHIN GROUP (ORDER BY v) FROM tv"));
            engine.execute("TRUNCATE TABLE tv");
            engine.execute("INSERT INTO tv VALUES ('2024-02-02'::DATE), ('2024-02-03'::TIMESTAMP_NTZ)");
            assertEquals("2024-02-02 00:00:00.000|2024-02-03 00:00:00.000",
                rows("SELECT LISTAGG(v, '|') WITHIN GROUP (ORDER BY v) FROM tv"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P462B_DB");
        }
    }

    @Test
    public void theColumnJudgesTheFoldedType() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P462C_DB");
            engine.execute("CREATE OR REPLACE TABLE tu (n NUMBER, d DATE, x VARIANT, bn BINARY)");
            assertRefused("INSERT INTO tu (d) VALUES ('7'), (1)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(1,0) for column D");
            assertRefused("INSERT INTO tu (d) VALUES ('07'), (1)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(1,0) for column D");
            assertRefused("INSERT INTO tu (d) VALUES ('abcdefghij'), (1)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(18,5) for column D");
            assertRefused("INSERT INTO tu (d) VALUES ('abcdefghijklmnopqrst'), (1)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(18,5) for column D");
            assertRefused("INSERT INTO tu (d) VALUES (1), ('abcdefghij')",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(18,5) for column D");
            assertRefused("INSERT INTO tu (d) VALUES ('ab'), (1.5)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(18,5) for column D");
            assertRefused("INSERT INTO tu (d) VALUES ('ab'), (123456.789)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(18,5) for column D");
            assertRefused("INSERT INTO tu (d) VALUES ('ab'::VARCHAR(100)), (1)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(18,5) for column D");
            assertRefused("INSERT INTO tu (d) VALUES ('ab'), (1.5::FLOAT)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got FLOAT for column D");
            assertRefused("INSERT INTO tu (d) VALUES (1), (2.5)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(2,1) for column D");
            assertRefused("INSERT INTO tu (d) VALUES (1), (2), (3.25)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(3,2) for column D");
            assertRefused("INSERT INTO tu (d) VALUES (100), (2.5)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(4,1) for column D");
            assertRefused("INSERT INTO tu (d) VALUES (TRUE), (1)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got BOOLEAN for column D");
            assertRefused("INSERT INTO tu (n) VALUES ('ab'), (CURRENT_DATE)",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got DATE for column N");
            assertRefused("INSERT INTO tu (n) VALUES (CURRENT_DATE), ('ab')",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got DATE for column N");
            assertRefused("INSERT INTO tu (d) VALUES (1::NUMBER(10,2)), (1::NUMBER(5,4))",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(12,4) for column D");
            assertRefused("INSERT INTO tu (d) VALUES (1::NUMBER(38,0)), (1.5)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(38,1) for column D");
            assertRefused("INSERT INTO tu (d) VALUES (1.5::FLOAT), (1)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got FLOAT for column D");
            assertRefused("INSERT INTO tu (d) VALUES (NULL), (1)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(1,0) for column D");
            assertRefused("INSERT INTO tu (d) VALUES (1), (NULL)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(1,0) for column D");
            assertRefused("INSERT INTO tu (n) VALUES ('2024-01-01'::DATE), ('2024-01-01 10:00:00'::TIMESTAMP_NTZ)",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got TIMESTAMP_NTZ(9) for column N");
            assertRefused("INSERT INTO tu (n) VALUES ('10:00:00'::TIME), ('2024-01-01'::DATE)",
                "SQL compilation error:\nInvalid data type [DATE] in VALUES clause");
            assertRefused("INSERT INTO tu (n) VALUES ('ab'), (TRUE)",
                "DML operation to table TU failed on column N with error: Numeric value 'ab' is not recognized");
            assertRefused("INSERT INTO tu (n) VALUES (TRUE), ('ab')",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got BOOLEAN for column N");
            assertRefused("INSERT INTO tu (d) VALUES ('ab'), (TRUE)",
                "DML operation to table TU failed on column D with error: Date 'ab' is not recognized");
            assertRefused("INSERT INTO tu (x) VALUES ('ab'), ('cd')",
                "SQL compilation error:\nExpression type does not match column data type, expecting VARIANT but got VARCHAR(2) for column X");
            assertRefused("INSERT INTO tu (x) VALUES ('a'), ('abc')",
                "SQL compilation error:\nExpression type does not match column data type, expecting VARIANT but got VARCHAR(3) for column X");
            assertRefused("INSERT INTO tu (x) VALUES ('abc'), ('a')",
                "SQL compilation error:\nExpression type does not match column data type, expecting VARIANT but got VARCHAR(3) for column X");
            assertRefused("INSERT INTO tu (d) VALUES ('ab'), (12345678901234567)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(22,5) for column D");
            assertRefused("INSERT INTO tu (d) VALUES ('ab'), (1.1234567)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(20,7) for column D");
            assertRefused("INSERT INTO tu (n) VALUES ('2024-01-01'::DATE), ('10:00:00'::TIME)",
                "SQL compilation error:\nInvalid data type [TIME(9)] in VALUES clause");
            assertRefused("INSERT INTO tu (n) VALUES ('2024-01-01 10:00:00'::TIMESTAMP_LTZ), ('2024-01-01'::DATE)",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got TIMESTAMP_LTZ(9) for column N");
            assertRefused("INSERT INTO tu (n) VALUES ('2024-01-01 10:00:00'::TIMESTAMP_NTZ), ('2024-01-01 10:00:00'::TIMESTAMP_TZ)",
                "SQL compilation error:\nInvalid data type [TIMESTAMP_TZ(9)] in VALUES clause");
            assertRefused("INSERT INTO tu (bn) VALUES (TO_BINARY('ab', 'HEX')), (1)",
                "SQL compilation error:\nInvalid data type [NUMBER(1,0)] in VALUES clause");
            assertRefused("INSERT INTO tu (n) VALUES (TO_BINARY('ab', 'HEX')), ('ab')",
                "SQL compilation error:\nInvalid data type [VARCHAR(2)] in VALUES clause");
            assertRefused("INSERT INTO tu (d) VALUES (1), (TRUE)",
                "SQL compilation error:\nInvalid data type [BOOLEAN] in VALUES clause");
            assertRefused("INSERT INTO tu (n) VALUES ('ab'), (CURRENT_TIMESTAMP)",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got TIMESTAMP_LTZ(9) for column N");
            assertRefused("INSERT INTO tu (n) VALUES (1.5::FLOAT), (TRUE)",
                "SQL compilation error:\nInvalid data type [BOOLEAN] in VALUES clause");
            assertRefused("INSERT INTO tu (n) VALUES (TRUE), (1.5::FLOAT)",
                "SQL compilation error:\nExpression type does not match column data type, expecting NUMBER(38,0) but got BOOLEAN for column N");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P462C_DB");
        }
    }

    @Test
    public void aStringLiteralContributesTheNumberItSpells() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P462D_DB");
            engine.execute("CREATE OR REPLACE TABLE tu (n NUMBER, d DATE)");
            engine.execute("CREATE OR REPLACE TABLE tv (v VARCHAR)");
            assertRefused("INSERT INTO tu (d) VALUES ('7.50'), (1)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(2,1) for column D");
            assertRefused("INSERT INTO tu (d) VALUES ('-7'), (1)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(1,0) for column D");
            assertRefused("INSERT INTO tu (d) VALUES ('1e3'), (1)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(4,0) for column D");
            assertRefused("INSERT INTO tu (d) VALUES (' 7 '), (1)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(18,5) for column D");
            assertRefused("INSERT INTO tu (d) VALUES ('7'), (1.5)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(2,1) for column D");
            assertRefused("INSERT INTO tu (d) VALUES ('123456'), (1)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(6,0) for column D");
            assertRefused("INSERT INTO tu (d) VALUES (1), ('7.5')",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(2,1) for column D");
            assertRefused("INSERT INTO tu (d) VALUES ('7'), ('8'), (1)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(1,0) for column D");
            assertRefused("INSERT INTO tu (d) VALUES ('0.001'), (1)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(4,3) for column D");
            assertRefused("INSERT INTO tu (d) VALUES ('7'), (1.5::FLOAT)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got FLOAT for column D");
            assertRefused("INSERT INTO tu (d) VALUES ('12345678901234567890123'), (1)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(23,0) for column D");
            assertRefused("INSERT INTO tu (d) VALUES ('7'::VARCHAR), (1)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(18,5) for column D");
            assertRefused("INSERT INTO tu (d) VALUES ('7'), (NULL), (1)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(1,0) for column D");
            assertRefused("INSERT INTO tu (d) VALUES (NULL), ('ab'), (1)",
                "SQL compilation error:\nExpression type does not match column data type, expecting DATE but got NUMBER(18,5) for column D");
            engine.execute("INSERT INTO tv VALUES ('x'), (NOT FALSE)");
            assertEquals("TRUE|x",
                rows("SELECT LISTAGG(v, '|') WITHIN GROUP (ORDER BY v) FROM tv"));
            engine.execute("TRUNCATE TABLE tv");
            engine.execute("TRUNCATE TABLE tv");
            engine.execute("TRUNCATE TABLE tv");
            assertRefused("INSERT INTO tv VALUES ('x'), (1 = 1)",
                "SQL compilation error:\nInvalid data type [BOOLEAN] in VALUES clause");
            assertEquals("",
                rows("SELECT LISTAGG(v, '|') WITHIN GROUP (ORDER BY v) FROM tv"));
            engine.execute("TRUNCATE TABLE tv");
            engine.execute("INSERT INTO tv VALUES ('7'), (1.5)");
            assertEquals("1.5|7.0",
                rows("SELECT LISTAGG(v, '|') WITHIN GROUP (ORDER BY v) FROM tv"));
            engine.execute("TRUNCATE TABLE tv");
            engine.execute("INSERT INTO tv VALUES ('ab'::VARCHAR(5)), (TRUE)");
            assertEquals("TRUE|ab",
                rows("SELECT LISTAGG(v, '|') WITHIN GROUP (ORDER BY v) FROM tv"));
            engine.execute("TRUNCATE TABLE tv");
            engine.execute("INSERT INTO tv VALUES (TRUE), ('0'), ('no')");
            assertEquals("false|false|true",
                rows("SELECT LISTAGG(v, '|') WITHIN GROUP (ORDER BY v) FROM tv"));
            engine.execute("TRUNCATE TABLE tv");
            engine.execute("INSERT INTO tv VALUES (FALSE), (0), (1.0)");
            assertEquals("false|false|true",
                rows("SELECT LISTAGG(v, '|') WITHIN GROUP (ORDER BY v) FROM tv"));
            engine.execute("TRUNCATE TABLE tv");
            engine.execute("TRUNCATE TABLE tv");
            engine.execute("INSERT INTO tv VALUES ('ab'), (NULL), ('abc')");
            assertEquals("ab|abc",
                rows("SELECT LISTAGG(v, '|') WITHIN GROUP (ORDER BY v) FROM tv"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P462D_DB");
        }
    }

    @Test
    public void aPredicateFoldsWithNoOtherFamily() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P462E_DB");
            engine.execute("CREATE OR REPLACE TABLE tu (n NUMBER, b BOOLEAN, x VARIANT, bn BINARY, v VARCHAR)");
            assertRefused("INSERT INTO tu (b) VALUES (1 = 1), ('true')",
                "SQL compilation error:\nInvalid data type [VARCHAR(4)] in VALUES clause");
            assertRefused("INSERT INTO tu (x) VALUES ('ab'), (TRUE)",
                "SQL compilation error:\nExpression type does not match column data type, expecting VARIANT but got VARCHAR(2) for column X");
            assertRefused("INSERT INTO tu (x) VALUES ('ab'::VARCHAR(5)), (TRUE)",
                "SQL compilation error:\nExpression type does not match column data type, expecting VARIANT but got VARCHAR(5) for column X");
            assertRefused("INSERT INTO tu (x) VALUES (TRUE), ('ab')",
                "DML operation to table TU failed on column X with error: Boolean value 'ab' is not recognized");
            assertRefused("INSERT INTO tu (bn) VALUES ('ab'), (TO_BINARY('ab', 'HEX'))",
                "SQL compilation error:\nInvalid data type [BINARY(67108864)] in VALUES clause");
            assertRefused("INSERT INTO tu (b) VALUES (1 < 2), (1)",
                "SQL compilation error:\nInvalid data type [NUMBER(1,0)] in VALUES clause");
            assertRefused("INSERT INTO tu (b) VALUES ('a' LIKE 'a'), (1)",
                "SQL compilation error:\nInvalid data type [NUMBER(1,0)] in VALUES clause");
            assertRefused("INSERT INTO tu (b) VALUES (1 IN (1, 2)), (1)",
                "SQL compilation error:\nInvalid data type [NUMBER(1,0)] in VALUES clause");
            assertRefused("INSERT INTO tu (b) VALUES (1 = 1 AND TRUE), (1)",
                "SQL compilation error:\nInvalid data type [NUMBER(1,0)] in VALUES clause");
            engine.execute("INSERT INTO tu (b) VALUES (TRUE AND TRUE), (1)");
            assertRefused("INSERT INTO tu (b) VALUES (1 IS NULL), (1)",
                "SQL compilation error:\nInvalid data type [NUMBER(1,0)] in VALUES clause");
            engine.execute("INSERT INTO tu (b) VALUES (IFF(TRUE, TRUE, FALSE)), (1)");
            assertRefused("INSERT INTO tu (n) VALUES (1), ('ab'), (TRUE)",
                "SQL compilation error:\nInvalid data type [BOOLEAN] in VALUES clause");
            assertRefused("INSERT INTO tu (n) VALUES ('ab'), (TRUE), (1)",
                "DML operation to table TU failed on column N with error: Numeric value 'ab' is not recognized");
            engine.execute("INSERT INTO tu (x) VALUES (1), (2.5)");
            assertRefused("INSERT INTO tu (x) VALUES (PARSE_JSON('1')), (2)",
                "SQL compilation error:\nInvalid expression [PARSE_JSON('1')] in VALUES clause");
            engine.execute("INSERT INTO tu (v) VALUES (1), (NULL), (2.5)");
            assertEquals("1.0|2.5",
                rows("SELECT LISTAGG(v, '|') WITHIN GROUP (ORDER BY v) FROM tu"));
            assertRefused("INSERT INTO tu (b) VALUES (1 BETWEEN 0 AND 2), (1)",
                "SQL compilation error:\nInvalid data type [NUMBER(1,0)] in VALUES clause");
            assertRefused("INSERT INTO tu (b) VALUES (NOT (1 = 1)), (1)",
                "SQL compilation error:\nInvalid data type [NUMBER(1,0)] in VALUES clause");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P462E_DB");
        }
    }

    @Test
    public void aValueWrittenIntoTextReadsAsItsCast() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P462F_DB");
            engine.execute("CREATE OR REPLACE TABLE tv (v VARCHAR)");
            engine.execute("INSERT INTO tv VALUES (2::FLOAT)");
            engine.execute("INSERT INTO tv SELECT 2::FLOAT");
            engine.execute("INSERT INTO tv VALUES (1e20::FLOAT)");
            engine.execute("INSERT INTO tv VALUES ('2024-02-03'::TIMESTAMP_NTZ)");
            engine.execute("INSERT INTO tv SELECT '2024-02-03 10:11:12.5'::TIMESTAMP_NTZ");
            engine.execute("INSERT INTO tv VALUES ('10:11:12'::TIME)");
            engine.execute("INSERT INTO tv VALUES ('2024-02-02'::DATE)");
            engine.execute("INSERT INTO tv VALUES (1.5::FLOAT)");
            assertEquals("1.5|10:11:12|1e+20|2|2|2024-02-02|2024-02-03 00:00:00.000|2024-02-03 10:11:12.500",
                rows("SELECT LISTAGG(v, '|') WITHIN GROUP (ORDER BY v) FROM tv"));
            assertEquals("2, 2024-02-03 00:00:00.000, 2",
                rows("SELECT (2::FLOAT)::VARCHAR, ('2024-02-03'::TIMESTAMP_NTZ)::VARCHAR, SQRT(4)::VARCHAR"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P462F_DB");
        }
    }
}
