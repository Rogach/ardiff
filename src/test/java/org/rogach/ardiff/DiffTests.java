package org.rogach.ardiff;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.StringBuilderWriter;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class DiffTests {

    @Test
    public void testZipDiffSameFile() throws Exception {
        byte[] simpleArchive = IOUtils.toByteArray(getClass().getResourceAsStream("/zip-simple/a1_b1_c1.zip"));

        ByteArrayOutputStream diffOutputStream = new ByteArrayOutputStream();
        ArchiveDiff.computeDiff(
                new ByteArrayInputStream(simpleArchive),
                new ByteArrayInputStream(simpleArchive),
                "zip",
                false,
                diffOutputStream
        );

        byte[] emptyDiff = diffOutputStream.toByteArray();
        Assert.assertEquals(8, emptyDiff.length);
        Assert.assertArrayEquals(ArchiveDiff.HEADER.getBytes("ASCII"), emptyDiff);
    }

    @Test
    public void testZipApplyEmptyDiff() throws Exception {
        byte[] simpleArchive = IOUtils.toByteArray(getClass().getResourceAsStream("/zip-simple/a1_b1_c1.zip"));
        byte[] emptyDiff = ArchiveDiff.HEADER.getBytes("ASCII");

        ByteArrayOutputStream resultOutputStream = new ByteArrayOutputStream();
        ArchiveDiff.applyDiff(
                new ByteArrayInputStream(simpleArchive),
                new ByteArrayInputStream(emptyDiff),
                "zip",
                false,
                resultOutputStream
        );

        byte[] result = resultOutputStream.toByteArray();

        ZipArchiveDiff archiveComparator = new ZipArchiveDiff();
        Assert.assertTrue(archiveComparator.archivesEqual(new ByteArrayInputStream(simpleArchive), new ByteArrayInputStream(result)));
    }

    void testDiffApplyInvariant(String archiveType, String resourceBefore, String resourceAfter, boolean assumeOrdering) throws Exception {
        byte[] before = IOUtils.toByteArray(getClass().getResourceAsStream(resourceBefore));
        byte[] after = IOUtils.toByteArray(getClass().getResourceAsStream(resourceAfter));

        ByteArrayOutputStream diffOutputStream = new ByteArrayOutputStream();
        ArchiveDiff.computeDiff(
                new ByteArrayInputStream(before),
                new ByteArrayInputStream(after),
                archiveType,
                assumeOrdering,
                diffOutputStream
        );
        byte[] diff = diffOutputStream.toByteArray();

        ByteArrayOutputStream resultOutputStream = new ByteArrayOutputStream();
        ArchiveDiff.applyDiff(
                new ByteArrayInputStream(before),
                new ByteArrayInputStream(diff),
                archiveType,
                assumeOrdering,
                resultOutputStream
        );

        byte[] result = resultOutputStream.toByteArray();

        ArchiveDiff archiveComparator = ArchiveDiff.comparatorForArchiveType(archiveType);
        Assert.assertTrue(
                String.format("diff-apply invariant failed between %s and %s", resourceBefore, resourceAfter),
                archiveComparator.archivesEqual(new ByteArrayInputStream(after), new ByteArrayInputStream(result)));
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
                System.out.printf("testing %s, %s\n", archiveBefore, archiveAfter);
                testDiffApplyInvariant("zip", "/zip-simple/" + archiveBefore, "/zip-simple/" + archiveAfter, false);
            }
        }
    }
}
