package com.musicapp.services;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.*;
import java.util.List;

/**
 * Image-based CAPTCHA service.
 * Generates a 3x3 grid of images from different categories.
 * The user must select all images matching a target category.
 */
@Service
public class ImageCaptchaService {

    private final SecureRandom random = new SecureRandom();

    // Available categories with their drawing methods
    public enum Category {
        CAR("ô tô"),
        TREE("cây cối"),
        HOUSE("ngôi nhà"),
        STAR("ngôi sao"),
        HEART("trái tim"),
        MUSIC("nốt nhạc"),
        SUN("mặt trời"),
        MOON("mặt trăng");

        private final String displayName;

        Category(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public static class CaptchaData {
        private final List<String> images; // Base64 encoded images (9 images)
        private final String targetCategory; // Display name of target category
        private final Set<Integer> correctIndices; // 0-based indices of correct images

        public CaptchaData(List<String> images, String targetCategory, Set<Integer> correctIndices) {
            this.images = images;
            this.targetCategory = targetCategory;
            this.correctIndices = correctIndices;
        }

        public List<String> getImages() { return images; }
        public String getTargetCategory() { return targetCategory; }
        public Set<Integer> getCorrectIndices() { return correctIndices; }
    }

    /**
     * Generate a new image CAPTCHA with 9 images in a 3x3 grid.
     */
    public CaptchaData generateCaptcha() {
        // Pick target category
        Category[] allCategories = Category.values();
        Category target = allCategories[random.nextInt(allCategories.length)];

        // Decide how many target images (2-4)
        int targetCount = 2 + random.nextInt(3); // 2, 3, or 4

        // Build list of 9 items
        List<Category> grid = new ArrayList<>();
        Set<Integer> correctIndices = new HashSet<>();

        // Fill with target images first
        for (int i = 0; i < targetCount; i++) {
            grid.add(target);
        }

        // Fill remaining with other categories
        List<Category> others = new ArrayList<>();
        for (Category c : allCategories) {
            if (c != target) others.add(c);
        }

        for (int i = targetCount; i < 9; i++) {
            grid.add(others.get(random.nextInt(others.size())));
        }

        // Shuffle
        Collections.shuffle(grid, random);

        // Record correct indices after shuffle
        for (int i = 0; i < grid.size(); i++) {
            if (grid.get(i) == target) {
                correctIndices.add(i);
            }
        }

        // Generate images
        List<String> images = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            BufferedImage img = drawCategoryImage(grid.get(i));
            images.add(imageToBase64(img));
        }

        return new CaptchaData(images, target.getDisplayName(), correctIndices);
    }

    /**
     * Verify user selections against correct answers.
     */
    public boolean verify(Set<Integer> userSelections, Set<Integer> correctIndices) {
        if (userSelections == null || correctIndices == null) return false;
        return userSelections.equals(correctIndices);
    }

    private BufferedImage drawCategoryImage(Category category) {
        int size = 100;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        // Anti-aliasing
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Random subtle background variation
        int bgBase = 25 + random.nextInt(15); // Dark bg: 25-40
        g.setColor(new Color(bgBase, bgBase, bgBase + 5));
        g.fillRect(0, 0, size, size);

        // Small random offset for variation
        int offsetX = random.nextInt(7) - 3;
        int offsetY = random.nextInt(7) - 3;

        switch (category) {
            case CAR -> drawCar(g, size, offsetX, offsetY);
            case TREE -> drawTree(g, size, offsetX, offsetY);
            case HOUSE -> drawHouse(g, size, offsetX, offsetY);
            case STAR -> drawStar(g, size, offsetX, offsetY);
            case HEART -> drawHeart(g, size, offsetX, offsetY);
            case MUSIC -> drawMusic(g, size, offsetX, offsetY);
            case SUN -> drawSun(g, size, offsetX, offsetY);
            case MOON -> drawMoon(g, size, offsetX, offsetY);
        }

        g.dispose();
        return image;
    }

