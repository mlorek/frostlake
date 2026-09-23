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
import dev.frostlake.storage.Row;

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

/**
 * What notebooks and Streamlit apps do beyond their properties: SECRETS, the version actions (ADD LIVE VERSION, ADD
 * VERSION, COMMIT, ABORT), SHOW VERSIONS, the Git actions (PUSH, PULL) and EXECUTE NOTEBOOK — each answered, or refused,
 * as the account answers it where no Git repository and no external access integration is at hand.
 */
public class NotebookStreamlitActionsTest extends BaseDatabaseTest {

    private static final String NOT_FROM_GIT =
        "Version contains invalid source lineage information: The version is not created from a git source..";
    private static final String NO_LINEAGE =
        "Version contains invalid source lineage information: Missing source domain id..";
    private static final String THREE_SETTINGS = "Invalid property list: The following three settings should all be "
        + "specified, or none of them should be specified: 1. GIT_CREDENTIALS or USERNAME&PASSWORD pair 2.NAME "
        + "3.EMAIL.";

    /** A query warehouse must exist, and the apps' versions are copied from a stage. */
    @BeforeEach
    public void createWarehouseAndStage() {
        engine.execute("CREATE WAREHOUSE IF NOT EXISTS fl_act_wh INITIALLY_SUSPENDED = TRUE");
        engine.execute("CREATE STAGE app_stage");
        engine.execute("CREATE SECRET plain_secret TYPE = GENERIC_STRING SECRET_STRING = 'x'");
        engine.execute("CREATE SECRET git_secret TYPE = PASSWORD USERNAME = 'u' PASSWORD = 'p'");
    }

    @AfterEach
    public void dropWarehouse() {
        engine.execute("DROP WAREHOUSE IF EXISTS fl_act_wh");
    }

    private String refusalOf(final String sql) {
        final RuntimeException refusal = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return refusal.getMessage();
    }

    private String status(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    /** SHOW VERSIONS as "name|alias|is_live|is_last|comment|source" per row, in its order. */
    private List<String> versions(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> rows = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            rows.add(cell(rs, row, "name") + "|" + cell(rs, row, "alias") + "|" + cell(rs, row, "is_live") + "|"
                + cell(rs, row, "is_last") + "|" + cell(rs, row, "comment") + "|"
                + cell(rs, row, "source_location_uri"));
        }
        return rows;
    }

    private String described(final String sql, final String column) {
        final ResultSet rs = engine.executeQuery(sql);
        return cell(rs, rs.getRows().get(0), column);
    }

