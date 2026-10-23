-keep class com.fuhbe.ytdlpg.simple.Media { *; }
-keep class com.fuhbe.ytdlpg.simple.Choice { *; }
-keep class com.fuhbe.ytdlpg.simple.QueueJob { *; }
-keep class com.fuhbe.ytdlpg.simple.SavedFile { *; }
-keep class com.fuhbe.ytdlpg.simple.QueueWorker { public <init>(...); }
-keepattributes Signature,*Annotation*
# Commons Compress 1.12 registers ZIP extra-field implementations reflectively.
# Without the upstream rule, R8 makes them abstract and removes constructors;
# ExtraFieldUtils.<clinit> then throws before Python/FFmpeg can initialize.
-keep class org.apache.commons.compress.archivers.zip.** { *; }
-keepattributes SourceFile,LineNumberTable

-keep class com.fuhbe.ytdlpg.simple.ItemRecord { *; }
