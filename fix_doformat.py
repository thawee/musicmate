import re

with open("app/src/main/java/apincer/android/mmate/ui/TagsEditorFragment.kt", "r") as f:
    content = f.read()

replacement = """    fun doFormatTags() {
        tagsActivity.startProgressBar()
        val itemsToProcess = ArrayList(tagsActivity.editItems ?: emptyList())
        CompletableFuture.supplyAsync {
            var totalFormatted = 0
            var thaiFixedCount = 0
            for (tag in itemsToProcess) {
                var thaiFixed = false
                if (ThaiEncodingUtils.isGarbledThai(tag.title)) {
                    tag.title = ThaiEncodingUtils.fixThaiEncoding(tag.title)
                    thaiFixed = true
                }
                if (ThaiEncodingUtils.isGarbledThai(tag.artist)) {
                    tag.artist = ThaiEncodingUtils.fixThaiEncoding(tag.artist)
                    thaiFixed = true
                }
                if (ThaiEncodingUtils.isGarbledThai(tag.album)) {
                    tag.album = ThaiEncodingUtils.fixThaiEncoding(tag.album)
                    thaiFixed = true
                }
                if (ThaiEncodingUtils.isGarbledThai(tag.albumArtist)) {
                    tag.albumArtist = ThaiEncodingUtils.fixThaiEncoding(tag.albumArtist)
                    thaiFixed = true
                }
                if (ThaiEncodingUtils.isGarbledThai(tag.genre)) {
                    tag.genre = ThaiEncodingUtils.fixThaiEncoding(tag.genre)
                    thaiFixed = true
                }
                if (ThaiEncodingUtils.isGarbledThai(tag.composer)) {
                    tag.composer = ThaiEncodingUtils.fixThaiEncoding(tag.composer)
                    thaiFixed = true
                }
                if (thaiFixed) thaiFixedCount++
                
                tag.title = StringUtils.formatTitle(tag.title)
                tag.artist = StringUtils.formatArtists(tag.artist)
                tag.albumArtist = StringUtils.formatTitle(tag.albumArtist)
                tag.genre = StringUtils.formatTitle(tag.genre)
                if (!StringUtils.isEmpty(tag.track)) {
                    tag.track = StringUtils.formatTrack(tag.track)
                }
                if (StringUtils.isEmpty(tag.album)) {
                    // We don't have getDefaultAlbum in Kotlin easily unless imported, let's just do:
                    tag.album = StringUtils.formatTitle("Unknown Album")
                }
                totalFormatted++
            }
            Pair(totalFormatted, thaiFixedCount)
        }.thenAccept { (total, thaiFixed) ->
            tagsActivity.refreshDisplayTag()
            tagsActivity.stopProgressBar()
            var msg = "Reformatted $total track(s)"
            if (thaiFixed > 0) msg += " (Fixed Thai encoding on $thaiFixed)"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }.exceptionally {
            tagsActivity.refreshDisplayTag()
            tagsActivity.stopProgressBar()
            null
        }
    }"""

# Replace from fun doFormatTags() to the end of the class
content = re.sub(r'fun doFormatTags\(\).*?}$', replacement + "\n}", content, flags=re.DOTALL)

with open("app/src/main/java/apincer/android/mmate/ui/TagsEditorFragment.kt", "w") as f:
    f.write(content)
