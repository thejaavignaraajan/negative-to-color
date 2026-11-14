package com.example.negativetcolor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;

import com.example.negativetcolor.model.ColorAdjustment;
import com.example.negativetcolor.model.ConversionMode;

/**
 * Core image processing service for converting film negatives to positive colour images.
 *
 * <h3>Why old orange-mask formula broke</h3>
 * <p>The original formula {@code positive = (255-neg) × 255/(255-mask)} amplifies channels with
 * high mask values (e.g. cyan base: G≈200, B≈210). The scale factor becomes 255/55 ≈ 4.6×,
 * clamping almost every pixel to 255 (white), wiping out all colour information.</p>
 *
 * <h3>Fixed Approach — Per-Channel Percentile Normalization</h3>
 * <p>For each colour channel independently:</p>
 * <ol>
 *   <li>Find <b>maxC</b> = 99th-percentile value → the film base / unexposed mask colour.</li>
 *   <li>Find <b>minC</b> = 1st-percentile value → the densest shadow / most exposed area.</li>
 *   <li>Invert + normalise: {@code positiveC = round((maxC − negC) × 255 / (maxC − minC))}</li>
 * </ol>
 * <p>This maps any base colour (orange, cyan, blue, etc.) to 0 (black borders)
 * and the brightest highlights to 255, while preserving scene colour fidelity.</p>
 *
 * <h3>Conversion Modes</h3>
 * <ul>
 *   <li>{@code SIMPLE_INVERT} — raw per-pixel inversion: out = 255 − in. No normalisation.</li>
 *   <li>{@code ORANGE_MASK_REMOVAL} — percentile-based inversion + normalisation per channel.</li>
 *   <li>{@code AUTO_COLOR_CORRECT} — same as above plus sRGB gamma curve (γ = 2.2) for
 *       natural midtone rendering on modern displays.</li>
 * </ul>
 */
@Service
public class NegativeConversionService {

    private static final Logger log = LoggerFactory.getLogger(NegativeConversionService.class);

    // sRGB standard gamma
    private static final double GAMMA = 2.2;

    // Percentile bounds for robust min/max estimation (ignores dust, sprocket holes, edge effects)
    private static final double LOW_PERCENTILE  = 0.01;  // 1st  percentile → dense shadow
    private static final double HIGH_PERCENTILE = 0.99;  // 99th percentile → film base

    /**
     * Main conversion entry point.
     *
     * @param inputStream raw bytes of the uploaded negative image (JPEG, PNG, TIFF …)
     * @param mode        conversion algorithm to apply
     * @return converted positive image as a PNG-encoded byte array
     */
    public byte[] convert(InputStream inputStream, ConversionMode mode) throws IOException {
        return convert(inputStream, mode, new ColorAdjustment());
    }