    // ── SECRETS ───────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    public void alterRefusesSecretsNoIntegrationAllows() {
        engine.execute("CREATE NOTEBOOK nb_sec");
        engine.execute("CREATE STREAMLIT st_sec");
        for (final String kind : new String[] {"NOTEBOOK nb_sec", "STREAMLIT st_sec"}) {
            assertEquals("SQL compilation error: Integrations do not allow secret 'TEST_DB.TEST_SCHEMA.PLAIN_SECRET'.",
                refusalOf("ALTER " + kind + " SET SECRETS = ('api_key' = plain_secret)"));
            assertEquals("SQL compilation error:\nSecret 'NO_SUCH' does not exist or operation not authorized.",
                refusalOf("ALTER " + kind + " SET SECRETS = ('a' = plain_secret, 'b' = no_such)"));
            assertEquals("SQL compilation error: Secret values must be specified using object identifier.",
                refusalOf("ALTER " + kind + " SET SECRETS = ('a' = no_such, 'b' = 'plain_secret')"));
            assertEquals("SQL compilation error: Secret names must be specified using single quotes.",
                refusalOf("ALTER " + kind + " SET SECRETS = (a = plain_secret)"));
            assertTrue(refusalOf("ALTER " + kind + " SET SECRETS = ('a' = no_such, b = plain_secret)")
                .contains("syntax error"));
            assertEquals("SQL compilation error: Duplicate secret name 'a' passed in.",
                refusalOf("ALTER " + kind + " SET SECRETS = ('a' = plain_secret, 'a' = plain_secret)"));
            assertEquals("SQL compilation error:\nduplicate property 'SECRETS';",
                refusalOf("ALTER " + kind + " SET SECRETS = () SECRETS = ()"));
            // An empty list and UNSET change nothing, and DESCRIBE keeps its empty map.
            engine.execute("ALTER " + kind + " SET SECRETS = ()");
            engine.execute("ALTER " + kind + " UNSET SECRETS");
            engine.execute("ALTER " + kind + " UNSET SECRETS, COMMENT");
            assertEquals("{}", described("DESCRIBE " + kind, "external_access_secrets"));
        }
    }

    @Test
    public void createRefusesAnySecrets() {
        final String using =
            "SQL compilation error: Using secrets requires a valid External Access Integration with allowed secrets.";
        assertEquals(using, refusalOf("CREATE NOTEBOOK nb_new SECRETS = ('a' = plain_secret)"));
        assertEquals(using, refusalOf("CREATE NOTEBOOK nb_new SECRETS = ('a' = no_such)"));
        assertEquals(using, refusalOf("CREATE STREAMLIT st_new SECRETS = ()"));
        assertEquals("SQL compilation error: Secret values must be specified using object identifier.",
            refusalOf("CREATE STREAMLIT st_new SECRETS = ('a' = 'plain_secret')"));
        assertEquals("SQL compilation error: Secret names must be specified using single quotes.",
            refusalOf("CREATE NOTEBOOK nb_new SECRETS = (a = plain_secret)"));
        engine.execute("CREATE NOTEBOOK nb_new");
        assertEquals(using, refusalOf("CREATE NOTEBOOK IF NOT EXISTS nb_new SECRETS = ('a' = plain_secret)"));
    }

    @Test
    public void aPropertyNamedTwiceIsRefused() {
        engine.execute("CREATE NOTEBOOK nb_twice");
        assertEquals("SQL compilation error:\nduplicate property 'COMMENT';",
            refusalOf("ALTER NOTEBOOK nb_twice SET COMMENT = 'a' COMMENT = 'b'"));
        assertEquals("SQL compilation error:\nduplicate property 'COMMENT';",
            refusalOf("ALTER NOTEBOOK nb_twice UNSET COMMENT, COMMENT"));
        assertEquals("SQL compilation error:\nduplicate property 'TITLE';",
            refusalOf("CREATE STREAMLIT st_twice TITLE = 'a' TITLE = 'b'"));
        engine.execute("CREATE STREAMLIT st_twice");
        assertEquals("SQL compilation error:\nduplicate property 'TITLE';",
            refusalOf("ALTER STREAMLIT st_twice SET TITLE = 'a' TITLE = 'b'"));
        assertEquals("SQL compilation error:\ninvalid type of property 'null' for 'IDLE_AUTO_SHUTDOWN_TIME_SECONDS'",
            refusalOf("ALTER NOTEBOOK nb_twice UNSET IDLE_AUTO_SHUTDOWN_TIME_SECONDS"));
    }

    // ── versions ──────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    public void aNotebookKeepsItsVersions() {
        engine.execute("CREATE NOTEBOOK nb_ver");
        assertEquals("Live version V1 successfully created.",
            status("ALTER NOTEBOOK nb_ver ADD LIVE VERSION v1 FROM LAST"));
        assertEquals("There is already a live version. Please commit it first.",
            refusalOf("ALTER NOTEBOOK nb_ver ADD LIVE VERSION FROM LAST COMMENT = 'x'"));
        assertEquals(List.of("null|V1|true|false|null|null", "VERSION$1|null|false|true|null|null"),
            versions("SHOW VERSIONS IN NOTEBOOK nb_ver"));
        assertEquals("SQL compilation error:\ninvalid parameter 'FOO'", refusalOf("ALTER NOTEBOOK nb_ver COMMIT FOO = 'x'"));
        assertEquals("Live version V1 successfully committed.",
            status("ALTER NOTEBOOK nb_ver COMMIT COMMENT = 'first'"));
        assertEquals("Live version is not found.", refusalOf("ALTER NOTEBOOK nb_ver COMMIT"));
        assertEquals("Live version is not found.", refusalOf("ALTER NOTEBOOK nb_ver ABORT"));
        assertEquals("Live version successfully created.", status("ALTER NOTEBOOK nb_ver ADD LIVE VERSION FROM LAST"));
        assertEquals("Live version successfully aborted.", status("ALTER NOTEBOOK nb_ver ABORT"));
        engine.execute("ALTER NOTEBOOK nb_ver ADD LIVE VERSION \"Low x\" FROM LAST");
        assertEquals("Live version Low x successfully aborted.", status("ALTER NOTEBOOK nb_ver ABORT"));
        // A committed version keeps the live version's alias and takes the commit's comment.
        assertEquals(List.of("VERSION$2|V1|false|true|first|null", "VERSION$1|null|false|false|null|null"),
            versions("SHOW VERSIONS IN NOTEBOOK nb_ver"));
        assertEquals("V1", described("DESCRIBE NOTEBOOK nb_ver", "last_version_alias"));
        assertEquals("notebook_app.ipynb", described("DESCRIBE NOTEBOOK nb_ver", "main_file"));
        assertTrue(refusalOf("ALTER NOTEBOOK IF EXISTS nb_ver COMMIT").contains("syntax error"));
        assertEquals("Unsupported feature 'SHOW VERSIONS'.", refusalOf("SHOW VERSIONS"));
    }

    @Test
    public void aVersionIsAddedFromAStage() {
        engine.execute("CREATE STREAMLIT st_ver");
        assertEquals("Version V2 successfully created.",
            status("ALTER STREAMLIT st_ver ADD VERSION v2 FROM '@app_stage/sub' COMMENT = 'staged'"));
        assertEquals("There is already a version exists with alias V2.",
            refusalOf("ALTER STREAMLIT st_ver ADD VERSION v2 FROM '@app_stage'"));
        assertEquals("There is already a version exists with alias V2.",
            status("ALTER STREAMLIT st_ver ADD VERSION IF NOT EXISTS v2 FROM '@app_stage'"));
        assertEquals("Version successfully created.", status("ALTER STREAMLIT st_ver ADD VERSION FROM @app_stage"));
        assertEquals(List.of("VERSION$3|null|false|true|null|@app_stage",
                "VERSION$2|V2|false|false|staged|@app_stage/sub/", "VERSION$1|null|false|false|null|null"),
            versions("SHOW VERSIONS IN STREAMLIT st_ver"));
        assertEquals("@app_stage", described("DESCRIBE STREAMLIT st_ver", "default_version_source_location_uri"));
        assertEquals("streamlit_app.py", described("DESCRIBE STREAMLIT st_ver", "main_file"));
        assertEquals("SQL compilation error:\ninvalid URL prefix found in: 'app_stage'",
            refusalOf("ALTER STREAMLIT st_ver ADD VERSION FROM 'app_stage'"));
        assertEquals("The specified stage NO_SUCH_STAGE does not exist or the current role does not have access. "
                + "Owner of the Streamlit must have at least READ on the specified stage.",
            refusalOf("ALTER STREAMLIT st_ver ADD VERSION FROM '@no_such_stage/x'"));
        assertEquals("Unsupported feature 'stage container type: git-repository'.",
            refusalOf("ALTER STREAMLIT st_ver ADD VERSION v9 FROM 'snow://git-repository/db.s.repo/branches/main'"));
        // An open live version stops a new version, and takes the last version's location.
        engine.execute("ALTER STREAMLIT st_ver ADD LIVE VERSION lv FROM LAST COMMENT = 'live c'");
        assertEquals("There is already a live version. Please commit it first.",
            refusalOf("ALTER STREAMLIT st_ver ADD VERSION v5 FROM '@app_stage'"));
        assertEquals("null|LV|true|false|live c|@app_stage", versions("SHOW VERSIONS IN STREAMLIT st_ver").get(0));
        assertEquals("SQL compilation error:\ninvalid parameter 'FOO'",
            refusalOf("ALTER STREAMLIT st_ver COMMIT COMMENT = 'x' FOO = 'y'"));
        engine.execute("ALTER STREAMLIT st_ver COMMIT");
        assertEquals("VERSION$4|LV|false|true|null|@app_stage", versions("SHOW VERSIONS IN STREAMLIT st_ver").get(0));
    }

    /**
     * A version's alias is a name the account keeps no word of its own as: FIRST, LAST, LIVE, VERSION or COMMENT
     * written bare is a syntax error where it stands, and an alias the account keeps for its own names — one that
     * starts with version$, holds a slash, or is FIRST, LAST, LIVE or DEFAULT in any case — is refused before the
     * location, the parameters and the app itself are looked at.
     */
    @Test
    public void aVersionAliasIsANameTheAccountKeepsNoneOf() {
        engine.execute("CREATE STREAMLIT st_alias");
        for (final String word : new String[] {"LAST", "FIRST", "LIVE", "DEFAULT", "VERSION", "COMMENT", "NAME"}) {
            final String sql = "ALTER STREAMLIT st_alias ADD VERSION " + word + " FROM '@app_stage'";
            assertEquals("SQL compilation error:\nsyntax error line 1 at position " + sql.indexOf(word + " FROM")
                + " unexpected '" + word + "'.", refusalOf(sql), sql);
        }
        final String live = "ALTER STREAMLIT st_alias ADD LIVE VERSION LAST FROM LAST";
        assertEquals("SQL compilation error:\nsyntax error line 1 at position " + live.indexOf("LAST")
            + " unexpected 'LAST'.", refusalOf(live));
        final String rule = ". Version alias must be a snowflake identifier. It cant start with \"version$\", contain "
            + "slashes, or be FIRST, LAST, LIVE or DEFAULT.";
        assertEquals("Invalid version alias VERSION$1" + rule,
            refusalOf("ALTER STREAMLIT st_alias ADD VERSION VERSION$1 FROM '@app_stage'"));
        assertEquals("Invalid version alias VERSION$ABC" + rule,
            refusalOf("ALTER STREAMLIT st_alias ADD VERSION Version$Abc FROM '@app_stage'"));
        assertEquals("Invalid version alias first" + rule,
            refusalOf("ALTER STREAMLIT st_alias ADD VERSION \"first\" FROM '@app_stage'"));
        assertEquals("Invalid version alias a/b" + rule,
            refusalOf("ALTER STREAMLIT st_alias ADD VERSION \"a/b\" FROM '@app_stage'"));
        assertEquals("Invalid version alias LAST" + rule,
            refusalOf("ALTER STREAMLIT st_alias ADD VERSION IF NOT EXISTS \"LAST\" FROM '@app_stage'"));
        // The alias is judged first: before the location, the parameters and the app.
        assertEquals("Invalid version alias VERSION$1" + rule,
            refusalOf("ALTER STREAMLIT st_alias ADD VERSION VERSION$1 FROM 'app_stage' COMMENT = 5"));
        assertEquals("Invalid version alias VERSION$3" + rule,
            refusalOf("ALTER NOTEBOOK no_such_nb ADD LIVE VERSION VERSION$3 FROM LAST"));
        engine.execute("ALTER STREAMLIT st_alias ADD LIVE VERSION lv FROM LAST");
        assertEquals("Invalid version alias DEFAULT" + rule,
            refusalOf("ALTER STREAMLIT st_alias ADD LIVE VERSION \"DEFAULT\" FROM LAST"));
        engine.execute("ALTER STREAMLIT st_alias ABORT");
        // A name the account reads as a plain one is an alias, quoted names with a blank or a dot too.
        engine.execute("ALTER STREAMLIT st_alias ADD VERSION \"a b\" FROM '@app_stage'");
        engine.execute("ALTER STREAMLIT st_alias ADD VERSION \"x.y\" FROM '@app_stage'");
        engine.execute("ALTER STREAMLIT st_alias ADD VERSION versionx FROM '@app_stage'");
        engine.execute("ALTER STREAMLIT st_alias ADD VERSION email FROM '@app_stage'");
        final List<String> aliases = new ArrayList<>();
        for (final String row : versions("SHOW VERSIONS IN STREAMLIT st_alias")) {
            aliases.add(row.split("\\|")[1]);
        }
        assertEquals(List.of("EMAIL", "VERSIONX", "x.y", "a b", "null"), aliases);
    }

    /**
     * A version action's parameter takes a string, quoted either way, or a name; a number or a boolean is an invalid
     * value, refused as the statement compiles — before the app is looked up, and leaving an open live version open.
     */
    @Test
    public void aParameterTakesAStringOrAName() {
        engine.execute("CREATE STREAMLIT st_param");
        engine.execute("ALTER STREAMLIT st_param ADD LIVE VERSION FROM LAST");
        assertEquals("SQL compilation error:\ninvalid value [TRUE] for parameter 'COMMENT'",
            refusalOf("ALTER STREAMLIT st_param COMMIT COMMENT = TRUE"));
        assertEquals("SQL compilation error:\ninvalid value [5] for parameter 'COMMENT'",
            refusalOf("ALTER STREAMLIT st_param COMMIT COMMENT = 5"));
        assertEquals("SQL compilation error:\ninvalid value [1.5] for parameter 'COMMENT'",
            refusalOf("ALTER STREAMLIT st_param COMMIT COMMENT = 1.5"));
        assertEquals("SQL compilation error:\ninvalid value [-1] for parameter 'COMMENT'",
            refusalOf("ALTER STREAMLIT st_param ADD VERSION FROM '@app_stage' COMMENT = -1"));
        assertEquals("SQL compilation error:\ninvalid value [5] for parameter 'USERNAME'",
            refusalOf("ALTER STREAMLIT st_param PUSH USERNAME = 5 PASSWORD = p NAME = n EMAIL = e"));
        assertEquals("SQL compilation error:\ninvalid parameter 'GIT_CREDENTIALS'",
            refusalOf("ALTER STREAMLIT st_param PULL GIT_CREDENTIALS = 5"));
        assertEquals("SQL compilation error:\ninvalid parameter 'FOO'",
            refusalOf("ALTER STREAMLIT st_param COMMIT COMMENT = 'a' FOO = 5"));
        assertEquals("Live version successfully committed.",
            status("ALTER STREAMLIT st_param COMMIT COMMENT = \"Mixed Case\""));
        engine.execute("ALTER STREAMLIT st_param ADD LIVE VERSION FROM LAST");
        engine.execute("ALTER STREAMLIT st_param COMMIT COMMENT = abc.def");
        engine.execute("ALTER STREAMLIT st_param ADD LIVE VERSION FROM LAST");
        engine.execute("ALTER STREAMLIT st_param COMMIT COMMENT = $$dq$$");
        engine.execute("ALTER STREAMLIT st_param ADD VERSION FROM '@app_stage' COMMENT = bare_word");
        final List<String> comments = new ArrayList<>();
        for (final String row : versions("SHOW VERSIONS IN STREAMLIT st_param")) {
            comments.add(row.split("\\|")[4]);
        }
        assertEquals(List.of("bare_word", "dq", "abc.def", "Mixed Case", "null"), comments);
        // The parameters are read as the statement compiles, before the app is looked up.
        assertEquals("SQL compilation error:\ninvalid value [5] for parameter 'COMMENT'",
            refusalOf("ALTER STREAMLIT no_such_st COMMIT COMMENT = 5"));
        assertEquals("SQL compilation error:\ninvalid parameter 'FOO'",
            refusalOf("ALTER STREAMLIT no_such_st PUSH FOO = 'x'"));
        assertEquals("SQL compilation error:\ninvalid URL prefix found in: 'bad'",
            refusalOf("ALTER STREAMLIT no_such_st ADD VERSION v1 FROM 'bad'"));
        assertEquals("SQL compilation error:\ninvalid URL prefix found in: 'bad'",
            refusalOf("ALTER STREAMLIT no_such_st PUSH TO 'bad'"));
    }

    /**
     * SHOW VERSIONS IN NOTEBOOK | STREAMLIT takes a LIMIT and its count and no other modifier; any other SHOW VERSIONS
     * is an unsupported feature, named with the kind of scope it was written with.
     */
    @Test
    public void showVersionsTakesACountAndNothingElse() {
        engine.execute("CREATE STREAMLIT st_list");
        engine.execute("ALTER STREAMLIT st_list ADD VERSION v2 FROM '@app_stage'");
        engine.execute("ALTER STREAMLIT st_list ADD VERSION v3 FROM '@app_stage'");
        assertEquals(List.of("VERSION$3|V3|false|true|null|@app_stage"),
            versions("SHOW VERSIONS IN STREAMLIT st_list LIMIT 1"));
        assertEquals(2, versions("SHOW VERSIONS IN STREAMLIT st_list LIMIT 2").size());
        assertEquals(3, versions("SHOW VERSIONS IN STREAMLIT st_list LIMIT 10").size());
        assertEquals("page size \"0\" must be greater than 0 in limit clause",
            refusalOf("SHOW VERSIONS IN STREAMLIT st_list LIMIT 0"));
        final String[][] faults = {
            {"SHOW VERSIONS IN STREAMLIT st_list LIMIT 1 FROM 'VERSION$1'", "FROM"},
            {"SHOW VERSIONS IN STREAMLIT st_list STARTS WITH 'VERSION$1'", "STARTS"},
            {"SHOW TERSE VERSIONS IN STREAMLIT st_list", "st_list"},
            {"SHOW VERSIONS LIKE 'VERSION$1' IN STREAMLIT st_list", "st_list"},
        };
        for (final String[] fault : faults) {
            assertEquals("SQL compilation error:\nsyntax error line 1 at position " + fault[0].indexOf(fault[1])
                + " unexpected '" + fault[1] + "'.", refusalOf(fault[0]), fault[0]);
        }
        final String noCount = "SHOW VERSIONS IN STREAMLIT st_list LIMIT";
        assertEquals("SQL compilation error:\nsyntax error line 1 at position " + noCount.length()
            + " unexpected '<EOF>'.", refusalOf(noCount));
        assertEquals("Unsupported feature 'SHOW VERSIONS'.", refusalOf("SHOW VERSIONS IN STREAMLIT"));
        assertEquals("Unsupported feature 'SHOW VERSIONS'.", refusalOf("SHOW TERSE VERSIONS"));
        assertEquals("Unsupported feature 'SHOW VERSIONS IN SCHEMA'.",
            refusalOf("SHOW VERSIONS IN SCHEMA test_schema"));
        assertEquals("Unsupported feature 'SHOW VERSIONS IN SCHEMA'.",
            refusalOf("SHOW VERSIONS LIKE 'x' IN SCHEMA test_schema"));
        assertEquals("Unsupported feature 'SHOW VERSIONS IN DATABASE'.",
            refusalOf("SHOW VERSIONS IN DATABASE test_db"));
        assertEquals("Unsupported feature 'SHOW VERSIONS IN ACCOUNT'.", refusalOf("SHOW VERSIONS IN ACCOUNT"));
    }

    /**
     * A PUSH to an app's own location finds no stage of its own there, whatever the app, and says so before it
     * reads the credentials; a stage named by a location that does not exist is named unquoted.
     */
    @Test
    public void pushToAnAppsOwnLocationFindsNoEmbeddedStage() {
        engine.execute("CREATE STREAMLIT st_push");
        engine.execute("CREATE NOTEBOOK nb_push");
        engine.execute("ALTER STREAMLIT st_push ADD VERSION FROM '@app_stage'");
        assertEquals("Streamlit ST_PUSH does not have an embedded stage. Operation aborted.",
            refusalOf("ALTER STREAMLIT st_push PUSH TO 'snow://streamlit/ST_PUSH/versions/live/'"));
        assertEquals("Streamlit ST_PUSH does not have an embedded stage. Operation aborted.",
            refusalOf("ALTER STREAMLIT st_push PUSH TO 'snow://streamlit/st_push/versions/live/' "
                + "GIT_CREDENTIALS = no_such NAME = 'n' EMAIL = 'e'"));
        assertEquals("Streamlit NB_PUSH does not have an embedded stage. Operation aborted.",
            refusalOf("ALTER NOTEBOOK nb_push PUSH TO 'snow://notebook/NB_PUSH'"));
        final String missing = " does not exist or the current role does not have access. Owner of the Streamlit "
            + "must have at least READ on the specified stage.";
        assertEquals("The specified stage Mixed Case" + missing,
            refusalOf("ALTER STREAMLIT st_push ADD VERSION FROM '@\"Mixed Case\"/x'"));
        assertEquals("The specified stage %Mixed" + missing,
            refusalOf("ALTER STREAMLIT st_push ADD VERSION FROM '@%\"Mixed\"/x'"));
        assertEquals("The specified stage stg" + missing,
            refusalOf("ALTER STREAMLIT st_push ADD VERSION FROM @\"stg\"/x"));
    }

    @Test
    public void aLegacyAppHasNoVersions() {
        engine.execute("CREATE STREAMLIT st_legacy ROOT_LOCATION = '@app_stage' MAIN_FILE = 'a.py'");
        for (final String action : new String[] {"ADD LIVE VERSION FROM LAST", "COMMIT", "ABORT", "PUSH", "PULL",
                "ADD VERSION v FROM '@app_stage'"}) {
            assertEquals("Attached stage not exists.", refusalOf("ALTER STREAMLIT st_legacy " + action));
        }
        assertEquals("Attached stage not exists.", refusalOf("SHOW VERSIONS IN STREAMLIT st_legacy"));
        engine.execute("ALTER STREAMLIT st_legacy SET SECRETS = ()");
        engine.execute("ALTER STREAMLIT st_legacy UNSET SECRETS");
    }

    // ── Git ───────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    public void theGitActionsRefuseWithoutARepository() {
        engine.execute("CREATE STREAMLIT st_git");
        engine.execute("CREATE NOTEBOOK nb_git");
        assertEquals(NO_LINEAGE, refusalOf("ALTER STREAMLIT st_git PUSH"));
        assertEquals(NO_LINEAGE, refusalOf("ALTER STREAMLIT st_git PULL"));
        assertEquals(NO_LINEAGE, refusalOf("ALTER NOTEBOOK nb_git PUSH"));
        assertEquals(NO_LINEAGE, refusalOf("ALTER NOTEBOOK nb_git PULL"));
        engine.execute("ALTER STREAMLIT st_git ADD VERSION FROM '@app_stage'");
        assertEquals(NOT_FROM_GIT, refusalOf("ALTER STREAMLIT st_git PUSH"));
        assertEquals(NOT_FROM_GIT, refusalOf("ALTER STREAMLIT st_git PUSH NAME = 'n' EMAIL = 'e@x.com'"));
        assertEquals(NOT_FROM_GIT, refusalOf("ALTER STREAMLIT st_git PUSH GIT_CREDENTIALS = git_secret "
            + "NAME = 'n' EMAIL = 'e@x.com' COMMENT = 'c'"));
        assertEquals(NOT_FROM_GIT, refusalOf("ALTER STREAMLIT st_git PULL COMMENT = 'c'"));
        // The credentials and the author: all three or none, never both kinds of credentials.
        assertEquals(THREE_SETTINGS, refusalOf("ALTER STREAMLIT st_git PUSH GIT_CREDENTIALS = git_secret"));
        assertEquals(THREE_SETTINGS, refusalOf("ALTER STREAMLIT st_git PUSH NAME = 'n'"));
        assertEquals(THREE_SETTINGS, refusalOf("ALTER STREAMLIT st_git PUSH USERNAME = 'u' NAME = 'n' EMAIL = 'e'"));
        assertEquals("Invalid property list: GIT_CREDENTIALS and USERNAME&PASSWORD pair cannot both be present.",
            refusalOf("ALTER STREAMLIT st_git PUSH USERNAME = 'u' PASSWORD = 'p' GIT_CREDENTIALS = git_secret "
                + "NAME = 'n' EMAIL = 'e'"));
        assertEquals("Invalid property list: GIT_CREDENTIALS must be a snowflake secret of PASSWORD type.",
            refusalOf("ALTER STREAMLIT st_git PUSH GIT_CREDENTIALS = plain_secret NAME = 'n' EMAIL = 'e'"));
        assertEquals(hinted("SQL compilation error:\nSecret 'TEST_DB.TEST_SCHEMA.NO_SUCH' does not exist or not "
            + "authorized."), refusalOf("ALTER STREAMLIT st_git PUSH GIT_CREDENTIALS = no_such NAME = 'n' EMAIL = 'e'"));
        // The branch is read first.
        assertEquals("Invalid git branch path: The path speicifed does not point to a git repo.",
            refusalOf("ALTER STREAMLIT st_git PUSH TO '@app_stage/branches/main' GIT_CREDENTIALS = git_secret"));
        assertEquals("Unsupported feature 'stage container type: git-repository'.",
            refusalOf("ALTER STREAMLIT st_git PUSH TO 'snow://git-repository/db.s.repo/branches/main' "
                + "USERNAME = 'u' NAME = 'n'"));
        assertEquals("SQL compilation error:\ninvalid URL prefix found in: 'https://example.com/x.git'",
            refusalOf("ALTER STREAMLIT st_git PUSH TO 'https://example.com/x.git'"));
        assertEquals("SQL compilation error:\ninvalid parameter 'FOO'", refusalOf("ALTER STREAMLIT st_git PUSH FOO = 'x'"));
        assertEquals("SQL compilation error:\ninvalid parameter 'GIT_CREDENTIALS'",
            refusalOf("ALTER STREAMLIT st_git PULL GIT_CREDENTIALS = git_secret"));
        assertTrue(refusalOf("ALTER STREAMLIT st_git PUSH COMMENT = 'c' TO '@app_stage'").contains("syntax error"));
        assertTrue(refusalOf("ALTER STREAMLIT IF EXISTS st_git PUSH").contains("syntax error"));
        // PULL wants the live version closed first.
        engine.execute("ALTER STREAMLIT st_git ADD LIVE VERSION FROM LAST");
        assertEquals("There is already a live version. Please commit it first.",
            refusalOf("ALTER STREAMLIT st_git PULL"));
        assertEquals(NOT_FROM_GIT, refusalOf("ALTER STREAMLIT st_git PUSH"));
        assertEquals(hinted("SQL compilation error:\nStreamlit 'TEST_DB.TEST_SCHEMA.NO_SUCH_ST' does not exist or "
            + "not authorized."), refusalOf("ALTER STREAMLIT no_such_st PUSH"));
    }

    // ── EXECUTE NOTEBOOK ──────────────────────────────────────────────────────────────────────────────────────

    @Test
    public void executeNotebookNeedsAWarehouseAndALiveVersion() {
        engine.execute("CREATE NOTEBOOK nb_exec");
        final String noWarehouse =
            "To execute the notebook, set the Query warehouse and the Notebook warehouse / Compute pool.";
        assertEquals(noWarehouse, refusalOf("EXECUTE NOTEBOOK nb_exec()"));
        assertEquals(noWarehouse, refusalOf("EXECUTE NOTEBOOK nb_exec"));
        assertEquals(noWarehouse, refusalOf("EXECUTE NOTEBOOK test_schema.nb_exec('a', 1 + 1, UPPER('b'), NULL)"));
        assertEquals(hinted("SQL compilation error:\nNotebook 'TEST_DB.TEST_SCHEMA.NO_SUCH_NB' does not exist or not "
            + "authorized."), refusalOf("EXECUTE NOTEBOOK no_such_nb()"));
        assertTrue(refusalOf("EXECUTE NOTEBOOK nb_exec(,)").contains("syntax error"));
        engine.execute("ALTER NOTEBOOK nb_exec SET QUERY_WAREHOUSE = fl_act_wh");
        assertEquals("Live version is not found.", refusalOf("EXECUTE NOTEBOOK nb_exec()"));
        engine.execute("ALTER NOTEBOOK nb_exec ADD LIVE VERSION FROM LAST");
        if (!isLiveSnowflake()) {
            // Live, this runs the notebook on the warehouse; the answer is the same flat sentence.
            assertEquals("Statement executed successfully.", status("EXECUTE NOTEBOOK nb_exec('x')"));
            assertNull(described("DESCRIBE NOTEBOOK nb_exec", "last_version_alias"));
        }
    }
}
