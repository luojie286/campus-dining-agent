package com.sky.controller;

import com.sky.service.ProductImageStorage;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@Profile("!bootstrap")
public class ProductImageController {

    private final ProductImageStorage imageStorage;

    public ProductImageController(ProductImageStorage imageStorage) {
        this.imageStorage = imageStorage;
    }

    @GetMapping(ProductImageStorage.PUBLIC_PATH + "{filename}")
    public ResponseEntity<Resource> read(@PathVariable("filename") String filename) {
        Resource image = imageStorage.read(filename);
        String contentType = filename.endsWith(".jpg") ? "image/jpeg"
                : filename.endsWith(".png") ? "image/png" : "image/webp";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(image);
    }
}
