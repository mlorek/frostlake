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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UUID_STRING, on the shared test base so a live run actually exercises it.
 *
 * <p>This class used to build its own {@code DatabaseEngine}, which made it invisible to the SF_LIVE
 * switch — the switch reroutes the test BASES, so a class holding its own engine tests Frostlake
 * against Frostlake for ever. Converting it is what surfaced the VALUES-clause rule asserted below.
 */
public class UuidStringTest extends BaseDatabaseTest {

    private static final Pattern UUID_PATTERN =
        Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
            Pattern.CASE_INSENSITIVE);

    @Test
    public void namedFormIsAnExactRfc4122Version5Uuid() {
        // Snowflake's UUID_STRING(uuid_namespace, name) is a version-5 (SHA-1) named UUID; this
        // input/output pair is the documented Snowflake example, so the named form is bit-exact.
        assertEquals("dc0b6f65-fca6-5b4b-9d37-ccc3fde1f3e2",
            engine.executeQuery("SELECT UUID_STRING('fe971b24-9572-4005-b22f-351e9c09274d', 'foo')")
                .getRows().get(0).getValue(0).toString());
        // Deterministic: same inputs, same UUID — and a numeric name is used as its text.
        assertEquals(
            engine.executeQuery("SELECT UUID_STRING('fe971b24-9572-4005-b22f-351e9c09274d', 42)")
                .getRows().get(0).getValue(0).toString(),
            engine.executeQuery("SELECT UUID_STRING('fe971b24-9572-4005-b22f-351e9c09274d', '42')")
                .getRows().get(0).getValue(0).toString());
        // Neither argument is null-propagating, and the two differ — live-measured. A NULL or
        // malformed NAMESPACE is an error; a NULL NAME hashes as the empty string and still yields a
        // UUID. This used to assert NULL for both, which is what the class never running live hid.
        final RuntimeException badNamespace = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT UUID_STRING(NULL, 'foo')");
            }
        });
        assertTrue(String.valueOf(badNamespace.getMessage()).contains("Badly formed UUID"),
            badNamespace.getMessage());
        assertEquals("5218e289-d8a6-5159-a111-bdcf3110899a",
            engine.executeQuery("SELECT UUID_STRING('fe971b24-9572-4005-b22f-351e9c09274d', NULL)")
                .getRows().get(0).getValue(0).toString());
    }

    @Test
    public void testUuidStringFunction() {
        final ResultSet rs = engine.executeQuery("SELECT UUID_STRING()");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        final String uuid = rs.getRows().get(0).getValue(0).toString();
        assertTrue(UUID_PATTERN.matcher(uuid).matches(), "Expected UUID format, got: " + uuid);
    }

    @Test
    public void testUuidStringGeneratesUniqueValues() {
        final ResultSet r1 = engine.executeQuery("SELECT UUID_STRING()");
        final ResultSet r2 = engine.executeQuery("SELECT UUID_STRING()");
        final String u1 = r1.getRows().get(0).getValue(0).toString();
        final String u2 = r2.getRows().get(0).getValue(0).toString();
        assertNotEquals(u1, u2, "Two UUID_STRING() calls should produce different values");
    }

    @Test
    public void testUuidStringAsDefault() {
        engine.execute("""
            CREATE OR REPLACE TABLE sample_generate_uuid (
              id UUID DEFAULT UUID_STRING() NOT NULL,
              sample_column VARCHAR)
            """);

        engine.execute("INSERT INTO sample_generate_uuid (sample_column) VALUES ('hello')");
        engine.execute("INSERT INTO sample_generate_uuid (sample_column) VALUES ('world')");

        final ResultSet rs = engine.executeQuery("SELECT id, sample_column FROM sample_generate_uuid");
        assertEquals(2, rs.getRowCount());

        final String id1 = rs.getRows().get(0).getValue(0).toString();
        final String id2 = rs.getRows().get(1).getValue(0).toString();

        assertTrue(UUID_PATTERN.matcher(id1).matches(), "id1 should be UUID: " + id1);
        assertTrue(UUID_PATTERN.matcher(id2).matches(), "id2 should be UUID: " + id2);
        assertNotEquals(id1, id2, "Each row should get a distinct UUID");
    }

    /**
     * UUID_STRING may not appear in a VALUES clause — live-measured, and by NAME rather than by
     * determinism: the exact RFC 4122 two-argument form is refused there too, while
     * CURRENT_TIMESTAMP() passes. INSERT ... SELECT is the supported route, so that is what this
     * asserts alongside the refusal.
     */
    @Test
    public void testExplicitUuidInsert() {
        engine.execute("CREATE TABLE with_uuid (id UUID, name VARCHAR)");

        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("INSERT INTO with_uuid VALUES (UUID_STRING(), 'Alice')");
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains("in VALUES clause"),
            refused.getMessage());

        engine.execute("INSERT INTO with_uuid SELECT UUID_STRING(), 'Alice'");
        final ResultSet rs = engine.executeQuery("SELECT id FROM with_uuid");
        assertEquals(1, rs.getRowCount());
        assertTrue(UUID_PATTERN.matcher(rs.getRows().get(0).getValue(0).toString()).matches());
    }
}
