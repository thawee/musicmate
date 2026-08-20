with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "r") as f:
    content = f.read()

import re
scroll_pattern = r'^[ \t]*private void scrollToPosition\(int position\) \{.*?\n[ \t]*\}\n'
content = re.sub(scroll_pattern, '    private void scrollToPosition(int position) {\n        // TODO: Implement Compose LazyListState scrolling in ListInterop\n    }\n', content, flags=re.MULTILINE | re.DOTALL)

with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "w") as f:
    f.write(content)
