package com.balsikandar.crashreporter.adapter;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.balsikandar.crashreporter.ui.CrashLogFragment;
import com.balsikandar.crashreporter.ui.ExceptionLogFragment;

public class MainStateAdapter extends FragmentStateAdapter {

    // Pass FragmentManager and Lifecycle to the super constructor
    public MainStateAdapter(@NonNull FragmentManager fragmentManager, @NonNull Lifecycle lifecycle) {
        super(fragmentManager, lifecycle);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        // Replaces getItem()
        if (position == 0) {
            return new CrashLogFragment();
        } else {
            // position == 1 or any other case
            return new ExceptionLogFragment();
        }
    }

    @Override
    public int getItemCount() {
        // Replaces getCount()
        return 2;
    }
}