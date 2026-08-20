import re

with open("app/src/main/java/apincer/android/mmate/ui/compose/TagsTechnicalPage.kt", "r") as f:
    content = f.read()

content = content.replace("apincer.android.mmate.repository.MusicFolderRepository", "apincer.music.core.repository.FileRepository")
content = content.replace("MusicFolderRepository", "FileRepository")
content = content.replace("apincer.android.mmate.dlna.FFMPegReader", "apincer.music.core.codec.FFMPegReader")
content = content.replace("apincer.android.mmate.dlna.TagReader", "apincer.music.core.codec.TagReader")

with open("app/src/main/java/apincer/android/mmate/ui/compose/TagsTechnicalPage.kt", "w") as f:
    f.write(content)

with open("app/src/main/java/apincer/android/mmate/ui/TagsTechnicalFragment.kt", "r") as f:
    content2 = f.read()

content2 = content2.replace("apincer.android.mmate.repository.MusicFolderRepository", "apincer.music.core.repository.FileRepository")
content2 = content2.replace("apincer.android.mmate.repository.MusicTagRepository", "apincer.music.core.repository.TagRepository")
content2 = content2.replace("MusicFolderRepository", "FileRepository")
content2 = content2.replace("MusicTagRepository", "TagRepository")
content2 = content2.replace("apincer.android.mmate.dlna.FFMpegHelper", "apincer.music.core.codec.FFMpegHelper")

with open("app/src/main/java/apincer/android/mmate/ui/TagsTechnicalFragment.kt", "w") as f:
    f.write(content2)
