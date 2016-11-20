package org.rogach.ardiff.formats;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
import org.rogach.ardiff.ArchiveDiff;
import org.rogach.ardiff.exceptions.ArchiveDiffException;

import java.io.*;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class TarArchiveDiff extends ArchiveDiff<TarArchiveEntry> {

    private String compressionType;

    public TarArchiveDiff(String compressionType) {
        this.compressionType = compressionType;
    }

    static final byte ATTR_MODE = 1;
    static final byte ATTR_USER_ID = 2;
    static final byte ATTR_GROUP_ID = 3;
    static final byte ATTR_MOD_TIME = 4;
    static final byte ATTR_LINK_NAME = 5;
    static final byte ATTR_USER_NAME = 6;
    static final byte ATTR_GROUP_NAME = 7;
    static final byte ATTR_DEV_MAJOR = 8;
    static final byte ATTR_DEV_MINOR = 9;

    @Override
    public String archiverName() {
        return "tar";
    }

    private GZIPOutputStream gzipOutputStream;

    @Override
    public ArchiveOutputStream createArchiveOutputStream(OutputStream output) throws IOException, ArchiveDiffException, ArchiveException {
        OutputStream compressedOutputStream;
        if ("".equals(compressionType)) {
            compressedOutputStream = output;
        } else if ("gz".equals(compressionType)) {
            gzipOutputStream = new GZIPOutputStream(output);
            compressedOutputStream = gzipOutputStream;
        } else if ("xz".equals(compressionType)) {
            compressedOutputStream = new XZCompressorOutputStream(output);
        } else {
            throw new ArchiveDiffException("Unexpected tar compression type: " + compressionType);
        }
        TarArchiveOutputStream outputStream = new TarArchiveOutputStream(compressedOutputStream);
        outputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
        return outputStream;
    }

    @Override
    public void finishArchiveOutputStream(ArchiveOutputStream output) throws IOException {
        output.finish();
        gzipOutputStream.finish();
    }

    @Override
    public ArchiveInputStream createArchiveInputStream(InputStream input) throws IOException, ArchiveDiffException, ArchiveException {
        InputStream decompressedInputStream;
        if ("".equals(compressionType)) {
            decompressedInputStream = input;
        } else if ("gz".equals(compressionType)) {
            decompressedInputStream = new GZIPInputStream(input);
        } else if ("xz".equals(compressionType)) {
            decompressedInputStream = new XZCompressorInputStream(input);
        } else {
            throw new ArchiveDiffException("Unexpected tar compression type: " + compressionType);
        }
        return new TarArchiveInputStream(decompressedInputStream);
    }

    @Override
    public TarArchiveEntry createNewArchiveEntry(String path, int length) {
        TarArchiveEntry newEntry = new TarArchiveEntry(path);
        newEntry.setSize(length);
        return newEntry;
    }

    @Override
    public TarArchiveEntry copyArchiveEntry(TarArchiveEntry orig, int length) throws IOException {
        byte[] header = new byte[TarConstants.DEFAULT_RCDSIZE];
        orig.writeEntryHeader(header);
        TarArchiveEntry newEntry = new TarArchiveEntry(header);
        newEntry.setSize(length);
        return newEntry;
    }

    @Override
    public void writeAttributes(TarArchiveEntry entry, DataOutputStream diffStream) throws IOException {
        writeAttribute(ATTR_MODE, entry.getMode(), diffStream);
        writeAttribute(ATTR_USER_ID, entry.getLongUserId(), diffStream);
        writeAttribute(ATTR_GROUP_ID, entry.getLongGroupId(), diffStream);
        writeAttribute(ATTR_MOD_TIME, entry.getModTime().getTime() / TarArchiveEntry.MILLIS_PER_SECOND, diffStream);
        writeAttribute(ATTR_LINK_NAME, entry.getLinkName(), diffStream);
        writeAttribute(ATTR_USER_NAME, entry.getUserName(), diffStream);
        writeAttribute(ATTR_GROUP_NAME, entry.getGroupName(), diffStream);
        writeAttribute(ATTR_DEV_MAJOR, entry.getDevMajor(), diffStream);
        writeAttribute(ATTR_DEV_MINOR, entry.getDevMinor(), diffStream);
        diffStream.writeByte(0);
    }

    @Override
    public TarArchiveEntry readAttributes(TarArchiveEntry entry, DataInputStream diffStream) throws IOException {
        do {
            byte command = diffStream.readByte();
            switch (command) {
                case 0: return entry;
                case ATTR_MODE: entry.setMode(diffStream.readInt()); break;
                case ATTR_USER_ID: entry.setUserId(diffStream.readLong()); break;
                case ATTR_GROUP_ID: entry.setGroupId(diffStream.readLong()); break;
                case ATTR_MOD_TIME: entry.setModTime(diffStream.readLong() * TarArchiveEntry.MILLIS_PER_SECOND); break;
                case ATTR_LINK_NAME: entry.setLinkName(readString(diffStream)); break;
                case ATTR_USER_NAME: entry.setUserName(readString(diffStream)); break;
                case ATTR_GROUP_NAME: entry.setGroupName(readString(diffStream)); break;
                case ATTR_DEV_MAJOR: entry.setDevMajor(diffStream.readInt()); break;
                case ATTR_DEV_MINOR: entry.setDevMinor(diffStream.readInt()); break;
            }
        } while (true);
    }

    @Override
    public boolean attributesEqual(TarArchiveEntry entryBefore, TarArchiveEntry entryAfter) {
        return entryBefore.getMode() == entryAfter.getMode() &&
                entryBefore.getLongUserId() == entryAfter.getLongUserId() &&
                entryBefore.getLongGroupId() == entryAfter.getLongGroupId() &&
                entryBefore.getModTime().getTime() == entryAfter.getModTime().getTime() &&
                Objects.equals(entryBefore.getLinkName(), entryAfter.getLinkName()) &&
                Objects.equals(entryBefore.getUserName(), entryAfter.getUserName()) &&
                Objects.equals(entryBefore.getGroupName(), entryAfter.getGroupName()) &&
                entryBefore.getDevMajor() == entryAfter.getDevMajor() &&
                entryBefore.getDevMinor() == entryAfter.getDevMinor();
    }

    @Override
    public void writeAttributesDiff(TarArchiveEntry entryBefore, TarArchiveEntry entryAfter, DataOutputStream diffStream) throws IOException {
        diffAttributes(ATTR_MODE, entryBefore.getMode(), entryAfter.getMode(), diffStream);
        diffAttributes(ATTR_USER_ID, entryBefore.getLongUserId(), entryAfter.getLongUserId(), diffStream);
        diffAttributes(ATTR_GROUP_ID, entryBefore.getLongGroupId(), entryAfter.getLongGroupId(), diffStream);
        diffAttributes(ATTR_MOD_TIME, entryBefore.getModTime().getTime() / TarArchiveEntry.MILLIS_PER_SECOND, entryAfter.getModTime().getTime() / TarArchiveEntry.MILLIS_PER_SECOND, diffStream);
        diffAttributes(ATTR_LINK_NAME, entryBefore.getLinkName(), entryAfter.getLinkName(), diffStream);
        diffAttributes(ATTR_USER_NAME, entryBefore.getUserName(), entryAfter.getUserName(), diffStream);
        diffAttributes(ATTR_GROUP_NAME, entryBefore.getGroupName(), entryAfter.getGroupName(), diffStream);
        diffAttributes(ATTR_DEV_MAJOR, entryBefore.getDevMajor(), entryAfter.getDevMajor(), diffStream);
        diffAttributes(ATTR_DEV_MINOR, entryBefore.getDevMinor(), entryAfter.getDevMinor(), diffStream);
        diffStream.writeByte(0);
    }

    @Override
    public TarArchiveEntry getEntryForData(TarArchiveEntry entry, byte[] data) {
        entry.setSize(data.length);
        return entry;
    }

}