    /**
     * Main conversion entry point with optional colour adjustments.
     *
     * @param inputStream  raw bytes of the uploaded negative image
     * @param mode         which base conversion algorithm to apply
     * @param adjustment   per-channel and tonal adjustments applied after conversion
     * @return converted (and adjusted) positive image as PNG bytes
     */
    public byte[] convert(InputStream inputStream, ConversionMode mode, ColorAdjustment adjustment) throws IOException {
        log.debug("Starting conversion, mode={}, adjustments={}", mode, !adjustment.isIdentity());

        BufferedImage negative = ImageIO.read(inputStream);
        if (negative == null) {
            throw new IOException("Unable to decode image — unsupported format or corrupt file.");
        }

        // Work with fully opaque TYPE_INT_RGB
        BufferedImage src = toRGB(negative);
        log.debug("Image loaded: {}×{} px", src.getWidth(), src.getHeight());

        // Step 1: base negative conversion
        BufferedImage positive = switch (mode) {
            case SIMPLE_INVERT       -> simpleInvert(src);
            case ORANGE_MASK_REMOVAL -> invertNormalised(src, false);
            case AUTO_COLOR_CORRECT  -> invertNormalised(src, true);
        };

        // Step 2: apply colour adjustments (if any)
        if (!adjustment.isIdentity()) {
            positive = applyAdjustments(positive, adjustment);
        }

        log.debug("Conversion done: {}×{}", positive.getWidth(), positive.getHeight());
        return toBytes(positive);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Mode 1: Simple Invert — no normalisation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Inverts every RGB channel: {@code out = 255 − in}.
     * Good for black-and-white negatives or digital scans with no colour mask.
     */
    private BufferedImage simpleInvert(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int r = 255 - ((rgb >> 16) & 0xFF);
                int g = 255 - ((rgb >> 8)  & 0xFF);
                int b = 255 - ( rgb        & 0xFF);
                out.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Modes 2 & 3: Percentile-normalised inversion
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Core algorithm used by both ORANGE_MASK_REMOVAL and AUTO_COLOR_CORRECT.
     *
     * <p>For each channel C independently:
     * <pre>
     *   positiveC = clamp( round( (maxC − negC) × 255 / (maxC − minC) ) )
     * </pre>
     * where {@code maxC} = 99th-percentile (film base) and
     *       {@code minC} = 1st-percentile (deepest shadow).</p>
     *
     * <p>When {@code applyGamma} is true, a sRGB gamma curve
     * ({@code out = 255 × (in/255)^(1/γ)}) is applied after normalisation
     * to brighten midtones for a natural-looking result.</p>
     *
     * @param src        source negative image (TYPE_INT_RGB)
     * @param applyGamma whether to apply sRGB gamma correction
     */
    private BufferedImage invertNormalised(BufferedImage src, boolean applyGamma) {
        int w = src.getWidth(), h = src.getHeight();

        // ── Step 1: build per-channel histograms ──
        int[] histR = new int[256], histG = new int[256], histB = new int[256];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                histR[(rgb >> 16) & 0xFF]++;
                histG[(rgb >> 8)  & 0xFF]++;
                histB[ rgb        & 0xFF]++;
            }
        }

        int total = w * h;

        // ── Step 2: find per-channel range via percentiles ──
        int minR = percentileValue(histR, total, LOW_PERCENTILE);
        int maxR = percentileValue(histR, total, HIGH_PERCENTILE);
        int minG = percentileValue(histG, total, LOW_PERCENTILE);
        int maxG = percentileValue(histG, total, HIGH_PERCENTILE);
        int minB = percentileValue(histB, total, LOW_PERCENTILE);
        int maxB = percentileValue(histB, total, HIGH_PERCENTILE);

        log.debug("Channel ranges — R:[{}-{}]  G:[{}-{}]  B:[{}-{}]  gamma={}",
                  minR, maxR, minG, maxG, minB, maxB, applyGamma);

        // Avoid division by zero; a 0-range channel stays 0 (pure shadow)
        double rangeR = Math.max(1, maxR - minR);
        double rangeG = Math.max(1, maxG - minG);
        double rangeB = Math.max(1, maxB - minB);

        // ── Step 3: build a 256-entry gamma LUT (identity if gamma disabled) ──
        double[] gammaLut = buildGammaLut(applyGamma);

        // ── Step 4: apply inversion + normalisation (+ optional gamma) per pixel ──
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);

                // Invert + normalise each channel
                int ri = clamp((int) Math.round((maxR - ((rgb >> 16) & 0xFF)) * 255.0 / rangeR));
                int gi = clamp((int) Math.round((maxG - ((rgb >> 8)  & 0xFF)) * 255.0 / rangeG));
                int bi = clamp((int) Math.round((maxB - ( rgb        & 0xFF)) * 255.0 / rangeB));

                // Apply gamma curve via LUT
                int r = clamp((int) Math.round(gammaLut[ri]));
                int g = clamp((int) Math.round(gammaLut[gi]));
                int b = clamp((int) Math.round(gammaLut[bi]));

