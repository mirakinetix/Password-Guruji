import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Converts the supplied product icon without cropping or redrawing the artwork. */
public final class BuildProductIcons {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        Path assets = root.resolve("documentation/brand-assets/password-guruji");
        BufferedImage original = ImageIO.read(assets.resolve("Password-Guruji_app_icon.png").toFile());
        if (original == null) throw new IOException("Cannot read supplied product icon.");
        Path resources = root.resolve("src/main/resources/com/mirakinetix/passwordguruji/brand");
        Files.createDirectories(resources);
        int side = Math.max(original.getWidth(), original.getHeight());
        BufferedImage square = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = square.createGraphics();
        g.drawImage(original, (side-original.getWidth())/2, (side-original.getHeight())/2, null);
        g.dispose();
        Map<Integer,byte[]> icons = new LinkedHashMap<>();
        for (int size : new int[]{16,24,32,48,64,128,256}) {
            BufferedImage scaled = resize(square, size);
            ImageIO.write(scaled, "png", resources.resolve("password-guruji-icon-"+size+".png").toFile());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(scaled, "png", bytes);
            icons.put(size, bytes.toByteArray());
        }
        writeIco(assets.resolve("password-guruji.ico"), icons);
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
