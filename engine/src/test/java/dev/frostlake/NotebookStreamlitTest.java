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

package dev.frostlake;

import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** NOTEBOOK and STREAMLIT objects: CREATE, ALTER, DROP, UNDROP, SHOW and DESCRIBE, with their versions. */
public class NotebookStreamlitTest extends BaseDatabaseTest {

    /** A query warehouse must exist, so the class makes two and takes them away again. */
    @BeforeEach
    public void createWarehouses() {
        engine.execute("CREATE WAREHOUSE IF NOT EXISTS fl_app_wh1 INITIALLY_SUSPENDED = TRUE");
        engine.execute("CREATE WAREHOUSE IF NOT EXISTS fl_app_wh2 INITIALLY_SUSPENDED = TRUE");
    }

    @AfterEach
    public void dropWarehouses() {
        engine.execute("DROP WAREHOUSE IF EXISTS fl_app_wh1");
        engine.execute("DROP WAREHOUSE IF EXISTS fl_app_wh2");
    }

    private static String cell(final ResultSet rs, final String column) {
        final Object value = rs.getRows().get(0).getValue(rs.getColumnIndex(column));
        return value == null ? null : value.toString();
    }

    private static List<String> names(final ResultSet rs) {
        final List<String> names = new ArrayList<>();
        for (int i = 0; i < rs.getRowCount(); i++) {
            names.add(rs.getRows().get(i).getValue(rs.getColumnIndex("name")).toString());
        }
        return names;
    }

    private void refused(final String sql, final String fragment) {
        final Throwable refusal = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(refusal.getMessage().contains(fragment), refusal.getMessage());
    }

    @Test
    public void aNotebookIsCreatedShownAndDescribed() {
        assertEquals("Notebook NB1 successfully created.", engine.executeQuery("CREATE NOTEBOOK nb1 FROM '@stg/nb' "
            + "MAIN_FILE = 'nb.ipynb' QUERY_WAREHOUSE = fl_app_wh1 COMMENT = 'first' IDLE_AUTO_SHUTDOWN_TIME_SECONDS = 600")
            .getRows().get(0).getValue(0));
        final ResultSet shown = engine.executeQuery("SHOW NOTEBOOKS");
        assertEquals(List.of("NB1"), names(shown));
        assertEquals("first", cell(shown, "comment"));
        assertEquals("FL_APP_WH1", cell(shown, "query_warehouse"));
        assertEquals("TEST_DB", cell(shown, "database_name"));
        assertEquals("ROLE", cell(shown, "owner_role_type"));
        final ResultSet described = engine.executeQuery("DESCRIBE NOTEBOOK nb1");
        assertEquals("nb.ipynb", cell(described, "main_file"));
        assertEquals("600", cell(described, "idle_auto_shutdown_time_seconds"));
        assertEquals("VERSION$1", cell(described, "default_version_name"));
        assertEquals("VERSION$1", cell(described, "last_version_name"));
        assertNull(cell(described, "live_version_location_uri"));
    }

    @Test
    public void createModesFollowTheUsualRules() {
        engine.execute("CREATE NOTEBOOK nb_modes COMMENT = 'a'");
        refused("CREATE NOTEBOOK nb_modes", "already exists");
        assertEquals("NB_MODES already exists, statement succeeded.",
            engine.executeQuery("CREATE NOTEBOOK IF NOT EXISTS nb_modes COMMENT = 'b'").getRows().get(0).getValue(0));
        assertEquals("a", cell(engine.executeQuery("SHOW NOTEBOOKS LIKE 'NB_MODES'"), "comment"));
        engine.execute("CREATE OR REPLACE NOTEBOOK nb_modes COMMENT = 'c'");
        assertEquals("c", cell(engine.executeQuery("SHOW NOTEBOOKS LIKE 'NB_MODES'"), "comment"));
    }

