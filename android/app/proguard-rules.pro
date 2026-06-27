# kotlinx.serialization — keep generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class app.meisaku.reader.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# kuromoji — 词典通过 classloader 读取 jar 内资源，类与资源不可裁剪
-keep class com.atilika.kuromoji.** { *; }
-dontwarn com.atilika.kuromoji.**
