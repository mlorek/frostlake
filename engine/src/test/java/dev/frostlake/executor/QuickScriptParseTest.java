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

package dev.frostlake.executor;

import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SqlCharStream;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The quick first stage of the statement parse answers only for a script it reads cleanly, and then reads it
 * exactly as full prediction does; a script with a fault of any kind - a syntax error, a character no token takes -
 * is left to the full parse, which words the refusal.
 */
public class QuickScriptParseTest {

    private static final List<String> RULES = Arrays.asList(FrostlakeParser.ruleNames);

    private static final String[] SCRIPTS = {
        "SELECT 1",
        "SELECT a, COUNT(*) FROM t WHERE b > 1 GROUP BY a HAVING COUNT(*) > 1 ORDER BY 1 DESC NULLS LAST LIMIT 5",
        "SELECT * FROM t1 JOIN t2 USING (id) LEFT JOIN t3 ON t3.a = t1.a QUALIFY ROW_NUMBER() OVER (ORDER BY 1) = 1",
        "WITH c AS (SELECT 1 AS x), d (y) AS (SELECT 2) SELECT x, y FROM c, d",
        "SELECT x FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[1,2]'))) f, LATERAL (SELECT f.value AS x)",
        "SELECT * FROM t PIVOT (SUM(v) FOR k IN ('a', 'b')) AS p",
        "SELECT 1 UNION ALL SELECT 2 EXCEPT SELECT 3",
        "INSERT INTO t (a, b) VALUES (1, 'x'), (2, 'y')",
        "INSERT OVERWRITE INTO t SELECT * FROM s",
        "UPDATE t SET a = a + 1, b = 'z' WHERE c IN (SELECT c FROM s)",
        "DELETE FROM t USING s WHERE t.a = s.a",
        "MERGE INTO t USING s ON t.a = s.a WHEN MATCHED THEN UPDATE SET b = s.b"
            + " WHEN NOT MATCHED THEN INSERT (a, b) VALUES (s.a, s.b)",
        "CREATE OR REPLACE TABLE t (a NUMBER(10,2) DEFAULT 1, b VARCHAR NOT NULL, PRIMARY KEY (a))",
        "CREATE TEMPORARY TABLE t2 AS SELECT * FROM t",
        "CREATE OR REPLACE VIEW v AS SELECT a FROM t WHERE b = 'x'",
        "CREATE FUNCTION f(x NUMBER, y VARCHAR DEFAULT 'd') RETURNS NUMBER AS 'x + 1'",
        "CREATE OR REPLACE PROCEDURE p(a NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN a; END; $$",
        "ALTER TABLE t ADD COLUMN c NUMBER DEFAULT 7",
        "DROP TABLE IF EXISTS t",
        "CALL p(a => 1)",
        "SHOW TABLES LIKE 'T%' IN SCHEMA s",
        "DESCRIBE TABLE t",
        "USE SCHEMA db.s",
        "ALTER SESSION SET TIMEZONE = 'UTC'",
        "BEGIN LET x := 1; IF (x > 0) THEN RETURN x + 1; END IF; RETURN 0; END;",
        "EXECUTE IMMEDIATE 'SELECT 1'",
        "SELECT 1; SELECT 2;",
        "GRANT SELECT ON TABLE t TO ROLE r",
        "COPY INTO load1 FROM @%load1/data1/ FILES = ('t1.csv')",
        "SELECT CURRENT_TIMESTAMP((3))",
    };

    private static String fullPredictionTree(final String script) {
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(script));
        lexer.removeErrorListeners();
        final FrostlakeParser parser = new FrostlakeParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.setErrorHandler(new BailErrorStrategy());
        return parser.sqlScript().toStringTree(RULES);
    }

    @Test
    public void aScriptTheQuickStageReadsIsReadAsFullPredictionReadsIt() {
        int quickly = 0;
        for (final String script : SCRIPTS) {
            final QuickScriptParse quick = QuickScriptParse.of(script);
            if (quick != null) {
                quickly++;
                assertEquals(fullPredictionTree(script), quick.tree().toStringTree(RULES), script);
                assertNotNull(quick.tokens(), script);
            }
        }
        // The quick stage is the usual one: nearly every well-formed script is read by it.
        assertTrue(quickly >= SCRIPTS.length - 4, quickly + " of " + SCRIPTS.length);
    }

    @Test
    public void aScriptWithAFaultIsLeftToTheFullParse() {
        assertNull(QuickScriptParse.of("SELECT FROM"));
        assertNull(QuickScriptParse.of("SELECT 1 +"));
        assertNull(QuickScriptParse.of("SELEC 1"));
        assertNull(QuickScriptParse.of("SELECT (1"));
        assertNull(QuickScriptParse.of("CREATE TABLE t (a NUMBER,)"));
    }
}
