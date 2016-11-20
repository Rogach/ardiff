package org.rogach.ardiff.formats;

import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.rogach.ardiff.ArchiveDiff;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.zip.CRC32;

public class ZipArchiveDiff extends ArchiveDiff<ZipArchiveEntry> {

    static final byte ATTR_EXTRA = 1;
    static final byte ATTR_COMMENT = 2;
    static final byte ATTR_VERSION_MADE_BY = 3;
    static final byte ATTR_TIME = 4;
    static final byte ATTR_INTERNAL_ATTRIBUTES = 5;
    static final byte ATTR_EXTERNAL_ATTRIBUTES = 6;

    @Override
    public String archiverName() {
        return ArchiveStreamFactory.ZIP;
    }

    @Override
    public ZipArchiveEntry createNewArchiveEntry(String path, int length) {
        ZipArchiveEntry newEntry = new ZipArchiveEntry(path);
        newEntry.setSize(length);
        return newEntry;
    }

    @Override
    public ZipArchiveEntry copyArchiveEntry(ZipArchiveEntry orig, int length) throws IOException {
        ZipArchiveEntry newEntry = new ZipArchiveEntry(orig);
        newEntry.setSize(length);
        return newEntry;
    }

    @Override
    public void writeAttributes(ZipArchiveEntry entry, DataOutputStream diffStream) throws IOException {
        writeAttribute(ATTR_EXTRA, entry.getExtra(), diffStream);
        writeAttribute(ATTR_EXTRA, entry.getComment(), diffStream);
        writeAttribute(ATTR_VERSION_MADE_BY, entry.getVersionMadeBy(), diffStream);
        writeAttribute(ATTR_TIME, entry.getTime(), diffStream);
        writeAttribute(ATTR_INTERNAL_ATTRIBUTES, entry.getInternalAttributes(), diffStream);
        writeAttribute(ATTR_EXTERNAL_ATTRIBUTES, entry.getExternalAttributes(), diffStream);
        diffStream.writeByte(0);
    }

    @Override
    public ZipArchiveEntry readAttributes(ZipArchiveEntry entry, DataInputStream diffStream) throws IOException {
        do {
            switch (diffStream.readByte()) {
                case 0: return entry;
                case ATTR_EXTRA: entry.setExtra(readBytes(diffStream)); break;
                case ATTR_COMMENT: entry.setComment(readString(diffStream)); break;
                case ATTR_VERSION_MADE_BY: entry.setVersionMadeBy(diffStream.readInt()); break;
                case ATTR_TIME: entry.setTime(diffStream.readLong()); break;
                case ATTR_INTERNAL_ATTRIBUTES: entry.setInternalAttributes(diffStream.readInt()); break;
                case ATTR_EXTERNAL_ATTRIBUTES: entry.setExternalAttributes(diffStream.readLong()); break;
            }
        } while (true);
    }

    @Override
    public boolean attributesEqual(ZipArchiveEntry entryBefore, ZipArchiveEntry entryAfter) {
        return Arrays.equals(entryBefore.getExtra(), entryAfter.getExtra()) &&
                Objects.equals(entryBefore.getComment(), entryAfter.getComment()) &&
                entryBefore.getVersionMadeBy() == entryAfter.getVersionMadeBy() &&
                entryBefore.getTime() == entryAfter.getTime() &&
                entryBefore.getInternalAttributes() == entryAfter.getInternalAttributes() &&
                entryBefore.getExternalAttributes() == entryAfter.getExternalAttributes();
    }

    @Override
    public void writeAttributesDiff(ZipArchiveEntry entryBefore, ZipArchiveEntry entryAfter, DataOutputStream diffStream) throws IOException {
        diffAttributes(ATTR_EXTRA, entryBefore.getExtra(), entryAfter.getExtra(), diffStream);
        diffAttributes(ATTR_COMMENT, entryBefore.getComment(), entryAfter.getComment(), diffStream);
        diffAttributes(ATTR_VERSION_MADE_BY, entryBefore.getVersionMadeBy(), entryAfter.getVersionMadeBy(), diffStream);
        diffAttributes(ATTR_TIME, entryBefore.getTime(), entryAfter.getTime(), diffStream);
        diffAttributes(ATTR_INTERNAL_ATTRIBUTES, entryBefore.getInternalAttributes(), entryAfter.getInternalAttributes(), diffStream);
        diffAttributes(ATTR_EXTERNAL_ATTRIBUTES, entryBefore.getExternalAttributes(), entryAfter.getExternalAttributes(), diffStream);
        diffStream.writeByte(0);
    }

    @Override
    public void readEntryChecksum(ZipArchiveEntry entry, DataInputStream diffStream) throws IOException {
        entry.setCrc(diffStream.readLong());
    }

    @Override
    public void writeEntryChecksum(byte[] data, DataOutputStream diffStream) throws IOException {
        CRC32 checksum = new CRC32();
        checksum.update(data);

        diffStream.writeLong(checksum.getValue());
    }

    @Override
    public ZipArchiveEntry getEntryForData(ZipArchiveEntry entry, byte[] data) {
        CRC32 checksum = new CRC32();
        checksum.update(data);

        entry.setCrc(checksum.getValue());
        entry.setSize(data.length);
        return entry;
    }
}

