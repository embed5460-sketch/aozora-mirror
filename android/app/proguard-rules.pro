# kotlinx.serialization — keep generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class app.meisaku.reader.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
