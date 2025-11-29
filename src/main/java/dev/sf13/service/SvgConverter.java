package dev.sf13.service;

import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.logging.Log;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@ApplicationScoped
public class SvgConverter {

    public boolean isSvg(byte[] data) {
        // Check first 100 bytes for SVG signature
        // Using UTF-8 explicitly as SVG is text/XML based
        String start = new String(data, 0, Math.min(data.length, 100), StandardCharsets.UTF_8)
                .trim()
                .toLowerCase(Locale.ROOT);

        // Also check a bit deeper if it starts with XML declaration
        boolean startsWithSvg = start.contains("<svg");
        boolean startsWithXml = start.contains("<?xml");

        if (startsWithSvg) {
            return true;
        }

        if (startsWithXml) {
            // If it starts with XML, look a bit further for <svg
            String deeperCheck = new String(data, 0, Math.min(data.length, 1024), StandardCharsets.UTF_8)
                    .toLowerCase(Locale.ROOT);
            return deeperCheck.contains("<svg");
        }

        return false;
    }

    public byte[] convertSvgToPng(byte[] svgData) throws IOException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(svgData);
        TranscoderInput input = new TranscoderInput(inputStream);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        TranscoderOutput output = new TranscoderOutput(outputStream);

        PNGTranscoder transcoder = new PNGTranscoder();
        try {
            transcoder.transcode(input, output);
        } catch (TranscoderException e) {
            throw new IOException("Failed to convert SVG to PNG", e);
        }

        return outputStream.toByteArray();
    }
}
