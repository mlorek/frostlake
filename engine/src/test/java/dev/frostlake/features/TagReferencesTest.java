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
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;

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
 * INFORMATION_SCHEMA.TAG_REFERENCES(object_name, object_domain) — the tags on one object, read the
 * way a real account exposes them. Both arguments are required; a bare object name resolves against
 * the current database and schema; TABLE covers every table-like object (passing VIEW is refused);
 * COLUMN takes a four-part name whose last part is the column.
 */
public class TagReferencesTest extends BaseDatabaseTest {

    @BeforeEach
    public void createTaggedObjects() {
        engine.execute("CREATE TAG cost_center");
        engine.execute("CREATE TABLE tagged (k NUMBER, v VARCHAR)");
        engine.execute("ALTER TABLE tagged SET TAG cost_center = 'finance'");
        engine.execute("ALTER TABLE tagged MODIFY COLUMN v SET TAG cost_center = 'col-level'");
        engine.execute("CREATE VIEW tagged_view AS SELECT * FROM tagged");
        engine.execute("ALTER VIEW tagged_view SET TAG cost_center = 'view-level'");
    }

    private List<String> columnNames(final ResultSet rs) {
        final List<String> names = new ArrayList<>();
        for (final ResultSetColumn column : rs.getColumns()) {
            names.add(column.getName());
        }
        return names;
    }

    private Object cell(final ResultSet rs, final String columnName) {
        final int index = columnNames(rs).indexOf(columnName);
        assertTrue(index >= 0, "no such column: " + columnName);
        return rs.getRows().get(0).getValue(index);
    }

    private ResultSet references(final String objectName, final String domain) {
        return engine.executeQuery("SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('"
            + objectName + "', '" + domain + "'))");
    }

    @Test
    public void tagReferencesCarriesLiveColumnLayout() {
        final ResultSet rs = references("TAGGED", "TABLE");
        assertEquals(List.of(
            "TAG_DATABASE", "TAG_SCHEMA", "TAG_NAME", "TAG_VALUE", "LEVEL", "OBJECT_DATABASE",
            "OBJECT_SCHEMA", "OBJECT_NAME", "DOMAIN", "COLUMN_NAME", "APPLY_METHOD"),
            columnNames(rs));
    }

    @Test
    public void aTableTagIsReportedAtTheTableLevel() {
        final ResultSet rs = references("TEST_DB.TEST_SCHEMA.TAGGED", "TABLE");
        assertEquals(1, rs.getRows().size());
        assertEquals("COST_CENTER", cell(rs, "TAG_NAME"));
        assertEquals("finance", cell(rs, "TAG_VALUE"));
        assertEquals("TABLE", cell(rs, "LEVEL"));
        assertEquals("TABLE", cell(rs, "DOMAIN"));
        assertEquals("TAGGED", cell(rs, "OBJECT_NAME"));
        assertEquals("MANUAL", cell(rs, "APPLY_METHOD"));
        // A table-level tag names no column.
        assertNull(cell(rs, "COLUMN_NAME"));
    }

    /** A column tag is addressed by a four-part name and reports the column it sits on. */
    @Test
    public void aColumnTagIsReportedAtTheColumnLevel() {
        final ResultSet rs = references("TEST_DB.TEST_SCHEMA.TAGGED.V", "COLUMN");
        assertEquals(1, rs.getRows().size());
        assertEquals("col-level", cell(rs, "TAG_VALUE"));
        assertEquals("COLUMN", cell(rs, "LEVEL"));
        assertEquals("COLUMN", cell(rs, "DOMAIN"));
        assertEquals("TAGGED", cell(rs, "OBJECT_NAME"));
        assertEquals("V", cell(rs, "COLUMN_NAME"));
    }

