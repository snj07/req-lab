plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    js(IR) {
        nodejs {
            testTask {
                useMocha {
                    timeout = "60000"
                }
            }
        }
    }

    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation(project(":editor-core"))
                implementation(npm("@codemirror/state",    "6.6.0"))
                implementation(npm("@codemirror/view",     "6.38.6"))
                implementation(npm("@codemirror/language", "6.11.3"))
                implementation(npm("@codemirror/commands", "6.8.0"))
                implementation(npm("@codemirror/lang-json",       "6.0.2"))
                implementation(npm("@codemirror/lang-xml",        "6.1.0"))
                implementation(npm("@codemirror/lang-html",       "6.4.9"))
                implementation(npm("@codemirror/lang-javascript",  "6.2.3"))
                implementation(npm("@codemirror/lint",            "6.9.0"))
                implementation(npm("@replit/codemirror-indentation-markers", "6.5.0"))
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}
