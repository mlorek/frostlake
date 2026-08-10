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

package dev.frostlake;

import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CREATE / SHOW / DESCRIBE / ALTER / DROP CORTEX SEARCH SERVICE, live-verified. The engine keeps the
 * whole definition and does no embedding of its own — that belongs to the optional {@code frostlake-ai}
 * pack — so every statement here works on a bare engine.
 */
public class CortexSearchServiceTest extends BaseDatabaseTest {

    private static final String CREATE_S1 = """
        CREATE CORTEX SEARCH SERVICE s1
          ON body
          ATTRIBUTES cat
          WAREHOUSE = fl_cortex_wh
          TARGET_LAG = '1 hour'
          AS (SELECT id, body, cat FROM docs)
        """;

    /**
     * The warehouse a service names must EXIST — live rejects an unknown one, where the embedded
     * engine accepts any name. So the class makes its own and takes it away again: warehouses are
     * account-level and would otherwise outlive the run, the way the compute pools did.
     */
    /**
     * A trial account carries no Cortex AI functions, so every service here would fail with the
     * account's own refusal. Probe once and SKIP with that sentence rather than reporting nineteen
     * errors that say nothing about fidelity. Embedded runs never probe.
     */
    @BeforeEach
    public void requireCortexOnLiveAccount() {
        if (!isLiveSnowflake()) {
            return;
        }
        try {
            engine.executeQuery(
                "SELECT SNOWFLAKE.CORTEX.EMBED_TEXT_768('snowflake-arctic-embed-m', 'probe')");
        } catch (final RuntimeException refused) {
            LiveFeatureGate.skipIfAccountTierBlocks(refused);
        }
    }

    @BeforeEach
    public void createSourceTable() {
        for (final String warehouse : List.of("fl_cortex_wh", "fl_cortex_wh2")) {
            engine.execute("CREATE WAREHOUSE IF NOT EXISTS " + warehouse
                + " WAREHOUSE_SIZE = XSMALL INITIALLY_SUSPENDED = TRUE");
        }
        engine.execute("CREATE TABLE docs(id INT, body VARCHAR, cat VARCHAR)");
        engine.execute("INSERT INTO docs VALUES (1, 'the battery lasts', 'power')");
    }

    @AfterEach
    public void dropTheWarehousesThisClassCreates() {
        for (final String warehouse : List.of("fl_cortex_wh", "fl_cortex_wh2")) {
            try {
                engine.execute("DROP WAREHOUSE IF EXISTS " + warehouse);
            } catch (final RuntimeException alreadyGone) {
                // Best effort; a warehouse that was never created is not a failure.
            }
        }
    }

    private String cell(final ResultSet rs, final String column) {
        final Object value = rs.getRows().get(0).getValue(rs.getColumnIndex(column));
        return value == null ? null : value.toString();
    }

    private List<String> columnNames(final ResultSet rs) {
        final List<String> names = new ArrayList<>();
        for (int i = 0; i < rs.getColumnCount(); i++) {
            names.add(rs.getColumns().get(i).getName());
        }
        return names;
    }

    @Test
    public void createStoresTheWholeDefinition() {
        engine.execute(CREATE_S1);
        final ResultSet rs = engine.executeQuery("SHOW CORTEX SEARCH SERVICES");
        assertEquals(1, rs.getRowCount());
        assertEquals("S1", cell(rs, "name"));
        assertEquals("BODY", cell(rs, "search_column"));
        assertEquals("[\"CAT\"]", cell(rs, "attribute_columns"));
        assertEquals("FL_CORTEX_WH", cell(rs, "warehouse"));
        assertEquals("1 hour", cell(rs, "target_lag"));
        assertEquals("TEST_DB", cell(rs, "database_name"));
        assertEquals("TEST_SCHEMA", cell(rs, "schema_name"));
        assertTrue(cell(rs, "definition").contains("FROM docs"), "definition keeps the query as written");
    }

    /** SHOW's 20 columns, in a real account's order. */
    @Test
    public void showColumnShapeMatchesSnowflake() {
        engine.execute(CREATE_S1);
        final List<String> expected = List.of(
            "created_on", "name", "database_name", "schema_name", "target_lag", "warehouse",
            "search_column", "attribute_columns", "columns", "definition", "comment",
            "embedding_model", "indexing_state", "serving_state", "source_data_num_rows",
            "primary_key_columns", "scoring_profile_count", "auto_suspend", "refresh_mode",
            "vector_indexes");
        assertEquals(expected, columnNames(engine.executeQuery("SHOW CORTEX SEARCH SERVICES")));
    }

    /** The indexed columns are the ones the defining query projects, not the table's. */
    @Test
    public void columnsComeFromTheDefiningQuery() {
        engine.execute("""
            CREATE CORTEX SEARCH SERVICE narrow
              ON body WAREHOUSE = fl_cortex_wh TARGET_LAG = '1 hour'
              AS (SELECT id, body FROM docs)
            """);
        final ResultSet rs = engine.executeQuery("SHOW CORTEX SEARCH SERVICES");
        assertEquals("[\"ID\",\"BODY\"]", cell(rs, "columns"));
    }

