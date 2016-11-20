package org.rogach.ardiff;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.rogach.ardiff.exceptions.ArchiveDiffException;
import org.rogach.ardiff.formats.ArArchiveDiff;
import org.rogach.ardiff.formats.TarArchiveDiff;
import org.rogach.ardiff.formats.ZipArchiveDiff;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

public abstract class ArchiveDiff<GenArchiveEntry extends ArchiveEntry>
        implements ArchiveDiffWriter<GenArchiveEntry>, ArchiveDiffReader<GenArchiveEntry>, ArchiveComparator<GenArchiveEntry>, ArchiveEntrySorter<GenArchiveEntry> {

    static final String HEADER = "_ardiff_";

    static final byte COMMAND_ADD = 1;
    static final byte COMMAND_REPLACE = 2;
    static final byte COMMAND_REMOVE = 3;
    static final byte COMMAND_PATCH = 4;
    static final byte COMMAND_ARCHIVE_PATCH = 5;
    static final byte COMMAND_UPDATE_ATTRIBUTES = 6;

    public static void computeDiff(
            InputStream before,
            InputStream after,
            OutputStream diff
    ) throws ArchiveDiffException, ArchiveException, IOException {
        computeDiff(before, after, false, diff);
    }

    public static void computeDiff(
            InputStream before,
            InputStream after,
            boolean assumeOrdering,
            OutputStream diff
    ) throws ArchiveDiffException, ArchiveException, IOException {
        String beforeArchiveType = detectArchiveType(before);
        String afterArchiveType = detectArchiveType(after);

        if (!Objects.equals(beforeArchiveType, afterArchiveType)) {
            throw new ArchiveDiffException(String.format("Unable to compute diff for different archive types: before=%s, after=%s", beforeArchiveType, afterArchiveType));
        }

        computeDiff(before, after, beforeArchiveType, assumeOrdering, diff);
    }

    public static void computeDiff(
            InputStream before,
            InputStream after,
            String archiveType,
            boolean assumeOrdering,
            OutputStream diff
    ) throws ArchiveException, ArchiveDiffException, IOException {
        getInstance(archiveType).computeDiffImpl(before, after, assumeOrdering, diff);
    }

    public static void applyDiff(
            InputStream before,
            InputStream diff,
            OutputStream after
    ) throws ArchiveException, IOException, ArchiveDiffException {
        applyDiff(before, diff, after, false);
    }

    public static void applyDiff(
            InputStream before,
            InputStream diff,
            OutputStream after,
            boolean assumeOrdering
    ) throws ArchiveException, IOException, ArchiveDiffException {
        String archiveType = detectArchiveType(before);
        applyDiff(before, diff, archiveType, assumeOrdering, after);
    }

    @SuppressWarnings("unchecked")
    public static void applyDiff(
            InputStream before,
            InputStream diff,
            String archiveType,
            boolean assumeOrdering,
            OutputStream after
    ) throws ArchiveException, IOException, ArchiveDiffException {
        if (assumeOrdering) {
            new StreamingArchiveDiffReader(before, diff, after, getInstance(archiveType)).streamingApplyDiff();
        } else {
            getInstance(archiveType).applyDiffImpl(before, diff, after);
        }
    }

    public static boolean archivesAreEqual(InputStream before, InputStream after) throws ArchiveDiffException, ArchiveException, IOException {
        String beforeArchiveType = detectArchiveType(before);
        String afterArchiveType = detectArchiveType(after);

        if (!Objects.equals(beforeArchiveType, afterArchiveType)) {
            throw new ArchiveDiffException(String.format("Unable to compute diff for different archive types: before=%s, after=%s", beforeArchiveType, afterArchiveType));
        }
        return comparatorForArchiveType(beforeArchiveType).archivesEqual(before, after);
    }

    @SuppressWarnings("unchecked")
    public static ArchiveDiff getInstance(String archiveType) {
        if (ArchiveStreamFactory.ZIP.equals(archiveType)) {
            return new ZipArchiveDiff();
        } else if (ArchiveStreamFactory.TAR.equals(archiveType)) {
            return new TarArchiveDiff();
        } else if (ArchiveStreamFactory.AR.equals(archiveType)) {
            return new ArArchiveDiff();
        } else {
            throw new RuntimeException("Unsupported archive type: " + archiveType);
        }
    }

    public static ArchiveComparator comparatorForArchiveType(String archiveType) {
        return getInstance(archiveType);
    }

    public static void sortArchiveEntries(InputStream input, OutputStream output) throws IOException, ArchiveDiffException, ArchiveException {
        String archiveType = detectArchiveType(input);
        getInstance(archiveType).sortArchiveEntriesImpl(input, output);
    }

    public static String detectArchiveType(InputStream in) throws ArchiveDiffException, IOException {
        try {
            final byte[] signature = new byte[12];
            in.mark(signature.length);
            int signatureLength = IOUtils.readFully(in, signature);
            in.reset();
            if (ZipArchiveInputStream.matches(signature, signatureLength)) {
                return ArchiveStreamFactory.ZIP;
            } else if (ArArchiveInputStream.matches(signature, signatureLength)) {
                return ArchiveStreamFactory.AR;
            } else {

                final byte[] tarheader = new byte[512];
                in.mark(tarheader.length);
                signatureLength = IOUtils.readFully(in, tarheader);
                in.reset();
                if (TarArchiveInputStream.matches(tarheader, signatureLength)) {
                    return ArchiveStreamFactory.TAR;
                }
            }
            throw new ArchiveDiffException("Unable to detect archive type - no supported archive type found for signature");
        } catch (IOException ex) {
            throw new ArchiveDiffException("Could not use reset and mark operations on input stream", ex);
        }
    }

    static boolean isSupportedArchive(ArchiveEntry entry) {
        return getArchiverType(entry) != null;
    }

    static String getArchiverType(ArchiveEntry entry) {
        if (entry.getName().endsWith(".zip")) {
            return ArchiveStreamFactory.ZIP;
        } else if (entry.getName().endsWith(".tar")) {
            return ArchiveStreamFactory.TAR;
        } else if (entry.getName().endsWith(".ar")) {
            return ArchiveStreamFactory.AR;
        } else if (entry.getName().endsWith(".deb")) {
            return ArchiveStreamFactory.AR;
        } else {
            return null;
        }
    }


}
