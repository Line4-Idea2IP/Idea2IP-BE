package com.line4.idea2ip.domain.project.storage;

import com.line4.idea2ip.global.exception.BaseException;
import com.line4.idea2ip.global.response.code.ErrorResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
@Slf4j
public class ProjectThumbnailStorage implements WebMvcConfigurer {

    private static final String URL_PREFIX = "/uploads/project-thumbnails/";
    public static final String RESOURCE_PATTERN = URL_PREFIX + "**";
    private static final Pattern THUMBNAIL_FILENAME = Pattern.compile("thumbnail-[0-9]+\\.(png|jpg)");

    private final Path directory;

    public ProjectThumbnailStorage(@Value("${idea2ip.project-thumbnail-dir}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = directory.toUri().toString();
        registry.addResourceHandler(RESOURCE_PATTERN)
                .addResourceLocations(location.endsWith("/") ? location : location + "/");
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BaseException(ErrorResponseCode.INVALID_PROJECT_THUMBNAIL);
        }

        Path target = null;
        try {
            byte[] content = file.getBytes();
            String extension = imageExtension(content);
            Files.createDirectories(directory);
            target = Files.createTempFile(directory, "thumbnail-", extension);
            Files.write(target, content);
            deleteOnRollback(target);
            return URL_PREFIX + target.getFileName();
        } catch (IOException e) {
            deleteFile(target);
            log.error("Failed to store project thumbnail", e);
            throw new BaseException(ErrorResponseCode.SERVER_ERROR);
        }
    }

    public void deleteAfterCommit(String thumbnailUrl) {
        if (thumbnailUrl == null || !thumbnailUrl.startsWith(URL_PREFIX)) {
            return;
        }
        String filename = thumbnailUrl.substring(URL_PREFIX.length());
        if (!THUMBNAIL_FILENAME.matcher(filename).matches()) {
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
                throw new BaseException(ErrorResponseCode.INVALID_PROJECT_THUMBNAIL);
            }

            ImageReader reader = readers.next();
            try {
                String extension = switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                    case "png" -> ".png";
                    case "jpeg" -> ".jpg";
                    default -> throw new BaseException(ErrorResponseCode.INVALID_PROJECT_THUMBNAIL);
                };
                reader.setInput(input);
                if (reader.getWidth(0) <= 0 || reader.getHeight(0) <= 0) {
                    throw new BaseException(ErrorResponseCode.INVALID_PROJECT_THUMBNAIL);
                }
                return extension;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new BaseException(ErrorResponseCode.INVALID_PROJECT_THUMBNAIL);
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
            log.warn("Failed to clean up project thumbnail {}", target.getFileName(), e);
        }
    }
}
