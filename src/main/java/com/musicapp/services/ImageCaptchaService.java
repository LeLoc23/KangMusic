package com.musicapp.services;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpSession;

@Service
public class ImageCaptchaService {

    public static final String SESSION_ATTRIBUTE = "captchaCode";
    public static final String SESSION_CODES_ATTRIBUTE = "captchaCodes";
    public static final String TOKEN_REQUEST_PARAM = "captchaToken";

    private static final char[] DIGITS = "23456789".toCharArray();
    private static final int CODE_LENGTH = 4;
    private static final int MAX_SESSION_CAPTCHAS = 6;

    private final SecureRandom random = new SecureRandom();

    public CaptchaData generateCaptcha() {
        String code = generateCode();
        return new CaptchaData(code, drawCodeImage(code), UUID.randomUUID().toString());
    }

    public boolean verify(String submittedCode, String expectedCode) {
        if (submittedCode == null || expectedCode == null) {
            return false;
        }
        return normalizeCode(submittedCode).equalsIgnoreCase(normalizeCode(expectedCode));
    }

    public void store(HttpSession session, CaptchaData captcha) {
        if (session == null || captcha == null) {
            return;
        }

        Map<String, String> codes = getCodes(session);
        if (codes.size() >= MAX_SESSION_CAPTCHAS) {
            codes.clear();
        }
        codes.put(captcha.token(), captcha.code());

        // Legacy fallback for forms rendered before captchaToken was added.
        session.setAttribute(SESSION_ATTRIBUTE, captcha.code());
        session.setAttribute(SESSION_CODES_ATTRIBUTE, codes);
    }

    public boolean verifyAndConsume(HttpSession session, String submittedCode, String token) {
        if (session == null) {
            return false;
        }

        String expectedCode = null;
        Map<String, String> codes = getCodes(session);
        String normalizedToken = token != null ? token.trim() : "";
        if (!normalizedToken.isEmpty()) {
            expectedCode = codes.remove(normalizedToken);
            session.setAttribute(SESSION_CODES_ATTRIBUTE, codes);
        }

        if (expectedCode == null) {
            expectedCode = (String) session.getAttribute(SESSION_ATTRIBUTE);
            session.removeAttribute(SESSION_ATTRIBUTE);
        } else if (expectedCode.equals(session.getAttribute(SESSION_ATTRIBUTE))) {
            session.removeAttribute(SESSION_ATTRIBUTE);
        }

        return verify(submittedCode, expectedCode);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getCodes(HttpSession session) {
        Object stored = session.getAttribute(SESSION_CODES_ATTRIBUTE);
        if (stored instanceof Map<?, ?>) {
            return (Map<String, String>) stored;
        }
        return new HashMap<>();
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.replaceAll("\\s+", "").trim();
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(DIGITS[random.nextInt(DIGITS.length)]);
        }
        return code.toString();
    }

    private String drawCodeImage(String code) {
        int width = 112;
        int height = 48;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        GradientPaint background = new GradientPaint(
                0, 0, new Color(235, 231, 196),
                width, height, new Color(184, 225, 198)
        );
        g.setPaint(background);
        g.fillRect(0, 0, width, height);

        drawInterference(g, width, height);
        drawDigits(g, code, height);

        g.dispose();
        return imageToBase64(image);
    }

    private void drawInterference(Graphics2D g, int width, int height) {
        for (int i = 0; i < 8; i++) {
            int x = random.nextInt(width) - 20;
            int y = random.nextInt(height);
            int length = 35 + random.nextInt(55);
            Color lineColor = random.nextBoolean()
                    ? new Color(58, 129, 90, 45)
                    : new Color(122, 73, 47, 42);
            g.setColor(lineColor);
            g.setStroke(new BasicStroke(1.0f + random.nextFloat()));
            g.drawLine(x, y, x + length, y - 14 + random.nextInt(29));
        }

        for (int i = 0; i < 55; i++) {
            int shade = 70 + random.nextInt(90);
            g.setColor(new Color(shade, shade + random.nextInt(30), shade, 35 + random.nextInt(50)));
            g.fillRect(random.nextInt(width), random.nextInt(height), 1, 1);
        }
    }

    private void drawDigits(Graphics2D g, String code, int height) {
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, 34);
        int x = 11;
        for (int i = 0; i < code.length(); i++) {
            AffineTransform originalTransform = g.getTransform();
            double angle = Math.toRadians(-4 + random.nextInt(9));
            int baseline = 34 + random.nextInt(7) - 3;

            g.rotate(angle, x + 10, height / 2.0);
            g.setFont(font.deriveFont(32f + random.nextFloat() * 3f));
            g.setColor(new Color(45 + random.nextInt(35), 55 + random.nextInt(30), 52 + random.nextInt(25), 235));
            g.drawString(String.valueOf(code.charAt(i)), x, baseline);
            g.setTransform(originalTransform);

            x += 24 + random.nextInt(3);
        }
    }

    private String imageToBase64(BufferedImage image) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (IOException e) {
            return "";
        }
    }

    public record CaptchaData(String code, String imageDataUri, String token) {
    }
}
