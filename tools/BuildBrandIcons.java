import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class BuildBrandIcons {
    private static final int[] PNG_SIZES = {16, 24, 32, 48, 64, 128, 256, 512};
    private static final int[] ICO_SIZES = {16, 24, 32, 48, 64, 128, 256};

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: BuildBrandIcons <original-logo.png> <repo-root>");
        Path source = Path.of(args[0]);
        Path root = Path.of(args[1]);
        BufferedImage original = ImageIO.read(source.toFile());
        if (original == null || original.getWidth() < 1000 || original.getHeight() < 700)
            throw new IOException("Expected the supplied high-resolution Mirakinetix logo.");

        Path docs = root.resolve("documentation/brand-assets");
        Path resources = root.resolve("src/main/resources/com/mirakinetix/passwordguruji/brand");
        Files.createDirectories(docs);
        Files.createDirectories(resources);
        Files.copy(source, docs.resolve("mirakinetix-logo-original.png"), StandardCopyOption.REPLACE_EXISTING);

        int x = Math.round(original.getWidth() * 0.298f);
        int y = Math.round(original.getHeight() * 0.135f);
        int w = Math.round(original.getWidth() * 0.445f);
        int h = Math.round(original.getHeight() * 0.445f);
        BufferedImage symbol = original.getSubimage(x, y, w, h);
        int background = original.getRGB(8, 8);
        BufferedImage master = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = master.createGraphics();
        g.setColor(new Color(background, true));
        g.fillRect(0, 0, 1024, 1024);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int maxW = 900, maxH = 650;
        double scale = Math.min((double) maxW / symbol.getWidth(), (double) maxH / symbol.getHeight());
        int drawW = (int) Math.round(symbol.getWidth() * scale);
        int drawH = (int) Math.round(symbol.getHeight() * scale);
        g.drawImage(symbol, (1024 - drawW) / 2, (1024 - drawH) / 2, drawW, drawH, null);
        g.dispose();

        Map<Integer, byte[]> icoImages = new LinkedHashMap<>();
        for (int size : PNG_SIZES) {
            BufferedImage scaled = resize(master, size);
            Path path = docs.resolve("mirakinetix-app-icon-" + size + ".png");
            ImageIO.write(scaled, "png", path.toFile());
            if (size <= 256) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ImageIO.write(scaled, "png", bytes);
                icoImages.put(size, bytes.toByteArray());
            }
        }
        ImageIO.write(resize(master, 512), "png", resources.resolve("mirakinetix-app-icon.png").toFile());
        writeIco(docs.resolve("mirakinetix-app-icon.ico"), icoImages);
        Files.writeString(docs.resolve("README.md"), """
                # Mirakinetix app logo assets

                These app icon files were derived from the original Mirakinetix logo supplied by the project owner. The square icon uses the central MK symbol so it remains recognizable at small sizes; the complete wordmark and slogan are preserved in `mirakinetix-logo-original.png`.

                - `mirakinetix-app-icon.ico`: multi-resolution Windows icon (16 through 256 px)
                - `mirakinetix-app-icon-*.png`: square PNG versions (16 through 512 px)
                - `mirakinetix-logo-original.png`: supplied full-size brand artwork

                The Java desktop window loads the 512 px PNG from application resources. The ICO is ready for a future Windows installer or executable wrapper.
                """);
    }

    private static BufferedImage resize(BufferedImage source, int size) {
        BufferedImage result = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source, 0, 0, size, size, null);
        g.dispose();
        return result;
    }

    private static void writeLeShort(DataOutputStream out, int value) throws IOException {
        out.writeByte(value & 0xff);
        out.writeByte((value >>> 8) & 0xff);
    }

    private static void writeLeInt(DataOutputStream out, int value) throws IOException {
        out.writeByte(value & 0xff);
        out.writeByte((value >>> 8) & 0xff);
        out.writeByte((value >>> 16) & 0xff);
        out.writeByte((value >>> 24) & 0xff);
    }
    private static void writeIco(Path target, Map<Integer, byte[]> images) throws IOException {
        int offset = 6 + 16 * images.size();
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(target))) {
            writeLeShort(out, 0);
            writeLeShort(out, 1);
            writeLeShort(out, images.size());
            for (var entry : images.entrySet()) {
                int size = entry.getKey();
                byte[] image = entry.getValue();
                out.writeByte(size == 256 ? 0 : size);
                out.writeByte(size == 256 ? 0 : size);
                out.writeByte(0);
                out.writeByte(0);
                writeLeShort(out, 1);
                writeLeShort(out, 32);
                writeLeInt(out, image.length);
                writeLeInt(out, offset);
                offset += image.length;
            }
            for (byte[] image : images.values()) out.write(image);
        }
    }
}
