import re

with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "r") as f:
    content = f.read()

# Add rootView as a class field
content = content.replace("private androidx.compose.ui.platform.ComposeView composeListView;", 
                          "private androidx.compose.ui.platform.ComposeView composeListView;\n    private View rootView;")

# Change setContentView
content = content.replace("setContentView(R.layout.activity_main);", 
                          "rootView = getLayoutInflater().inflate(R.layout.activity_main, null);\n        setContentView(apincer.android.mmate.ui.compose.DrawerInterop.getComposeView(this, rootView));")

# Change all occurrences of findViewById( to rootView.findViewById(
# But avoid getWindow().findViewById( or view.findViewById( (e.g. storageView.findViewById)
content = re.sub(r'(?<!\.)\bfindViewById\(', 'rootView.findViewById(', content)

# Remove ResideMenu
content = re.sub(r'import apincer\.android\.residemenu\.ResideMenu;\n', '', content)
content = re.sub(r'    private ResideMenu mResideMenu;\n', '', content)
# We completely wipe setupResideMenus
content = re.sub(r'    private void setupResideMenus\(\) \{.*?\n    \}\n', '', content, flags=re.MULTILINE|re.DOTALL)
# Instead of setupResideMenus(), we just delete the call
content = content.replace("setupResideMenus();", "")

# In doShowLeftMenus and doShowRightMenus, we just call DrawerInterop.openDrawer()
content = re.sub(r'    private void doShowLeftMenus\(\) \{.*?\n    \}', 
                 '    private void doShowLeftMenus() {\n        apincer.android.mmate.ui.compose.DrawerInterop.openDrawer();\n    }', 
                 content, flags=re.MULTILINE|re.DOTALL)
content = re.sub(r'    private void doShowRightMenus\(\) \{.*?\n    \}', 
                 '    private void doShowRightMenus() {\n        apincer.android.mmate.ui.compose.DrawerInterop.openDrawer();\n    }', 
                 content, flags=re.MULTILINE|re.DOTALL)

# Handle back button closing menu (we remove it or rely on Compose handling it natively)
# In onBackPressed() 
content = re.sub(r'            if \(mResideMenu != null && mResideMenu\.isOpened\(\)\) \{\n                mResideMenu\.closeMenu\(\);\n                return;\n            \}', '', content)

# Add handleNavigationItemClick
handle_nav = """
    public void handleNavigationItemClick(int itemId) {
        // Create a dummy MenuItem
        MenuItem item = new MenuItem() {
            @Override public int getItemId() { return itemId; }
            @Override public int getGroupId() { return 0; }
            @Override public int getOrder() { return 0; }
            @Override public MenuItem setTitle(CharSequence title) { return this; }
            @Override public MenuItem setTitle(int title) { return this; }
            @Override public CharSequence getTitle() { return null; }
            @Override public MenuItem setTitleCondensed(CharSequence title) { return this; }
            @Override public CharSequence getTitleCondensed() { return null; }
            @Override public MenuItem setIcon(Drawable icon) { return this; }
            @Override public MenuItem setIcon(int iconRes) { return this; }
            @Override public Drawable getIcon() { return null; }
            @Override public MenuItem setIntent(Intent intent) { return this; }
            @Override public Intent getIntent() { return null; }
            @Override public MenuItem setShortcut(char numericChar, char alphaChar) { return this; }
            @Override public MenuItem setShortcut(char numericChar, char alphaChar, int numericModifiers, int alphaModifiers) { return this; }
            @Override public MenuItem setNumericShortcut(char numericChar) { return this; }
            @Override public MenuItem setNumericShortcut(char numericChar, int numericModifiers) { return this; }
            @Override public char getNumericShortcut() { return 0; }
            @Override public int getNumericModifiers() { return 0; }
            @Override public MenuItem setAlphabeticShortcut(char alphaChar) { return this; }
            @Override public MenuItem setAlphabeticShortcut(char alphaChar, int alphaModifiers) { return this; }
            @Override public char getAlphabeticShortcut() { return 0; }
            @Override public int getAlphabeticModifiers() { return 0; }
            @Override public MenuItem setCheckable(boolean checkable) { return this; }
            @Override public boolean isCheckable() { return false; }
            @Override public MenuItem setChecked(boolean checked) { return this; }
            @Override public boolean isChecked() { return false; }
            @Override public MenuItem setVisible(boolean visible) { return this; }
            @Override public boolean isVisible() { return true; }
            @Override public MenuItem setEnabled(boolean enabled) { return this; }
            @Override public boolean isEnabled() { return true; }
            @Override public boolean hasSubMenu() { return false; }
            @Override public android.view.SubMenu getSubMenu() { return null; }
            @Override public MenuItem setOnMenuItemClickListener(OnMenuItemClickListener menuItemClickListener) { return this; }
            @Override public ContextMenu.ContextMenuInfo getMenuInfo() { return null; }
            @Override public void setShowAsAction(int actionEnum) {}
            @Override public MenuItem setShowAsActionFlags(int actionEnum) { return this; }
            @Override public MenuItem setActionView(View view) { return this; }
            @Override public MenuItem setActionView(int resId) { return this; }
            @Override public View getActionView() { return null; }
            @Override public MenuItem setActionProvider(android.view.ActionProvider actionProvider) { return this; }
            @Override public android.view.ActionProvider getActionProvider() { return null; }
            @Override public boolean expandActionView() { return false; }
            @Override public boolean collapseActionView() { return false; }
            @Override public boolean isActionViewExpanded() { return false; }
            @Override public MenuItem setOnActionExpandListener(OnActionExpandListener listener) { return this; }
        };
        onOptionsItemSelected(item);
    }
"""
content = content.replace("    public boolean onOptionsItemSelected(MenuItem item) {", handle_nav + "\n    public boolean onOptionsItemSelected(MenuItem item) {")


with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "w") as f:
    f.write(content)
