with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "r") as f:
    content = f.read()

import re
broken_pattern = r'    private void scrollToPosition\(int position\) \{\n        // TODO: Implement Compose LazyListState scrolling in ListInterop\n    \}\n.*?    private void doHideSearch\(\) \{'
fixed = '    private void scrollToPosition(int position) {\n        // TODO: Implement Compose LazyListState scrolling in ListInterop\n    }\n\n    private void doHideSearch() {'

content = re.sub(broken_pattern, fixed, content, flags=re.MULTILINE | re.DOTALL)

with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "w") as f:
    f.write(content)