                out.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Colour Adjustment Pipeline
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Applies per-channel and tonal adjustments to a positive image.
     *
     * <p>Processing order (matches how photo editors typically apply adjustments):</p>
     * <ol>
     *   <li><b>Saturation</b> — shift colour intensity via luminance-weighted lerp</li>
     *   <li><b>Contrast</b> — Photoshop-style pivot-128 contrast</li>
     *   <li><b>RGB offsets</b> — per-channel additive colour balance</li>
     *   <li><b>Brightness</b> — global lightness offset</li>
     * </ol>
     *
     * @param src        input positive image
     * @param adj        adjustment parameters
     * @return new image with adjustments applied
     */
    public BufferedImage applyAdjustments(BufferedImage src, ColorAdjustment adj) {
        int w = src.getWidth(), h = src.getHeight();

        // Pre-compute contrast factor (Photoshop formula)
        // c ∈ [-128, +128]; factor centres around 128
        double contrastFactor = 1.0;
        if (adj.getContrast() != 0) {
            int c = Math.max(-128, Math.min(128, adj.getContrast()));
            contrastFactor = (259.0 * (c + 255)) / (255.0 * (259 - c));
        }

        // Saturation multiplier: 0 = greyscale, 1 = no change, 2 = vivid
        // slider is -100..+100 → mult is 0..2
        double satMult = 1.0 + (adj.getSaturation() / 100.0);

        int bri = adj.getBrightness();
        int offR = adj.getRed();
        int offG = adj.getGreen();
        int offB = adj.getBlue();

        log.debug("Applying adjustments: sat={} contrast={} bri={} R={} G={} B={}",
                  satMult, contrastFactor, bri, offR, offG, offB);

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                double r = (rgb >> 16) & 0xFF;
                double g = (rgb >> 8)  & 0xFF;
                double b =  rgb        & 0xFF;

                // 1. Saturation — perceptual luminance-weighted grey lerp
                //    luma weights: ITU-R BT.601
                double luma = 0.299 * r + 0.587 * g + 0.114 * b;
                r = luma + satMult * (r - luma);
                g = luma + satMult * (g - luma);
                b = luma + satMult * (b - luma);

                // 2. Contrast (pivot around 128)
                if (adj.getContrast() != 0) {
                    r = contrastFactor * (r - 128) + 128;
                    g = contrastFactor * (g - 128) + 128;
                    b = contrastFactor * (b - 128) + 128;
                }

                // 3. Per-channel colour balance offsets
                r += offR;
                g += offG;
                b += offB;

                // 4. Global brightness
                r += bri;
                g += bri;
                b += bri;

                int ri = clamp((int) Math.round(r));
                int gi = clamp((int) Math.round(g));
                int bi = clamp((int) Math.round(b));
                out.setRGB(x, y, (ri << 16) | (gi << 8) | bi);
            }
        }
        return out;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the pixel value at the given percentile from a histogram.
     *
     * @param hist  256-bucket histogram
     * @param total total number of pixels
     * @param p     fraction in [0.0, 1.0]
     */
    private int percentileValue(int[] hist, int total, double p) {
        int target = (int) Math.round(total * p);
        int cumulative = 0;
        for (int i = 0; i < 256; i++) {
            cumulative += hist[i];
            if (cumulative >= target) return i;
        }
        return 255;
    }

    /**
     * Builds a 256-entry look-up table for sRGB gamma encoding.
     * When {@code apply} is false, returns an identity (pass-through) LUT.
     *
     * <p>Gamma encode: {@code lut[i] = 255 × (i/255)^(1/γ)}</p>
     * <p>Values are stored as doubles to avoid rounding until the last moment.</p>
     */
    private double[] buildGammaLut(boolean apply) {
        double[] lut = new double[256];
        for (int i = 0; i < 256; i++) {
            if (apply) {
                lut[i] = 255.0 * Math.pow(i / 255.0, 1.0 / GAMMA);
            } else {
                lut[i] = i;
            }
        }
        return lut;
    }

    /** Converts any {@link BufferedImage} to {@code TYPE_INT_RGB} (strips alpha, flattens on white). */
    private BufferedImage toRGB(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) return src;
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, src.getWidth(), src.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return rgb;
    }

    /** Encodes a {@link BufferedImage} as PNG bytes in memory. */
    private byte[] toBytes(BufferedImage img) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }

    /** Clamps an integer to the [0, 255] range. */
    private int clamp(int v) {
        return Math.min(255, Math.max(0, v));
    }

    /**
     * Returns the width and height of an image encoded as PNG bytes.
     * Used by the controller to populate the response metadata.
     */
    public int[] getDimensions(byte[] pngBytes) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(pngBytes));
        return img != null ? new int[]{img.getWidth(), img.getHeight()} : new int[]{0, 0};
    }
}
