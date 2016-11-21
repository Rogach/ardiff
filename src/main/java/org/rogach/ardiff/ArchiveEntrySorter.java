package org.rogach.ardiff;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.rogach.ardiff.exceptions.ArchiveDiffException;
import org.rogach.ardiff.formats.ArArchiveDiff;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface ArchiveEntrySorter<GenArchiveEntry extends ArchiveEntry> extends ArchiveDiffBase<GenArchiveEntry> {

    default void sortArchiveEntriesImpl(InputStream input, OutputStream output) throws IOException, ArchiveException, ArchiveDiffException {
        ArchiveInputStream archiveInputStream = createArchiveInputStream(input);

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

                byte[] sortedArchive = sortedArchiveOutputStream.toByteArray();

                entries.add(new ArchiveEntryWithData<>(entry, sortedArchive));
            } else {
                entries.add(new ArchiveEntryWithData<>(entry, IOUtils.toByteArray(archiveInputStream)));
            }
            entry = getNextEntry(archiveInputStream);
        }

        // we must not sort AR, because dpkg explicitly specifies archive entry order
        if (!(this instanceof ArArchiveDiff)) {
            Collections.sort(entries);
        }

        ArchiveOutputStream archiveOutputStream = createArchiveOutputStream(output);

        for (ArchiveEntryWithData<GenArchiveEntry> archiveEntry : entries) {
            archiveOutputStream.putArchiveEntry(getEntryForData(archiveEntry.entry, archiveEntry.data));
            IOUtils.copy(new ByteArrayInputStream(archiveEntry.data), archiveOutputStream);
            archiveOutputStream.closeArchiveEntry();
        }

        finishArchiveOutputStream(archiveOutputStream);
    }

    GenArchiveEntry getEntryForData(GenArchiveEntry entry, byte[] data) throws IOException;

}
