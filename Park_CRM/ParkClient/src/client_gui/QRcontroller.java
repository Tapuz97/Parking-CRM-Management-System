/**
 * Controller class for managing the QR code generation and display in the GUI.
 * This class uses the ZXing library to generate QR codes and displays them
 * in a JavaFX ImageView. It also supports theme switching between dark and light modes.
 */
package client_gui;

import java.awt.image.BufferedImage;

import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.image.Image;
import javafx.scene.text.Text;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;

public class QRcontroller {

    /**
     * The main container for the QR code screen.
     */
    @FXML private AnchorPane mainScreen;

    /**
     * The ImageView where the generated QR code will be displayed.
     */
    @FXML private ImageView qrImageView;

    /**
     * The Text element for displaying status messages.
     */
    @FXML private Text statustTxt;

    /**
     * The foreground color of the QR code.
     */
    private int foreground = 0xFFFCC52D;

    /**
     * The background color of the QR code.
     */
    private int background = 0xFF2B3137;

    /**
     * Initializes the QR code with the given token and displays it in the ImageView.
     * 
     * @param token The token to encode in the QR code.
     */
    public void initToken(String token) {
        statustTxt.setText("Scan to log in");

        try {
            int size = 250;
            BitMatrix matrix = new QRCodeWriter().encode(token, BarcodeFormat.QR_CODE, size, size);
            MatrixToImageConfig config = new MatrixToImageConfig(foreground, background);
            BufferedImage qrBuffered = MatrixToImageWriter.toBufferedImage(matrix, config);

            Image fxImage = SwingFXUtils.toFXImage(qrBuffered, null);
            qrImageView.setImage(fxImage);

            // TODO: Start polling server to check token status here

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Sets the theme of the QR code by switching between dark and light modes.
     * 
     * @param isDarkTheme True for dark theme, false for light theme.
     */
    public void setTheme(boolean isDarkTheme) {
        if (isDarkTheme) {
            this.foreground = 0xFF2B3137;
            this.background = 0xFFFCC52D;
        } else {
            this.foreground = 0xFFFCC52D;
            this.background = 0xFF2B3137;
        }
    }
}
