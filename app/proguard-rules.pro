# #23: R8 ProGuard rules for release.
#
# The libraries we depend on (LiveKit, WebRTC, OkHttp, Kotlin coroutines,
# Compose) all ship consumer ProGuard rules inside their AARs/JARs, so they
# need no host-side -keep. The few rules below cover Syncle-specific
# reflection / serialization surfaces.

# Our model enums are read by name from LiveKit data packets / attributes.
# Don't let R8 rename or strip the enum constants.
-keepclassmembers enum com.example.syncle.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# We don't use SerializedName / Gson; if that changes, add a -keep here.
