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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests applying object tags via {@code ALTER <object> SET TAG} / {@code UNSET TAG} across the
 * applicable object types (database, schema, table, column, view, warehouse), plus ALLOWED_VALUES
 * enforcement and the tag-must-exist requirement.
 */
public class ObjectTaggingTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
        engine.execute("CREATE TAG cost_center");
        engine.execute("CREATE TAG sensitivity ALLOWED_VALUES 'PUBLIC', 'CONFIDENTIAL'");
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.execute("CREATE VIEW v AS SELECT id FROM t");
        engine.execute("CREATE WAREHOUSE wh WITH WAREHOUSE_SIZE = 'SMALL'");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private Schema schema() {
        return engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
    }

    @Test
    public void testSetAndUnsetTagOnTable() {
        engine.execute("ALTER TABLE t SET TAG cost_center = 'engineering'");
        assertEquals("engineering", schema().getTable("T").getTagValue("cost_center"));

        engine.execute("ALTER TABLE t UNSET TAG cost_center");
        assertNull(schema().getTable("T").getTagValue("cost_center"));
    }

    @Test
    public void testSetAndUnsetTagOnColumn() {
        engine.execute("ALTER TABLE t ALTER COLUMN name SET TAG cost_center = 'pii'");
        assertEquals("pii", schema().getTable("T").getColumn("name").getTagValue("cost_center"));

        engine.execute("ALTER TABLE t ALTER COLUMN name UNSET TAG cost_center");
        assertNull(schema().getTable("T").getColumn("name").getTagValue("cost_center"));
    }

    @Test
    public void testSetTagOnView() {
        engine.execute("ALTER VIEW v SET TAG cost_center = 'analytics'");
        assertEquals("analytics", schema().getView("V").getTagValue("cost_center"));
    }

    @Test
    public void testSetTagOnSchema() {
        engine.execute("ALTER SCHEMA public SET TAG cost_center = 'shared'");
        assertEquals("shared", schema().getTagValue("cost_center"));
    }

    @Test
    public void testSetTagOnDatabase() {
        engine.execute("ALTER DATABASE test_db SET TAG cost_center = 'corp'");
        assertEquals("corp", engine.getCatalog().getDatabase("TEST_DB").getTagValue("cost_center"));
    }

    @Test
    public void testSetTagOnWarehouse() {
        engine.execute("ALTER WAREHOUSE wh SET TAG cost_center = 'etl'");
        assertEquals("etl", engine.getCatalog().getWarehouse("WH").getTagValue("cost_center"));
    }

    @Test
    public void testMultipleTagsInOneStatement() {
        engine.execute("ALTER TABLE t SET TAG cost_center = 'eng', sensitivity = 'PUBLIC'");
        assertEquals("eng", schema().getTable("T").getTagValue("cost_center"));
        assertEquals("PUBLIC", schema().getTable("T").getTagValue("sensitivity"));
    }

    @Test
    public void testTagLookupIsCaseInsensitive() {
        engine.execute("ALTER TABLE t SET TAG cost_center = 'eng'");
        // Tag association is keyed by the canonical upper-cased tag name.
        assertEquals("eng", schema().getTable("T").getTagValue("COST_CENTER"));
    }

    @Test
    public void testAllowedValuesEnforced() {
        // 'SECRET' is not in the tag's ALLOWED_VALUES ('PUBLIC','CONFIDENTIAL').
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE t SET TAG sensitivity = 'SECRET'");
            }
        });
    }

    @Test
    public void testSetUndefinedTagFails() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE t SET TAG no_such_tag = 'x'");
            }
        });
    }

    @Test
    public void testSystemGetTagReadsValue() {
        engine.execute("ALTER TABLE t SET TAG cost_center = 'engineering'");
        engine.execute("ALTER TABLE t ALTER COLUMN name SET TAG cost_center = 'pii'");

        assertEquals("engineering",
            engine.executeQuery("SELECT SYSTEM$GET_TAG('cost_center', 't', 'TABLE')").getRows().get(0).getValue(0));
        assertEquals("pii",
            engine.executeQuery("SELECT SYSTEM$GET_TAG('cost_center', 't.name', 'COLUMN')").getRows().get(0).getValue(0));
        // A tag not assigned to the object returns NULL.
        assertNull(
            engine.executeQuery("SELECT SYSTEM$GET_TAG('sensitivity', 't', 'TABLE')").getRows().get(0).getValue(0));
    }

    @Test
    public void testTagReferencesView() {
        engine.execute("ALTER TABLE t SET TAG cost_center = 'engineering'");
        engine.execute("ALTER TABLE t ALTER COLUMN name SET TAG cost_center = 'pii'");

        ResultSet rs = engine.executeQuery("SELECT * FROM information_schema.tag_references");
        boolean foundTable = false;
        boolean foundColumn = false;
        while (rs.next()) {
            String domain = (String) rs.getValue("DOMAIN");
            String objName = (String) rs.getValue("OBJECT_NAME");
            String tagName = (String) rs.getValue("TAG_NAME");
            String tagValue = (String) rs.getValue("TAG_VALUE");
            String colName = (String) rs.getValue("COLUMN_NAME");
            if ("TABLE".equals(domain) && "T".equalsIgnoreCase(objName) && "COST_CENTER".equals(tagName)) {
                assertEquals("engineering", tagValue);
                foundTable = true;
            }
            if ("COLUMN".equals(domain) && "NAME".equalsIgnoreCase(colName) && "COST_CENTER".equals(tagName)) {
                assertEquals("pii", tagValue);
                foundColumn = true;
            }
        }
        assertTrue(foundTable, "TAG_REFERENCES should list the table-level tag");
        assertTrue(foundColumn, "TAG_REFERENCES should list the column-level tag");
    }
}
