package com.example.negativetcolor.model;

/**
 * Colour adjustment parameters applied on top of the base negative conversion.
 * All values use a normalised human-friendly scale matching the UI sliders.
 */
public class ColorAdjustment {

    /**
     * Red channel offset: -255 to +255 (0 = no change).
     * Positive shifts shadows/midtones towards red; negative towards cyan.
     */
    private int red = 0;

    /**
     * Green channel offset: -255 to +255 (0 = no change).
     */
    private int green = 0;

    /**
     * Blue channel offset: -255 to +255 (0 = no change).
     */
    private int blue = 0;

    /**
     * Brightness offset applied to all channels after colour offsets: -255 to +255 (0 = no change).
     */
    private int brightness = 0;

    /**
     * Contrast multiplier: -128 to +128 (0 = no change).
     * Uses the Photoshop-style contrast formula:
     *   factor = (259 × (c + 255)) / (255 × (259 − c))
     *   out = factor × (in − 128) + 128
     */
    private int contrast = 0;

    /**
     * Saturation scale in the range -100 to +100 (0 = no change, -100 = greyscale, +100 = vivid).
     * Internally converted to a multiplier around luminance.
     */
    private int saturation = 0;

    public ColorAdjustment() {}

    // ── Convenience constructor ──────────────────────────────────────────────
    public ColorAdjustment(int red, int green, int blue, int brightness, int contrast, int saturation) {
        this.red = red; this.green = green; this.blue = blue;
        this.brightness = brightness; this.contrast = contrast; this.saturation = saturation;
    }

    /** Returns true if all sliders are at their default (no-op) values. */
    public boolean isIdentity() {
        return red == 0 && green == 0 && blue == 0
                && brightness == 0 && contrast == 0 && saturation == 0;
    }

    // ── Getters & Setters ────────────────────────────────────────────────────
    public int getRed()        { return red; }
    public void setRed(int r)  { this.red = r; }

    public int getGreen()       { return green; }
    public void setGreen(int g) { this.green = g; }

    public int getBlue()        { return blue; }
    public void setBlue(int b)  { this.blue = b; }

    public int getBrightness()         { return brightness; }
    public void setBrightness(int bri) { this.brightness = bri; }

    public int getContrast()          { return contrast; }
    public void setContrast(int con)  { this.contrast = con; }

    public int getSaturation()          { return saturation; }
    public void setSaturation(int sat)  { this.saturation = sat; }
}
