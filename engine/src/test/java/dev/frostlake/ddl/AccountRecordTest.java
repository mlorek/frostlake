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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CREATE, DROP and UNDROP ACCOUNT, SHOW ACCOUNTS [HISTORY] [LIKE], and CREATE, DROP and SHOW MANAGED ACCOUNT(S):
 * the records of the organization's other accounts and of this account's reader accounts.
 */
public class AccountRecordTest extends BaseDatabaseTest {

    /** Each row of a listing as its chosen cells joined with {@code |}. */
    private List<String> rows(final String sql, final String... columns) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            final StringBuilder line = new StringBuilder();
            for (final String column : columns) {
                if (line.length() > 0) {
                    line.append('|');
                }
                line.append(row.getValue(rs.getColumnIndex(column)));
            }
            out.add(line.toString());
        }
        return out;
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private String status(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }
    @Test
    public void anAccountIsCreatedListedDroppedAndRestored() {
        final int before = rows("SHOW ACCOUNTS", "account_name").size();
        assertEquals("Account ACC_ONE successfully created.", status("""
            CREATE ACCOUNT acc_one ADMIN_NAME = admin ADMIN_PASSWORD = 'TestPassword1' FIRST_NAME = 'Jane'
              EMAIL = 'jane@example.com' EDITION = ENTERPRISE REGION = AWS_US_WEST_2 COMMENT = 'first'"""));
        assertEquals(List.of("ACC_ONE|ENTERPRISE|AWS_US_WEST_2|first|false"),
            rows("SHOW ACCOUNTS LIKE 'acc_%'", "account_name", "edition", "snowflake_region", "comment",
                "is_org_admin"));
        assertEquals(before + 1, rows("SHOW ACCOUNTS", "account_name").size());
        assertFalse(engine.executeQuery("SHOW ACCOUNTS").getColumns().toString().contains("dropped_on"));
        assertTrue(refusal("""
            CREATE ACCOUNT acc_one ADMIN_NAME = a ADMIN_PASSWORD = 'p' EMAIL = 'e' EDITION = STANDARD""")
            .contains("already exists"));

        assertEquals("ACC_ONE successfully dropped.", status("DROP ACCOUNT acc_one GRACE_PERIOD_IN_DAYS = 3"));
        assertEquals(List.of(), rows("SHOW ACCOUNTS LIKE 'ACC_ONE'", "account_name"));
        final List<String> history = rows("SHOW ACCOUNTS HISTORY LIKE 'ACC_ONE'", "account_name", "restored_on");
        assertEquals(List.of("ACC_ONE|null"), history);
        assertTrue(rows("SHOW ACCOUNTS HISTORY LIKE 'ACC_ONE'", "dropped_on").get(0).startsWith("20"));
        assertTrue(refusal("DROP ACCOUNT acc_one GRACE_PERIOD_IN_DAYS = 3").contains("does not exist"));
        assertEquals("Drop statement executed successfully (ACC_ONE already dropped).",
            status("DROP ACCOUNT IF EXISTS acc_one GRACE_PERIOD_IN_DAYS = 3"));

        engine.executeQuery("UNDROP ACCOUNT acc_one");
        assertEquals(List.of("ACC_ONE"), rows("SHOW ACCOUNTS LIKE 'ACC_ONE'", "account_name"));
        assertTrue(refusal("UNDROP ACCOUNT acc_one").contains("already exists"));
        assertTrue(refusal("UNDROP ACCOUNT no_such_account").contains("does not exist"));
    }

    @Test
    public void createAccountRequiresItsOptionsAndAValidEdition() {
        assertEquals("SQL compilation error:\nMissing option(s): [ADMIN_PASSWORD, EDITION].",
            refusal("CREATE ACCOUNT acc_two ADMIN_NAME = admin EMAIL = 'e@x.io'"));
        assertTrue(refusal("""
            CREATE ACCOUNT acc_two ADMIN_NAME = a ADMIN_PASSWORD = 'p' EMAIL = 'e' EDITION = PLATINUM""")
            .contains("EDITION"));
        engine.executeQuery("""
            CREATE ACCOUNT acc_two ADMIN_NAME = a ADMIN_RSA_PUBLIC_KEY = 'MIIB' EMAIL = 'e' EDITION = STANDARD""");
        assertTrue(refusal("DROP ACCOUNT acc_two GRACE_PERIOD_IN_DAYS = 2").contains("GRACE_PERIOD_IN_DAYS"));
        assertTrue(refusal("DROP ACCOUNT acc_two GRACE_PERIOD_IN_DAYS = 91").contains("GRACE_PERIOD_IN_DAYS"));
        assertTrue(refusal("DROP ACCOUNT acc_two").contains("syntax error"), "the grace period is required");
    }

    @Test
    public void aManagedAccountIsCreatedListedAndDropped() {
        final String created = status("""
            CREATE MANAGED ACCOUNT reader_one ADMIN_NAME = admin, ADMIN_PASSWORD = 'Sdfed43da!44', TYPE = READER,
              COMMENT = 'for sharing'""");
        assertTrue(created.startsWith("{\"accountName\":\"READER_ONE\",\"accountLocator\":\""), created);
        assertTrue(created.contains("\"accountLocatorUrl\":\"https://"), created);
        assertEquals(List.of("READER_ONE|for sharing|true"),
            rows("SHOW MANAGED ACCOUNTS", "account_name", "comment", "is_reader"));
        assertEquals(List.of("READER_ONE"), rows("SHOW MANAGED ACCOUNTS LIKE 'reader%'", "account_name"));
        assertEquals(List.of(), rows("SHOW MANAGED ACCOUNTS LIKE 'x%'", "account_name"));
        assertTrue(refusal("CREATE MANAGED ACCOUNT reader_one ADMIN_NAME = a, ADMIN_PASSWORD = 'p', TYPE = READER")
            .contains("already exists"));
        assertTrue(refusal("CREATE MANAGED ACCOUNT reader_two ADMIN_NAME = a, TYPE = READER")
            .contains("ADMIN_PASSWORD"));
        assertEquals("READER_ONE successfully dropped.", status("DROP MANAGED ACCOUNT reader_one"));
        assertTrue(refusal("DROP MANAGED ACCOUNT reader_one").contains("does not exist"));
    }
}
