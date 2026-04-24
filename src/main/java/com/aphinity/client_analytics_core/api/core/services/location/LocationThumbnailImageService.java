package com.aphinity.client_analytics_core.api.core.services.location;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import dev.matrixlab.webp4j.WebPCodec;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * Converts uploaded image thumbnails into a normalized WEBP payload.
 *
 * This service isolates the image-processing dependency so the rest of the
 * location write path stays focused on authorization and persistence.
 */
@Service
public class LocationThumbnailImageService {
    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/webp"
    );

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
        "jpeg",
        "jpg",
        "png",
        "webp"
    );

    private static final float WEBP_QUALITY = 85.0f;

    public byte[] convertToWebp(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalidThumbnail("Thumbnail image is required");
        }

        String contentType = normalize(file.getContentType());
        String originalFilename = file.getOriginalFilename();
        if (!isSupportedInputType(contentType, originalFilename)) {
            throw invalidThumbnail("Unsupported image format. Please upload a JPG, PNG, or WEBP image");
        }

        byte[] sourceBytes;
        try {
            sourceBytes = file.getBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read the uploaded thumbnail image", ex);
        }

        BufferedImage decodedImage = decodeSupportedImage(sourceBytes);
        if (decodedImage == null) {
            throw invalidThumbnail("Unsupported image format. Please upload a JPG, PNG, or WEBP image");
        }

        try {
            return encodeToWebp(decodedImage);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to convert thumbnail image to WEBP", ex);
        }
    }

    private boolean isSupportedInputType(String contentType, String originalFilename) {
        if (contentType != null && SUPPORTED_CONTENT_TYPES.contains(contentType)) {
            return true;
        }
        return hasSupportedExtension(originalFilename);
    }

    private boolean hasSupportedExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return false;
        }

        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex >= originalFilename.length() - 1) {
            return false;
        }

        String extension = originalFilename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS.contains(extension);
    }

    private BufferedImage decodeSupportedImage(byte[] sourceBytes) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(sourceBytes)) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image != null) {
                return image;
            }
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read the uploaded thumbnail image", ex);
        }

        return decodeWebp(sourceBytes);
    }

    private BufferedImage decodeWebp(byte[] sourceBytes) {
        try {
            return WebPCodec.decodeImage(sourceBytes);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read the uploaded thumbnail image", ex);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read the uploaded thumbnail image", ex);
        }
    }

    private byte[] encodeToWebp(BufferedImage decodedImage) throws IOException {
        boolean lossless = decodedImage.getColorModel().hasAlpha();
        try {
            return WebPCodec.encodeImage(decodedImage, WEBP_QUALITY, lossless);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to convert thumbnail image to WEBP", ex);
        }
    }

    private ResponseStatusException invalidThumbnail(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        int separatorIndex = normalized.indexOf(';');
        if (separatorIndex >= 0) {
            normalized = normalized.substring(0, separatorIndex).trim();
        }
        return normalized;
    }
}
