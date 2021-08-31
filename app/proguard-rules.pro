# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in D:\Android\sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-dontobfuscate

# Required when -dontobfuscate is set, see http://stackoverflow.com/a/7587680
-optimizations !code/allocation/variable

# Make sure that getActiveXposedVersion() is actually called
# (instead of always using it's seemingly static value -1)
-optimizations !method/propagation/returnvalue,!method/inlining/*

# See https://code.google.com/p/android/issues/detail?id=58508
-keep class android.support.v7.widget.SearchView { *; }

-dontwarn com.squareup.okhttp.**
