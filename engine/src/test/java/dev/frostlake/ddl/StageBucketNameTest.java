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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * An S3 or GCS stage location names a bucket, and CREATE STAGE and ALTER STAGE ... SET URL refuse one that is empty or
 * holds anything but letters, digits, hyphens, periods and underscores, in the account's own sentence; a scheme
 * written without its {@code //} is no URL at all. A refused ALTER sets none of its properties (live-verified).
 */
public class StageBucketNameTest extends BaseDatabaseTest {

    private static String bucketRefusal(final String bucket) {
        return "Bucket name '" + bucket + "' in the stage location is not supported. Valid bucket names must consist"
            + " of lowercase letters, digits, hyphens '-', and periods '.'.";
    }

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return refused.getMessage();
    }

    private String created(final String url) {
        final Row row = engine.executeQuery("CREATE OR REPLACE STAGE b_ok URL = '" + url + "'").getRows().get(0);
        return String.valueOf(row.getValue(0));
    }

    @Test
    public void aLocationWithNoBucketIsRefused() {
        assertEquals(bucketRefusal(""), refusal("CREATE STAGE b1 URL = 's3:///abs/path/'"));
        assertEquals(bucketRefusal(""), refusal("CREATE STAGE b1 URL = 's3://'"));
        assertEquals(bucketRefusal(""), refusal("CREATE STAGE b1 URL = 'S3:///x/'"));
        assertEquals(bucketRefusal(""), refusal("CREATE STAGE b1 URL = 's3gov:///x/'"));
        assertEquals(bucketRefusal(""), refusal("CREATE STAGE b1 URL = 's3china:///x/'"));
        assertEquals(bucketRefusal(""), refusal("CREATE STAGE b1 URL = 'gcs:///x/'"));
    }

    @Test
    public void aBucketWithAnyOtherCharacterIsRefused() {
        assertEquals(bucketRefusal("bucket name"), refusal("CREATE STAGE b1 URL = 's3://bucket name/x/'"));
        assertEquals(bucketRefusal(" bucket"), refusal("CREATE STAGE b1 URL = 's3:// bucket/x/'"));
        assertEquals(bucketRefusal("bucket?x"), refusal("CREATE STAGE b1 URL = 's3://bucket?x/y/'"));
        assertEquals(bucketRefusal("bucket#x"), refusal("CREATE STAGE b1 URL = 's3://bucket#x/y/'"));
        assertEquals(bucketRefusal("bucket:9000"), refusal("CREATE STAGE b1 URL = 's3://bucket:9000/y/'"));
        assertEquals(bucketRefusal("bucket@x"), refusal("CREATE STAGE b1 URL = 's3://bucket@x/y/'"));
        assertEquals(bucketRefusal("buck~et"), refusal("CREATE STAGE b1 URL = 's3://buck~et/x/'"));
        assertEquals(bucketRefusal("buck'et"), refusal("CREATE STAGE b1 URL = 's3://buck''et/x/'"));
        assertEquals(bucketRefusal("bucket name"), refusal("CREATE STAGE b1 URL = 'gcs://bucket name/x/'"));
    }

    @Test
    public void aSchemeWithoutItsSlashesIsNoUrl() {
        assertEquals("SQL compilation error:\ninvalid URL: s3:x/", refusal("CREATE STAGE b1 URL = 's3:x/'"));
        assertEquals("SQL compilation error:\ninvalid URL: s3:/x/", refusal("CREATE STAGE b1 URL = 's3:/x/'"));
        assertEquals("SQL compilation error:\ninvalid URL: gcs:x/", refusal("CREATE STAGE b1 URL = 'gcs:x/'"));
    }

    @Test
    public void lettersOfEitherCaseDigitsHyphensPeriodsAndUnderscoresAreABucket() {
        assertEquals("Stage area B_OK successfully created.", created("s3://bucket/x/"));
        assertEquals("Stage area B_OK successfully created.", created("s3://MyBucket/x/"));
        assertEquals("Stage area B_OK successfully created.", created("s3://my_bucket/x/"));
        assertEquals("Stage area B_OK successfully created.", created("s3://a.b-c/x/"));
        assertEquals("Stage area B_OK successfully created.", created("s3://bucket"));
    }

    @Test
    public void alterStageSetUrlJudgesTheLocationAndSetsNothingWhenRefused() {
        engine.execute("CREATE OR REPLACE STAGE b_ok URL = 's3://bucket/x/'");
        assertEquals("SQL compilation error:\ninvalid URL: s3:x/", refusal("ALTER STAGE b_ok SET URL = 's3:x/'"));
        assertEquals(bucketRefusal("bucket name"), refusal("ALTER STAGE b_ok SET URL = 's3://bucket name/x/'"));
        engine.execute("ALTER STAGE b_ok SET URL = 's3://bucket/y/' COMMENT = 'c'");
        assertEquals(bucketRefusal(""), refusal("ALTER STAGE b_ok SET COMMENT = 'd' URL = 's3:///y/'"));
        final Row row = engine.executeQuery(
            "SELECT STAGE_URL, COMMENT FROM INFORMATION_SCHEMA.STAGES WHERE STAGE_NAME = 'B_OK'").getRows().get(0);
        assertEquals("s3://bucket/y/", row.getValue(0));
        assertEquals("c", row.getValue(1));
    }
}
