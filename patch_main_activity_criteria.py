import re

with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "r") as f:
    content = f.read()

# Add currentCriteria field
if "private SearchCriteria currentCriteria;" not in content:
    content = content.replace("private MusicTagAdapter adapter;", "private MusicTagAdapter adapter;\n    private SearchCriteria currentCriteria = new SearchCriteria(SearchCriteria.TYPE.LIBRARY);")

# Replace adapter.getCriteria() with currentCriteria
content = content.replace("adapter.getCriteria()", "currentCriteria")

# Replace adapter.search("") with something else? 
# Wait, adapter.search() updates criteria and filters. Let's see what it does.
