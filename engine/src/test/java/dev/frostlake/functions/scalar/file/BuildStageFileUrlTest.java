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

package dev.frostlake.functions.scalar.file;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BUILD_STAGE_FILE_URL: a named stage's file URL, {@code …/api/files/<db>/<schema>/<stage>/<path>}. The host is the
 * account's live and the engine's HTTP server here, so the assertions read the path of the URL, which the two share.
 */
public class BuildStageFileUrlTest extends BaseDatabaseTest {

    private static final String FILES = "/api/files/TEST_DB/TEST_SCHEMA/";

    private String value(final String sql) {
        final Object value = engine.executeQuery(sql).getRows().get(0).getValue(0);
        return value == null ? null : value.toString();
    }

    private String url(final String args) {
        return value("SELECT BUILD_STAGE_FILE_URL(" + args + ")");
    }

    private void assertUrl(final String expectedTail, final String args) {
        final String url = url(args);
        assertTrue(url.endsWith(FILES + expectedTail), args + " -> " + url);
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    @Test
    public void theStageIsWrittenBareOrAsAString() {
        engine.execute("CREATE STAGE bsfu");
        assertUrl("BSFU/f.csv", "@bsfu, 'f.csv'");
        assertUrl("BSFU/f.csv", "'@bsfu', 'f.csv'");
        assertUrl("BSFU/f.csv", "@TEST_SCHEMA.bsfu, 'f.csv'");
        assertUrl("BSFU/f.csv", "@test_db.test_schema.bsfu, 'f.csv'");
        assertUrl("BSFU/f.csv", "'@\"BSFU\"', 'f.csv'");
        assertUrl("BSFU/f.csv", "'@bsfu  ', 'f.csv'");
        assertUrl("BSFU/F.CSV", "@bsfu, 'F.CSV'");
        assertUrl("BSFU/", "@bsfu, ''");
        assertNull(url("@bsfu, NULL"));
    }

    @Test
    public void thePathIsPercentEncodedInLowerCaseHex() {
        engine.execute("CREATE STAGE bsfu_enc");
        assertUrl("BSFU_ENC/dir%2fsub%20dir%2ff%201.csv", "@bsfu_enc, 'dir/sub dir/f 1.csv'");
        assertUrl("BSFU_ENC/%2ff.csv", "@bsfu_enc, '/f.csv'");
        assertUrl("BSFU_ENC/a%2bb%26c%3dd%3fe%23f%25g.csv", "@bsfu_enc, 'a+b&c=d?e#f%g.csv'");
        assertUrl("BSFU_ENC/za%c5%bc%c3%b3%c5%82%c4%87.csv", "@bsfu_enc, 'zażółć.csv'");
        assertUrl("BSFU_ENC/~t-_.%21%2a%27%28%29x.csv", "@bsfu_enc, '~t-_.!*''()x.csv'");
        assertUrl("BSFU_ENC/a%2f..%2fb.csv", "@bsfu_enc, 'a/../b.csv'");
    }

    /** A dot is kept in a path the statement folds, and encoded in one read from a column. */
    @Test
    public void aComputedPathEncodesItsDots() {
        engine.execute("CREATE STAGE bsfu_dot");
        assertTrue(value("SELECT BUILD_STAGE_FILE_URL('@bsfu_dot', p) FROM (SELECT 'a-b_c~d.e/f g' p)")
            .endsWith(FILES + "BSFU_DOT/a-b_c~d%2ee%2ff%20g"));
        assertUrl("BSFU_DOT/xy.csv", "'@bsfu_dot', 'x' || 'y.csv'");
        assertUrl("BSFU_DOT/xy.csv", "'@bsfu_dot', CONCAT('x', 'y.csv')");
        assertUrl("BSFU_DOT/Y.CSV", "'@bsfu_dot', UPPER('y.csv')");
    }

    @Test
    public void aQuotedNameIsSpelledAsSqlSpellsIt() {
        engine.execute("CREATE STAGE \"my stage\"");
        engine.execute("CREATE STAGE \"lower\"");
        assertUrl("%22my%20stage%22/f.csv", "'@\"my stage\"', 'f.csv'");
        assertUrl("%22lower%22/f.csv", "@\"lower\", 'f.csv'");
        assertEquals("SQL compilation error: Stage '@lower' provided to the function 'BUILD_STAGE_FILE_URL'"
            + " does not exist or is not authorized.", refusal("SELECT BUILD_STAGE_FILE_URL('@lower', 'f.csv')"));
    }

    @Test
    public void theStageArgumentIsRefusedAsTheAccountRefusesIt() {
        engine.execute("CREATE STAGE bsfu_ref");
        engine.execute("CREATE TABLE bsfu_t (a INT)");
        final String prefix = "SQL compilation error: Argument 1 to function 'BUILD_STAGE_FILE_URL' ";
        assertEquals(prefix + "cannot be null or empty.", refusal("SELECT BUILD_STAGE_FILE_URL(NULL, 'f.csv')"));
        assertEquals(prefix + "cannot be null or empty.", refusal("SELECT BUILD_STAGE_FILE_URL('', 'f.csv')"));
        assertEquals(prefix + "does not start with '@'. Please specify stage name as '@<stage_name>'.",
            refusal("SELECT BUILD_STAGE_FILE_URL('bsfu_ref', 'f.csv')"));
        assertEquals(prefix + "does not start with '@'. Please specify stage name as '@<stage_name>'.",
            refusal("SELECT BUILD_STAGE_FILE_URL('  @bsfu_ref', 'f.csv')"));
        assertEquals("SQL compilation error:\nmissing stage name in URL: @",
            refusal("SELECT BUILD_STAGE_FILE_URL('@', 'f.csv')"));
        assertEquals(prefix + "should only contain the stage name and not a path.",
            refusal("SELECT BUILD_STAGE_FILE_URL('@bsfu_ref/dir', 'f.csv')"));
        assertEquals(prefix + "should only contain the stage name and not a path.",
            refusal("SELECT BUILD_STAGE_FILE_URL('@~/x', 'f.csv')"));
        final String kinds = prefix + "provides a user or table stage. These stage kinds are not supported by this"
            + " function.";
        assertEquals(kinds, refusal("SELECT BUILD_STAGE_FILE_URL(@~, 'f.csv')"));
        assertEquals(kinds, refusal("SELECT BUILD_STAGE_FILE_URL(@%bsfu_t, 'f.csv')"));
        assertEquals(kinds, refusal("SELECT BUILD_STAGE_FILE_URL('@%bsfu_nosuch', 'f.csv')"));
        assertEquals("SQL compilation error: Stage '@bsfu_nosuch' provided to the function 'BUILD_STAGE_FILE_URL'"
            + " does not exist or is not authorized.", refusal("SELECT BUILD_STAGE_FILE_URL(@bsfu_nosuch, 'f.csv')"));
        assertEquals("SQL compilation error: Stage '@bsfu_nosuch  ' provided to the function"
                + " 'BUILD_STAGE_FILE_URL' does not exist or is not authorized.",
            refusal("SELECT BUILD_STAGE_FILE_URL('@bsfu_nosuch  ', 'f.csv')"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 9 unexpected '<EOF>'.",
            refusal("SELECT BUILD_STAGE_FILE_URL('@bsfu_ref.', 'f.csv')"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 0 unexpected '.'.",
            refusal("SELECT BUILD_STAGE_FILE_URL('@.bsfu_ref', 'f.csv')"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 12 unexpected '.'.",
            refusal("SELECT BUILD_STAGE_FILE_URL('@test_schema..bsfu_ref', 'f.csv')"));
        // A name has three parts at most; a dot after the third is refused where it stands, bare or quoted.
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 28 unexpected '.'.",
            refusal("SELECT BUILD_STAGE_FILE_URL('@test_db.test_schema.bsfu_ref.x', 'f.csv')"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 28 unexpected '.'.",
            refusal("SELECT BUILD_STAGE_FILE_URL(@test_db.test_schema.bsfu_ref.x, 'f.csv')"));
    }

    /**
     * A directory table's FILE_URL is the file's stage file URL, its path spelled as a path read from a row is —
     * BUILD_STAGE_FILE_URL(@stage, RELATIVE_PATH) — a name that other names continue included.
     */
    @Test
    public void theDirectoryTablesFileUrlIsTheStageFileUrl() {
        engine.execute("CREATE STAGE bsfu_dir DIRECTORY = (ENABLE = TRUE)");
        engine.execute("COPY INTO @bsfu_dir/k FROM (SELECT 1) FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE)"
            + " SINGLE = TRUE");
        engine.execute("COPY INTO @bsfu_dir/k/m.csv FROM (SELECT 2) FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE)"
            + " SINGLE = TRUE");
        if (isLiveSnowflake()) {
            // The account lists a staged file once its directory is refreshed; this engine's listing is always current.
            engine.execute("ALTER STAGE bsfu_dir REFRESH");
        }
        final List<Row> rows = engine.executeQuery("SELECT RELATIVE_PATH, FILE_URL,"
            + " BUILD_STAGE_FILE_URL(@bsfu_dir, RELATIVE_PATH) FROM DIRECTORY(@bsfu_dir) ORDER BY 1").getRows();
        assertEquals(2, rows.size());
        assertEquals("k", String.valueOf(rows.get(0).getValue(0)));
        assertTrue(String.valueOf(rows.get(0).getValue(1)).endsWith(FILES + "BSFU_DIR/k"),
            String.valueOf(rows.get(0).getValue(1)));
        assertEquals(rows.get(0).getValue(2), rows.get(0).getValue(1));
        assertEquals("k/m.csv", String.valueOf(rows.get(1).getValue(0)));
        assertTrue(String.valueOf(rows.get(1).getValue(1)).endsWith(FILES + "BSFU_DIR/k%2fm%2ecsv"),
            String.valueOf(rows.get(1).getValue(1)));
        assertEquals(rows.get(1).getValue(2), rows.get(1).getValue(1));
    }

    @Test
    public void theStageMustBeAConstantString() {
        engine.execute("CREATE STAGE bsfu_const");
        assertEquals("SQL compilation error:\nArgument number 1 for function 'BUILD_STAGE_FILE_URL' needs to be a"
            + " string literal.", refusal("SELECT BUILD_STAGE_FILE_URL(TRUE, 'f.csv')"));
        assertEquals("SQL compilation error:\nArgument number 1 for function 'BUILD_STAGE_FILE_URL' needs to be a"
            + " string literal.", refusal("SELECT BUILD_STAGE_FILE_URL(1, 'f.csv')"));
        assertEquals("SQL compilation error:\nargument 1 to function BUILD_STAGE_FILE_URL needs to be constant,"
            + " found 'X'", refusal("SELECT BUILD_STAGE_FILE_URL(x, 'f.csv') FROM (SELECT '@bsfu_const' x)"));
        assertEquals("SQL compilation error:\nargument 1 to function BUILD_STAGE_FILE_URL needs to be constant,"
            + " found ''@' || 'bsfu_const''", refusal("SELECT BUILD_STAGE_FILE_URL('@' || 'bsfu_const', 'f.csv')"));
        assertEquals("SQL compilation error:\nargument 1 to function BUILD_STAGE_FILE_URL needs to be constant,"
            + " found 'CAST(null AS VARCHAR(134217728))'", refusal("SELECT BUILD_STAGE_FILE_URL(NULL::VARCHAR, 'f')"));
        engine.execute("SET bsfu_var = '@bsfu_const'");
        assertUrl("BSFU_CONST/f.csv", "$bsfu_var, 'f.csv'");
    }

    @Test
    public void theCountIsTheCallsOwnSentence() {
        engine.execute("CREATE STAGE bsfu_n");
        assertEquals("SQL compilation error: error line 1 at position 7\nnot enough arguments for function"
            + " [BUILD_STAGE_FILE_URL('@bsfu_n')], expected 2, got 1", refusal("SELECT BUILD_STAGE_FILE_URL(@bsfu_n)"));
        assertEquals("SQL compilation error: error line 1 at position 7\ntoo many arguments for function"
                + " [BUILD_STAGE_FILE_URL('@bsfu_n', 'f.csv', 1)] expected 2, got 3",
            refusal("SELECT BUILD_STAGE_FILE_URL(@bsfu_n, 'f.csv', 1)"));
    }

    /** The stage is resolved while the statement compiles, so a missing one is refused over no rows. */
    @Test
    public void aMissingStageIsRefusedOverAnEmptyTable() {
        engine.execute("CREATE STAGE bsfu_e");
        engine.execute("CREATE TABLE bsfu_rows (a INT)");
        engine.execute("INSERT INTO bsfu_rows VALUES (1), (2)");
        assertEquals(0, engine.executeQuery("SELECT BUILD_STAGE_FILE_URL(@bsfu_e, 'f.csv') AS u FROM bsfu_rows"
            + " WHERE FALSE").getRows().size());
        assertTrue(refusal("SELECT BUILD_STAGE_FILE_URL(@bsfu_missing, 'f.csv') AS u FROM bsfu_rows WHERE FALSE")
            .contains("Stage '@bsfu_missing' provided to the function 'BUILD_STAGE_FILE_URL' does not exist"));
        assertTrue(value("SELECT BUILD_STAGE_FILE_URL(@bsfu_e, 'x' || a) FROM bsfu_rows ORDER BY 1")
            .endsWith(FILES + "BSFU_E/x1"));
    }

    @Test
    public void theResultIsABareVarchar() {
        engine.execute("CREATE STAGE bsfu_type");
        assertEquals("VARCHAR[LOB]", value("SELECT SYSTEM$TYPEOF(BUILD_STAGE_FILE_URL(@bsfu_type, 'f.csv'))"));
    }

    /** The derived column name is the call as written, upper-cased. */
    @Test
    public void theColumnIsNamedByTheCallAsWritten() {
        engine.execute("CREATE STAGE bsfu_name");
        assertEquals("BUILD_STAGE_FILE_URL(@BSFU_NAME, 'F.CSV')", engine.executeQuery(
            "SELECT BUILD_STAGE_FILE_URL(@bsfu_name, 'f.csv')").getColumns().get(0).getName());
    }
}