    /** A view's tags come back under the TABLE domain — live has no separate VIEW domain. */
    @Test
    public void aViewsTagsAreReadUnderTheTableDomain() {
        final ResultSet rs = references("TAGGED_VIEW", "TABLE");
        assertEquals(1, rs.getRows().size());
        assertEquals("view-level", cell(rs, "TAG_VALUE"));
        assertEquals("TAGGED_VIEW", cell(rs, "OBJECT_NAME"));
        assertEquals("TABLE", cell(rs, "DOMAIN"));
    }

    /** A bare object name resolves against the session's current database and schema. */
    @Test
    public void aBareObjectNameResolvesAgainstTheCurrentSchema() {
        final ResultSet rs = references("TAGGED", "TABLE");
        assertEquals(1, rs.getRows().size());
        assertEquals("TEST_DB", cell(rs, "OBJECT_DATABASE"));
        assertEquals("TEST_SCHEMA", cell(rs, "OBJECT_SCHEMA"));
        assertEquals("finance", cell(rs, "TAG_VALUE"));
    }

    /** An untagged object is an empty result, not an error. */
    @Test
    public void anUntaggedObjectReturnsNoRows() {
        engine.execute("CREATE TABLE untagged (k NUMBER)");
        assertEquals(0, references("UNTAGGED", "TABLE").getRows().size());
    }

