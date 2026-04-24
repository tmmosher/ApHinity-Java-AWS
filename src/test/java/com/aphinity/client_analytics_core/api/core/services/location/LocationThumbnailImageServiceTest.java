package com.aphinity.client_analytics_core.api.core.services.location;

import dev.matrixlab.webp4j.WebPCodec;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocationThumbnailImageServiceTest {
    private final LocationThumbnailImageService locationThumbnailImageService = new LocationThumbnailImageService();

    @Test
    void rejectsEmptyThumbnailUploads() {
        MockMultipartFile file = new MockMultipartFile("file", "thumbnail.png", "image/png", new byte[0]);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationThumbnailImageService.convertToWebp(file)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Thumbnail image is required", ex.getReason());
    }

    @Test
    void rejectsUnsupportedThumbnailFormats() {
        MockMultipartFile file = new MockMultipartFile("file", "thumbnail.txt", "text/plain", "nope".getBytes());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationThumbnailImageService.convertToWebp(file)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Unsupported image format. Please upload a JPG, PNG, or WEBP image", ex.getReason());
    }

    @Test
    void convertsPngThumbnailBytesEvenWhenMetadataClaimsWebp() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, Color.RED.getRGB());
        image.setRGB(1, 0, Color.GREEN.getRGB());
        image.setRGB(0, 1, Color.BLUE.getRGB());
        image.setRGB(1, 1, Color.WHITE.getRGB());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "thumbnail.webp",
            "image/webp",
            outputStream.toByteArray()
        );

        byte[] converted = locationThumbnailImageService.convertToWebp(file);
        assertNotNull(converted);
        BufferedImage decoded = WebPCodec.decodeImage(converted);
        assertNotNull(decoded);
        assertEquals(2, decoded.getWidth());
        assertEquals(2, decoded.getHeight());
    }

    @Test
    void convertsWebpThumbnailBytes() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.RED.getRGB());
        image.setRGB(1, 0, Color.GREEN.getRGB());
        image.setRGB(0, 1, Color.BLUE.getRGB());
        image.setRGB(1, 1, Color.WHITE.getRGB());

        byte[] webpBytes = WebPCodec.encodeImage(image, 80.0f);
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "thumbnail.webp",
            "image/webp",
            webpBytes
        );

        byte[] converted = locationThumbnailImageService.convertToWebp(file);
        assertNotNull(converted);
        BufferedImage decoded = WebPCodec.decodeImage(converted);
        assertNotNull(decoded);
        assertEquals(2, decoded.getWidth());
        assertEquals(2, decoded.getHeight());
    }

    @Test
    void rejectsCorruptedImageBytesWhenMetadataLooksSupported() {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "thumbnail.png",
            "image/png",
            "not-an-image".getBytes()
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            locationThumbnailImageService.convertToWebp(file)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Unable to read the uploaded thumbnail image", ex.getReason());
    }
}
