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

package dev.frostlake.persistence;

import dev.frostlake.DatabaseEngine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A tag set on the account itself, {@code ALTER ACCOUNT SET TAG}, holds for the account's life: an engine clone and a
 * checkpoint restore both carry it, and one unset before the checkpoint stays unset.
 */
public class AccountTagSnapshotTest {

    private Path dir;

    @AfterEach
    public void cleanup() {
        if (dir != null) {
            deleteRecursively(dir.toFile());
        }
    }

    private static void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    /** An engine clone and a checkpoint restore of the original. */
    private List<DatabaseEngine> copiesOf(final DatabaseEngine original) throws Exception {
        final List<DatabaseEngine> copies = new ArrayList<>();
        copies.add(original.cloneInstance());
        dir = Files.createTempDirectory("account_tag_");
        original.checkpointStateTo(dir);
        final DatabaseEngine restored = new DatabaseEngine();
        restored.restoreStateFrom(dir);
        copies.add(restored);
        return copies;
    }

    @Test
    public void theAccountsTagSurvivesACopy() throws Exception {
        final DatabaseEngine original = new DatabaseEngine();
        original.execute("CREATE DATABASE at");
        original.execute("CREATE SCHEMA at.tags");
        original.execute("CREATE TAG at.tags.kept");
        original.execute("CREATE TAG at.tags.dropped");
        original.execute("ALTER ACCOUNT SET TAG at.tags.kept = 'av'");
        original.execute("ALTER ACCOUNT SET TAG at.tags.dropped = 'gone'");
        original.execute("ALTER ACCOUNT UNSET TAG at.tags.dropped");
        assertEquals(1, original.getCatalog().getAccountTags().getTagValues().size());
        for (final DatabaseEngine copy : copiesOf(original)) {
            assertEquals(1, copy.getCatalog().getAccountTags().getTagValues().size());
            assertTrue(copy.getCatalog().getAccountTags().getTagValues().containsValue("av"));
            copy.shutdown();
        }
        original.shutdown();
    }
}
