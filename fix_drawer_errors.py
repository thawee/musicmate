import re
with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "r") as f:
    content = f.read()

# Fix onResume
content = re.sub(r'        if \(mResideMenu\.isOpened\(\)\) \{\n            mResideMenu\.closeMenu\(\);\n        \}\n', '', content)

# Fix BackPressedCallback
content = re.sub(r'            if \(mResideMenu\.isOpened\(\)\) \{\n                mResideMenu\.closeMenu\(\);\n                return;\n            \}\n', '', content)

# Fix @Override on handleNavigationItemClick
content = content.replace("    @Override\n\n    public void handleNavigationItemClick(int itemId) {", "    public void handleNavigationItemClick(int itemId) {")

# Import ContextMenu
content = content.replace("import android.view.MenuItem;", "import android.view.MenuItem;\nimport android.view.ContextMenu;")

with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "w") as f:
    f.write(content)
