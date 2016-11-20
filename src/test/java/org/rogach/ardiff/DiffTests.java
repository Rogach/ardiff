package org.rogach.ardiff;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.rogach.ardiff.exceptions.ArchiveDiffCorruptedException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.InputStream;
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

        ByteArrayOutputStream sortedOutputStream = new ByteArrayOutputStream();
        ArchiveDiff.sortArchiveEntries(
                new ByteArrayInputStream(before),
                sortedOutputStream
        );
        sortedOutputStream.close();
        byte[] sortedBefore = sortedOutputStream.toByteArray();

        ByteArrayOutputStream diffOutputStream = new ByteArrayOutputStream();
        ArchiveDiff.computeDiff(
                new ByteArrayInputStream(before),
                new ByteArrayInputStream(after),
                assumeOrdering,
                diffOutputStream
        );
        diffOutputStream.close();

        byte[] diff = diffOutputStream.toByteArray();

        ByteArrayOutputStream resultOutputStream = new ByteArrayOutputStream();
        ArchiveDiff.applyDiff(
                new ByteArrayInputStream(assumeOrdering ? sortedBefore : before),
                new ByteArrayInputStream(diff),
                resultOutputStream,
                assumeOrdering
        );
        resultOutputStream.close();

        byte[] result = resultOutputStream.toByteArray();

        Assert.assertTrue(
                String.format("diff-apply invariant failed between %s and %s", resourceBefore, resourceAfter),
                ArchiveDiff.archivesAreEqual(new ByteArrayInputStream(after), new ByteArrayInputStream(result)));
    }

    @Test
    public void testSimple() throws Exception {
        int archivePairsTested = 0;

        for (String archiveType : Arrays.asList("zip", "tar", "ar")) {
            List<String> archiveNames = new ArrayList<>();
            for (int a = 0; a <= 2; a++) {
                for (int b = 0; b <= 2; b++) {
                    for (int c = 0; c <= 2; c++) {
                        StringBuilder name = new StringBuilder();
                        name.append("/").append(archiveType).append("-simple/");
                        if (a > 0) name.append("a").append(a);
                        name.append("_");
                        if (b > 0) name.append("b").append(b);
                        name.append("_");
                        if (c > 0) name.append("c").append(c);
                        name.append(".").append(archiveType);

                        // skip empty archive
                        if (a > 0 || b > 0 || c > 0) {
                            archiveNames.add(name.toString());
                        }
                    }
                }
            }

            for (String archiveBefore : archiveNames) {
                for (String archiveAfter : archiveNames) {
                    testDiffApplyInvariant(archiveBefore, archiveAfter, false);
                    testDiffApplyInvariant(archiveBefore, archiveAfter, true);
                    archivePairsTested += 2;
                    if (archivePairsTested % 100 == 0) {
                        System.out.printf("testSimple: tested %d archive pairs\n", archivePairsTested);
                    }
                }
            }
        }
    }

    @Test
    public void testRecursive() throws Exception {
        int archivePairsTested = 0;
        for (String outerArchiveType : Arrays.asList("zip", "tar", "ar")) {
            for (String innerArchiveType : Arrays.asList("zip", "tar", "ar")) {
                List<String> archiveNames = new ArrayList<>();
                for (int a = 0; a <= 2; a++) {
                    for (int b = 0; b <= 2; b++) {
                        for (int c = 0; c <= 2; c++) {
                            StringBuilder name = new StringBuilder();
                            name.append("/recursive/");
                            if (a > 0) name.append("a").append(a);
                            name.append("_r_");
                            if (b > 0) name.append("b").append(b);
                            name.append("_");
                            if (c > 0) name.append("c").append(c);
                            name.append("_").append(innerArchiveType).append(".").append(outerArchiveType);

                            // skip empty archive
                            if (a > 0 || b > 0 || c > 0) {
                                archiveNames.add(name.toString());
                            }
                        }
                    }
                }

                for (String archiveBefore : archiveNames) {
                    for (String archiveAfter : archiveNames) {
                        testDiffApplyInvariant(archiveBefore, archiveAfter, false);
                        testDiffApplyInvariant(archiveBefore, archiveAfter, true);
                        archivePairsTested += 2;
                        if (archivePairsTested % 100 == 0) {
                            System.out.printf("testRecursiveZips: tested %d archive pairs\n", archivePairsTested);
                        }
                    }
                }
            }
        }
    }

    @Test(expected = ArchiveDiffCorruptedException.class)
    public void testDiffChecksumValidation() throws Exception {
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

    @Test(expected = ArchiveDiffCorruptedException.class)
    public void testStreamingDiffChecksumValidation() throws Exception {
        byte[] before = IOUtils.toByteArray(getClass().getResourceAsStream("/zip-simple/a1_b1_c1.zip"));
        byte[] after = IOUtils.toByteArray(getClass().getResourceAsStream("/zip-simple/a2_b2_c2.zip"));

        ByteArrayOutputStream diffOutputStream = new ByteArrayOutputStream();
        ArchiveDiff.computeDiff(
                new ByteArrayInputStream(before),
                new ByteArrayInputStream(after),
                "zip",
                true,
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
                true,
                resultOutputStream
        );
        resultOutputStream.close();
    }

    @Test
    public void testArchiveSorting() throws Exception {
        byte[] unsorted = IOUtils.toByteArray(getClass().getResourceAsStream("/unsorted.zip"));

        ByteArrayOutputStream sortedOutputStream = new ByteArrayOutputStream();
        ArchiveDiff.sortArchiveEntries(
                new ByteArrayInputStream(unsorted),
                sortedOutputStream
        );
        sortedOutputStream.close();

        byte[] sorted = sortedOutputStream.toByteArray();

        ensureArchiveSorted(new ByteArrayInputStream(sorted), "zip");
    }

    @Test
    public void testArchiveSortingRecursive() throws Exception {
        byte[] unsorted = IOUtils.toByteArray(getClass().getResourceAsStream("/unsorted-recursive.zip"));

        ByteArrayOutputStream sortedOutputStream = new ByteArrayOutputStream();
        ArchiveDiff.sortArchiveEntries(
                new ByteArrayInputStream(unsorted),
                sortedOutputStream
        );
        sortedOutputStream.close();

        byte[] sorted = sortedOutputStream.toByteArray();

        ensureArchiveSorted(new ByteArrayInputStream(sorted), "zip");
    }

    private void ensureArchiveSorted(InputStream input, String archiveType) throws Exception {
        ArchiveInputStream archiveInputStream = new ArchiveStreamFactory().createArchiveInputStream(archiveType, input);

        String lastName = "";
        do {
            ArchiveEntry entry;
            try {
                entry = archiveInputStream.getNextEntry();
            } catch (EOFException ex) {
                break;
            }

            if (entry == null) break;

            if (entry.getName().compareTo(lastName) < 0) {
                throw new AssertionError(String.format("Archive is not sorted: entry '%s' comes before '%s'", entry.getName(), lastName));
            }

            if (entry.getName().endsWith("zip")) {
                ensureArchiveSorted(archiveInputStream, "zip");
            }

            lastName = entry.getName();

        } while (true);
    }

}
