import re

with open("app/src/main/java/apincer/android/mmate/ui/view/AudioHubBottomSheet.java", "r") as f:
    content = f.read()

# Fix setupEngineSwitcher
setup_engine_start = content.find("private void setupEngineSwitcher(View view) {")
if setup_engine_start != -1:
    brace_count = 0
    setup_engine_end = -1
    for i in range(setup_engine_start, len(content)):
        if content[i] == '{':
            brace_count += 1
        elif content[i] == '}':
            brace_count -= 1
            if brace_count == 0:
                setup_engine_end = i + 1
                break
    if setup_engine_end != -1:
        content = content[:setup_engine_start] + "private void setupEngineSwitcher(View view) { /* Handled by Compose */ }" + content[setup_engine_end:]

# Fix updateServerUI
content = content.replace("SERVER_STATUS_ONLINE)", "SERVER_STATUS_ONLINE_PREFIX)")
content = content.replace("getServerURL(getContext())", "getServerLocationUrl()")

# Also, there's a call to setupEngineSwitcher in setupMediaServerTab or flattenPage? Let's make sure flattenPage doesn't crash on null.
# Since it's all handled by compose, we don't even need to call setupEngineSwitcher if we wiped it.

with open("app/src/main/java/apincer/android/mmate/ui/view/AudioHubBottomSheet.java", "w") as f:
    f.write(content)
print("Patched AudioHubBottomSheet.java round 2")
