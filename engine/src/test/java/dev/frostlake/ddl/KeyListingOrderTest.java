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

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * The order of SHOW IMPORTED KEYS and SHOW EXPORTED KEYS (live-verified): an imported key is ordered by the column it
 * references, that column's database, schema and table first, and an exported one by the column that references, so a
 * key of several columns lists its columns by name. Keys referencing one column come in no order the account fixes, so
 * a listing holding such keys is read by the columns that order it, and once more in an order of its own. A key
 * listing's schema named by one part is refused as a ->> chain compiles, bare at whichever stage it stands.
 */
public class KeyListingOrderTest extends MeasuredCellsTestSupport {

    @Test
    public void importedAndExportedKeysAreOrderedByTheirSidesColumns() {
        engine.execute("CREATE OR REPLACE TABLE pk901a (id INT PRIMARY KEY)");
        engine.execute("CREATE OR REPLACE TABLE pk901b (id INT PRIMARY KEY)");
        engine.execute("CREATE OR REPLACE TABLE pk901c (id INT PRIMARY KEY)");
        engine.execute("CREATE OR REPLACE TABLE fk901y (a INT, b INT, CONSTRAINT zz FOREIGN KEY (a) REFERENCES "
            + "pk901a (id), CONSTRAINT aa FOREIGN KEY (b) REFERENCES pk901b (id))");
        engine.execute("CREATE OR REPLACE TABLE fk901x (c INT, b INT, a INT, CONSTRAINT k1 FOREIGN KEY (c) "
            + "REFERENCES pk901a (id), CONSTRAINT k2 FOREIGN KEY (b) REFERENCES pk901c (id), CONSTRAINT k3 "
            + "FOREIGN KEY (a) REFERENCES pk901b (id))");
        engine.execute("CREATE OR REPLACE TABLE fk901w (q INT REFERENCES pk901c (id), p INT REFERENCES pk901a (id))");
        assertCells(new String[][] {
            {"SHOW IMPORTED KEYS IN TABLE fk901y ->> SELECT \"pk_table_name\", \"fk_table_name\", "
                + "\"fk_column_name\", \"fk_name\" FROM $1",
                "PK901A|FK901Y|A|ZZ ;; PK901B|FK901Y|B|AA"},
            {"SHOW IMPORTED KEYS IN TABLE fk901x ->> SELECT \"pk_table_name\", \"fk_table_name\", "
                + "\"fk_column_name\", \"fk_name\" FROM $1",
                "PK901A|FK901X|C|K1 ;; PK901B|FK901X|A|K3 ;; PK901C|FK901X|B|K2"},
            {"SHOW IMPORTED KEYS IN TABLE fk901w ->> SELECT \"pk_table_name\", \"fk_column_name\" FROM $1",
                "PK901A|P ;; PK901C|Q"},
            {"SHOW IMPORTED KEYS IN SCHEMA test_db.test_schema ->> SELECT \"pk_table_name\" FROM $1 WHERE "
                + "\"fk_table_name\" LIKE 'FK901%'",
                "PK901A ;; PK901A ;; PK901A ;; PK901B ;; PK901B ;; PK901C ;; PK901C"},
            {"SHOW IMPORTED KEYS IN SCHEMA test_db.test_schema ->> SELECT \"pk_table_name\", \"fk_table_name\", "
                + "\"fk_column_name\" FROM $1 WHERE \"fk_table_name\" LIKE 'FK901%' ORDER BY 1, 2, 3",
                "PK901A|FK901W|P ;; PK901A|FK901X|C ;; PK901A|FK901Y|A ;; PK901B|FK901X|A ;; PK901B|FK901Y|B ;; "
                + "PK901C|FK901W|Q ;; PK901C|FK901X|B"},
        });
        assertShapedAnswer("FK901W|P|SYS_CONSTRAINT_<QID> ;; FK901X|C|K1 ;; FK901Y|A|ZZ",
            "SHOW EXPORTED KEYS IN TABLE pk901a ->> SELECT \"fk_table_name\", \"fk_column_name\", "
                + "\"fk_name\" FROM $1");
        assertCells(new String[][] {
            {"SHOW EXPORTED KEYS IN SCHEMA test_db.test_schema ->> SELECT \"pk_table_name\", \"fk_table_name\", "
                + "\"fk_column_name\" FROM $1 WHERE \"fk_table_name\" LIKE 'FK901%'",
                "PK901A|FK901W|P ;; PK901C|FK901W|Q ;; PK901B|FK901X|A ;; PK901C|FK901X|B ;; PK901A|FK901X|C ;; "
                + "PK901A|FK901Y|A ;; PK901B|FK901Y|B"},
            {"SHOW IMPORTED KEYS IN SCHEMA test_schema",
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "TEST_SCHEMA"},
            {"SHOW IMPORTED KEYS IN SCHEMA nosuch901",
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "NOSUCH901"},
            {"SHOW IMPORTED KEYS IN SCHEMA test_schema ->> SELECT 1",
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "TEST_SCHEMA"},
            {"SHOW TABLES IN SCHEMA test_schema ->> SELECT 1", "1"},
            {"SHOW PRIMARY KEYS IN SCHEMA test_schema ->> SELECT 1",
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "TEST_SCHEMA"},
            {"SHOW UNIQUE KEYS IN SCHEMA test_schema ->> SELECT 1",
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "TEST_SCHEMA"},
            {"SHOW EXPORTED KEYS IN SCHEMA test_schema ->> SELECT 1",
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "TEST_SCHEMA"},
        });
    }

