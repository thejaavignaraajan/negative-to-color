package com.example.negativetcolor.model;

/**
 * Response object returned after a successful image conversion.
 */
public class ConversionResult {

    private String originalFilename;
    private String outputFilename;
    private ConversionMode modeUsed;
    private int widthPx;
    private int heightPx;
    private long processingTimeMs;
    private String message;

    public ConversionResult() {}

    public ConversionResult(String originalFilename, String outputFilename,
                             ConversionMode modeUsed, int widthPx, int heightPx,
                             long processingTimeMs, String message) {
        this.originalFilename = originalFilename;
        this.outputFilename = outputFilename;
        this.modeUsed = modeUsed;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.processingTimeMs = processingTimeMs;
        this.message = message;
    }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }

    public String getOutputFilename() { return outputFilename; }
    public void setOutputFilename(String outputFilename) { this.outputFilename = outputFilename; }

    public ConversionMode getModeUsed() { return modeUsed; }
    public void setModeUsed(ConversionMode modeUsed) { this.modeUsed = modeUsed; }

    public int getWidthPx() { return widthPx; }
    public void setWidthPx(int widthPx) { this.widthPx = widthPx; }

    public int getHeightPx() { return heightPx; }
    public void setHeightPx(int heightPx) { this.heightPx = heightPx; }

    public long getProcessingTimeMs() { return processingTimeMs; }
    public void setProcessingTimeMs(long processingTimeMs) { this.processingTimeMs = processingTimeMs; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
