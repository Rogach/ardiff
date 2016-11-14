package org.rogach.ardiff;

import com.nothome.delta.GDiffPatcher;
import org.apache.commons.compress.archivers.*;
import org.apache.commons.compress.utils.IOUtils;
import org.rogach.ardiff.exceptions.ArchiveDiffCorruptedException;
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
            boolean assumeOrdering,
            OutputStream after
    ) throws ArchiveException, IOException, ArchiveDiffFormatException, ArchiveDiffCorruptedException {
        ArchiveInputStream archiveStreamBefore = new ArchiveStreamFactory().createArchiveInputStream(archiverName(), before);
        CheckedInputStream checkedDiffStream = new CheckedInputStream(diff, new CRC32());
        DataInputStream diffStream = new DataInputStream(checkedDiffStream);

        ArchiveOutputStream archiveStreamAfter = new ArchiveStreamFactory().createArchiveOutputStream(archiverName(), after);

        byte[] header = new byte[8];
        diffStream.readFully(header);

        if (!Arrays.equals(header, ArchiveDiff.HEADER.getBytes("ASCII"))) {
            throw new ArchiveDiffFormatException("Invalid diff stream header");
        }

        if (!assumeOrdering) {
            HashMap<String, ArchiveEntryWithData<GenArchiveEntry>> entries = readAllEntries(archiveStreamBefore);

            do {
                checkedDiffStream.getChecksum().reset();

                byte command;

                try {
                    command = diffStream.readByte();
                } catch (EOFException ex) {
                    command = 0;
                }

                if (command == 0) {
                    break;
                }

                String path = readPath(diffStream);
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
                    throw new UnsupportedOperationException("Patch for recursive archives is not yet implemented");
                } else if (command == ArchiveDiff.COMMAND_UPDATE_ATTRIBUTES) {
                    entries.put(path, readEntryUpdateAttributes(entryBefore, diffStream));
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
        } else {
            throw new UnsupportedOperationException("Diff for sorted archives is not yet implemented");
        }

        archiveStreamAfter.close();
    }

    GenArchiveEntry createNewArchiveEntry(String path);

    GenArchiveEntry copyArchiveEntry(GenArchiveEntry orig) throws IOException;

    default ArchiveEntryWithData<GenArchiveEntry> readEntryAdd(String path, DataInputStream diffStream) throws IOException {
        GenArchiveEntry entry = createNewArchiveEntry(path);

        readAttributes(entry, diffStream);

        int dataLength = diffStream.readInt();
        byte[] data = new byte[dataLength];
        diffStream.readFully(data);

        return new ArchiveEntryWithData<>(entry, data);
    }

    default ArchiveEntryWithData<GenArchiveEntry> readEntryReplace(GenArchiveEntry before, DataInputStream diffStream) throws IOException {
        GenArchiveEntry entry = copyArchiveEntry(before);

        readAttributes(entry, diffStream);

        int dataLength = diffStream.readInt();
        byte[] data = new byte[dataLength];
        diffStream.readFully(data);

        return new ArchiveEntryWithData<>(entry, data);
    }

    default ArchiveEntryWithData<GenArchiveEntry> readEntryPatch(ArchiveEntryWithData<GenArchiveEntry> entryBefore, DataInputStream diffStream) throws IOException {
        GenArchiveEntry entryAfter = copyArchiveEntry(entryBefore.entry);

        readAttributes(entryAfter, diffStream);

        int patchLength = diffStream.readInt();
        byte[] patch = new byte[patchLength];
        diffStream.readFully(patch);

        byte[] dataAfter = new GDiffPatcher().patch(entryBefore.data, patch);

        return new ArchiveEntryWithData<>(entryAfter, dataAfter);
    }

    default ArchiveEntryWithData<GenArchiveEntry> readEntryUpdateAttributes(ArchiveEntryWithData<GenArchiveEntry> entryBefore, DataInputStream diffStream) throws IOException {
        GenArchiveEntry entryAfter = copyArchiveEntry(entryBefore.entry);

        readAttributes(entryAfter, diffStream);

        return new ArchiveEntryWithData<>(entryAfter, entryBefore.data);
    }

    void readAttributes(GenArchiveEntry entry, DataInputStream diffStream) throws IOException;

    default String readPath(DataInputStream diffStream) throws IOException {
        short length = diffStream.readShort();
        byte[] bytes = new byte[length];
        diffStream.readFully(bytes);
        return new String(bytes, "UTF-8");
    }

}
