with open("tasks/ui-migration-plan.md", "r") as f:
    content = f.read()

content = content.replace(
    "- [ ] Migrate `actionFormat()` (Encode/Convert Audio Format Dialog)",
    "- [x] Migrate `actionFormat()` (Encode/Convert Audio Format Dialog)"
)

with open("tasks/ui-migration-plan.md", "w") as f:
    f.write(content)
