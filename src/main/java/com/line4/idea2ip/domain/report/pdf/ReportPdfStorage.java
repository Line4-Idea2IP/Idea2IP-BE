package com.line4.idea2ip.domain.report.pdf;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Component
public class ReportPdfStorage {
    private static final Pattern KEY = Pattern.compile("[0-9]+-[a-f0-9-]{36}\\.pdf");
    private final Path root;

    public ReportPdfStorage(@Value("${idea2ip.brief.pdf-dir}") String directory) {
        root = Path.of(directory).toAbsolutePath().normalize();
    }

    public Stored write(Long versionId, UUID attempt, byte[] pdf) throws IOException {
        String key = versionId + "-" + attempt + ".pdf";
        String checksum = checksum(pdf);
        Files.createDirectories(root);
        Path target = resolve(key);
        boolean created = false;
        try (var out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            created = true;
            out.write(pdf);
        } catch (IOException exception) {
            if (created) discard(key);
            throw exception;
        }
        return new Stored(key, checksum);
    }

    public byte[] read(String key, String expectedChecksum) throws IOException {
        try (var input = Files.newInputStream(resolve(key), LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readAllBytes();
            if (!checksum(bytes).equals(expectedChecksum)) throw new IOException("Report PDF checksum mismatch");
            return bytes;
        }
    }

    public void discard(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException exception) {
            log.warn("Could not remove report PDF {}", key);
        }
    }

    private Path resolve(String key) {
        if (key == null || !KEY.matcher(key).matches()) throw new IllegalArgumentException("Invalid report PDF key");
        return root.resolve(key);
    }

    private String checksum(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record Stored(String key, String checksum) {}
}
