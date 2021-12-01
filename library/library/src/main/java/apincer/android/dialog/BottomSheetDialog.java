package apincer.android.dialog;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import apincer.android.carousellayoutmanager.CarouselLayoutManager;
import apincer.android.carousellayoutmanager.CarouselZoomPostLayoutListener;
import apincer.android.carousellayoutmanager.CenterScrollListener;
import apincer.android.library.R;
import apincer.android.listener.OnDialogButtonClickListener;
import apincer.android.listener.OnMenuItemClickListener;

@SuppressLint("ValidFragment")
public class BottomSheetDialog extends BottomSheetDialogFragment {
    protected final Builder mBuilder;
    protected BottomSheetDialog(Builder builder) {
        mBuilder = builder;
        mBuilder.bottomDialog = this;
    }

   // private View vTitlePanel;
   // private ImageView vIcon;
    private TextView vTitle, vContent;
    private View decorView;
    protected FrameLayout vCustomView;
    protected Button vNegative ;
    protected Button vPositive;
    private int buttomMargin = 0;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(mBuilder.dim_brackground) {
            setStyle(DialogFragment.STYLE_NO_TITLE, R.style.TransparentBottomSheetStyleDimBackground);
        }else {
            setStyle(DialogFragment.STYLE_NO_TITLE, R.style.TransparentBottomSheetStyle);
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.view_dialog_bottomsheet_carousel, container, false);
        if (getDialog() != null) {
            if(getDialog().getWindow() != null) {
                getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
                getDialog().getWindow().getDecorView().setBackground(new ColorDrawable(Color.TRANSPARENT));
            }
        }
        getDialog().setCanceledOnTouchOutside(false);

        return view;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //Common elements
        //vTitlePanel = view.findViewById(R.id.title_panel);
        vTitle = view.findViewById(R.id.title);
       // vIcon = view.findViewById(R.id.icon_title);
        vContent = view.findViewById(R.id.message);
        vCustomView = view.findViewById(R.id.bottomDialog_custom_view);
        vNegative = view.findViewById(R.id.bottomDialog_negative);
        vPositive = view.findViewById(R.id.bottomDialog_positive);

        // Icon, Title

        if (mBuilder.title != null) {
           // vTitlePanel.setVisibility(View.VISIBLE);
            vTitle.setText(mBuilder.title);
            vTitle.setVisibility(View.VISIBLE);
            if(mBuilder.title_color>0) {
                vTitle.setTextColor(mBuilder.context.getColor(mBuilder.title_color));
            }
            if (mBuilder.icon != null) {
                vTitle.setCompoundDrawables(mBuilder.icon,null,null,null);
               // Drawable icon = mBuilder.icon;
                if(mBuilder.title_color>0) {
                    vTitle.setCompoundDrawableTintList(ColorStateList.valueOf(mBuilder.title_color));
                    vTitle.setCompoundDrawableTintMode(PorterDuff.Mode.SRC_IN);
                   // icon.setColorFilter(mBuilder.context.getColor(mBuilder.title_color), PorterDuff.Mode.SRC_IN);
                }
               // vTitle.set
              //  vIcon.setImageDrawable(icon);
               // vIcon.setVisibility(View.VISIBLE);
            }
        }else {
           // vTitlePanel.setVisibility(View.GONE);
            vTitle.setVisibility(View.GONE);
           // vIcon.setVisibility(View.GONE);
        }

        // Content
        if (mBuilder.content != null) {
            vContent.setText(mBuilder.content);
        }else {
            vContent.setVisibility(View.GONE);
        }

        // customView
        if(mBuilder.customView!= null) {
            getDialog().setCanceledOnTouchOutside(false);
            vCustomView.addView(mBuilder.customView);
            vCustomView.setPadding(mBuilder.customViewPaddingLeft, mBuilder.customViewPaddingTop, mBuilder.customViewPaddingRight, mBuilder.customViewPaddingBottom);
        }else {
            vCustomView.setVisibility(View.GONE);
        }

        // Menus
        RecyclerView banner = view.findViewById(R.id.bottomDialog_menu_view);
        if(mBuilder.menu_items!=-1) {
            getDialog().setCanceledOnTouchOutside(false);
            MenuItemRecyclerAdapter adapter = new MenuItemRecyclerAdapter(getContext(), mBuilder.menu_items, mBuilder.menu_orientation);
            if (mBuilder.menu_callback != null) {
                adapter.setOnMenuItemClickListener(this, mBuilder.menu_callback);
            }
            banner.setAdapter(adapter);
            final CarouselLayoutManager layoutManager;
            if (mBuilder.menu_orientation == Builder.ORIENTATION.HORIZONTAL) {
                layoutManager = new CarouselLayoutManager(CarouselLayoutManager.HORIZONTAL, true);
            }else {
                layoutManager = new CarouselLayoutManager(CarouselLayoutManager.VERTICAL, true);
            }
            layoutManager.setPostLayoutListener(new CarouselZoomPostLayoutListener());

            banner.setLayoutManager(layoutManager);
            banner.setHasFixedSize(true);
            banner.addOnScrollListener(new CenterScrollListener());
        }else {
            banner.setVisibility(View.GONE);
        }