    @Test
    public void keysOfSeveralTablesAndColumns() {
        engine.execute("CREATE OR REPLACE TABLE pk902x (id INT PRIMARY KEY)");
        engine.execute("CREATE OR REPLACE TABLE pk902y (id INT PRIMARY KEY)");
        engine.execute("CREATE OR REPLACE TABLE pk902m (a INT, b INT, PRIMARY KEY (a, b))");
        engine.execute("CREATE OR REPLACE TABLE fk902b (s INT, t INT, CONSTRAINT q2 FOREIGN KEY (t) REFERENCES "
            + "pk902x (id))");
        engine.execute("CREATE OR REPLACE TABLE fk902c (u INT, CONSTRAINT q1 FOREIGN KEY (u) REFERENCES pk902x (id))");
        engine.execute("CREATE OR REPLACE TABLE fk902a (q INT, p INT, CONSTRAINT c2 FOREIGN KEY (q) REFERENCES "
            + "pk902x (id))");
        engine.execute("ALTER TABLE fk902a ADD CONSTRAINT c1 FOREIGN KEY (p) REFERENCES pk902x (id)");
        engine.execute("ALTER TABLE fk902b ADD CONSTRAINT q0 FOREIGN KEY (s) REFERENCES pk902y (id)");
        engine.execute("CREATE OR REPLACE TABLE fk902m (y INT, x INT, z INT, CONSTRAINT m1 FOREIGN KEY (y, x) "
            + "REFERENCES pk902m (a, b), CONSTRAINT m0 FOREIGN KEY (z) REFERENCES pk902x (id))");
        assertCells(new String[][] {
            {"SHOW IMPORTED KEYS IN SCHEMA test_db.test_schema ->> SELECT \"pk_table_name\", \"pk_column_name\" "
                + "FROM $1 WHERE \"fk_table_name\" LIKE 'FK902%'",
                "PK902M|A ;; PK902M|B ;; PK902X|ID ;; PK902X|ID ;; PK902X|ID ;; PK902X|ID ;; PK902X|ID ;; PK902Y|ID"},
            {"SHOW IMPORTED KEYS IN SCHEMA test_db.test_schema ->> SELECT \"pk_table_name\", "
                + "\"pk_column_name\", \"fk_table_name\", \"fk_column_name\", \"key_sequence\", \"fk_name\" FROM $1 "
                + "WHERE \"fk_table_name\" LIKE 'FK902%' ORDER BY 1, 2, 3, 4, 5, 6",
                "PK902M|A|FK902M|Y|1|M1 ;; PK902M|B|FK902M|X|2|M1 ;; PK902X|ID|FK902A|P|1|C1 ;; "
                + "PK902X|ID|FK902A|Q|1|C2 ;; PK902X|ID|FK902B|T|1|Q2 ;; PK902X|ID|FK902C|U|1|Q1 ;; "
                + "PK902X|ID|FK902M|Z|1|M0 ;; PK902Y|ID|FK902B|S|1|Q0"},
            {"SHOW EXPORTED KEYS IN SCHEMA test_db.test_schema ->> SELECT \"pk_table_name\", "
                + "\"pk_column_name\", \"fk_table_name\", \"fk_column_name\", \"key_sequence\", \"fk_name\" FROM $1 "
                + "WHERE \"fk_table_name\" LIKE 'FK902%'",
                "PK902X|ID|FK902A|P|1|C1 ;; PK902X|ID|FK902A|Q|1|C2 ;; PK902Y|ID|FK902B|S|1|Q0 ;; "
                + "PK902X|ID|FK902B|T|1|Q2 ;; PK902X|ID|FK902C|U|1|Q1 ;; PK902M|B|FK902M|X|2|M1 ;; "
                + "PK902M|A|FK902M|Y|1|M1 ;; PK902X|ID|FK902M|Z|1|M0"},
            {"SHOW IMPORTED KEYS IN TABLE fk902a ->> SELECT \"pk_table_name\", \"pk_column_name\" FROM $1",
                "PK902X|ID ;; PK902X|ID"},
            {"SHOW IMPORTED KEYS IN TABLE fk902a ->> SELECT \"pk_table_name\", \"pk_column_name\", "
                + "\"fk_table_name\", \"fk_column_name\", \"key_sequence\", \"fk_name\" FROM $1 ORDER BY 1, 2, 3, 4, "
                + "5, 6",
                "PK902X|ID|FK902A|P|1|C1 ;; PK902X|ID|FK902A|Q|1|C2"},
            {"SHOW IMPORTED KEYS IN TABLE fk902b ->> SELECT \"pk_table_name\", \"pk_column_name\", "
                + "\"fk_table_name\", \"fk_column_name\", \"key_sequence\", \"fk_name\" FROM $1",
                "PK902X|ID|FK902B|T|1|Q2 ;; PK902Y|ID|FK902B|S|1|Q0"},
            {"SHOW IMPORTED KEYS IN TABLE fk902m ->> SELECT \"pk_table_name\", \"pk_column_name\", "
                + "\"fk_table_name\", \"fk_column_name\", \"key_sequence\", \"fk_name\" FROM $1",
                "PK902M|A|FK902M|Y|1|M1 ;; PK902M|B|FK902M|X|2|M1 ;; PK902X|ID|FK902M|Z|1|M0"},
            {"SHOW EXPORTED KEYS IN TABLE pk902x ->> SELECT \"pk_table_name\", \"pk_column_name\", "
                + "\"fk_table_name\", \"fk_column_name\", \"key_sequence\", \"fk_name\" FROM $1",
                "PK902X|ID|FK902A|P|1|C1 ;; PK902X|ID|FK902A|Q|1|C2 ;; PK902X|ID|FK902B|T|1|Q2 ;; "
                + "PK902X|ID|FK902C|U|1|Q1 ;; PK902X|ID|FK902M|Z|1|M0"},
            {"SHOW EXPORTED KEYS IN TABLE pk902m ->> SELECT \"pk_table_name\", \"pk_column_name\", "
                + "\"fk_table_name\", \"fk_column_name\", \"key_sequence\", \"fk_name\" FROM $1",
                "PK902M|B|FK902M|X|2|M1 ;; PK902M|A|FK902M|Y|1|M1"},
            {"SHOW IMPORTED KEYS IN DATABASE test_db ->> SELECT \"pk_table_name\", \"pk_column_name\" FROM $1 "
                + "WHERE \"fk_table_name\" LIKE 'FK902%'",
                "PK902M|A ;; PK902M|B ;; PK902X|ID ;; PK902X|ID ;; PK902X|ID ;; PK902X|ID ;; PK902X|ID ;; PK902Y|ID"},
            {"SHOW IMPORTED KEYS IN DATABASE test_db ->> SELECT \"pk_table_name\", \"pk_column_name\", "
                + "\"fk_table_name\", \"fk_column_name\", \"key_sequence\", \"fk_name\" FROM $1 WHERE "
                + "\"fk_table_name\" LIKE 'FK902%' ORDER BY 1, 2, 3, 4, 5, 6",
                "PK902M|A|FK902M|Y|1|M1 ;; PK902M|B|FK902M|X|2|M1 ;; PK902X|ID|FK902A|P|1|C1 ;; "
                + "PK902X|ID|FK902A|Q|1|C2 ;; PK902X|ID|FK902B|T|1|Q2 ;; PK902X|ID|FK902C|U|1|Q1 ;; "
                + "PK902X|ID|FK902M|Z|1|M0 ;; PK902Y|ID|FK902B|S|1|Q0"},
            {"SHOW EXPORTED KEYS IN DATABASE test_db ->> SELECT \"pk_table_name\", \"pk_column_name\", "
                + "\"fk_table_name\", \"fk_column_name\", \"key_sequence\", \"fk_name\" FROM $1 WHERE "
                + "\"fk_table_name\" LIKE 'FK902%'",
                "PK902X|ID|FK902A|P|1|C1 ;; PK902X|ID|FK902A|Q|1|C2 ;; PK902Y|ID|FK902B|S|1|Q0 ;; "
                + "PK902X|ID|FK902B|T|1|Q2 ;; PK902X|ID|FK902C|U|1|Q1 ;; PK902M|B|FK902M|X|2|M1 ;; "
                + "PK902M|A|FK902M|Y|1|M1 ;; PK902X|ID|FK902M|Z|1|M0"},
            {"SHOW IMPORTED KEYS ->> SELECT \"pk_table_name\", \"pk_column_name\" FROM $1 WHERE "
                + "\"fk_table_name\" LIKE 'FK902%'",
                "PK902M|A ;; PK902M|B ;; PK902X|ID ;; PK902X|ID ;; PK902X|ID ;; PK902X|ID ;; PK902X|ID ;; PK902Y|ID"},
            {"SHOW IMPORTED KEYS ->> SELECT \"pk_table_name\", \"pk_column_name\", \"fk_table_name\", "
                + "\"fk_column_name\", \"key_sequence\", \"fk_name\" FROM $1 WHERE \"fk_table_name\" LIKE 'FK902%' "
                + "ORDER BY 1, 2, 3, 4, 5, 6",
                "PK902M|A|FK902M|Y|1|M1 ;; PK902M|B|FK902M|X|2|M1 ;; PK902X|ID|FK902A|P|1|C1 ;; "
                + "PK902X|ID|FK902A|Q|1|C2 ;; PK902X|ID|FK902B|T|1|Q2 ;; PK902X|ID|FK902C|U|1|Q1 ;; "
                + "PK902X|ID|FK902M|Z|1|M0 ;; PK902Y|ID|FK902B|S|1|Q0"},
            {"SHOW EXPORTED KEYS ->> SELECT \"pk_table_name\", \"pk_column_name\", \"fk_table_name\", "
                + "\"fk_column_name\", \"key_sequence\", \"fk_name\" FROM $1 WHERE \"fk_table_name\" LIKE 'FK902%'",
                "PK902X|ID|FK902A|P|1|C1 ;; PK902X|ID|FK902A|Q|1|C2 ;; PK902Y|ID|FK902B|S|1|Q0 ;; "
                + "PK902X|ID|FK902B|T|1|Q2 ;; PK902X|ID|FK902C|U|1|Q1 ;; PK902M|B|FK902M|X|2|M1 ;; "
                + "PK902M|A|FK902M|Y|1|M1 ;; PK902X|ID|FK902M|Z|1|M0"},
            {"SHOW PRIMARY KEYS IN SCHEMA test_db.test_schema ->> SELECT \"table_name\", \"column_name\", "
                + "\"key_sequence\" FROM $1 WHERE \"table_name\" LIKE 'PK902%'",
                "PK902M|A|1 ;; PK902M|B|2 ;; PK902Y|ID|1 ;; PK902X|ID|1"},
        });
    }

