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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A hybrid table's listing has NINE columns of its own, not the table listing's twenty-seven. It
 * carries no kind, no clustering, no retention and none of the {@code is_*} flags — a hybrid table is
 * the only thing the listing answers, so it says nothing to tell one from another. Frostlake served
 * it from SHOW TABLES' layout, and refused {@code SHOW TERSE HYBRID TABLES} outright.
 */
public class ShowHybridTablesLayoutTest extends BaseDatabaseTest {

    /** Live's layout, column for column. */
    private static final String LAYOUT =
        "created_on,name,database_name,schema_name,owner,rows,bytes,comment,owner_role_type";

    /** The TERSE shape every object listing shares. */
    private static final String TERSE = "created_on,name,kind,database_name,schema_name";

    /** The listing's column names, joined. */
    private String layoutOf(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final ResultSetColumn col : rs.getColumns()) {
            if (out.length() > 0) {
                out.append(",");
            }
            out.append(col.getName());
        }
        return out.toString();
    }

    /** The listing answers its own nine columns, under every scope. */
    @Test
    public void theHybridListingHasItsOwnLayout() {
        assertEquals(LAYOUT, layoutOf("SHOW HYBRID TABLES"));
        assertEquals(LAYOUT, layoutOf("SHOW HYBRID TABLES IN ACCOUNT"));
        assertEquals(LAYOUT, layoutOf("SHOW HYBRID TABLES LIKE 'x%'"));
    }

    /** TERSE is accepted, and trims to the shape every listing shares. */
    @Test
    public void terseIsAcceptedAndTrims() {
        assertEquals(TERSE, layoutOf("SHOW TERSE HYBRID TABLES"));
    }

    /** SHOW ICEBERG TABLES keeps the twenty columns of its own. */
    @Test
    public void theIcebergListingIsUnchanged() {
        assertEquals("created_on,name,database_name,schema_name,owner,external_volume_name,"
            + "catalog_name,iceberg_table_type,catalog_table_name,catalog_namespace,base_location,"
            + "can_write_metadata,comment,owner_role_type,name_mapping,catalog_sync_name,"
            + "auto_refresh_status,partition_specs,current_partition_spec_id,"
            + "iceberg_table_format_version", layoutOf("SHOW ICEBERG TABLES"));
    }
}
