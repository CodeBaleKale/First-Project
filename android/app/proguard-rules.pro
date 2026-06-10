# kotlinx.serialization — keep generated serializers if minification is enabled later
-keepclassmembers class com.snapcal.app.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.snapcal.app.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
