import re
with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "r") as f:
    content = f.read()

content = content.replace("public class MainActivity extends AppCompatActivity {\n    private static final String TAG = \"MainActivity\";", "public class MainActivity extends AppCompatActivity {\n    private static final String TAG = \"MainActivity\";\n    private androidx.compose.ui.platform.ComposeView composeListView;")

# Remove the duplicate local declaration
content = content.replace("androidx.compose.ui.platform.ComposeView composeListView = findViewById(R.id.compose_list_view);\n        apincer.android.mmate.ui.compose.ListInterop.setMusicListContent(composeListView, this);", "")

with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "w") as f:
    f.write(content)
