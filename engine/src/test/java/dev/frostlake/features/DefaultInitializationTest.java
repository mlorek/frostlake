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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Default database and schema semantics, asserted through the SQL surface — the SNOWFLAKE
 * database is always visible, every new database is born with PUBLIC and INFORMATION_SCHEMA,
 * PUBLIC is droppable like any schema while INFORMATION_SCHEMA is protected — so every check runs
 * against whichever engine executed the statements, embedded or live. The fresh-engine bootstrap
 * state (which database and schema a brand-new engine starts in) lives in
 * {@code EngineBootstrapDefaultsTest}.
 */
public class DefaultInitializationTest extends BaseDatabaseTest {

    @Test
    public void testDefaultSnowflakeDatabaseExists() {
        final ResultSet databases = engine.executeQuery("SHOW DATABASES LIKE 'SNOWFLAKE'");
        soleRowWhere(databases, "name", "SNOWFLAKE");
    }

    @Test
    public void testNewDatabaseGetsDefaultSchemas() {
        // test_db was created fresh for this test: it must carry both default schemas.
        final ResultSet schemas = engine.executeQuery("SHOW SCHEMAS IN DATABASE test_db");
        soleRowWhere(schemas, "name", "PUBLIC");
        soleRowWhere(schemas, "name", "INFORMATION_SCHEMA");
    }

    @Test
    public void testShowSchemasIncludesBothDefaultSchemas() {
        final ResultSet schemas = engine.executeQuery("SHOW SCHEMAS");
        soleRowWhere(schemas, "name", "PUBLIC");
        soleRowWhere(schemas, "name", "INFORMATION_SCHEMA");
    }

    @Test
    public void testCanDropPublicSchema() {
        // PUBLIC is droppable like any other schema.
        engine.execute("DROP SCHEMA test_db.PUBLIC");

        assertEquals(0, engine.executeQuery("SHOW SCHEMAS LIKE 'PUBLIC'").getRowCount());
    }

    @Test
    public void testCannotDropInformationSchema() {
        final RuntimeException exception = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("DROP SCHEMA test_db.INFORMATION_SCHEMA");
            }
        });
        assertTrue(exception.getMessage().contains("INFORMATION_SCHEMA"), exception.getMessage());
    }

    @Test
    public void testInformationSchemaHasSystemViews() {
        final ResultSet views = engine.executeQuery("SHOW VIEWS IN SCHEMA test_db.INFORMATION_SCHEMA");

        // Should have at least DATABASES, SCHEMATA, TABLES, COLUMNS, VIEWS
        assertTrue(views.getRowCount() >= 5,
            "INFORMATION_SCHEMA should have at least 5 system views");
    }

    @Test
    public void testCannotDropInformationSchemaViews() {
        // INFORMATION_SCHEMA is read-only: dropping one of its views refuses with the
        // access-control wording — under DROP VIEW and DROP TABLE alike, since the view is
        // found before any kind check.
        final RuntimeException viaDropView = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("DROP VIEW test_db.information_schema.tables");
            }
        });
        assertTrue(viaDropView.getMessage().contains(
            "Insufficient privileges to operate on view 'TABLES'"), viaDropView.getMessage());

        final RuntimeException viaDropTable = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("DROP TABLE test_db.information_schema.tables");
            }
        });
        assertTrue(viaDropTable.getMessage().contains(
            "Insufficient privileges to operate on view 'TABLES'"), viaDropTable.getMessage());

        // IF EXISTS forgives absence, never this refusal.
        final RuntimeException underIfExists = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("DROP VIEW IF EXISTS test_db.information_schema.tables");
            }
        });
        assertTrue(underIfExists.getMessage().contains("Insufficient privileges"), underIfExists.getMessage());

        // The view is untouched.
        soleRowWhere(engine.executeQuery("SHOW VIEWS IN SCHEMA test_db.INFORMATION_SCHEMA"),
            "name", "TABLES");
    }

    @Test
    public void testUserObjectsNamedLikeSystemViewsStayDroppable() {
        // Protection is keyed on the schema, not the name: a user view or table that merely
        // shares a system view's name creates and drops freely.
        engine.execute("CREATE VIEW tables AS SELECT 1 AS c");
        engine.execute("DROP VIEW tables");
        assertEquals(0, engine.executeQuery("SHOW VIEWS LIKE 'tables'").getRowCount());

        engine.execute("CREATE TABLE columns (id INTEGER)");
        engine.execute("DROP TABLE columns");
        assertEquals(0, engine.executeQuery("SHOW TABLES LIKE 'columns'").getRowCount());
    }
}
