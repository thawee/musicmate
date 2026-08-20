with open("app/build.gradle", "r") as f:
    content = f.read()

content = content.replace(
    'alias libs.plugins.dagger.hilt', 
    'alias libs.plugins.dagger.hilt\n    alias libs.plugins.kotlin.compose'
)

content = content.replace(
    'dataBinding = false', 
    'dataBinding = false\n        compose = true'
)

deps_injection = """
    // Jetpack Compose
    implementation platform(libs.compose.bom)
    implementation libs.androidx.compose.ui
    implementation libs.androidx.compose.ui.graphics
    implementation libs.androidx.compose.ui.tooling.preview
    implementation libs.androidx.compose.material3
    implementation libs.androidx.activity.compose
    debugImplementation libs.androidx.compose.ui.tooling
"""

content = content.replace(
    '// Media & Playback Engine', 
    deps_injection + '\n    // Media & Playback Engine'
)

with open("app/build.gradle", "w") as f:
    f.write(content)
