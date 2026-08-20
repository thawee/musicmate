with open("app/src/main/res/layout/activity_main.xml", "r") as f:
    xml = f.read()

import re
xml = re.sub(r'<androidx.recyclerview.widget.RecyclerView.*?\/>', 
    '''<FrameLayout
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">
                    <androidx.recyclerview.widget.RecyclerView
                        android:id="@+id/recycler_view"
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:visibility="gone"
                        android:clipToPadding="false"
                        android:paddingBottom="104dp"
                        tools:listitem="@layout/view_list_item" />
                    <androidx.compose.ui.platform.ComposeView
                        android:id="@+id/compose_list_view"
                        android:layout_width="match_parent"
                        android:layout_height="match_parent" />
                </FrameLayout>''', 
    xml, flags=re.DOTALL)
with open("app/src/main/res/layout/activity_main.xml", "w") as f:
    f.write(xml)
