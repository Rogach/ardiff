package org.rogach.ardiff;

import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

class ZipArchiveDiff extends ArchiveDiff<ZipArchiveEntry> {

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
    public ZipArchiveEntry createNewArchiveEntry(String path) {
        return new ZipArchiveEntry(path);
    }

    @Override
    public ZipArchiveEntry copyArchiveEntry(ZipArchiveEntry orig) throws IOException {
        return new ZipArchiveEntry(orig);
    }

    @Override
    public void writeAttributes(ZipArchiveEntry entry, DataOutputStream diffStream) throws IOException {
        if (entry.getExtra() != null) {
            diffStream.writeByte(ATTR_EXTRA);
            diffStream.writeShort(entry.getExtra().length);
            diffStream.write(entry.getExtra());
        }

        if (entry.getComment() != null) {
            diffStream.writeByte(ATTR_COMMENT);
            byte[] commentBytes = entry.getComment().getBytes("UTF-8");
            diffStream.writeShort(commentBytes.length);
            diffStream.write(commentBytes);
        }

        diffStream.writeByte(ATTR_VERSION_MADE_BY);
        diffStream.writeInt(entry.getVersionMadeBy());

        diffStream.writeByte(ATTR_TIME);
        diffStream.writeLong(entry.getTime());

        diffStream.writeByte(ATTR_INTERNAL_ATTRIBUTES);
        diffStream.writeInt(entry.getInternalAttributes());

        diffStream.writeByte(ATTR_EXTERNAL_ATTRIBUTES);
        diffStream.writeLong(entry.getExternalAttributes());

        diffStream.writeByte(0);
    }

    @Override
    public void readAttributes(ZipArchiveEntry entry, DataInputStream diffStream) throws IOException {
        do {
            byte command = diffStream.readByte();
            if (command == 0) {
                return;
            }

            if (command == ATTR_EXTRA) {
                short length = diffStream.readShort();
                byte[] extra = new byte[length];
                diffStream.readFully(extra);
                entry.setExtra(extra);
            }

            if (command == ATTR_COMMENT) {
                short length = diffStream.readShort();
                byte[] commentBytes = new byte[length];
                diffStream.readFully(commentBytes);
                entry.setComment(new String(commentBytes, "UTF-8"));
            }

            if (command == ATTR_VERSION_MADE_BY) {
                entry.setVersionMadeBy(diffStream.readInt());
            }

            if (command == ATTR_TIME) {
                entry.setTime(diffStream.readLong());
            }

            if (command == ATTR_INTERNAL_ATTRIBUTES) {
                entry.setInternalAttributes(diffStream.readInt());
            }

            if (command == ATTR_EXTERNAL_ATTRIBUTES) {
                entry.setExternalAttributes(diffStream.readLong());
            }

        } while (true);
    }

    @Override
    public boolean attributesEqual(ZipArchiveEntry entryBefore, ZipArchiveEntry entryAfter) {
        return Arrays.equals(entryBefore.getExtra(), entryAfter.getExtra()) &&
                Objects.deepEquals(entryBefore.getComment(), entryAfter.getComment()) &&
                entryBefore.getVersionMadeBy() == entryAfter.getVersionMadeBy() &&
                entryBefore.getTime() == entryAfter.getTime() &&
                entryBefore.getInternalAttributes() == entryAfter.getInternalAttributes() &&
                entryBefore.getExternalAttributes() == entryAfter.getExternalAttributes();
    }

    @Override
    public void writeAttributesDiff(ZipArchiveEntry entryBefore, ZipArchiveEntry entryAfter, DataOutputStream diffStream) throws IOException {
        if (!Arrays.equals(entryBefore.getExtra(), entryAfter.getExtra())) {
            diffStream.writeByte(ATTR_EXTRA);

            byte[] extra = entryAfter.getExtra();
            if (extra != null) {
                diffStream.writeShort(extra.length);
                diffStream.write(extra);
            } else {
                diffStream.writeShort(0);
            }
        }

        if (!Objects.deepEquals(entryBefore.getComment(), entryAfter.getComment())) {
            diffStream.writeByte(ATTR_COMMENT);

            String comment = entryAfter.getComment();
            if (comment != null) {
                byte[] commentBytes = comment.getBytes("UTF-8");
                diffStream.writeShort(commentBytes.length);
                diffStream.write(commentBytes);
            } else {
                diffStream.writeShort(0);
            }
        }

        if (entryBefore.getVersionMadeBy() != entryAfter.getVersionMadeBy()) {
            diffStream.writeByte(ATTR_VERSION_MADE_BY);
            diffStream.writeInt(entryAfter.getVersionMadeBy());
        }

        if (entryBefore.getTime() != entryAfter.getTime()) {
            diffStream.writeByte(ATTR_TIME);
            diffStream.writeLong(entryAfter.getTime());
        }

        if (entryBefore.getInternalAttributes() != entryAfter.getInternalAttributes()) {
            diffStream.writeByte(ATTR_INTERNAL_ATTRIBUTES);
            diffStream.writeInt(entryAfter.getInternalAttributes());
        }

        if (entryBefore.getExternalAttributes() != entryAfter.getExternalAttributes()) {
            diffStream.writeByte(ATTR_INTERNAL_ATTRIBUTES);
            diffStream.writeLong(entryAfter.getExternalAttributes());
        }

        diffStream.writeByte(0);
    }

}

