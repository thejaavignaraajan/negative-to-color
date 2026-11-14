package com.example.negativetcolor.controller;

import com.example.negativetcolor.model.ColorAdjustment;
import com.example.negativetcolor.model.ConversionMode;
import com.example.negativetcolor.model.ConversionResult;
import com.example.negativetcolor.service.NegativeConversionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * REST controller for the film-negative-to-colour conversion API.
 */
@RestController
@RequestMapping("/api/convert")
@CrossOrigin(origins = "*")
public class ConversionController {

    private static final Logger log = LoggerFactory.getLogger(ConversionController.class);

    private final NegativeConversionService conversionService;

    public ConversionController(NegativeConversionService conversionService) {
        this.conversionService = conversionService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  POST /api/convert/image — returns raw PNG bytes (download)
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping(value = "/image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> convertImage(
            @RequestParam("file")  MultipartFile file,
            @RequestParam(value = "mode",       defaultValue = "AUTO_COLOR_CORRECT") ConversionMode mode,
            @RequestParam(value = "red",        defaultValue = "0")   int red,
            @RequestParam(value = "green",      defaultValue = "0")   int green,
            @RequestParam(value = "blue",       defaultValue = "0")   int blue,
            @RequestParam(value = "brightness", defaultValue = "0")   int brightness,
            @RequestParam(value = "contrast",   defaultValue = "0")   int contrast,
            @RequestParam(value = "saturation", defaultValue = "0")   int saturation) {

        log.info("Convert (file): '{}' mode={} adj=R{}/G{}/B{}/Bri{}/Con{}/Sat{}",
                 file.getOriginalFilename(), mode, red, green, blue, brightness, contrast, saturation);
        validateFile(file);

        try {
            ColorAdjustment adj = new ColorAdjustment(red, green, blue, brightness, contrast, saturation);
            long start = System.currentTimeMillis();
            byte[] result = conversionService.convert(file.getInputStream(), mode, adj);
            long elapsed = System.currentTimeMillis() - start;
            log.info("Done in {} ms, {} bytes", elapsed, result.length);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setContentDispositionFormData("attachment", buildOutputFilename(file.getOriginalFilename(), mode));
            headers.setContentLength(result.length);
            return new ResponseEntity<>(result, headers, HttpStatus.OK);

        } catch (IOException e) {
            log.error("Conversion failed", e);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  POST /api/convert/image/base64 — returns JSON with Base64 PNG + metadata
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping(value = "/image/base64", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> convertImageBase64(
            @RequestParam("file")  MultipartFile file,
            @RequestParam(value = "mode",       defaultValue = "AUTO_COLOR_CORRECT") ConversionMode mode,
            @RequestParam(value = "red",        defaultValue = "0")   int red,
            @RequestParam(value = "green",      defaultValue = "0")   int green,
            @RequestParam(value = "blue",       defaultValue = "0")   int blue,
            @RequestParam(value = "brightness", defaultValue = "0")   int brightness,
            @RequestParam(value = "contrast",   defaultValue = "0")   int contrast,
            @RequestParam(value = "saturation", defaultValue = "0")   int saturation) {

        validateFile(file);
        try {
            ColorAdjustment adj = new ColorAdjustment(red, green, blue, brightness, contrast, saturation);
            long start = System.currentTimeMillis();
            byte[] result = conversionService.convert(file.getInputStream(), mode, adj);
            long elapsed = System.currentTimeMillis() - start;

            int[] dims = conversionService.getDimensions(result);
            String base64 = java.util.Base64.getEncoder().encodeToString(result);

            ConversionResult meta = new ConversionResult(
                    file.getOriginalFilename(),
                    buildOutputFilename(file.getOriginalFilename(), mode),
                    mode, dims[0], dims[1], elapsed, "Conversion successful");

            return ResponseEntity.ok(Map.of(
                    "metadata",  meta,
                    "image",     "data:image/png;base64," + base64,
                    "timestamp", Instant.now().toString()
            ));

        } catch (IOException e) {
            log.error("Base64 conversion failed", e);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", "Could not process image: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  GET /api/convert/modes — list available modes
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/modes")
    public ResponseEntity<?> listModes() {
        return ResponseEntity.ok(Map.of("modes", new Object[]{
            Map.of("id", "SIMPLE_INVERT",
                   "label", "Simple Invert",
                   "description", "Flips every pixel: out = 255 − in. Best for B&W negatives."),
            Map.of("id", "ORANGE_MASK_REMOVAL",
                   "label", "Mask Removal (Any Base)",
                   "description", "Per-channel percentile normalisation. Works for orange or cyan/blue-shifted negatives."),
            Map.of("id", "AUTO_COLOR_CORRECT",
                   "label", "Auto Color Correct (Recommended)",
                   "description", "Mask removal + sRGB gamma correction. Best all-round result for 90s colour negatives.")
        }));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file uploaded.");
        }
        String ct = file.getContentType();
        if (ct == null || (!ct.startsWith("image/jpeg") && !ct.startsWith("image/png")
                && !ct.startsWith("image/tiff") && !ct.startsWith("image/bmp")
                && !ct.startsWith("image/webp"))) {
            throw new IllegalArgumentException(
                    "Unsupported file type '" + ct + "'. Upload JPEG, PNG, TIFF, BMP or WebP.");
        }
    }

    private String buildOutputFilename(String original, ConversionMode mode) {
        if (original == null) original = "negative";
        int dot = original.lastIndexOf('.');
        String base = (dot > 0) ? original.substring(0, dot) : original;
        return base + "_positive_" + mode.name().toLowerCase() + ".png";
    }
}
