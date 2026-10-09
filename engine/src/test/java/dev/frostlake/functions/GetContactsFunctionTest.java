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
package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SNOWFLAKE.CORE.GET_CONTACTS(object_name, object_domain): the contacts attached to an object, a row per
 * purpose in the purposes' order, each with the contact's e-mail and URL and the domain it is attached at
 * (live-verified).
 */
public class GetContactsFunctionTest extends BaseDatabaseTest {

    private List<String> contacts(final String object, final String domain) {
        final ResultSet rows = engine.executeQuery("SELECT * FROM TABLE(SNOWFLAKE.CORE.GET_CONTACTS('" + object
            + "', '" + domain + "'))");
        final List<String> told = new ArrayList<>();
        for (final Row row : rows.getRows()) {
            told.add(row.getValue(0) + "|" + row.getValue(1) + "|" + row.getValue(2) + "|" + row.getValue(3) + "|"
                + row.getValue(4));
        }
        return told;
    }

    @Test
    public void aTablesContactsAreToldByPurpose() {
        engine.execute("CREATE CONTACT gc_mail EMAIL_DISTRIBUTION_LIST = 'a@example.com'");
        engine.execute("CREATE CONTACT gc_url URL = 'https://example.com/help'");
        engine.execute("CREATE TABLE gc_t (a INT)");
        assertTrue(contacts("gc_t", "TABLE").isEmpty(), "an object without contacts answers no rows");
        engine.execute("ALTER TABLE gc_t SET CONTACT SUPPORT = gc_mail, ACCESS_APPROVAL = gc_url");
        assertEquals("[ACCESS_APPROVAL|null|https://example.com/help|null|TABLE,"
            + " SUPPORT|a@example.com|null|null|TABLE]", contacts("gc_t", "TABLE").toString());
        assertEquals("[ACCESS_APPROVAL|null|https://example.com/help|null|TABLE,"
            + " SUPPORT|a@example.com|null|null|TABLE]", contacts("test_db.test_schema.gc_t", "table").toString(),
            "by a full name, the domain in either case");
        engine.execute("ALTER TABLE gc_t UNSET CONTACT SUPPORT");
        assertEquals("[ACCESS_APPROVAL|null|https://example.com/help|null|TABLE]", contacts("gc_t", "TABLE").toString());
    }

    @Test
    public void theColumnsAreTheAccounts() {
        engine.execute("CREATE TABLE gc_t (a INT)");
        final List<String> names = new ArrayList<>();
        for (final ResultSetColumn column : engine.executeQuery("SELECT * FROM"
                + " TABLE(SNOWFLAKE.CORE.GET_CONTACTS('gc_t', 'TABLE'))").getColumns()) {
            names.add(column.getName());
        }
        assertEquals("[PURPOSE, EMAIL, URL, USER, LEVEL]", names.toString());
    }
}
