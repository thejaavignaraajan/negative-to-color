package com.example.negativetcolor.model;

/**
 * Enum representing the type of film negative conversion mode.
 */
public enum ConversionMode {

    /**
     * Simple pixel-level inversion: new = 255 - old.
     * Works well for digital/scanned B&W negatives.
     */
    SIMPLE_INVERT,

    /**
     * Color inversion + orange mask removal.
     * Applies per-channel normalization to compensate for the orange/amber
     * base layer inherent in C-41 color film negatives (most 90s colour film).
     */
    ORANGE_MASK_REMOVAL,

    /**
     * Full auto-color-correct conversion:
     * Inverts, removes orange mask, then applies auto white-balance and
     * gamma correction for a more natural-looking result.
     */
    AUTO_COLOR_CORRECT
}
