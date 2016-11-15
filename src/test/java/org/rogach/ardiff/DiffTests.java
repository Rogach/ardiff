package org.rogach.ardiff;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.rogach.ardiff.exceptions.ArchiveDiffCorruptedException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DiffTests {

    @Test
    public void testZipDiffSameFile() throws Exception {
        byte[] simpleArchive = IOUtils.toByteArray(getClass().getResourceAsStream("/zip-simple/a1_b1_c1.zip"));

        ByteArrayOutputStream diffOutputStream = new ByteArrayOutputStream();
        ArchiveDiff.computeDiff(
                new ByteArrayInputStream(simpleArchive),
                new ByteArrayInputStream(simpleArchive),
                diffOutputStream
        );
        diffOutputStream.close();

        byte[] emptyDiff = diffOutputStream.toByteArray();
        Assert.assertEquals(9, emptyDiff.length);
        Assert.assertArrayEquals(ArchiveDiff.HEADER.getBytes("ASCII"), Arrays.copyOf(emptyDiff, 8));
        Assert.assertEquals(0, emptyDiff[8]);
    }

    @Test
    public void testZipApplyEmptyDiff() throws Exception {
        byte[] simpleArchive = IOUtils.toByteArray(getClass().getResourceAsStream("/zip-simple/__c1.zip"));
        byte[] emptyDiff = (ArchiveDiff.HEADER + '\0').getBytes("ASCII");

        ByteArrayOutputStream resultOutputStream = new ByteArrayOutputStream();
        ArchiveDiff.applyDiff(
                new ByteArrayInputStream(simpleArchive),
                new ByteArrayInputStream(emptyDiff),
                resultOutputStream
        );
        resultOutputStream.close();

        byte[] result = resultOutputStream.toByteArray();

        Assert.assertTrue(ArchiveDiff.archivesAreEqual(new ByteArrayInputStream(simpleArchive), new ByteArrayInputStream(result)));
    }

    void testDiffApplyInvariant(String resourceBefore, String resourceAfter, boolean assumeOrdering) throws Exception {
        byte[] before = IOUtils.toByteArray(getClass().getResourceAsStream(resourceBefore));
        byte[] after = IOUtils.toByteArray(getClass().getResourceAsStream(resourceAfter));

        ByteArrayOutputStream diffOutputStream = new ByteArrayOutputStream();
        ArchiveDiff.computeDiff(
                new ByteArrayInputStream(before),
                new ByteArrayInputStream(after),
                diffOutputStream
        );
        diffOutputStream.close();

        byte[] diff = diffOutputStream.toByteArray();

        ByteArrayOutputStream resultOutputStream = new ByteArrayOutputStream();
        ArchiveDiff.applyDiff(
                new ByteArrayInputStream(before),
                new ByteArrayInputStream(diff),
                resultOutputStream
        );
        resultOutputStream.close();

        byte[] result = resultOutputStream.toByteArray();

        Assert.assertTrue(
                String.format("diff-apply invariant failed between %s and %s", resourceBefore, resourceAfter),
                ArchiveDiff.archivesAreEqual(new ByteArrayInputStream(after), new ByteArrayInputStream(result)));
    }

    @Test
    public void testSimpleZips() throws Exception {
        List<String> archiveNames = new ArrayList<>();
        for (int a = 0; a <= 3; a++) {
            for (int b = 0; b <= 3; b++) {
                for (int c = 0; c <= 3; c++) {
                    StringBuilder name = new StringBuilder();
                    if (a > 0) name.append("a" + a);
                    name.append("_");
                    if (b > 0) name.append("b" + b);
                    name.append("_");
                    if (c > 0) name.append("c" + c);
                    name.append(".zip");

                    // skip empty archive
                    if (a > 0 || b > 0 || c > 0) {
                        archiveNames.add(name.toString());
                    }
                }
            }
        }

        for (String archiveBefore : archiveNames) {
            for (String archiveAfter : archiveNames) {
                testDiffApplyInvariant("/zip-simple/" + archiveBefore, "/zip-simple/" + archiveAfter, false);
            }
        }
    }

    @Test
    public void testRecursiveZips() throws Exception {
        List<String> archiveNames = new ArrayList<>();
        for (int a = 0; a <= 3; a++) {
            for (int b = 0; b <= 3; b++) {
                for (int c = 0; c <= 3; c++) {
                    StringBuilder name = new StringBuilder();
                    if (a > 0) name.append("a" + a);
                    name.append("_r_");
                    if (b > 0) name.append("b" + b);
                    name.append("_");
                    if (c > 0) name.append("c" + c);
                    name.append(".zip");

                    // skip empty archive
                    if (a > 0 || b > 0 || c > 0) {
                        archiveNames.add(name.toString());
                    }
                }
            }
        }

        for (String archiveBefore : archiveNames) {
            for (String archiveAfter : archiveNames) {
                testDiffApplyInvariant("/zip-recursive/" + archiveBefore, "/zip-recursive/" + archiveAfter, false);
            }
        }
    }

    @Test(expected = ArchiveDiffCorruptedException.class)
    public void testDiffCorruption() throws Exception {
        byte[] before = IOUtils.toByteArray(getClass().getResourceAsStream("/zip-simple/a1_b1_c1.zip"));
        byte[] after = IOUtils.toByteArray(getClass().getResourceAsStream("/zip-simple/a2_b2_c2.zip"));

        ByteArrayOutputStream diffOutputStream = new ByteArrayOutputStream();
        ArchiveDiff.computeDiff(
                new ByteArrayInputStream(before),
                new ByteArrayInputStream(after),
                "zip",
                false,
                diffOutputStream
        );
        diffOutputStream.close();

        byte[] diff = diffOutputStream.toByteArray();

        // corrupt the byte
        diff[42] = 0;

        ByteArrayOutputStream resultOutputStream = new ByteArrayOutputStream();
        ArchiveDiff.applyDiff(
                new ByteArrayInputStream(before),
                new ByteArrayInputStream(diff),
                "zip",
                false,
                resultOutputStream
        );
        resultOutputStream.close();
    }
}
