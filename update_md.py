with open("tasks/ui-migration-plan.md", "r") as f:
    content = f.read()

content = content.replace(
    "- [ ] Migrate `actionTrash()` (Move to Trash Dialog)",
    "- [x] Migrate `actionTrash()` (Move to Trash Dialog)"
).replace(
    "- [ ] Migrate `actionMove()` (Move to Music Dialog)",
    "- [x] Migrate `actionMove()` (Move to Music Dialog)"
).replace(
    "- [ ] **`AudioHubBottomSheet.java`**",
    "- [x] **`AudioHubBottomSheet.java` (In Progress)**\n  - [x] Migrate Media Server Tab to Compose (`MediaServerPage.kt`)."
)

with open("tasks/ui-migration-plan.md", "w") as f:
    f.write(content)

with open("tasks/todo.md", "r") as f:
    content = f.read()
    
content = content.replace(
    "- [ ] Phase 2: Action Dialogs & Bottom Sheets.",
    "- [x] Phase 2: Action Dialogs & Bottom Sheets (In Progress - Trash, Move, and Media Server Tab migrated)."
)
with open("tasks/todo.md", "w") as f:
    f.write(content)
