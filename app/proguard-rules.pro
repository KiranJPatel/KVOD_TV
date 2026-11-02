-keepattributes *Annotation*, InnerClasses, Signature
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
# Source secrets are (de)serialized with kotlinx.serialization; consumer rules cover the rest.
-keep,includedescriptorclasses class com.iptv.firetv.core.security.SourceSecrets$$serializer { *; }
-keepclassmembers class com.iptv.firetv.core.security.SourceSecrets { *** Companion; }
