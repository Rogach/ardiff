package org.rogach.ardiff;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.utils.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public class ArchiveEntryWithDataStream<GenArchiveEntry extends ArchiveEntry> implements Comparable<ArchiveEntryWithDataStream<GenArchiveEntry>> {

    GenArchiveEntry entry;
    Optional<byte[]> dataOpt = Optional.empty();
    Optional<InputStream> dataStreamOpt = Optional.empty();

    public ArchiveEntryWithDataStream(GenArchiveEntry entry, byte[] data) {
        this.entry = entry;
        this.dataOpt = Optional.of(data);
    }

    public ArchiveEntryWithDataStream(GenArchiveEntry entry, InputStream dataStream) {
        this.entry = entry;
        this.dataStreamOpt = Optional.of(dataStream);
    }

    @Override
    public int compareTo(ArchiveEntryWithDataStream<GenArchiveEntry> o) {
        return this.entry.getName().compareTo(o.entry.getName());
    }

    public byte[] readData() {
        return dataOpt.orElseGet(() -> {
            try {
                return IOUtils.toByteArray(dataStreamOpt.get());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
