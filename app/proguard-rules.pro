# OkHttp and Firebase ship their own consumer rules; org.json is part of Android.
# Keep line numbers so release crash reports are readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Razorpay checkout (from Razorpay's integration guide).
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
-keepattributes *Annotation*
-dontwarn com.razorpay.**
-keep class com.razorpay.** { *; }
-optimizations !method/inlining/*
-keepclasseswithmembers class * {
    public void onPayment*(...);
}
