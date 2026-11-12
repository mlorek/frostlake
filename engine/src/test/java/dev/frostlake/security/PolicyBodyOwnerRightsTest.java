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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A masking or a row access policy's body runs with its owner's rights: a role that may read the protected table needs
 * no USAGE on a UDF the body calls and no SELECT on a mapping table it reads, while CURRENT_ROLE() still answers the
 * reading role, so the mapping-table pattern works. The body ran with the reading role's privileges and refused it.
 * Engine only: the live account allows no CREATE ROLE, CREATE USER or GRANT.
 */
public class PolicyBodyOwnerRightsTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE pb");
        engine.execute("USE SCHEMA pb.public");
        engine.execute("CREATE TABLE t (a INT, b VARCHAR)");
        engine.execute("INSERT INTO t VALUES (1, 'secret'), (2, 'other')");
        engine.execute("CREATE FUNCTION mask_it(v VARCHAR) RETURNS VARCHAR AS 'REPEAT(''*'', LENGTH(v))'");
        engine.execute("CREATE MASKING POLICY mp AS (v VARCHAR) RETURNS VARCHAR -> pb.public.mask_it(v)");
        engine.execute("ALTER TABLE t MODIFY COLUMN b SET MASKING POLICY mp");
        engine.execute("CREATE TABLE unmask (r VARCHAR)");
        engine.execute("INSERT INTO unmask VALUES ('NOBODY')");
        engine.execute("CREATE MASKING POLICY mp2 AS (v VARCHAR) RETURNS VARCHAR -> CASE WHEN EXISTS (SELECT 1 FROM "
            + "pb.public.unmask m WHERE m.r = CURRENT_ROLE()) THEN v ELSE '***' END");
        engine.execute("CREATE TABLE t2 (a INT, b VARCHAR)");
        engine.execute("INSERT INTO t2 VALUES (1, 'secret')");
        engine.execute("ALTER TABLE t2 MODIFY COLUMN b SET MASKING POLICY mp2");
        engine.execute("CREATE TABLE entitled (r VARCHAR)");
        engine.execute("INSERT INTO entitled VALUES ('RA')");
        engine.execute("CREATE ROW ACCESS POLICY rap AS (a INT) RETURNS BOOLEAN -> EXISTS (SELECT 1 FROM "
            + "pb.public.entitled e WHERE e.r = CURRENT_ROLE()) AND a = 1");
        engine.execute("CREATE TABLE r (a INT)");
        engine.execute("INSERT INTO r VALUES (1), (2)");
        engine.execute("ALTER TABLE r ADD ROW ACCESS POLICY rap ON (a)");
        engine.execute("CREATE ROLE ra");
        engine.execute("CREATE USER ua DEFAULT_SECONDARY_ROLES = ()");
        engine.execute("GRANT ROLE ra TO USER ua");
        engine.execute("GRANT USAGE ON DATABASE pb TO ROLE ra");
        engine.execute("GRANT USAGE ON SCHEMA pb.public TO ROLE ra");
        engine.execute("GRANT SELECT ON TABLE t TO ROLE ra");
        engine.execute("GRANT SELECT ON TABLE t2 TO ROLE ra");
        engine.execute("GRANT SELECT ON TABLE r TO ROLE ra");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() == 0 ? "" : " ;; ").append(row.getValue(0));
        }
        return out.toString();
    }

    @Test
    public void theBodiesReadWhatTheirOwnerMayRead() {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("UA");
        engine.setCurrentRole("RA");
        assertEquals("****** ;; *****", rows("SELECT b FROM pb.public.t ORDER BY a"));
        assertEquals("***", rows("SELECT b FROM pb.public.t2"));
        assertEquals("1", rows("SELECT a FROM pb.public.r ORDER BY a"));
        assertEquals("RA", rows("SELECT CURRENT_ROLE()"));
    }
}
