package org.rogach.ardiff;

import com.nothome.delta.GDiffPatcher;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.utils.CountingInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.rogach.ardiff.exceptions.ArchiveDiffCorruptedException;
import org.rogach.ardiff.exceptions.ArchiveDiffException;
import org.rogach.ardiff.exceptions.ArchiveDiffFormatException;

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

public interface ArchiveDiffReader<GenArchiveEntry extends ArchiveEntry>
        extends ArchiveDiffBase<GenArchiveEntry> {

    default void applyDiffImpl(
            InputStream before,
            InputStream diff,
            OutputStream after
    ) throws ArchiveException, IOException, ArchiveDiffException {
        ArchiveInputStream archiveStreamBefore = createArchiveInputStream(before);
        CheckedInputStream checkedDiffStream = new CheckedInputStream(diff, new CRC32());
        CountingInputStream countingDiffStream = new CountingInputStream(checkedDiffStream);
        DataInputStream diffStream = new DataInputStream(countingDiffStream);

        ArchiveOutputStream archiveStreamAfter = createArchiveOutputStream(after);

        byte[] header = new byte[8];
        diffStream.readFully(header);

        if (!Arrays.equals(header, ArchiveDiff.HEADER.getBytes("ASCII"))) {
            throw new ArchiveDiffFormatException("Invalid diff stream header");
        }

        HashMap<String, ArchiveEntryWithData<GenArchiveEntry>> entries = readAllEntries(archiveStreamBefore);

        do {
            checkedDiffStream.getChecksum().reset();

            byte command = diffStream.readByte();

            if (command == 0) {
                break;
            }

            String path = readString(diffStream);
            ArchiveEntryWithData<GenArchiveEntry> entryBefore = entries.get(path);

            if (command == ArchiveDiff.COMMAND_ADD) {
                entries.put(path, readEntryAdd(path, diffStream));
            } else if (command == ArchiveDiff.COMMAND_REPLACE) {
                entries.put(path, readEntryReplace(entryBefore.entry, diffStream));
            } else if (command == ArchiveDiff.COMMAND_REMOVE) {
                entries.remove(path);
            } else if (command == ArchiveDiff.COMMAND_PATCH) {
                entries.put(path, readEntryPatch(entryBefore, diffStream));
            } else if (command == ArchiveDiff.COMMAND_ARCHIVE_PATCH) {
                entries.put(path, readEntryArchivePatch(entryBefore, diffStream));
            } else if (command == ArchiveDiff.COMMAND_UPDATE_ATTRIBUTES) {
                entries.put(path, readEntryUpdateAttributes(entryBefore, diffStream));
            } else {
                throw new ArchiveDiffException("Unexpected command: " + command);
            }

            long checksum = checkedDiffStream.getChecksum().getValue();
            long expectedChecksum = diffStream.readLong();
            if (checksum != expectedChecksum) {
                throw new ArchiveDiffCorruptedException("Checksum mismatch at offset " + countingDiffStream.getBytesRead());
            }
        } while (true);

        for (ArchiveEntryWithData<GenArchiveEntry> entry : entries.values()) {
            archiveStreamAfter.putArchiveEntry(entry.entry);
            IOUtils.copy(new ByteArrayInputStream(entry.data), archiveStreamAfter);
            archiveStreamAfter.closeArchiveEntry();
        }

        finishArchiveOutputStream(archiveStreamAfter);
    }

    GenArchiveEntry createNewArchiveEntry(String path, int length);

    GenArchiveEntry copyArchiveEntry(GenArchiveEntry orig, int length) throws IOException;

    default ArchiveEntryWithData<GenArchiveEntry> readEntryAdd(String path, DataInputStream diffStream) throws IOException {
        int dataLength = diffStream.readInt();

        GenArchiveEntry entry = createNewArchiveEntry(path, dataLength);

        readEntryChecksum(entry, diffStream);

        entry = readAttributes(entry, diffStream);

        byte[] data = new byte[dataLength];
        diffStream.readFully(data);

        return new ArchiveEntryWithData<>(entry, data);
    }

    default ArchiveEntryWithData<GenArchiveEntry> readEntryReplace(GenArchiveEntry before, DataInputStream diffStream) throws IOException {
        int dataLength = diffStream.readInt();

        GenArchiveEntry entry = copyArchiveEntry(before, dataLength);

        readEntryChecksum(entry, diffStream);

        entry = readAttributes(entry, diffStream);

        byte[] data = new byte[dataLength];
        diffStream.readFully(data);

        return new ArchiveEntryWithData<>(entry, data);
    }

    default ArchiveEntryWithData<GenArchiveEntry> readEntryPatch(ArchiveEntryWithData<GenArchiveEntry> entryBefore, DataInputStream diffStream) throws IOException {
        int length = diffStream.readInt();

        GenArchiveEntry entryAfter = copyArchiveEntry(entryBefore.entry, length);

        readEntryChecksum(entryAfter, diffStream);

        entryAfter = readAttributes(entryAfter, diffStream);

        int patchLength = diffStream.readInt();
        byte[] patch = new byte[patchLength];
        diffStream.readFully(patch);

        byte[] dataAfter = new GDiffPatcher().patch(entryBefore.data, patch);

        return new ArchiveEntryWithData<>(entryAfter, dataAfter);
    }

    default ArchiveEntryWithData<GenArchiveEntry> readEntryArchivePatch(ArchiveEntryWithData<GenArchiveEntry> entryBefore, DataInputStream diffStream) throws IOException, ArchiveDiffException, ArchiveException {
        int length = diffStream.readInt();

        GenArchiveEntry entryAfter = copyArchiveEntry(entryBefore.entry, length);

        readEntryChecksum(entryAfter, diffStream);

        entryAfter = readAttributes(entryAfter, diffStream);

        diffStream.readInt();

        ByteArrayOutputStream after = new ByteArrayOutputStream();
        ArchiveDiff.applyDiff(
                new ByteArrayInputStream(entryBefore.data),
                diffStream,
                after
        );
        after.close();

        return new ArchiveEntryWithData<>(entryAfter, after.toByteArray());
    }

    default ArchiveEntryWithData<GenArchiveEntry> readEntryUpdateAttributes(ArchiveEntryWithData<GenArchiveEntry> entryBefore, DataInputStream diffStream) throws IOException {
        int length = diffStream.readInt();

        GenArchiveEntry entryAfter = copyArchiveEntry(entryBefore.entry, length);

        entryAfter = readAttributes(entryAfter, diffStream);

        return new ArchiveEntryWithData<>(entryAfter, entryBefore.data);
    }

    default void readEntryChecksum(GenArchiveEntry entry, DataInputStream diffStream) throws IOException {}

    GenArchiveEntry readAttributes(GenArchiveEntry entry, DataInputStream diffStream) throws IOException;

    default String readString(DataInputStream diffStream) throws IOException {
        return new String(readBytes(diffStream), "UTF-8");
    }

    default byte[] readBytes(DataInputStream diffStream) throws IOException {
        short length = diffStream.readShort();
        byte[] bytes = new byte[length];
        diffStream.readFully(bytes);
        return bytes;
    }

}
