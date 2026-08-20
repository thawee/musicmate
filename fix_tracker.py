import re

with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "r") as f:
    content = f.read()

# Replace imports
content = re.sub(r'import androidx\.recyclerview\.selection\.SelectionTracker;', 'import apincer.android.mmate.ui.MySelectionTracker;', content)

# Replace fields
content = re.sub(r'private SelectionTracker<Long> mTracker;', 'private MySelectionTracker mTracker;', content)

# Replace builder
content = re.sub(r'mTracker = new SelectionTracker\.Builder<\>\(.*?\.build\(\);', 'mTracker = new MySelectionTracker();', content, flags=re.DOTALL)

# Replace observer
content = re.sub(r'SelectionTracker\.SelectionObserver<Long> observer = new SelectionTracker\.SelectionObserver<\>\(\) \{.*?\};\n.*mTracker\.addObserver\(observer\);', '''MySelectionTracker.SelectionObserver observer = new MySelectionTracker.SelectionObserver() {
                @Override
                public void onSelectionChanged() {
                    int count = mTracker.getSelection().size();
                    selections.clear();
                    if (count > 0) {
                        mTracker.getSelection().forEach(item -> {
                            Track tag = adapter.getMusicTag(item.intValue());
                            if (tag != null) {
                                selections.add(tag);
                            }
                        });
                        if (actionMode == null) {
                            actionMode = startSupportActionMode(actionModeCallback);
                        }
                    } else if (actionMode != null) {
                        actionMode.finish();
                        actionMode = null;
                    }
                    if (actionMode != null) {
                        actionMode.setTitle(count + " Selected");
                        //actionMode.invalidate();
                    }
                }
            };
            mTracker.setObserver(observer);''', content, flags=re.DOTALL)

# Remove injectTracker
content = re.sub(r'adapter\.injectTracker\(mTracker\);', '', content)

# Remove the predicate class properly by counting braces
lines = content.split('\n')
out_lines = []
skip = False
brace_count = 0
for line in lines:
    if "private class MusicTrackSelectionPredicate" in line:
        skip = True
        brace_count = line.count('{') - line.count('}')
        continue
    
    if skip:
        brace_count += line.count('{') - line.count('}')
        if brace_count == 0:
            skip = False
        continue
        
    out_lines.append(line)

with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "w") as f:
    f.write("\n".join(out_lines))
