package org.rogach.ardiff;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;

import static org.junit.Assert.assertEquals;

public class DiffTests {

    @Test
    public void testDiffSameFile() throws Exception {
        ByteArrayOutputStream diffOutputStream = new ByteArrayOutputStream();
        ArchiveDiff.computeDiff(
                new FileInputStream("src/test/resources/f2.zip"),
                new FileInputStream("src/test/resources/f2.zip"),
                "zip",
                false,
                diffOutputStream
        );

        FileUtils.writeByteArrayToFile(new File("/home/platon/Tor/diff.bin"), diffOutputStream.toByteArray());

        assertEquals(8, diffOutputStream.toByteArray().length);
    }

}
