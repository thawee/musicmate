import re

with open("app/src/main/java/apincer/android/mmate/ui/view/AudioHubBottomSheet.java", "r") as f:
    content = f.read()

# Remove updateQueueHeader
header_start = content.find("private void updateQueueHeader(@NonNull View root, List<Track> queue) {")
if header_start != -1:
    brace_count = 0
    header_end = -1
    for i in range(header_start, len(content)):
        if content[i] == '{':
            brace_count += 1
        elif content[i] == '}':
            brace_count -= 1
            if brace_count == 0:
                header_end = i + 1
                break
    content = content[:header_start] + content[header_end:]

with open("app/src/main/java/apincer/android/mmate/ui/view/AudioHubBottomSheet.java", "w") as f:
    f.write(content)
