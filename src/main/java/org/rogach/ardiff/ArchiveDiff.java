package org.rogach.ardiff;

import com.nothome.delta.Delta;
import com.nothome.delta.GDiffPatcher;
import org.apache.commons.compress.archivers.*;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.utils.IOUtils;
import org.rogach.ardiff.exceptions.ArchiveDiffCorruptedException;
import org.rogach.ardiff.exceptions.ArchiveDiffFormatException;

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;

public abstract class ArchiveDiff<GenArchiveEntry extends ArchiveEntry> {

    static final String HEADER = "_ardiff_";

    static final byte COMMAND_REPLACE = 1;
    static final byte COMMAND_REMOVE = 2;
    static final byte COMMAND_PATCH = 3;
    static final byte COMMAND_ARCHIVE_PATCH = 4;
    static final byte COMMAND_UPDATE_ATTRIBUTES = 5;

    public static void computeDiff(
            InputStream before,
            InputStream after,
            String archiveType,
            boolean assumeOrdering,
            OutputStream diff
    ) throws ArchiveException, IOException {
        if ("zip".equals(archiveType)) {
            new ZipArchiveDiff().computeDiff(before, after, assumeOrdering, diff);
        } else {
            throw new RuntimeException("Unsupported archive type: " + archiveType);
        }
    }

    abstract String archiverName();

    public void computeDiff(
            InputStream before,
            InputStream after,
            boolean assumeOrdering,
            OutputStream diff
    ) throws ArchiveException, IOException {
        ArchiveInputStream archiveStreamBefore = new ArchiveStreamFactory().createArchiveInputStream(archiverName(), before);
        ArchiveInputStream archiveStreamAfter = new ArchiveStreamFactory().createArchiveInputStream(archiverName(), after);

        CheckedOutputStream checkedDiffStream = new CheckedOutputStream(diff, new CRC32());
        DataOutputStream diffStream = new DataOutputStream(checkedDiffStream);
        diffStream.write(HEADER.getBytes("ASCII"));

        if (!assumeOrdering) {
            HashMap<String, ArchiveEntryWithData> entriesBefore = readAllEntries(archiveStreamBefore);
            HashMap<String, ArchiveEntryWithData> entriesAfter = readAllEntries(archiveStreamAfter);


            for (String path : entriesBefore.keySet()) {
                ArchiveEntryWithData entryBefore = entriesBefore.get(path);
                ArchiveEntryWithData entryAfter = entriesAfter.get(path);

                checkedDiffStream.getChecksum().reset();

                if (entryAfter == null) {
                    writeEntryRemoved(entryBefore.entry, diffStream);
                } else {
                    boolean entryWritten = writeEntryDiff(entryBefore, entryAfter, diffStream);
                    if (!entryWritten) continue;
                }

                diffStream.writeLong(checkedDiffStream.getChecksum().getValue());
            }

            for (String path : entriesAfter.keySet()) {
                if (!entriesBefore.containsKey(path)) {
                    ArchiveEntryWithData entryAfter = entriesAfter.get(path);

                    checkedDiffStream.getChecksum().reset();

                    writeEntryAdded(entryAfter, diffStream);

                    diffStream.writeLong(checkedDiffStream.getChecksum().getValue());
                }
            }
        } else {
            throw new UnsupportedOperationException("Diff for sorted archives is not yet implemented");
        }

        diffStream.close();
    }

    public void applyDiff(
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

        if (!Arrays.equals(header, HEADER.getBytes("ASCII"))) {
            throw new ArchiveDiffFormatException("Invalid diff stream header");
        }

        if (!assumeOrdering) {
            HashMap<String, ArchiveEntryWithData> entries = readAllEntries(archiveStreamBefore);

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
                ArchiveEntryWithData entryBefore = entries.get(path);

                if (command == COMMAND_REPLACE) {
                    entries.put(path, readEntryReplace(path, diffStream));
                } else if (command == COMMAND_REMOVE) {
                    entries.remove(path);
                } else if (command == COMMAND_PATCH) {
                    entries.put(path, readEntryPatch(entryBefore, diffStream));
                } else if (command == COMMAND_ARCHIVE_PATCH) {
                    throw new UnsupportedOperationException("Patch for recursive archives is not yet implemented");
                } else if (command == COMMAND_UPDATE_ATTRIBUTES) {
                    entries.put(path, readEntryUpdateAttributes(entryBefore, diffStream));
                }

                long checksum = checkedDiffStream.getChecksum().getValue();
                long expectedChecksum = diffStream.readLong();
                if (checksum != expectedChecksum) {
                    throw new ArchiveDiffCorruptedException("Checksum mismatch at offset " + diffStream.read());
                }
            } while (true);

            for (ArchiveEntryWithData entry : entries.values()) {
                archiveStreamAfter.putArchiveEntry(entry.entry);
                IOUtils.copy(new ByteArrayInputStream(entry.data), archiveStreamAfter);
                archiveStreamAfter.closeArchiveEntry();
            }
        } else {
            throw new UnsupportedOperationException("Diff for sorted archives is not yet implemented");
        }

