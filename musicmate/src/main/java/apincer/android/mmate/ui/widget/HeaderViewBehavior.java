package apincer.android.mmate.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.animation.AnticipateOvershootInterpolator;
import android.view.animation.BounceInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

/**
 * Created by Administrator on 2015/12/28.
 *
 */

public class HeaderViewBehavior extends CoordinatorLayout.Behavior<View> {

    private static final float DEFAULT_TENSION = 0.5f;
    private static final long DEFAULT_ANIMATION_TIME = 400;
    private static final Interpolator FASTOUTSLOWIN_INTERPOLATOR = new FastOutSlowInInterpolator();
    private static final Interpolator BOUNCE_INTERPOLATOR = new BounceInterpolator();
    private static final Interpolator ANTICIPATEOVERSHOOT_INTERPOLATOR = new AnticipateOvershootInterpolator();
    private Interpolator OverShootInterpolator = new OvershootInterpolator(DEFAULT_TENSION);
    private float tension = DEFAULT_TENSION;
    private long animation_time = DEFAULT_ANIMATION_TIME;
    private float animation_distance = 0.0f;
    private boolean scrollUp;
    private float totalY = 0.0f;
    private boolean autoUp = false;
    private boolean autoDown = false;


    private View child;
    private View target;

    private ViewPropertyAnimator animatorChild;
    private ViewPropertyAnimator animatorTarget;

    public HeaderViewBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

//    @Override
//    public boolean layoutDependsOn(CoordinatorLayout parent, View child, View dependency) {
//        return dependency instanceof AppBarLayout;
//    }
//
//    @Override
//    public boolean onDependentViewChanged(CoordinatorLayout parent, View child, View dependency) {
//        float translationY = Math.abs(dependency.getTranslationY());
//        child.setTranslationY(translationY);
//        return true;
//    }

    private float createTensionFromFlingVelocity(final float velocity) {
        float denominator = 1500.0f;
        return Math.abs(velocity) / denominator;
    }

    private long createAnimationTimeFromFlingVelocity(final float velocity) {
        long time = (long)(animation_distance / Math.abs(velocity)) * 1000;
        if (time == 0 ) {
            time = 100;
        }
        if (time > DEFAULT_ANIMATION_TIME) {
            time = DEFAULT_ANIMATION_TIME;
        }
        return time;
    }

    @Override
    public boolean onNestedFling(@NonNull CoordinatorLayout coordinatorLayout, @NonNull View child, @NonNull View target, float velocityX, float velocityY, boolean consumed) {
        tension = createTensionFromFlingVelocity(velocityY);
        if (tension > DEFAULT_TENSION) {
            OverShootInterpolator = new OvershootInterpolator(tension);
        }
        animation_time = createAnimationTimeFromFlingVelocity(velocityY);
        return super.onNestedFling(coordinatorLayout, child, target, velocityX, velocityY, consumed);
    }

    @Override
    public boolean onStartNestedScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull View child, @NonNull View directTargetChild, @NonNull View target, int nestedScrollAxes, int type) {
        return ((nestedScrollAxes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0);
    }

    @Override
    public void onNestedPreScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull View child, @NonNull View target, int dx, int dy, @NonNull int[] consumed, int type) {

        if (tension != DEFAULT_TENSION) {
            tension = DEFAULT_TENSION;
            OverShootInterpolator = new OvershootInterpolator(tension);
        }

        if (animation_time != DEFAULT_ANIMATION_TIME) {
            animation_time = DEFAULT_ANIMATION_TIME;
        }

        if (autoUp || autoDown) {
            return;
        }

        totalY += dy;

        //consumed[1] = dy;

        if (dy >= 0) {
            scrollUp = true;
            animation_distance = child.getHeight() - totalY;
        } else {
            scrollUp = false;
            animation_distance = totalY;
        }

        move(child, target);
    }

    @Override
    public void onStopNestedScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull View child, @NonNull View target, int type) {

        if (autoUp || autoDown) {
            return;
        }

        this.child = child;
        this.target = target;

        if (scrollUp ) {
            if (totalY > child.getHeight() / 4) {
                autoUp(child, target);
            } else {
                autoDown(child, target);
            }
        } else {
            if (totalY < (3 * child.getHeight() / 4)) {
                autoDown(child, target);
            } else {
                autoUp(child, target);
            }
        }
    }

    private void move(final View child, final View target) {

        if (totalY > 0) {

            if (totalY > child.getHeight()) {
                totalY = child.getHeight();
            }

            child.setTranslationY(-totalY / 2);
            float scale = 1 - totalY / (child.getHeight() * 10);
            child.setScaleX(scale);
            child.setScaleY(scale);
//            target.setTranslationY(-totalY);

        } else {
            totalY = 0;
            child.setTranslationY(0);
//            target.setTranslationY(0);
        }

    }

    private void autoUp(final View child, final View target) {
        autoUp = true;
        animatorChild = child.animate()
                .translationY(-child.getHeight() / 2)
                .setInterpolator(OverShootInterpolator)
                .setDuration(animation_time);

        animatorTarget = target.animate()
                .translationY(-child.getHeight())
                .setInterpolator(OverShootInterpolator)
                .setDuration(animation_time);

        animatorChild.withStartAction(animateRunnable);
        animatorChild.withEndAction(animateEndRunnable);
        animatorTarget.withStartAction(animateRunnable);
        animatorTarget.withEndAction(animateEndRunnable);

        animateRunnable.run();
    }

    Runnable animateRunnable = new Runnable() {
        @Override
        public void run() {
            animatorChild.start();
            animatorTarget.start();
        }
    };

    Runnable animateEndRunnable = new Runnable() {
        @Override
        public void run() {
            if (autoUp) {
                autoUp = false;
                totalY = child.getHeight();
            } else if (autoDown) {
                autoDown = false;
                totalY = 0;
            }
        }
    };

    private void autoDown(final View child, final View target) {
        autoDown = true;

        animatorChild = child.animate()
                .translationY(0)
                .setInterpolator(OverShootInterpolator)
                .setDuration(animation_time);

        animatorTarget = target.animate()
                .translationY(0)
                .setInterpolator(OverShootInterpolator)
                .setDuration(animation_time);

        animatorChild.withStartAction(animateRunnable);
        animatorChild.withEndAction(animateEndRunnable);
        animatorTarget.withStartAction(animateRunnable);
        animatorTarget.withEndAction(animateEndRunnable);

        animateRunnable.run();
    }
}