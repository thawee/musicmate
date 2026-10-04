package apincer.android.mmate.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.ArrayList;

public class TagsTabLayoutAdapter extends FragmentStateAdapter {

    ArrayList<Fragment> fragments=new ArrayList<>();
    ArrayList<String> titles=new ArrayList<>();

    public TagsTabLayoutAdapter(@NonNull FragmentManager fm, @NonNull  Lifecycle lc) {
        super(fm, lc);
    }

    public void addNewTab(Fragment fragment,String title){
        fragments.add(fragment);
        titles.add(title);
    }

    @Nullable
    public CharSequence getPageTitle(int position) {
        return titles.get(position);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return fragments.get(position);
    }

    @Override
    public int getItemCount() {
        return titles.size();
    }
}