package com.line4.idea2ip.global.storage;

import com.line4.idea2ip.global.exception.BaseException;
import com.line4.idea2ip.global.response.code.ErrorResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Pattern;

@Slf4j
public class LocalImageStorage {

    private final Path directory;
    private final String filenamePrefix;
    private final Pattern filenamePattern;
    private final ErrorResponseCode invalidImage;

    public LocalImageStorage(String directory, String filenamePrefix, ErrorResponseCode invalidImage) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        this.filenamePrefix = filenamePrefix;
        this.filenamePattern = Pattern.compile(Pattern.quote(filenamePrefix) + "[0-9]+\\.(png|jpg)");
        this.invalidImage = invalidImage;
    }

    public Path getDirectory() {
        return directory;
    }

    public byte[] read(String filename) throws IOException {
        if (!filenamePattern.matcher(filename).matches()) {
            throw new NoSuchFileException(filename);
        }
        try (var input = Files.newInputStream(directory.resolve(filename), LinkOption.NOFOLLOW_LINKS)) {
            return input.readAllBytes();
        }
    }

    public StoredImage store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BaseException(invalidImage);
        }

        Path target = null;
        try {
            byte[] content = file.getBytes();
            String extension = imageExtension(content);
            Files.createDirectories(directory);
            target = Files.createTempFile(directory, filenamePrefix, extension);
            Files.write(target, content);
            deleteOnRollback(target);
            String contentType = extension.equals(".png") ? MediaType.IMAGE_PNG_VALUE : MediaType.IMAGE_JPEG_VALUE;
            return new StoredImage(target.getFileName().toString(), contentType);
        } catch (IOException e) {
            deleteFile(target);
            log.error("Failed to store image", e);
            throw new BaseException(ErrorResponseCode.SERVER_ERROR);
        }
    }

    public void deleteAfterCommit(String filename) {
        if (!filenamePattern.matcher(filename).matches()) {
            return;
        }
        Path target = directory.resolve(filename);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteFile(target);
            }
        });
    }

    private String imageExtension(byte[] content) {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new BaseException(invalidImage);
            }

            ImageReader reader = readers.next();
            try {
                String extension = switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                    case "png" -> ".png";
                    case "jpeg" -> ".jpg";
                    default -> throw new BaseException(invalidImage);
                };
                reader.setInput(input);
                if (reader.getWidth(0) <= 0 || reader.getHeight(0) <= 0) {
                    throw new BaseException(invalidImage);
                }
                return extension;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new BaseException(invalidImage);
        }
    }

    private void deleteOnRollback(Path target) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    deleteFile(target);
                }
            }
        });
    }

    private void deleteFile(Path target) {
        if (target == null) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("Failed to clean up image {}", target.getFileName(), e);
        }
    }

    public record StoredImage(String fileName, String contentType) {
    }
}
