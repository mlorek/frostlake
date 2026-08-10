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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Applying object tags via {@code ALTER <object> SET TAG} / {@code UNSET TAG} across the
 * applicable object types (database, schema, table, column, view, warehouse), plus ALLOWED_VALUES
 * enforcement and the tag-must-exist requirement — asserted through the SQL surface,
 * {@code SYSTEM$GET_TAG}, so every check runs against whichever engine executed the DDL,
 * embedded or live.
 */
public class ObjectTaggingTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TAG cost_center");
        engine.execute("CREATE TAG sensitivity ALLOWED_VALUES 'PUBLIC', 'CONFIDENTIAL'");
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.execute("CREATE VIEW v AS SELECT id FROM t");
        engine.execute("CREATE WAREHOUSE IF NOT EXISTS wh WITH WAREHOUSE_SIZE = 'SMALL' "
            + "AUTO_SUSPEND = 60 INITIALLY_SUSPENDED = TRUE");
    }

    @Override
    protected void teardownTest() {
        engine.execute("DROP WAREHOUSE IF EXISTS wh");
    }

    /** One SYSTEM$GET_TAG read: the tag's value on the object, or null when unassigned. */
    private String tagOf(final String tag, final String object, final String domain) {
        final Object value = engine.executeQuery(
            "SELECT SYSTEM$GET_TAG('" + tag + "', '" + object + "', '" + domain + "')")
            .getRows().get(0).getValue(0);
        return value == null ? null : value.toString();
    }

    @Test
    public void testSetAndUnsetTagOnTable() {
        engine.execute("ALTER TABLE t SET TAG cost_center = 'engineering'");
        assertEquals("engineering", tagOf("cost_center", "t", "TABLE"));

        engine.execute("ALTER TABLE t UNSET TAG cost_center");
        assertNull(tagOf("cost_center", "t", "TABLE"));
    }

    @Test
    public void testSetAndUnsetTagOnColumn() {
        engine.execute("ALTER TABLE t ALTER COLUMN name SET TAG cost_center = 'pii'");
        assertEquals("pii", tagOf("cost_center", "t.name", "COLUMN"));

        engine.execute("ALTER TABLE t ALTER COLUMN name UNSET TAG cost_center");
        assertNull(tagOf("cost_center", "t.name", "COLUMN"));
    }

    @Test
    public void testSetTagOnView() {
        // A view is a table-like object: it answers under the TABLE domain (live-verified).
        engine.execute("ALTER VIEW v SET TAG cost_center = 'analytics'");
        assertEquals("analytics", tagOf("cost_center", "v", "TABLE"));
    }

    @Test
    public void testViewDomainIsRefused() {
        engine.execute("ALTER VIEW v SET TAG cost_center = 'analytics'");
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT SYSTEM$GET_TAG('cost_center', 'v', 'VIEW')");
            }
        });
        assertTrue(e.getMessage().contains("Invalid value VIEW for argument OBJECT_TYPE. "
            + "Please use object type TABLE for all kinds of table-like objects."), e.getMessage());
    }

    @Test
    public void testSetTagOnSchema() {
        engine.execute("ALTER SCHEMA test_schema SET TAG cost_center = 'shared'");
        assertEquals("shared", tagOf("cost_center", "test_schema", "SCHEMA"));
    }

    @Test
    public void testSetTagOnDatabase() {
        engine.execute("ALTER DATABASE test_db SET TAG cost_center = 'corp'");
        assertEquals("corp", tagOf("cost_center", "test_db", "DATABASE"));
    }

    @Test
    public void testSetTagOnWarehouse() {
        engine.execute("ALTER WAREHOUSE wh SET TAG cost_center = 'etl'");
        assertEquals("etl", tagOf("cost_center", "wh", "WAREHOUSE"));
    }

    @Test
    public void testMultipleTagsInOneStatement() {
        engine.execute("ALTER TABLE t SET TAG cost_center = 'eng', sensitivity = 'PUBLIC'");
        assertEquals("eng", tagOf("cost_center", "t", "TABLE"));
        assertEquals("PUBLIC", tagOf("sensitivity", "t", "TABLE"));
    }

    @Test
    public void testTagLookupIsCaseInsensitive() {
        engine.execute("ALTER TABLE t SET TAG cost_center = 'eng'");
        // Tag association is keyed by the canonical upper-cased tag name.
        assertEquals("eng", tagOf("COST_CENTER", "t", "TABLE"));
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

        assertEquals("engineering", tagOf("cost_center", "t", "TABLE"));
        assertEquals("pii", tagOf("cost_center", "t.name", "COLUMN"));
        // A tag not assigned to the object returns NULL.
        assertNull(tagOf("sensitivity", "t", "TABLE"));
    }

    /**
     * TAG_REFERENCES is a table FUNCTION on a real account, not a view — reading it as a relation
     * is "Object … does not exist or not authorized", and calling the function without its
     * object-name argument is "missing required argument [OBJECT_NAME]" (both measured). Assigned
     * tags are read back here through SYSTEM$GET_TAG, which this engine does implement.
     */
    @Test
    public void tagReferencesIsNotAViewAndTagsReadBackThroughGetTag() {
        engine.execute("ALTER TABLE t SET TAG cost_center = 'engineering'");
        engine.execute("ALTER TABLE t ALTER COLUMN name SET TAG cost_center = 'pii'");

        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM information_schema.tag_references");
            }
        });
        assertTrue(String.valueOf(error.getMessage()).contains("does not exist"),
            "expected a does-not-exist rejection, got: " + error.getMessage());

        assertEquals("engineering", tagOf("cost_center", "t", "TABLE"));
        assertEquals("pii", tagOf("cost_center", "t.name", "COLUMN"));
    }
}
