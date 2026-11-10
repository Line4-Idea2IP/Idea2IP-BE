package com.line4.idea2ip.domain.project.storage;

import com.line4.idea2ip.global.response.code.ErrorResponseCode;
import com.line4.idea2ip.global.storage.LocalImageStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.regex.Pattern;

@Component
public class ProjectThumbnailStorage implements WebMvcConfigurer {

    private static final String URL_PREFIX = "/uploads/project-thumbnails/";
    public static final String RESOURCE_PATTERN = URL_PREFIX + "**";
    private static final Pattern THUMBNAIL_FILENAME = Pattern.compile("thumbnail-[0-9]+\\.(png|jpg)");

    private final LocalImageStorage imageStorage;

    public ProjectThumbnailStorage(@Value("${idea2ip.project-thumbnail-dir}") String directory) {
        this.imageStorage = new LocalImageStorage(directory, "thumbnail-", ErrorResponseCode.INVALID_PROJECT_THUMBNAIL);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = imageStorage.getDirectory().toUri().toString();
        registry.addResourceHandler(RESOURCE_PATTERN)
                .addResourceLocations(location.endsWith("/") ? location : location + "/");
    }

    public String store(MultipartFile file) {
        return URL_PREFIX + imageStorage.store(file).fileName();
    }

    public void deleteAfterCommit(String thumbnailUrl) {
        if (thumbnailUrl == null || !thumbnailUrl.startsWith(URL_PREFIX)) {
            return;
        }
        String filename = thumbnailUrl.substring(URL_PREFIX.length());
        if (THUMBNAIL_FILENAME.matcher(filename).matches()) {
            imageStorage.deleteAfterCommit(filename);
        }
    }
}
