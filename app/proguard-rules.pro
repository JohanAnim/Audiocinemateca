# Add project specific ProGuard rules here.
# By default, the flags set here are appended to the flags specified in
# /Users/johan/AppData/Local/Android/sdk/tools/proguard/proguard-android-optimize.txt
# You can remove the line above and specify your own ProGuard rules here.

# Keep PlayerFragment
-keep class com.johang.audiocinemateca.presentation.player.PlayerFragment { *; }

# Keep its ViewModel (if used directly by the fragment and not just injected)
-keep class com.johang.audiocinemateca.presentation.player.PlayerViewModel { *; }

# Keep the generated Safe Args classes for PlayerFragment
-keep class com.johang.audiocinemateca.presentation.player.PlayerFragmentArgs { *; }

# Keep all fragments used in navigation graphs
-keep class * extends androidx.fragment.app.Fragment { *; }

# Keep all views used in layouts
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

# Keep custom attributes
-keep class * extends android.view.ViewGroup {
    <init>(android.content.Context, android.util.AttributeSet);
}
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# Evita que R8 elimine o modifique la clase abstracta o interfaz usada por Gson
-keepclassmembers class V2.f {
    *;
}
-keep class V2.f { *; }

# También puedes decirle a Gson que mantenga TODAS las clases que Gson necesita (más general):
-keep class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# Asegura que todas las clases usadas por Gson se conserven (versión completa):
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.stream.** { *; }
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# Retrofit
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-dontwarn retrofit2.Platform$Java8

# OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Hilt
-keep class dagger.hilt.android.internal.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$ViewComponentBuilder { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.flow.** { *; }

# Room
-keep class androidx.room.** { *; }

# Keep data model classes
-keep class com.johang.audiocinemateca.data.model.** { *; }
-keep class com.johang.audiocinemateca.data.remote.model.** { *; }

