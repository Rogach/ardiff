package org.rogach.ardiff;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class DiffTests {

    @Test
    public void testDiffSameFile() throws Exception {
        byte[] simpleArchive = IOUtils.toByteArray(getClass().getResourceAsStream("/f2.zip"));

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
    public void testApplyEmptyDiff() throws Exception {
        byte[] simpleArchive = IOUtils.toByteArray(getClass().getResourceAsStream("/f2.zip"));
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

}
