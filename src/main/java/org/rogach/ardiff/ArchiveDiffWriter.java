package org.rogach.ardiff;

import com.nothome.delta.Delta;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;

public interface ArchiveDiffWriter<GenArchiveEntry extends ArchiveEntry>
        extends ArchiveDiffBase<GenArchiveEntry> {

    default void computeDiff(
            InputStream before,
            InputStream after,
            boolean assumeOrdering,
            OutputStream diff
    ) throws ArchiveException, IOException {
        ArchiveInputStream archiveStreamBefore = new ArchiveStreamFactory().createArchiveInputStream(archiverName(), before);
        ArchiveInputStream archiveStreamAfter = new ArchiveStreamFactory().createArchiveInputStream(archiverName(), after);

        CheckedOutputStream checkedDiffStream = new CheckedOutputStream(diff, new CRC32());
        DataOutputStream diffStream = new DataOutputStream(checkedDiffStream);
        diffStream.write(ArchiveDiff.HEADER.getBytes("ASCII"));

        if (!assumeOrdering) {
            HashMap<String, ArchiveEntryWithData<GenArchiveEntry>> entriesBefore = readAllEntries(archiveStreamBefore);
            HashMap<String, ArchiveEntryWithData<GenArchiveEntry>> entriesAfter = readAllEntries(archiveStreamAfter);


            for (String path : entriesBefore.keySet()) {
                ArchiveEntryWithData<GenArchiveEntry> entryBefore = entriesBefore.get(path);
                ArchiveEntryWithData<GenArchiveEntry> entryAfter = entriesAfter.get(path);

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
                    ArchiveEntryWithData<GenArchiveEntry> entryAfter = entriesAfter.get(path);

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


    default void writeEntryRemoved(GenArchiveEntry entry, DataOutputStream diffStream) throws IOException {
        diffStream.writeByte(ArchiveDiff.COMMAND_REMOVE);
        writePath(entry.getName(), diffStream);
    }

    default void writeEntryAdded(ArchiveEntryWithData<GenArchiveEntry> entryWithData, DataOutputStream diffStream) throws IOException {
        diffStream.writeByte(ArchiveDiff.COMMAND_ADD);
        writePath(entryWithData.entry.getName(), diffStream);

        writeAttributes(entryWithData.entry, diffStream);

        diffStream.writeInt(entryWithData.data.length);
        diffStream.write(entryWithData.data);
    }

    void writeAttributes(GenArchiveEntry entry, DataOutputStream diffStream) throws IOException;

    default boolean writeEntryDiff(ArchiveEntryWithData<GenArchiveEntry> entryBefore, ArchiveEntryWithData<GenArchiveEntry> entryAfter, DataOutputStream diffStream) throws IOException {
        boolean attributesDifferent = !attributesEqual(entryBefore.entry, entryAfter.entry);
        boolean dataDifferent = !Arrays.equals(entryBefore.data, entryAfter.data);

        if (!attributesDifferent && !dataDifferent) {
            return false;
        }

        if (attributesDifferent && !dataDifferent) {
            diffStream.writeByte(ArchiveDiff.COMMAND_UPDATE_ATTRIBUTES);
            writePath(entryAfter.entry.getName(), diffStream);

            writeAttributesDiff(entryBefore.entry, entryAfter.entry, diffStream);
        } else {
            if (ArchiveDiff.isSupportedArchive(entryAfter.entry)) {
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

                    diffStream.writeByte(ArchiveDiff.COMMAND_PATCH);
                    writePath(entryAfter.entry.getName(), diffStream);

                    writeAttributesDiff(entryBefore.entry, entryAfter.entry, diffStream);

                    diffStream.writeInt(entryDiff.length);
                    diffStream.write(entryDiff);

                } else {

                    diffStream.writeByte(ArchiveDiff.COMMAND_REPLACE);
                    writePath(entryAfter.entry.getName(), diffStream);

                    writeAttributesDiff(entryBefore.entry, entryAfter.entry, diffStream);

                    diffStream.writeInt(entryAfter.data.length);
                    diffStream.write(entryAfter.data);

                }
            }
        }

        return true;
    }

    void writeAttributesDiff(GenArchiveEntry entryBefore, GenArchiveEntry entryAfter, DataOutputStream diffStream) throws IOException;

    default void writePath(String path, DataOutputStream diffStream) throws IOException {
        byte[] bytes = path.getBytes("UTF-8");
        diffStream.writeShort(bytes.length);
        diffStream.write(bytes);
    }

}
