with open("tasks/ui-migration-plan.md", "r") as f:
    content = f.read()

content = content.replace(
    "- [ ] `TagsTechnicalFragment.java` (read-only technical specs layout) -> Compose",
    "- [x] `TagsTechnicalFragment.java` (read-only technical specs layout) -> Compose (`TagsTechnicalPage.kt`)"
)
content = content.replace(
    "- [ ] `TagsEditorFragment.java` (ID3 tag text fields and cover art picker) -> Compose",
    "- [x] `TagsEditorFragment.java` (ID3 tag text fields and cover art picker) -> Compose (`TagsEditorPage.kt`)"
)

with open("tasks/ui-migration-plan.md", "w") as f:
    f.write(content)
