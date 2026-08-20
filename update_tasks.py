with open("tasks/ui-migration-plan.md", "r") as f:
    content = f.read()

content = content.replace("- [x] **MusicTagAdapter:** Rewrite the complex `RecyclerView` adapter and `SelectionTracker` into a `LazyVerticalGrid` / `LazyColumn` with declarative `selected` states.",
"- [x] **MusicTagAdapter:** Rewrite the complex `RecyclerView` adapter and `SelectionTracker` into a `LazyVerticalGrid` / `LazyColumn` with declarative `selected` states. (COMPLETED - Adapter fully excised from MainActivity)")

with open("tasks/ui-migration-plan.md", "w") as f:
    f.write(content)