    @Test
    public void aKeysReferencedColumnsOrderItsImportedRows() {
        engine.execute("CREATE OR REPLACE TABLE pk903r (b INT, a INT, PRIMARY KEY (b, a))");
        engine.execute("CREATE OR REPLACE TABLE fk903r (x INT, y INT, FOREIGN KEY (x, y) REFERENCES pk903r (b, a))");
        engine.execute("CREATE OR REPLACE TABLE pk903x (id INT PRIMARY KEY)");
        engine.execute("CREATE OR REPLACE TABLE fk903p (a INT)");
        engine.execute("CREATE OR REPLACE TABLE fk903q (b INT REFERENCES pk903x (id))");
        engine.execute("ALTER TABLE fk903p ADD FOREIGN KEY (a) REFERENCES pk903x (id)");
        assertCells(new String[][] {
            {"SHOW IMPORTED KEYS IN TABLE fk903r ->> SELECT \"pk_table_name\", \"pk_column_name\", "
                + "\"fk_table_name\", \"fk_column_name\", \"key_sequence\" FROM $1",
                "PK903R|A|FK903R|Y|2 ;; PK903R|B|FK903R|X|1"},
            {"SHOW EXPORTED KEYS IN TABLE pk903r ->> SELECT \"pk_table_name\", \"pk_column_name\", "
                + "\"fk_table_name\", \"fk_column_name\", \"key_sequence\" FROM $1",
                "PK903R|B|FK903R|X|1 ;; PK903R|A|FK903R|Y|2"},
            {"SHOW IMPORTED KEYS IN SCHEMA test_db.test_schema ->> SELECT \"pk_table_name\", \"pk_column_name\" "
                + "FROM $1 WHERE \"fk_table_name\" LIKE 'FK903%'",
                "PK903R|A ;; PK903R|B ;; PK903X|ID ;; PK903X|ID"},
            {"SHOW IMPORTED KEYS IN SCHEMA test_db.test_schema ->> SELECT \"pk_table_name\", "
                + "\"pk_column_name\", \"fk_table_name\", \"fk_column_name\", \"key_sequence\" FROM $1 WHERE "
                + "\"fk_table_name\" LIKE 'FK903%' ORDER BY 1, 2, 3, 4, 5",
                "PK903R|A|FK903R|Y|2 ;; PK903R|B|FK903R|X|1 ;; PK903X|ID|FK903P|A|1 ;; PK903X|ID|FK903Q|B|1"},
            {"SHOW EXPORTED KEYS IN TABLE pk903x ->> SELECT \"pk_table_name\", \"pk_column_name\", "
                + "\"fk_table_name\", \"fk_column_name\", \"key_sequence\" FROM $1",
                "PK903X|ID|FK903P|A|1 ;; PK903X|ID|FK903Q|B|1"},
        });
    }

