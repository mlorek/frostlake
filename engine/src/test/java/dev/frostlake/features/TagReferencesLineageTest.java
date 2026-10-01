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
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TAG_REFERENCES over the securable object hierarchy: an object reports the tags set on it (MANUAL) and those it
 * inherits from its table, schema and database (INHERITED, LEVEL naming where the tag is set), the nearest
 * assignment of a tag winning; the object name is read as identifiers are, quoted parts kept as written.
 */
public class TagReferencesLineageTest extends BaseDatabaseTest {

    private List<List<Object>> rows(final String objectName, final String domain) {
        final ResultSet rs = engine.executeQuery("SELECT TAG_NAME, TAG_VALUE, LEVEL, APPLY_METHOD FROM TABLE("
            + "INFORMATION_SCHEMA.TAG_REFERENCES('" + objectName + "', '" + domain + "')) ORDER BY TAG_NAME");
        final List<List<Object>> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            out.add(row.getValues());
        }
        return out;
    }

    private void tagTheHierarchy() {
        engine.executeQuery("CREATE TAG cost");
        engine.executeQuery("CREATE TAG team");
        engine.executeQuery("CREATE TAG tier");
        engine.executeQuery("CREATE TABLE lt (k INT, v VARCHAR)");
        engine.executeQuery("ALTER DATABASE test_db SET TAG cost = 'db', team = 'db-team'");
        engine.executeQuery("ALTER SCHEMA test_db.test_schema SET TAG cost = 'schema'");
        engine.executeQuery("ALTER TABLE lt SET TAG tier = 'gold'");
        engine.executeQuery("ALTER TABLE lt MODIFY COLUMN v SET TAG cost = 'column'");
    }

    @Test
    public void aTableInheritsFromItsSchemaAndDatabase() {
        tagTheHierarchy();
        assertEquals(Arrays.asList(
            Arrays.<Object>asList("COST", "schema", "SCHEMA", "INHERITED"),
            Arrays.<Object>asList("TEAM", "db-team", "DATABASE", "INHERITED"),
            Arrays.<Object>asList("TIER", "gold", "TABLE", "MANUAL")), rows("lt", "TABLE"));
    }

    @Test
    public void aColumnInheritsFromItsTableAndTheNearestAssignmentWins() {
        tagTheHierarchy();
        assertEquals(Arrays.asList(
            Arrays.<Object>asList("COST", "column", "COLUMN", "MANUAL"),
            Arrays.<Object>asList("TEAM", "db-team", "DATABASE", "INHERITED"),
            Arrays.<Object>asList("TIER", "gold", "TABLE", "INHERITED")), rows("lt.v", "COLUMN"));
    }

    @Test
    public void aSchemaInheritsFromItsDatabase() {
        tagTheHierarchy();
        assertEquals(Arrays.asList(
            Arrays.<Object>asList("COST", "schema", "SCHEMA", "MANUAL"),
            Arrays.<Object>asList("TEAM", "db-team", "DATABASE", "INHERITED")), rows("test_db.test_schema", "SCHEMA"));
        assertEquals(Arrays.asList(
            Arrays.<Object>asList("COST", "db", "DATABASE", "MANUAL"),
            Arrays.<Object>asList("TEAM", "db-team", "DATABASE", "MANUAL")), rows("test_db", "DATABASE"));
    }

    @Test
    public void aQuotedNameInsideTheStringNamesTheObjectAsWritten() {
        engine.executeQuery("CREATE TAG cost");
        engine.executeQuery("CREATE WAREHOUSE \"MyWh\"");
        engine.executeQuery("ALTER WAREHOUSE \"MyWh\" SET TAG cost = 'wh'");
        assertEquals(Arrays.asList(Arrays.<Object>asList("COST", "wh", "WAREHOUSE", "MANUAL")),
            rows("\"MyWh\"", "WAREHOUSE"));
        engine.executeQuery("CREATE TABLE \"lower t\" (k INT)");
        engine.executeQuery("ALTER TABLE \"lower t\" SET TAG cost = 'lt'");
        assertEquals(Arrays.asList(Arrays.<Object>asList("COST", "lt", "TABLE", "MANUAL")),
            rows("test_db.test_schema.\"lower t\"", "TABLE"));
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                rows("\"NoSuchWh\"", "WAREHOUSE");
            }
        });
        assertTrue(refused.getMessage().contains("Warehouse '\"NoSuchWh\"' does not exist or not authorized."),
            refused.getMessage());
    }

    @Test
    public void theTagIsReportedWhereItIsDefined() {
        engine.executeQuery("CREATE SCHEMA tags_home");
        engine.executeQuery("CREATE TAG test_db.tags_home.cost");
        engine.executeQuery("USE SCHEMA test_db.test_schema");
        engine.executeQuery("CREATE TABLE lt2 (k INT)");
        engine.executeQuery("ALTER TABLE lt2 SET TAG test_db.tags_home.cost = 'x'");
        final ResultSet rs = engine.executeQuery("SELECT TAG_DATABASE, TAG_SCHEMA, OBJECT_SCHEMA FROM TABLE("
            + "INFORMATION_SCHEMA.TAG_REFERENCES('test_db.test_schema.lt2', 'TABLE'))");
        assertEquals(Arrays.<Object>asList("TEST_DB", "TAGS_HOME", "TEST_SCHEMA"), rs.getRows().get(0).getValues());
    }

    @Test
    public void everyDocumentedDomainIsAccepted() {
        assertEquals(0, rows("anything", "SHARE").size());
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                rows("anything", "BOGUS");
            }
        });
        assertTrue(refused.getMessage().contains("Unknown domain: BOGUS."), refused.getMessage());
    }
}
