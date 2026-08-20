import re
with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "r") as f:
    content = f.read()

click_pattern = r'^[ \t]*MusicTagAdapter\.OnListItemClick onListItemClick = \(view, position\) -> \{.*?\n[ \t]*\};\n'
content = re.sub(click_pattern, '', content, flags=re.MULTILINE | re.DOTALL)

with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "w") as f:
    f.write(content)
