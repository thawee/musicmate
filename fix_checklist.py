with open("tasks/ui-migration-plan.md", "r") as f:
    content = f.read()

content = content.replace("- [ ] **`TagsTechnicalFragment.java`:**", "- [x] **`TagsTechnicalFragment.java`:**")
content = content.replace("- [ ] **`TagsEditorFragment.java`:**", "- [x] **`TagsEditorFragment.java`:**")

with open("tasks/ui-migration-plan.md", "w") as f:
    f.write(content)
