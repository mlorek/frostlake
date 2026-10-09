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
package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * A REVOKE answers with a count. One that names its object counts every privilege it took back — two for
 * SELECT, INSERT when both were held, none for a privilege that was not, and one for each privilege ALL stands
 * for on that kind of object. A bulk REVOKE … ON ALL … IN SCHEMA counts the objects it took a privilege from,
 * and a REVOKE of a FUTURE grant, like every GRANT that names its object, answers the bare status. Every
 * answer is live-verified.
 */
public class RevokeCountTest extends MeasuredCellsTestSupport {

    @Test
    public void aRevokeCountsThePrivilegesItTookBack() {
        try {
            engine.execute("DROP ROLE IF EXISTS flq_rv_r");
            engine.execute("CREATE ROLE flq_rv_r");
            engine.execute("CREATE OR REPLACE SCHEMA rv_s");
            engine.execute("USE SCHEMA test_schema");
            engine.execute("CREATE OR REPLACE TABLE rv_s.t1 (a INT)");
            engine.execute("CREATE OR REPLACE TABLE rv_s.t2 (a INT)");
            engine.execute("CREATE OR REPLACE VIEW rv_s.v1 AS SELECT a FROM rv_s.t1");
            engine.execute("CREATE OR REPLACE SEQUENCE rv_s.q1");
            engine.execute("CREATE OR REPLACE FUNCTION rv_s.f1() RETURNS INT AS '1'");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT SELECT ON TABLE rv_s.t1 TO ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT SELECT ON TABLE rv_s.t1 TO ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully. 1 objects affected.",
                "REVOKE SELECT ON TABLE rv_s.t1 FROM ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully. 0 objects affected.",
                "REVOKE SELECT ON TABLE rv_s.t1 FROM ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT SELECT, INSERT ON TABLE rv_s.t1 TO ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully. 2 objects affected.",
                "REVOKE SELECT, INSERT ON TABLE rv_s.t1 FROM ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT SELECT ON TABLE rv_s.t1 TO ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully. 1 objects affected.",
                "REVOKE SELECT, INSERT ON TABLE rv_s.t1 FROM ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON SCHEMA rv_s TO ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully. 1 objects affected.",
                "REVOKE USAGE ON SCHEMA rv_s FROM ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully. 2 objects affected.",
                "GRANT SELECT ON ALL TABLES IN SCHEMA rv_s TO ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully. 2 objects affected.",
                "REVOKE SELECT ON ALL TABLES IN SCHEMA rv_s FROM ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully. 0 objects affected.",
                "REVOKE SELECT ON ALL TABLES IN SCHEMA rv_s FROM ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully. 1 objects affected.",
                "GRANT SELECT ON ALL VIEWS IN SCHEMA rv_s TO ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully. 1 objects affected.",
                "REVOKE SELECT ON ALL VIEWS IN SCHEMA rv_s FROM ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT SELECT ON FUTURE TABLES IN SCHEMA rv_s TO ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully.",
                "REVOKE SELECT ON FUTURE TABLES IN SCHEMA rv_s FROM ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully.",
                "REVOKE SELECT ON FUTURE TABLES IN SCHEMA rv_s FROM ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT SELECT ON VIEW rv_s.v1 TO ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully. 1 objects affected.",
                "REVOKE SELECT ON VIEW rv_s.v1 FROM ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON SEQUENCE rv_s.q1 TO ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully. 1 objects affected.",
                "REVOKE USAGE ON SEQUENCE rv_s.q1 FROM ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON FUNCTION rv_s.f1() TO ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully. 1 objects affected.",
                "REVOKE USAGE ON FUNCTION rv_s.f1() FROM ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT USAGE ON DATABASE test_db TO ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully. 1 objects affected.",
                "REVOKE USAGE ON DATABASE test_db FROM ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT ALL ON TABLE rv_s.t2 TO ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully. 11 objects affected.",
                "REVOKE ALL ON TABLE rv_s.t2 FROM ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully. 0 objects affected.",
                "REVOKE ALL ON TABLE rv_s.t2 FROM ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT ALL PRIVILEGES ON TABLE rv_s.t2 TO ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully. 1 objects affected.",
                "REVOKE SELECT ON TABLE rv_s.t2 FROM ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully. 10 objects affected.",
                "REVOKE ALL PRIVILEGES ON TABLE rv_s.t2 FROM ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT ALL ON VIEW rv_s.v1 TO ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully. 12 objects affected.",
                "REVOKE ALL ON VIEW rv_s.v1 FROM ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT ALL ON SEQUENCE rv_s.q1 TO ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully. 1 objects affected.",
                "REVOKE ALL ON SEQUENCE rv_s.q1 FROM ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT ALL ON FUNCTION rv_s.f1() TO ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully. 2 objects affected.",
                "REVOKE ALL ON FUNCTION rv_s.f1() FROM ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT SELECT ON TABLE rv_s.t1 TO ROLE flq_rv_r WITH GRANT OPTION");
            assertAnswer(
                "Statement executed successfully. 1 objects affected.",
                "REVOKE GRANT OPTION FOR SELECT ON TABLE rv_s.t1 FROM ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully. 1 objects affected.",
                "REVOKE SELECT ON TABLE rv_s.t1 FROM ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully. 0 objects affected.",
                "REVOKE SELECT ON TABLE rv_s.t1 FROM ROLE flq_rv_r CASCADE");
            assertAnswer(
                "REFUSED SQL compilation error: | Role 'FLQ_RV_NOPE' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on ROLE FLQ_RV_NOPE.",
                "REVOKE SELECT ON TABLE rv_s.t1 FROM ROLE flq_rv_nope");
            assertAnswer(
                "REFUSED SQL compilation error: | Table 'TEST_DB.RV_S.NOPE' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE TEST_DB.RV_S.NOPE.",
                "REVOKE SELECT ON TABLE rv_s.nope FROM ROLE flq_rv_r");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT OWNERSHIP ON TABLE rv_s.t2 TO ROLE flq_rv_r");
        } finally {
            engine.execute("DROP ROLE IF EXISTS flq_rv_r");
        }
    }

    @Test
    public void whatAllStandsForOnEachKind() {
        try {
            engine.execute("DROP ROLE IF EXISTS flq_al_r");
            engine.execute("CREATE ROLE flq_al_r");
            engine.execute("CREATE OR REPLACE SCHEMA al_s");
            engine.execute("USE SCHEMA test_schema");
            engine.execute("CREATE OR REPLACE TABLE al_s.t1 (a INT)");
            engine.execute("CREATE OR REPLACE VIEW al_s.v1 AS SELECT a FROM al_s.t1");
            engine.execute("CREATE OR REPLACE SEQUENCE al_s.q1");
            engine.execute("CREATE OR REPLACE FUNCTION al_s.f1() RETURNS INT AS '1'");
            engine.execute("CREATE OR REPLACE STAGE al_s.st1");
            engine.execute("CREATE OR REPLACE PROCEDURE al_s.p1() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
            engine.execute("CREATE OR REPLACE FILE FORMAT al_s.ff1 TYPE = CSV");
            engine.execute("CREATE OR REPLACE STREAM al_s.s1 ON TABLE al_s.t1");
            engine.execute("CREATE OR REPLACE MATERIALIZED VIEW al_s.mv1 AS SELECT a FROM al_s.t1");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT ALL ON TABLE al_s.t1 TO ROLE flq_al_r");
            engine.execute("SHOW GRANTS ON TABLE al_s.t1");
            assertAnswer(
                "APPLYBUDGET,DELETE,DELETE ERROR TABLE,EVOLVE SCHEMA,INSERT,REBUILD,REFERENCES,SELECT,SELECT "
                + "ERROR TABLE,TRUNCATE,UPDATE",
                "SELECT LISTAGG(\"privilege\", ',') WITHIN GROUP (ORDER BY \"privilege\") FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"grantee_name\" = 'FLQ_AL_R'");
            assertAnswer(
                "Statement executed successfully. 11 objects affected.",
                "REVOKE ALL ON TABLE al_s.t1 FROM ROLE flq_al_r");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT ALL ON VIEW al_s.v1 TO ROLE flq_al_r");
            engine.execute("SHOW GRANTS ON VIEW al_s.v1");
            assertAnswer(
                "APPLYBUDGET,DELETE,DELETE ERROR TABLE,EVOLVE SCHEMA,INSERT,REBUILD,REFERENCES,SELECT,SELECT "
                + "ERROR TABLE,TRUNCATE,UPDATE,VIEW EXPANDED QUERY PROFILE",
                "SELECT LISTAGG(\"privilege\", ',') WITHIN GROUP (ORDER BY \"privilege\") FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"grantee_name\" = 'FLQ_AL_R'");
            assertAnswer(
                "Statement executed successfully. 12 objects affected.",
                "REVOKE ALL ON VIEW al_s.v1 FROM ROLE flq_al_r");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT ALL ON SEQUENCE al_s.q1 TO ROLE flq_al_r");
            engine.execute("SHOW GRANTS ON SEQUENCE al_s.q1");
            assertAnswer(
                "USAGE",
                "SELECT LISTAGG(\"privilege\", ',') WITHIN GROUP (ORDER BY \"privilege\") FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"grantee_name\" = 'FLQ_AL_R'");
            assertAnswer(
                "Statement executed successfully. 1 objects affected.",
                "REVOKE ALL ON SEQUENCE al_s.q1 FROM ROLE flq_al_r");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT ALL ON FUNCTION al_s.f1() TO ROLE flq_al_r");
            engine.execute("SHOW GRANTS ON FUNCTION al_s.f1()");
            assertAnswer(
                "MONITOR,USAGE",
                "SELECT LISTAGG(\"privilege\", ',') WITHIN GROUP (ORDER BY \"privilege\") FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"grantee_name\" = 'FLQ_AL_R'");
            assertAnswer(
                "Statement executed successfully. 2 objects affected.",
                "REVOKE ALL ON FUNCTION al_s.f1() FROM ROLE flq_al_r");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT ALL ON PROCEDURE al_s.p1() TO ROLE flq_al_r");
            engine.execute("SHOW GRANTS ON PROCEDURE al_s.p1()");
            assertAnswer(
                "MONITOR,USAGE",
                "SELECT LISTAGG(\"privilege\", ',') WITHIN GROUP (ORDER BY \"privilege\") FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"grantee_name\" = 'FLQ_AL_R'");
            assertAnswer(
                "Statement executed successfully. 2 objects affected.",
                "REVOKE ALL ON PROCEDURE al_s.p1() FROM ROLE flq_al_r");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT ALL ON STAGE al_s.st1 TO ROLE flq_al_r");
            engine.execute("SHOW GRANTS ON STAGE al_s.st1");
            assertAnswer(
                "READ,WRITE",
                "SELECT LISTAGG(\"privilege\", ',') WITHIN GROUP (ORDER BY \"privilege\") FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"grantee_name\" = 'FLQ_AL_R'");
            assertAnswer(
                "Statement executed successfully. 2 objects affected.",
                "REVOKE ALL ON STAGE al_s.st1 FROM ROLE flq_al_r");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT ALL ON FILE FORMAT al_s.ff1 TO ROLE flq_al_r");
            engine.execute("SHOW GRANTS ON FILE FORMAT al_s.ff1");
            assertAnswer(
                "USAGE",
                "SELECT LISTAGG(\"privilege\", ',') WITHIN GROUP (ORDER BY \"privilege\") FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"grantee_name\" = 'FLQ_AL_R'");
            assertAnswer(
                "Statement executed successfully. 1 objects affected.",
                "REVOKE ALL ON FILE FORMAT al_s.ff1 FROM ROLE flq_al_r");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT ALL ON STREAM al_s.s1 TO ROLE flq_al_r");
            engine.execute("SHOW GRANTS ON STREAM al_s.s1");
            assertAnswer(
                "SELECT",
                "SELECT LISTAGG(\"privilege\", ',') WITHIN GROUP (ORDER BY \"privilege\") FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"grantee_name\" = 'FLQ_AL_R'");
            assertAnswer(
                "Statement executed successfully. 1 objects affected.",
                "REVOKE ALL ON STREAM al_s.s1 FROM ROLE flq_al_r");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT ALL ON SCHEMA al_s TO ROLE flq_al_r");
            engine.execute("SHOW GRANTS ON SCHEMA al_s");
            assertAnswer(
                "84",
                "SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"grantee_name\" = 'FLQ_AL_R'");
            assertAnswer(
                "Statement executed successfully. 84 objects affected.",
                "REVOKE ALL ON SCHEMA al_s FROM ROLE flq_al_r");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT ALL ON DATABASE test_db TO ROLE flq_al_r");
            engine.execute("SHOW GRANTS ON DATABASE test_db");
            assertAnswer(
                "APPLYBUDGET,CREATE DATABASE ROLE,CREATE SCHEMA,EXECUTE AUTO CLASSIFICATION,MODIFY,MONITOR,USAGE",
                "SELECT LISTAGG(\"privilege\", ',') WITHIN GROUP (ORDER BY \"privilege\") FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE \"grantee_name\" = 'FLQ_AL_R'");
            assertAnswer(
                "Statement executed successfully. 7 objects affected.",
                "REVOKE ALL ON DATABASE test_db FROM ROLE flq_al_r");
            assertAnswer(
                "Statement executed successfully.",
                "GRANT SELECT, INSERT, UPDATE ON TABLE al_s.t1 TO ROLE flq_al_r");
            assertAnswer(
                "Statement executed successfully. 3 objects affected.",
                "REVOKE SELECT, INSERT, UPDATE, DELETE ON TABLE al_s.t1 FROM ROLE flq_al_r");
            assertAnswer(
                "Statement executed successfully. 1 objects affected.",
                "GRANT ALL ON ALL TABLES IN SCHEMA al_s TO ROLE flq_al_r");
            assertAnswer(
                "Statement executed successfully. 1 objects affected.",
                "REVOKE ALL ON ALL TABLES IN SCHEMA al_s FROM ROLE flq_al_r");
            assertAnswer(
                "Statement executed successfully. 1 objects affected.",
                "GRANT SELECT, INSERT ON ALL TABLES IN SCHEMA al_s TO ROLE flq_al_r");
            assertAnswer(
                "Statement executed successfully. 1 objects affected.",
                "REVOKE SELECT, INSERT ON ALL TABLES IN SCHEMA al_s FROM ROLE flq_al_r");
        } finally {
            engine.execute("DROP ROLE IF EXISTS flq_al_r");
        }
    }
}
