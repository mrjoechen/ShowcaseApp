# Preserve the generated OpenCV Java/JNI bridge when consumers enable shrinking.
-keep class org.opencv.objdetect.FaceDetectorYN { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
