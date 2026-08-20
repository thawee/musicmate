with open("app/src/main/java/apincer/android/mmate/ui/compose/ListInterop.kt", "r") as f:
    content = f.read()

if "fun getTracks()" not in content:
    content = content.replace("fun updateTracks(tracks: List<Track>) {", "@JvmStatic\n    fun getTracks(): List<Track> = _tracks\n\n    @JvmStatic\n    fun updateTracks(tracks: List<Track>) {")
    with open("app/src/main/java/apincer/android/mmate/ui/compose/ListInterop.kt", "w") as f:
        f.write(content)
