with open("tasks/ui-migration-plan.md", "r") as f:
    content = f.read()

content = content.replace(
    "- [x] Migrate Media Server Tab to Compose (`MediaServerPage.kt`).",
    "- [x] Migrate Media Server Tab to Compose (`MediaServerPage.kt`).\n  - [x] Migrate Queue Tab to Compose (`QueuePage.kt`)."
)

with open("tasks/ui-migration-plan.md", "w") as f:
    f.write(content)
