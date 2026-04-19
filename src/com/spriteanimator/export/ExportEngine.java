package com.spriteanimator.export;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Exports animation frames as:
 *  - PNG spritesheet   (name_sheet.png)
 *  - Animated GIF      (name.gif)
 *  - Individual frames (name_frame_00.png … name_frame_NN.png)
 */
public class ExportEngine {

    public static void exportSpriteSheet(BufferedImage[] frames, File outFile) throws IOException {
        if (frames == null || frames.length == 0) throw new IOException("Keine Frames.");
        int fw = frames[0].getWidth(), fh = frames[0].getHeight();
        BufferedImage sheet = new BufferedImage(fw * frames.length, fh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        for (int i = 0; i < frames.length; i++) g.drawImage(frames[i], i * fw, 0, null);
        g.dispose();
        ImageIO.write(sheet, "PNG", outFile);
    }

    public static void exportFrames(BufferedImage[] frames, File dir, String namePrefix)
            throws IOException {
        if (frames == null || frames.length == 0) throw new IOException("Keine Frames.");
        for (int i = 0; i < frames.length; i++) {
            File out = new File(dir, String.format("%s_frame_%02d.png", namePrefix, i));
            ImageIO.write(frames[i], "PNG", out);
        }
    }

    public static void exportGif(BufferedImage[] frames, int delayMs, File outFile)
            throws IOException {
        if (frames == null || frames.length == 0) throw new IOException("Keine Frames.");
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(outFile)) {
            writer.setOutput(ios);
            writer.prepareWriteSequence(null);
            for (int i = 0; i < frames.length; i++) {
                BufferedImage gifFrame = toRgb(frames[i]);
                IIOMetadata meta = buildGifMeta(writer, delayMs, i == 0);
                writer.writeToSequence(new javax.imageio.IIOImage(gifFrame, null, meta), null);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static BufferedImage toRgb(BufferedImage src) {
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(),
                                              BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(new Color(30, 30, 30));
        g.fillRect(0, 0, src.getWidth(), src.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return rgb;
    }

    private static IIOMetadata buildGifMeta(ImageWriter writer, int delayMs, boolean first)
            throws IOException {
        ImageWriteParam param = writer.getDefaultWriteParam();
        IIOMetadata meta = writer.getDefaultImageMetadata(
            new javax.imageio.ImageTypeSpecifier(toRgb(
                new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB))), param);
        String fmt = meta.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(fmt);

        IIOMetadataNode gce = getOrCreate(root, "GraphicControlExtension");
        gce.setAttribute("disposalMethod",       "doNotDispose");
        gce.setAttribute("userInputFlag",          "FALSE");
        gce.setAttribute("transparentColorFlag",   "FALSE");
        gce.setAttribute("delayTime",              String.valueOf(delayMs / 10));
        gce.setAttribute("transparentColorIndex",  "0");

        if (first) {
            IIOMetadataNode appExt = getOrCreate(root, "ApplicationExtensions");
            IIOMetadataNode app   = new IIOMetadataNode("ApplicationExtension");
            app.setAttribute("applicationID",      "NETSCAPE");
            app.setAttribute("authenticationCode", "2.0");
            app.setUserObject(new byte[]{ 0x1, 0x0, 0x0 });
            appExt.appendChild(app);
        }
        meta.setFromTree(fmt, root);
        return meta;
    }

    private static IIOMetadataNode getOrCreate(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++)
            if (root.item(i).getNodeName().equalsIgnoreCase(name))
                return (IIOMetadataNode) root.item(i);
        IIOMetadataNode node = new IIOMetadataNode(name);
        root.appendChild(node);
        return node;
    }
}