    @Test
    public void anUnknownObjectIsRejectedByItsWrittenName() {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                references("NO_SUCH_TABLE", "TABLE");
            }
        });
        assertTrue(error.getMessage().contains("Table 'NO_SUCH_TABLE' does not exist or not authorized."),
            "unexpected message: " + error.getMessage());
    }

    @Test
    public void anUnknownDomainIsRejected() {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                references("TAGGED", "BOGUS");
            }
        });
        assertTrue(error.getMessage().contains("Unknown domain: BOGUS."),
            "unexpected message: " + error.getMessage());
    }

    /** VIEW is not a domain: live directs the caller to TABLE for every table-like object. */
    @Test
    public void theViewDomainIsRefusedInFavourOfTable() {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                references("TAGGED_VIEW", "VIEW");
            }
        });
        assertTrue(error.getMessage().contains(
            "Please use object type TABLE for all kinds of table-like objects"),
            "unexpected message: " + error.getMessage());
    }

    /** MODIFY COLUMN is live's synonym for ALTER COLUMN, including when it sets a tag. */
    @Test
    public void modifyColumnIsAcceptedAsAlterColumn() {
        engine.execute("CREATE TABLE modify_probe (k NUMBER, v VARCHAR)");
        engine.execute("ALTER TABLE modify_probe MODIFY COLUMN v SET TAG cost_center = 'via-modify'");
        final ResultSet rs = references("TEST_DB.TEST_SCHEMA.MODIFY_PROBE.V", "COLUMN");
        assertEquals("via-modify", cell(rs, "TAG_VALUE"));

        engine.execute("ALTER TABLE modify_probe MODIFY COLUMN v UNSET TAG cost_center");
        assertEquals(0, references("TEST_DB.TEST_SCHEMA.MODIFY_PROBE.V", "COLUMN").getRows().size());
    }

    /** Several tags on one object come back as several rows. */
    @Test
    public void everyTagOnAnObjectIsOneRow() {
        engine.execute("CREATE TAG owner_team");
        engine.execute("CREATE TABLE multi_tagged (k NUMBER)");
        engine.execute("ALTER TABLE multi_tagged SET TAG cost_center = 'ops'");
        engine.execute("ALTER TABLE multi_tagged SET TAG owner_team = 'platform'");
        final ResultSet rs = references("MULTI_TAGGED", "TABLE");
        assertEquals(2, rs.getRows().size());
        final List<String> tagNames = new ArrayList<>();
        final int nameIndex = columnNames(rs).indexOf("TAG_NAME");
        for (final Row row : rs.getRows()) {
            tagNames.add(String.valueOf(row.getValue(nameIndex)));
        }
        assertTrue(tagNames.contains("COST_CENTER") && tagNames.contains("OWNER_TEAM"),
            "unexpected tags: " + tagNames);
    }

    /** A creation-time [WITH] TAG clause is recorded, not merely accepted. */
    @Test
    public void aCreationTimeTagClauseReachesTagReferences() {
        engine.execute("CREATE TABLE inline_tagged (k NUMBER) WITH TAG (cost_center = 'inline')");
        assertEquals("inline", cell(references("INLINE_TAGGED", "TABLE"), "TAG_VALUE"));

        // The clause is equally valid without the WITH.
        engine.execute("CREATE TABLE bare_tagged (k NUMBER) TAG (cost_center = 'bare')");
        assertEquals("bare", cell(references("BARE_TAGGED", "TABLE"), "TAG_VALUE"));
    }

    /** A column's own tag clause belongs to the column, not to the table it sits in. */
    @Test
    public void aColumnTagClauseDoesNotLeakOntoTheTable() {
        engine.execute("CREATE TABLE col_only (k NUMBER, v VARCHAR TAG (cost_center = 'col'))");
        assertEquals(0, references("COL_ONLY", "TABLE").getRows().size());
    }

    @Test
    public void aViewTakesACreationTimeTagClause() {
        engine.execute("CREATE TABLE view_tag_src (k NUMBER)");
        engine.execute("CREATE VIEW inline_view COMMENT = 'v'"
            + " TAG (cost_center = 'view-inline') AS SELECT * FROM view_tag_src");
        assertEquals("view-inline", cell(references("INLINE_VIEW", "TABLE"), "TAG_VALUE"));
    }

    /** Schemas, databases and warehouses are tag domains of their own. */
    @Test
    public void schemaTagsAreReadUnderTheSchemaDomain() {
        engine.execute("CREATE SCHEMA tagged_schema WITH TAG (cost_center = 'schema-level')");
        engine.execute("USE SCHEMA test_schema");
        final ResultSet rs = references("TEST_DB.TAGGED_SCHEMA", "SCHEMA");
        assertEquals("schema-level", cell(rs, "TAG_VALUE"));
        assertEquals("SCHEMA", cell(rs, "LEVEL"));
        assertEquals("TAGGED_SCHEMA", cell(rs, "OBJECT_NAME"));
        assertEquals("TEST_DB", cell(rs, "OBJECT_DATABASE"));
        // A schema has no containing schema, so live leaves that cell empty.
        assertNull(cell(rs, "OBJECT_SCHEMA"));
    }

    @Test
    public void databaseTagsAreReadUnderTheDatabaseDomain() {
        engine.execute("ALTER DATABASE test_db SET TAG cost_center = 'db-level'");
        final ResultSet rs = references("TEST_DB", "DATABASE");
        assertEquals("db-level", cell(rs, "TAG_VALUE"));
        assertEquals("DATABASE", cell(rs, "LEVEL"));
        assertEquals("TEST_DB", cell(rs, "OBJECT_NAME"));
        // A database sits at the account level, so neither container cell applies.
        assertNull(cell(rs, "OBJECT_DATABASE"));
        assertNull(cell(rs, "OBJECT_SCHEMA"));
    }

    @Test
    public void warehouseTagsAreReadUnderTheWarehouseDomain() {
        engine.execute("CREATE WAREHOUSE tagged_wh WAREHOUSE_SIZE = XSMALL");
        engine.execute("ALTER WAREHOUSE tagged_wh SET TAG cost_center = 'wh-level'");
        final ResultSet rs = references("TAGGED_WH", "WAREHOUSE");
        assertEquals("wh-level", cell(rs, "TAG_VALUE"));
        assertEquals("WAREHOUSE", cell(rs, "LEVEL"));
        assertEquals("TAGGED_WH", cell(rs, "OBJECT_NAME"));
        assertNull(cell(rs, "OBJECT_DATABASE"));
    }
}
