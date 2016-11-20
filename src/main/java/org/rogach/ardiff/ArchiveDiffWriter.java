package org.rogach.ardiff;

import com.nothome.delta.Delta;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.rogach.ardiff.exceptions.ArchiveDiffException;

import java.io.*;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;

public interface ArchiveDiffWriter<GenArchiveEntry extends ArchiveEntry>
        extends ArchiveDiffBase<GenArchiveEntry> {

    default void computeDiffImpl(
            InputStream before,
            InputStream after,
            boolean assumeOrdering,
            OutputStream diff
    ) throws ArchiveException, ArchiveDiffException, IOException {
        ArchiveInputStream archiveStreamBefore = new ArchiveStreamFactory().createArchiveInputStream(archiverName(), before);
        ArchiveInputStream archiveStreamAfter = new ArchiveStreamFactory().createArchiveInputStream(archiverName(), after);

        CheckedOutputStream checkedDiffStream = new CheckedOutputStream(diff, new CRC32());
        DataOutputStream diffStream = new DataOutputStream(checkedDiffStream);
        diffStream.write(ArchiveDiff.HEADER.getBytes("ASCII"));

        List<ArchiveEntryWithData<GenArchiveEntry>> entriesBefore = listAllEntries(archiveStreamBefore);
        List<ArchiveEntryWithData<GenArchiveEntry>> entriesAfter = listAllEntries(archiveStreamAfter);

        Iterator<ArchiveEntryWithData<GenArchiveEntry>> iteratorBefore = entriesBefore.iterator();
        Iterator<ArchiveEntryWithData<GenArchiveEntry>> iteratorAfter = entriesAfter.iterator();

        ArchiveEntryWithData<GenArchiveEntry> entryBefore = iteratorBefore.hasNext() ? iteratorBefore.next() : null;
        ArchiveEntryWithData<GenArchiveEntry> entryAfter = iteratorAfter.hasNext() ? iteratorAfter.next() : null;

        while (entryBefore != null || entryAfter != null) {
            if (entryBefore == null) {
                checkedDiffStream.getChecksum().reset();
                writeEntryAdded(entryAfter, diffStream);
                diffStream.writeLong(checkedDiffStream.getChecksum().getValue());
                entryAfter = iteratorAfter.hasNext() ? iteratorAfter.next() : null;
            } else if (entryAfter == null) {
                checkedDiffStream.getChecksum().reset();
                writeEntryRemoved(entryBefore.entry, diffStream);
                diffStream.writeLong(checkedDiffStream.getChecksum().getValue());
                entryBefore = iteratorBefore.hasNext() ? iteratorBefore.next() : null;
            } else {
                int entryOrder = entryBefore.entry.getName().compareTo(entryAfter.entry.getName());
                if (entryOrder < 0) {
                    checkedDiffStream.getChecksum().reset();
                    writeEntryRemoved(entryBefore.entry, diffStream);
                    diffStream.writeLong(checkedDiffStream.getChecksum().getValue());
                    entryBefore = iteratorBefore.hasNext() ? iteratorBefore.next() : null;
                } else if (entryOrder > 0) {
                    checkedDiffStream.getChecksum().reset();
                    writeEntryAdded(entryAfter, diffStream);
                    diffStream.writeLong(checkedDiffStream.getChecksum().getValue());
                    entryAfter = iteratorAfter.hasNext() ? iteratorAfter.next() : null;
                } else {
                    checkedDiffStream.getChecksum().reset();
                    boolean entryWritten = writeEntryDiff(entryBefore, entryAfter, diffStream, assumeOrdering);
                    if (entryWritten) {
                        diffStream.writeLong(checkedDiffStream.getChecksum().getValue());
                    }
                    entryBefore = iteratorBefore.hasNext() ? iteratorBefore.next() : null;
                    entryAfter = iteratorAfter.hasNext() ? iteratorAfter.next() : null;
                }
            }
        }

        diffStream.writeByte(0);
    }


    default void writeEntryRemoved(GenArchiveEntry entry, DataOutputStream diffStream) throws IOException {
        diffStream.writeByte(ArchiveDiff.COMMAND_REMOVE);
        writeString(entry.getName(), diffStream);
    }

    default void writeEntryAdded(ArchiveEntryWithData<GenArchiveEntry> entryWithData, DataOutputStream diffStream) throws IOException {
        diffStream.writeByte(ArchiveDiff.COMMAND_ADD);
        writeString(entryWithData.entry.getName(), diffStream);

        diffStream.writeInt(entryWithData.data.length);

        writeEntryChecksum(entryWithData.data, diffStream);

        writeAttributes(entryWithData.entry, diffStream);

        diffStream.writeInt(entryWithData.data.length);
        diffStream.write(entryWithData.data);
    }

    void writeAttributes(GenArchiveEntry entry, DataOutputStream diffStream) throws IOException;

    default void writeEntryChecksum(byte[] data, DataOutputStream diffStream) throws IOException {}

    default boolean writeEntryDiff(
            ArchiveEntryWithData<GenArchiveEntry> entryBefore,
            ArchiveEntryWithData<GenArchiveEntry> entryAfter,
            DataOutputStream diffStream,
            boolean assumeOrdering
    ) throws IOException, ArchiveDiffException, ArchiveException {

        boolean attributesDifferent = !attributesEqual(entryBefore.entry, entryAfter.entry);
        boolean dataDifferent = !Arrays.equals(entryBefore.data, entryAfter.data);

        if (!attributesDifferent && !dataDifferent) {
            return false;
        }

        if (attributesDifferent && !dataDifferent) {
            diffStream.writeByte(ArchiveDiff.COMMAND_UPDATE_ATTRIBUTES);
            writeString(entryAfter.entry.getName(), diffStream);

            diffStream.writeInt(entryAfter.data.length);

            writeAttributesDiff(entryBefore.entry, entryAfter.entry, diffStream);
        } else {
            if (ArchiveDiff.isSupportedArchive(entryAfter.entry)) {
                diffStream.writeByte(ArchiveDiff.COMMAND_ARCHIVE_PATCH);
                writeString(entryAfter.entry.getName(), diffStream);

                byte[] beforeData = entryBefore.data;

                if (assumeOrdering) {
                    ByteArrayOutputStream sortedBeforeOutputStream = new ByteArrayOutputStream();
                    ArchiveDiff.sortArchiveEntries(
                            new ByteArrayInputStream(entryBefore.data),
                            sortedBeforeOutputStream
                    );
                    sortedBeforeOutputStream.close();
                    beforeData = sortedBeforeOutputStream.toByteArray();
                }

                ByteArrayOutputStream diffByteArrayOutputStream = new ByteArrayOutputStream();
                ArchiveDiff.computeDiff(
                        new ByteArrayInputStream(beforeData),
                        new ByteArrayInputStream(entryAfter.data),
                        diffByteArrayOutputStream
                );
                diffByteArrayOutputStream.close();

                byte[] nestedArchiveDiff = diffByteArrayOutputStream.toByteArray();

                ByteArrayOutputStream recompressByteArrayOutputStream = new ByteArrayOutputStream();
                ArchiveDiff.applyDiff(
                        new ByteArrayInputStream(beforeData),
                        new ByteArrayInputStream(nestedArchiveDiff),
                        recompressByteArrayOutputStream,
                        assumeOrdering
                );
                recompressByteArrayOutputStream.close();

                byte[] recompress = recompressByteArrayOutputStream.toByteArray();

                diffStream.writeInt(recompress.length);

                writeEntryChecksum(recompress, diffStream);

                writeAttributesDiff(entryBefore.entry, entryAfter.entry, diffStream);

                diffStream.writeInt(nestedArchiveDiff.length);
                diffStream.write(nestedArchiveDiff);

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
                    writeString(entryAfter.entry.getName(), diffStream);

                    diffStream.writeInt(entryAfter.data.length);

                    writeEntryChecksum(entryAfter.data, diffStream);

                    writeAttributesDiff(entryBefore.entry, entryAfter.entry, diffStream);

                    diffStream.writeInt(entryDiff.length);
                    diffStream.write(entryDiff);

                } else {

                    diffStream.writeByte(ArchiveDiff.COMMAND_REPLACE);
                    writeString(entryAfter.entry.getName(), diffStream);

                    diffStream.writeInt(entryAfter.data.length);

                    writeEntryChecksum(entryAfter.data, diffStream);

                    writeAttributesDiff(entryBefore.entry, entryAfter.entry, diffStream);

                    diffStream.writeInt(entryAfter.data.length);
                    diffStream.write(entryAfter.data);

                }
            }
        }

        return true;
    }

    void writeAttributesDiff(GenArchiveEntry entryBefore, GenArchiveEntry entryAfter, DataOutputStream diffStream) throws IOException;

    default void writeString(String s, DataOutputStream diffStream) throws IOException {
        if (s != null) {
            writeBytes(s.getBytes("UTF-8"), diffStream);
        } else {
            diffStream.writeShort(0);
        }
    }

    default void writeBytes(byte[] bytes, DataOutputStream diffStream) throws IOException {
        if (bytes != null) {
            diffStream.writeShort(bytes.length);
            diffStream.write(bytes);
        } else {
            diffStream.writeShort(0);
        }
    }

    default void writeAttribute(byte code, int value, DataOutputStream diffStream) throws IOException {
        diffStream.writeByte(code);
        diffStream.writeInt(value);
    }

    default void writeAttribute(byte code, long value, DataOutputStream diffStream) throws IOException {
        diffStream.writeByte(code);
        diffStream.writeLong(value);
    }

    default void writeAttribute(byte code, String value, DataOutputStream diffStream) throws IOException {
        if (value != null) {
            diffStream.writeByte(code);
            writeString(value, diffStream);
        }
    }

    default void writeAttribute(byte code, byte[] value, DataOutputStream diffStream) throws IOException {
        if (value != null) {
            diffStream.writeByte(code);
            writeBytes(value, diffStream);
        }
    }

    default void diffAttributes(byte code, byte[] valueBefore, byte[] valueAfter, DataOutputStream diffStream) throws IOException {
        if (!Arrays.equals(valueBefore, valueAfter)) {
            writeAttribute(code, valueAfter, diffStream);
        }
    }

    default void diffAttributes(byte code, String valueBefore, String valueAfter, DataOutputStream diffStream) throws IOException {
        if (!Objects.equals(valueBefore, valueAfter)) {
            writeAttribute(code, valueAfter, diffStream);
        }
    }

    default void diffAttributes(byte code, int valueBefore, int valueAfter, DataOutputStream diffStream) throws IOException {
        if (valueBefore != valueAfter) {
            writeAttribute(code, valueAfter, diffStream);
        }
    }

    default void diffAttributes(byte code, long valueBefore, long valueAfter, DataOutputStream diffStream) throws IOException {
        if (valueBefore != valueAfter) {
            writeAttribute(code, valueAfter, diffStream);
        }
    }
}
