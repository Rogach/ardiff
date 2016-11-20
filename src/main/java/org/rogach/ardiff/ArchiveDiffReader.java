package org.rogach.ardiff;

import com.nothome.delta.GDiffPatcher;
import org.apache.commons.compress.archivers.*;
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
        ArchiveInputStream archiveStreamBefore = new ArchiveStreamFactory().createArchiveInputStream(archiverName(), before);
        CheckedInputStream checkedDiffStream = new CheckedInputStream(diff, new CRC32());
        DataInputStream diffStream = new DataInputStream(checkedDiffStream);

        ArchiveOutputStream archiveStreamAfter = new ArchiveStreamFactory().createArchiveOutputStream(archiverName(), after);

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
                throw new ArchiveDiffCorruptedException("Checksum mismatch at offset " + diffStream.read());
            }
        } while (true);

        for (ArchiveEntryWithData<GenArchiveEntry> entry : entries.values()) {
            archiveStreamAfter.putArchiveEntry(entry.entry);
            IOUtils.copy(new ByteArrayInputStream(entry.data), archiveStreamAfter);
            archiveStreamAfter.closeArchiveEntry();
        }

        archiveStreamAfter.finish();
    }

    GenArchiveEntry createNewArchiveEntry(String path);

    GenArchiveEntry copyArchiveEntry(GenArchiveEntry orig) throws IOException;

    default ArchiveEntryWithData<GenArchiveEntry> readEntryAdd(String path, DataInputStream diffStream) throws IOException {
        GenArchiveEntry entry = createNewArchiveEntry(path);

        readAttributes(entry, diffStream);

        readEntrySizeAndChecksum(entry, diffStream);

        int dataLength = diffStream.readInt();
        byte[] data = new byte[dataLength];
        diffStream.readFully(data);

        return new ArchiveEntryWithData<>(entry, data);
    }

    default ArchiveEntryWithData<GenArchiveEntry> readEntryReplace(GenArchiveEntry before, DataInputStream diffStream) throws IOException {
        GenArchiveEntry entry = copyArchiveEntry(before);

        readAttributes(entry, diffStream);

        readEntrySizeAndChecksum(entry, diffStream);

        int dataLength = diffStream.readInt();
        byte[] data = new byte[dataLength];
        diffStream.readFully(data);

        return new ArchiveEntryWithData<>(entry, data);
    }

    default ArchiveEntryWithData<GenArchiveEntry> readEntryPatch(ArchiveEntryWithData<GenArchiveEntry> entryBefore, DataInputStream diffStream) throws IOException {
        GenArchiveEntry entryAfter = copyArchiveEntry(entryBefore.entry);

        readAttributes(entryAfter, diffStream);

        readEntrySizeAndChecksum(entryAfter, diffStream);

        int patchLength = diffStream.readInt();
        byte[] patch = new byte[patchLength];
        diffStream.readFully(patch);

        byte[] dataAfter = new GDiffPatcher().patch(entryBefore.data, patch);

        return new ArchiveEntryWithData<>(entryAfter, dataAfter);
    }

    default ArchiveEntryWithData<GenArchiveEntry> readEntryArchivePatch(ArchiveEntryWithData<GenArchiveEntry> entryBefore, DataInputStream diffStream) throws IOException, ArchiveDiffException, ArchiveException {
        GenArchiveEntry entryAfter = copyArchiveEntry(entryBefore.entry);

        readAttributes(entryAfter, diffStream);

        readEntrySizeAndChecksum(entryAfter, diffStream);

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
        GenArchiveEntry entryAfter = copyArchiveEntry(entryBefore.entry);

        readAttributes(entryAfter, diffStream);

        return new ArchiveEntryWithData<>(entryAfter, entryBefore.data);
    }

    void readEntrySizeAndChecksum(GenArchiveEntry entry, DataInputStream diffStream) throws IOException;

    void readAttributes(GenArchiveEntry entry, DataInputStream diffStream) throws IOException;

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
