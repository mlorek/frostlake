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

package dev.frostlake.security;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A FUTURE grant is a standing instruction, not a record. Frostlake accepted one, listed it and
 * revoked it, but never applied it: a table created after {@code GRANT OWNERSHIP ON FUTURE TABLES IN
 * SCHEMA s TO ROLE r} kept the CREATING role as its owner.
 *
 * <p>What fires, and in whose name, is measured:
 *
 * <pre>
 *   OWNERSHIP     names the new object's owner
 *   any other     is granted to the holder, recorded as granted_by the NEW OWNER — not the creator
 *   two scopes    the narrower wins WHOLE: a schema grant shuts the database one out for that kind,
 *                 and the database one still covers a sibling schema the schema grant cannot reach
 * </pre>
 */
public class FutureGrantApplicationTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("USE ROLE ACCOUNTADMIN");
        engine.execute("CREATE OR REPLACE ROLE fga");
        engine.execute("CREATE OR REPLACE ROLE fgb");
        engine.execute("CREATE OR REPLACE SCHEMA fs1");
        engine.execute("CREATE OR REPLACE SCHEMA fs2");
    }

    /** One cell of the first row a listing answers. */
    private String cellOf(final String sql, final String column) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(column));
    }

    /** Every row of a listing, as "c1, c2 | c1, c2". */
    private String rowsOf(final String sql, final String... columns) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        while (rs.next()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int c = 0; c < columns.length; c++) {
                if (c > 0) {
                    out.append(", ");
                }
                out.append(String.valueOf(rs.getValue(columns[c])));
            }
        }
        return out.toString();
    }

    /** A future OWNERSHIP grant owns what is created afterwards, whatever role creates it. */
    @Test
    public void aFutureOwnershipGrantOwnsWhatComesAfter() {
        engine.execute("GRANT OWNERSHIP ON FUTURE TABLES IN SCHEMA fs1 TO ROLE fga");
        engine.execute("CREATE TABLE fs1.o1 (a INT)");
        assertEquals("FGA", cellOf("SHOW TABLES LIKE 'o1' IN SCHEMA fs1", "owner"));
        assertEquals("OWNERSHIP, FGA, FGA",
            rowsOf("SHOW GRANTS ON TABLE fs1.o1", "privilege", "grantee_name", "granted_by"));

        // A table created BEFORE the grant keeps the owner it had.
        engine.execute("CREATE TABLE fs2.o0 (a INT)");
        assertEquals("ACCOUNTADMIN", cellOf("SHOW TABLES LIKE 'o0' IN SCHEMA fs2", "owner"));
    }

    /** A CTAS and a view take their kind's grant too; each kind answers only its own. */
    @Test
    public void everyKindAnswersItsOwnGrant() {
        engine.execute("GRANT OWNERSHIP ON FUTURE TABLES IN SCHEMA fs1 TO ROLE fga");
        engine.execute("CREATE TABLE fs1.k1 AS SELECT 1 AS a");
        assertEquals("FGA", cellOf("SHOW TABLES LIKE 'k1' IN SCHEMA fs1", "owner"));
        engine.execute("CREATE VIEW fs1.kv AS SELECT 1 AS a");
        assertEquals("ACCOUNTADMIN", cellOf("SHOW VIEWS LIKE 'kv' IN SCHEMA fs1", "owner"),
            "the TABLES grant does not reach a view");

        engine.execute("GRANT OWNERSHIP ON FUTURE VIEWS IN SCHEMA fs1 TO ROLE fgb");
        engine.execute("CREATE VIEW fs1.kv2 AS SELECT 1 AS a");
        assertEquals("FGB", cellOf("SHOW VIEWS LIKE 'kv2' IN SCHEMA fs1", "owner"));

        engine.execute("GRANT OWNERSHIP ON FUTURE SEQUENCES IN SCHEMA fs1 TO ROLE fga");
        engine.execute("CREATE SEQUENCE fs1.kq");
        assertEquals("FGA", cellOf("SHOW SEQUENCES LIKE 'kq' IN SCHEMA fs1", "owner"));

        engine.execute("GRANT OWNERSHIP ON FUTURE STAGES IN SCHEMA fs1 TO ROLE fga");
        engine.execute("CREATE STAGE fs1.kg");
        assertEquals("FGA", cellOf("SHOW STAGES LIKE 'kg' IN SCHEMA fs1", "owner"));
    }

    /** A privilege other than OWNERSHIP is granted in the OWNER's name, not the creator's. */
    @Test
    public void aFuturePrivilegeIsRecordedInTheOwnersName() {
        engine.execute("GRANT OWNERSHIP ON FUTURE TABLES IN SCHEMA fs1 TO ROLE fga");
        engine.execute("GRANT SELECT ON FUTURE TABLES IN SCHEMA fs1 TO ROLE fgb");
        engine.execute("CREATE TABLE fs1.p1 (a INT)");
        assertEquals("OWNERSHIP, FGA, FGA, true | SELECT, FGB, FGA, false",
            rowsOf("SHOW GRANTS ON TABLE fs1.p1",
                "privilege", "grantee_name", "granted_by", "grant_option"));
    }

    /** WITH GRANT OPTION carries through to the grant the future grant makes. */
    @Test
    public void theGrantOptionCarriesThrough() {
        engine.execute("GRANT OWNERSHIP ON FUTURE TABLES IN SCHEMA fs1 TO ROLE fga");
        engine.execute("GRANT INSERT ON FUTURE TABLES IN SCHEMA fs1 TO ROLE fgb WITH GRANT OPTION");
        engine.execute("CREATE TABLE fs1.g1 (a INT)");
        assertEquals("OWNERSHIP, FGA, FGA, true | INSERT, FGB, FGA, true",
            rowsOf("SHOW GRANTS ON TABLE fs1.g1",
                "privilege", "grantee_name", "granted_by", "grant_option"));
    }

    /** The narrower scope wins whole, and the wider one still covers what it alone reaches. */
    @Test
    public void aSchemaScopeShutsOutTheDatabaseScope() {
        engine.execute("GRANT OWNERSHIP ON FUTURE TABLES IN SCHEMA fs1 TO ROLE fga");
        engine.execute("GRANT OWNERSHIP ON FUTURE TABLES IN DATABASE test_db TO ROLE fgb");
        engine.execute("CREATE TABLE fs1.s1 (a INT)");
        assertEquals("FGA", cellOf("SHOW TABLES LIKE 's1' IN SCHEMA fs1", "owner"),
            "the schema's grant wins where both cover the object");
        engine.execute("CREATE TABLE fs2.s2 (a INT)");
        assertEquals("FGB", cellOf("SHOW TABLES LIKE 's2' IN SCHEMA fs2", "owner"),
            "and the database's grant covers the schema the other cannot reach");
    }

    /** A future grant on SCHEMAS is scoped to the database, and owns the schemas made after it. */
    @Test
    public void aFutureSchemaGrantIsScopedToTheDatabase() {
        engine.execute("GRANT OWNERSHIP ON FUTURE SCHEMAS IN DATABASE test_db TO ROLE fga");
        engine.execute("CREATE SCHEMA fs3");
        assertEquals("FGA", cellOf("SHOW SCHEMAS LIKE 'fs3' IN DATABASE test_db", "owner"));
    }

    /** Revoking the grant stops it firing, and leaves what it already produced alone. */
    @Test
    public void revokingStopsTheNextOneOnly() {
        engine.execute("GRANT OWNERSHIP ON FUTURE TABLES IN SCHEMA fs1 TO ROLE fga");
        engine.execute("CREATE TABLE fs1.r1 (a INT)");
        engine.execute("REVOKE OWNERSHIP ON FUTURE TABLES IN SCHEMA fs1 FROM ROLE fga");
        engine.execute("CREATE TABLE fs1.r2 (a INT)");
        assertEquals("FGA", cellOf("SHOW TABLES LIKE 'r1' IN SCHEMA fs1", "owner"));
        assertEquals("ACCOUNTADMIN", cellOf("SHOW TABLES LIKE 'r2' IN SCHEMA fs1", "owner"));
    }
}
