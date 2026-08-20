import re

with open("app/src/main/java/apincer/android/mmate/ui/compose/AudioBadges.kt", "r") as f:
    content = f.read()

content = content.replace("import androidx.compose.material.icons.Icons\n", "")
content = content.replace("import androidx.compose.material.icons.filled.FiberNew\n", "")
content = re.sub(r"""        Icon\(\s*imageVector = Icons\.Default\.FiberNew,\s*contentDescription = "New",\s*tint = textColor,\s*modifier = Modifier\.size\(14\.dp\)\s*\)\s*Spacer\(modifier = Modifier\.width\(2\.dp\)\)""", "", content, flags=re.MULTILINE)

with open("app/src/main/java/apincer/android/mmate/ui/compose/AudioBadges.kt", "w") as f:
    f.write(content)
