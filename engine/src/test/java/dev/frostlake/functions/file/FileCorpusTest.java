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

package dev.frostlake.functions.file;

import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * A committed fixture corpus for the FILE family, staged as REAL files and classified END TO END.
 *
 * <p><b>Why this class exists.</b> The per-function suites pin the two halves of the FILE pipeline
 * separately: {@code FlGetContentTypeTest} checks EXTENSION &rarr; {@code CONTENT_TYPE} for a handful of
 * extensions, and the {@code FlIs*} suites check {@code CONTENT_TYPE} &rarr; CATEGORY by feeding
 * {@code TRY_TO_FILE} a hand-built descriptor. Nothing joined them. So the entries that make the two
 * tables DISAGREE were unprotected: {@code FileContentTypes} maps {@code .gz} to
 * {@code application/x-gzip}, and that string is deliberately absent from the compressed set, but
 * "correcting" the map to {@code application/gzip} would not have failed a single test —
 * {@code FlIsCompressedTest} asserts {@code isCompressed("application/x-gzip")} is FALSE by passing the
 * content type in directly, and no test ever staged a {@code .gz}. The same hole covered {@code .avi},
 * {@code .png}, {@code .mp3} and {@code .tsv}. (Before this class, {@code TO_FILE('@st/a.gz')} appeared
 * in exactly one place in the test tree: a Javadoc sentence in {@code FlIsCompressedTest}.)
 *
 * <p><b>Where the expectations come from.</b> Every row below is a live Snowflake measurement,
 * re-read from the recorded transcripts rather than restated from memory. The
 * account that produced them has since expired, so these are the recorded originals and the
 * {@code CONTENT_TYPE}s are quoted from the file each was measured on:
 *
 * <pre>
 *   .png  image/png                   image      &lt;- image_real.png
 *   .jpg  image/jpeg                  image      &lt;- s.jpg
 *   .gz   application/x-gzip          unknown    &lt;- data.csv.gz   (NOT compressed)
 *   .zip  application/zip             compressed &lt;- archive.zip
 *   .avi  video/x-msvideo             video      &lt;- s.avi         (video AND audio at once)
 *   .mp3  audio/mpeg                  audio      &lt;- sound.mp3
 *   .tsv  text/tab-separated-values   unknown    &lt;- s.tsv         (NOT a document)
 *   .txt  text/plain                  document   &lt;- hello.txt
 *   none  application/octet-stream    unknown    &lt;- noext
 * </pre>
 *
 * <p><b>The centrepiece.</b> Live, the SAME 69 PNG bytes were staged under two names and reported
 * {@code image/png} as {@code image_real.png} but {@code text/plain} as {@code png_named.txt} — with an
 * identical {@code ETAG} of {@code 8b040e045eb7cdb16b37b2443fcf1d0e} and an identical {@code SIZE},
 * because the digest is over the bytes and the content type is over the name. Those bytes are
 * {@link #PNG_BYTES}, and that digest is reproducible from them, which is what ties this corpus to the
 * account it was measured on.
 *
 * <p>{@code file://} stages are a Frostlake local-testing extension, so the whole class is engine-only.
 */
public class FileCorpusTest extends StagedFileTestSupport {

    /** The shared payload behind every fixture that exists only to carry an extension. */
    private static final String PAYLOAD = "corpus payload\n";

    /** MD5 of {@link #PAYLOAD}: the same bytes under many names must produce this one digest. */
    private static final String PAYLOAD_ETAG = "1e6e8ad188c5e661e4e146f5b7789e8a";

    /** {@link #PAYLOAD}'s byte length. */
    private static final String PAYLOAD_SIZE = "15";

    /** MD5 of {@link #PNG_BYTES}, which the live account reported as the ETAG of those same bytes. */
    private static final String PNG_ETAG = "8b040e045eb7cdb16b37b2443fcf1d0e";

    /** MD5 of zero bytes — the one digest a file's content cannot vary. */
    private static final String EMPTY_ETAG = "d41d8cd98f00b204e9800998ecf8427e";

    /** A name with a space and a non-ASCII letter, escaped so the source file's encoding cannot matter. */
    private static final String AWKWARD_NAME = "sp ace nai\u00EFve.txt";

    @Override
    protected void setupTest() {
        super.setupTest();
        if (isLiveSnowflake()) {
            return;
        }
        stageBytes("c.png", PNG_BYTES);
        stage("c.jpg", PAYLOAD);
        stage("c.gz", PAYLOAD);
        stage("c.zip", PAYLOAD);
        stage("c.avi", PAYLOAD);
        stage("c.mp3", PAYLOAD);
        stage("c.tsv", PAYLOAD);
        stage("c.txt", PAYLOAD);
        stage("noext", PAYLOAD);
        stageBytes("png_bytes.txt", PNG_BYTES);
        stage("sub/nested.png", PAYLOAD);
        stage("empty.txt", "");
        stage(AWKWARD_NAME, PAYLOAD);
    }

    /**
     * The whole corpus in one table, in the shape the live account printed it:
     * {@code CONTENT_TYPE|FILE_TYPE|IMAGE|VIDEO|AUDIO|DOCUMENT|COMPRESSED}.
     *
     * <p>This is the assertion the family was missing. Each row travels the FULL path — real bytes on a
     * stage, {@code TO_FILE} deriving a {@code CONTENT_TYPE} from the name, then the classifiers reading
     * that derived field — so breaking EITHER lookup table, or the join between them, fails here.
     */
    @Test
    public void theCorpusClassifiesExactlyAsTheAccountDid() {
        assumeFalse(isLiveSnowflake(), stageOnlyReason());

        assertEquals("image/png|image|TRUE|FALSE|FALSE|FALSE|FALSE", classification("c.png"));
        assertEquals("image/jpeg|image|TRUE|FALSE|FALSE|FALSE|FALSE", classification("c.jpg"));
        assertEquals("application/x-gzip|unknown|FALSE|FALSE|FALSE|FALSE|FALSE", classification("c.gz"));
        assertEquals("application/zip|compressed|FALSE|FALSE|FALSE|FALSE|TRUE", classification("c.zip"));
        assertEquals("video/x-msvideo|video|FALSE|TRUE|TRUE|FALSE|FALSE", classification("c.avi"));
        assertEquals("audio/mpeg|audio|FALSE|FALSE|TRUE|FALSE|FALSE", classification("c.mp3"));
        assertEquals("text/tab-separated-values|unknown|FALSE|FALSE|FALSE|FALSE|FALSE",
            classification("c.tsv"));
        assertEquals("text/plain|document|FALSE|FALSE|FALSE|TRUE|FALSE", classification("c.txt"));
        assertEquals("application/octet-stream|unknown|FALSE|FALSE|FALSE|FALSE|FALSE",
            classification("noext"));
        assertEquals("text/plain|document|FALSE|FALSE|FALSE|TRUE|FALSE", classification("png_bytes.txt"));
        assertEquals("image/png|image|TRUE|FALSE|FALSE|FALSE|FALSE", classification("sub/nested.png"));
        assertEquals("text/plain|document|FALSE|FALSE|FALSE|TRUE|FALSE", classification("empty.txt"));
        assertEquals("text/plain|document|FALSE|FALSE|FALSE|TRUE|FALSE", classification(AWKWARD_NAME));
    }

    /**
     * Live: the two archive fixtures disagree, and the disagreement is the point. A real {@code .gz} is
     * {@code application/x-gzip}, which is NOT in the compressed set, so {@code FL_IS_COMPRESSED} over an
     * actual gzip file is FALSE and its category is {@code unknown} — while a {@code .zip} is
     * {@code application/zip} and behaves as one would expect.
     *
     * <p>Snowflake does list {@code application/gzip} as compressed; it simply never assigns that
     * spelling to a staged file. Aligning the extension map onto the compressed set would look like a
     * tidy-up and would be a fidelity regression, so it is pinned from both ends here.
     */
    @Test
    public void gzIsNotCompressedThoughZipIs() {
        assumeFalse(isLiveSnowflake(), stageOnlyReason());

        assertEquals("application/x-gzip", contentTypeOf("c.gz"));
        assertEquals("FALSE", predicate("SELECT FL_IS_COMPRESSED(TO_FILE('@st/c.gz'))"));
        assertEquals("unknown", scalar("SELECT FL_GET_FILE_TYPE(TO_FILE('@st/c.gz'))"));

        assertEquals("application/zip", contentTypeOf("c.zip"));
        assertEquals("TRUE", predicate("SELECT FL_IS_COMPRESSED(TO_FILE('@st/c.zip'))"));

        // The spelling that IS compressed, to show the set membership is real and only the map differs.
        assertEquals("TRUE", predicate("SELECT FL_IS_COMPRESSED(" + fileOf("application/gzip") + ")"));
    }

    /**
     * Live: the category sets OVERLAP, and an {@code .avi} is the file that proves it — a real staged
     * {@code video/x-msvideo} satisfies {@code FL_IS_VIDEO} AND {@code FL_IS_AUDIO} at once, while
     * {@code FL_GET_FILE_TYPE} answers {@code video}. So {@code FL_IS_x} is NOT
     * {@code FL_GET_FILE_TYPE = 'x'}, and no refactor may implement one in terms of the other.
     */
    @Test
    public void aviIsVideoAndAudioFromARealStagedFile() {
        assumeFalse(isLiveSnowflake(), stageOnlyReason());

        assertEquals("TRUE", predicate("SELECT FL_IS_VIDEO(TO_FILE('@st/c.avi'))"));
        assertEquals("TRUE", predicate("SELECT FL_IS_AUDIO(TO_FILE('@st/c.avi'))"));
        assertEquals("video", scalar("SELECT FL_GET_FILE_TYPE(TO_FILE('@st/c.avi'))"));
        // The .mp3 is the control: audio only, so the .avi's double membership is not a blanket rule.
        assertEquals("FALSE", predicate("SELECT FL_IS_VIDEO(TO_FILE('@st/c.mp3'))"));
    }

    /**
     * Live: classification follows the NAME and never the CONTENT. The same 69 PNG bytes staged twice —
     * once as {@code .png}, once as {@code .txt} — classify as an image and as a document respectively,
     * yet report the same {@code SIZE} and the same {@code ETAG}, because those two ARE read from the
     * bytes. One fixture pair separates "derived from the name" from "derived from the content".
     */
    @Test
    public void identicalBytesClassifyByNameAloneYetShareSizeAndDigest() {
        assumeFalse(isLiveSnowflake(), stageOnlyReason());

        assertEquals("image/png", contentTypeOf("c.png"));
        assertEquals("text/plain", contentTypeOf("png_bytes.txt"));
        assertEquals("image", scalar("SELECT FL_GET_FILE_TYPE(TO_FILE('@st/c.png'))"));
        assertEquals("document", scalar("SELECT FL_GET_FILE_TYPE(TO_FILE('@st/png_bytes.txt'))"));

        // Live reported this digest for these bytes under BOTH names.
        assertEquals(PNG_ETAG, scalar("SELECT FL_GET_ETAG(TO_FILE('@st/c.png'))"));
        assertEquals(PNG_ETAG, scalar("SELECT FL_GET_ETAG(TO_FILE('@st/png_bytes.txt'))"));
        assertEquals(String.valueOf(PNG_BYTES.length), scalar("SELECT FL_GET_SIZE(TO_FILE('@st/c.png'))"));
        assertEquals(String.valueOf(PNG_BYTES.length),
            scalar("SELECT FL_GET_SIZE(TO_FILE('@st/png_bytes.txt'))"));

        // ... and the payload fixtures, being different bytes, must not collide with it.
        assertNotEquals(PNG_ETAG, scalar("SELECT FL_GET_ETAG(TO_FILE('@st/c.jpg'))"));
    }

    /**
     * The extension fixtures deliberately share one payload, so {@code SIZE} and {@code ETAG} are
     * invariant across every name in the corpus while {@code CONTENT_TYPE} varies with all of them. That
     * separation is what makes the corpus able to blame the right table when a row moves.
     */
    @Test
    public void oneSharedPayloadKeepsSizeAndEtagConstantAcrossEveryExtension() {
        assumeFalse(isLiveSnowflake(), stageOnlyReason());

        final String[] fixtures = {"c.jpg", "c.gz", "c.zip", "c.avi", "c.mp3", "c.tsv", "c.txt",
            "noext", "sub/nested.png", AWKWARD_NAME};
        for (final String fixture : fixtures) {
            assertEquals(PAYLOAD_ETAG, scalar("SELECT FL_GET_ETAG(" + toFile(fixture) + ")"), fixture);
            assertEquals(PAYLOAD_SIZE, scalar("SELECT FL_GET_SIZE(" + toFile(fixture) + ")"), fixture);
        }
    }

    /**
     * A zero-byte file is still a file: it resolves, it classifies by its extension, its {@code SIZE} is
     * 0 rather than NULL, and its {@code ETAG} is the MD5 of no bytes at all.
     */
    @Test
    public void emptyFileHasZeroSizeAndTheDigestOfNoBytes() {
        assumeFalse(isLiveSnowflake(), stageOnlyReason());

        assertEquals("0", scalar("SELECT FL_GET_SIZE(TO_FILE('@st/empty.txt'))"));
        assertEquals(EMPTY_ETAG, scalar("SELECT FL_GET_ETAG(TO_FILE('@st/empty.txt'))"));
        assertEquals("text/plain", contentTypeOf("empty.txt"));
        assertEquals("document", scalar("SELECT FL_GET_FILE_TYPE(TO_FILE('@st/empty.txt'))"));
    }

    /**
     * Path handling the corpus exists to stress: a sub-directory is KEPT in {@code RELATIVE_PATH} (and
     * the extension behind it is still what classifies the file), and a name carrying a space and a
     * non-ASCII letter resolves and round-trips unchanged.
     *
     * <p>NOT live-measured: #135 never staged a name like {@link #AWKWARD_NAME}, and the expired account
     * cannot be asked now. This pins Frostlake's current behaviour — which is the reasonable one — rather
     * than a measured Snowflake answer, and a live {@code PUT} of such a name should be re-checked if the
     * account is ever restored.
     */
    @Test
    public void subDirectoriesAndAwkwardNamesRoundTrip() {
        assumeFalse(isLiveSnowflake(), stageOnlyReason());

        assertEquals("sub/nested.png",
            scalar("SELECT FL_GET_RELATIVE_PATH(TO_FILE('@st/sub/nested.png'))"));
        assertEquals("image/png", contentTypeOf("sub/nested.png"));

        assertEquals(AWKWARD_NAME, scalar("SELECT FL_GET_RELATIVE_PATH(" + toFile(AWKWARD_NAME) + ")"));
        assertEquals("text/plain", contentTypeOf(AWKWARD_NAME));
    }

    /**
     * {@code LAST_MODIFIED} is asserted by SHAPE, never by value: it is the fixture's own modification
     * time, which is whenever this test ran locally and whenever the {@code PUT} happened on an account.
     * No literal could hold across both backends, so the corpus checks the field is present and renders
     * as a full timestamp.
     *
     * <p>The URL accessors are omitted here on purpose — live they are NULL for every descriptor built
     * from a stage path, even with {@code DIRECTORY = (ENABLE = TRUE)}, and
     * {@code FlGetScopedFileUrlTest} already pins exactly that.
     */
    @Test
    public void everyFixtureCarriesAWellFormedLastModified() {
        assumeFalse(isLiveSnowflake(), stageOnlyReason());

        final String[] fixtures = {"c.png", "c.gz", "noext", "empty.txt", "sub/nested.png", AWKWARD_NAME};
        for (final String fixture : fixtures) {
            final String rendered = scalar("SELECT TO_VARCHAR(FL_GET_LAST_MODIFIED(" + toFile(fixture)
                + "), 'YYYY-MM-DD HH24:MI:SS')");
            assertNotNull(rendered, fixture);
            assertEquals(19, rendered.length(), fixture + " -> " + rendered);
        }
    }

    /** The stage every fixture reports, so a corpus file is anchored to the stage it was staged on. */
    @Test
    public void everyFixtureReportsTheStageItLivesOn() {
        assumeFalse(isLiveSnowflake(), stageOnlyReason());

        final String expected = scalar("SELECT FL_GET_STAGE(TO_FILE('@st/c.txt'))");
        assertNotNull(expected);
        assertEquals(expected, scalar("SELECT FL_GET_STAGE(TO_FILE('@st/sub/nested.png'))"));
        assertEquals(expected, scalar("SELECT FL_GET_STAGE(" + toFile(AWKWARD_NAME) + ")"));
    }

    /** A {@code TO_FILE} over a corpus fixture, with the path quoted for the SQL literal. */
    private static String toFile(final String relativePath) {
        return "TO_FILE('@st/" + relativePath.replace("'", "''") + "')";
    }

    /** An FL_IS_* answer, case-folded the way every existing FlIs*Test helper folds it. */
    private String predicate(final String sql) {
        return scalar(sql).toUpperCase();
    }

    private String contentTypeOf(final String relativePath) {
        return scalar("SELECT FL_GET_CONTENT_TYPE(" + toFile(relativePath) + ")");
    }

    /**
     * One fixture's whole classification as a single pipe-joined string, in the same column order the
     * live account printed, so an expectation can be read straight off the recorded transcript.
     *
     * @param relativePath the fixture's path within the stage
     * @return {@code CONTENT_TYPE|FILE_TYPE|IMAGE|VIDEO|AUDIO|DOCUMENT|COMPRESSED}
     */
    private String classification(final String relativePath) {
        final String file = toFile(relativePath);
        final Row row = engine.executeQuery("SELECT FL_GET_CONTENT_TYPE(" + file + "),"
            + " FL_GET_FILE_TYPE(" + file + "), FL_IS_IMAGE(" + file + "), FL_IS_VIDEO(" + file + "),"
            + " FL_IS_AUDIO(" + file + "), FL_IS_DOCUMENT(" + file + "), FL_IS_COMPRESSED(" + file + ")")
            .getRows().get(0);
        final StringBuilder joined = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if (i > 0) {
                joined.append('|');
            }
            final Object value = row.getValue(i);
            final String text = value == null ? "<NULL>" : value.toString();
            // Columns 2-6 are the FL_IS_* booleans. The engine renders a BOOLEAN lower-case where the
            // account prints TRUE/FALSE, so they are folded here exactly as every existing FlIs*Test
            // helper folds them -- a display convention, not a classification difference. Columns 0-1
            // are compared verbatim: FL_GET_FILE_TYPE really is lower-case ('image', 'unknown') on both.
            joined.append(i >= 2 ? text.toUpperCase() : text);
        }
        return joined.toString();
    }
}
