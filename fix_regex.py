with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "r") as f:
    content = f.read()

content = content.replace("Track tag = (item.intValue( >= 0 && item.intValue( < apincer.android.mmate.ui.compose.ListInterop.getTracks().size() ? apincer.android.mmate.ui.compose.ListInterop.getTracks().get(item.intValue() : null));", 
"Track tag = (item.intValue() >= 0 && item.intValue() < apincer.android.mmate.ui.compose.ListInterop.getTracks().size() ? apincer.android.mmate.ui.compose.ListInterop.getTracks().get(item.intValue()) : null);")

with open("app/src/main/java/apincer/android/mmate/ui/MainActivity.java", "w") as f:
    f.write(content)
