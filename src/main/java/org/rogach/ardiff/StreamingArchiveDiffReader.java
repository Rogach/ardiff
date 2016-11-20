package org.rogach.ardiff;

import com.nothome.delta.GDiffPatcher;
import org.apache.commons.compress.archivers.*;
import org.apache.commons.compress.utils.BoundedInputStream;
import org.apache.commons.compress.utils.CountingInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.rogach.ardiff.exceptions.ArchiveDiffCorruptedException;
import org.rogach.ardiff.exceptions.ArchiveDiffException;
import org.rogach.ardiff.exceptions.ArchiveDiffFormatException;

import java.io.*;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

public class StreamingArchiveDiffReader<GenArchiveEntry extends ArchiveEntry> {

    private InputStream before;
    private InputStream diff;
    private OutputStream after;
    private ArchiveDiff<GenArchiveEntry> utils;

    public StreamingArchiveDiffReader(InputStream before, InputStream diff, OutputStream after, ArchiveDiff<GenArchiveEntry> utils) {
        this.before = before;
        this.diff = diff;
        this.after = after;
        this.utils = utils;
    }

    ArchiveInputStream archiveStreamBefore;
    CheckedInputStream checkedDiffStream;
    CountingInputStream countingDiffStream;
    DataInputStream diffStream;
    ArchiveOutputStream archiveStreamAfter;

    private GenArchiveEntry entry;
    private byte command;
    private String commandPath = null;

    void streamingApplyDiff() throws ArchiveException, IOException, ArchiveDiffException {
        archiveStreamBefore = utils.createArchiveInputStream(before);
        checkedDiffStream = new CheckedInputStream(diff, new CRC32());
        countingDiffStream = new CountingInputStream(checkedDiffStream);
        diffStream = new DataInputStream(countingDiffStream);

        archiveStreamAfter = utils.createArchiveOutputStream(after);

        byte[] header = new byte[8];
        diffStream.readFully(header);

        if (!Arrays.equals(header, ArchiveDiff.HEADER.getBytes("ASCII"))) {
            throw new ArchiveDiffFormatException("Invalid diff stream header");
        }

        readNextEntry();
        readNextDiffCommand();

        while (entry != null || command != 0) {
            if (command == 0) {
                copyUnchangedEntry();
                readNextEntry();
            } else if (entry == null) {
                if (command == ArchiveDiff.COMMAND_ADD) {
                    addEntry();
                    validateChecksum();
                    readNextDiffCommand();
                } else {
                    throw new ArchiveDiffException("Unexpected command: " + command);
                }
            } else {
                int entryOrder = entry.getName().compareTo(commandPath);
                if (entryOrder < 0) {
                    copyUnchangedEntry();
                    readNextEntry();
                } else if (entryOrder > 0) {
                    if (command == ArchiveDiff.COMMAND_ADD) {
                        addEntry();
                        validateChecksum();
                        readNextDiffCommand();
                    } else {
                        throw new ArchiveDiffException("Unexpected command: " + command);
                    }
                } else {
                    if (command == ArchiveDiff.COMMAND_REPLACE) {
                        replaceEntry();
                    } else if (command == ArchiveDiff.COMMAND_REMOVE) {
                        // do nothing, simply proceed to next entry
                    } else if (command == ArchiveDiff.COMMAND_PATCH) {
                        patchEntry();
                    } else if (command == ArchiveDiff.COMMAND_ARCHIVE_PATCH) {
                        patchArchiveEntry();
                    } else if (command == ArchiveDiff.COMMAND_UPDATE_ATTRIBUTES) {
                        updateEntryAttributes();
                    } else {
                        throw new ArchiveDiffException("Unexpected command: " + command);
                    }

                    validateChecksum();
                    readNextDiffCommand();
                    readNextEntry();
                }
            }
        }

        archiveStreamAfter.finish();
    }

    private void readNextEntry() throws IOException {
        entry = utils.getNextEntry(archiveStreamBefore);
    }

    private void readNextDiffCommand() throws IOException {
        checkedDiffStream.getChecksum().reset();
        command = diffStream.readByte();
        if (command != 0) {
            commandPath = utils.readString(diffStream);
        } else {
            commandPath = null;
        }
    }

