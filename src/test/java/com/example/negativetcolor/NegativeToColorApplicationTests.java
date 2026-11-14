package com.example.negativetcolor;

import com.example.negativetcolor.model.ConversionMode;
import com.example.negativetcolor.service.NegativeConversionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class NegativeToColorApplicationTests {

    @Autowired
    private NegativeConversionService service;

    private byte[] syntheticNegative;

    /**
     * Creates a synthetic negative: a 100x100 image with
     * a bright orange top-border (simulating film rebate)
     * and a mid-grey body (simulating a uniform mid-tone scene).
     */
    @BeforeEach
    void setUp() throws IOException {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        // Film rebate (top 5 rows) — typical orange/amber of C-41 base
        g.setColor(new Color(220, 160, 100));
        g.fillRect(0, 0, 100, 5);

        // Scene area — mid-grey (inverted from scene mid-tone)
        g.setColor(new Color(130, 130, 130));
        g.fillRect(0, 5, 100, 95);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        syntheticNegative = baos.toByteArray();
    }

    @Test
    @DisplayName("SIMPLE_INVERT: output dimensions match input")
    void simpleInvert_dimensionsMatch() throws IOException {
        byte[] result = service.convert(new ByteArrayInputStream(syntheticNegative), ConversionMode.SIMPLE_INVERT);
        assertNotNull(result);
        assertTrue(result.length > 0);
        int[] dims = service.getDimensions(result);
        assertEquals(100, dims[0]); // width
        assertEquals(100, dims[1]); // height
    }

    @Test
    @DisplayName("SIMPLE_INVERT: each pixel channel is correctly inverted")
    void simpleInvert_correctInversion() throws IOException {
        byte[] result = service.convert(new ByteArrayInputStream(syntheticNegative), ConversionMode.SIMPLE_INVERT);
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(result));

        // Mid-grey at (50,50): 130 inverted → 125
        int rgb = out.getRGB(50, 50);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8)  & 0xFF;
        int b =  rgb        & 0xFF;

        assertEquals(255 - 130, r, 2);
        assertEquals(255 - 130, g, 2);
        assertEquals(255 - 130, b, 2);
    }

    @Test
    @DisplayName("ORANGE_MASK_REMOVAL: output is a valid PNG image")
    void orangeMaskRemoval_producesValidPng() throws IOException {
        byte[] result = service.convert(new ByteArrayInputStream(syntheticNegative), ConversionMode.ORANGE_MASK_REMOVAL);
        assertNotNull(result);
        // PNG magic bytes: 0x89 0x50 0x4E 0x47
        assertEquals((byte) 0x89, result[0]);
        assertEquals((byte) 0x50, result[1]);
        assertEquals((byte) 0x4E, result[2]);
        assertEquals((byte) 0x47, result[3]);
    }

    @Test
    @DisplayName("AUTO_COLOR_CORRECT: pixels are in valid 0-255 range")
    void autoColorCorrect_pixelsInRange() throws IOException {
        byte[] result = service.convert(new ByteArrayInputStream(syntheticNegative), ConversionMode.AUTO_COLOR_CORRECT);
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(result));

        for (int y = 0; y < out.getHeight(); y++) {
            for (int x = 0; x < out.getWidth(); x++) {
                int rgb = out.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8)  & 0xFF;
                int bv = rgb        & 0xFF;
                assertTrue(r >= 0 && r <= 255, "Red out of range at (" + x + "," + y + ")");
                assertTrue(g >= 0 && g <= 255, "Green out of range at (" + x + "," + y + ")");
                assertTrue(bv >= 0 && bv <= 255, "Blue out of range at (" + x + "," + y + ")");
            }
        }
    }

    @Test
    @DisplayName("All three modes produce non-empty output")
    void allModes_produceOutput() throws IOException {
        for (ConversionMode mode : ConversionMode.values()) {
            byte[] result = service.convert(new ByteArrayInputStream(syntheticNegative), mode);
            assertNotNull(result, "Result should not be null for mode: " + mode);
            assertTrue(result.length > 100, "Result too small for mode: " + mode);
        }
    }
}
