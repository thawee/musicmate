package apincer.android.mmate.ui.view;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class BottomOffsetDecoration extends RecyclerView.ItemDecoration {
    private int mBottomOffset;

    public BottomOffsetDecoration(int bottomOffset) {
        mBottomOffset = bottomOffset;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        super.getItemOffsets(outRect, view, parent, state);

        RecyclerView.Adapter adapter = parent.getAdapter();

        if (adapter == null || adapter.getItemCount() == 0) {
            return;
        }

        if (parent.getLayoutManager() instanceof GridLayoutManager) {
            if (isOnLastRow(view, parent)) {
                outRect.bottom = mBottomOffset;
            }
        } else if (parent.getChildAdapterPosition(view) == adapter.getItemCount() - 1) {
            // Only set the offset for the last item.
            outRect.bottom = mBottomOffset;
        } else {
            outRect.bottom = 0;
        }
    }

    /** Sets the value to use for the bottom offset. */
    public void setBottomOffset(int bottomOffset) {
        mBottomOffset = bottomOffset;
    }

    /** Returns the set bottom offset. If none has been set, then 0 will be returned. */
    public int getBottomOffset() {
        return mBottomOffset;
    }

    /**
     * Returns whether or not the given view is on the last row of a {@code RecyclerView} with a
     * {@link GridLayoutManager}.
     *
     * @param view The view to inspect.
     * @param parent {@link RecyclerView} that contains the given view.
     * @return {@code true} if the given view is on the last row of the {@code RecyclerView}.
     */
    public static boolean isOnLastRow(View view, RecyclerView parent) {
        return getLastItemPositionOnSameRow(view, parent) == parent.getAdapter().getItemCount() - 1;
    }
    /**
     * Returns the span index of an item.
     */
    public static int getSpanIndex(View item) {
        GridLayoutManager.LayoutParams layoutParams =
                ((GridLayoutManager.LayoutParams) item.getLayoutParams());
        return layoutParams.getSpanIndex();
    }

    /**
     * Returns the position of the last item that is on the same row as input {@code view}.
     *
     * @param view The view to inspect.
     * @param parent {@link RecyclerView} that contains the given view.
     */
    public static int getLastItemPositionOnSameRow(View view, RecyclerView parent) {
        GridLayoutManager layoutManager = ((GridLayoutManager) parent.getLayoutManager());

        GridLayoutManager.SpanSizeLookup spanSizeLookup = layoutManager.getSpanSizeLookup();
        int spanCount = layoutManager.getSpanCount();
        int lastItemPosition = parent.getAdapter().getItemCount() - 1;

        int currentChildPosition = parent.getChildAdapterPosition(view);
        int spanSum = getSpanIndex(view) + spanSizeLookup.getSpanSize(currentChildPosition);
        // Iterate to the end of the row starting from the current child position.
        while (currentChildPosition <= lastItemPosition && spanSum <= spanCount) {
            spanSum += spanSizeLookup.getSpanSize(currentChildPosition + 1);
            if (spanSum > spanCount) {
                return currentChildPosition;
            }
            currentChildPosition++;
        }
        return lastItemPosition;
    }
}