    @Test
    public void aNotebookIsAlteredRenamedDroppedAndUndropped() {
        engine.execute("CREATE NOTEBOOK nb_alter QUERY_WAREHOUSE = fl_app_wh1");
        engine.execute("ALTER NOTEBOOK nb_alter SET COMMENT = 'x' QUERY_WAREHOUSE = fl_app_wh2 "
            + "IDLE_AUTO_SHUTDOWN_TIME_SECONDS = 120");
        assertEquals("FL_APP_WH2", cell(engine.executeQuery("SHOW NOTEBOOKS LIKE 'NB_ALTER'"), "query_warehouse"));
        engine.execute("ALTER NOTEBOOK nb_alter UNSET COMMENT, QUERY_WAREHOUSE");
        final ResultSet unset = engine.executeQuery("SHOW NOTEBOOKS LIKE 'NB_ALTER'");
        assertNull(cell(unset, "comment"));
        assertNull(cell(unset, "query_warehouse"));
        engine.execute("ALTER NOTEBOOK nb_alter RENAME TO nb_renamed");
        assertEquals(List.of("NB_RENAMED"), names(engine.executeQuery("SHOW NOTEBOOKS LIKE 'NB_%'")));
        assertEquals("NB_RENAMED successfully dropped.",
            engine.executeQuery("DROP NOTEBOOK nb_renamed").getRows().get(0).getValue(0));
        refused("DESCRIBE NOTEBOOK nb_renamed", "Notebook 'TEST_DB.TEST_SCHEMA.NB_RENAMED' does not exist");
        engine.execute("UNDROP NOTEBOOK nb_renamed");
        assertEquals(List.of("NB_RENAMED"), names(engine.executeQuery("SHOW NOTEBOOKS")));
        refused("UNDROP NOTEBOOK nb_renamed", "already exists");
        refused("ALTER NOTEBOOK nosuch SET COMMENT = 'x'", "does not exist or not authorized");
        engine.execute("ALTER NOTEBOOK IF EXISTS nosuch SET COMMENT = 'x'");
    }

    @Test
    public void aStreamlitKeepsItsVersions() {
        assertEquals("Streamlit APP successfully created.", engine.executeQuery("CREATE STREAMLIT app FROM "
            + "'@stg/app' MAIN_FILE = 'streamlit_app.py' TITLE = 'My app' QUERY_WAREHOUSE = fl_app_wh1 "
            + "IMPORTS = ('@stg/lib.py') EXTERNAL_ACCESS_INTEGRATIONS = (ext1)").getRows().get(0).getValue(0));
        ResultSet described = engine.executeQuery("DESCRIBE STREAMLIT app");
        assertEquals("My app", cell(described, "title"));
        assertEquals("[\"@stg/lib.py\"]", cell(described, "import_urls"));
        assertEquals("[\"EXT1\"]", cell(described, "external_access_integrations"));
        assertEquals("VERSION$1", cell(described, "last_version_name"));
        assertNull(cell(described, "live_version_location_uri"));
        refused("ALTER STREAMLIT app COMMIT", "Live version is not found.");
        refused("ALTER STREAMLIT app ABORT", "Live version is not found.");
        engine.execute("ALTER STREAMLIT app ADD LIVE VERSION FROM LAST");
        refused("ALTER STREAMLIT app ADD LIVE VERSION FROM LAST", "There is already a live version.");
        described = engine.executeQuery("DESCRIBE STREAMLIT app");
        assertTrue(cell(described, "live_version_location_uri").endsWith("/versions/live/"),
            cell(described, "live_version_location_uri"));
        engine.execute("ALTER STREAMLIT app COMMIT");
        described = engine.executeQuery("DESCRIBE STREAMLIT app");
        assertEquals("VERSION$2", cell(described, "last_version_name"));
        assertEquals("VERSION$2", cell(described, "default_version_name"));
        assertEquals("LAST", cell(described, "default_version"));
        assertNull(cell(described, "live_version_location_uri"), "a commit closes the live version");
        engine.execute("ALTER STREAMLIT app ADD LIVE VERSION FROM LAST");
        engine.execute("ALTER STREAMLIT app ABORT");
        assertNull(cell(engine.executeQuery("DESCRIBE STREAMLIT app"), "live_version_location_uri"));
    }

