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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A table's clustering key reads back as its statement wrote it, character for character: GET_DDL prints the text
 * after {@code cluster by}, SHOW TABLES and INFORMATION_SCHEMA print it under LINEAR unless the statement named the
 * ordering itself. A column a key reads is neither renamed nor dropped while the key stands.
 */
public class ClusteringKeySpellingTest extends BaseDatabaseTest {

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private String scalar(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private String listedKey(final String table) {
        return scalar("SELECT clustering_key FROM information_schema.tables WHERE table_name = '" + table + "'");
    }

    private String shownKey(final String table) {
        final ResultSet rs = engine.executeQuery("SHOW TABLES LIKE '" + table + "'");
        return cell(rs, soleRowWhere(rs, "name", table), "cluster_by");
    }

    private String ddlHead(final String table) {
        final String ddl = scalar("SELECT GET_DDL('TABLE', '" + table + "')");
        return ddl.substring(0, ddl.indexOf('\n'));
    }

    @Test
    public void theKeyReadsBackAsWritten() {
        engine.execute("CREATE OR REPLACE TABLE CK1 (a INT, ts TIMESTAMP_NTZ, b INT) CLUSTER BY (a+1,TO_DATE( ts ) , b)");
        assertEquals("LINEAR(a+1,TO_DATE( ts ) , b)", listedKey("CK1"));
        assertEquals("LINEAR(a+1,TO_DATE( ts ) , b)", shownKey("CK1"));
        assertEquals("create or replace TABLE CK1 cluster by (a+1,TO_DATE( ts ) , b)(", ddlHead("CK1"));
        engine.execute("CREATE OR REPLACE TABLE CK2 (a INT, b INT, c VARCHAR) CLUSTER BY (  a  ,upper(c))");
        assertEquals("LINEAR(  a  ,upper(c))", listedKey("CK2"));
        assertEquals("create or replace TABLE CK2 cluster by (  a  ,upper(c))(", ddlHead("CK2"));
        engine.execute("CREATE OR REPLACE TABLE CK3 (a INT, b INT)"
            + " CLUSTER BY (CASE WHEN a > 1 THEN 1 ELSE 0 END, a /* c */ % 10, b)");
        assertEquals("LINEAR(CASE WHEN a > 1 THEN 1 ELSE 0 END, a /* c */ % 10, b)", listedKey("CK3"));
        engine.execute("CREATE OR REPLACE TABLE CK5 (a INT, v VARIANT) CLUSTER BY (v:k::string, a::varchar)");
        assertEquals("LINEAR(v:k::string, a::varchar)", listedKey("CK5"));
    }

    @Test
    public void anOrderingTheStatementNamedIsKeptAsWritten() {
        engine.execute("CREATE OR REPLACE TABLE CK4 (a INT, b INT) cluster by linear(a,b)");
        assertEquals("linear(a,b)", listedKey("CK4"));
        assertEquals("create or replace TABLE CK4 cluster by linear(a,b)(", ddlHead("CK4"));
        engine.execute("ALTER TABLE CK4 CLUSTER BY (a  +  1,b)");
        assertEquals("LINEAR(a  +  1,b)", listedKey("CK4"));
        assertEquals("create or replace TABLE CK4 cluster by (a  +  1,b)(", ddlHead("CK4"));
        engine.execute("ALTER TABLE CK4 CLUSTER BY LINEAR(b , a)");
        assertEquals("LINEAR(b , a)", listedKey("CK4"));
        assertEquals("create or replace TABLE CK4 cluster by LINEAR(b , a)(", ddlHead("CK4"));
        engine.execute("ALTER TABLE CK4 DROP CLUSTERING KEY");
        assertEquals("null", listedKey("CK4"));
        assertEquals("create or replace TABLE CK4 (", ddlHead("CK4"));
    }

    @Test
    public void aColumnAKeyReadsIsNeitherRenamedNorDropped() {
        engine.execute("CREATE OR REPLACE TABLE KC (a INT, b INT, c VARCHAR, v VARIANT, \"q c\" INT, free INT)"
            + " CLUSTER BY (a, b + 1, upper(c), v:k::string, \"q c\")");
        assertEquals("Cannot rename column 'A' which belongs to a clustering key\n",
            refusalOf("ALTER TABLE KC RENAME COLUMN a TO z"));
        assertEquals("Cannot rename column 'B' which belongs to a clustering key\n",
            refusalOf("ALTER TABLE KC RENAME COLUMN \"B\" TO z"));
        assertEquals("Cannot rename column 'C' which belongs to a clustering key\n",
            refusalOf("ALTER TABLE KC RENAME COLUMN c TO c2"));
        assertEquals("Cannot rename column 'V' which belongs to a clustering key\n",
            refusalOf("ALTER TABLE KC RENAME COLUMN v TO v2"));
        assertEquals("Cannot rename column 'q c' which belongs to a clustering key\n",
            refusalOf("ALTER TABLE KC RENAME COLUMN \"q c\" TO qc"));
        assertEquals("Cannot drop column 'A' which belongs to a clustering key\n",
            refusalOf("ALTER TABLE KC DROP COLUMN a"));
        assertEquals("Cannot drop column 'q c' which belongs to a clustering key\n",
            refusalOf("ALTER TABLE KC DROP COLUMN \"q c\""));
        assertEquals("Cannot drop column 'B' which belongs to a clustering key\n",
            refusalOf("ALTER TABLE KC DROP COLUMN IF EXISTS b"));
        // The key's column is named before the new name is judged, and before a column the list misses.
        assertEquals("Cannot rename column 'A' which belongs to a clustering key\n",
            refusalOf("ALTER TABLE KC RENAME COLUMN a TO b"));
        assertEquals("Cannot rename column 'A' which belongs to a clustering key\n",
            refusalOf("ALTER TABLE KC RENAME COLUMN a TO a"));
        assertEquals("Cannot drop column 'A' which belongs to a clustering key\n",
            refusalOf("ALTER TABLE KC DROP COLUMN nosuch, a"));
        assertEquals("Cannot drop column 'A' which belongs to a clustering key\n",
            refusalOf("ALTER TABLE KC DROP COLUMN free, a"));
        assertEquals("6", scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'KC'"));
    }

    @Test
    public void everythingElseAboutAKeyColumnMayChangeAndTheKeyGoneItIsFree() {
        engine.execute("CREATE OR REPLACE TABLE KF (a INT, b INT, c INT) CLUSTER BY (b)");
        engine.execute("ALTER TABLE KF ALTER COLUMN b SET DATA TYPE NUMBER(38,0)");
        engine.execute("ALTER TABLE KF ALTER COLUMN b COMMENT 'c'");
        engine.execute("ALTER TABLE KF ALTER COLUMN b SET NOT NULL");
        engine.execute("ALTER TABLE KF RENAME COLUMN a TO a2");
        engine.execute("ALTER TABLE KF DROP COLUMN c");
        assertEquals("LINEAR(b)", listedKey("KF"));
        engine.execute("ALTER TABLE KF DROP CLUSTERING KEY");
        engine.execute("ALTER TABLE KF RENAME COLUMN b TO z");
        engine.execute("ALTER TABLE KF DROP COLUMN z");
        assertEquals("1", scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'KF'"));
    }
}
