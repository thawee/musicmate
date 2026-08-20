import re

with open("app/src/main/java/apincer/android/mmate/ui/view/AudioHubBottomSheet.java", "r") as f:
    content = f.read()

sig_start = content.find("private void populateSignalPathWidget(@NonNull View view, @Nullable Track track) {")
if sig_start != -1:
    brace_count = 0
    sig_end = -1
    for i in range(sig_start, len(content)):
        if content[i] == '{':
            brace_count += 1
        elif content[i] == '}':
            brace_count -= 1
            if brace_count == 0:
                sig_end = i + 1
                break
                
    new_sig = """private void populateSignalPathWidget(@NonNull View view, @Nullable Track track) {
        // Migrated to compose, this could be handled by state in the future.
    }"""
    content = content[:sig_start] + new_sig + content[sig_end:]

with open("app/src/main/java/apincer/android/mmate/ui/view/AudioHubBottomSheet.java", "w") as f:
    f.write(content)