        archiveStreamAfter.close();
    }

    @SuppressWarnings("unchecked")
    private HashMap<String, ArchiveEntryWithData> readAllEntries(ArchiveInputStream archiveInputStream) throws IOException {
        HashMap<String, ArchiveEntryWithData> entries = new HashMap<>();
        GenArchiveEntry entry = (GenArchiveEntry) archiveInputStream.getNextEntry();
        while (entry != null) {
            entries.put(entry.getName(), new ArchiveEntryWithData(entry, IOUtils.toByteArray(archiveInputStream)));
            entry = (GenArchiveEntry) archiveInputStream.getNextEntry();
        }
        return entries;
    }


    private void writeEntryRemoved(GenArchiveEntry entry, DataOutputStream diffStream) throws IOException {
        diffStream.writeByte(COMMAND_REMOVE);
        writePath(entry.getName(), diffStream);
    }

    private void writeEntryAdded(ArchiveEntryWithData entryWithData, DataOutputStream diffStream) throws IOException {
        diffStream.writeByte(COMMAND_REPLACE);
        writePath(entryWithData.entry.getName(), diffStream);

        writeAttributes(entryWithData.entry, diffStream);

        diffStream.writeInt(entryWithData.data.length);
        diffStream.write(entryWithData.data);
    }

    protected abstract void writeAttributes(GenArchiveEntry entry, DataOutputStream diffStream) throws IOException;

    private boolean writeEntryDiff(ArchiveEntryWithData entryBefore, ArchiveEntryWithData entryAfter, DataOutputStream diffStream) throws IOException {
        boolean attributesDifferent = areAttributesDifferent(entryBefore.entry, entryAfter.entry);
        boolean dataDifferent = !Arrays.equals(entryBefore.data, entryAfter.data);

        if (!attributesDifferent && !dataDifferent) {
            return false;
        }

        if (attributesDifferent && !dataDifferent) {
            diffStream.writeByte(COMMAND_UPDATE_ATTRIBUTES);
            writePath(entryAfter.entry.getName(), diffStream);

            writeAttributesDiff(entryBefore.entry, entryAfter.entry, diffStream);
        } else {
            if (isSupportedArchive(entryAfter.entry)) {
                throw new UnsupportedOperationException("Diff for recursive archives is not yet implemented");
            } else {
                ByteArrayOutputStream entryDiffByteArrayOutputStream = new ByteArrayOutputStream();
                new Delta().compute(entryBefore.data, entryAfter.data, entryDiffByteArrayOutputStream);
                byte[] entryDiff = entryDiffByteArrayOutputStream.toByteArray();

                // if diff is too large, we can simply send the whole file
                // even if diff is slightly smaller, we should send the whole file to
                // to avoid extra memory & cpu cost on decoding side
                // (we assume decoding machines to be weaker)
                if (entryDiff.length < entryAfter.data.length * 0.7) {

                    diffStream.writeByte(COMMAND_PATCH);
                    writePath(entryAfter.entry.getName(), diffStream);

                    writeAttributesDiff(entryBefore.entry, entryAfter.entry, diffStream);

                    diffStream.writeInt(entryDiff.length);
                    diffStream.write(entryDiff);

                } else {

                    diffStream.writeByte(COMMAND_REPLACE);
                    writePath(entryAfter.entry.getName(), diffStream);

                    writeAttributesDiff(entryBefore.entry, entryAfter.entry, diffStream);

                    diffStream.writeInt(entryAfter.data.length);
                    diffStream.write(entryAfter.data);

                }
            }
        }

        return true;
    }

    protected abstract boolean areAttributesDifferent(GenArchiveEntry entryBefore, GenArchiveEntry entryAfter);

    protected abstract void writeAttributesDiff(GenArchiveEntry entryBefore, GenArchiveEntry entryAfter, DataOutputStream diffStream) throws IOException;


    protected abstract GenArchiveEntry createNewArchiveEntry(String path);

    protected abstract GenArchiveEntry copyArchiveEntry(GenArchiveEntry orig) throws IOException;

    private ArchiveEntryWithData readEntryReplace(String path, DataInputStream diffStream) throws IOException {
        GenArchiveEntry entry = createNewArchiveEntry(path);

        readAttributes(entry, diffStream);

        int dataLength = diffStream.readInt();
        byte[] data = new byte[dataLength];
        diffStream.readFully(data);

        return new ArchiveEntryWithData(entry, data);
    }

    private ArchiveEntryWithData readEntryPatch(ArchiveEntryWithData entryBefore, DataInputStream diffStream) throws IOException {
        GenArchiveEntry entryAfter = copyArchiveEntry(entryBefore.entry);

        readAttributes(entryAfter, diffStream);

        int patchLength = diffStream.readInt();
        byte[] patch = new byte[patchLength];
        diffStream.readFully(patch);

        byte[] dataAfter = new GDiffPatcher().patch(entryBefore.data, patch);

        return new ArchiveEntryWithData(entryAfter, dataAfter);
    }

    private ArchiveEntryWithData readEntryUpdateAttributes(ArchiveEntryWithData entryBefore, DataInputStream diffStream) throws IOException {
        GenArchiveEntry entryAfter = copyArchiveEntry(entryBefore.entry);

        readAttributes(entryAfter, diffStream);

        return new ArchiveEntryWithData(entryAfter, entryBefore.data);
    }

    protected abstract void readAttributes(GenArchiveEntry entry, DataInputStream diffStream) throws IOException;


    private void writePath(String path, DataOutputStream diffStream) throws IOException {
        byte[] bytes = path.getBytes("UTF-8");
        diffStream.writeShort(bytes.length);
        diffStream.write(bytes);
    }

    private String readPath(DataInputStream diffStream) throws IOException {
        short length = diffStream.readShort();
        byte[] bytes = new byte[length];
        diffStream.readFully(bytes);
        return new String(bytes, "UTF-8");
    }

    private boolean isSupportedArchive(ArchiveEntry entry) {
        return getArchiverType(entry) != null;
    }

    private String getArchiverType(ArchiveEntry entry) {
        if (entry.getName().endsWith(".zip")) {
            return "zip";
        } else {
            return null;
        }
    }

    protected class ArchiveEntryWithData {
        GenArchiveEntry entry;
        byte[] data;

        public ArchiveEntryWithData(GenArchiveEntry entry, byte[] data) {
            this.entry = entry;
            this.data = data;
        }
    }

}
