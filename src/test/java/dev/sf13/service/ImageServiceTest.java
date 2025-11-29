package dev.sf13.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@QuarkusTest
public class ImageServiceTest {

    @Inject
    ImageService imageService;

    @Inject
    SvgConverter svgConverter;

    @Test
    public void testSvgDetectionAndConversion() throws IOException {
        // Create a simple SVG
        String svgContent = "<svg height=\"100\" width=\"100\" xmlns=\"http://www.w3.org/2000/svg\">\n" +
                "  <circle cx=\"50\" cy=\"50\" r=\"40\" stroke=\"black\" stroke-width=\"3\" fill=\"red\" />\n" +
                "</svg>";
        byte[] svgBytes = svgContent.getBytes(StandardCharsets.UTF_8);

        // Test SvgConverter directly
        Assertions.assertTrue(svgConverter.isSvg(svgBytes), "Should detect SVG");

        byte[] pngBytes = svgConverter.convertSvgToPng(svgBytes);
        Assertions.assertNotNull(pngBytes);
        Assertions.assertTrue(pngBytes.length > 0);

        // Verify it's a PNG (first bytes)
        // PNG signature: 89 50 4E 47 0D 0A 1A 0A
        Assertions.assertEquals((byte) 0x89, pngBytes[0]);
        Assertions.assertEquals((byte) 'P', pngBytes[1]);
        Assertions.assertEquals((byte) 'N', pngBytes[2]);
        Assertions.assertEquals((byte) 'G', pngBytes[3]);

        Assertions.assertFalse(svgConverter.isSvg(pngBytes), "Should not detect PNG as SVG");
    }
}