    @Test
    public void aLegacyStreamlitDescribesItsRootLocation() {
        engine.execute("CREATE STREAMLIT legacy ROOT_LOCATION = '@stg/old' MAIN_FILE = 'a.py'");
        final ResultSet described = engine.executeQuery("DESC STREAMLIT legacy");
        assertEquals("@stg/old", cell(described, "root_location"));
        assertEquals("a.py", cell(described, "main_file"));
        for (int i = 0; i < described.getColumnCount(); i++) {
            assertTrue(!described.getColumns().get(i).getName().startsWith("last_version"),
                "a legacy app has no versions");
        }
    }

    @Test
    public void aStreamlitIsAlteredDroppedAndUndropped() {
        engine.execute("CREATE STREAMLIT st_alter TITLE = 't' COMMENT = 'c'");
        engine.execute("ALTER STREAMLIT st_alter SET TITLE = 'u' MAIN_FILE = 'b.py'");
        assertEquals("u", cell(engine.executeQuery("SHOW STREAMLITS LIKE 'ST_ALTER'"), "title"));
        engine.execute("ALTER STREAMLIT st_alter UNSET TITLE, COMMENT");
        assertNull(cell(engine.executeQuery("SHOW STREAMLITS LIKE 'ST_ALTER'"), "title"));
        engine.execute("ALTER STREAMLIT st_alter RENAME TO st_renamed");
        engine.execute("DROP STREAMLIT st_renamed");
        assertEquals("Drop statement executed successfully (ST_RENAMED already dropped).",
            engine.executeQuery("DROP STREAMLIT IF EXISTS st_renamed").getRows().get(0).getValue(0));
        engine.execute("UNDROP STREAMLIT st_renamed");
        assertEquals(List.of("ST_RENAMED"), names(engine.executeQuery("SHOW STREAMLITS")));
        refused("UNDROP STREAMLIT never_dropped", "Streamlit NEVER_DROPPED did not exist or was purged.");
        refused("CREATE NOTEBOOK nb_wh QUERY_WAREHOUSE = no_such_wh", "The specified warehouse NO_SUCH_WH does not exist");
    }

    @Test
    public void listingsTakeTheirModifiers() {
        engine.execute("CREATE STREAMLIT b_app");
        engine.execute("CREATE STREAMLIT a_app");
        engine.execute("CREATE STREAMLIT c_app");
        assertEquals(List.of("A_APP", "B_APP", "C_APP"), names(engine.executeQuery("SHOW STREAMLITS")));
        assertEquals(List.of("B_APP"), names(engine.executeQuery("SHOW STREAMLITS STARTS WITH 'B'")));
        assertEquals(List.of("B_APP"), names(engine.executeQuery("SHOW STREAMLITS LIMIT 1 FROM 'B'")));
        assertEquals(List.of("A_APP", "B_APP", "C_APP"),
            names(engine.executeQuery("SHOW STREAMLITS IN SCHEMA test_db.test_schema")));
        final ResultSet terse = engine.executeQuery("SHOW TERSE STREAMLITS");
        assertEquals("Streamlit", cell(terse, "kind"));
        assertEquals(7, terse.getColumnCount());
    }

    @Test
    public void theNewWordsStillNameColumns() {
        engine.execute("CREATE TABLE app_words (notebook INT, streamlit INT, version INT, title STRING, live INT, "
            + "abort INT, main_file STRING)");
        engine.execute("INSERT INTO app_words VALUES (1, 2, 3, 't', 4, 5, 'm')");
        final ResultSet rs = engine.executeQuery("SELECT notebook + streamlit + version + live + abort AS total, "
            + "title, main_file FROM app_words");
        assertEquals("15", rs.getRows().get(0).getValue(0).toString());
    }
}
