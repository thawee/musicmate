package apincer.android.mmate.ui;
import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * Custom ArrayAdapter that disables filtering — always shows the full list.
 * Ideal for use with Material AutoCompleteTextView dropdowns where typing
 * should not filter items (like a static selection list).
 */
public class NoFilterArrayAdapter<T> extends ArrayAdapter<T> {

    private final List<T> items;
    private final Filter noFilter = new NoFilter();

    public NoFilterArrayAdapter(@NonNull Context context,
                                int resource,
                                @NonNull List<T> items) {
        super(context, resource, items);
        this.items = items;
    }

    @NonNull
    @Override
    public Filter getFilter() {
        // Return a filter that always returns the full list
        return noFilter;
    }

    private class NoFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults results = new FilterResults();
            results.values = items;
            results.count = items.size();
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            // Notify that the data set is unchanged, so full list is always shown
            notifyDataSetChanged();
        }
    }

    @Nullable
    @Override
    public T getItem(int position) {
        return (position >= 0 && position < items.size()) ? items.get(position) : null;
    }

    @Override
    public int getCount() {
        return items.size();
    }
}
