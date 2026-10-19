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
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Tag;
import dev.frostlake.types.NumericType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A schema holds each kind of object under its name exactly as stored, so two whose names differ only in case are
 * two objects, each reached by its own spelling; the engine's own Java callers also reach one by a name in another
 * case when exactly one stored name matches it. Another session's temporary object is reached by neither and listed
 * by neither, though the engine's bookkeeping still holds it.
 */
public class SchemaObjectStoreTest {

    private final Schema schema = new Schema("S");

    @AfterEach
    public void unbind() {
        StatementSession.unbind();
    }

    private static String refusal(final Executable call) {
        return assertThrows(RuntimeException.class, call).getMessage();
    }

    private static Function function(final String name) {
        return new Function(name, List.of(new Parameter("X", NumericType.NUMBER)), NumericType.NUMBER, "X", false);
    }

    @Test
    public void twoTagsWhoseNamesDifferInCaseAreEachReachedByTheirOwnSpelling() {
        schema.addTag(new Tag("TG"));
        schema.addTag(new Tag("tg"));
        assertEquals("TG", schema.getTagExact("TG").getName());
        assertEquals("tg", schema.getTagExact("tg").getName());
        // A name in a third case matches both, so the folding lookup chooses neither.
        assertFalse(schema.hasTag("Tg"));
        assertTrue(refusal(new Executable() {
            @Override
            public void execute() {
                schema.getTagExact("Tg");
            }
        }).contains("Tag 'S.\"Tg\"' does not exist or not authorized."));
        schema.dropTagExact("tg");
        assertEquals("TG", schema.getTag("tg").getName());
        assertFalse(schema.hasTagExact("tg"));
    }

    @Test
    public void aRenameKeepsTheNewNameAsGivenAndRefusesOneAnotherObjectHolds() {
        schema.addTag(new Tag("A"));
        schema.addTag(new Tag("B"));
        assertTrue(refusal(new Executable() {
            @Override
            public void execute() {
                schema.renameTag("A", "B");
            }
        }).contains("Object 'B' already exists."));
        schema.renameTag("a", "c");
        assertEquals("c", schema.getTagExact("c").getName());
        assertFalse(schema.hasTagExact("A"));
        assertEquals(2, schema.getTags().size());
    }

    @Test
    public void anotherSessionsTemporaryFileFormatIsNeitherReachedNorReplaced() {
        final FileFormat temporary = new FileFormat("FF", "CSV");
        temporary.markTemporary(1L);
        schema.addFileFormat(temporary);
        StatementSession.bind(2L);
        assertNull(schema.getFileFormat("ff"));
        assertNull(schema.getFileFormatExact("FF"));
        assertEquals(0, schema.getFileFormats().size());
        assertEquals(1, schema.allFileFormats().size());
        assertTrue(refusal(new Executable() {
            @Override
            public void execute() {
                schema.addFileFormat(new FileFormat("FF", "JSON"));
            }
        }).contains("Object 'FF' already exists."));
        StatementSession.bind(1L);
        assertSame(temporary, schema.getFileFormatExact("FF"));
        final FileFormat replacement = new FileFormat("FF", "JSON");
        schema.addFileFormat(replacement);
        assertSame(replacement, schema.getFileFormat("ff"));
    }

    @Test
    public void anotherSessionsTemporaryFunctionIsNoOverloadThere() {
        StatementSession.bind(1L);
        final Function first = function("F");
        first.setTemporary(true);
        schema.addFunction(first);
        assertEquals(1, schema.getFunctionOverloads("F").size());
        StatementSession.bind(2L);
        assertEquals(0, schema.getFunctionOverloads("F").size());
        final Function own = function("F");
        own.setTemporary(true);
        schema.addFunction(own);
        assertSame(own, schema.getFunction("f"));
        assertEquals(2, schema.allFunctions().size());
        schema.removeFunction(own);
        assertNull(own.getHome());
        assertSame(schema, first.getHome());
        assertEquals(1, schema.allFunctions().size());
    }

    @Test
    public void aSignatureAnOverloadOfTheNameCarriesIsRefusedAsTaken() {
        schema.addFunction(function("G"));
        assertTrue(refusal(new Executable() {
            @Override
            public void execute() {
                schema.addFunction(function("G"));
            }
        }).contains("Object 'G' already exists."));
        assertEquals(1, schema.getFunctions().size());
    }
}
