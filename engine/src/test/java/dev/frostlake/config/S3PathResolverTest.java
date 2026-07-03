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

package dev.frostlake.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link S3PathResolver}: mapping {@code s3://bucket/key} references to local filesystem
 * paths via the {@code stage.s3.localRoot} fallback base and the explicit {@code stage.s3.localMappings}
 * prefix overrides (longest matching prefix wins).
 */
public class S3PathResolverTest {

    private static S3PathResolver resolver(final String localRoot, final String mappings) {
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_STAGE_S3_LOCAL_ROOT, localRoot);
        config.setProperty(EngineConfig.PROP_STAGE_S3_LOCAL_MAPPINGS, mappings);
        return new S3PathResolver(config);
    }

    @Test
    public void rootBasedResolutionMirrorsBucketAndKey() {
        final S3PathResolver r = resolver("/tmp/s3root", "");
        assertEquals(Paths.get("/tmp/s3root/my-bucket/data/x.jar"),
            r.toLocalPath("s3://my-bucket/data/x.jar"));
    }

    @Test
    public void explicitMappingRedirectsToLocalDir() {
        final S3PathResolver r = resolver("/tmp/s3root", "s3://my-bucket/lib/=/opt/jars");
        assertEquals(Paths.get("/opt/jars/handler.jar"),
            r.toLocalPath("s3://my-bucket/lib/handler.jar"));
    }

    @Test
    public void prefixWithNoTrailingKeyResolvesToTheMappedDir() {
        final S3PathResolver r = resolver("/tmp/s3root", "s3://my-bucket/lib/=/opt/jars");
        assertEquals(Paths.get("/opt/jars"), r.toLocalPath("s3://my-bucket/lib/"));
    }

    @Test
    public void longestMatchingPrefixWins() {
        final S3PathResolver r = resolver("/tmp/s3root", "s3://b/=/d1; s3://b/lib/=/d2");
        assertEquals(Paths.get("/d2/x.jar"), r.toLocalPath("s3://b/lib/x.jar"));
        assertEquals(Paths.get("/d1/other/x.jar"), r.toLocalPath("s3://b/other/x.jar"));
    }

    @Test
    public void unmappedBucketFallsBackToRoot() {
        final S3PathResolver r = resolver("/tmp/s3root", "s3://b/lib/=/opt/jars");
        assertEquals(Paths.get("/tmp/s3root/elsewhere/y.jar"),
            r.toLocalPath("s3://elsewhere/y.jar"));
    }

    @Test
    public void isS3UrlRecognisesScheme() {
        assertTrue(S3PathResolver.isS3Url("s3://bucket/key"));
        assertFalse(S3PathResolver.isS3Url("file:///tmp/x"));
        assertFalse(S3PathResolver.isS3Url("/local/path"));
        assertFalse(S3PathResolver.isS3Url(null));
    }

    @Test
    public void toLocalPathRejectsNonS3Url() {
        final S3PathResolver r = resolver("/tmp/s3root", "");
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                r.toLocalPath("/local/path.jar");
            }
        });
    }

    @Test
    public void malformedMappingEntriesAreIgnored() {
        // Missing '=', non-s3 key, and a blank entry are all skipped; the valid one still applies.
        final S3PathResolver r = resolver("/tmp/s3root", "garbage; /not/s3=/d; ; s3://b/=/good");
        assertEquals(Paths.get("/good/x.jar"), r.toLocalPath("s3://b/x.jar"));
    }
}
