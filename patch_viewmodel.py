with open("app/src/main/java/apincer/android/mmate/ui/viewmodel/MainViewModel.java", "r") as f:
    content = f.read()
content = content.replace("public void search(MusicTagAdapter adapter, String query) {\n        adapter.search(query);\n        loadMusicItems(adapter.getCriteria());\n    }", "public void search(SearchCriteria criteria, String query) {\n        if(query == null || query.isEmpty()) criteria.resetSearch(); else criteria.searchFor(query);\n        loadMusicItems(criteria);\n    }")
content = content.replace("import apincer.android.mmate.ui.MusicTagAdapter;", "")
with open("app/src/main/java/apincer/android/mmate/ui/viewmodel/MainViewModel.java", "w") as f:
    f.write(content)
