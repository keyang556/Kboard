# The JNI bridge is looked up by class and method name from native code.
-keep class io.github.keyang556.kboard.engine.ChewingNative {
    native <methods>;
}
