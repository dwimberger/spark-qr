package net.wimpi.qr;

import static spark.Spark.*;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.servlet.MultipartConfigElement;
import javax.servlet.http.Part;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author dieter at wimpi.net
 */
public class App {

    private static final Logger LOG = LoggerFactory.getLogger(App.class);
    private static final Map<DecodeHintType, ?> finalHints = buildHints();
    private static final String QR_PREFIX_KEY = "QR_PREFIX";
    private static final String QR_PREFIX;

    static {
        //Prefix
        if(System.getenv().containsKey(QR_PREFIX_KEY)) {
            QR_PREFIX = System.getenv(QR_PREFIX_KEY);
        } else {
            QR_PREFIX = "";
        }

    }//static initializer

    public static void main(String[] args) {
        //Encoder
        get("/qr/:name", (request, response) -> {
            String in = QR_PREFIX + request.params(":name");
            if (in == null || in.length() == 0) {
                halt(400, "String to be encoded is missing!");
            } else {
                //handle size
                int size = 300;
                String sizeString =request.queryParams("size");
                if (sizeString != null) {
                    try {
                        size = Integer.parseInt(sizeString);
                    } catch (NumberFormatException ex) {
                        halt(500, ex.getMessage());
                    }
                }
                //handle format
                String imageFormat = request.queryParams("format");
                if (imageFormat == null) {
                    imageFormat = "PNG";
                }
                //handle ECL
                String errorCorrectionLevel = null;
                if (request.queryParams("ecl") != null) {
                    errorCorrectionLevel = request.queryParams("ecl");
                }

                Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
                if (errorCorrectionLevel != null) {
                    hints.put(EncodeHintType.ERROR_CORRECTION, errorCorrectionLevel);
                }

                BitMatrix matrix = new MultiFormatWriter().encode(
                        in,
                        BarcodeFormat.QR_CODE,
                        size,
                        size,
                        hints
                );
                response.type("image/" + imageFormat.toLowerCase());
                MatrixToImageWriter.writeToStream(matrix, imageFormat, response.raw().getOutputStream());

            }
            return null;
        });
        post("/qr", (request, response) -> {

            MultipartConfigElement multipartConfigElement = new MultipartConfigElement("/tmp");
            request.raw().setAttribute("org.eclipse.jetty.multipartConfig", multipartConfigElement);
            Part file = request.raw().getPart("image"); //file is name of the upload form
            try {
                BufferedImage image = ImageIO.read(file.getInputStream());
                LuminanceSource source = new BufferedImageLuminanceSource(image);

                BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

                MultiFormatReader multiFormatReader = new MultiFormatReader();
                Result[] results;
                try {
                    results = new Result[]{multiFormatReader.decode(bitmap, finalHints)};
                    if (results == null || results.length < 1) {
                        halt(400, "Decoding failed!");
                    } else {
                        response.type("application/json");
                        return "{\"res\":\"" + results[0].getText() + "\"}";
                    }
                } catch (NotFoundException ignored) {
                    halt(400, "No barcode found!");
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                halt(500, "Error decoding.");
            }
            return null;
        });
    }

    private static Map<DecodeHintType, ?> buildHints() {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        List<BarcodeFormat> finalPossibleFormats = new ArrayList<BarcodeFormat>();
        finalPossibleFormats.addAll(Arrays.asList(
                BarcodeFormat.QR_CODE,
                BarcodeFormat.DATA_MATRIX
        ));
        hints.put(DecodeHintType.POSSIBLE_FORMATS, finalPossibleFormats);
        return hints;
    }//buildHints

}
