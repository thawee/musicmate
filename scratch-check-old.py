import subprocess

# Let's check git diff to see the original implementation of actionDirectories
result = subprocess.run(["git", "diff", "app/src/main/java/apincer/android/mmate/ui/MainActivity.java"], capture_output=True, text=True)
for line in result.stdout.splitlines():
    if "save" in line or "scan" in line or "btnOK" in line or "TagRepository" in line:
        print(line)
