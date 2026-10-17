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
 * An Azure stage location names a storage account's blob host and a container (live-verified):
 * {@code azure://<account>.blob.core.windows.net/<container>[/<path>]}, the host in any case. A location without an
 * account, a host, a container or the {@code //} is refused, the Data Lake endpoint and any other host are named as
 * unsupported endpoints, and a Government or China cloud's blob host is refused for this deployment. A stage on such a
 * location is an external Azure stage whatever the case of its scheme.
 */
public class AzureStageLocationTest extends MeasuredCellsTestSupport {

    @Test
    public void anAzureLocationNamesABlobHostAndAContainer() {
        assertCells(new String[][] {
            {"CREATE STAGE az1 URL = 'azure:///x/'",
                "REFUSED SQL compilation error: | Invalid location format or value 'azure:///x/'."},
            {"CREATE STAGE az2 URL = 'azure://acct.blob.core.windows.net/c/'", "Stage area AZ2 successfully created."},
            {"CREATE STAGE az3 URL = 'azure://acct.blob.core.windows.net/'",
                "REFUSED SQL compilation error: | Invalid location format or value "
                + "'azure://acct.blob.core.windows.net/'."},
            {"CREATE STAGE az4 URL = 'azure://acct.blob.core.windows.net'",
                "REFUSED SQL compilation error: | Invalid location format or value "
                + "'azure://acct.blob.core.windows.net'."},
            {"CREATE STAGE az5 URL = 'azure://acct.blob.core.windows.net/c'", "Stage area AZ5 successfully created."},
            {"CREATE STAGE az6 URL = 'azure://acct.dfs.core.windows.net/c/'",
                "REFUSED SQL compilation error: | Provided Azure storage endpoint 'dfs.core.windows.net' is "
                + "currently not supported."},
            {"CREATE STAGE az7 URL = 'azure://acct/c/'",
                "REFUSED SQL compilation error: | Invalid location format or value 'azure://acct/c/'."},
            {"CREATE STAGE az8 URL = 'azure://.blob.core.windows.net/c/'",
                "REFUSED SQL compilation error: | Invalid location format or value "
                + "'azure://.blob.core.windows.net/c/'."},
            {"CREATE STAGE az9 URL = 'azure://ACCT.BLOB.CORE.WINDOWS.NET/c/'", "Stage area AZ9 successfully created."},
            {"CREATE STAGE az10 URL = 'azure://acct.queue.core.windows.net/c/'",
                "REFUSED SQL compilation error: | Provided Azure storage endpoint 'acct.queue.core.windows.net' is "
                + "currently not supported."},
            {"CREATE STAGE az11 URL = 'AZURE://acct.blob.core.windows.net/c/'",
                "Stage area AZ11 successfully created."},
            {"CREATE STAGE az12 URL = 'azure://acct.blob.core.usgovcloudapi.net/c/'",
                "REFUSED SQL compilation error: on non-Government deployments, using an Azure Government storage "
                + "location is not allowed."},
            {"CREATE STAGE az13 URL = 'azure://acct.blob.core.chinacloudapi.cn/c/'",
                "REFUSED SQL compilation error: on non-China deployments, using an Azure China storage location is "
                + "not allowed."},
            {"CREATE STAGE az14 URL = 'azure://acct.blob.core.windows.net/c/a b/'",
                "Stage area AZ14 successfully created."},
            {"CREATE STAGE az15 URL = 'azure://acct_1.blob.core.windows.net/c/'",
                "Stage area AZ15 successfully created."},
            {"CREATE STAGE az16 URL = 'azure://acct.blob.core.windows.net//'",
                "REFUSED SQL compilation error: | Invalid location format or value "
                + "'azure://acct.blob.core.windows.net//'."},
            {"CREATE STAGE az17 URL = 'azure:/x/'", "REFUSED SQL compilation error: | invalid URL: azure:/x/"},
            {"CREATE STAGE az18 URL = 'azure:x/'", "REFUSED SQL compilation error: | invalid URL: azure:x/"},
            {"CREATE STAGE az19 URL = 'azure://acct.blob.core.windows.net/C_UP/'",
                "Stage area AZ19 successfully created."},
            {"CREATE STAGE az20 URL = 'azure://acct.example.com/c/'",
                "REFUSED SQL compilation error: | Provided Azure storage endpoint 'acct.example.com' is currently "
                + "not supported."},
            {"CREATE STAGE az21 URL = 'azure://acct.blob.core.windows.net/c/p/q/'",
                "Stage area AZ21 successfully created."},
            {"CREATE STAGE az22 URL = 'azure://acct.blob.core.windows.net/c-1.x_y/'",
                "Stage area AZ22 successfully created."},
            {"CREATE STAGE az23 URL = 'azure://acct.blob.core.windows.net/c/' CREDENTIALS = (AZURE_SAS_TOKEN = 'tok')",
                "Stage area AZ23 successfully created."},
            {"ALTER STAGE az2 SET URL = 'azure:///y/'",
                "REFUSED SQL compilation error: | Invalid location format or value 'azure:///y/'."},
            {"ALTER STAGE az2 SET URL = 'azure://other.blob.core.windows.net/d/'", "Statement executed successfully."},
        });
        engine.execute("SHOW STAGES LIKE 'AZ%'");
        assertCells(new String[][] {
            {"SELECT \"name\", \"url\", \"cloud\", \"type\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID(-1))) ORDER BY 1",
                "AZ11|AZURE://acct.blob.core.windows.net/c/|AZURE|EXTERNAL ;; "
                + "AZ14|azure://acct.blob.core.windows.net/c/a b/|AZURE|EXTERNAL ;; "
                + "AZ15|azure://acct_1.blob.core.windows.net/c/|AZURE|EXTERNAL ;; "
                + "AZ19|azure://acct.blob.core.windows.net/C_UP/|AZURE|EXTERNAL ;; "
                + "AZ2|azure://other.blob.core.windows.net/d/|AZURE|EXTERNAL ;; "
                + "AZ21|azure://acct.blob.core.windows.net/c/p/q/|AZURE|EXTERNAL ;; "
                + "AZ22|azure://acct.blob.core.windows.net/c-1.x_y/|AZURE|EXTERNAL ;; "
                + "AZ23|azure://acct.blob.core.windows.net/c/|AZURE|EXTERNAL ;; "
                + "AZ5|azure://acct.blob.core.windows.net/c|AZURE|EXTERNAL ;; "
                + "AZ9|azure://ACCT.BLOB.CORE.WINDOWS.NET/c/|AZURE|EXTERNAL"},
        });
    }
}
