package apincer.android.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.reflect.Constructor;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import apincer.android.library.R;
import apincer.android.listener.OnMenuItemClickListener;

public class MenuItemRecyclerAdapter extends RecyclerView.Adapter<MenuItemRecyclerAdapter.MenuItemHolder> {
        Context context;
        int drawable;
        int menuRes;
        private Menu menu;
        private OnMenuItemClickListener onMenuItemClickListener;
        private BottomSheetDialog bottomSheetDialog;

    public MenuItemRecyclerAdapter(Context context, int menuRes, BottomSheetDialog.Builder.ORIENTATION orientation) {
        this.context = context;
        this.menuRes = menuRes;
        if(orientation==BottomSheetDialog.Builder.ORIENTATION.HORIZONTAL) {
            this.drawable = R.layout.view_menu_item_horizontal;
        }else {
            this.drawable = R.layout.view_menu_item_vertical;
        }
        menu = newMenuInstance(context);
        MenuInflater inflater = new MenuInflater(context);
        inflater.inflate(menuRes, menu);
    }

    protected Menu newMenuInstance(Context context) {
            try {
                Class<?> menuBuilderClass = Class.forName("com.android.internal.view.menu.MenuBuilder");

                Constructor<?> constructor = menuBuilderClass.getDeclaredConstructor(Context.class);

                return (Menu) constructor.newInstance(context);

            } catch (Exception e) {e.printStackTrace();}

            return null;
        }

        @NonNull
        @Override
        public MenuItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(context).inflate(drawable, null);
            return new MenuItemHolder(itemView);
        }

        @Override
        public void onBindViewHolder(@NonNull MenuItemHolder holder, final int position) {
            MenuItem item = menu.getItem(position);
            holder.tv.setText(item.getTitle());
            holder.iv.setImageDrawable(item.getIcon());
            if(onMenuItemClickListener!=null) {
                holder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onMenuItemClickListener.onClick(menu.getItem(position));
                        bottomSheetDialog.dismiss();
                    }
                });
            }
        }

        @Override
        public int getItemCount() {
            return menu.size();
        }

    public void setOnMenuItemClickListener(BottomSheetDialog bottomSheetDialog, OnMenuItemClickListener onMenuItemClickListener) {
        this.onMenuItemClickListener = onMenuItemClickListener;
        this.bottomSheetDialog = bottomSheetDialog;
    }

    class MenuItemHolder extends RecyclerView.ViewHolder {
        View itemView;
        ImageView iv;
        TextView tv;
        public MenuItemHolder(View itemView) {
            super(itemView);
            this.itemView = itemView;
            iv = itemView.findViewById(R.id.iv);
            tv = itemView.findViewById(R.id.tv);
        }
    }
}
