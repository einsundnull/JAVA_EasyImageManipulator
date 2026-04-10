package paint;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JOptionPane;

public class WhiteToAlphaConverter {
	// console.log("### WhiteToAlphaConverter.java ###");

	public static void convertCompleteWhiteToAlpha(File inputFile) {
		// console.log("### WhiteToAlphaConverter.java convertCompleteWhiteToAlpha ###");
		try {
			BufferedImage image = ImageIO.read(inputFile);
			if (image == null) {
				JOptionPane.showMessageDialog(null, "Could not load image: " + inputFile.getName());
				return;
			}

			BufferedImage result = new BufferedImage(
				image.getWidth(), 
				image.getHeight(), 
				BufferedImage.TYPE_INT_ARGB
			);

			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					int rgb = image.getRGB(x, y);

					// Extract RGB values
					int r = (rgb >> 16) & 0xFF;
					int g = (rgb >> 8) & 0xFF;
					int b = rgb & 0xFF;

					// Check if pixel is white (with small tolerance)
					if (isWhite(r, g, b, 10)) {
						// Set to transparent
						result.setRGB(x, y, 0x00000000);
					} else {
						// Keep original pixel
						result.setRGB(x, y, rgb | 0xFF000000);
					}
				}
			}

			// Save result
			String outputPath = getOutputPath(inputFile, "_white_to_alpha");
			File outputFile = new File(outputPath);
			ImageIO.write(result, "PNG", outputFile);

			JOptionPane.showMessageDialog(null,
				"White-to-Alpha conversion complete!\nSaved: " + outputFile.getName());

		} catch (IOException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(null, "Error processing image: " + e.getMessage());
		}
	}

	private static boolean isWhite(int r, int g, int b, int tolerance) {
		// console.log("### WhiteToAlphaConverter.java isWhite ###");
		return r >= (255 - tolerance) && 
		       g >= (255 - tolerance) && 
		       b >= (255 - tolerance);
	}

	public static String getOutputPath(File inputFile, String suffix) {
		// console.log("### WhiteToAlphaConverter.java getOutputPath ###");
		String path = inputFile.getAbsolutePath();
		int lastDot = path.lastIndexOf('.');
		if (lastDot != -1) {
			return path.substring(0, lastDot) + suffix + ".png";
		} else {
			return path + suffix + ".png";
		}
	}
}