package org.rogach.ardiff;

import java.util.zip.CRC32;

public class ArchiveDiffUtils {

    public static long computeCRC32Checksum(byte[] data) {
        CRC32 checksum = new CRC32();
        checksum.update(data);
        return checksum.getValue();
    }

}
