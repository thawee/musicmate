with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "r") as f:
    content = f.read()

import re
broken_pattern = r'    private boolean isSelectionBlocked\(\) \{\n        return false;\n                \|\| \(SystemClock\.elapsedRealtime\(\) - lastScrollEventTime < 500\)\n                \|\| isScrollStoppingTouch;\n    \}'
fixed = '    private boolean isSelectionBlocked() {\n        return false;\n    }'

content = re.sub(broken_pattern, fixed, content, flags=re.MULTILINE)

with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "w") as f:
    f.write(content)
