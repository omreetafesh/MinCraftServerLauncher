import java.io.*;
import javax.imageio.ImageIO;

/**
 * Standalone utility — no module system or JavaFX needed.
 * Generates icon.ico (multi-resolution) from the app's own logo (src/main/AppIcon.java),
 * so the installer, the .exe and the running window always show the same icon.
 * Compile: javac -d tools\bin tools\MakeIcon.java src\main\AppIcon.java
 * Run:     java -cp tools\bin MakeIcon icon.ico
 */
public class MakeIcon {

    public static void main(String[] args) throws Exception {
        String outPath = args.length > 0 ? args[0] : "icon.ico";

        // Windows picks from these for the taskbar, Explorer views, Alt+Tab and the title bar
        int[] sizes = {16, 20, 24, 32, 40, 48, 64, 256};
        byte[][] pngs = new byte[sizes.length][];

        System.out.println("Rendering the app icon at " + sizes.length + " sizes...");
        for (int i = 0; i < sizes.length; i++) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            ImageIO.write(main.AppIcon.draw(sizes[i]), "png", buf);
            pngs[i] = buf.toByteArray();
        }

        // Write ICO file (little-endian, PNG-compressed entries — Vista+ compatible)
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(outPath))) {
            le16(os, 0);            // reserved
            le16(os, 1);            // type = icon
            le16(os, sizes.length); // number of images

            // Directory entries (16 bytes each)
            int dataOffset = 6 + 16 * sizes.length;
            for (int i = 0; i < sizes.length; i++) {
                int dim = (sizes[i] == 256) ? 0 : sizes[i]; // 0 means 256 in ICO spec
                os.write(dim);                  // width
                os.write(dim);                  // height
                os.write(0);                    // color count (0 = true-color)
                os.write(0);                    // reserved
                le16(os, 1);                    // color planes
                le16(os, 32);                   // bits per pixel
                le32(os, pngs[i].length);       // size of image data
                le32(os, dataOffset);           // file offset of image data
                dataOffset += pngs[i].length;
            }

            // Embedded PNG blobs
            for (byte[] png : pngs) os.write(png);
        }

        System.out.println("[OK] " + outPath);
    }

    // ── Little-endian helpers ────────────────────────────────────────────

    static void le16(OutputStream os, int v) throws IOException {
        os.write(v & 0xFF);
        os.write((v >> 8) & 0xFF);
    }

    static void le32(OutputStream os, int v) throws IOException {
        os.write(v & 0xFF);
        os.write((v >> 8) & 0xFF);
        os.write((v >> 16) & 0xFF);
        os.write((v >> 24) & 0xFF);
    }
}
