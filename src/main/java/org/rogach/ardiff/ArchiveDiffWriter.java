package org.rogach.ardiff;

import com.nothome.delta.Delta;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.utils.CountingInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.rogach.ardiff.exceptions.ArchiveDiffException;
import org.rogach.ardiff.formats.ArArchiveDiff;

import java.io.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;

public interface ArchiveDiffWriter<GenArchiveEntry extends ArchiveEntry>
        extends ArchiveDiffBase<GenArchiveEntry> {

    default void computeDiffImpl(
            InputStream before,
            InputStream after,
            boolean assumeOrdering,
            OutputStream diff
    ) throws ArchiveException, ArchiveDiffException, IOException {
        ArchiveInputStream archiveStreamBefore = createArchiveInputStream(before);
        ArchiveInputStream archiveStreamAfter = createArchiveInputStream(after);

        CheckedOutputStream checkedDiffStream = new CheckedOutputStream(diff, new CRC32());
        DataOutputStream diffStream = new DataOutputStream(checkedDiffStream);
        diffStream.write(ArchiveDiff.HEADER.getBytes("ASCII"));

        boolean sortInputArchives = !(assumeOrdering || this instanceof ArArchiveDiff);
        Iterator<ArchiveEntryWithDataStream<GenArchiveEntry>> iteratorBefore = iterateAllEntries(archiveStreamBefore, sortInputArchives);
        Iterator<ArchiveEntryWithDataStream<GenArchiveEntry>> iteratorAfter = iterateAllEntries(archiveStreamAfter, sortInputArchives);

        ArchiveEntryWithDataStream<GenArchiveEntry> entryBefore = iteratorBefore.hasNext() ? iteratorBefore.next() : null;
        ArchiveEntryWithDataStream<GenArchiveEntry> entryAfter = iteratorAfter.hasNext() ? iteratorAfter.next() : null;

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

    default Iterator<ArchiveEntryWithDataStream<GenArchiveEntry>> iterateAllEntries(ArchiveInputStream archiveInputStream, boolean sort) throws IOException {
        if (sort) {
            return listAllEntries(archiveInputStream, true).iterator();
        } else {
            return iterateAllEntries(archiveInputStream);
        }
    }

    default Iterator<ArchiveEntryWithDataStream<GenArchiveEntry>> iterateAllEntries(ArchiveInputStream archiveInputStream) throws IOException {
        return new Iterator<ArchiveEntryWithDataStream<GenArchiveEntry>>() {
            boolean gotNextEntry = false;
            GenArchiveEntry entry = null;

            @Override
            public boolean hasNext() {
                if (!gotNextEntry) {
                    try {
                        entry = getNextEntry(archiveInputStream);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    gotNextEntry = true;
                }
                return entry != null;
            }

            @Override
            public ArchiveEntryWithDataStream<GenArchiveEntry> next() {
                ArchiveEntryWithDataStream<GenArchiveEntry> entryWithData = new ArchiveEntryWithDataStream<>(entry, archiveInputStream);
                gotNextEntry = false;
                return entryWithData;
            }
        };
    }

    default List<ArchiveEntryWithDataStream<GenArchiveEntry>> listAllEntries(ArchiveInputStream archiveInputStream, boolean sort) throws IOException {
        List<ArchiveEntryWithDataStream<GenArchiveEntry>> entries = new ArrayList<>();
        GenArchiveEntry entry = getNextEntry(archiveInputStream);
        while (entry != null) {
            entries.add(new ArchiveEntryWithDataStream<>(entry, IOUtils.toByteArray(archiveInputStream)));
            entry = getNextEntry(archiveInputStream);
        }

        if (sort) {
            Collections.sort(entries);
        }

        return entries;
    }

    default void writeEntryRemoved(GenArchiveEntry entry, DataOutputStream diffStream) throws IOException {
        diffStream.writeByte(ArchiveDiff.COMMAND_REMOVE);
        writeString(entry.getName(), diffStream);
    }

    default void writeEntryAdded(ArchiveEntryWithDataStream<GenArchiveEntry> entryWithData, DataOutputStream diffStream) throws IOException {
        diffStream.writeByte(ArchiveDiff.COMMAND_ADD);
        writeString(entryWithData.entry.getName(), diffStream);

        byte[] data;

        if (entryWithData.dataOpt.isPresent()) {
            data = entryWithData.dataOpt.get();
        } else {
            ByteArrayOutputStream dataOutputStream = new ByteArrayOutputStream();
            IOUtils.copy(entryWithData.dataStreamOpt.get(), dataOutputStream);
            data = dataOutputStream.toByteArray();
        }

        diffStream.writeInt(data.length);

        writeEntryChecksum(() -> ArchiveDiffUtils.computeCRC32Checksum(data), diffStream);

        writeAttributes(entryWithData.entry, diffStream);

        diffStream.write(data);
    }

    void writeAttributes(GenArchiveEntry entry, DataOutputStream diffStream) throws IOException;

    default void writeEntryChecksum(Supplier<Long> checksumSupplier, DataOutputStream diffStream) throws IOException {}

    default boolean writeEntryDiff(
            ArchiveEntryWithDataStream<GenArchiveEntry> entryBefore,
            ArchiveEntryWithDataStream<GenArchiveEntry> entryAfter,
            DataOutputStream diffStream,
            boolean assumeOrdering
    ) throws IOException, ArchiveDiffException, ArchiveException {

        if (ArchiveDiff.isSupportedArchive(entryAfter.entry)) {
            diffStream.writeByte(ArchiveDiff.COMMAND_ARCHIVE_PATCH);
            writeString(entryAfter.entry.getName(), diffStream);

            if (assumeOrdering) {
                CountingInputStream dataAfterCountingStream = new CountingInputStream(entryAfter.dataStreamOpt.get());
                CheckedInputStream dataAfterCheckedStream = new CheckedInputStream(dataAfterCountingStream, new CRC32());

                ByteArrayOutputStream diffByteArrayOutputStream = new ByteArrayOutputStream();
                ArchiveDiff.computeDiff(
                        new BufferedInputStream(entryBefore.dataStreamOpt.get(), 4096),
                        new BufferedInputStream(dataAfterCheckedStream, 4096),
                        diffByteArrayOutputStream,
                        true
                );
                diffByteArrayOutputStream.close();

                byte[] nestedArchiveDiff = diffByteArrayOutputStream.toByteArray();

                diffStream.writeInt((int) dataAfterCountingStream.getBytesRead());

                writeEntryChecksum(() -> dataAfterCheckedStream.getChecksum().getValue(), diffStream);

                writeAttributesDiff(entryBefore.entry, entryAfter.entry, diffStream);

                diffStream.writeInt(nestedArchiveDiff.length);
                diffStream.write(nestedArchiveDiff);

            } else {

                byte[] dataBefore = entryBefore.readData();
                byte[] dataAfter = entryAfter.readData();

                ByteArrayOutputStream diffByteArrayOutputStream = new ByteArrayOutputStream();
                ArchiveDiff.computeDiff(
                        new ByteArrayInputStream(dataBefore),
                        new ByteArrayInputStream(dataAfter),
                        diffByteArrayOutputStream
                );
                diffByteArrayOutputStream.close();

                byte[] nestedArchiveDiff = diffByteArrayOutputStream.toByteArray();

                ByteArrayOutputStream recompressByteArrayOutputStream = new ByteArrayOutputStream();
                ArchiveDiff.applyDiff(
                        new ByteArrayInputStream(dataBefore),
                        new ByteArrayInputStream(nestedArchiveDiff),
                        recompressByteArrayOutputStream
                );
                recompressByteArrayOutputStream.close();
                byte[] recompressedData = recompressByteArrayOutputStream.toByteArray();

                diffStream.writeInt(recompressedData.length);

                writeEntryChecksum(() -> ArchiveDiffUtils.computeCRC32Checksum(recompressedData), diffStream);

                writeAttributesDiff(entryBefore.entry, entryAfter.entry, diffStream);

                diffStream.writeInt(nestedArchiveDiff.length);
                diffStream.write(nestedArchiveDiff);
            }
        } else {

            byte[] dataBefore = entryBefore.readData();
            byte[] dataAfter = entryAfter.readData();

            boolean attributesDifferent = !attributesEqual(entryBefore.entry, entryAfter.entry);
            boolean dataDifferent = !Arrays.equals(dataBefore, dataAfter);

            if (!attributesDifferent && !dataDifferent) {
                return false;
            }

            if (attributesDifferent && !dataDifferent) {
                diffStream.writeByte(ArchiveDiff.COMMAND_UPDATE_ATTRIBUTES);
                writeString(entryAfter.entry.getName(), diffStream);

                diffStream.writeInt(dataAfter.length);

                writeAttributesDiff(entryBefore.entry, entryAfter.entry, diffStream);
            } else {

                ByteArrayOutputStream entryDiffByteArrayOutputStream = new ByteArrayOutputStream();
                new Delta().compute(dataBefore, dataAfter, entryDiffByteArrayOutputStream);
                byte[] entryDiff = entryDiffByteArrayOutputStream.toByteArray();

                // if diff is too large, we can simply send the whole file
                // even if diff is slightly smaller, we should send the whole file to
                // to avoid extra memory & cpu cost on decoding side
                // (we assume decoding machines to be weaker)
                if (entryDiff.length < dataAfter.length * 0.7) {

                    diffStream.writeByte(ArchiveDiff.COMMAND_PATCH);
                    writeString(entryAfter.entry.getName(), diffStream);

                    diffStream.writeInt(dataAfter.length);

                    writeEntryChecksum(() -> ArchiveDiffUtils.computeCRC32Checksum(dataAfter), diffStream);

                    writeAttributesDiff(entryBefore.entry, entryAfter.entry, diffStream);

                    diffStream.writeInt(entryDiff.length);
                    diffStream.write(entryDiff);

                } else {

                    diffStream.writeByte(ArchiveDiff.COMMAND_REPLACE);
                    writeString(entryAfter.entry.getName(), diffStream);

                    diffStream.writeInt(dataAfter.length);

                    writeEntryChecksum(() -> ArchiveDiffUtils.computeCRC32Checksum(dataAfter), diffStream);

                    writeAttributesDiff(entryBefore.entry, entryAfter.entry, diffStream);

                    diffStream.write(dataAfter);

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
