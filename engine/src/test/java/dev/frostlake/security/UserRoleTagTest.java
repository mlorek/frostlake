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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ALTER USER and ALTER ROLE … SET TAG / UNSET TAG, read back through TAG_REFERENCES, and ALTER ROLE UNSET COMMENT. */
public class UserRoleTagTest extends BaseDatabaseTest {

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
    public void aUserAndARoleCarryTags() {
        engine.executeQuery("CREATE TAG ur_tag");
        engine.executeQuery("CREATE USER tagged_user");
        engine.executeQuery("CREATE ROLE tagged_role COMMENT = 'c'");
        engine.executeQuery("ALTER USER tagged_user SET TAG ur_tag = 'u1'");
        engine.executeQuery("ALTER ROLE tagged_role SET TAG test_db.test_schema.ur_tag = 'r1'");
        assertEquals(List.of("UR_TAG|u1|USER|TAGGED_USER"), rows("""
            SELECT * FROM TABLE(test_db.INFORMATION_SCHEMA.TAG_REFERENCES('tagged_user', 'USER'))""",
            "TAG_NAME", "TAG_VALUE", "LEVEL", "OBJECT_NAME"));
        assertEquals(List.of("UR_TAG|r1|ROLE"), rows("""
            SELECT * FROM TABLE(test_db.INFORMATION_SCHEMA.TAG_REFERENCES('tagged_role', 'ROLE'))""",
            "TAG_NAME", "TAG_VALUE", "LEVEL"));
        engine.executeQuery("ALTER USER tagged_user UNSET TAG ur_tag");
        engine.executeQuery("ALTER ROLE tagged_role UNSET TAG ur_tag");
        assertEquals(List.of(), rows("""
            SELECT * FROM TABLE(test_db.INFORMATION_SCHEMA.TAG_REFERENCES('tagged_user', 'USER'))""", "TAG_NAME"));
        assertEquals(List.of(), rows("""
            SELECT * FROM TABLE(test_db.INFORMATION_SCHEMA.TAG_REFERENCES('tagged_role', 'ROLE'))""", "TAG_NAME"));
        assertTrue(refusal("ALTER ROLE tagged_role SET TAG no_such_tag = 'x'").contains("does not exist"));

        engine.executeQuery("ALTER ROLE tagged_role UNSET COMMENT");
        assertEquals(List.of("TAGGED_ROLE|"), rows("SHOW ROLES LIKE 'TAGGED_ROLE'", "name", "comment"));
    }
}
