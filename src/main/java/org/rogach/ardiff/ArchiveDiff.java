package org.rogach.ardiff;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.rogach.ardiff.exceptions.ArchiveDiffException;
import org.rogach.ardiff.formats.ArArchiveDiff;
import org.rogach.ardiff.formats.TarArchiveDiff;
import org.rogach.ardiff.formats.ZipArchiveDiff;

import java.io.*;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

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
        computeDiff(before, after, diff, false);
    }

    public static void computeDiff(
            InputStream before,
            InputStream after,
            OutputStream diff,
            boolean assumeOrdering
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
        if ("zip".equals(archiveType)) {
            return new ZipArchiveDiff();
        } else if ("tar".equals(archiveType)) {
            return new TarArchiveDiff("");
        } else if ("tar.gz".equals(archiveType)) {
            return new TarArchiveDiff("gz");
        } else if ("tar.xz".equals(archiveType)) {
            return new TarArchiveDiff("xz");
        } else if ("ar".equals(archiveType)) {
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
                return "zip";
            } else if (ArArchiveInputStream.matches(signature, signatureLength)) {
                return "ar";
            } else if (GzipCompressorInputStream.matches(signature, signatureLength)) {
                in.mark(256 * 1024); // should be enough to decompress at least a single block
                InputStream unzippedStream = new BufferedInputStream(new GZIPInputStream(in), 512);
                String archiveType = detectArchiveType(unzippedStream);
                in.reset();
                if ("tar".equals(archiveType)) {
                    return "tar.gz";
                } else {
                    throw new ArchiveDiffException("Unable to detect archive type - no supported archive type found for signature");
                }
            } else if (XZCompressorInputStream.matches(signature, signatureLength)) {
                in.mark(256 * 1024); // should be enough to decompress at least a single block
                InputStream unzippedStream = new BufferedInputStream(new XZCompressorInputStream(in), 12);
                String archiveType = detectArchiveType(unzippedStream);
                in.reset();
                if ("tar".equals(archiveType)) {
                    return "tar.xz";
                } else {
                    throw new ArchiveDiffException("Unable to detect archive type - no supported archive type found for signature");
                }
            } else {

                final byte[] tarheader = new byte[512];
                in.mark(tarheader.length);
                signatureLength = IOUtils.readFully(in, tarheader);
                in.reset();
                if (TarArchiveInputStream.matches(tarheader, signatureLength)) {
                    return "tar";
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
            return "zip";
        } else if (entry.getName().endsWith(".jar")) {
            return "jar";
        } else if (entry.getName().endsWith(".tar")) {
            return "tar";
        } else if (entry.getName().endsWith(".tar.gz")) {
            return "tar.gz";
        } else if (entry.getName().endsWith(".tar.xz")) {
            return "tar.xz";
        } else if (entry.getName().endsWith(".ar")) {
            return "ar";
        } else if (entry.getName().endsWith(".deb")) {
            return "ar";
        } else {
            return null;
        }
    }

    public static void main(String[] args) throws IOException, ArchiveDiffException, ArchiveException {
        if (args.length == 4 && args[0].equals("compute")) {
            OutputStream output = new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(args[3])));
            ArchiveDiff.computeDiff(
                    new BufferedInputStream(new FileInputStream(args[1])),
                    new BufferedInputStream(new FileInputStream(args[2])),
                    output
            );
            output.close();
        } else if (args.length == 5 && args[0].equals("compute") && args[1].equals("--sorted")) {
            OutputStream output = new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(args[4])));
            ArchiveDiff.computeDiff(
                    new BufferedInputStream(new FileInputStream(args[2])),
                    new BufferedInputStream(new FileInputStream(args[3])),
                    output,
                    true
            );
            output.close();
        } else if (args.length == 4 && args[0].equals("apply")) {
            OutputStream output = new BufferedOutputStream(new FileOutputStream(args[3]));
            ArchiveDiff.applyDiff(
                    new BufferedInputStream(new FileInputStream(args[1])),
                    new GZIPInputStream(new BufferedInputStream(new FileInputStream(args[2]))),
                    output
            );
            output.close();
        } else if (args.length == 5 && args[0].equals("apply") && args[1].equals("--sorted")) {
            OutputStream output = new BufferedOutputStream(new FileOutputStream(args[4]));
            ArchiveDiff.applyDiff(
                    new BufferedInputStream(new FileInputStream(args[2])),
                    new GZIPInputStream(new BufferedInputStream(new FileInputStream(args[3]))),
                    output,
                    true
            );
            output.close();
        } else if (args.length == 3 && args[0].equals("sort")) {
            OutputStream output = new BufferedOutputStream(new FileOutputStream(args[2]));
            ArchiveDiff.sortArchiveEntries(
                    new BufferedInputStream(new FileInputStream(args[1])),
                    output
            );
            output.close();
        } else {
            System.out.println(String.join(
                    "\n",
                    "ArchiveDiff",
                    "",
                    "Usage:",
                    "",
                    "  java -jar ArchiveDiff.jar <command> [args...]",
                    "",
                    "Commands:",
                    "",
                    "  compute [--sorted] <before> <after> <diff>     Compute archive difference between <before> and <after>, store in <patch>.",
                    "                                                 If --sorted is provided, assumes that input archives were pre-sorted and",
                    "                                                 switches memory-efficient streaming mode which ensures binary equality of",
                    "                                                 patched archive to original <after> archive.",
                    "",
                    "  apply [--sorted] <before> <diff> <after>       Apply previously computed <diff> to <before> file, outputting patched archive",
                    "                                                 to <after>. If --sorted is provided, assumes that <before> archive is pre-sorted",
                    "                                                 and <diff> was also computed with --sorted on. In that case memory-efficient",
                    "                                                 streaming mode is used, and generated patched <after> will be binary equal to",
                    "                                                 original <after> archive.",
                    "",
                    "  sort <input> <output>                          Repack archive. Sorts entries by names inside the archive (except for AR format),",
                    "                                                 normalizes compression and entry headers. Archives that were preprocessed with",
                    "                                                 this option can be later passed into `compute` and `apply`, allowing for",
                    "                                                 faster computation and binary-equal archive patches."
            ));
        }
    }

}