    @Test
    public void aOnePartSchemaIsRefusedAsTheChainCompiles() {
        engine.execute("CREATE OR REPLACE TABLE kc902 (id INT PRIMARY KEY)");
        engine.execute("CREATE OR REPLACE SEQUENCE kq902");
        assertCells(new String[][] {
            {"SELECT 1 ->> SHOW IMPORTED KEYS IN SCHEMA test_schema",
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "TEST_SCHEMA"},
            {"SELECT 1 ->> SHOW PRIMARY KEYS IN SCHEMA test_schema ->> SELECT 2",
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "TEST_SCHEMA"},
            {"SHOW PRIMARY KEYS IN TABLE nosuch902 ->> SELECT 1",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 0 : SQL compilation "
                + "error: | Table 'NOSUCH902' does not exist or not authorized. Your primary role <ROLE> must have "
                + "at least one privilege granted on TABLE NOSUCH902."},
            {"SELECT 1 ->> SHOW PRIMARY KEYS IN TABLE nosuch902",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: | Table 'NOSUCH902' does not exist or not authorized. Your primary role <ROLE> must have "
                + "at least one privilege granted on TABLE NOSUCH902."},
            {"SHOW UNIQUE KEYS IN nosuch902 ->> SELECT 1",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 0 : SQL compilation "
                + "error: | Table 'NOSUCH902' does not exist or not authorized. Your primary role <ROLE> must have "
                + "at least one privilege granted on TABLE NOSUCH902."},
            {"SHOW IMPORTED KEYS IN TABLE kq902 ->> SELECT 1",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 0 : SQL compilation "
                + "error: | Table 'KQ902' does not exist or not authorized. Your primary role <ROLE> must have at "
                + "least one privilege granted on TABLE KQ902."},
            {"SHOW PRIMARY KEYS IN SCHEMA nosuch902 ->> SELECT 1",
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "NOSUCH902"},
            {"SHOW PRIMARY KEYS IN SCHEMA test_db.nosuch902 ->> SELECT 1",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 0 : SQL compilation "
                + "error: | Schema 'TEST_DB.NOSUCH902' does not exist or not authorized. Your primary role <ROLE> "
                + "must have USAGE or any other privilege granted on SCHEMA TEST_DB.NOSUCH902."},
            {"SHOW PRIMARY KEYS IN DATABASE nosuch902 ->> SELECT 1",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 0 : SQL compilation "
                + "error: | Database 'NOSUCH902' does not exist or not authorized. Your primary role <ROLE> must "
                + "have USAGE or any other privilege granted on DATABASE NOSUCH902."},
            {"SHOW PRIMARY KEYS IN SCHEMA test_schema",
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "TEST_SCHEMA"},
            {"/* c */ SHOW PRIMARY KEYS IN SCHEMA test_schema ->> SELECT 1",
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "TEST_SCHEMA"},
            {"SHOW PRIMARY KEYS IN SCHEMA \"TEST_SCHEMA\" ->> SELECT 1",
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "TEST_SCHEMA"},
            {"SHOW TERSE PRIMARY KEYS IN SCHEMA test_schema ->> SELECT 1",
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "TEST_SCHEMA"},
            {"SHOW PRIMARY KEYS IN TABLE kc902 ->> SELECT \"column_name\" FROM $1", "ID"},
        });
    }

