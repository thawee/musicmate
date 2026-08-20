import re

with open("app/src/main/java/apincer/android/mmate/ui/view/AudioHubBottomSheet.java", "r") as f:
    content = f.read()

def wipe_method(method_decl):
    global content
    idx = content.find(method_decl)
    if idx != -1:
        brace_count = 0
        end_idx = -1
        for i in range(idx, len(content)):
            if content[i] == '{':
                brace_count += 1
            elif content[i] == '}':
                brace_count -= 1
                if brace_count == 0:
                    end_idx = i + 1
                    break
        content = content[:idx] + method_decl.split("(")[0] + "() { /* Migrated to Compose */ }" + content[end_idx:]

wipe_method("private void showGestureOverlayIcon(View parentView, int iconResId) {")
wipe_method("private void animateGestureFeedback(View albumArt, float translationX) {")

# For flattenPage, just wipe out the Now Playing part
flat_part_old = """            if (isNowPlaying) {
                // For Now Playing page: hide drag handle, inner header, and inner queue section
                for (int i = 0; i < vg.getChildCount(); i++) {
                    View child = vg.getChildAt(i);
                    if (child.getId() != R.id.sheet_now_playing_card) {
                        child.setVisibility(GONE);
                    }
                }
            } else if"""
flat_part_new = "            if"
content = content.replace(flat_part_old, flat_part_new)

# Also check if it's there
if flat_part_old not in content:
    content = content.replace("child.getId() != R.id.sheet_now_playing_card", "false")

with open("app/src/main/java/apincer/android/mmate/ui/view/AudioHubBottomSheet.java", "w") as f:
    f.write(content)
