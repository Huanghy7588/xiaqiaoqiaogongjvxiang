# Add project specific ProGuard rules here.

# 应用代码全部保留（仅裁剪未使用的 androidx / material 库代码），避免反射或回调被误删
-keep class com.huanghy7588.** { *; }

# 保留行号便于排查崩溃
-keepattributes SourceFile,LineNumberTable
