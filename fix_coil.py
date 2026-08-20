import re

with open("app/src/main/java/apincer/android/mmate/ui/view/AudioHubBottomSheet.java", "r") as f:
    content = f.read()

content = content.replace("""                        .target(new coil3.target.Target() {
                            public void onSuccess(android.graphics.drawable.Drawable result) {
                                if (result instanceof android.graphics.drawable.BitmapDrawable) {
                                    nowPlayingState.getAlbumArt().setValue(((android.graphics.drawable.BitmapDrawable) result).getBitmap());
                                }
                            }
                            public void onError(android.graphics.drawable.Drawable error) {
                                nowPlayingState.getAlbumArt().setValue(null);
                            }
                        })""", """                        .target(new coil3.target.Target() {
                            @Override
                            public void onSuccess(coil3.Image result) {
                                if (result instanceof coil3.BitmapImage) {
                                    nowPlayingState.getAlbumArt().setValue(((coil3.BitmapImage) result).getBitmap());
                                }
                            }
                            @Override
                            public void onError(coil3.Image error) {
                                nowPlayingState.getAlbumArt().setValue(null);
                            }
                            @Override
                            public void onStart(coil3.Image placeholder) {
                                nowPlayingState.getAlbumArt().setValue(null);
                            }
                        })""")

with open("app/src/main/java/apincer/android/mmate/ui/view/AudioHubBottomSheet.java", "w") as f:
    f.write(content)
