package org.rogach.ardiff;

import com.nothome.delta.Delta;
import com.nothome.delta.DiffWriter;
import com.nothome.delta.GDiffWriter;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.utils.IOUtils;

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;

public abstract class ArchiveDiff<GenArchiveEntry extends ArchiveEntry> {

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

        DataOutputStream diffStream = new DataOutputStream(diff);
        diffStream.write("_ardiff_".getBytes("ASCII"));

        if (!assumeOrdering) {
            HashMap<String, ArchiveEntryWithData> entriesBefore = readAllEntries(archiveStreamBefore);
            HashMap<String, ArchiveEntryWithData> entriesAfter = readAllEntries(archiveStreamAfter);

            for (String key : entriesBefore.keySet()) {
                ArchiveEntryWithData entryBefore = entriesBefore.get(key);
                ArchiveEntryWithData entryAfter = entriesAfter.get(key);
                if (entryAfter == null) {
                    writeEntryRemoved(entryBefore.entry, diffStream);
                } else {
                    writeEntryDiff(entryBefore, entryAfter, diffStream);
                }
            }

            for (String key : entriesAfter.keySet()) {
                if (!entriesBefore.containsKey(key)) {
                    ArchiveEntryWithData entryAfter =  entriesAfter.get(key);
                    writeEntryAdded(entryAfter, diffStream);
                }
            }
        } else {
            throw new UnsupportedOperationException("Diff for sorted archives is not yet implemented");
        }

        diffStream.close();
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
        CheckedOutputStream checkedStream = new CheckedOutputStream(diffStream, new CRC32());
        DataOutputStream checkedDiffStream = new DataOutputStream(checkedStream);

        checkedDiffStream.writeByte(COMMAND_REMOVE);
        writePath(entry.getName(), checkedDiffStream);

        diffStream.writeLong(checkedStream.getChecksum().getValue());
    }

    private void writeEntryAdded(ArchiveEntryWithData entryWithData, DataOutputStream diffStream) throws IOException {
        CheckedOutputStream checkedStream = new CheckedOutputStream(diffStream, new CRC32());
        DataOutputStream checkedDiffStream = new DataOutputStream(checkedStream);

        checkedDiffStream.writeByte(COMMAND_REPLACE);
        writePath(entryWithData.entry.getName(), checkedDiffStream);

        writeAttributes(entryWithData.entry, checkedDiffStream);

        checkedDiffStream.writeInt(entryWithData.data.length);
        checkedDiffStream.write(entryWithData.data);

        diffStream.writeLong(checkedStream.getChecksum().getValue());
    }

    protected abstract void writeAttributes(GenArchiveEntry entry, DataOutputStream diffStream) throws IOException;

    private void writeEntryDiff(ArchiveEntryWithData entryBefore, ArchiveEntryWithData entryAfter, DataOutputStream diffStream) throws IOException {
        boolean attributesDifferent = areAttributesDifferent(entryBefore.entry, entryAfter.entry);
        boolean dataDifferent = !Arrays.equals(entryBefore.data, entryAfter.data);

        if (!attributesDifferent && !dataDifferent) {
            return;
        }

        CheckedOutputStream checkedStream = new CheckedOutputStream(diffStream, new CRC32());
        DataOutputStream checkedDiffStream = new DataOutputStream(checkedStream);

        if (attributesDifferent && !dataDifferent) {
            checkedDiffStream.writeByte(COMMAND_UPDATE_ATTRIBUTES);
            writePath(entryAfter.entry.getName(), checkedDiffStream);

            writeAttributesDiff(entryBefore.entry, entryAfter.entry, checkedDiffStream);

            // write zero-length data (for consistency with other commands)
            checkedDiffStream.writeInt(0);
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

                    checkedDiffStream.writeByte(COMMAND_PATCH);
                    writePath(entryAfter.entry.getName(), checkedDiffStream);

                    writeAttributesDiff(entryBefore.entry, entryAfter.entry, checkedDiffStream);

                    checkedDiffStream.writeInt(entryDiff.length);
                    checkedDiffStream.write(entryDiff);

                } else {

                    checkedDiffStream.writeByte(COMMAND_REPLACE);
                    writePath(entryAfter.entry.getName(), checkedDiffStream);

                    writeAttributesDiff(entryBefore.entry, entryAfter.entry, checkedDiffStream);

                    checkedDiffStream.writeInt(entryAfter.data.length);
                    checkedDiffStream.write(entryAfter.data);

                }
            }
        }

        diffStream.writeLong(checkedStream.getChecksum().getValue());
    }

    protected abstract boolean areAttributesDifferent(GenArchiveEntry entryBefore, GenArchiveEntry entryAfter);

    protected abstract void writeAttributesDiff(GenArchiveEntry entryBefore, GenArchiveEntry entryAfter, DataOutputStream diffStream) throws IOException;

    private void writePath(String path, DataOutputStream diffStream) throws IOException {
        byte[] bytes = path.getBytes("UTF-8");
        diffStream.writeShort(bytes.length);
        diffStream.write(bytes);
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
