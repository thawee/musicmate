with open("gradle/libs.versions.toml", "r") as f:
    content = f.read()

content = content.replace('espressoCore = "3.7.0"\n', 'espressoCore = "3.7.0"\ncomposeBom = "2024.12.01"\n')

compose_deps = """
# Jetpack Compose
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-ui-graphics = { module = "androidx.compose.ui:ui-graphics" }
androidx-compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version = "1.13.0" }
"""
content = content.replace('# AndroidX Lifecycle & WorkManager', compose_deps + '\n# AndroidX Lifecycle & WorkManager')

content = content.replace('kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }', 
    'kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }\nkotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }')

with open("gradle/libs.versions.toml", "w") as f:
    f.write(content)