    @Test
    public void theKeyListingsOfOneSchema() {
        engine.execute("CREATE OR REPLACE TABLE pk900a (id INT PRIMARY KEY, x INT)");
        engine.execute("CREATE OR REPLACE TABLE pk900b (id INT PRIMARY KEY, y INT)");
        engine.execute("CREATE OR REPLACE TABLE fk900z (a INT REFERENCES pk900b (id), b INT REFERENCES pk900a (id))");
        engine.execute("CREATE OR REPLACE TABLE fk900c (k INT, CONSTRAINT zz FOREIGN KEY (k) REFERENCES pk900a (id))");
        engine.execute("CREATE OR REPLACE TABLE fk900a (m INT, CONSTRAINT aa FOREIGN KEY (m) REFERENCES pk900b (id))");
        assertCells(new String[][] {
            {"SHOW IMPORTED KEYS IN SCHEMA test_schema ->> SELECT \"pk_table_name\", \"pk_column_name\", "
                + "\"fk_table_name\", \"fk_column_name\", \"fk_name\" FROM $1",
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "TEST_SCHEMA"},
            {"SHOW EXPORTED KEYS IN SCHEMA test_schema ->> SELECT \"pk_table_name\", \"pk_column_name\", "
                + "\"fk_table_name\", \"fk_column_name\", \"fk_name\" FROM $1",
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "TEST_SCHEMA"},
            {"SHOW IMPORTED KEYS IN TABLE fk900z ->> SELECT \"pk_table_name\", \"fk_column_name\", "
                + "\"key_sequence\" FROM $1",
                "PK900A|B|1 ;; PK900B|A|1"},
        });
        assertShapedAnswer("FK900C|K|ZZ ;; FK900Z|B|SYS_CONSTRAINT_<QID>",
            "SHOW EXPORTED KEYS IN TABLE pk900a ->> SELECT \"fk_table_name\", \"fk_column_name\", "
                + "\"fk_name\" FROM $1");
    }
}
