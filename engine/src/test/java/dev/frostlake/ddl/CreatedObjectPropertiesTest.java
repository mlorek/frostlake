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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The {@code name = value} properties a CREATE TABLE, CREATE VIEW and CREATE SEQUENCE are written with: a name
 * that is no property is refused before the statement's objects are looked at, a value of another type as it is
 * written, and a parameter of another level by name. A word that assigns nothing is read as a property's name, so
 * the fault lies after it.
 */
public class CreatedObjectPropertiesTest extends BaseDatabaseTest {

    private static final String COMPILATION = "SQL compilation error:\n";
    private static final String SYNTAX = COMPILATION + "syntax error line 1 at position ";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE cop_t (a INT)");
    }

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private void refused(final String expected, final String sql) {
        assertEquals(expected, refusalOf(sql), sql);
    }

    private static String invalidProperty(final String name, final String kind) {
        return COMPILATION + "invalid property '" + name + "' for '" + kind + "'";
    }

    private static String invalidValue(final String value, final String parameter) {
        return COMPILATION + "invalid value [" + value + "] for parameter '" + parameter + "'";
    }

    private static String cannotSet(final String parameter, final String kind) {
        return COMPILATION + "cannot set parameter '" + parameter + "' at '" + kind + "'";
    }

    @Test
    public void aTablePropertyOfNoKnownNameIsRefusedAsWritten() {
        final String unknown = invalidProperty("x", "TABLE");
        refused(unknown, "CREATE OR REPLACE TABLE cop_new (a INT) x = 1");
        refused(unknown, "CREATE OR REPLACE TABLE cop_new (a INT) x = 'str'");
        refused(unknown, "CREATE OR REPLACE TABLE cop_new (a INT) x = abc");
        refused(unknown, "CREATE OR REPLACE TABLE cop_new (a INT) x = TRUE");
        refused(unknown, "CREATE OR REPLACE TABLE cop_new (a INT) x = -1");
        refused(invalidProperty("\"x\"", "TABLE"), "CREATE OR REPLACE TABLE cop_new (a INT) \"x\" = 1");
        refused(invalidProperty("FOO", "TABLE"), "CREATE OR REPLACE TABLE cop_new (a INT) FOO = 1");
        refused(unknown, "CREATE OR REPLACE TEMPORARY TABLE cop_new (a INT) x = 1");
        refused(unknown, "CREATE OR REPLACE TRANSIENT TABLE cop_new (a INT) x = 1");
        refused(unknown, "CREATE OR REPLACE ICEBERG TABLE cop_new (a VARIANT) x = 1");
        refused(unknown, "CREATE OR REPLACE TABLE cop_new LIKE cop_t x = 1");
        refused(unknown, "CREATE OR REPLACE TABLE cop_new CLONE cop_t x = 1");
        refused(unknown, "CREATE OR REPLACE TABLE cop_new (a INT) x = 1 AS SELECT 1");
        refused(unknown, "CREATE OR REPLACE TABLE cop_new x = 1 AS SELECT 1 a");
        refused(unknown, "CREATE OR REPLACE TABLE cop_new x = 1 (a INT)");
        refused(invalidProperty("foo", "TABLE"), "CREATE OR REPLACE TABLE cop_new (x) foo = 1 AS SELECT 1");
    }

    @Test
    public void anUnknownTablePropertyIsRefusedBeforeTheStatementsObjectsAndItsOtherProperties() {
        final String unknown = invalidProperty("x", "TABLE");
        // The name is taken, the schema is not there, the copied table is not there.
        refused(unknown, "CREATE TABLE cop_t (a INT) x = 1");
        refused(unknown, "CREATE TABLE IF NOT EXISTS cop_t (a INT) x = 1");
        refused(unknown, "CREATE OR REPLACE TABLE cop_nosuch_schema.cop_new (a INT) x = 1");
        refused(unknown, "CREATE OR REPLACE TABLE cop_new LIKE cop_nosuch x = 1");
        // A property with another fault, written before the unknown one or after it.
        refused(unknown, "CREATE OR REPLACE TABLE cop_new (a INT) COMMENT = 'c' x = 1");
        refused(unknown, "CREATE OR REPLACE TABLE cop_new (a INT) x = 1 COMMENT = 'c'");
        refused(unknown, "CREATE OR REPLACE TABLE cop_new (a INT) CHANGE_TRACKING = 5 x = 1");
        refused(unknown, "CREATE OR REPLACE TABLE cop_new (a INT) x = 1 CHANGE_TRACKING = 5");
        refused(unknown, "CREATE OR REPLACE TABLE cop_new (a INT) TIMEZONE = 'UTC' x = 1");
        refused(unknown, "CREATE OR REPLACE TABLE cop_new (a INT) x = 1 TIMEZONE = 'UTC'");
        // A value of another type is refused ahead of a parameter of another level.
        refused(invalidValue("5", "CHANGE_TRACKING"),
            "CREATE OR REPLACE TABLE cop_new (a INT) CHANGE_TRACKING = 5 TIMEZONE = 'UTC'");
        refused(invalidValue("5", "CHANGE_TRACKING"),
            "CREATE OR REPLACE TABLE cop_new (a INT) TIMEZONE = 'UTC' CHANGE_TRACKING = 5");
    }

    @Test
    public void aPropertyWrittenTwiceIsRefusedFirstOfAll() {
        refused(COMPILATION + "duplicate property 'x';", "CREATE OR REPLACE TABLE cop_new (a INT) x = 1 x = 2");
        refused(COMPILATION + "duplicate property 'CHANGE_TRACKING';",
            "CREATE OR REPLACE TABLE cop_new (a INT) CHANGE_TRACKING = TRUE CHANGE_TRACKING = TRUE x = 1");
        refused(COMPILATION + "duplicate property 'x';", "CREATE OR REPLACE SEQUENCE cop_seq x = 1 x = 1");
    }

    @Test
    public void aTablePropertysValueIsOfItsType() {
        for (final String value : new String[] {"5", "1", "'TRUE'", "'x'", "abc", "-1", "''"}) {
            refused(invalidValue(value, "CHANGE_TRACKING"),
                "CREATE OR REPLACE TABLE cop_new (a INT) CHANGE_TRACKING = " + value);
            refused(invalidValue(value, "ENABLE_SCHEMA_EVOLUTION"),
                "CREATE OR REPLACE TABLE cop_new (a INT) ENABLE_SCHEMA_EVOLUTION = " + value);
        }
        for (final String value : new String[] {"TRUE", "'TRUE'", "'x'", "abc", "-1", "''"}) {
            refused(invalidValue(value, "DATA_RETENTION_TIME_IN_DAYS"),
                "CREATE OR REPLACE TABLE cop_new (a INT) DATA_RETENTION_TIME_IN_DAYS = " + value);
            refused(invalidValue(value, "MAX_DATA_EXTENSION_TIME_IN_DAYS"),
                "CREATE OR REPLACE TABLE cop_new (a INT) MAX_DATA_EXTENSION_TIME_IN_DAYS = " + value);
        }
        for (final String value : new String[] {"TRUE", "1", "5", "-1"}) {
            refused(invalidValue(value, "DEFAULT_DDL_COLLATION"),
                "CREATE OR REPLACE TABLE cop_new (a INT) DEFAULT_DDL_COLLATION = " + value);
            refused(invalidValue(value, "COMMENT"), "CREATE OR REPLACE TABLE cop_new (a INT) COMMENT = " + value);
        }
        refused(invalidValue("1", "ERROR_LOGGING"), "CREATE OR REPLACE TABLE cop_new (a INT) ERROR_LOGGING = 1");
        refused(invalidValue("1", "BASE_LOCATION"), "CREATE OR REPLACE TABLE cop_new (a INT) BASE_LOCATION = 1");
        // A parameter of another level is judged for its value's type before its level.
        refused(invalidValue("1", "TIMEZONE"), "CREATE OR REPLACE TABLE cop_new (a INT) TIMEZONE = 1");
    }

    @Test
    public void aTableTakesItsOwnPropertiesAndParameters() {
        for (final String property : new String[] {"CHANGE_TRACKING = TRUE", "ENABLE_SCHEMA_EVOLUTION = TRUE",
            "DATA_RETENTION_TIME_IN_DAYS = 1", "MAX_DATA_EXTENSION_TIME_IN_DAYS = 5", "DEFAULT_DDL_COLLATION = 'en'",
            "COMMENT = 'c'", "ERROR_LOGGING = DEFAULT", "ERROR_LOGGING = TRUE", "ROW_TIMESTAMP = TRUE",
            "DATA_METRIC_SCHEDULE = '5 MINUTE'", "STAGE_FILE_FORMAT = (TYPE = CSV)",
            "STAGE_COPY_OPTIONS = (ON_ERROR = 'SKIP_FILE')"}) {
            engine.execute("CREATE OR REPLACE TABLE cop_new (a INT) " + property);
        }
        engine.execute("CREATE OR REPLACE TABLE cop_new LIKE cop_t COMMENT = 'c' CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE TABLE cop_new COMMENT = 'c' LIKE cop_t");
        engine.execute("CREATE OR REPLACE TABLE cop_new CLONE cop_t COMMENT = 'c' CHANGE_TRACKING = TRUE");
        engine.execute("CREATE OR REPLACE TABLE cop_new COMMENT = 'c' CLONE cop_t");
    }

    @Test
    public void aParameterOfAnotherLevelCannotBeSetOnATable() {
        refused(cannotSet("TIMEZONE", "TABLE"), "CREATE OR REPLACE TABLE cop_new (a INT) TIMEZONE = 'UTC'");
        refused(cannotSet("QUERY_TAG", "TABLE"), "CREATE OR REPLACE TABLE cop_new (a INT) QUERY_TAG = 'q'");
        refused(cannotSet("LOCK_TIMEOUT", "TABLE"), "CREATE OR REPLACE TABLE cop_new (a INT) LOCK_TIMEOUT = 1");
        refused(cannotSet("AUTOCOMMIT", "TABLE"), "CREATE OR REPLACE TABLE cop_new (a INT) AUTOCOMMIT = TRUE");
        // What another kind of table is given is a feature a plain table has not.
        refused("Unsupported feature 'AUTO_REFRESH'.", "CREATE OR REPLACE TABLE cop_new (a INT) AUTO_REFRESH = 1");
        refused("Unsupported feature 'LOCATION'.", "CREATE OR REPLACE TABLE cop_new (a INT) LOCATION = 1");
        refused("Unsupported feature 'PATTERN'.", "CREATE OR REPLACE TABLE cop_new (a INT) PATTERN = 1");
        refused(COMPILATION + "Iceberg table options (e.g. BASE_LOCATION) require"
            + " DEFAULT_METADATA_WRITE_FORMAT=ICEBERG or CREATE ICEBERG TABLE",
            "CREATE OR REPLACE TABLE cop_new (a INT) BASE_LOCATION = 'x'");
    }

    @Test
    public void aWordThatAssignsNothingIsReadAsAPropertysName() {
        refused(SYNTAX + "42 unexpected 'y'.", "CREATE OR REPLACE TABLE cop_new (a INT) x y");
        refused(SYNTAX + "41 unexpected '<EOF>'.", "CREATE OR REPLACE TABLE cop_new (a INT) x");
        refused(SYNTAX + "43 unexpected '<EOF>'.", "CREATE OR REPLACE TABLE cop_new (a INT) x =");
        refused(SYNTAX + "41 unexpected '.'.", "CREATE OR REPLACE TABLE cop_new (a INT) x.y = 1");
        refused(SYNTAX + "44 unexpected '<EOF>'.", "CREATE OR REPLACE TABLE cop_new LIKE cop_t x");
        refused(SYNTAX + "45 unexpected '<EOF>'.", "CREATE OR REPLACE TABLE cop_new CLONE cop_t x");
        refused(SYNTAX + "36 unexpected '<EOF>'.", "CREATE OR REPLACE SEQUENCE cop_seq x");
        refused(SYNTAX + "37 unexpected 'bar'.", "CREATE OR REPLACE SEQUENCE cop_s foo bar");
        refused(SYNTAX + "43 unexpected 'x'.", "CREATE OR REPLACE SEQUENCE cop_seq START 1 x");
        // After an option a word is refused where it stands; after a comma it is read as a property's name.
        refused(SYNTAX + "43 unexpected 'x'.", "CREATE OR REPLACE SEQUENCE cop_seq START 1 x y");
        refused(SYNTAX + "41 unexpected 'y'.", "CREATE OR REPLACE SEQUENCE cop_seq x = 1 y");
        refused(SYNTAX + "45 unexpected '<EOF>'.", "CREATE OR REPLACE SEQUENCE cop_seq START 1, x");
        refused(SYNTAX + "49 unexpected 'x'.", "CREATE OR REPLACE SEQUENCE cop_seq COMMENT = 'c' x");
        refused(SYNTAX + "33 unexpected '<EOF>'.", "CREATE OR REPLACE VIEW cop_view x");
        refused(SYNTAX + "34 unexpected 'y'.", "CREATE OR REPLACE VIEW cop_view x y AS SELECT 1 a");
    }

    @Test
    public void aViewsUnknownPropertyIsNamedATablesAndItsParametersAreRefusedByLevel() {
        final String unknown = invalidProperty("x", "TABLE");
        refused(unknown, "CREATE OR REPLACE VIEW cop_view x = 1 AS SELECT 1 a");
        refused(unknown, "CREATE OR REPLACE VIEW cop_view (a) x = 1 AS SELECT 1 a");
        refused(unknown, "CREATE OR REPLACE SECURE VIEW cop_view x = 1 AS SELECT 1 a");
        refused(unknown, "CREATE OR REPLACE VIEW cop_view COMMENT = 'c' x = 1 AS SELECT 1 a");
        refused(unknown, "CREATE OR REPLACE VIEW cop_view COPY GRANTS x = 1 AS SELECT 1 a");
        refused(invalidProperty("\"x\"", "TABLE"), "CREATE OR REPLACE VIEW cop_view \"x\" = 1 AS SELECT 1 a");
        refused(cannotSet("TIMEZONE", "VIEW"), "CREATE OR REPLACE VIEW cop_view TIMEZONE = 'UTC' AS SELECT 1 a");
        refused(cannotSet("AUTOCOMMIT", "VIEW"), "CREATE OR REPLACE VIEW cop_view AUTOCOMMIT = TRUE AS SELECT 1 a");
        refused(invalidValue("5", "CHANGE_TRACKING"),
            "CREATE OR REPLACE VIEW cop_view CHANGE_TRACKING = 5 AS SELECT 1 a");
        // A table's own properties: two it does not have, and one that does not go with it.
        refused(invalidProperty("ERROR_LOGGING", "VIEW"),
            "CREATE OR REPLACE VIEW cop_view ERROR_LOGGING = TRUE AS SELECT 1 a");
        refused(invalidProperty("ROW_TIMESTAMP", "VIEW"),
            "CREATE OR REPLACE VIEW cop_view ROW_TIMESTAMP = TRUE AS SELECT 1 a");
        refused(COMPILATION + "options ENABLE_SCHEMA_EVOLUTION and {1} are incompatible.",
            "CREATE OR REPLACE VIEW cop_view ENABLE_SCHEMA_EVOLUTION = TRUE AS SELECT 1 a");
        // A table's parameters are refused as a schema the view may not be made in.
        final String schema = "SQL access control error:\nInsufficient privileges to operate on schema 'TEST_SCHEMA'.";
        refused(schema, "CREATE OR REPLACE VIEW cop_view DATA_RETENTION_TIME_IN_DAYS = 1 AS SELECT 1 a");
        refused(schema, "CREATE OR REPLACE VIEW cop_view MAX_DATA_EXTENSION_TIME_IN_DAYS = 1 AS SELECT 1 a");
        refused(schema, "CREATE OR REPLACE VIEW cop_view DEFAULT_DDL_COLLATION = 'en' AS SELECT 1 a");
        // What a view does take.
        engine.execute("CREATE OR REPLACE VIEW cop_view COMMENT = 'c' AS SELECT a FROM cop_t");
        engine.execute("CREATE OR REPLACE VIEW cop_view CHANGE_TRACKING = TRUE AS SELECT a FROM cop_t");
        engine.execute("CREATE OR REPLACE VIEW cop_view DATA_METRIC_SCHEDULE = '5 MINUTE' AS SELECT a FROM cop_t");
    }

    @Test
    public void aSequenceTakesNoPropertyButItsOwn() {
        refused(invalidProperty("foo", "SEQUENCE"), "CREATE OR REPLACE SEQUENCE cop_seq foo = 1");
        refused(invalidProperty("FOO", "SEQUENCE"), "CREATE OR REPLACE SEQUENCE cop_seq START = 1 FOO = 1");
        refused(invalidProperty("x", "SEQUENCE"), "CREATE OR REPLACE SEQUENCE cop_seq START = 1 x = 1 INCREMENT = 1");
        refused(invalidProperty("\"foo\"", "SEQUENCE"), "CREATE OR REPLACE SEQUENCE cop_seq \"foo\" = 1");
        refused(invalidProperty("CHANGE_TRACKING", "SEQUENCE"),
            "CREATE OR REPLACE SEQUENCE cop_seq CHANGE_TRACKING = TRUE");
        refused(invalidProperty("ERROR_LOGGING", "SEQUENCE"), "CREATE OR REPLACE SEQUENCE cop_seq ERROR_LOGGING = TRUE");
        refused(cannotSet("TIMEZONE", "SEQUENCE"), "CREATE OR REPLACE SEQUENCE cop_seq TIMEZONE = 'UTC'");
        refused(cannotSet("DATA_RETENTION_TIME_IN_DAYS", "SEQUENCE"),
            "CREATE OR REPLACE SEQUENCE cop_seq DATA_RETENTION_TIME_IN_DAYS = 1");
        refused(cannotSet("DATA_METRIC_SCHEDULE", "SEQUENCE"),
            "CREATE OR REPLACE SEQUENCE cop_seq DATA_METRIC_SCHEDULE = '5 MINUTE'");
        engine.execute("CREATE OR REPLACE SEQUENCE cop_seq COMMENT = 'c' START = 1 INCREMENT = 1 ORDER");
    }
}
