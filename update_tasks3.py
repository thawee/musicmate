with open("tasks/ui-migration-plan.md", "r") as f:
    content = f.read()

content = content.replace(
    "- [ ] Rewrite `AudioHubBottomSheet` XML into a Compose `@OptIn(ExperimentalMaterial3Api::class) ModalBottomSheet`.",
    "- [x] Rewrite `AudioHubBottomSheet` XML into a Compose `@OptIn(ExperimentalMaterial3Api::class) ModalBottomSheet`."
)
content = content.replace(
    "- [ ] Wrap with Interop class for `MainActivity` to call.",
    "- [x] Wrap with Interop class for `MainActivity` to call."
)

with open("tasks/ui-migration-plan.md", "w") as f:
    f.write(content)
