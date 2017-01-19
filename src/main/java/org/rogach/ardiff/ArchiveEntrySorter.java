package org.rogach.ardiff;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.rogach.ardiff.exceptions.ArchiveDiffException;
import org.rogach.ardiff.formats.ArArchiveDiff;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public interface ArchiveEntrySorter<GenArchiveEntry extends ArchiveEntry> extends ArchiveDiffBase<GenArchiveEntry> {

    default void sortArchiveEntriesImpl(InputStream input, OutputStream output) throws IOException, ArchiveException, ArchiveDiffException {
        ArchiveInputStream archiveInputStream = createArchiveInputStream(input);
        ArchiveOutputStream archiveOutputStream = createArchiveOutputStream(output);

        List<ArchiveEntryWithData<GenArchiveEntry>> entries = new ArrayList<>();
        GenArchiveEntry entry = getNextEntry(archiveInputStream);
        while (entry != null) {
            if (ArchiveDiff.isSupportedArchive(entry)) {
                ByteArrayOutputStream sortedArchiveOutputStream = new ByteArrayOutputStream();
                ArchiveDiff.sortArchiveEntries(
                        new BufferedInputStream(archiveInputStream),
                        sortedArchiveOutputStream
                );
                sortedArchiveOutputStream.close();

                if (!supportsSorting()) {
                    archiveOutputStream.putArchiveEntry(
                            getEntryForData(entry, sortedArchiveOutputStream.size(), () -> ArchiveDiffUtils.computeCRC32Checksum(sortedArchiveOutputStream.toInputStream())));

                    sortedArchiveOutputStream.writeTo(archiveOutputStream);
                    archiveOutputStream.closeArchiveEntry();
                } else {
                    entries.add(new ArchiveEntryWithData<>(entry, sortedArchiveOutputStream.toByteArray()));
                }
            } else {
                byte[] data = IOUtils.toByteArray(archiveInputStream);
                if (!supportsSorting()) {
                    archiveOutputStream.putArchiveEntry(getEntryForData(entry, data.length, () -> ArchiveDiffUtils.computeCRC32Checksum(data)));
                    archiveOutputStream.write(data);
                    archiveOutputStream.closeArchiveEntry();
                } else {
                    entries.add(new ArchiveEntryWithData<>(entry, data));
                }
            }
            entry = getNextEntry(archiveInputStream);
        }


        if (supportsSorting()) {
            Collections.sort(entries);
            for (ArchiveEntryWithData<GenArchiveEntry> archiveEntry : entries) {
                archiveOutputStream.putArchiveEntry(
                        getEntryForData(archiveEntry.entry, archiveEntry.data.length, () -> ArchiveDiffUtils.computeCRC32Checksum(archiveEntry.data)));

                archiveOutputStream.write(archiveEntry.data);
                archiveOutputStream.closeArchiveEntry();
            }
        }

        finishArchiveOutputStream(archiveOutputStream);
    }

    GenArchiveEntry getEntryForData(GenArchiveEntry entry, int dataSize, Supplier<Long> checksumSupplier) throws IOException;

}
