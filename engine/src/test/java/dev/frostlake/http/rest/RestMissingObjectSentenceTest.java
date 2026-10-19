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
package dev.frostlake.http.rest;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The 404 a resource answers for an object that is not there carries the sentence a statement over it refuses with,
 * in lower case: the kind, the name (a schema's object by its full path, an account's by its own) and the privilege
 * the session's role would need on it. A request under a database or a schema that is not there names that database
 * or schema instead. Every sentence is the account's own, measured through its API with a role of its own.
 */
public class RestMissingObjectSentenceTest extends BaseRestTest {

    /** The method, the path and the message of each request. */
    private static final String[][] REQUESTS = {
        {"GET", "/api/v2/warehouses/NOSUCH_WH_R7REST",
            "\nwarehouse 'nosuch_wh_r7rest' does not exist or not authorized. your primary role <ROLE> must "
                + "have at least one privilege granted on warehouse nosuch_wh_r7rest."},
        {"GET", "/api/v2/databases/NOSUCH_DB_R7REST",
            "\ndatabase 'nosuch_db_r7rest' does not exist or not authorized. your primary role <ROLE> must "
                + "have usage or any other privilege granted on database nosuch_db_r7rest."},
        {"DELETE", "/api/v2/databases/NOSUCH_DB_R7REST",
            "\ndatabase 'nosuch_db_r7rest' does not exist or not authorized. your primary role <ROLE> must "
                + "have usage or any other privilege granted on database nosuch_db_r7rest."},
        {"GET", "/api/v2/databases/NOSUCH_DB_R7REST/schemas/PUBLIC",
            "\ndatabase 'nosuch_db_r7rest' does not exist or not authorized. your primary role <ROLE> must "
                + "have usage or any other privilege granted on database nosuch_db_r7rest."},
        {"GET", "/api/v2/databases/REST_MS/schemas/NOSUCH_S_R7REST",
            "\nschema 'rest_ms.nosuch_s_r7rest' does not exist or not authorized. your primary role <ROLE> "
                + "must have usage or any other privilege granted on schema rest_ms.nosuch_s_r7rest."},
        {"GET", "/api/v2/databases/REST_MS/schemas/S1/tables/NOSUCH_T_R7REST",
            "\ntable 'rest_ms.s1.nosuch_t_r7rest' does not exist or not authorized. your primary role "
                + "<ROLE> must have at least one privilege granted on table rest_ms.s1.nosuch_t_r7rest."},
        {"GET", "/api/v2/roles/NOSUCH_ROLE_R7REST/grants",
            "\nrole 'nosuch_role_r7rest' does not exist or not authorized. your primary role <ROLE> must "
                + "have at least one privilege granted on role nosuch_role_r7rest."},
        {"GET", "/api/v2/databases/NOSUCH_DB_R7REST/schemas/PUBLIC/tables/T",
            "\ndatabase 'nosuch_db_r7rest' does not exist or not authorized. your primary role <ROLE> must "
                + "have usage or any other privilege granted on database nosuch_db_r7rest."},
        {"GET", "/api/v2/databases/REST_MS/schemas/NOSUCH_S_R7REST/tables/T",
            "\nschema 'rest_ms.nosuch_s_r7rest' does not exist or not authorized. your primary role <ROLE> "
                + "must have usage or any other privilege granted on schema rest_ms.nosuch_s_r7rest."},
        {"GET", "/api/v2/databases/REST_MS/schemas/S1/views/NOSUCH_V_R7REST",
            "\nview 'rest_ms.s1.nosuch_v_r7rest' does not exist or not authorized. your primary role <ROLE> "
                + "must have at least one privilege granted on table rest_ms.s1.nosuch_v_r7rest."},
        {"GET", "/api/v2/databases/REST_MS/schemas/S1/streams/NOSUCH_SM_R7REST",
            "\nstream 'rest_ms.s1.nosuch_sm_r7rest' does not exist or not authorized. your primary role "
                + "<ROLE> must have at least one privilege granted on stream rest_ms.s1.nosuch_sm_r7rest."},
        {"GET", "/api/v2/databases/REST_MS/schemas/S1/tasks/NOSUCH_TK_R7REST",
            "\ntask 'rest_ms.s1.nosuch_tk_r7rest' does not exist or not authorized. your primary role "
                + "<ROLE> must have at least one privilege granted on task rest_ms.s1.nosuch_tk_r7rest."},
        {"GET", "/api/v2/databases/REST_MS/schemas/S1/pipes/NOSUCH_PP_R7REST",
            "\npipe 'rest_ms.s1.nosuch_pp_r7rest' does not exist or not authorized. your primary role "
                + "<ROLE> must have at least one privilege granted on pipe rest_ms.s1.nosuch_pp_r7rest."},
        {"GET", "/api/v2/databases/REST_MS/schemas/S1/stages/NOSUCH_ST_R7REST",
            "\nstage 'rest_ms.s1.nosuch_st_r7rest' does not exist or not authorized. your primary role "
                + "<ROLE> must have at least one privilege granted on stage rest_ms.s1.nosuch_st_r7rest."},
        {"GET", "/api/v2/databases/REST_MS/schemas/S1/dynamic-tables/NOSUCH_DT_R7REST",
            "\ndynamic table 'rest_ms.s1.nosuch_dt_r7rest' does not exist or not authorized. your primary "
                + "role <ROLE> must have at least one privilege granted on table rest_ms.s1.nosuch_dt_r7rest."},
        {"GET", "/api/v2/databases/REST_MS/schemas/S1/event-tables/NOSUCH_ET_R7REST",
            "\nevent table 'rest_ms.s1.nosuch_et_r7rest' does not exist or not authorized. your primary "
                + "role <ROLE> must have at least one privilege granted on table rest_ms.s1.nosuch_et_r7rest."},
        {"GET", "/api/v2/databases/REST_MS/schemas/S1/alerts/NOSUCH_AL_R7REST",
            "\nalert 'rest_ms.s1.nosuch_al_r7rest' does not exist or not authorized. your primary role "
                + "<ROLE> must have at least one privilege granted on alert rest_ms.s1.nosuch_al_r7rest."},
        {"GET", "/api/v2/databases/REST_MS/schemas/S1/functions/NOSUCH_F_R7REST()",
            "\nfunction 'rest_ms.s1.nosuch_f_r7rest' does not exist or not authorized. your primary role "
                + "<ROLE> must have at least one privilege granted on function rest_ms.s1.nosuch_f_r7rest."},
        {"GET", "/api/v2/databases/REST_MS/schemas/S1/procedures/NOSUCH_P_R7REST()",
            "\nprocedure 'rest_ms.s1.nosuch_p_r7rest' does not exist or not authorized. your primary role "
                + "<ROLE> must have at least one privilege granted on procedure rest_ms.s1.nosuch_p_r7rest."},
        {"GET", "/api/v2/databases/REST_MS/schemas/S1/user-defined-functions/NOSUCH_F_R7REST()",
            "\nfunction 'rest_ms.s1.nosuch_f_r7rest' does not exist or not authorized. your primary role "
                + "<ROLE> must have at least one privilege granted on function rest_ms.s1.nosuch_f_r7rest."},
        {"GET", "/api/v2/databases/REST_MS/schemas/S1/iceberg-tables/NOSUCH_IT_R7REST",
            "\niceberg table 'rest_ms.s1.nosuch_it_r7rest' does not exist or not authorized. your primary "
                + "role <ROLE> must have at least one privilege granted on table rest_ms.s1.nosuch_it_r7rest."},
        {"GET", "/api/v2/databases/REST_MS/schemas/S1/notebooks/NOSUCH_NB_R7REST",
            "\nnotebook 'rest_ms.s1.nosuch_nb_r7rest' does not exist or not authorized. your primary role "
                + "<ROLE> must have at least one privilege granted on notebook rest_ms.s1.nosuch_nb_r7rest."},
        {"GET", "/api/v2/databases/REST_MS/schemas/S1/services/NOSUCH_SV_R7REST",
            "\nservice 'rest_ms.s1.nosuch_sv_r7rest' does not exist or not authorized. your primary role "
                + "<ROLE> must have at least one privilege granted on service rest_ms.s1.nosuch_sv_r7rest."},
        {"GET", "/api/v2/databases/REST_MS/schemas/S1/image-repositories/NOSUCH_IR_R7REST",
            "\nimage repository 'rest_ms.s1.nosuch_ir_r7rest' does not exist or not authorized. your "
                + "primary role <ROLE> must have at least one privilege granted on stage "
                + "rest_ms.s1.nosuch_ir_r7rest."},
        {"GET", "/api/v2/databases/REST_MS/schemas/S1/cortex-search-services/NOSUCH_CS_R7REST",
            "\ncortex search service 'rest_ms.s1.nosuch_cs_r7rest' does not exist or not authorized. your "
                + "primary role <ROLE> must have at least one privilege granted on cortex search service "
                + "rest_ms.s1.nosuch_cs_r7rest."},
        {"GET", "/api/v2/users/NOSUCH_USER_R7REST",
            "\nuser 'nosuch_user_r7rest' does not exist or not authorized. your primary role <ROLE> must "
                + "have usage granted on account <ACCOUNT>."},
        {"GET", "/api/v2/roles/NOSUCH_ROLE_R7REST/grants-of",
            "\nrole 'nosuch_role_r7rest' does not exist or not authorized. your primary role <ROLE> must "
                + "have at least one privilege granted on role nosuch_role_r7rest."},
        {"GET", "/api/v2/compute-pools/NOSUCH_CP_R7REST",
            "\ncompute pool 'nosuch_cp_r7rest' does not exist or not authorized. your primary role <ROLE> "
                + "must have at least one privilege granted on compute pool nosuch_cp_r7rest."},
        {"GET", "/api/v2/external-volumes/NOSUCH_EV_R7REST",
            "\nexternal volume 'nosuch_ev_r7rest' does not exist or not authorized. your primary role "
                + "<ROLE> must have at least one privilege granted on volume nosuch_ev_r7rest."},
        {"GET", "/api/v2/network-policies/NOSUCH_NP_R7REST",
            "\nnetwork policy 'nosuch_np_r7rest' does not exist or not authorized. your primary role <ROLE> "
                + "must have at least one privilege granted on network policy nosuch_np_r7rest."},
        {"GET", "/api/v2/api-integrations/NOSUCH_AI_R7REST",
            "\nintegration 'nosuch_ai_r7rest' does not exist or not authorized. your primary role <ROLE> "
                + "must have at least one privilege granted on integration nosuch_ai_r7rest."},
        {"GET", "/api/v2/catalog-integrations/NOSUCH_CI_R7REST",
            "\nintegration 'nosuch_ci_r7rest' does not exist or not authorized. your primary role <ROLE> "
                + "must have at least one privilege granted on integration nosuch_ci_r7rest."},
        {"GET", "/api/v2/notification-integrations/NOSUCH_NI_R7REST",
            "\nintegration 'nosuch_ni_r7rest' does not exist or not authorized. your primary role <ROLE> "
                + "must have at least one privilege granted on integration nosuch_ni_r7rest."},
    };

    @BeforeAll
    public static void makeTheParents() {
        sql("CREATE DATABASE IF NOT EXISTS REST_MS");
        sql("CREATE SCHEMA IF NOT EXISTS REST_MS.S1");
    }

    @Test
    public void aMissingObjectIsNamedAsAStatementNamesIt() throws Exception {
        final String account = server.getEngine().getEngine().getCatalog().getAccountLocator().toLowerCase(Locale.ROOT);
        final String role = server.getEngine().getEngine().getSecurityManager().getSessionContext().getCurrentRole()
            .toLowerCase(Locale.ROOT);
        for (final String[] request : REQUESTS) {
            final String path = request[1].replace("(", "%28").replace(")", "%29");
            final HttpResponse<String> response = request(request[0], path, null);
            assertEquals(request[2].replace("<ACCOUNT>", account).replace("<ROLE>", role), error(404, response),
                request[0] + " " + request[1]);
            assertEquals("002003", json(response).path("code").asString(), request[1]);
        }
    }
}
