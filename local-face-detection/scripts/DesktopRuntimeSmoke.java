import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opencv.core.Core;
import org.opencv.core.CvException;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.dnn.Dnn;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.FaceDetectorYN;

/** Real JNI/codec/DNN smoke, compiled against only the common JAR by verify_desktop.py. */
public final class DesktopRuntimeSmoke {
    private static final float SCORE_THRESHOLD = 0.6f;
    private static final float NMS_THRESHOLD = 0.3f;
    private static final int TOP_K = 5000;
    private static final int MAX_LONGEST = 640;

    private enum Result { FACE_DETECTED, NO_FACE, INDETERMINATE }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void verifyRuntime() {
        check(Runtime.version().feature() == 17, "Smoke requires JDK 17");
        check(Core.VERSION.equals("4.12.0"), "Wrong Java binding version: " + Core.VERSION);
        check(Core.getVersionString().equals("4.12.0"), "Wrong native version: " + Core.getVersionString());
        String info = Core.getBuildInformation();
        Matcher built = Pattern.compile("(?m)^\\s*To be built:\\s*([^\\r\\n]+)").matcher(info);
        check(built.find(), "Missing compiled module list\n" + info);
        Set<String> modules = new TreeSet<>(Arrays.asList(built.group(1).trim().split("\\s+")));
        check(modules.equals(Set.of("core", "imgproc", "imgcodecs", "dnn", "objdetect",
                "java", "calib3d", "features2d", "flann")), "Unexpected native modules: " + modules);
        Core.setNumThreads(1);
        System.out.println("OpenCV " + Core.getVersionString() + "; modules=" + modules);
    }

    private static FaceDetectorYN createDetector(byte[] modelBytes) {
        MatOfByte model = new MatOfByte(modelBytes);
        MatOfByte config = new MatOfByte();
        try {
            // Exercise the buffer overload used by the app, including its JNI entry
            // point and ONNX importer. No model filename or external Java loader.
            return FaceDetectorYN.create("onnx", model, config, new Size(320, 320),
                    SCORE_THRESHOLD, NMS_THRESHOLD, TOP_K,
                    Dnn.DNN_BACKEND_OPENCV, Dnn.DNN_TARGET_CPU);
        } finally {
            model.release();
            config.release();
        }
    }

    private static Result inspect(FaceDetectorYN detector, byte[] encoded, String label) {
        if (encoded.length == 0) {
            return Result.INDETERMINATE;
        }
        MatOfByte buffer = new MatOfByte(encoded);
        Mat decoded = null;
        Mat resized = new Mat();
        Mat faces = new Mat();
        try {
            try {
                decoded = Imgcodecs.imdecode(buffer, Imgcodecs.IMREAD_COLOR | Imgcodecs.IMREAD_IGNORE_ORIENTATION);
            } catch (CvException invalidImage) {
                return Result.INDETERMINATE;
            }
            if (decoded.empty()) {
                return Result.INDETERMINATE;
            }
            check(decoded.channels() == 3, label + ": expected BGR input");
            Mat input = decoded;
            int longest = Math.max(decoded.cols(), decoded.rows());
            if (longest > MAX_LONGEST) {
                double scale = (double) MAX_LONGEST / longest;
                Size size = new Size(Math.max(1, Math.round(decoded.cols() * scale)),
                        Math.max(1, Math.round(decoded.rows() * scale)));
                Imgproc.resize(decoded, resized, size, 0, 0, Imgproc.INTER_LINEAR);
                input = resized;
            }
            check(Math.max(input.cols(), input.rows()) <= MAX_LONGEST, label + ": resize limit exceeded");
            detector.setInputSize(input.size());
            detector.detect(input, faces);
            System.out.printf("%s: %dx%d -> %dx%d; detections=%d%n", label,
                    decoded.cols(), decoded.rows(), input.cols(), input.rows(), faces.rows());
            if (faces.empty()) {
                return Result.NO_FACE;
            }
            check(faces.cols() == 15, label + ": unexpected YuNet output columns: " + faces.cols());
            for (int index = 0; index < faces.rows(); index++) {
                float[] row = new float[15];
                check(faces.get(index, 0, row) > 0, label + ": unreadable detection");
                for (float value : row) {
                    check(Float.isFinite(value), label + ": non-finite detection");
                }
                check(row[14] >= SCORE_THRESHOLD && row[14] <= 1.0f,
                        label + ": invalid confidence: " + row[14]);
                if (row[2] > 0 && row[3] > 0
                        && Math.min(input.cols(), row[0] + row[2]) > Math.max(0, row[0])
                        && Math.min(input.rows(), row[1] + row[3]) > Math.max(0, row[1])) {
                    return Result.FACE_DETECTED;
                }
            }
            return Result.NO_FACE;
        } finally {
            buffer.release();
            if (decoded != null) {
                decoded.release();
            }
            resized.release();
            faces.release();
        }
    }

    private static byte[] enlargedPng(byte[] encoded) {
        MatOfByte buffer = new MatOfByte(encoded);
        Mat decoded = null;
        Mat enlarged = new Mat();
        MatOfByte output = new MatOfByte();
        try {
            decoded = Imgcodecs.imdecode(buffer, Imgcodecs.IMREAD_COLOR | Imgcodecs.IMREAD_IGNORE_ORIENTATION);
            check(!decoded.empty(), "Cannot decode resize fixture");
            Imgproc.resize(decoded, enlarged, new Size(1024, 1024), 0, 0, Imgproc.INTER_LINEAR);
            check(Imgcodecs.imencode(".png", enlarged, output), "Cannot encode enlarged PNG fixture");
            return output.toArray();
        } finally {
            buffer.release();
            if (decoded != null) {
                decoded.release();
            }
            enlarged.release();
            output.release();
        }
    }

    private static void expect(FaceDetectorYN detector, byte[] bytes, Result expected, String label) {
        Result actual = inspect(detector, bytes, label);
        check(actual == expected, label + ": expected " + expected + ", got " + actual);
        System.out.println(label + ": " + actual);
    }

    public static void main(String[] args) throws Exception {
        check(args.length == 5, "Usage: DesktopRuntimeSmoke <native> <model> <lena> <blank.png> <blank.jpg>");
        System.load(Path.of(args[0]).toAbsolutePath().toString());
        verifyRuntime();
        FaceDetectorYN detector = createDetector(Files.readAllBytes(Path.of(args[1])));
        check(detector != null, "YuNet buffer factory returned null");
        byte[] lena = Files.readAllBytes(Path.of(args[2]));
        expect(detector, lena, Result.FACE_DETECTED, "Lena JPEG");
        expect(detector, Files.readAllBytes(Path.of(args[3])), Result.NO_FACE, "Blank PNG");
        expect(detector, Files.readAllBytes(Path.of(args[4])), Result.NO_FACE, "Blank JPEG");
        expect(detector, enlargedPng(lena), Result.FACE_DETECTED, "Lena PNG resized to longest 640");
        expect(detector, new byte[] {1, 2, 3, 4, 5}, Result.INDETERMINATE, "Invalid bytes");
        expect(detector, new byte[0], Result.INDETERMINATE, "Empty bytes");
        expect(detector, lena, Result.FACE_DETECTED, "Lena after invalid input");
        // FaceDetectorYN exposes no close/release in these generated bindings.
        // Its native owner lives until this short-lived JVM exits; every Mat is released.
        System.out.println("Desktop runtime smoke passed");
    }
}
