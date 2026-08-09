plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.btreceivecall"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.btreceivecall"
        minSdk = 26 // Android 8.0 & above for maximum phone compatibility
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            enableV1Signing = true
            enableV2Signing = true
        }
        create("release") {
            enableV1Signing = true
            enableV2Signing = true
            if (project.hasProperty("MYAPP_RELEASE_STORE_FILE")) {
                storeFile = file(project.property("MYAPP_RELEASE_STORE_FILE") as String)
                storePassword = project.property("MYAPP_RELEASE_STORE_PASSWORD") as String
                keyAlias = project.property("MYAPP_RELEASE_KEY_ALIAS") as String
                keyPassword = project.property("MYAPP_RELEASE_KEY_PASSWORD") as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
