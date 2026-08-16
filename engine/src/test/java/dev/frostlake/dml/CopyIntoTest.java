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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * COPY INTO over INTERNAL named stages, so the load cells run on every transport: an empty stage
 * is a legitimate no-op ({@code Copy executed with 0 files processed.}), naming absent FILES is an
 * error, the JSON one-column rule fires at compile time even with nothing staged, and unloads
 * answer {@code rows_unloaded} — all live-verified. Only the cells whose essence is a raw cloud
 * URL, the engine's own optional-module refusal, or a transformation function live does not allow
 * in COPY stay embedded-only.
 */
public class CopyIntoTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(CopyIntoTest.class);

    private static final String RAW_CLOUD_URL =
        "targets a raw s3:// URL; a real account contacts the bucket and fails with Access "
        + "Denied (403) — the cell's essence is the URL form itself";

    private static final String MODULE_REFUSAL_IS_ENGINE_OWN =
        "pins the engine's own frostlake-formats module-missing refusal; a real account loads "
        + "PARQUET natively";

    private static final String NO_FILES = "Copy executed with 0 files processed.";

    @Test
    public void testCopyIntoTableFromStageSimple() {
        logger.info("Testing COPY INTO table FROM stage - simple case");

        engine.execute("CREATE TABLE employees (id INTEGER, name VARCHAR, salary DECIMAL(10,2))");
        engine.execute("CREATE STAGE my_stage");

        final ResultSet rs = engine.executeQuery("""
            COPY INTO employees
            FROM @my_stage
            """);
        assertEquals(NO_FILES, rs.getRows().get(0).getValue(0).toString());
    }

    @Test
    public void testCopyIntoTableWithFileFormat() {
        logger.info("Testing COPY INTO with FILE_FORMAT");

        engine.execute("CREATE TABLE sales (sale_id INTEGER, product VARCHAR, amount DECIMAL(10,2))");
        engine.execute("CREATE STAGE sales_stage");

        engine.execute("""
            COPY INTO sales
            FROM @sales_stage
            FILE_FORMAT = (TYPE = 'CSV' FIELD_DELIMITER = ',' SKIP_HEADER = 1)
            """);
    }

    @Test
    public void testCopyIntoTableWithPattern() {
        logger.info("Testing COPY INTO with PATTERN");

        engine.execute("CREATE TABLE logs (timestamp VARCHAR, message VARCHAR, level VARCHAR)");
        engine.execute("CREATE STAGE log_stage");

        final ResultSet rs = engine.executeQuery("""
            COPY INTO logs
            FROM @log_stage
            PATTERN = '.*\\.csv'
            """);
        assertEquals(NO_FILES, rs.getRows().get(0).getValue(0).toString());
    }

    @Test
    public void testCopyIntoTableWithOnError() {
        logger.info("Testing COPY INTO with ON_ERROR");

        engine.execute("CREATE TABLE transactions (id INTEGER, amount DECIMAL(10,2), status VARCHAR)");
        engine.execute("CREATE STAGE tx_stage");

        engine.execute("""
            COPY INTO transactions
            FROM @tx_stage
            FILE_FORMAT = (TYPE = 'CSV')
            ON_ERROR = 'CONTINUE'
            """);
    }

    @Test
    public void testCopyIntoTableWithValidationMode() {
        logger.info("Testing COPY INTO with VALIDATION_MODE");

        engine.execute("CREATE TABLE test_data (col1 INTEGER, col2 VARCHAR)");
        engine.execute("CREATE STAGE test_stage");

        engine.execute("""
            COPY INTO test_data
            FROM @test_stage
            VALIDATION_MODE = 'RETURN_ERRORS'
            """);
    }

    @Test
    public void testCopyIntoTableWithForce() {
        logger.info("Testing COPY INTO with FORCE");

        engine.execute("CREATE TABLE reload_data (id INTEGER, value VARCHAR)");
        engine.execute("CREATE STAGE reload_stage");

        engine.execute("""
            COPY INTO reload_data
            FROM @reload_stage
            FORCE = TRUE
            """);
    }

    /**
     * FILES = (…) over an empty stage. Naming files that are not there is an ERROR (SQLSTATE 22000,
     * error 91016) rather than a quiet no-op — see {@link CopyMissingFileTest} for the whole rule
     * and its live evidence.
     */
    @Test
    public void testCopyIntoTableWithFiles() {
        logger.info("Testing COPY INTO with FILES");

        engine.execute("CREATE TABLE specific_files (id INTEGER, data VARCHAR)");
        engine.execute("CREATE STAGE files_stage");

        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("""
                    COPY INTO specific_files
                    FROM @files_stage
                    FILES = ('file1.csv', 'file2.csv', 'file3.csv')
                    """);
            }
        });
        assertTrue(e.getMessage().contains("Remote file") && e.getMessage().contains("was not found"),
            e.getMessage());
    }

    @Test
    public void testCopyIntoTableWithColumnMapping() {
        logger.info("Testing COPY INTO with column mapping");

        engine.execute("CREATE TABLE mapped_data (id INTEGER, name VARCHAR, email VARCHAR)");
        engine.execute("CREATE STAGE mapped_stage");

        engine.execute("""
            COPY INTO mapped_data (id, name, email)
            FROM @mapped_stage
            FILE_FORMAT = (TYPE = 'CSV')
            """);
    }

    @Test
    public void testCopyIntoTableFromS3Direct() {
        Assumptions.assumeFalse(isLiveSnowflake(), RAW_CLOUD_URL);
        logger.info("Testing COPY INTO from S3 URL directly");

        engine.execute("CREATE TABLE s3_data (id INTEGER, value VARCHAR)");

        engine.execute("""
            COPY INTO s3_data
            FROM 's3://mybucket/data/'
            FILE_FORMAT = (TYPE = 'CSV')
            """);
    }

    @Test
    public void testCopyIntoTableWithJsonFormat() {
        logger.info("Testing COPY INTO with JSON format");

        engine.execute("CREATE TABLE json_data (data VARIANT)");
        engine.execute("CREATE STAGE json_stage");

        // A JSON record is ONE semi-structured value, so the target must be a SINGLE variant/object/array
        // column; the rule fires at compile time even over an empty stage, live-verified:
        // "JSON file format can produce one and only one column of type variant, object, or array. Load
        // data into separate columns using the MATCH_BY_COLUMN_NAME copy option or copy with
        // transformation."
        engine.execute("""
            COPY INTO json_data
            FROM @json_stage
            FILE_FORMAT = (TYPE = 'JSON')
            """);
        engine.execute("CREATE TABLE json_pair (id INTEGER, data VARIANT)");
        final RuntimeException tooManyColumns = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("""
                    COPY INTO json_pair
                    FROM @json_stage
                    FILE_FORMAT = (TYPE = 'JSON')
                    """);
            }
        });
        assertTrue(tooManyColumns.getMessage().contains("one and only one column"),
            tooManyColumns.getMessage());
    }

    // Without the optional frostlake-formats module on the classpath, the engine ships only the JSON/XML
    // readers — so COPY of Avro/Parquet/ORC is rejected loudly with a pointer to the module, rather than
    // silently "succeeding" while loading nothing. (The formats module's own tests cover a real PARQUET load.)
    @Test
    public void testCopyIntoTableRejectsParquetWithoutFormatsModule() {
        Assumptions.assumeFalse(isLiveSnowflake(), MODULE_REFUSAL_IS_ENGINE_OWN);
        // A single variant column, so the load reaches the reader rather than failing the
        // one-column rule first (see testCopyIntoTableWithJsonFormat).
        engine.execute("CREATE TABLE parquet_data (data VARIANT)");
        engine.execute("CREATE STAGE parquet_stage");

        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("""
                    COPY INTO parquet_data
                    FROM @parquet_stage
                    FILE_FORMAT = (TYPE = 'PARQUET')
                    """);
            }
        });
        assertTrue(ex.getMessage().contains("frostlake-formats"), ex.getMessage());
    }

    @Test
    public void testCopyIntoTableComplexOptions() {
        logger.info("Testing COPY INTO with multiple complex options");

        engine.execute("""
            CREATE TABLE complex_load (
                id INTEGER,
                name VARCHAR,
                email VARCHAR,
                created_date DATE
            )
            """);
        engine.execute("CREATE STAGE complex_stage");

        engine.execute("""
            COPY INTO complex_load (id, name, email, created_date)
            FROM @complex_stage
            FILE_FORMAT = (
                TYPE = 'CSV'
                FIELD_DELIMITER = ','
                SKIP_HEADER = 1
                DATE_FORMAT = 'YYYY-MM-DD'
                COMPRESSION = 'GZIP'
            )
            PATTERN = '.*data.*\\.csv\\.gz'
            ON_ERROR = 'SKIP_FILE'
            SIZE_LIMIT = 1000000
            PURGE = TRUE
            """);
    }

    @Test
    public void testCopyIntoStageFromTable() {
        logger.info("Testing COPY INTO stage FROM table (unload)");

        engine.execute("CREATE TABLE export_data (id INTEGER, name VARCHAR, value DECIMAL(10,2))");
        engine.execute("INSERT INTO export_data VALUES (1, 'Item1', 100.50), (2, 'Item2', 200.75)");
        engine.execute("CREATE STAGE export_stage");

        final ResultSet rs = engine.executeQuery("""
            COPY INTO @export_stage
            FROM export_data
            FILE_FORMAT = (TYPE = 'CSV' FIELD_DELIMITER = ',')
            """);
        // Space-separated FILE_FORMAT options parse, and both rows are unloaded (rows_unloaded).
        assertEquals(2, ((Number) rs.getRows().get(0).getValue(0)).intValue());
    }

    @Test
    public void testCopyIntoStageFromQuery() {
        logger.info("Testing COPY INTO stage FROM query");

        engine.execute("CREATE TABLE source_data (id INTEGER, category VARCHAR, amount DECIMAL(10,2))");
        engine.execute("INSERT INTO source_data VALUES (1, 'A', 100), (2, 'B', 200), (3, 'A', 150)");
        engine.execute("CREATE STAGE query_export_stage");

        final ResultSet rs = engine.executeQuery("""
            COPY INTO @query_export_stage
            FROM (SELECT category, SUM(amount) as total FROM source_data GROUP BY category)
            FILE_FORMAT = (TYPE = 'CSV')
            """);
        // Two category groups (A, B) are unloaded from the query.
        assertEquals(2, ((Number) rs.getRows().get(0).getValue(0)).intValue());
    }

    @Test
    public void testCopyTransformationHonorsTheFunctionAllowlist() {
        logger.info("Testing the COPY transformation function allowlist");

        engine.execute("CREATE TABLE transformed_data (id INTEGER, name VARCHAR, tail VARCHAR)");
        engine.execute("CREATE STAGE transform_stage");

        // The allowed shapes run (0 files staged — the check is compile-time either way):
        // conversions, SUBSTR, IFF, CONCAT/||, casts.
        engine.execute("""
            COPY INTO transformed_data
            FROM (
                SELECT $1::INTEGER, IFF($2 = '', 'empty', CONCAT($2, '!')), SUBSTR($3, 1, 3)
                FROM @transform_stage
            )
            FILE_FORMAT = (TYPE = 'CSV')
            """);

        // UPPER is outside the account's allowlist — refused at compile time with live's own
        // capital-C casing; arithmetic refuses by its operator symbol.
        final RuntimeException upper = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("""
                    COPY INTO transformed_data
                    FROM (SELECT $1, UPPER($2), $3 FROM @transform_stage)
                    FILE_FORMAT = (TYPE = 'CSV')
                    """);
            }
        });
        assertEquals("SQL Compilation error: Function 'UPPER' not supported within a COPY",
            upper.getMessage());

        final RuntimeException arithmetic = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("""
                    COPY INTO transformed_data
                    FROM (SELECT $1, $2, $3 * 2 FROM @transform_stage)
                    FILE_FORMAT = (TYPE = 'CSV')
                    """);
            }
        });
        assertEquals("SQL Compilation error: Function '*' not supported within a COPY",
            arithmetic.getMessage());
    }

    @Test
    public void testCopyIntoTableReturnsResults() {
        logger.info("Testing COPY INTO returns result information");

        engine.execute("CREATE TABLE result_test (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STAGE result_stage");

        final ResultSet result = engine.executeQuery("""
            COPY INTO result_test
            FROM @result_stage
            FILE_FORMAT = (TYPE = 'CSV')
            """);

        assertNotNull(result);
        assertEquals(NO_FILES, result.getRows().get(0).getValue(0).toString());
    }

    @Test
    public void testMultipleCopyOperations() {
        logger.info("Testing multiple COPY operations in sequence");

        engine.execute("CREATE TABLE batch1 (id INTEGER, data VARCHAR)");
        engine.execute("CREATE TABLE batch2 (id INTEGER, data VARCHAR)");
        engine.execute("CREATE STAGE batch_stage");

        engine.execute("""
            COPY INTO batch1
            FROM @batch_stage
            PATTERN = '.*batch1_.*\\.csv'
            FILE_FORMAT = (TYPE = 'CSV')
            """);

        engine.execute("""
            COPY INTO batch2
            FROM @batch_stage
            PATTERN = '.*batch2_.*\\.csv'
            FILE_FORMAT = (TYPE = 'CSV')
            """);
    }

    @Test
    public void testCopyIntoTableWithMatchByColumnName() {
        logger.info("Testing COPY INTO with MATCH_BY_COLUMN_NAME");

        engine.execute("CREATE TABLE column_match (id INTEGER, name VARCHAR, email VARCHAR)");
        engine.execute("CREATE STAGE column_stage");

        engine.execute("""
            COPY INTO column_match
            FROM @column_stage
            FILE_FORMAT = (TYPE = 'JSON')
            MATCH_BY_COLUMN_NAME = 'CASE_INSENSITIVE'
            """);
    }
}
