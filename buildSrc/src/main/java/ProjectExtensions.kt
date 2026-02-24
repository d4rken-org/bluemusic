import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import java.util.Properties

val Project.projectConfig: ProjectConfig
    get() = extensions.findByType(ProjectConfig::class.java)!!

fun LibraryExtension.setupLibraryDefaults(projectConfig: ProjectConfig) {
    if (projectConfig.compileSdkPreview != null) {
        compileSdkPreview = projectConfig.compileSdkPreview
    } else {
        compileSdk = projectConfig.compileSdk
    }

    defaultConfig {
        minSdk = projectConfig.minSdk
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // For modules that depend on app-common which has flavors
        missingDimensionStrategy("version", "foss")
    }
}

fun LibraryExtension.setupModuleBuildTypes() {
    buildTypes {
        debug {
            consumerProguardFiles("consumer-rules.pro")
        }
        create("beta") {
            consumerProguardFiles("consumer-rules.pro")
        }
        release {
            consumerProguardFiles("consumer-rules.pro")
        }
    }
}

fun Project.setupKotlinOptions() {
    tasks.withType(KotlinCompile::class.java) {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=kotlin.ExperimentalStdlibApi",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-opt-in=kotlinx.coroutines.FlowPreview",
                "-opt-in=kotlin.time.ExperimentalTime",
                "-opt-in=kotlin.RequiresOptIn",
                "-jvm-default=enable",
                "-Xcontext-parameters",
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            )
        }
    }
}

fun LibraryExtension.setupCompileOptions() {
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

fun com.android.build.api.dsl.SigningConfig.setupCredentials(
    signingPropsPath: File? = null
) {

    val keyStoreFromEnv = System.getenv("STORE_PATH")?.let { File(it) }

    if (keyStoreFromEnv?.exists() == true) {
        println("Using signing data from environment variables.")

        val missingVars = listOf("STORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
            .filter { System.getenv(it).isNullOrBlank() }
        if (missingVars.isNotEmpty()) {
            println("WARNING: STORE_PATH is set but missing env vars: ${missingVars.joinToString()}")
        }

        storeFile = keyStoreFromEnv
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS")
        keyPassword = System.getenv("KEY_PASSWORD")
    } else {
        println("Trying signing data from properties file: $signingPropsPath")
        val props = Properties().apply {
            signingPropsPath?.takeIf { it.canRead() }?.let { file ->
                file.inputStream().use { stream -> load(stream) }
            }
        }

        val keyStorePath = props.getProperty("release.storePath")?.let { File(it) }

        if (keyStorePath?.exists() == true) {
            println("Using signing data from properties file: $signingPropsPath")
            storeFile = keyStorePath
            storePassword = props.getProperty("release.storePassword")
            keyAlias = props.getProperty("release.keyAlias")
            keyPassword = props.getProperty("release.keyPassword")
        } else {
            println("WARNING: No valid signing configuration found (no env vars or properties file)")
        }
    }
}

fun Test.setupTestLogging() {
    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED,
//            TestLogEvent.STANDARD_OUT,
        )
        exceptionFormat = TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true

        addTestListener(object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) {}
            override fun beforeTest(testDescriptor: TestDescriptor) {}
            override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {}
            override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                if (suite.parent != null) {
                    val messages = """
                        ------------------------------------------------------------------------------------------------
                        | ${result.resultType} ${result.testCount} tests: ${result.successfulTestCount} passed, ${result.failedTestCount} failed, ${result.skippedTestCount} skipped)
                        ------------------------------------------------------------------------------------------------
                        
                    """.trimIndent()
                    println(messages)
                }
            }
        })
    }
}
