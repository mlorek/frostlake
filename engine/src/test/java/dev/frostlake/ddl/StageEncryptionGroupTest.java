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
 * A stage's ENCRYPTION group is a property list, read in the order written and then judged whole (live-verified). Each
 * parameter is judged as it is read: a TYPE that is no text or names no type is an invalid value of TYPE, a key that is
 * no text an invalid value of its own, and any other name an invalid parameter. Then the group: each type takes its own
 * number of parameters, TYPE among them, the second one the type's own key, a MASTER_KEY is Base64 of 128, 192 or 256
 * bits, and the type fits the stage - Snowflake's own types an internal or temporary stage, which an ALTER may not
 * encrypt at all, and the cloud's own types and NONE an external one. Without a TYPE the type is AWS_CSE. A GCS stage
 * needs a storage integration, and SHOW STAGES reads has_encryption_key as whether an external stage encrypts.
 */
public class StageEncryptionGroupTest extends MeasuredCellsTestSupport {

    /** An internal stage takes SNOWFLAKE_SSE and SNOWFLAKE_FULL, each alone. */
    @Test
    public void anInternalStageTakesSnowflakesOwnTypes() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE STAGE si787 ENCRYPTION = (TYPE = 'SNOWFLAKE_SSE')",
                "Stage area SI787 successfully created."},
            {"CREATE OR REPLACE STAGE si787 ENCRYPTION = (TYPE = 'SNOWFLAKE_FULL')",
                "Stage area SI787 successfully created."},
            {"CREATE OR REPLACE STAGE si787 ENCRYPTION = (TYPE = 'snowflake_sse')",
                "Stage area SI787 successfully created."},
            {"CREATE OR REPLACE STAGE si787 ENCRYPTION = (TYPE = 'SNOWFLAKE_SSE' MASTER_KEY = 'k')",
                "REFUSED SQL compilation error: | Incompatible number of parameters for value [TYPE]"},
            {"CREATE OR REPLACE STAGE si787 ENCRYPTION = (TYPE = 'SNOWFLAKE_FULL' MASTER_KEY = 'k')",
                "REFUSED SQL compilation error: | Incompatible number of parameters for value [TYPE]"},
            {"CREATE OR REPLACE STAGE si787 ENCRYPTION = (TYPE = 'SNOWFLAKE_SSE' KMS_KEY_ID = 'k')",
                "REFUSED SQL compilation error: | Incompatible number of parameters for value [TYPE]"},
            {"CREATE OR REPLACE STAGE si787 ENCRYPTION = (TYPE = 'SNOWFLAKE_SSE' FOO = 'k')",
                "REFUSED SQL compilation error: | invalid parameter 'FOO'"},
            {"CREATE OR REPLACE STAGE si787 ENCRYPTION = (TYPE = 'NONE')",
                "REFUSED SQL compilation error: | Cannot set URL, credentials, or encryption key of an internal or "
                + "temporary stage."},
            {"CREATE OR REPLACE STAGE si787 ENCRYPTION = (TYPE = 'NONE' MASTER_KEY = 'k')",
                "REFUSED SQL compilation error: | Incompatible number of parameters for value [TYPE]"},
            {"CREATE OR REPLACE STAGE si787 ENCRYPTION = (TYPE = 'AWS_CSE')",
                "REFUSED SQL compilation error: | Cannot set URL, credentials, or encryption key of an internal or "
                + "temporary stage."},
            {"CREATE OR REPLACE STAGE si787 ENCRYPTION = (TYPE = 'AWS_CSE' MASTER_KEY = "
                + "'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=')",
                "REFUSED SQL compilation error: | Cannot set URL, credentials, or encryption key of an internal or "
                + "temporary stage."},
            {"CREATE OR REPLACE STAGE si787 ENCRYPTION = (MASTER_KEY = 'k')",
                "REFUSED The provided master key has invalid length. It must be either 128 bits, 192 bits, or 256 bits "
                + "long."},
            {"CREATE OR REPLACE STAGE si787 ENCRYPTION = ()",
                "REFUSED SQL compilation error: | invalid value [Not a property list: TOK_CONSTANT_LIST] for parameter "
                + "'{1}'"},
            {"CREATE OR REPLACE STAGE si787 ENCRYPTION = (TYPE = 'BOGUS')",
                "REFUSED SQL compilation error: | invalid value [BOGUS] for parameter 'TYPE'"},
            {"CREATE OR REPLACE STAGE si787 ENCRYPTION = (TYPE = 'BOGUS' MASTER_KEY = 'k')",
                "REFUSED SQL compilation error: | invalid value [BOGUS] for parameter 'TYPE'"},
            {"CREATE OR REPLACE STAGE si787 ENCRYPTION = (TYPE = 'SNOWFLAKE_SSE', TYPE = 'SNOWFLAKE_FULL')",
                "Stage area SI787 successfully created."},
            {"SHOW STAGES LIKE 'SI787' ->> SELECT \"name\", \"has_encryption_key\", \"has_credentials\" FROM $1",
                "SI787|N|N"},
        });
    }

    /** An S3 stage takes AWS_CSE with or without its MASTER_KEY, AWS_SSE_S3, AWS_SSE_KMS and NONE. */
    @Test
    public void anS3StageTakesItsCloudsTypesAndKeys() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE STAGE sx787 URL = 's3://frostlake-nosuch-bucket-787/p/' ENCRYPTION = (TYPE = "
                + "'AWS_CSE')",
                "Stage area SX787 successfully created."},
            {"CREATE OR REPLACE STAGE sx787 URL = 's3://frostlake-nosuch-bucket-787/p/' ENCRYPTION = (TYPE = 'AWS_CSE' "
                + "MASTER_KEY = 'a2V5')",
                "REFUSED The provided master key has invalid length. It must be either 128 bits, 192 bits, or 256 bits "
                + "long."},
            {"CREATE OR REPLACE STAGE sx787 URL = 's3://frostlake-nosuch-bucket-787/p/' ENCRYPTION = (TYPE = 'AWS_CSE' "
                + "MASTER_KEY = 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=')",
                "Stage area SX787 successfully created."},
            {"CREATE OR REPLACE STAGE sx787 URL = 's3://frostlake-nosuch-bucket-787/p/' ENCRYPTION = (TYPE = 'AWS_CSE' "
                + "MASTER_KEY = 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=' KMS_KEY_ID = 'x')",
                "REFUSED SQL compilation error: | Incompatible number of parameters for value [TYPE]"},
            {"CREATE OR REPLACE STAGE sx787 URL = 's3://frostlake-nosuch-bucket-787/p/' ENCRYPTION = (TYPE = "
                + "'AWS_SSE_S3')",
                "Stage area SX787 successfully created."},
            {"CREATE OR REPLACE STAGE sx787 URL = 's3://frostlake-nosuch-bucket-787/p/' ENCRYPTION = (TYPE = "
                + "'AWS_SSE_S3' KMS_KEY_ID = 'x')",
                "REFUSED SQL compilation error: | Incompatible number of parameters for value [TYPE]"},
            {"CREATE OR REPLACE STAGE sx787 URL = 's3://frostlake-nosuch-bucket-787/p/' ENCRYPTION = (TYPE = "
                + "'AWS_SSE_S3' MASTER_KEY = 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=')",
                "REFUSED SQL compilation error: | Incompatible number of parameters for value [TYPE]"},
            {"CREATE OR REPLACE STAGE sx787 URL = 's3://frostlake-nosuch-bucket-787/p/' ENCRYPTION = (TYPE = "
                + "'AWS_SSE_KMS')",
                "Stage area SX787 successfully created."},
            {"CREATE OR REPLACE STAGE sx787 URL = 's3://frostlake-nosuch-bucket-787/p/' ENCRYPTION = (TYPE = "
                + "'AWS_SSE_KMS' KMS_KEY_ID = 'x')",
                "Stage area SX787 successfully created."},
            {"CREATE OR REPLACE STAGE sx787 URL = 's3://frostlake-nosuch-bucket-787/p/' ENCRYPTION = (TYPE = "
                + "'AWS_SSE_KMS' MASTER_KEY = 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=')",
                "REFUSED SQL compilation error: | value [AWS_SSE_KMS] for parameter 'TYPE' conflict with parameter "
                + "'MASTER_KEY'"},
            {"CREATE OR REPLACE STAGE sx787 URL = 's3://frostlake-nosuch-bucket-787/p/' ENCRYPTION = (TYPE = 'NONE')",
                "Stage area SX787 successfully created."},
            {"CREATE OR REPLACE STAGE sx787 URL = 's3://frostlake-nosuch-bucket-787/p/' ENCRYPTION = (TYPE = 'NONE' "
                + "MASTER_KEY = 'x')",
                "REFUSED SQL compilation error: | Incompatible number of parameters for value [TYPE]"},
            {"CREATE OR REPLACE STAGE sx787 URL = 's3://frostlake-nosuch-bucket-787/p/' ENCRYPTION = (TYPE = 'NONE' "
                + "KMS_KEY_ID = 'x')",
                "REFUSED SQL compilation error: | Incompatible number of parameters for value [TYPE]"},
            {"CREATE OR REPLACE STAGE sx787 URL = 's3://frostlake-nosuch-bucket-787/p/' ENCRYPTION = (TYPE = "
                + "'SNOWFLAKE_SSE')",
                "REFUSED SQL compilation error: | invalid value [SNOWFLAKE_SSE] for parameter 'TYPE'"},
            {"CREATE OR REPLACE STAGE sx787 URL = 's3://frostlake-nosuch-bucket-787/p/' ENCRYPTION = (TYPE = "
                + "'SNOWFLAKE_FULL')",
                "REFUSED SQL compilation error: | invalid value [SNOWFLAKE_FULL] for parameter 'TYPE'"},
            {"CREATE OR REPLACE STAGE sx787 URL = 's3://frostlake-nosuch-bucket-787/p/' ENCRYPTION = (TYPE = "
                + "'GCS_SSE_KMS')",
                "REFUSED SQL compilation error: | invalid value [GCS_SSE_KMS] for parameter 'TYPE'"},
            {"CREATE OR REPLACE STAGE sx787 URL = 's3://frostlake-nosuch-bucket-787/p/' ENCRYPTION = (TYPE = "
                + "'AZURE_CSE' MASTER_KEY = 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=')",
                "REFUSED SQL compilation error: | invalid value [AZURE_CSE] for parameter 'TYPE'"},
            {"CREATE OR REPLACE STAGE sx787 URL = 's3://frostlake-nosuch-bucket-787/p/' ENCRYPTION = (MASTER_KEY = "
                + "'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=')",
                "Stage area SX787 successfully created."},
            {"CREATE OR REPLACE STAGE sx787 URL = 's3://frostlake-nosuch-bucket-787/p/' ENCRYPTION = (KMS_KEY_ID = "
                + "'x')",
                "REFUSED SQL compilation error: | value [AWS_CSE] for parameter 'TYPE' conflict with parameter "
                + "'KMS_KEY_ID'"},
            {"CREATE OR REPLACE STAGE sx787 URL = 's3://frostlake-nosuch-bucket-787/p/' ENCRYPTION = (TYPE = 'aws_cse' "
                + "MASTER_KEY = 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=')",
                "Stage area SX787 successfully created."},
            {"CREATE OR REPLACE STAGE sx787 URL = 's3://frostlake-nosuch-bucket-787/p/' ENCRYPTION = (TYPE = 'AWS_CSE' "
                + "FOO = 'x')",
                "REFUSED SQL compilation error: | invalid parameter 'FOO'"},
            {"CREATE OR REPLACE STAGE sx787 URL = 's3://frostlake-nosuch-bucket-787/p/' ENCRYPTION = (TYPE = "
                + "'AWS_SSE_KMS' KMS_KEY_ID = 'x' FOO = 'y')",
                "REFUSED SQL compilation error: | invalid parameter 'FOO'"},
            {"CREATE OR REPLACE STAGE sx787 URL = 's3://frostlake-nosuch-bucket-787/p/' ENCRYPTION = (TYPE = 'AWS_CSE' "
                + "MASTER_KEY = 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=')",
                "Stage area SX787 successfully created."},
            {"SHOW STAGES LIKE 'SX787' ->> SELECT \"name\", \"has_encryption_key\", \"has_credentials\" FROM $1",
                "SX787|Y|N"},
        });
    }

    /** An Azure stage takes AZURE_CSE with its key and NONE; a GCS stage needs a storage integration. */
    @Test
    public void azureAndGcsStagesTakeTheirOwnTypes() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE STAGE sz787 URL = 'azure://frostlakenosuch787.blob.core.windows.net/c/p/' ENCRYPTION = "
                + "(TYPE = 'AZURE_CSE')",
                "REFUSED SQL compilation error: | Incompatible number of parameters for value [TYPE]"},
            {"CREATE OR REPLACE STAGE sz787 URL = 'azure://frostlakenosuch787.blob.core.windows.net/c/p/' ENCRYPTION = "
                + "(TYPE = 'AZURE_CSE' MASTER_KEY = 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=')",
                "Stage area SZ787 successfully created."},
            {"CREATE OR REPLACE STAGE sz787 URL = 'azure://frostlakenosuch787.blob.core.windows.net/c/p/' ENCRYPTION = "
                + "(TYPE = 'NONE')",
                "Stage area SZ787 successfully created."},
            {"CREATE OR REPLACE STAGE sz787 URL = 'azure://frostlakenosuch787.blob.core.windows.net/c/p/' ENCRYPTION = "
                + "(TYPE = 'AWS_SSE_S3')",
                "REFUSED SQL compilation error: | invalid value [AWS_SSE_S3] for parameter 'TYPE'"},
            {"CREATE OR REPLACE STAGE sz787 URL = 'azure://frostlakenosuch787.blob.core.windows.net/c/p/' ENCRYPTION = "
                + "(TYPE = 'AZURE_CSE' MASTER_KEY = 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=' KMS_KEY_ID = 'x')",
                "REFUSED SQL compilation error: | Incompatible number of parameters for value [TYPE]"},
            {"CREATE OR REPLACE STAGE sg787 URL = 'gcs://frostlake-nosuch-bucket-787/p/' ENCRYPTION = (TYPE = "
                + "'GCS_SSE_KMS')",
                "REFUSED SQL compilation error: | Creation of stages with direct credentials, including accessing "
                + "public stages, has been forbidden for GCS stages. See your account administrator for details."},
            {"CREATE OR REPLACE STAGE sg787 URL = 'gcs://frostlake-nosuch-bucket-787/p/' ENCRYPTION = (TYPE = "
                + "'GCS_SSE_KMS' KMS_KEY_ID = 'x')",
                "REFUSED SQL compilation error: | Creation of stages with direct credentials, including accessing "
                + "public stages, has been forbidden for GCS stages. See your account administrator for details."},
            {"CREATE OR REPLACE STAGE sg787 URL = 'gcs://frostlake-nosuch-bucket-787/p/' ENCRYPTION = (TYPE = 'NONE')",
                "REFUSED SQL compilation error: | Creation of stages with direct credentials, including accessing "
                + "public stages, has been forbidden for GCS stages. See your account administrator for details."},
            {"CREATE OR REPLACE STAGE sg787 URL = 'gcs://frostlake-nosuch-bucket-787/p/' ENCRYPTION = (TYPE = "
                + "'GCS_SSE_KMS' MASTER_KEY = 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=')",
                "REFUSED SQL compilation error: | value [GCS_SSE_KMS] for parameter 'TYPE' conflict with parameter "
                + "'MASTER_KEY'"},
            {"CREATE OR REPLACE STAGE sg787 URL = 'gcs://frostlake-nosuch-bucket-787/p/' ENCRYPTION = (TYPE = "
                + "'AWS_SSE_S3')",
                "REFUSED SQL compilation error: | invalid value [AWS_SSE_S3] for parameter 'TYPE'"},
            {"CREATE OR REPLACE STAGE sg787 URL = 'gcs://frostlake-nosuch-bucket-787/p/'",
                "REFUSED SQL compilation error: | Creation of stages with direct credentials, including accessing "
                + "public stages, has been forbidden for GCS stages. See your account administrator for details."},
        });
    }

    /** ALTER STAGE ... SET ENCRYPTION judges the group as CREATE does, and an internal stage takes none. */
    @Test
    public void anAlterJudgesTheGroupAtTheStagesLocation() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE STAGE sa787 URL = 's3://frostlake-nosuch-bucket-787/p/'",
                "Stage area SA787 successfully created."},
            {"ALTER STAGE sa787 SET ENCRYPTION = (TYPE = 'AWS_CSE')",
                "Statement executed successfully."},
            {"ALTER STAGE sa787 SET ENCRYPTION = (TYPE = 'AWS_CSE' MASTER_KEY = "
                + "'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=')",
                "Statement executed successfully."},
            {"SHOW STAGES LIKE 'SA787' ->> SELECT \"name\", \"has_encryption_key\", \"has_credentials\" FROM $1",
                "SA787|Y|N"},
            {"ALTER STAGE sa787 SET ENCRYPTION = (TYPE = 'NONE' MASTER_KEY = 'x')",
                "REFUSED SQL compilation error: | Incompatible number of parameters for value [TYPE]"},
            {"ALTER STAGE sa787 SET ENCRYPTION = (TYPE = 'AWS_SSE_KMS' KMS_KEY_ID = 'x')",
                "Statement executed successfully."},
            {"ALTER STAGE sa787 SET ENCRYPTION = (TYPE = 'NONE')",
                "Statement executed successfully."},
            {"SHOW STAGES LIKE 'SA787' ->> SELECT \"name\", \"has_encryption_key\", \"has_credentials\" FROM $1",
                "SA787|N|N"},
            {"ALTER STAGE sa787 SET ENCRYPTION = (TYPE = 'BOGUS')",
                "REFUSED SQL compilation error: | invalid value [BOGUS] for parameter 'TYPE'"},
            {"ALTER STAGE sa787 SET ENCRYPTION = ()",
                "REFUSED SQL compilation error: | invalid value [Not a property list: TOK_CONSTANT_LIST] for parameter "
                + "'{1}'"},
            {"ALTER STAGE sa787 SET ENCRYPTION = (TYPE = 'SNOWFLAKE_SSE')",
                "REFUSED SQL compilation error: | invalid value [SNOWFLAKE_SSE] for parameter 'TYPE'"},
            {"ALTER STAGE sa787 SET URL = 's3://frostlake-nosuch-bucket-787/q/' ENCRYPTION = (TYPE = 'AWS_SSE_S3' "
                + "MASTER_KEY = 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=')",
                "REFUSED SQL compilation error: | Incompatible number of parameters for value [TYPE]"},
            {"CREATE OR REPLACE STAGE sb787",
                "Stage area SB787 successfully created."},
            {"ALTER STAGE sb787 SET ENCRYPTION = (TYPE = 'SNOWFLAKE_SSE')",
                "REFUSED SQL compilation error: | Cannot set URL, credentials, or encryption key of an internal or "
                + "temporary stage."},
            {"ALTER STAGE sb787 SET ENCRYPTION = (TYPE = 'NONE')",
                "REFUSED SQL compilation error: | Cannot set URL, credentials, or encryption key of an internal or "
                + "temporary stage."},
            {"ALTER STAGE sb787 SET ENCRYPTION = (TYPE = 'NONE' MASTER_KEY = 'x')",
                "REFUSED SQL compilation error: | Incompatible number of parameters for value [TYPE]"},
        });
    }

    /**
     * Names and TYPE values are judged as read, then the count, the key that goes with the type, the key's form, and
     * the location.
     */
    @Test
    public void theGroupIsReadInOrderThenJudgedWhole() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE STAGE ss789 URL = 's3://frostlake-nosuch-bucket-789/p/' ENCRYPTION = (MASTER_KEY = 'x' "
                + "TYPE = 'NONE')",
                "REFUSED SQL compilation error: | Incompatible number of parameters for value [TYPE]"},
            {"CREATE OR REPLACE STAGE ss789 URL = 's3://frostlake-nosuch-bucket-789/p/' ENCRYPTION = (KMS_KEY_ID = 'x' "
                + "TYPE = 'AWS_SSE_S3')",
                "REFUSED SQL compilation error: | Incompatible number of parameters for value [TYPE]"},
            {"CREATE OR REPLACE STAGE ss789 URL = 's3://frostlake-nosuch-bucket-789/p/' ENCRYPTION = (MASTER_KEY = "
                + "'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=' TYPE = 'AWS_SSE_KMS')",
                "REFUSED SQL compilation error: | value [AWS_SSE_KMS] for parameter 'TYPE' conflict with parameter "
                + "'MASTER_KEY'"},
            {"CREATE OR REPLACE STAGE ss789 URL = 's3://frostlake-nosuch-bucket-789/p/' ENCRYPTION = (FOO = 'x' TYPE = "
                + "'BOGUS')",
                "REFUSED SQL compilation error: | invalid parameter 'FOO'"},
            {"CREATE OR REPLACE STAGE ss789 URL = 's3://frostlake-nosuch-bucket-789/p/' ENCRYPTION = (TYPE = 'BOGUS' "
                + "FOO = 'x')",
                "REFUSED SQL compilation error: | invalid value [BOGUS] for parameter 'TYPE'"},
            {"CREATE OR REPLACE STAGE ss789 URL = 's3://frostlake-nosuch-bucket-789/p/' ENCRYPTION = (MASTER_KEY = 'x' "
                + "FOO = 'y')",
                "REFUSED SQL compilation error: | invalid parameter 'FOO'"},
            {"CREATE OR REPLACE STAGE ss789 URL = 's3://frostlake-nosuch-bucket-789/p/' ENCRYPTION = (FOO = 'y' "
                + "MASTER_KEY = 'x')",
                "REFUSED SQL compilation error: | invalid parameter 'FOO'"},
            {"CREATE OR REPLACE STAGE ss789 URL = 's3://frostlake-nosuch-bucket-789/p/' ENCRYPTION = (TYPE = 'NONE' "
                + "FOO = 'y' MASTER_KEY = 'x')",
                "REFUSED SQL compilation error: | invalid parameter 'FOO'"},
            {"CREATE OR REPLACE STAGE ss789 URL = 's3://frostlake-nosuch-bucket-789/p/' ENCRYPTION = (MASTER_KEY = "
                + "'!!!!')",
                "REFUSED The provided master key is not properly Base64-encoded."},
            {"CREATE OR REPLACE STAGE ss789 URL = 's3://frostlake-nosuch-bucket-789/p/' ENCRYPTION = (MASTER_KEY = "
                + "'AAAAAAAAAAAAAAAAAAAAAA==')",
                "Stage area SS789 successfully created."},
            {"CREATE OR REPLACE STAGE ss789 URL = 's3://frostlake-nosuch-bucket-789/p/' ENCRYPTION = (MASTER_KEY = "
                + "'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA')",
                "Stage area SS789 successfully created."},
            {"CREATE OR REPLACE STAGE ss789 URL = 's3://frostlake-nosuch-bucket-789/p/' ENCRYPTION = (MASTER_KEY = "
                + "'AAAA')",
                "REFUSED The provided master key has invalid length. It must be either 128 bits, 192 bits, or 256 bits "
                + "long."},
            {"CREATE OR REPLACE STAGE ss789 URL = 's3://frostlake-nosuch-bucket-789/p/' ENCRYPTION = (TYPE = 'AWS_CSE' "
                + "MASTER_KEY = '')",
                "REFUSED The provided master key has invalid length. It must be either 128 bits, 192 bits, or 256 bits "
                + "long."},
            {"CREATE OR REPLACE STAGE ss789 URL = 's3://frostlake-nosuch-bucket-789/p/' ENCRYPTION = (TYPE = "
                + "'AWS_SSE_KMS' KMS_KEY_ID = '')",
                "Stage area SS789 successfully created."},
            {"CREATE OR REPLACE STAGE ss789 URL = 's3://frostlake-nosuch-bucket-789/p/' ENCRYPTION = (TYPE = 'AWS_CSE' "
                + "MASTER_KEY = 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=' MASTER_KEY = "
                + "'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=')",
                "Stage area SS789 successfully created."},
            {"CREATE OR REPLACE STAGE ss789 URL = 's3://frostlake-nosuch-bucket-789/p/' ENCRYPTION = (TYPE = 'AWS_CSE' "
                + "TYPE = 'NONE')",
                "Stage area SS789 successfully created."},
            {"CREATE OR REPLACE STAGE ss789 URL = 's3://frostlake-nosuch-bucket-789/p/' ENCRYPTION = (type = 'AWS_CSE' "
                + "master_key = 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=')",
                "Stage area SS789 successfully created."},
            {"CREATE OR REPLACE STAGE ss789 URL = 's3://frostlake-nosuch-bucket-789/p/' ENCRYPTION = (TYPE = AWS_CSE)",
                "REFUSED SQL compilation error: | invalid value [AWS_CSE] for parameter 'TYPE'"},
            {"CREATE OR REPLACE STAGE ss789 URL = 's3://frostlake-nosuch-bucket-789/p/' ENCRYPTION = (TYPE = 'AWS_CSE' "
                + "MASTER_KEY = 1)",
                "REFUSED SQL compilation error: | invalid value [1] for parameter 'MASTER_KEY'"},
            {"CREATE OR REPLACE STAGE ss789 URL = 's3://frostlake-nosuch-bucket-789/p/' ENCRYPTION = (TYPE = "
                + "'AWS_CSE', MASTER_KEY = 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=')",
                "Stage area SS789 successfully created."},
            {"CREATE OR REPLACE STAGE ss789 URL = 's3://frostlake-nosuch-bucket-789/p/' ENCRYPTION = (TYPE = "
                + "'AWS_SSE_KMS' KMS_KEY_ID = 'x' TYPE = 'AWS_CSE')",
                "REFUSED SQL compilation error: | value [AWS_CSE] for parameter 'TYPE' conflict with parameter "
                + "'KMS_KEY_ID'"},
            {"CREATE OR REPLACE STAGE ss789 URL = 's3://frostlake-nosuch-bucket-789/p/' ENCRYPTION = (TYPE = "
                + "'AWS_CSE')",
                "Stage area SS789 successfully created."},
            {"SHOW STAGES LIKE 'SS789' ->> SELECT \"name\", \"has_encryption_key\" FROM $1",
                "SS789|Y"},
            {"CREATE OR REPLACE STAGE ss789 URL = 's3://frostlake-nosuch-bucket-789/p/' ENCRYPTION = (TYPE = "
                + "'AWS_SSE_KMS' KMS_KEY_ID = 'x')",
                "Stage area SS789 successfully created."},
            {"SHOW STAGES LIKE 'SS789' ->> SELECT \"name\", \"has_encryption_key\" FROM $1",
                "SS789|Y"},
            {"CREATE OR REPLACE STAGE ss789 URL = 's3://frostlake-nosuch-bucket-789/p/' ENCRYPTION = (TYPE = "
                + "'AWS_SSE_KMS')",
                "Stage area SS789 successfully created."},
            {"SHOW STAGES LIKE 'SS789' ->> SELECT \"name\", \"has_encryption_key\" FROM $1",
                "SS789|Y"},
            {"CREATE OR REPLACE STAGE ss789 URL = 's3://frostlake-nosuch-bucket-789/p/' ENCRYPTION = (TYPE = "
                + "'AWS_SSE_S3')",
                "Stage area SS789 successfully created."},
            {"SHOW STAGES LIKE 'SS789' ->> SELECT \"name\", \"has_encryption_key\" FROM $1",
                "SS789|Y"},
            {"CREATE OR REPLACE STAGE ss789 URL = 's3://frostlake-nosuch-bucket-789/p/' ENCRYPTION = (MASTER_KEY = "
                + "'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=')",
                "Stage area SS789 successfully created."},
            {"SHOW STAGES LIKE 'SS789' ->> SELECT \"name\", \"has_encryption_key\" FROM $1",
                "SS789|Y"},
            {"CREATE OR REPLACE STAGE ss789 URL = 'azure://frostlakenosuch789.blob.core.windows.net/c/p/' ENCRYPTION = "
                + "(TYPE = 'AZURE_CSE' MASTER_KEY = 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=')",
                "Stage area SS789 successfully created."},
            {"SHOW STAGES LIKE 'SS789' ->> SELECT \"name\", \"has_encryption_key\" FROM $1",
                "SS789|Y"},
            {"CREATE OR REPLACE STAGE ss789 URL = 'azure://frostlakenosuch789.blob.core.windows.net/c/p/' ENCRYPTION = "
                + "(MASTER_KEY = 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=')",
                "Stage area SS789 successfully created."},
            {"CREATE OR REPLACE STAGE ss789 URL = 'azure://frostlakenosuch789.blob.core.windows.net/c/p/' ENCRYPTION = "
                + "(KMS_KEY_ID = 'x')",
                "REFUSED SQL compilation error: | value [AWS_CSE] for parameter 'TYPE' conflict with parameter "
                + "'KMS_KEY_ID'"},
            {"CREATE OR REPLACE STAGE ss789 URL = 'gcs://frostlake-nosuch-bucket-789/p/' ENCRYPTION = (MASTER_KEY = "
                + "'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=')",
                "REFUSED SQL compilation error: | invalid value [AWS_CSE] for parameter 'TYPE'"},
            {"CREATE OR REPLACE STAGE ss789 URL = 'gcs://frostlake-nosuch-bucket-789/p/' ENCRYPTION = (KMS_KEY_ID = "
                + "'x')",
                "REFUSED SQL compilation error: | value [AWS_CSE] for parameter 'TYPE' conflict with parameter "
                + "'KMS_KEY_ID'"},
            {"CREATE OR REPLACE STAGE ss789 URL = 's3://frostlake-nosuch-bucket-789/p/' ENCRYPTION = TRUE",
                "REFUSED SQL compilation error: | invalid value [Not a property list: TRUE] for parameter '{1}'"},
            {"CREATE OR REPLACE STAGE ss789 URL = 's3://frostlake-nosuch-bucket-789/p/' ENCRYPTION = FALSE",
                "REFUSED SQL compilation error: | invalid value [Not a property list: FALSE] for parameter '{1}'"},
            {"CREATE OR REPLACE STAGE ss789 ENCRYPTION = TRUE",
                "REFUSED SQL compilation error: | invalid value [Not a property list: TRUE] for parameter '{1}'"},
            {"CREATE OR REPLACE STAGE ss789 ENCRYPTION = FALSE",
                "REFUSED SQL compilation error: | invalid value [Not a property list: FALSE] for parameter '{1}'"},
            {"CREATE OR REPLACE STAGE ss789 ENCRYPTION = (MASTER_KEY = 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=')",
                "REFUSED SQL compilation error: | Cannot set URL, credentials, or encryption key of an internal or "
                + "temporary stage."},
            {"CREATE OR REPLACE STAGE ss789 ENCRYPTION = (KMS_KEY_ID = 'x')",
                "REFUSED SQL compilation error: | value [AWS_CSE] for parameter 'TYPE' conflict with parameter "
                + "'KMS_KEY_ID'"},
            {"CREATE OR REPLACE STAGE ss789 ENCRYPTION = (TYPE = 'GCS_SSE_KMS')",
                "REFUSED SQL compilation error: | Cannot set URL, credentials, or encryption key of an internal or "
                + "temporary stage."},
            {"CREATE OR REPLACE STAGE ss789 ENCRYPTION = (TYPE = 'AZURE_CSE' MASTER_KEY = "
                + "'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=')",
                "REFUSED SQL compilation error: | Cannot set URL, credentials, or encryption key of an internal or "
                + "temporary stage."},
            {"CREATE OR REPLACE TEMPORARY STAGE ss789 ENCRYPTION = (TYPE = 'SNOWFLAKE_SSE')",
                "Stage area SS789 successfully created."},
            {"CREATE OR REPLACE TEMPORARY STAGE ss789 ENCRYPTION = (TYPE = 'NONE')",
                "REFUSED SQL compilation error: | Cannot set URL, credentials, or encryption key of an internal or "
                + "temporary stage."},
        });
    }
}
