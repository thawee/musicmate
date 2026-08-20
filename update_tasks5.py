with open("tasks/ui-migration-plan.md", "r") as f:
    content = f.read()

content = content.replace(
    "- [ ] `DynamicRangeView.java` -> `DynamicRangeMeters.kt`",
    "- [x] `DynamicRangeView.java` -> `DynamicRangeMeters.kt`"
)
content = content.replace(
    "- [ ] `WaveformView.java` -> `Waveform.kt`",
    "- [x] `WaveformView.java` -> `Waveform.kt` (Unused Java deleted, Compose created)"
)

with open("tasks/ui-migration-plan.md", "w") as f:
    f.write(content)
