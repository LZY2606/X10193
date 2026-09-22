package com.symbolcourt.storage;

import com.symbolcourt.elf.ArchiveParser;
import com.symbolcourt.elf.ElfConstants;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/** Stores imported binaries read-only on disk; never modifies an original. */
@Component
public class BinaryStore {

    private final Path root;
    private final ArchiveParser archiveParser = new ArchiveParser();

    public BinaryStore() {
        String home = System.getProperty("SYMBOL_COURT_HOME",
                System.getenv().getOrDefault("SYMBOL_COURT_HOME", "data"));
        this.root = Path.of(home, "objects");
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path root() {
        return root;
    }

    public StoredBinary store(byte[] data, String originalName) {
        String sha = sha256(data);
        String ext = guessExtension(data, originalName);
        String fileId = sha.substring(0, 16) + ext;
        Path target = root.resolve(fileId);
        try {
            if (!Files.exists(target)) {
                Path tmp = root.resolve(fileId + ".tmp");
                Files.write(tmp, data);
                Files.move(tmp, target);
            }
            // Read-only preservation of the imported binary.
            try {
                Files.setLastModifiedTime(target, Files.getLastModifiedTime(target));
            } catch (IOException ignored) {
                // best effort
            }
            target.toFile().setReadOnly();
            return new StoredBinary(fileId, originalName, target, sha, data.length);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String guessExtension(byte[] data, String name) {
        if (archiveParser.isArchive(data)) {
            return ".a";
        }
        if (data.length >= 4 && data[0] == 0x7f && data[1] == 'E') {
            return ".o";
        }
        return ".bin";
    }

    private String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record StoredBinary(String fileId, String originalName, Path path, String sha256,
                               long size) {
    }
}