    private void validateChecksum() throws IOException, ArchiveDiffCorruptedException {
        long checksum = checkedDiffStream.getChecksum().getValue();
        long expectedChecksum = diffStream.readLong();
        if (checksum != expectedChecksum) {
            throw new ArchiveDiffCorruptedException("Checksum mismatch at offset " + countingDiffStream.getBytesRead());
        }
    }

    private void copyStream(InputStream input, OutputStream output, int n) throws IOException {
        byte[] buffer = new byte[4096];
        while (n > 0) {
            int bytesRead = input.read(buffer, 0, Math.min(buffer.length, n));
            if (bytesRead == -1) {
                throw new EOFException(String.format("Unexpected end of input - expected %d more bytes to read", n));
            }
            output.write(buffer, 0, bytesRead);
            n -= bytesRead;
        }
    }


    private void addEntry() throws IOException, ArchiveDiffCorruptedException {
        int dataLength = diffStream.readInt();

        GenArchiveEntry entry = utils.createNewArchiveEntry(commandPath, dataLength);

        utils.readEntryChecksum(entry, diffStream);

        entry = utils.readAttributes(entry, diffStream);

        archiveStreamAfter.putArchiveEntry(entry);
        copyStream(diffStream, archiveStreamAfter, dataLength);
        archiveStreamAfter.closeArchiveEntry();
    }

    private void copyUnchangedEntry() throws IOException {
        archiveStreamAfter.putArchiveEntry(entry);
        IOUtils.copy(archiveStreamBefore, archiveStreamAfter);
        archiveStreamAfter.closeArchiveEntry();
    }

    private void replaceEntry() throws IOException {
        int dataLength = diffStream.readInt();

        GenArchiveEntry newEntry = utils.copyArchiveEntry(entry, dataLength);

        utils.readEntryChecksum(newEntry, diffStream);

        newEntry = utils.readAttributes(newEntry, diffStream);

        archiveStreamAfter.putArchiveEntry(newEntry);
        copyStream(diffStream, archiveStreamAfter, dataLength);
        archiveStreamAfter.closeArchiveEntry();
    }

    private void patchEntry() throws IOException {
        int length = diffStream.readInt();

        GenArchiveEntry newEntry = utils.copyArchiveEntry(entry, length);

        utils.readEntryChecksum(newEntry, diffStream);

        newEntry = utils.readAttributes(newEntry, diffStream);

        ByteArrayOutputStream dataBeforeOutputStream = new ByteArrayOutputStream();
        IOUtils.copy(archiveStreamBefore, dataBeforeOutputStream);
        byte[] dataBefore = dataBeforeOutputStream.toByteArray();

        int patchLength = diffStream.readInt();
        BoundedInputStream patchInputStream = new BoundedInputStream(diffStream, patchLength);

        archiveStreamAfter.putArchiveEntry(newEntry);
        new GDiffPatcher().patch(dataBefore, patchInputStream, archiveStreamAfter);
        archiveStreamAfter.closeArchiveEntry();
    }

    private void updateEntryAttributes() throws IOException {
        int length = diffStream.readInt();

        GenArchiveEntry newEntry = utils.copyArchiveEntry(entry, length);

        newEntry = utils.readAttributes(newEntry, diffStream);

        archiveStreamAfter.putArchiveEntry(newEntry);
        IOUtils.copy(archiveStreamBefore, archiveStreamAfter);
        archiveStreamAfter.closeArchiveEntry();
    }

    private void patchArchiveEntry() throws IOException, ArchiveDiffException, ArchiveException {
        int length = diffStream.readInt();

        GenArchiveEntry newEntry = utils.copyArchiveEntry(entry, length);

        utils.readEntryChecksum(newEntry, diffStream);

        newEntry = utils.readAttributes(newEntry, diffStream);

        int patchLength = diffStream.readInt();

        archiveStreamAfter.putArchiveEntry(newEntry);

        ArchiveDiff.applyDiff(
                new BufferedInputStream(archiveStreamBefore, 64),
                new BoundedInputStream(diffStream, patchLength),
                archiveStreamAfter,
                true
        );

        archiveStreamAfter.closeArchiveEntry();
    }

}
