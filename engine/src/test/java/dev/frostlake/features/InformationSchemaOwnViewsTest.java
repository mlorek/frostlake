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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * INFORMATION_SCHEMA's own views are listed as a real account lists them: 63 of them, APPLICATION_SERVICES
 * among them and queryable with its column shape and no rows. None was ever created, so SHOW VIEWS and SHOW
 * OBJECTS give each the epoch as its creation time, no owner and the account's fixed description as its
 * comment, SHOW VIEWS gives it no text, and INFORMATION_SCHEMA.TABLES and VIEWS give it no times and no
 * definition. Every cell is live-verified.
 */
public class InformationSchemaOwnViewsTest extends BaseDatabaseTest {

    private static final String TABLES_DESCRIPTION =
        "The tables defined in this database that are accessible to the current user's role.";

    private static final String SERVICES_DESCRIPTION =
        "The application services in this database that are accessible to the current user's role.";

    /** The first row's cells, a comma between them. */
    private String firstRow(final String sql) {
        final Row row = engine.executeQuery(sql).getRows().get(0);
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < row.getValues().size(); i++) {
            out.append(i > 0 ? ", " : "").append(row.getValue(i));
        }
        return out.toString();
    }

    @Test
    public void showViewsListsEachAtTheEpochWithItsDescriptionAndNoText() {
        engine.executeQuery("SHOW VIEWS LIKE 'TABLES' IN SCHEMA test_db.INFORMATION_SCHEMA");
        assertEquals("0, TABLES, , " + TABLES_DESCRIPTION + ", , false",
            firstRow("SELECT DATE_PART(EPOCH_SECOND, \"created_on\"), \"name\", \"owner\", \"comment\", \"text\", "
                + "\"is_secure\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
        engine.executeQuery("SHOW VIEWS LIKE 'APPLICATION_SERVICES' IN SCHEMA test_db.INFORMATION_SCHEMA");
        assertEquals("0, APPLICATION_SERVICES, " + SERVICES_DESCRIPTION + ", ",
            firstRow("SELECT DATE_PART(EPOCH_SECOND, \"created_on\"), \"name\", \"comment\", \"text\" "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
    }

    @Test
    public void showObjectsListsEachAtTheEpochWithItsDescription() {
        engine.executeQuery("SHOW OBJECTS LIKE 'TABLES' IN SCHEMA test_db.INFORMATION_SCHEMA");
        assertEquals("0, TABLES, VIEW, " + TABLES_DESCRIPTION + ", ",
            firstRow("SELECT DATE_PART(EPOCH_SECOND, \"created_on\"), \"name\", \"kind\", \"comment\", \"owner\" "
                + "FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
        engine.executeQuery("SHOW TERSE VIEWS IN SCHEMA test_db.INFORMATION_SCHEMA");
        assertEquals("63, 0, 0",
            firstRow("SELECT COUNT(*), MIN(DATE_PART(EPOCH_SECOND, \"created_on\")), "
                + "MAX(DATE_PART(EPOCH_SECOND, \"created_on\")) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
    }

    @Test
    public void theCatalogDescribesItsOwnViewsWithoutTimesOrDefinition() {
        assertEquals("VIEW, null, null, null, null, " + TABLES_DESCRIPTION,
            firstRow("SELECT TABLE_TYPE, TABLE_OWNER, CREATED, LAST_ALTERED, LAST_DDL, COMMENT "
                + "FROM test_db.INFORMATION_SCHEMA.TABLES "
                + "WHERE TABLE_SCHEMA = 'INFORMATION_SCHEMA' AND TABLE_NAME = 'TABLES'"));
        assertEquals("null, null, null, null, NONE, " + TABLES_DESCRIPTION,
            firstRow("SELECT VIEW_DEFINITION, CREATED, LAST_ALTERED, LAST_DDL, CHECK_OPTION, COMMENT "
                + "FROM test_db.INFORMATION_SCHEMA.VIEWS "
                + "WHERE TABLE_SCHEMA = 'INFORMATION_SCHEMA' AND TABLE_NAME = 'TABLES'"));
        assertEquals("63",
            firstRow("SELECT COUNT(*) FROM test_db.INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'INFORMATION_SCHEMA'"));
    }

    @Test
    public void applicationServicesIsQueryableWithItsColumnsAndNoRows() {
        final ResultSet services = engine.executeQuery("SELECT * FROM test_db.INFORMATION_SCHEMA.APPLICATION_SERVICES");
        final List<String> names = new ArrayList<>();
        for (final ResultSetColumn column : services.getColumns()) {
            names.add(column.getName().toUpperCase());
        }
        assertEquals(Arrays.asList(
            "APPLICATION_SERVICE_CATALOG", "APPLICATION_SERVICE_SCHEMA", "APPLICATION_SERVICE_NAME", "STATUS", "URL",
            "PRIVATELINK_URL", "SOURCE", "APPLICATION_SERVICE_OWNER", "APPLICATION_SERVICE_OWNER_ROLE_TYPE",
            "CREATED_BY", "COMMENT", "ADDITIONAL_PROPERTIES", "CREATED", "LAST_ALTERED", "LAST_RESUMED",
            "LAST_SUSPENDED", "IS_UPGRADING", "AUTO_RESUME", "AUTO_SUSPEND_SECS", "QUERY_WAREHOUSE",
            "COMPUTE_RESOURCE", "MIN_INSTANCES", "MAX_INSTANCES", "CURRENT_INSTANCES",
            "EXTERNAL_ACCESS_INTEGRATIONS", "URL_PREFIX"), names);
        assertEquals(0, services.getRowCount());
    }
}
