plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.example.nikkeauto"; compileSdk = 35
 defaultConfig { applicationId = "com.example.nikkeauto"; minSdk = 29; targetSdk = 35; versionCode = 1; versionName = "0.1" } }
dependencies { implementation("com.google.mlkit:text-recognition-chinese:16.0.1"); implementation("com.google.android.gms:play-services-tasks:18.2.0") }