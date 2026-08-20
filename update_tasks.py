with open("tasks/ui-migration-plan.md", "r") as f:
    content = f.read()

content = content.replace("- [ ] **Navigation & Scaffold:** Replace the legacy `ResideMenu` with a standard Jetpack Compose Material 3 `ModalNavigationDrawer` and `Scaffold`.",
"- [x] **Navigation & Scaffold:** Replace the legacy `ResideMenu` with a standard Jetpack Compose Material 3 `ModalNavigationDrawer` and `Scaffold`. (COMPLETED - DrawerInterop created)")

with open("tasks/ui-migration-plan.md", "w") as f:
    f.write(content)
