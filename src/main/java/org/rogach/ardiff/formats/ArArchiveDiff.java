package org.rogach.ardiff.formats;

import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.rogach.ardiff.ArchiveDiff;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ArArchiveDiff extends ArchiveDiff<ArArchiveEntry> {

    static final byte ATTR_USER_ID = 1;
    static final byte ATTR_GROUP_ID = 2;
    static final byte ATTR_MODE = 3;
    static final byte ATTR_LAST_MODIFIED = 4;

    @Override
    public String archiverName() {
        return "ar";
    }


    @Override
    public ArArchiveEntry createNewArchiveEntry(String path, int length) {
        return new ArArchiveEntry(path, length);
    }

    @Override
    public ArArchiveEntry copyArchiveEntry(ArArchiveEntry orig, int length) {
        return new ArArchiveEntry(
                orig.getName(),
                length,
                orig.getUserId(),
                orig.getGroupId(),
                orig.getMode(),
                orig.getLastModified()
        );
    }

    @Override()
    public void writeAttributes(ArArchiveEntry entry, DataOutputStream diffStream) throws IOException {
        writeAttribute(ATTR_USER_ID, entry.getUserId(), diffStream);
        writeAttribute(ATTR_GROUP_ID, entry.getGroupId(), diffStream);
        writeAttribute(ATTR_MODE, entry.getMode(), diffStream);
        writeAttribute(ATTR_LAST_MODIFIED, entry.getLastModified(), diffStream);
        diffStream.writeByte(0);
    }

    @Override
    public ArArchiveEntry readAttributes(ArArchiveEntry entry, DataInputStream diffStream) throws IOException {
        do {
            byte command = diffStream.readByte();
            switch (command) {
                case 0: return entry;
                case ATTR_USER_ID: entry = new ArArchiveEntry(entry.getName(), entry.getLength(), diffStream.readInt(), entry.getGroupId(), entry.getMode(), entry.getLastModified()); break;
                case ATTR_GROUP_ID: entry = new ArArchiveEntry(entry.getName(), entry.getLength(), entry.getUserId(), diffStream.readInt(), entry.getMode(), entry.getLastModified()); break;
                case ATTR_MODE: entry = new ArArchiveEntry(entry.getName(), entry.getLength(), entry.getUserId(), entry.getGroupId(), diffStream.readInt(), entry.getLastModified()); break;
                case ATTR_LAST_MODIFIED: entry = new ArArchiveEntry(entry.getName(), entry.getLength(), entry.getUserId(), entry.getGroupId(), entry.getMode(), diffStream.readLong()); break;
            }
        } while (true);
    }

    @Override
    public boolean attributesEqual(ArArchiveEntry entryBefore, ArArchiveEntry entryAfter) {
        return entryBefore.getUserId() == entryAfter.getUserId() &&
                entryBefore.getGroupId() == entryAfter.getGroupId() &&
                entryBefore.getMode() == entryAfter.getMode() &&
                entryBefore.getLastModified() == entryAfter.getLastModified();
    }

    @Override
    public void writeAttributesDiff(ArArchiveEntry entryBefore, ArArchiveEntry entryAfter, DataOutputStream diffStream) throws IOException {
        diffAttributes(ATTR_USER_ID, entryBefore.getUserId(), entryAfter.getUserId(), diffStream);
        diffAttributes(ATTR_GROUP_ID, entryBefore.getGroupId(), entryAfter.getGroupId(), diffStream);
        diffAttributes(ATTR_MODE, entryBefore.getMode(), entryAfter.getMode(), diffStream);
        diffAttributes(ATTR_LAST_MODIFIED, entryBefore.getLastModified(), entryAfter.getLastModified(), diffStream);
        diffStream.writeByte(0);
    }

    @Override
    public ArArchiveEntry getEntryForData(ArArchiveEntry entry, byte[] data) {
        return copyArchiveEntry(entry, data.length);
    }
}
