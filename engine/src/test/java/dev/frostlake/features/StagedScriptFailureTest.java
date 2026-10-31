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
package dev.frostlake.features;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * EXECUTE IMMEDIATE FROM runs a staged script's statements one after another, each committing as it ends unless a
 * transaction is open - the caller's, or one the script began, which outlives the script - and a statement that fails
 * stops the script with an uncaught STATEMENT_ERROR that names the file by its stage's name and its path, and the line
 * and position the failing statement starts at; a script that does not parse is refused naming the file alone
 * (live-verified).
 */
public class StagedScriptFailureTest extends MeasuredCellsTestSupport {

    /** Where each run writes the scripts it stages. */
    @TempDir
    Path dir;

    /**
     * A script's writes join the caller's transaction, a transaction the script begins stays open after it, a COMMIT in
     * it ends the caller's, and a failure leaves the caller's open.
     */
    @Test
    public void aScriptWritesInTheTransactionThatIsOpen() throws IOException {
        engine.execute("CREATE OR REPLACE TABLE ts783 (i INT)");
        engine.execute("CREATE OR REPLACE STAGE st783");
        stage("st783", "s2.sql", "INSERT INTO ts783 VALUES (1);\nINSERT INTO ts783 VALUES (2);\n");
        stage("st783", "s3.sql", "BEGIN TRANSACTION;\nINSERT INTO ts783 VALUES (3);\n");
        stage("st783", "s4.sql", "INSERT INTO ts783 VALUES (4);\nSELECT 1/0;\nINSERT INTO ts783 VALUES (5);\n");
        stage("st783", "s5.sql", "INSERT INTO ts783 VALUES (6);\nCOMMIT;\n");
        assertCells(new String[][] {
            {"BEGIN TRANSACTION",
                "Statement executed successfully."},
            {"EXECUTE IMMEDIATE FROM @st783/s2.sql",
                "1"},
            {"SELECT CURRENT_TRANSACTION() IS NOT NULL",
                "true"},
            {"ROLLBACK",
                "Statement executed successfully."},
            {"SELECT COUNT(*) FROM ts783",
                "0"},
            {"EXECUTE IMMEDIATE FROM @st783/s3.sql",
                "1"},
            {"SELECT CURRENT_TRANSACTION() IS NOT NULL",
                "true"},
            {"ROLLBACK",
                "Statement executed successfully."},
            {"SELECT COUNT(*) FROM ts783",
                "0"},
            {"EXECUTE IMMEDIATE FROM @st783/s4.sql",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' in file @ST783/s4.sql on line 2 at position 0:  "
                + "| Division by zero"},
            {"SELECT COUNT(*) FROM ts783",
                "1"},
            {"BEGIN TRANSACTION",
                "Statement executed successfully."},
            {"EXECUTE IMMEDIATE FROM @st783/s5.sql",
                "Statement executed successfully."},
            {"ROLLBACK",
                "Statement executed successfully."},
            {"SELECT COUNT(*) FROM ts783",
                "2"},
            {"BEGIN TRANSACTION",
                "Statement executed successfully."},
            {"EXECUTE IMMEDIATE FROM @st783/s3.sql",
                "1"},
            {"SELECT CURRENT_TRANSACTION() IS NOT NULL",
                "true"},
            {"ROLLBACK",
                "Statement executed successfully."},
            {"SELECT COUNT(*) FROM ts783",
                "2"},
            {"BEGIN TRANSACTION",
                "Statement executed successfully."},
            {"EXECUTE IMMEDIATE FROM @st783/s4.sql",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' in file @ST783/s4.sql on line 2 at position 0:  "
                + "| Division by zero"},
            {"SELECT CURRENT_TRANSACTION() IS NOT NULL",
                "true"},
            {"ROLLBACK",
                "Statement executed successfully."},
            {"SELECT COUNT(*) FROM ts783",
                "2"},
        });
    }

    /**
     * A failure names the stage by its name however the statement wrote it, and the line and position of the statement
     * that failed - inside a block, the block's own statement.
     */
    @Test
    public void aFailingStatementIsPlacedInItsFile() throws IOException {
        engine.execute("CREATE OR REPLACE TABLE tf784 (i INT)");
        engine.execute("CREATE OR REPLACE STAGE sf784");
        stage("sf784", "f1.sql", "SELECT 1/0;\n");
        stage("sf784", "f2.sql", "SELECT 1;\nSELECT * FROM nosuch784;\n");
        stage("sf784", "f3.sql", "SELECT 1;\nSELEC 2;\n");
        stage("sf784", "f4.sql", "  SELECT 1/0;\n");
        stage("sf784", "f5.sql", "SELECT 1; SELECT 1/0;\n");
        stage("sf784", "f6.sql", "INSERT INTO tf784 VALUES (1);\nBEGIN\n  SELECT 1/0;\nEND;\n");
        stage("sf784", "f7.sql", "-- comment\nINSERT INTO tf784 VALUES (2);\nSELECT 1/0;\n");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE FROM @sf784/f1.sql",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' in file @SF784/f1.sql on line 1 at position 0:  "
                + "| Division by zero"},
            {"EXECUTE IMMEDIATE FROM @sf784/f2.sql",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' in file @SF784/f2.sql on line 2 at position 0:  "
                + "| SQL compilation error: | Object 'NOSUCH784' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE NOSUCH784."},
            {"EXECUTE IMMEDIATE FROM @sf784/f3.sql",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' in file @SF784/f3.sql:  | SQL compilation error: "
                + "| syntax error line 2 at position 0 unexpected 'SELEC'."},
            {"EXECUTE IMMEDIATE FROM @sf784/f4.sql",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' in file @SF784/f4.sql on line 1 at position 2:  "
                + "| Division by zero"},
            {"EXECUTE IMMEDIATE FROM @sf784/f5.sql",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' in file @SF784/f5.sql on line 1 at position 10:  "
                + "| Division by zero"},
            {"EXECUTE IMMEDIATE FROM @sf784/f6.sql",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' in file @SF784/f6.sql on line 3 at position 2:  "
                + "| Division by zero"},
            {"EXECUTE IMMEDIATE FROM @sf784/f7.sql",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' in file @SF784/f7.sql on line 3 at position 0:  "
                + "| Division by zero"},
            {"SELECT COUNT(*) FROM tf784",
                "2"},
            {"EXECUTE IMMEDIATE FROM '@sf784/f1.sql'",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' in file @SF784/f1.sql on line 1 at position 0:  "
                + "| Division by zero"},
            {"EXECUTE IMMEDIATE FROM @SF784/f1.sql",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' in file @SF784/f1.sql on line 1 at position 0:  "
                + "| Division by zero"},
            {"EXECUTE IMMEDIATE FROM @test_db.test_schema.sf784/f1.sql",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' in file @SF784/f1.sql on line 1 at position 0:  "
                + "| Division by zero"},
            {"EXECUTE IMMEDIATE FROM @sf784/./f1.sql",
                "REFUSED File '/./f1.sql' not found in stage 'TEST_DB.TEST_SCHEMA.SF784'."},
        });
    }

    /** Writes a script and stages it, uncompressed, on the stage. */
    private void stage(final String stage, final String name, final String text) throws IOException {
        final Path file = Files.write(dir.resolve(name), text.getBytes(StandardCharsets.UTF_8));
        engine.execute("PUT 'file://" + file + "' @" + stage + " AUTO_COMPRESS = FALSE");
    }
}
