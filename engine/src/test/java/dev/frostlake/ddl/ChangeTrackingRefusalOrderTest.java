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
 * A stream on a view, a view created with CHANGE_TRACKING = TRUE and ALTER VIEW ... SET CHANGE_TRACKING = TRUE are
 * refused the first construct change tracking cannot follow, each in the account's words and at its place: a window
 * function, QUALIFY, DISTINCT, GROUP BY and its super-groups, LIMIT, TOP and FETCH, a scalar aggregate named as the
 * account computes it, a set operation other than UNION ALL, an outer join and CONNECT BY; a stream on a view also
 * refuses any table function first. A view the query reads is judged by its own query (all live-verified).
 */
public class ChangeTrackingRefusalOrderTest extends MeasuredCellsTestSupport {

    @Test
    public void eachConstructIsRefusedInTheAccountsWords() {
        assertAnswer("Table A successfully created.", "CREATE OR REPLACE TABLE a (id INT)");
        assertAnswer("2", "INSERT INTO a VALUES (1), (2)");
        assertAnswer("View VS successfully created.",
            "CREATE OR REPLACE VIEW vs AS SELECT id, SUM(id) OVER () s FROM a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 29: | Change tracking is not supported on "
                + "queries with window functions. (inside view 'TEST_DB.TEST_SCHEMA.VS').",
            "CREATE OR REPLACE STREAM ss ON VIEW vs");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 53: | Change tracking is not supported on "
                + "queries with window functions.",
            "CREATE OR REPLACE VIEW vw2 CHANGE_TRACKING = TRUE AS SELECT id, ROW_NUMBER() OVER (ORDER BY id) rn FROM "
                + "a");
        assertAnswer("View VL successfully created.",
            "CREATE OR REPLACE VIEW vl AS SELECT id, LAG(id) OVER (ORDER BY id) prev FROM a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 29: | Change tracking is not supported on "
                + "queries with window functions. (inside view 'TEST_DB.TEST_SCHEMA.VL').",
            "CREATE OR REPLACE STREAM sl ON VIEW vl");
        assertAnswer("View VQ successfully created.",
            "CREATE OR REPLACE VIEW vq AS SELECT id FROM a QUALIFY ROW_NUMBER() OVER (ORDER BY id) = 1");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 46: | Change tracking is not supported on "
                + "queries with QUALIFY. (inside view 'TEST_DB.TEST_SCHEMA.VQ').",
            "CREATE OR REPLACE STREAM sq ON VIEW vq");
        assertAnswer("View VD successfully created.",
            "CREATE OR REPLACE VIEW vd AS SELECT id FROM (SELECT id, ROW_NUMBER() OVER (ORDER BY id) rn FROM a) "
                + "WHERE rn = 1");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 45: | Change tracking is not supported on "
                + "queries with window functions. (inside view 'TEST_DB.TEST_SCHEMA.VD').",
            "CREATE OR REPLACE STREAM sd ON VIEW vd");
        assertAnswer("Function F716 successfully created.",
            "CREATE OR REPLACE FUNCTION f716(x INT) RETURNS INT AS 'x * 10'");
        assertAnswer("View VU successfully created.", "CREATE OR REPLACE VIEW vu AS SELECT id, f716(id) fx FROM a");
        assertAnswer("Stream SU successfully created.", "CREATE OR REPLACE STREAM su ON VIEW vu");
        assertAnswer("View VUW successfully created.",
            "CREATE OR REPLACE VIEW vuw AS SELECT id FROM a WHERE f716(id) > 10");
        assertAnswer("Stream SUW successfully created.", "CREATE OR REPLACE STREAM suw ON VIEW vuw");
        assertAnswer("View VUP successfully created.",
            "CREATE OR REPLACE VIEW vup AS SELECT id, UPPER(TO_VARCHAR(id)) u FROM a");
        assertAnswer("Stream SUP successfully created.", "CREATE OR REPLACE STREAM sup ON VIEW vup");
        assertAnswer("View VS2 successfully created.",
            "CREATE OR REPLACE VIEW vs2 AS SELECT id, SUM(id) OVER () s FROM a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 30: | Change tracking is not supported on "
                + "queries with window functions. (inside view 'TEST_DB.TEST_SCHEMA.VS2').",
            "ALTER VIEW vs2 SET CHANGE_TRACKING = TRUE");
        assertAnswer("View VW3 successfully created.",
            "CREATE OR REPLACE VIEW vw3 CHANGE_TRACKING = TRUE AS SELECT id, f716(id) fx FROM a");
        assertAnswer("View VAGG successfully created.",
            "CREATE OR REPLACE VIEW vagg AS SELECT id, COUNT(*) OVER (PARTITION BY id) c FROM a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 31: | Change tracking is not supported on "
                + "queries with window functions. (inside view 'TEST_DB.TEST_SCHEMA.VAGG').",
            "CREATE OR REPLACE STREAM sagg ON VIEW vagg");
        assertAnswer("View VC successfully created.", "CREATE OR REPLACE VIEW vc AS SELECT COUNT(*) c FROM a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 36: | Query contains the function 'COUNT(*)', "
                + "but change tracking is not supported on queries with scalar aggregate functions. (inside view "
                + "'TEST_DB.TEST_SCHEMA.VC').",
            "CREATE OR REPLACE STREAM sc ON VIEW vc");
        assertAnswer("View VWU successfully created.", "CREATE OR REPLACE VIEW vwu AS SELECT id, s FROM vs");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 29: | Change tracking is not supported on "
                + "queries with window functions. (inside view 'TEST_DB.TEST_SCHEMA.VS').",
            "CREATE OR REPLACE STREAM swu ON VIEW vwu");
        assertAnswer("View VD0 successfully created.",
            "CREATE OR REPLACE VIEW vd0 AS SELECT id FROM (SELECT id FROM a) WHERE id > 0");
        answer("CREATE OR REPLACE STREAM sd0 ON VIEW vd0");
        assertAnswer("View VDI successfully created.", "CREATE OR REPLACE VIEW vdi AS SELECT DISTINCT id FROM a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 30: | Change tracking is not supported on "
                + "queries with DISTINCT. Remove the DISTINCT keyword from the query or from the definition of the "
                + "view that contains it. (inside view 'TEST_DB.TEST_SCHEMA.VDI').",
            "CREATE OR REPLACE STREAM sdi ON VIEW vdi");
        assertAnswer("View VLI successfully created.", "CREATE OR REPLACE VIEW vli AS SELECT id FROM a LIMIT 1");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with LIMIT. (inside view 'TEST_DB.TEST_SCHEMA.VLI').",
            "CREATE OR REPLACE STREAM sli ON VIEW vli");
        assertAnswer("View VCT successfully created.",
            "CREATE OR REPLACE VIEW vct AS WITH c AS (SELECT id FROM a) SELECT id FROM c");
        answer("CREATE OR REPLACE STREAM sct ON VIEW vct");
        assertAnswer("View VFX successfully created.",
            "CREATE OR REPLACE VIEW vfx AS SELECT id, f716(id) + 1 fx FROM a WHERE id > 0");
        assertAnswer("Stream SFX successfully created.", "CREATE OR REPLACE STREAM sfx ON VIEW vfx");
        assertAnswer("1", "INSERT INTO a VALUES (5)");
        assertAnswer("REFUSED SQL compilation error: | Object 'SS' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE SS.",
            "SELECT id, s, METADATA$ACTION FROM ss ORDER BY 1");
        assertAnswer("5|50|INSERT", "SELECT id, fx, METADATA$ACTION FROM su ORDER BY 1");
        assertAnswer("5|5|INSERT", "SELECT id, u, METADATA$ACTION FROM sup ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Object 'VW2' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE VW2.",
            "SELECT id, rn FROM vw2 ORDER BY 1");
        engine.execute("SHOW VIEWS LIKE 'V%'");
        answer("SELECT \"name\", \"change_tracking\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
    }

    @Test
    public void constructsAreRefusedAtTheirPlaces() {
        assertAnswer("Table A successfully created.", "CREATE OR REPLACE TABLE a (id INT, g INT)");
        assertAnswer("2", "INSERT INTO a VALUES (1, 1), (2, 1)");
        assertAnswer("View VG successfully created.",
            "CREATE OR REPLACE VIEW vg AS SELECT g, COUNT(*) c FROM a GROUP BY g");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 57: | Change tracking is not supported on "
                + "queries with GROUP BY. Remove the GROUP BY clause from the query or from the definition of the "
                + "view that contains it. (inside view 'TEST_DB.TEST_SCHEMA.VG').",
            "CREATE OR REPLACE STREAM s_vg ON VIEW vg");
        assertAnswer("View VH successfully created.",
            "CREATE OR REPLACE VIEW vh AS SELECT g FROM a GROUP BY g HAVING COUNT(*) > 0");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 45: | Change tracking is not supported on "
                + "queries with GROUP BY. Remove the GROUP BY clause from the query or from the definition of the "
                + "view that contains it. (inside view 'TEST_DB.TEST_SCHEMA.VH').",
            "CREATE OR REPLACE STREAM s_vh ON VIEW vh");
        assertAnswer("View VUN successfully created.",
            "CREATE OR REPLACE VIEW vun AS SELECT id FROM a UNION SELECT id FROM a");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with joins of type '[UNION]'. (inside view 'TEST_DB.TEST_SCHEMA.VUN').",
            "CREATE OR REPLACE STREAM s_vun ON VIEW vun");
        assertAnswer("View VLJ successfully created.",
            "CREATE OR REPLACE VIEW vlj AS SELECT a.id FROM a LEFT JOIN a b ON a.id = b.id");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with joins of type '[LEFT_OUTER_JOIN]'. (inside view 'TEST_DB.TEST_SCHEMA.VLJ').",
            "CREATE OR REPLACE STREAM s_vlj ON VIEW vlj");
        assertAnswer("View VLC successfully created.", "CREATE OR REPLACE VIEW vlc AS SELECT count(*) c FROM a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 37: | Query contains the function 'COUNT(*)', "
                + "but change tracking is not supported on queries with scalar aggregate functions. (inside view "
                + "'TEST_DB.TEST_SCHEMA.VLC').",
            "CREATE OR REPLACE STREAM s_vlc ON VIEW vlc");
        assertAnswer("View VSM successfully created.", "CREATE OR REPLACE VIEW vsm AS SELECT sum(id) s FROM a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 37: | Query contains the function 'SUM(A.ID)',"
                + " but change tracking is not supported on queries with scalar aggregate functions. (inside view "
                + "'TEST_DB.TEST_SCHEMA.VSM').",
            "CREATE OR REPLACE STREAM s_vsm ON VIEW vsm");
        assertAnswer("View VMX successfully created.", "CREATE OR REPLACE VIEW vmx AS SELECT MAX(id) + 1 m FROM a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 37: | Query contains the function 'MAX(A.ID)',"
                + " but change tracking is not supported on queries with scalar aggregate functions. (inside view "
                + "'TEST_DB.TEST_SCHEMA.VMX').",
            "CREATE OR REPLACE STREAM s_vmx ON VIEW vmx");
        assertAnswer("View VCD successfully created.",
            "CREATE OR REPLACE VIEW vcd AS SELECT COUNT(DISTINCT id) c FROM a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 37: | Query contains the function "
                + "'COUNT(DISTINCT A.ID)', but change tracking is not supported on queries with scalar aggregate "
                + "functions. (inside view 'TEST_DB.TEST_SCHEMA.VCD').",
            "CREATE OR REPLACE STREAM s_vcd ON VIEW vcd");
        assertAnswer("View VTOP successfully created.", "CREATE OR REPLACE VIEW vtop AS SELECT TOP 1 id FROM a");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with LIMIT. (inside view 'TEST_DB.TEST_SCHEMA.VTOP').",
            "CREATE OR REPLACE STREAM s_vtop ON VIEW vtop");
        assertAnswer("View VORD successfully created.", "CREATE OR REPLACE VIEW vord AS SELECT id FROM a ORDER BY id");
        assertAnswer("Stream S_VORD successfully created.", "CREATE OR REPLACE STREAM s_vord ON VIEW vord");
        assertAnswer("View VFF successfully created.",
            "CREATE OR REPLACE VIEW vff AS SELECT id FROM a FETCH FIRST 1 ROWS ONLY");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with LIMIT. (inside view 'TEST_DB.TEST_SCHEMA.VFF').",
            "CREATE OR REPLACE STREAM s_vff ON VIEW vff");
        assertAnswer("View VLO successfully created.",
            "CREATE OR REPLACE VIEW vlo AS SELECT id FROM a LIMIT 1 OFFSET 1");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with LIMIT. (inside view 'TEST_DB.TEST_SCHEMA.VLO').",
            "CREATE OR REPLACE STREAM s_vlo ON VIEW vlo");
        assertAnswer("View VDW successfully created.",
            "CREATE OR REPLACE VIEW vdw AS SELECT DISTINCT id, ROW_NUMBER() OVER (ORDER BY id) rn FROM a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 30: | Change tracking is not supported on "
                + "queries with DISTINCT. Remove the DISTINCT keyword from the query or from the definition of the "
                + "view that contains it. (inside view 'TEST_DB.TEST_SCHEMA.VDW').",
            "CREATE OR REPLACE STREAM s_vdw ON VIEW vdw");
        assertAnswer("View VWS successfully created.",
            "CREATE OR REPLACE VIEW vws AS SELECT id, ROW_NUMBER() OVER (ORDER BY id) rn, (SELECT 1) one FROM a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 30: | Change tracking is not supported on "
                + "queries with window functions. (inside view 'TEST_DB.TEST_SCHEMA.VWS').",
            "CREATE OR REPLACE STREAM s_vws ON VIEW vws");
        assertAnswer("View VSW successfully created.",
            "CREATE OR REPLACE VIEW vsw AS SELECT id, (SELECT 1) one, ROW_NUMBER() OVER (ORDER BY id) rn FROM a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 30: | Change tracking is not supported on "
                + "queries with window functions. (inside view 'TEST_DB.TEST_SCHEMA.VSW').",
            "CREATE OR REPLACE STREAM s_vsw ON VIEW vsw");
        assertAnswer("View VGW successfully created.",
            "CREATE OR REPLACE VIEW vgw AS SELECT g, ROW_NUMBER() OVER (ORDER BY g) rn FROM a GROUP BY g");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 81: | Change tracking is not supported on "
                + "queries with GROUP BY. Remove the GROUP BY clause from the query or from the definition of the "
                + "view that contains it. (inside view 'TEST_DB.TEST_SCHEMA.VGW').",
            "CREATE OR REPLACE STREAM s_vgw ON VIEW vgw");
        assertAnswer("View VAW successfully created.",
            "CREATE OR REPLACE VIEW vaw AS SELECT COUNT(*) c, ROW_NUMBER() OVER (ORDER BY 1) rn FROM a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 30: | Change tracking is not supported on "
                + "queries with window functions. (inside view 'TEST_DB.TEST_SCHEMA.VAW').",
            "CREATE OR REPLACE STREAM s_vaw ON VIEW vaw");
        assertAnswer("View VWG successfully created.",
            "CREATE OR REPLACE VIEW vwg AS SELECT id, g FROM a WHERE g > 0 QUALIFY ROW_NUMBER() OVER (PARTITION BY g "
                + "ORDER BY id) = 1");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 62: | Change tracking is not supported on "
                + "queries with QUALIFY. (inside view 'TEST_DB.TEST_SCHEMA.VWG').",
            "CREATE OR REPLACE STREAM s_vwg ON VIEW vwg");
        assertAnswer("View VDX successfully created.",
            "CREATE OR REPLACE VIEW vdx AS SELECT x.id FROM (SELECT id FROM a) x JOIN a ON x.id = a.id");
        answer("CREATE OR REPLACE STREAM s_vdx ON VIEW vdx");
        assertAnswer("View VUA successfully created.",
            "CREATE OR REPLACE VIEW vua AS SELECT id FROM a UNION ALL SELECT id FROM a WHERE id > 1");
        assertAnswer("Stream S_VUA successfully created.", "CREATE OR REPLACE STREAM s_vua ON VIEW vua");
        assertAnswer("View VUA2 successfully created.",
            "CREATE OR REPLACE VIEW vua2 AS SELECT id FROM a UNION ALL SELECT ROW_NUMBER() OVER (ORDER BY id) FROM a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 58: | Change tracking is not supported on "
                + "queries with window functions. (inside view 'TEST_DB.TEST_SCHEMA.VUA2').",
            "CREATE OR REPLACE STREAM s_vua2 ON VIEW vua2");
        assertAnswer("View VDG successfully created.",
            "CREATE OR REPLACE VIEW vdg AS SELECT c FROM (SELECT g, COUNT(*) c FROM a GROUP BY g)");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 73: | Change tracking is not supported on "
                + "queries with GROUP BY. Remove the GROUP BY clause from the query or from the definition of the "
                + "view that contains it. (inside view 'TEST_DB.TEST_SCHEMA.VDG').",
            "CREATE OR REPLACE STREAM s_vdg ON VIEW vdg");
        assertAnswer("View VLG successfully created.", "CREATE OR REPLACE VIEW vlg AS SELECT LISTAGG(id) l FROM a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 37: | Query contains the function "
                + "'LISTAGG(CAST(A.ID AS VARCHAR(134217728)))', but change tracking is not supported on queries with "
                + "scalar aggregate functions. (inside view 'TEST_DB.TEST_SCHEMA.VLG').",
            "CREATE OR REPLACE STREAM s_vlg ON VIEW vlg");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 82: | Change tracking is not supported on "
                + "queries with GROUP BY. Remove the GROUP BY clause from the query or from the definition of the "
                + "view that contains it.",
            "CREATE OR REPLACE VIEW t_vg CHANGE_TRACKING = TRUE AS SELECT g, COUNT(*) c FROM a GROUP BY g");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 70: | Change tracking is not supported on "
                + "queries with GROUP BY. Remove the GROUP BY clause from the query or from the definition of the "
                + "view that contains it.",
            "CREATE OR REPLACE VIEW t_vh CHANGE_TRACKING = TRUE AS SELECT g FROM a GROUP BY g HAVING COUNT(*) > 0");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with joins of type '[UNION]'.",
            "CREATE OR REPLACE VIEW t_vun CHANGE_TRACKING = TRUE AS SELECT id FROM a UNION SELECT id FROM a");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with joins of type '[LEFT_OUTER_JOIN]'.",
            "CREATE OR REPLACE VIEW t_vlj CHANGE_TRACKING = TRUE AS SELECT a.id FROM a LEFT JOIN a b ON a.id = b.id");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 62: | Query contains the function 'COUNT(*)', "
                + "but change tracking is not supported on queries with scalar aggregate functions.",
            "CREATE OR REPLACE VIEW t_vlc CHANGE_TRACKING = TRUE AS SELECT count(*) c FROM a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 62: | Query contains the function 'SUM(A.ID)',"
                + " but change tracking is not supported on queries with scalar aggregate functions.",
            "CREATE OR REPLACE VIEW t_vsm CHANGE_TRACKING = TRUE AS SELECT sum(id) s FROM a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 62: | Query contains the function 'MAX(A.ID)',"
                + " but change tracking is not supported on queries with scalar aggregate functions.",
            "CREATE OR REPLACE VIEW t_vmx CHANGE_TRACKING = TRUE AS SELECT MAX(id) + 1 m FROM a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 62: | Query contains the function "
                + "'COUNT(DISTINCT A.ID)', but change tracking is not supported on queries with scalar aggregate "
                + "functions.",
            "CREATE OR REPLACE VIEW t_vcd CHANGE_TRACKING = TRUE AS SELECT COUNT(DISTINCT id) c FROM a");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with LIMIT.",
            "CREATE OR REPLACE VIEW t_vtop CHANGE_TRACKING = TRUE AS SELECT TOP 1 id FROM a");
        assertAnswer("View T_VORD successfully created.",
            "CREATE OR REPLACE VIEW t_vord CHANGE_TRACKING = TRUE AS SELECT id FROM a ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with LIMIT.",
            "CREATE OR REPLACE VIEW t_vff CHANGE_TRACKING = TRUE AS SELECT id FROM a FETCH FIRST 1 ROWS ONLY");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with LIMIT.",
            "CREATE OR REPLACE VIEW t_vlo CHANGE_TRACKING = TRUE AS SELECT id FROM a LIMIT 1 OFFSET 1");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 57: | Change tracking is not supported on "
                + "queries with GROUP BY. Remove the GROUP BY clause from the query or from the definition of the "
                + "view that contains it. (inside view 'TEST_DB.TEST_SCHEMA.VG').",
            "ALTER VIEW vg SET CHANGE_TRACKING = TRUE");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 30: | Change tracking is not supported on "
                + "queries with DISTINCT. Remove the DISTINCT keyword from the query or from the definition of the "
                + "view that contains it. (inside view 'TEST_DB.TEST_SCHEMA.VDW').",
            "ALTER VIEW vdw SET CHANGE_TRACKING = TRUE");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with LIMIT. (inside view 'TEST_DB.TEST_SCHEMA.VLO').",
            "ALTER VIEW vlo SET CHANGE_TRACKING = TRUE");
        engine.execute("SHOW VIEWS LIKE 'V%'");
        answer("SELECT \"name\", \"change_tracking\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1");
    }

    @Test
    public void aBlockIsRefusedInTheAccountsOrder() {
        assertAnswer("Table A successfully created.", "CREATE OR REPLACE TABLE a (id INT, g INT, s STRING)");
        assertAnswer("2", "INSERT INTO a VALUES (1, 1, 'x'), (2, 1, 'y')");
        assertAnswer("View P01 successfully created.",
            "CREATE OR REPLACE VIEW p01 AS SELECT DISTINCT g FROM a GROUP BY g");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 30: | Change tracking is not supported on "
                + "queries with DISTINCT. Remove the DISTINCT keyword from the query or from the definition of the "
                + "view that contains it. (inside view 'TEST_DB.TEST_SCHEMA.P01').",
            "CREATE OR REPLACE STREAM s_p01 ON VIEW p01");
        assertAnswer("View P02 successfully created.",
            "CREATE OR REPLACE VIEW p02 AS SELECT g FROM a GROUP BY g QUALIFY ROW_NUMBER() OVER (ORDER BY g) = 1");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 46: | Change tracking is not supported on "
                + "queries with GROUP BY. Remove the GROUP BY clause from the query or from the definition of the "
                + "view that contains it. (inside view 'TEST_DB.TEST_SCHEMA.P02').",
            "CREATE OR REPLACE STREAM s_p02 ON VIEW p02");
        assertAnswer("View P03 successfully created.",
            "CREATE OR REPLACE VIEW p03 AS SELECT DISTINCT id FROM a QUALIFY ROW_NUMBER() OVER (ORDER BY id) = 1");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 30: | Change tracking is not supported on "
                + "queries with DISTINCT. Remove the DISTINCT keyword from the query or from the definition of the "
                + "view that contains it. (inside view 'TEST_DB.TEST_SCHEMA.P03').",
            "CREATE OR REPLACE STREAM s_p03 ON VIEW p03");
        assertAnswer("View P04 successfully created.",
            "CREATE OR REPLACE VIEW p04 AS SELECT g FROM a GROUP BY g LIMIT 1");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 46: | Change tracking is not supported on "
                + "queries with GROUP BY. Remove the GROUP BY clause from the query or from the definition of the "
                + "view that contains it. (inside view 'TEST_DB.TEST_SCHEMA.P04').",
            "CREATE OR REPLACE STREAM s_p04 ON VIEW p04");
        assertAnswer("View P05 successfully created.",
            "CREATE OR REPLACE VIEW p05 AS SELECT DISTINCT id FROM a LIMIT 1");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 30: | Change tracking is not supported on "
                + "queries with DISTINCT. Remove the DISTINCT keyword from the query or from the definition of the "
                + "view that contains it. (inside view 'TEST_DB.TEST_SCHEMA.P05').",
            "CREATE OR REPLACE STREAM s_p05 ON VIEW p05");
        assertAnswer("View P06 successfully created.",
            "CREATE OR REPLACE VIEW p06 AS SELECT ROW_NUMBER() OVER (ORDER BY id) rn FROM a LIMIT 1");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with LIMIT. (inside view 'TEST_DB.TEST_SCHEMA.P06').",
            "CREATE OR REPLACE STREAM s_p06 ON VIEW p06");
        assertAnswer("View P07 successfully created.",
            "CREATE OR REPLACE VIEW p07 AS SELECT COUNT(*) c FROM a LIMIT 1");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with LIMIT. (inside view 'TEST_DB.TEST_SCHEMA.P07').",
            "CREATE OR REPLACE STREAM s_p07 ON VIEW p07");
        assertAnswer("View P08 successfully created.",
            "CREATE OR REPLACE VIEW p08 AS SELECT id FROM a UNION SELECT ROW_NUMBER() OVER (ORDER BY id) FROM a");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with joins of type '[UNION]'. (inside view 'TEST_DB.TEST_SCHEMA.P08').",
            "CREATE OR REPLACE STREAM s_p08 ON VIEW p08");
        assertAnswer("View P09 successfully created.",
            "CREATE OR REPLACE VIEW p09 AS SELECT g FROM a GROUP BY g UNION SELECT id FROM a");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with joins of type '[UNION]'. (inside view 'TEST_DB.TEST_SCHEMA.P09').",
            "CREATE OR REPLACE STREAM s_p09 ON VIEW p09");
        assertAnswer("View P10 successfully created.",
            "CREATE OR REPLACE VIEW p10 AS SELECT a.id, ROW_NUMBER() OVER (ORDER BY a.id) rn FROM a LEFT JOIN a b ON "
                + "a.id = b.id");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 30: | Change tracking is not supported on "
                + "queries with window functions. (inside view 'TEST_DB.TEST_SCHEMA.P10').",
            "CREATE OR REPLACE STREAM s_p10 ON VIEW p10");
        assertAnswer("View P11 successfully created.",
            "CREATE OR REPLACE VIEW p11 AS SELECT a.g FROM a LEFT JOIN a b ON a.id = b.id GROUP BY a.g");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 77: | Change tracking is not supported on "
                + "queries with GROUP BY. Remove the GROUP BY clause from the query or from the definition of the "
                + "view that contains it. (inside view 'TEST_DB.TEST_SCHEMA.P11').",
            "CREATE OR REPLACE STREAM s_p11 ON VIEW p11");
        assertAnswer("View P12 successfully created.",
            "CREATE OR REPLACE VIEW p12 AS SELECT g, (SELECT 1) o FROM a GROUP BY g");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 60: | Change tracking is not supported on "
                + "queries with GROUP BY. Remove the GROUP BY clause from the query or from the definition of the "
                + "view that contains it. (inside view 'TEST_DB.TEST_SCHEMA.P12').",
            "CREATE OR REPLACE STREAM s_p12 ON VIEW p12");
        assertAnswer("View P13 successfully created.",
            "CREATE OR REPLACE VIEW p13 AS SELECT ROW_NUMBER() OVER (ORDER BY 1) rn");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 30: | Change tracking is not supported on "
                + "queries with window functions. (inside view 'TEST_DB.TEST_SCHEMA.P13').",
            "CREATE OR REPLACE STREAM s_p13 ON VIEW p13");
        assertAnswer("View P14 successfully created.",
            "CREATE OR REPLACE VIEW p14 AS SELECT f.value v, ROW_NUMBER() OVER (ORDER BY 1) rn FROM "
                + "TABLE(FLATTEN(INPUT => [1, 2])) f");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 93: | Change tracking is not supported on "
                + "queries with 'Table Function'.",
            "CREATE OR REPLACE STREAM s_p14 ON VIEW p14");
        assertAnswer("View P15 successfully created.",
            "CREATE OR REPLACE VIEW p15 AS SELECT COUNT(*) c, (SELECT 1) o FROM a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 37: | Query contains the function 'COUNT(*)', "
                + "but change tracking is not supported on queries with scalar aggregate functions. (inside view "
                + "'TEST_DB.TEST_SCHEMA.P15').",
            "CREATE OR REPLACE STREAM s_p15 ON VIEW p15");
        assertAnswer("View P16 successfully created.",
            "CREATE OR REPLACE VIEW p16 AS SELECT (SELECT 1) o, COUNT(*) c FROM a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 38: | Change tracking is not supported on "
                + "queries with subquery expressions. (inside view 'TEST_DB.TEST_SCHEMA.P16').",
            "CREATE OR REPLACE STREAM s_p16 ON VIEW p16");
        assertAnswer("View P17 successfully created.",
            "CREATE OR REPLACE VIEW p17 AS SELECT ROW_NUMBER() OVER (ORDER BY id) rn FROM a QUALIFY rn = 1");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 79: | Change tracking is not supported on "
                + "queries with QUALIFY. (inside view 'TEST_DB.TEST_SCHEMA.P17').",
            "CREATE OR REPLACE STREAM s_p17 ON VIEW p17");
        assertAnswer("View P18 successfully created.",
            "CREATE OR REPLACE VIEW p18 AS SELECT COUNT(*) c FROM a HAVING COUNT(*) > 0");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 37: | Query contains the function 'COUNT(*)', "
                + "but change tracking is not supported on queries with scalar aggregate functions. (inside view "
                + "'TEST_DB.TEST_SCHEMA.P18').",
            "CREATE OR REPLACE STREAM s_p18 ON VIEW p18");
        assertAnswer("View P19 successfully created.",
            "CREATE OR REPLACE VIEW p19 AS SELECT g, COUNT(*) c FROM a GROUP BY ALL");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 58: | Change tracking is not supported on "
                + "queries with GROUP BY. Remove the GROUP BY clause from the query or from the definition of the "
                + "view that contains it. (inside view 'TEST_DB.TEST_SCHEMA.P19').",
            "CREATE OR REPLACE STREAM s_p19 ON VIEW p19");
        assertAnswer("View P20 successfully created.",
            "CREATE OR REPLACE VIEW p20 AS SELECT id FROM a ORDER BY ROW_NUMBER() OVER (ORDER BY id)");
        assertAnswer("Stream S_P20 successfully created.", "CREATE OR REPLACE STREAM s_p20 ON VIEW p20");
        assertAnswer("View P21 successfully created.",
            "CREATE OR REPLACE VIEW p21 AS SELECT id FROM a INTERSECT SELECT id FROM a");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with joins of type '[INTERSECT]'. (inside view 'TEST_DB.TEST_SCHEMA.P21').",
            "CREATE OR REPLACE STREAM s_p21 ON VIEW p21");
        assertAnswer("View P22 successfully created.",
            "CREATE OR REPLACE VIEW p22 AS SELECT id FROM a MINUS SELECT id FROM a");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with joins of type '[MINUS]'. (inside view 'TEST_DB.TEST_SCHEMA.P22').",
            "CREATE OR REPLACE STREAM s_p22 ON VIEW p22");
        assertAnswer("View P23 successfully created.",
            "CREATE OR REPLACE VIEW p23 AS SELECT a.id FROM a RIGHT JOIN a b ON a.id = b.id");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with joins of type '[RIGHT_OUTER_JOIN, LEFT_OUTER_JOIN]'. (inside view "
                + "'TEST_DB.TEST_SCHEMA.P23').",
            "CREATE OR REPLACE STREAM s_p23 ON VIEW p23");
        assertAnswer("View P24 successfully created.",
            "CREATE OR REPLACE VIEW p24 AS SELECT a.id FROM a FULL JOIN a b ON a.id = b.id");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with joins of type '[FULL_OUTER_JOIN]'. (inside view 'TEST_DB.TEST_SCHEMA.P24').",
            "CREATE OR REPLACE STREAM s_p24 ON VIEW p24");
        assertAnswer("View P25 successfully created.",
            "CREATE OR REPLACE VIEW p25 AS SELECT id FROM a START WITH id = 1 CONNECT BY PRIOR id = g");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "recursive queries (e.g., with CONNECT BY or recursive CTE). (inside view "
                + "'TEST_DB.TEST_SCHEMA.P25').",
            "CREATE OR REPLACE STREAM s_p25 ON VIEW p25");
        assertAnswer("View P26 successfully created.", "CREATE OR REPLACE VIEW p26 AS SELECT id FROM a SAMPLE (50)");
        assertAnswer("View P27 successfully created.",
            "CREATE OR REPLACE VIEW p27 AS SELECT g FROM a GROUP BY ROLLUP (g)");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 46: | Change tracking is not supported on "
                + "queries with GROUP BY GROUPING SETS/CUBE/ROLLUP. Remove the GROUP BY GROUPING SETS/CUBE/ROLLUP "
                + "clause from the query or from the definition of the view that contains it. (inside view "
                + "'TEST_DB.TEST_SCHEMA.P27').",
            "CREATE OR REPLACE STREAM s_p27 ON VIEW p27");
        assertAnswer("View P28 successfully created.",
            "CREATE OR REPLACE VIEW p28 AS SELECT id FROM a WHERE id > 0 LIMIT 1");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with LIMIT. (inside view 'TEST_DB.TEST_SCHEMA.P28').",
            "CREATE OR REPLACE STREAM s_p28 ON VIEW p28");
        assertAnswer("View P29 successfully created.",
            "CREATE OR REPLACE VIEW p29 AS SELECT id FROM a UNION SELECT id FROM a LIMIT 1");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with LIMIT. (inside view 'TEST_DB.TEST_SCHEMA.P29').",
            "CREATE OR REPLACE STREAM s_p29 ON VIEW p29");
        assertAnswer("View P30 successfully created.",
            "CREATE OR REPLACE VIEW p30 AS SELECT a.id FROM a LEFT JOIN a b ON a.id = b.id UNION SELECT id FROM a");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with joins of type '[UNION]'. (inside view 'TEST_DB.TEST_SCHEMA.P30').",
            "CREATE OR REPLACE STREAM s_p30 ON VIEW p30");
        assertAnswer("View P31 successfully created.",
            "CREATE OR REPLACE VIEW p31 AS SELECT DISTINCT a.id FROM a LEFT JOIN a b ON a.id = b.id");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 30: | Change tracking is not supported on "
                + "queries with DISTINCT. Remove the DISTINCT keyword from the query or from the definition of the "
                + "view that contains it. (inside view 'TEST_DB.TEST_SCHEMA.P31').",
            "CREATE OR REPLACE STREAM s_p31 ON VIEW p31");
        assertAnswer("View P32 successfully created.",
            "CREATE OR REPLACE VIEW p32 AS SELECT a.id, (SELECT 1) o FROM a LEFT JOIN a b ON a.id = b.id");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with joins of type '[LEFT_OUTER_JOIN]'. (inside view 'TEST_DB.TEST_SCHEMA.P32').",
            "CREATE OR REPLACE STREAM s_p32 ON VIEW p32");
        assertAnswer("View P33 successfully created.",
            "CREATE OR REPLACE VIEW p33 AS SELECT COUNT(*) c FROM a LEFT JOIN a b ON a.id = b.id");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with joins of type '[LEFT_OUTER_JOIN]'. (inside view 'TEST_DB.TEST_SCHEMA.P33').",
            "CREATE OR REPLACE STREAM s_p33 ON VIEW p33");
        assertAnswer("View Q01 successfully created.", "CREATE OR REPLACE VIEW q01 AS SELECT AVG(id) v FROM a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 37: | Query contains the function 'SUM(A.ID)',"
                + " but change tracking is not supported on queries with scalar aggregate functions. (inside view "
                + "'TEST_DB.TEST_SCHEMA.Q01').",
            "CREATE OR REPLACE STREAM s_q01 ON VIEW q01");
        assertAnswer("View Q02 successfully created.", "CREATE OR REPLACE VIEW q02 AS SELECT COUNT(id) v FROM a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 37: | Query contains the function "
                + "'COUNT(A.ID)', but change tracking is not supported on queries with scalar aggregate functions. "
                + "(inside view 'TEST_DB.TEST_SCHEMA.Q02').",
            "CREATE OR REPLACE STREAM s_q02 ON VIEW q02");
        assertAnswer("View Q03 successfully created.", "CREATE OR REPLACE VIEW q03 AS SELECT SUM(id + 1) v FROM a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 37: | Query contains the function 'SUM(A.ID + "
                + "1)', but change tracking is not supported on queries with scalar aggregate functions. (inside view "
                + "'TEST_DB.TEST_SCHEMA.Q03').",
            "CREATE OR REPLACE STREAM s_q03 ON VIEW q03");
        assertAnswer("View Q04 successfully created.", "CREATE OR REPLACE VIEW q04 AS SELECT SUM(x.id) v FROM a x");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 37: | Query contains the function 'SUM(X.ID)',"
                + " but change tracking is not supported on queries with scalar aggregate functions. (inside view "
                + "'TEST_DB.TEST_SCHEMA.Q04').",
            "CREATE OR REPLACE STREAM s_q04 ON VIEW q04");
        assertAnswer("View Q05 successfully created.", "CREATE OR REPLACE VIEW q05 AS SELECT ARRAY_AGG(id) v FROM a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 37: | Query contains the function "
                + "'ARRAY_AGG(A.ID)', but change tracking is not supported on queries with scalar aggregate "
                + "functions. (inside view 'TEST_DB.TEST_SCHEMA.Q05').",
            "CREATE OR REPLACE STREAM s_q05 ON VIEW q05");
        assertAnswer("View Q06 successfully created.", "CREATE OR REPLACE VIEW q06 AS SELECT LISTAGG(s) v FROM a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 37: | Query contains the function "
                + "'LISTAGG(A.S)', but change tracking is not supported on queries with scalar aggregate functions. "
                + "(inside view 'TEST_DB.TEST_SCHEMA.Q06').",
            "CREATE OR REPLACE STREAM s_q06 ON VIEW q06");
        assertAnswer("View Q07 successfully created.",
            "CREATE OR REPLACE VIEW q07 AS SELECT COUNT(*) + SUM(id) v FROM a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 37: | Query contains the function 'COUNT(*)', "
                + "but change tracking is not supported on queries with scalar aggregate functions. (inside view "
                + "'TEST_DB.TEST_SCHEMA.Q07').",
            "CREATE OR REPLACE STREAM s_q07 ON VIEW q07");
        assertAnswer("View Q08 successfully created.", "CREATE OR REPLACE VIEW q08 AS SELECT 1 + SUM(id) v FROM a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 41: | Query contains the function 'SUM(A.ID)',"
                + " but change tracking is not supported on queries with scalar aggregate functions. (inside view "
                + "'TEST_DB.TEST_SCHEMA.Q08').",
            "CREATE OR REPLACE STREAM s_q08 ON VIEW q08");
        assertAnswer("View Q09 successfully created.",
            "CREATE OR REPLACE VIEW q09 AS SELECT SUM(id) v FROM test_db.test_schema.a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 37: | Query contains the function 'SUM(A.ID)',"
                + " but change tracking is not supported on queries with scalar aggregate functions. (inside view "
                + "'TEST_DB.TEST_SCHEMA.Q09').",
            "CREATE OR REPLACE STREAM s_q09 ON VIEW q09");
        assertAnswer("View Q10 successfully created.",
            "CREATE OR REPLACE VIEW q10 AS SELECT BOOLAND_AGG(id > 0) v FROM a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 37: | Query contains the function "
                + "'MIN(CAST(A.ID > 0 AS BOOLEAN))', but change tracking is not supported on queries with scalar "
                + "aggregate functions. (inside view 'TEST_DB.TEST_SCHEMA.Q10').",
            "CREATE OR REPLACE STREAM s_q10 ON VIEW q10");
        assertAnswer("View Q11 successfully created.",
            "CREATE OR REPLACE VIEW q11 AS SELECT MIN(s) v FROM a WHERE id > 1");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 37: | Query contains the function 'MIN(A.S)', "
                + "but change tracking is not supported on queries with scalar aggregate functions. (inside view "
                + "'TEST_DB.TEST_SCHEMA.Q11').",
            "CREATE OR REPLACE STREAM s_q11 ON VIEW q11");
        assertAnswer("View Q12 successfully created.", "CREATE OR REPLACE VIEW q12 AS SELECT COUNT(1) v FROM a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 37: | Query contains the function 'COUNT(1)', "
                + "but change tracking is not supported on queries with scalar aggregate functions. (inside view "
                + "'TEST_DB.TEST_SCHEMA.Q12').",
            "CREATE OR REPLACE STREAM s_q12 ON VIEW q12");
        assertAnswer("View Q13 successfully created.", "CREATE OR REPLACE VIEW q13 AS SELECT MAX(UPPER(s)) v FROM a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 37: | Query contains the function "
                + "'MAX(UPPER(A.S))', but change tracking is not supported on queries with scalar aggregate "
                + "functions. (inside view 'TEST_DB.TEST_SCHEMA.Q13').",
            "CREATE OR REPLACE STREAM s_q13 ON VIEW q13");
    }

    @Test
    public void aStreamRefusesTableFunctionsFirst() {
        assertAnswer("Table A successfully created.", "CREATE OR REPLACE TABLE a (id INT, g INT, s STRING)");
        assertAnswer("2", "INSERT INTO a VALUES (1, 1, 'x'), (2, 1, 'y')");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 53: | Change tracking is not supported on "
                + "queries with DISTINCT. Remove the DISTINCT keyword from the query or from the definition of the "
                + "view that contains it.",
            "CREATE OR REPLACE VIEW r01 CHANGE_TRACKING = TRUE AS SELECT DISTINCT g FROM a GROUP BY g");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with LIMIT.",
            "CREATE OR REPLACE VIEW r02 CHANGE_TRACKING = TRUE AS SELECT ROW_NUMBER() OVER (ORDER BY id) rn FROM a "
                + "LIMIT 1");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 53: | Change tracking is not supported on "
                + "queries with window functions.",
            "CREATE OR REPLACE VIEW r03 CHANGE_TRACKING = TRUE AS SELECT a.id, ROW_NUMBER() OVER (ORDER BY a.id) rn "
                + "FROM a LEFT JOIN a b ON a.id = b.id");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 53: | Change tracking is not supported on "
                + "queries with window functions.",
            "CREATE OR REPLACE VIEW r04 CHANGE_TRACKING = TRUE AS SELECT ROW_NUMBER() OVER (ORDER BY 1) rn");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with 'TABLE_FUNCTION' inside views, saw 'FLATTEN'.",
            "CREATE OR REPLACE VIEW r05 CHANGE_TRACKING = TRUE AS SELECT f.value v FROM TABLE(FLATTEN(INPUT => [1, "
                + "2])) f");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 53: | Change tracking is not supported on "
                + "queries with window functions.",
            "CREATE OR REPLACE VIEW r06 CHANGE_TRACKING = TRUE AS SELECT f.value v, ROW_NUMBER() OVER (ORDER BY 1) "
                + "rn FROM TABLE(FLATTEN(INPUT => [1, 2])) f");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with joins of type '[UNION]'.",
            "CREATE OR REPLACE VIEW r07 CHANGE_TRACKING = TRUE AS SELECT g FROM a GROUP BY g UNION SELECT id FROM a");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with LIMIT.",
            "CREATE OR REPLACE VIEW r08 CHANGE_TRACKING = TRUE AS SELECT id FROM a UNION SELECT id FROM a LIMIT 1");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 60: | Query contains the function 'COUNT(*)', "
                + "but change tracking is not supported on queries with scalar aggregate functions.",
            "CREATE OR REPLACE VIEW r09 CHANGE_TRACKING = TRUE AS SELECT COUNT(*) c, (SELECT 1) o FROM a");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with LIMIT.",
            "CREATE OR REPLACE VIEW r10 CHANGE_TRACKING = TRUE AS SELECT id FROM a QUALIFY ROW_NUMBER() OVER (ORDER "
                + "BY id) = 1 LIMIT 1");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 53: | Change tracking is not supported on "
                + "queries with window functions.",
            "CREATE OR REPLACE VIEW r11 CHANGE_TRACKING = TRUE AS SELECT ROW_NUMBER() OVER (ORDER BY 1) rn FROM "
                + "VALUES (1)");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "recursive queries (e.g., with CONNECT BY or recursive CTE).",
            "CREATE OR REPLACE VIEW r12 CHANGE_TRACKING = TRUE AS SELECT id FROM a START WITH id = 1 CONNECT BY "
                + "PRIOR id = g");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with joins of type '[RIGHT_OUTER_JOIN, LEFT_OUTER_JOIN]'.",
            "CREATE OR REPLACE VIEW r13 CHANGE_TRACKING = TRUE AS SELECT a.id FROM a RIGHT JOIN a b ON a.id = b.id");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 60: | Query contains the function 'SUM(A.ID)',"
                + " but change tracking is not supported on queries with scalar aggregate functions.",
            "CREATE OR REPLACE VIEW r14 CHANGE_TRACKING = TRUE AS SELECT SUM(id) s FROM a");
        assertAnswer("View XR05 successfully created.",
            "CREATE OR REPLACE VIEW xr05 AS SELECT f.value v FROM TABLE(FLATTEN(INPUT => [1, 2])) f");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with 'TABLE_FUNCTION' inside views, saw 'FLATTEN'. (inside view "
                + "'TEST_DB.TEST_SCHEMA.XR05').",
            "ALTER VIEW xr05 SET CHANGE_TRACKING = TRUE");
        assertAnswer("View XR06 successfully created.",
            "CREATE OR REPLACE VIEW xr06 AS SELECT f.value v, ROW_NUMBER() OVER (ORDER BY 1) rn FROM "
                + "TABLE(FLATTEN(INPUT => [1, 2])) f");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 31: | Change tracking is not supported on "
                + "queries with window functions. (inside view 'TEST_DB.TEST_SCHEMA.XR06').",
            "ALTER VIEW xr06 SET CHANGE_TRACKING = TRUE");
        assertAnswer("View W13 successfully created.",
            "CREATE OR REPLACE VIEW w13 AS SELECT SEQ4() s FROM TABLE(GENERATOR(ROWCOUNT => 1))");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 57: | Change tracking is not supported on "
                + "queries with 'Table Function'.",
            "CREATE OR REPLACE STREAM s_w13 ON VIEW w13");
        assertAnswer("View W14 successfully created.",
            "CREATE OR REPLACE VIEW w14 AS SELECT f.value v FROM TABLE(FLATTEN(INPUT => [1, 2])) f");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 58: | Change tracking is not supported on "
                + "queries with 'Table Function'.",
            "CREATE OR REPLACE STREAM s_w14 ON VIEW w14");
        assertAnswer("View W15 successfully created.",
            "CREATE OR REPLACE VIEW w15 AS SELECT f.value v FROM a, LATERAL FLATTEN(INPUT => [a.id]) f");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 63: | Change tracking is not supported on "
                + "queries with 'Table Function'.",
            "CREATE OR REPLACE STREAM s_w15 ON VIEW w15");
        assertAnswer("View W16 successfully created.",
            "CREATE OR REPLACE VIEW w16 AS SELECT id FROM a QUALIFY ROW_NUMBER() OVER (ORDER BY id) = 1 LIMIT 1");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with LIMIT. (inside view 'TEST_DB.TEST_SCHEMA.W16').",
            "CREATE OR REPLACE STREAM s_w16 ON VIEW w16");
        assertAnswer("View W17 successfully created.",
            "CREATE OR REPLACE VIEW w17 AS SELECT ROW_NUMBER() OVER (ORDER BY 1) rn FROM VALUES (1)");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 30: | Change tracking is not supported on "
                + "queries with window functions. (inside view 'TEST_DB.TEST_SCHEMA.W17').",
            "CREATE OR REPLACE STREAM s_w17 ON VIEW w17");
        assertAnswer("View W18 successfully created.", "CREATE OR REPLACE VIEW w18 AS SELECT 1 AS a");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 30: | Change tracking is not supported on "
                + "queries with 'VALUES'. (inside view 'TEST_DB.TEST_SCHEMA.W18').",
            "CREATE OR REPLACE STREAM s_w18 ON VIEW w18");
        assertAnswer("View W19 successfully created.", "CREATE OR REPLACE VIEW w19 AS SELECT a FROM (SELECT 1 AS a)");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 45: | Change tracking is not supported on "
                + "queries with 'VALUES'. (inside view 'TEST_DB.TEST_SCHEMA.W19').",
            "CREATE OR REPLACE STREAM s_w19 ON VIEW w19");
        assertAnswer("View W20 successfully created.",
            "CREATE OR REPLACE VIEW w20 AS SELECT a.id FROM a LEFT JOIN a b ON a.id = b.id LEFT JOIN a c ON a.id = "
                + "c.id FULL JOIN a d ON a.id = d.id");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with joins of type '[FULL_OUTER_JOIN]'. (inside view 'TEST_DB.TEST_SCHEMA.W20').",
            "CREATE OR REPLACE STREAM s_w20 ON VIEW w20");
        assertAnswer("View W21 successfully created.",
            "CREATE OR REPLACE VIEW w21 AS SELECT id FROM a EXCEPT SELECT id FROM a");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with joins of type '[MINUS]'. (inside view 'TEST_DB.TEST_SCHEMA.W21').",
            "CREATE OR REPLACE STREAM s_w21 ON VIEW w21");
        assertAnswer("View W22 successfully created.",
            "CREATE OR REPLACE VIEW w22 AS SELECT id FROM a UNION ALL SELECT id FROM a UNION SELECT id FROM a");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with joins of type '[UNION]'. (inside view 'TEST_DB.TEST_SCHEMA.W22').",
            "CREATE OR REPLACE STREAM s_w22 ON VIEW w22");
        assertAnswer("View W23 successfully created.",
            "CREATE OR REPLACE VIEW w23 AS SELECT a.id FROM a NATURAL JOIN a b");
        answer("CREATE OR REPLACE STREAM s_w23 ON VIEW w23");
        assertAnswer("View W24 successfully created.",
            "CREATE OR REPLACE VIEW w24 AS WITH RECURSIVE r (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < "
                + "3) SELECT n FROM r");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 55: | Change tracking is not supported on "
                + "queries with 'VALUES'. (inside view 'TEST_DB.TEST_SCHEMA.W24').",
            "CREATE OR REPLACE STREAM s_w24 ON VIEW w24");
        assertAnswer("View W25 successfully created.",
            "CREATE OR REPLACE VIEW w25 AS SELECT id FROM a, (SELECT 1 AS b) x");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 49: | Change tracking is not supported on "
                + "queries with 'VALUES'. (inside view 'TEST_DB.TEST_SCHEMA.W25').",
            "CREATE OR REPLACE STREAM s_w25 ON VIEW w25");
        assertAnswer("View W26 successfully created.", "CREATE OR REPLACE VIEW w26 AS SELECT * FROM VALUES (1)");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with 'VALUES'. (inside view 'TEST_DB.TEST_SCHEMA.W26').",
            "CREATE OR REPLACE STREAM s_w26 ON VIEW w26");
        assertAnswer("View W27 successfully created.",
            "CREATE OR REPLACE VIEW w27 AS SELECT id FROM a WHERE id IN (SELECT id FROM a)");
        assertAnswer("REFUSED SQL compilation error: line 0 at position -1: | Change tracking is not supported on "
                + "queries with subquery expressions. (inside view 'TEST_DB.TEST_SCHEMA.W27').",
            "CREATE OR REPLACE STREAM s_w27 ON VIEW w27");
        assertAnswer("View W28 successfully created.",
            "CREATE OR REPLACE VIEW w28 AS SELECT id FROM a WHERE EXISTS (SELECT 1 FROM a b WHERE b.id = a.id)");
        assertAnswer("REFUSED SQL compilation error: line 1 at position 53: | Change tracking is not supported on "
                + "queries with subquery expressions. (inside view 'TEST_DB.TEST_SCHEMA.W28').",
            "CREATE OR REPLACE STREAM s_w28 ON VIEW w28");
    }
}
