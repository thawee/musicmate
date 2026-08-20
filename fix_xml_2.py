import re
with open("app/src/main/res/layout/activity_main.xml", "r") as f:
    content = f.read()

pattern = r'<FrameLayout\s*android:layout_width="match_parent"\s*android:layout_height="match_parent"\s*>\s*<androidx.compose.ui.platform.ComposeView\s*android:id="@+id/compose_list_view"\s*android:layout_width="match_parent"\s*android:layout_height="match_parent"\s*/>\s*</FrameLayout>'

fixed = '<androidx.compose.ui.platform.ComposeView\n                        android:id="@+id/compose_list_view"\n                        android:layout_width="match_parent"\n                        android:layout_height="match_parent"\n                        />'

content = re.sub(pattern, fixed, content, flags=re.MULTILINE | re.DOTALL)

with open("app/src/main/res/layout/activity_main.xml", "w") as f:
    f.write(content)
