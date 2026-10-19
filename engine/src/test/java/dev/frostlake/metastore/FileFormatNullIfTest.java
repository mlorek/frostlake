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

package dev.frostlake.metastore;

import dev.frostlake.metastore.model.FileFormat;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * NULL_IF's stored form reads back whole: a value holding a comma, a single empty value and a value opening a bracket
 * each round-trip, and the listing an earlier version kept for a stage's values still reads.
 */
public class FileFormatNullIfTest {

    @Test
    public void aValueOfAnyShapeRoundTrips() {
        final FileFormat format = new FileFormat("F", "CSV");
        format.setNullIfValues(Arrays.asList("[x]", "y"));
        assertEquals(Arrays.asList("[x]", "y"), format.getNullIfValues());
        format.setNullIfValues(Arrays.asList("a,b", ""));
        assertEquals(Arrays.asList("a,b", ""), format.getNullIfValues());
        format.setNullIfValues(Collections.singletonList(""));
        assertEquals(Collections.singletonList(""), format.getNullIfValues());
        format.setNullIfValues(Collections.<String>emptyList());
        assertEquals(Collections.emptyList(), format.getNullIfValues());
    }

    @Test
    public void anEarlierVersionsListingStillReads() {
        assertEquals(Arrays.asList("\\N", "NULL"), FileFormat.nullIfValues("[\\\\N, NULL]"));
        assertEquals(Collections.emptyList(), FileFormat.nullIfValues("[]"));
        assertEquals(null, FileFormat.nullIfValues(null));
    }
}
