package org.rogach.ardiff;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;

public class ArchiveDiffUtils {

    public static long computeCRC32Checksum(byte[] data) {
        CRC32 checksum = new CRC32();
        checksum.update(data);
        return checksum.getValue();
    }

    public static long computeCRC32Checksum(InputStream dataStream) {
        CheckedOutputStream checkedOutputStream = new CheckedOutputStream(new NullOutputStream(), new CRC32());
        try {
            IOUtils.copy(dataStream, checkedOutputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return checkedOutputStream.getChecksum().getValue();
    }

}
