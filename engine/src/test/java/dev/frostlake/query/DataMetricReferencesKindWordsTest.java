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
 * Further words as DATA_METRIC_FUNCTION_REFERENCES' domain: each kind of object the account knows is no value for
 * the argument, named with an underscore between its words, and any other word an unknown domain (all
 * live-verified).
 */
public class DataMetricReferencesKindWordsTest extends MeasuredCellsTestSupport {

    @Test
    public void furtherKindsOfObject() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        assertAnswer("REFUSED Unknown domain: ANOMALY DETECTION.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'ANOMALY DETECTION'))");
        assertAnswer("REFUSED SQL compilation error: Invalid value 'APPLICATION_ROLE' for argument "
                + "'ref_entity_domain' for function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'APPLICATION ROLE'))");
        assertAnswer("REFUSED SQL compilation error: Invalid value 'APPLICATION_SERVICE' for argument "
                + "'ref_entity_domain' for function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'APPLICATION SERVICE'))");
        assertAnswer("REFUSED SQL compilation error: Invalid value 'ARTIFACT_REPOSITORY' for argument "
                + "'ref_entity_domain' for function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'ARTIFACT REPOSITORY'))");
        assertAnswer("REFUSED SQL compilation error: Invalid value 'CHANNEL' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'CHANNEL'))");
        assertAnswer("REFUSED SQL compilation error: Invalid value 'DCM_PROJECT' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'DCM PROJECT'))");
        assertAnswer("REFUSED SQL compilation error: Invalid value 'DELEGATED_AUTHORIZATION' for argument "
                + "'ref_entity_domain' for function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'DELEGATED AUTHORIZATION'))");
        assertAnswer("REFUSED SQL compilation error: Invalid value 'DEPLOYMENT' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'DEPLOYMENT'))");
        assertAnswer("REFUSED SQL compilation error: Invalid value 'EVENT_ROUTING_TABLE' for argument "
                + "'ref_entity_domain' for function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'EVENT ROUTING TABLE'))");
        assertAnswer("REFUSED SQL compilation error: Invalid value 'FEATURE_POLICY' for argument 'ref_entity_domain' "
                + "for function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'FEATURE POLICY'))");
        assertAnswer("REFUSED SQL compilation error: Invalid value 'GIT_BRANCH' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'GIT BRANCH'))");
        assertAnswer("REFUSED SQL compilation error: Invalid value 'GIT_TAG' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'GIT TAG'))");
        assertAnswer("REFUSED SQL compilation error: Invalid value 'GLOBAL_ACCOUNT' for argument 'ref_entity_domain' "
                + "for function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'GLOBAL ACCOUNT'))");
        assertAnswer("REFUSED SQL compilation error: Invalid value 'LOCK' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'LOCK'))");
        assertAnswer("REFUSED SQL compilation error: Invalid value 'MAINTENANCE_POLICY' for argument "
                + "'ref_entity_domain' for function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'MAINTENANCE POLICY'))");
        assertAnswer("REFUSED SQL compilation error: Invalid value 'MFA_METHOD' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'MFA METHOD'))");
        assertAnswer("REFUSED SQL compilation error: Invalid value 'MULTI_PARTY_APPROVAL_POLICY' for argument "
                + "'ref_entity_domain' for function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'MULTI PARTY APPROVAL POLICY'))");
        assertAnswer("REFUSED SQL compilation error: Invalid value 'NOTEBOOK_PROJECT' for argument "
                + "'ref_entity_domain' for function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'NOTEBOOK PROJECT'))");
        assertAnswer("REFUSED SQL compilation error: Invalid value 'ORGANIZATION_PROFILE' for argument "
                + "'ref_entity_domain' for function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'ORGANIZATION PROFILE'))");
        assertAnswer("REFUSED SQL compilation error: Invalid value 'ORGANIZATION_USER' for argument "
                + "'ref_entity_domain' for function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'ORGANIZATION USER'))");
        assertAnswer("REFUSED SQL compilation error: Invalid value 'ORGANIZATION_USER_GROUP' for argument "
                + "'ref_entity_domain' for function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'ORGANIZATION USER GROUP'))");
        assertAnswer("REFUSED SQL compilation error: Invalid value 'PARAMETER' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'PARAMETER'))");
        assertAnswer("REFUSED SQL compilation error: Invalid value 'PROVISIONED_THROUGHPUT' for argument "
                + "'ref_entity_domain' for function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'PROVISIONED THROUGHPUT'))");
        assertAnswer("REFUSED SQL compilation error: Invalid value 'RESULT' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'RESULT'))");
        assertAnswer("REFUSED SQL compilation error: Invalid value 'SERVICE_CONTAINER' for argument "
                + "'ref_entity_domain' for function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'SERVICE CONTAINER'))");
        assertAnswer("REFUSED SQL compilation error: Invalid value 'SERVICE_VOLUME' for argument 'ref_entity_domain' "
                + "for function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'SERVICE VOLUME'))");
        assertAnswer("REFUSED SQL compilation error: Invalid value 'TRANSACTION' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'TRANSACTION'))");
        assertAnswer("REFUSED SQL compilation error: Invalid value 'VARIABLE' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'VARIABLE'))");
        assertAnswer("REFUSED SQL compilation error: Invalid value 'APPLICATION_SPECIFICATION' for argument "
                + "'ref_entity_domain' for function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'APPLICATION SPECIFICATION'))");
        assertAnswer("REFUSED SQL compilation error: Invalid value 'POSTGRES' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'POSTGRES'))");
        assertAnswer("REFUSED SQL compilation error: Invalid value 'FILE' for argument 'ref_entity_domain' for "
                + "function  information_schema.data_metric_function_references.",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => "
                + "'nosuch_t', REF_ENTITY_DOMAIN => 'FILE'))");
    }
}