    private void drawCar(Graphics2D g, int size, int ox, int oy) {
        int cx = size / 2 + ox;
        int cy = size / 2 + oy;
        // Car body
        Color bodyColor = randomColor(new int[][]{{200, 60, 60}, {60, 120, 200}, {200, 180, 50}, {100, 200, 100}});
        g.setColor(bodyColor);
        g.fillRoundRect(cx - 35, cy - 5, 70, 22, 10, 10);
        // Car top
        g.fillRoundRect(cx - 22, cy - 22, 44, 20, 10, 10);
        // Windows
        g.setColor(new Color(180, 220, 255, 180));
        g.fillRoundRect(cx - 18, cy - 19, 16, 13, 4, 4);
        g.fillRoundRect(cx + 2, cy - 19, 16, 13, 4, 4);
        // Wheels
        g.setColor(new Color(50, 50, 50));
        g.fillOval(cx - 25, cy + 12, 16, 16);
        g.fillOval(cx + 10, cy + 12, 16, 16);
        // Wheel highlights
        g.setColor(new Color(120, 120, 120));
        g.fillOval(cx - 21, cy + 16, 8, 8);
        g.fillOval(cx + 14, cy + 16, 8, 8);
        // Headlights
        g.setColor(new Color(255, 255, 150));
        g.fillOval(cx + 30, cy, 6, 6);
    }

    private void drawTree(Graphics2D g, int size, int ox, int oy) {
        int cx = size / 2 + ox;
        int cy = size / 2 + oy;
        // Trunk
        g.setColor(new Color(120, 80, 40));
        g.fillRoundRect(cx - 6, cy + 5, 12, 30, 4, 4);
        // Foliage layers (3 circles for bushy look)
        Color leafColor = new Color(30 + random.nextInt(40), 150 + random.nextInt(60), 50 + random.nextInt(40));
        g.setColor(leafColor);
        g.fillOval(cx - 22, cy - 25, 44, 38);
        g.setColor(leafColor.darker());
        g.fillOval(cx - 18, cy - 32, 36, 30);
        g.setColor(leafColor.brighter());
        g.fillOval(cx - 14, cy - 20, 28, 24);
    }

    private void drawHouse(Graphics2D g, int size, int ox, int oy) {
        int cx = size / 2 + ox;
        int cy = size / 2 + oy;
        // Wall
        Color wallColor = randomColor(new int[][]{{200, 180, 150}, {180, 140, 100}, {220, 200, 170}});
        g.setColor(wallColor);
        g.fillRect(cx - 25, cy - 5, 50, 35);
        // Roof
        g.setColor(new Color(180, 60, 40));
        int[] xPoints = {cx - 30, cx, cx + 30};
        int[] yPoints = {cy - 5, cy - 30, cy - 5};
        g.fillPolygon(xPoints, yPoints, 3);
        // Door
        g.setColor(new Color(100, 70, 40));
        g.fillRoundRect(cx - 7, cy + 10, 14, 20, 4, 4);
        // Door knob
        g.setColor(new Color(220, 180, 50));
        g.fillOval(cx + 3, cy + 18, 4, 4);
        // Window
        g.setColor(new Color(150, 200, 255, 200));
        g.fillRect(cx + 12, cy, 10, 10);
        g.setColor(new Color(80, 80, 80));
        g.drawLine(cx + 17, cy, cx + 17, cy + 10);
        g.drawLine(cx + 12, cy + 5, cx + 22, cy + 5);
    }

    private void drawStar(Graphics2D g, int size, int ox, int oy) {
        int cx = size / 2 + ox;
        int cy = size / 2 + oy;
        Color starColor = randomColor(new int[][]{{255, 215, 0}, {255, 180, 30}, {255, 240, 80}});
        g.setColor(starColor);
        // 5-pointed star
        Path2D star = new Path2D.Double();
        double outerR = 30;
        double innerR = 12;
        for (int i = 0; i < 10; i++) {
            double r = (i % 2 == 0) ? outerR : innerR;
            double angle = Math.PI / 2 + i * Math.PI / 5;
            double x = cx + r * Math.cos(angle);
            double y = cy - r * Math.sin(angle);
            if (i == 0) star.moveTo(x, y);
            else star.lineTo(x, y);
        }
        star.closePath();
        g.fill(star);
        // Glow effect
        g.setColor(new Color(255, 255, 200, 40));
        g.fill(new Ellipse2D.Double(cx - 35, cy - 35, 70, 70));
    }

