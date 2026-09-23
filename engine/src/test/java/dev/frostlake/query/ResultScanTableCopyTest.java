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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A table built over a result's RESULT_SCAN takes each column's declared type and keeps its name as the
 * result spelled it: a text column stays text whatever it holds, a block's result column keeps the type
 * its RETURN declared, a procedure's the RETURNS type, and a SHOW or DESCRIBE result its own column names
 * in lower case — {@code default} and {@code null?} included. A {@code *} over a column whose name reads
 * as a keyword, a literal, a call or no plain name at all ({@code default}, {@code true},
 * {@code CURRENT_DATE}, {@code a b}) projects that column. Every cell is live-verified.
 */
public class ResultScanTableCopyTest extends BaseDatabaseTest {

    private static final String COPY = "CREATE OR REPLACE TABLE %s AS SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))";

    /** Each column's name, type, text length, precision and scale, colon-separated, in order. */
    private String columns(final String table) {
        return firstCell("SELECT LISTAGG(column_name || ':' || data_type || ':' || COALESCE(character_maximum_length::VARCHAR, '-')"
            + " || ':' || COALESCE(numeric_precision::VARCHAR, '-') || ':' || COALESCE(numeric_scale::VARCHAR, '-'), ', ')"
            + " WITHIN GROUP (ORDER BY ordinal_position) FROM information_schema.columns"
            + " WHERE table_schema = 'TEST_SCHEMA' AND table_name = '" + table + "'");
    }

    private String firstCell(final String sql) {
        for (final Row row : engine.executeQuery(sql).getRows()) {
            return String.valueOf(row.getValue(0));
        }
        return "no row";
    }

    /** Every cell of the first row, comma-separated. */
    private String firstRow(final String sql) {
        for (final Row row : engine.executeQuery(sql).getRows()) {
            final StringBuilder out = new StringBuilder();
            for (int i = 0; i < row.getValues().size(); i++) {
                out.append(i > 0 ? ", " : "").append(row.getValue(i));
            }
            return out.toString();
        }
        return "no row";
    }

    private void copyAfter(final String statement, final String table) {
        engine.executeQuery(statement);
        engine.execute(String.format(COPY, table));
    }

    @Test
    public void aCopyTakesTheResultsDeclaredTypes() {
        copyAfter("SELECT '1' AS a, '2024-01-01' AS d, 'true' AS b", "T_TEXT");
        assertEquals("A:TEXT:1:-:-, D:TEXT:10:-:-, B:TEXT:4:-:-", columns("T_TEXT"));
        copyAfter("EXECUTE IMMEDIATE $$ BEGIN RETURN 5; END; $$", "T_BLOCK_NUMBER");
        assertEquals("anonymous block:NUMBER:-:1:0", columns("T_BLOCK_NUMBER"));
        copyAfter("EXECUTE IMMEDIATE $$ BEGIN RETURN '5'; END; $$", "T_BLOCK_TEXT");
        assertEquals("anonymous block:TEXT:16777216:-:-", columns("T_BLOCK_TEXT"));
        copyAfter("EXECUTE IMMEDIATE $$ BEGIN RETURN CURRENT_DATE(); END; $$", "T_BLOCK_DATE");
        assertEquals("anonymous block:DATE:-:-:-", columns("T_BLOCK_DATE"));
        copyAfter("EXECUTE IMMEDIATE $$ BEGIN RETURN TRUE; END; $$", "T_BLOCK_BOOLEAN");
        assertEquals("anonymous block:BOOLEAN:-:-:-", columns("T_BLOCK_BOOLEAN"));
        engine.execute("CREATE OR REPLACE PROCEDURE p_text() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 1; END; $$");
        copyAfter("CALL p_text()", "T_CALL_TEXT");
        assertEquals("P_TEXT:TEXT:16777216:-:-", columns("T_CALL_TEXT"));
        engine.execute("CREATE OR REPLACE PROCEDURE p_variant() RETURNS VARIANT LANGUAGE SQL AS $$ BEGIN RETURN 1; END; $$");
        copyAfter("CALL p_variant()", "T_CALL_VARIANT");
        assertEquals("P_VARIANT:VARIANT:-:-:-", columns("T_CALL_VARIANT"));
        copyAfter("SELECT 1::VARIANT AS v, 1.5::FLOAT AS f, 12.34::NUMBER(10,2) AS n", "T_TYPED");
        assertEquals("V:VARIANT:-:-:-, F:FLOAT:-:-:-, N:NUMBER:-:10:2", columns("T_TYPED"));
    }