        // Buttons
        if (mBuilder.btn_positive != null) {
            vPositive.setVisibility(View.VISIBLE);
            vPositive.setText(mBuilder.btn_positive);
            vPositive.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (mBuilder.btn_positive_callback != null)
                        mBuilder.btn_positive_callback.onClick(BottomSheetDialog.this);
                    if (mBuilder.isAutoDismiss)
                        dismiss();
                }
            });

            if (mBuilder.btn_colorPositive != 0) {
                vPositive.setTextColor(mBuilder.btn_colorPositive);
            }

            if (mBuilder.btn_colorPositiveBackground == 0) {
                TypedValue v = new TypedValue();
                boolean hasColorPrimary = mBuilder.context.getTheme().resolveAttribute(R.attr.colorPrimary, v, true);
                mBuilder.btn_colorPositiveBackground = !hasColorPrimary ? v.data : ContextCompat.getColor(mBuilder.context, R.color.colorPrimary);
            }

            Drawable buttonBackground = UtilsLibrary.createButtonBackgroundDrawable(mBuilder.context, mBuilder.btn_colorPositiveBackground);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                vPositive.setBackground(buttonBackground);
            } else {
                // noinspection deprecation
                vPositive.setBackgroundDrawable(buttonBackground);
            }
        }

        if (mBuilder.btn_negative != null) {
            vNegative.setVisibility(View.VISIBLE);
            vNegative.setText(mBuilder.btn_negative);
            vNegative.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (mBuilder.btn_negative_callback != null)
                        mBuilder.btn_negative_callback.onClick(BottomSheetDialog.this);
                    if (mBuilder.isAutoDismiss)
                        dismiss();
                }
            });

            if (mBuilder.btn_colorNegative != 0) {
                vNegative.setTextColor(mBuilder.btn_colorNegative);
            }
        }

        if(mBuilder.isCancelable) {
            setCancelable(mBuilder.isCancelable);
            getDialog().setCanceledOnTouchOutside(true);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        Window window = dialog.getWindow();
        WindowManager.LayoutParams wlp = window.getAttributes();
        wlp.windowAnimations = R.style.BottomSheetDialogAnimation;
        wlp.gravity = Gravity.CENTER;
        window.setAttributes(wlp);

        return dialog;
    }

    public void setDialogBorder(Dialog dialog) {
        FrameLayout bottomSheet = (FrameLayout) dialog.getWindow().findViewById(com.google.android.material.R.id.design_bottom_sheet);
        bottomSheet.setBackground(new ColorDrawable(Color.TRANSPARENT));
        setMargins(bottomSheet, 10, 0, 10, 20);
    }

    private void setMargins(View view, int left, int top, int right, int bottom) {
        if (view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            p.setMargins(left, top, right, bottom);
            view.requestLayout();
        }
    }

    public static class Builder {
        protected BottomSheetDialog bottomDialog;
        protected Context context;

        // Bottom Dialog
        protected Dialog dialog;

        // Icon, Title and Content
        protected Drawable icon;
        protected CharSequence title, content;

        //Menus
        protected int menu_items = -1;
        protected OnMenuItemClickListener menu_callback;
        protected ORIENTATION menu_orientation;
        private boolean menu_carousel;

        // Buttons
        protected CharSequence btn_negative, btn_positive;
        protected OnDialogButtonClickListener btn_negative_callback, btn_positive_callback;
        protected boolean isAutoDismiss;

        // Button text colors
        protected int btn_colorNegative, btn_colorPositive;

        // Button background colors
        protected int btn_colorPositiveBackground;

        // Text text colors
        protected int title_color;

        private View decorView;

        // Custom View
        protected View customView;
        protected int customViewPaddingLeft, customViewPaddingTop, customViewPaddingRight, customViewPaddingBottom;

        // Other options
        protected boolean isCancelable;

        public void setDimBrackground(boolean dimBrackground) {
            this.dim_brackground = dimBrackground;
        }

        public boolean dim_brackground = true;

        public Builder(@NonNull Context context) {
            this.context = context;
            this.isCancelable = true;
            this.isAutoDismiss = true;
            this.menu_orientation = ORIENTATION.HORIZONTAL;
        }

        public Builder setTitle(@StringRes int titleRes) {
            setTitle(this.context.getString(titleRes));
            return this;
        }

        public Builder setTitleColor(@ColorRes int colorRes) {
            title_color = colorRes;
            return this;
        }

        public Builder setTitle(@NonNull CharSequence title) {
            this.title = title;
            return this;
        }

        public Builder setContent(@StringRes int contentRes) {
            setContent(this.context.getString(contentRes));
            return this;
        }

        public Builder setContent(@NonNull CharSequence content) {
            this.content = content;
            return this;
        }

        public Builder setIcon(@NonNull Drawable icon) {
            this.icon = icon;
            return this;
        }

        public Builder setIcon(@DrawableRes int iconRes) {
            this.icon = ResourcesCompat.getDrawable(context.getResources(), iconRes, null);
            return this;
        }

        public Builder setPositiveBackgroundColorResource(@ColorRes int buttonColorRes) {
            this.btn_colorPositiveBackground = ResourcesCompat.getColor(context.getResources(), buttonColorRes, null);
            return this;
        }

        public Builder setPositiveBackgroundColor(int color) {
            this.btn_colorPositiveBackground = color;
            return this;
        }

        public Builder setPositiveTextColorResource(@ColorRes int textColorRes) {
            this.btn_colorPositive = ResourcesCompat.getColor(context.getResources(), textColorRes, null);
            return this;
        }

        public Builder setPositiveTextColor(int color) {
            this.btn_colorPositive = color;
            return this;
        }

        public Builder setPositiveText(@StringRes int buttonTextRes) {
            setPositiveText(this.context.getString(buttonTextRes));
            return this;
        }

        public Builder setPositiveText(@NonNull CharSequence buttonText) {
            this.btn_positive = buttonText;
            return this;
        }

        public Builder onPositive(@NonNull OnDialogButtonClickListener buttonCallback) {
            this.btn_positive_callback = buttonCallback;
            return this;
        }

        public Builder setNegativeTextColorResource(@ColorRes int textColorRes) {
            this.btn_colorNegative = ResourcesCompat.getColor(context.getResources(), textColorRes, null);
            return this;
        }

        public Builder setNegativeTextColor(int color) {
            this.btn_colorNegative = color;
            return this;
        }

        public Builder setNegativeText(@StringRes int buttonTextRes) {
            setNegativeText(this.context.getString(buttonTextRes));
            return this;
        }

        public Builder setNegativeText(@NonNull CharSequence buttonText) {
            this.btn_negative = buttonText;
            return this;
        }

        public Builder onNegative(@NonNull OnDialogButtonClickListener buttonCallback) {
            this.btn_negative_callback = buttonCallback;
            return this;
        }

        public Builder setCancelable(boolean cancelable) {
            this.isCancelable = cancelable;
            return this;
        }

        public Builder autoDismiss(boolean autodismiss) {
            this.isAutoDismiss = autodismiss;
            return this;
        }

        public Builder setCustomView(View customView) {
            this.customView = customView;
            this.customViewPaddingLeft = 0;
            this.customViewPaddingRight = 0;
            this.customViewPaddingTop = 0;
            this.customViewPaddingBottom = 0;
            return this;
        }

        public Builder setCustomView(View customView, int left, int top, int right, int bottom) {
            this.customView = customView;
            this.customViewPaddingLeft = dpToPixels(context, left);
            this.customViewPaddingRight = dpToPixels(context, right);
            this.customViewPaddingTop = dpToPixels(context, top);
            this.customViewPaddingBottom = dpToPixels(context, bottom);
            return this;
        }

        public Builder setMenuRes(@MenuRes int menu) {
            this.menu_items = menu;
            return this;
        }

        public Builder setMenuOrientation(ORIENTATION menu_carouse_orientation) {
            this.menu_orientation = menu_carouse_orientation;
            return this;
        }

        public Builder onMenuItemClick(@NonNull OnMenuItemClickListener buttonCallback) {
            this.menu_callback = buttonCallback;
            return this;
        }

        @UiThread
        public BottomSheetDialog build() {
            return new BottomSheetDialog(this);
        }

        @UiThread
        public BottomSheetDialog show(androidx.fragment.app.FragmentManager fragmentManager) {
            BottomSheetDialog bottomDialog = build();
            bottomDialog.show(fragmentManager, "BottomSheetDialog");
            return bottomDialog;
        }

        static int dpToPixels(Context context, int dp) {
            final float scale = context.getResources().getDisplayMetrics().density;
            return (int) (dp * scale + 0.5f);
        }

        public Builder setMenuCarousel(boolean menuCarousel) {
            this.menu_carousel = menuCarousel;
            return this;
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public Builder setDecorView(View decorView) {
            this.decorView = decorView;
            return this;
        }
        public View getDecorView() { return decorView; }

        public enum ORIENTATION {HORIZONTAL, VERTICAL}
    }
}