    private void drawHeart(Graphics2D g, int size, int ox, int oy) {
        int cx = size / 2 + ox;
        int cy = size / 2 + oy - 2;
        Color heartColor = randomColor(new int[][]{{220, 40, 60}, {230, 50, 80}, {200, 30, 50}, {240, 80, 120}});
        g.setColor(heartColor);
        // Heart shape using bezier curves
        Path2D heart = new Path2D.Double();
        heart.moveTo(cx, cy + 25);
        heart.curveTo(cx - 45, cy - 5, cx - 25, cy - 35, cx, cy - 15);
        heart.curveTo(cx + 25, cy - 35, cx + 45, cy - 5, cx, cy + 25);
        heart.closePath();
        g.fill(heart);
        // Highlight
        g.setColor(new Color(255, 255, 255, 50));
        g.fillOval(cx - 15, cy - 22, 14, 14);
    }

    private void drawMusic(Graphics2D g, int size, int ox, int oy) {
        int cx = size / 2 + ox;
        int cy = size / 2 + oy;
        Color noteColor = randomColor(new int[][]{{30, 215, 96}, {100, 180, 255}, {255, 150, 50}, {200, 100, 255}});

        // Note head 1
        g.setColor(noteColor);
        g.fillOval(cx - 20, cy + 8, 18, 14);

        // Note head 2
        g.fillOval(cx + 4, cy + 2, 18, 14);

        // Stems
        g.setStroke(new BasicStroke(3));
        g.drawLine(cx - 4, cy + 12, cx - 4, cy - 25);
        g.drawLine(cx + 20, cy + 6, cx + 20, cy - 30);

        // Beam connecting stems
        g.setStroke(new BasicStroke(4));
        g.drawLine(cx - 4, cy - 25, cx + 20, cy - 30);
        g.drawLine(cx - 4, cy - 20, cx + 20, cy - 25);
    }

    private void drawSun(Graphics2D g, int size, int ox, int oy) {
        int cx = size / 2 + ox;
        int cy = size / 2 + oy;
        // Rays
        g.setColor(new Color(255, 200, 50, 150));
        g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI / 6;
            int x1 = cx + (int)(22 * Math.cos(angle));
            int y1 = cy + (int)(22 * Math.sin(angle));
            int x2 = cx + (int)(36 * Math.cos(angle));
            int y2 = cy + (int)(36 * Math.sin(angle));
            g.drawLine(x1, y1, x2, y2);
        }
        // Sun body
        g.setColor(new Color(255, 220, 50));
        g.fillOval(cx - 18, cy - 18, 36, 36);
        // Face - eyes
        g.setColor(new Color(50, 50, 50));
        g.fillOval(cx - 8, cy - 6, 5, 5);
        g.fillOval(cx + 4, cy - 6, 5, 5);
        // Smile
        g.setStroke(new BasicStroke(2));
        g.drawArc(cx - 8, cy - 2, 16, 12, 200, 140);
    }

    private void drawMoon(Graphics2D g, int size, int ox, int oy) {
        int cx = size / 2 + ox;
        int cy = size / 2 + oy;
        // Moon crescent
        g.setColor(new Color(240, 230, 180));
        g.fillOval(cx - 22, cy - 22, 44, 44);
        // Cut out crescent
        int bgBase = 25 + random.nextInt(15);
        g.setColor(new Color(bgBase, bgBase, bgBase + 5));
        g.fillOval(cx - 8, cy - 26, 40, 40);
        // Small stars around
        g.setColor(new Color(255, 255, 200, 180));
        drawSmallStar(g, cx + 20, cy - 18, 4);
        drawSmallStar(g, cx + 25, cy + 5, 3);
        drawSmallStar(g, cx - 25, cy - 15, 3);
    }

    private void drawSmallStar(Graphics2D g, int x, int y, int r) {
        Path2D star = new Path2D.Double();
        for (int i = 0; i < 8; i++) {
            double rad = (i % 2 == 0) ? r : r / 2.0;
            double angle = i * Math.PI / 4 - Math.PI / 2;
            double px = x + rad * Math.cos(angle);
            double py = y + rad * Math.sin(angle);
            if (i == 0) star.moveTo(px, py);
            else star.lineTo(px, py);
        }
        star.closePath();
        g.fill(star);
    }

    private Color randomColor(int[][] options) {
        int[] chosen = options[random.nextInt(options.length)];
        return new Color(chosen[0], chosen[1], chosen[2]);
    }

    private String imageToBase64(BufferedImage image) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bos);
            byte[] imageBytes = bos.toByteArray();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes);
        } catch (IOException e) {
            return "";
        }
    }
}