    /** No EMBEDDING_MODEL means Snowflake's default, reported rather than left blank. */
    @Test
    public void embeddingModelDefaults() {
        engine.execute(CREATE_S1);
        assertEquals("snowflake-arctic-embed-m-v1.5",
            cell(engine.executeQuery("SHOW CORTEX SEARCH SERVICES"), "embedding_model"));
    }

    @Test
    public void embeddingModelIsKeptWhenNamed() {
        engine.execute("""
            CREATE CORTEX SEARCH SERVICE named
              ON body WAREHOUSE = fl_cortex_wh TARGET_LAG = '1 hour'
              EMBEDDING_MODEL = 'snowflake-arctic-embed-l-v2.0'
              COMMENT = 'a comment'
              AS (SELECT id, body FROM docs)
            """);
        final ResultSet rs = engine.executeQuery("SHOW CORTEX SEARCH SERVICES");
        assertEquals("snowflake-arctic-embed-l-v2.0", cell(rs, "embedding_model"));
        assertEquals("a comment", cell(rs, "comment"));
    }

    /** WAREHOUSE and TARGET_LAG are required, and the report names the missing one. */
    @Test
    public void warehouseIsRequired() {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("""
                    CREATE CORTEX SEARCH SERVICE s2 ON body TARGET_LAG = '1 hour'
                      AS (SELECT id, body FROM docs)
                    """);
            }
        });
        assertTrue(error.getMessage().contains("Missing option(s): [WAREHOUSE]"), error.getMessage());
    }

    @Test
    public void targetLagIsRequired() {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("""
                    CREATE CORTEX SEARCH SERVICE s3 ON body WAREHOUSE = fl_cortex_wh
                      AS (SELECT id, body FROM docs)
                    """);
            }
        });
        assertTrue(error.getMessage().contains("Missing option(s): [TARGET_LAG]"), error.getMessage());
    }

    /** The options are order-free: WAREHOUSE may follow TARGET_LAG. */
    @Test
    public void optionOrderIsFree() {
        engine.execute("""
            CREATE CORTEX SEARCH SERVICE swapped
              ON body TARGET_LAG = '2 hours' WAREHOUSE = fl_cortex_wh
              AS (SELECT id, body FROM docs)
            """);
        assertEquals("2 hours",
            cell(engine.executeQuery("SHOW CORTEX SEARCH SERVICES"), "target_lag"));
    }

    /** The parentheses around the defining query are optional. */
    @Test
    public void definingQueryNeedsNoParentheses() {
        engine.execute("""
            CREATE CORTEX SEARCH SERVICE bare
              ON body WAREHOUSE = fl_cortex_wh TARGET_LAG = '1 hour'
              AS SELECT id, body FROM docs
            """);
        assertEquals(1, engine.executeQuery("SHOW CORTEX SEARCH SERVICES").getRowCount());
    }

    /** A searched column the query does not project is an invalid identifier, as live reports it. */
    @Test
    public void searchColumnMustBeProjected() {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("""
                    CREATE CORTEX SEARCH SERVICE s4 ON nosuch WAREHOUSE = fl_cortex_wh TARGET_LAG = '1 hour'
                      AS (SELECT id, body FROM docs)
                    """);
            }
        });
        assertTrue(error.getMessage().contains("invalid identifier 'NOSUCH'"), error.getMessage());
    }

    @Test
    public void attributeColumnMustBeProjected() {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("""
                    CREATE CORTEX SEARCH SERVICE s5 ON body ATTRIBUTES nosuch
                      WAREHOUSE = fl_cortex_wh TARGET_LAG = '1 hour'
                      AS (SELECT id, body FROM docs)
                    """);
            }
        });
        assertTrue(error.getMessage().contains("invalid identifier 'NOSUCH'"), error.getMessage());
    }

    @Test
    public void describeReturnsTheOneServiceInTheShowShape() {
        engine.execute(CREATE_S1);
        final ResultSet rs = engine.executeQuery("DESCRIBE CORTEX SEARCH SERVICE s1");
        assertEquals(1, rs.getRowCount());
        assertEquals(20, rs.getColumnCount());
        assertEquals("S1", cell(rs, "name"));
    }

    @Test
    public void describeAcceptsTheShortSpelling() {
        engine.execute(CREATE_S1);
        assertEquals("S1", cell(engine.executeQuery("DESC CORTEX SEARCH SERVICE s1"), "name"));
    }

    /** A missing service is reported the way every other missing object is. */
    @Test
    public void describeMissingServiceReportsItFullyQualified() {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("DESCRIBE CORTEX SEARCH SERVICE nosuch");
            }
        });
        assertTrue(error.getMessage().contains(
            "Cortex Search Service 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not authorized."),
            error.getMessage());
    }

    @Test
    public void dropMissingServiceIsAnError() {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("DROP CORTEX SEARCH SERVICE nosuch");
            }
        });
        assertTrue(error.getMessage().contains(
            "Cortex Search Service 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not authorized."),
            error.getMessage());
    }

    /** DROP … IF EXISTS over a name that is not taken succeeds silently. */
    @Test
    public void dropIfExistsForgivesAMissingService() {
        engine.execute("DROP CORTEX SEARCH SERVICE IF EXISTS nosuch");
    }

    @Test
    public void dropRemovesTheService() {
        engine.execute(CREATE_S1);
        engine.execute("DROP CORTEX SEARCH SERVICE s1");
        assertEquals(0, engine.executeQuery("SHOW CORTEX SEARCH SERVICES").getRowCount());
    }

    @Test
    public void createOrReplaceRedefinesTheService() {
        engine.execute(CREATE_S1);
        engine.execute("""
            CREATE OR REPLACE CORTEX SEARCH SERVICE s1
              ON cat WAREHOUSE = fl_cortex_wh2 TARGET_LAG = '5 minutes'
              AS (SELECT id, cat FROM docs)
            """);
        final ResultSet rs = engine.executeQuery("SHOW CORTEX SEARCH SERVICES");
        assertEquals(1, rs.getRowCount());
        assertEquals("CAT", cell(rs, "search_column"));
        assertEquals("FL_CORTEX_WH2", cell(rs, "warehouse"));
    }

    @Test
    public void createIfNotExistsKeepsTheFirstDefinition() {
        engine.execute(CREATE_S1);
        engine.execute("""
            CREATE CORTEX SEARCH SERVICE IF NOT EXISTS s1
              ON cat WAREHOUSE = fl_cortex_wh2 TARGET_LAG = '5 minutes'
              AS (SELECT id, cat FROM docs)
            """);
        final ResultSet rs = engine.executeQuery("SHOW CORTEX SEARCH SERVICES");
        assertEquals(1, rs.getRowCount());
        assertEquals("BODY", cell(rs, "search_column"));
    }

    @Test
    public void alterSetsAndUnsetsTheComment() {
        engine.execute(CREATE_S1);
        engine.execute("ALTER CORTEX SEARCH SERVICE s1 SET COMMENT = 'now commented'");
        assertEquals("now commented",
            cell(engine.executeQuery("SHOW CORTEX SEARCH SERVICES"), "comment"));
        engine.execute("ALTER CORTEX SEARCH SERVICE s1 UNSET COMMENT");
        assertNull(cell(engine.executeQuery("SHOW CORTEX SEARCH SERVICES"), "comment"));
    }

    @Test
    public void alterMissingServiceIsAnError() {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER CORTEX SEARCH SERVICE nosuch SET COMMENT = 'x'");
            }
        });
        assertTrue(error.getMessage().contains("Cortex Search Service"), error.getMessage());
    }

    @Test
    public void alterIfExistsForgivesAMissingService() {
        engine.execute("ALTER CORTEX SEARCH SERVICE IF EXISTS nosuch SET COMMENT = 'x'");
    }

    @Test
    public void showFiltersByLike() {
        engine.execute(CREATE_S1);
        engine.execute("""
            CREATE CORTEX SEARCH SERVICE other ON body WAREHOUSE = fl_cortex_wh TARGET_LAG = '1 hour'
              AS (SELECT id, body FROM docs)
            """);
        assertEquals(2, engine.executeQuery("SHOW CORTEX SEARCH SERVICES").getRowCount());
        final ResultSet filtered = engine.executeQuery("SHOW CORTEX SEARCH SERVICES LIKE 'S%'");
        assertEquals(1, filtered.getRowCount());
        assertEquals("S1", cell(filtered, "name"));
    }

    @Test
    public void showScopesToASchema() {
        engine.execute(CREATE_S1);
        assertEquals(1, engine.executeQuery(
            "SHOW CORTEX SEARCH SERVICES IN SCHEMA test_db.test_schema").getRowCount());
    }

    @Test
    public void showScopesToADatabase() {
        engine.execute(CREATE_S1);
        assertEquals(1, engine.executeQuery(
            "SHOW CORTEX SEARCH SERVICES IN DATABASE test_db").getRowCount());
    }

    /** The keywords stay usable as ordinary identifiers. */
    @Test
    public void keywordsRemainIdentifiers() {
        engine.execute("CREATE TABLE cortex(search VARCHAR, service VARCHAR, attributes VARCHAR)");
        engine.execute("INSERT INTO cortex VALUES ('a', 'b', 'c')");
        final ResultSet rs = engine.executeQuery("SELECT search, service, attributes FROM cortex");
        assertEquals("a", rs.getRows().get(0).getValue(0).toString());
        assertEquals("b", rs.getRows().get(0).getValue(1).toString());
        assertEquals("c", rs.getRows().get(0).getValue(2).toString());
    }

    /** Without the AI pack on the classpath, the Cortex functions are unknown — as they are on an
     *  account without the feature. */
    @Test
    public void cortexFunctionsAreUnknownWithoutThePack() {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT SNOWFLAKE.CORTEX.SENTIMENT('x')");
            }
        });
        assertTrue(error.getMessage().contains("SNOWFLAKE.CORTEX.SENTIMENT"), error.getMessage());
    }
}
