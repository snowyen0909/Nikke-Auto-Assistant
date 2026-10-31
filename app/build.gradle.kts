plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "com.example.nikkeauto"
 compileSdk = 35
 defaultConfig { applicationId = "com.example.nikkeauto"; minSdk = 29; targetSdk = 35; versionCode = 3; versionName = "0.3" }
 flavorDimensions += "interface"
 productFlavors {
  create("modern") {
   dimension = "interface"
   applicationIdSuffix = ".modern"
   resValue("string","app_name","NIKKE Automation")
  }
  create("classic") {
   dimension = "interface"
   applicationIdSuffix = ".classic"
   resValue("string","app_name","NIKKE Assistant Classic")
  }
 }
 buildFeatures { buildConfig = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation("com.google.mlkit:text-recognition-chinese:16.0.1") }