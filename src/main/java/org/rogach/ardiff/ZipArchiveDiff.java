package org.rogach.ardiff;

import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

public class ZipArchiveDiff extends ArchiveDiff<ZipArchiveEntry> {

    static final byte ATTR_EXTRA = 1;
    static final byte ATTR_COMMENT = 2;
    static final byte ATTR_PLATFORM = 3;
    static final byte ATTR_TIME = 4;
    static final byte ATTR_INTERNAL = 5;
    static final byte ATTR_EXTERNAL = 6;

    @Override
    String archiverName() {
        return ArchiveStreamFactory.ZIP;
    }

    @Override
    protected void writeAttributes(ZipArchiveEntry entry, DataOutputStream diffStream) throws IOException {
        if (entry.getExtra() != null) {
            diffStream.writeByte(ATTR_EXTRA);
            diffStream.writeShort(entry.getExtra().length);
            diffStream.write(entry.getExtra());
        }

        if (entry.getComment() != null) {
            diffStream.writeByte(ATTR_COMMENT);
            byte[] comment = entry.getComment().getBytes("UTF-8");
            diffStream.writeShort(comment.length);
            diffStream.write(comment);
        }

        diffStream.writeByte(ATTR_PLATFORM);
        diffStream.writeInt(entry.getPlatform());

        diffStream.writeByte(ATTR_TIME);
        diffStream.writeLong(entry.getTime());

        diffStream.writeByte(ATTR_INTERNAL);
        diffStream.writeInt(entry.getInternalAttributes());

        diffStream.writeByte(ATTR_EXTERNAL);
        diffStream.writeLong(entry.getExternalAttributes());

        diffStream.writeByte(0);
    }

    @Override
    protected boolean areAttributesDifferent(ZipArchiveEntry entryBefore, ZipArchiveEntry entryAfter) {
        return !Arrays.equals(entryBefore.getExtra(), entryAfter.getExtra()) ||
                !Objects.deepEquals(entryBefore.getComment(), entryAfter.getComment()) ||
                entryBefore.getPlatform() != entryAfter.getPlatform() ||
                entryBefore.getTime() != entryAfter.getTime() ||
                entryBefore.getInternalAttributes() != entryAfter.getInternalAttributes() ||
                entryBefore.getExternalAttributes() != entryAfter.getExternalAttributes();
    }

    @Override
    protected void writeAttributesDiff(ZipArchiveEntry entryBefore, ZipArchiveEntry entryAfter, DataOutputStream diffStream) throws IOException {
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

        if (entryBefore.getPlatform() != entryAfter.getPlatform()) {
            diffStream.writeByte(ATTR_PLATFORM);
            diffStream.writeInt(entryAfter.getPlatform());
        }

        if (entryBefore.getTime() != entryAfter.getTime()) {
            diffStream.writeByte(ATTR_TIME);
            diffStream.writeLong(entryAfter.getTime());
        }

        if (entryBefore.getInternalAttributes() != entryAfter.getInternalAttributes()) {
            diffStream.writeByte(ATTR_INTERNAL);
            diffStream.writeInt(entryAfter.getInternalAttributes());
        }

        if (entryBefore.getExternalAttributes() != entryAfter.getExternalAttributes()) {
            diffStream.writeByte(ATTR_INTERNAL);
            diffStream.writeLong(entryAfter.getExternalAttributes());
        }

        diffStream.writeByte(0);
    }
}