    @Test
    public void aCopyOfAShowOrDescribeResultKeepsItsLowerCaseNames() {
        engine.execute("CREATE OR REPLACE TABLE src (id INT, name VARCHAR(20))");
        copyAfter("SHOW COLUMNS IN TABLE src", "T_SHOW_COLUMNS");
        assertEquals("table_name:TEXT:16777216:-:-, schema_name:TEXT:16777216:-:-, column_name:TEXT:16777216:-:-,"
            + " data_type:TEXT:16777216:-:-, null?:TEXT:16777216:-:-, default:TEXT:16777216:-:-, kind:TEXT:16777216:-:-,"
            + " expression:TEXT:16777216:-:-, comment:TEXT:16777216:-:-, database_name:TEXT:16777216:-:-,"
            + " autoincrement:TEXT:16777216:-:-, schema_evolution_record:TEXT:16777216:-:-, write_default:TEXT:16777216:-:-",
            columns("T_SHOW_COLUMNS"));
        assertEquals("SRC, ID", firstRow("SELECT \"table_name\", \"column_name\" FROM t_show_columns WHERE \"column_name\" = 'ID'"));
        copyAfter("SHOW PARAMETERS LIKE 'TIMEZONE' IN SESSION", "T_SHOW_PARAMETERS");
        assertEquals("key:TEXT:16777216:-:-, value:TEXT:16777216:-:-, default:TEXT:16777216:-:-, level:TEXT:16777216:-:-,"
            + " description:TEXT:16777216:-:-, type:TEXT:16777216:-:-", columns("T_SHOW_PARAMETERS"));
        copyAfter("DESCRIBE TABLE src", "T_DESCRIBE_TABLE");
        assertEquals("NAME", firstCell("SELECT \"name\" FROM t_describe_table WHERE \"default\" IS NULL ORDER BY \"name\" DESC"));
    }

    @Test
    public void aStarProjectsAColumnNamedLikeAKeyword() {
        assertEquals("1, 2, 3, 4, 5, 6", firstRow("SELECT * FROM (SELECT 1 AS \"default\", 2 AS \"DEFAULT\", 3 AS \"true\","
            + " 4 AS \"CURRENT_DATE\", 5 AS \"a b\", 6 AS \"null\")"));
        engine.execute("CREATE OR REPLACE TABLE t_keywords (\"default\" INT, \"select\" INT, \"order\" INT, \"true\" INT, \"a b\" INT)");
        engine.execute("INSERT INTO t_keywords VALUES (1, 2, 3, 4, 5)");
        assertEquals("1, 2, 3, 4, 5", firstRow("SELECT * FROM t_keywords"));
        assertEquals("1, 2, 3, 4, 5", firstRow("SELECT k.* FROM t_keywords k"));
        assertEquals("1, 2, 3, 4, 5", firstRow("SELECT t_keywords.* FROM t_keywords"));
        assertEquals("1, 5", firstRow("SELECT * EXCLUDE (\"select\", \"order\", \"true\") FROM t_keywords"));
        assertEquals("1, 2, 3, 4, 5, 9", firstRow("SELECT * FROM t_keywords k JOIN (SELECT 9 AS \"a b\") j ON TRUE"));
        assertEquals("1", firstRow("SELECT k.* FROM (SELECT 1 AS \"a b\") k"));
        engine.execute("CREATE OR REPLACE TABLE t_keywords_copy AS SELECT * FROM t_keywords");
        assertEquals("default:NUMBER:-:38:0, select:NUMBER:-:38:0, order:NUMBER:-:38:0, true:NUMBER:-:38:0,"
            + " a b:NUMBER:-:38:0", columns("T_KEYWORDS_COPY"));
    }
}
