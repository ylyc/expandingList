/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified by Leon Cheng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.expandingcells;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.content.Context;
import android.graphics.Canvas;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.AnimatorListenerAdapter;
import com.nineoldandroids.animation.AnimatorSet;
import com.nineoldandroids.animation.ObjectAnimator;
import com.nineoldandroids.animation.PropertyValuesHolder;
import com.nineoldandroids.view.ViewHelper;

/**
 * A custom listview which supports the preview of extra content corresponding to each cell
 * by clicking on the cell to hide and show the extra content.
 */
public class ExpandingListView extends ListView {
    private OnItemClickListener mExternalOnItemClickListener;
    
    private OnExpandingListener mOnExpandingListener;

    private boolean mShouldRemoveObserver = false;

    private List<View> mViewsToDraw = new ArrayList<View>();

    private int[] mTranslate;

    public ExpandingListView(Context context) {
        super(context);
        init();
    }

    public ExpandingListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ExpandingListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        super.setOnItemClickListener(mItemClickListener);
    }

    @Override
    public void setOnItemClickListener(OnItemClickListener listener) {
        //keep a reference to the listener so that
        //we can forward onItemClick responses
        mExternalOnItemClickListener = listener;
    }

    /**
     * Listens for item clicks and expands or collapses the selected view depending on
     * its current state.
     */
    private AdapterView.OnItemClickListener mItemClickListener = new AdapterView
            .OnItemClickListener() {
        @Override
        public void onItemClick (AdapterView<?> parent, View view, int position, long id) {
            if(mExternalOnItemClickListener != null){
                mExternalOnItemClickListener.onItemClick(parent, view, position, id);
            }

            ExpandableListItem viewObject = (ExpandableListItem)getItemAtPosition(getPositionForView
                    (view));
            if (!viewObject.isExpanded()) {
                expandView(view);
                if(mOnExpandingListener != null){
                    mOnExpandingListener.onExpand(view);
                }
            } else {
                collapseView(view);
                if(mOnExpandingListener != null){
                    mOnExpandingListener.onCollapse(view);
                }
            }
        }
    };
    
    
    @Override
    public void setAdapter(ListAdapter adapter) {
        //will require ListViewItems to be instances of ExpandableListItem 
        if (adapter != null && !adapter.isEmpty() && !(adapter.getItem(0) instanceof ExpandableListItem)) {
            throw new IllegalArgumentException("ListItem must be ExpandablelistItem");
        }
         
        super.setAdapter(adapter);
    }

    /**
     * Calculates the top and bottom bound changes of the selected item. These values are
     * also used to move the bounds of the items around the one that is actually being
     * expanded or collapsed.
     *
     * This method can be modified to achieve different user experiences depending
     * on how you want the cells to expand or collapse. In this specific demo, the cells
     * always try to expand downwards (leaving top bound untouched), and similarly,
     * collapse upwards (leaving top bound untouched). If the change in bounds
     * results in the complete disappearance of a cell, its lower bound is moved is
     * moved to the top of the screen so as not to hide any additional content that
     * the user has not interacted with yet. Furthermore, if the collapsed cell is
     * partially off screen when it is first clicked, it is translated such that its
     * full contents are visible. Lastly, this behaviour varies slightly near the bottom
     * of the listview in order to account for the fact that the bottom bounds of the actual
     * listview cannot be modified.
     */
    private int[] getCollapsingTopAndBottomTranslation(int viewPosition, int curTop, int curBottom, int yDelta) {
        int yTranslateTop = 0;
        int yTranslateBottom = yDelta;

        int height = curBottom - curTop;
        int newHeight = height - yDelta;

        // this is the height above the viewPosition
        int precedingHeight = 0;
        // this is the height below the viewPosition
        int followingHeight = 0;

        for (int i = 0; i < getAdapter().getCount(); i++) {
            ExpandableListItem item = (ExpandableListItem) getItemAtPosition(i);
            if (item.isExpanded()) {
                if (i < viewPosition) {
                    precedingHeight += item.getExpandedHeight();
                } else if (i > viewPosition) {
                    followingHeight += item.getExpandedHeight();
                }
            } else {
                if (i < viewPosition) {
                    precedingHeight += item.getCollapsedHeight();
                } else if (i > viewPosition) {
                    followingHeight += item.getCollapsedHeight();
                }
            }
        }

        if (followingHeight + curTop + newHeight >= getHeight()) {
            yTranslateTop = 0;
            yTranslateBottom = -yDelta;
        } else if (yDelta >= followingHeight) {
            if(precedingHeight + newHeight + followingHeight <= getHeight()){
                yTranslateTop = precedingHeight - curTop;
                yTranslateBottom = -yDelta + yTranslateTop;
            }
            else if (curBottom > getHeight()) { // if cell is below screen
                yTranslateTop = yDelta - curTop - followingHeight;
                yTranslateBottom = -yDelta + yTranslateTop;
            } 
            else {
                yTranslateBottom = 0;
                yTranslateTop = yDelta;
            }
        } else {
            if (precedingHeight > getHeight()) {
                // goes to the bottom
                int newY = getHeight() - newHeight;
                yTranslateTop = newY - curTop;
                yTranslateBottom = -yDelta + yTranslateTop;
            } else {
                // somewhere in the middle
                yTranslateTop = precedingHeight - curTop;
                yTranslateBottom = -yDelta + yTranslateTop;
            }
        }

        return new int[] {yTranslateTop, yTranslateBottom };
    }

    private int[] getExpandingTopAndBottomTranslations(int viewPosition, int curtop, int curbottom, int yDelta) {
        int yTranslateTop = 0;
        int yTranslateBottom = yDelta;

        int height = curbottom - curtop;

        boolean isOverTop = curtop < 0;

        // check if the view we trying to expand is actually longer than the
        // listview height
        boolean isBelowBottom = (curtop + height + yDelta) > getHeight();

        if (isOverTop) {
            yTranslateTop = curtop;
            yTranslateBottom = yDelta - yTranslateTop;
        } else if (isBelowBottom) {// We will move the view up to compensate for
                                   // the view below the screen.
            // View below the screen
            int deltaBelow = curtop + height + yDelta - getHeight();

            // check whether we should move the view to the top or are there
            // enough space to compensate for the view below the screen
            yTranslateTop = curtop - deltaBelow < 0 ? curtop : deltaBelow;

            yTranslateBottom = yDelta - yTranslateTop;
        }

        return new int[] {yTranslateTop, yTranslateBottom };
    }

    /**
     * This method expands the view that was clicked and animates all the views
     * around it to make room for the expanding view. There are several steps required
     * to do this which are outlined below.
     *
     * 1. Store the current top and bottom bounds of each visible item in the listview.
     * 2. Update the layout parameters of the selected view. In the context of this
     *    method, the view should be originally collapsed and set to some custom height.
     *    The layout parameters are updated so as to wrap the content of the additional
     *    text that is to be displayed.
     *
     * After invoking a layout to take place, the listview will order all the items
     * such that there is space for each view. This layout will be independent of what
     * the bounds of the items were prior to the layout so two pre-draw passes will
     * be made. This is necessary because after the layout takes place, some views that
     * were visible before the layout may now be off bounds but a reference to these
     * views is required so the animation completes as intended.
     *
     * 3. The first predraw pass will set the bounds of all the visible items to
     *    their original location before the layout took place and then force another
     *    layout. Since the bounds of the cells cannot be set directly, the method
     *    setSelectionFromTop can be used to achieve a very similar effect.
     * 4. The expanding view's bounds are animated to what the final values should be
     *    from the original bounds.
     * 5. The bounds above the expanding view are animated upwards while the bounds
     *    below the expanding view are animated downwards.
     * 6. The extra text is faded in as its contents become visible throughout the
     *    animation process.
     *
     * It is important to note that the listview is disabled during the animation
     * because the scrolling behaviour is unpredictable if the bounds of the items
     * within the listview are not constant during the scroll.
     */
    private void expandView(final View view) {
        final ExpandableListItem viewObject = (ExpandableListItem)getItemAtPosition(getPositionForView
                (view));

        /* Store the original top and bottom bounds of all the cells.*/
        final int oldTop = view.getTop();
        final int oldBottom = view.getBottom();

        final HashMap<View, int[]> oldCoordinates = new HashMap<View, int[]>();

        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View v = getChildAt(i);
            ViewCompat.setHasTransientState(v, true);
            oldCoordinates.put(v, new int[] {v.getTop(), v.getBottom()});
        }

        /* Update the layout so the extra content becomes visible.*/
        final View expandingLayout = view.findViewById(R.id.expanding_layout);
        if (expandingLayout == null) {
            throw new IllegalStateException("Layout must have a ExpandingLayout called expanding_layout.");
        }
        expandingLayout.setVisibility(View.VISIBLE);

        /* Add an onPreDraw Listener to the listview. onPreDraw will get invoked after onLayout
         * and onMeasure have run but before anything has been drawn. This
         * means that the final post layout properties for all the items have already been
         * determined, but still have not been rendered onto the screen.*/
        final ViewTreeObserver observer = getViewTreeObserver();
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {

            @Override
            public boolean onPreDraw() {
                /* Determine if this is the first or second pass.*/
                if (!mShouldRemoveObserver) {
                    mShouldRemoveObserver = true;

                    /* Calculate what the parameters should be for setSelectionFromTop.
                     * The ListView must be offset in a way, such that after the animation
                     * takes place, all the cells that remain visible are rendered completely
                     * by the ListView.*/
                    int newTop = view.getTop();
                    int newBottom = view.getBottom();

                    int newHeight = newBottom - newTop;
                    int oldHeight = oldBottom - oldTop;
                    int delta = newHeight - oldHeight;

                    mTranslate = getExpandingTopAndBottomTranslations(getPositionForView(view), oldTop, oldBottom, delta);

                    int currentTop = view.getTop();
                    int futureTop = oldTop - mTranslate[0];

                    int firstChildStartTop = getChildAt(0).getTop();
                    int firstVisiblePosition = getFirstVisiblePosition();
                    int deltaTop = currentTop - futureTop;

                    int i;
                    int childCount = getChildCount();
                    for (i = 0; i < childCount; i++) {
                        View v = getChildAt(i);
                        int height = v.getBottom() - Math.max(0, v.getTop());
                        if (deltaTop - height > 0) {
                            firstVisiblePosition++;
                            deltaTop -= height;
                        } else {
                            break;
                        }
                    }

                    if (i > 0) {
                        firstChildStartTop = 0;
                    }

                    setSelectionFromTop(firstVisiblePosition, firstChildStartTop - deltaTop);

                    /* Request another layout to update the layout parameters of the cells.*/
                    requestLayout();

                    /* Return false such that the ListView does not redraw its contents on
                     * this layout but only updates all the parameters associated with its
                     * children.*/
                    return false;
                }

                /* Remove the predraw listener so this method does not keep getting called. */
                mShouldRemoveObserver = false;
                observer.removeOnPreDrawListener(this);

                int yTranslateTop = mTranslate[0];
                int yTranslateBottom = mTranslate[1];

                ArrayList <Animator> animations = new ArrayList<Animator>();

                int index = indexOfChild(view);

                /* Loop through all the views that were on the screen before the cell was
                 *  expanded. Some cells will still be children of the ListView while
                 *  others will not. The cells that remain children of the ListView
                 *  simply have their bounds animated appropriately. The cells that are no
                 *  longer children of the ListView also have their bounds animated, but
                 *  must also be added to a list of views which will be drawn in dispatchDraw.*/
                for (View v: oldCoordinates.keySet()) {
                    int[] old = oldCoordinates.get(v);

                    handleSetTopAndBottom(v, old[0], old[1]);
                    
                    if(v == view){
                        continue;
                    }
                    else if (v.getParent() == null) {
                        mViewsToDraw.add(v);
                        int delta = old[0] < oldTop ? -yTranslateTop : yTranslateBottom;
                        animations.add(getAnimation(v, old[0], old[1], delta, delta));
                        
                    } else {
                        int i = indexOfChild(v);
                        if (v != view) {
                            int delta = i > index ? yTranslateBottom : -yTranslateTop;
                            animations.add(getAnimation(v, old[0], old[1], delta, delta));
                        }
                        ViewCompat.setHasTransientState(v, false);
                    }
                }
                
                

                /* Adds animation for expanding the cell that was clicked. */
                int[] old = oldCoordinates.get(view);
                animations.add(getAnimation(view, old[0], old[1], -yTranslateTop, yTranslateBottom));

                /* Adds an animation for fading in the extra content. */
                animations.add(ObjectAnimator.ofFloat(view.findViewById(R.id.expanding_layout),
                        "alpha", 0, 1));

                /* Disabled the ListView for the duration of the animation.*/
                setEnabled(false);
                setClickable(false);

                /* Play all the animations created above together at the same time. */
                AnimatorSet s = new AnimatorSet();
                s.playTogether(animations);
                s.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        viewObject.setExpanded(true);
                        setEnabled(true);
                        setClickable(true);
                        if (mViewsToDraw.size() > 0) {
                            for (View v : mViewsToDraw) {
                                ViewCompat.setHasTransientState(v, false);
                            }
                        }
                        mViewsToDraw.clear();

                        if(mOnExpandingListener!=null){
                            mOnExpandingListener.onExpanded(view);
                        }
                    }
                });
                s.start();
                return true;
            }
        });
    }

    /**
     * By overriding dispatchDraw, we can draw the cells that disappear during the
     * expansion process. When the cell expands, some items below or above the expanding
     * cell may be moved off screen and are thus no longer children of the ListView's
     * layout. By storing a reference to these views prior to the layout, and
     * guaranteeing that these cells do not get recycled, the cells can be drawn
     * directly onto the canvas during the animation process. After the animation
     * completes, the references to the extra views can then be discarded.
     */
    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (mViewsToDraw.size() == 0) {
            return;
        }

        for (View v: mViewsToDraw) {
            canvas.translate(0, v.getTop());
            v.draw(canvas);
            canvas.translate(0, -v.getTop());
        }
    }

    /**
     * This method collapses the view that was clicked and animates all the views
     * around it to close around the collapsing view. There are several steps required
     * to do this which are outlined below.
     *
     * 1. Update the layout parameters of the view clicked so as to minimize its height
     *    to the original collapsed (default) state.
     * 2. After invoking a layout, the listview will shift all the cells so as to display
     *    them most efficiently. Therefore, during the first predraw pass, the listview
     *    must be offset by some amount such that given the custom bound change upon
     *    collapse, all the cells that need to be on the screen after the layout
     *    are rendered by the listview.
     * 3. On the second predraw pass, all the items are first returned to their original
     *    location (before the first layout).
     * 4. The collapsing view's bounds are animated to what the final values should be.
     * 5. The bounds above the collapsing view are animated downwards while the bounds
     *    below the collapsing view are animated upwards.
     * 6. The extra text is faded out as its contents become visible throughout the
     *    animation process.
     */
    private void collapseView(final View view) {
        final ExpandableListItem viewObject = (ExpandableListItem)getItemAtPosition
                (getPositionForView(view));

        /* Store the original top and bottom bounds of all the cells.*/
        final int oldTop = view.getTop();
        final int oldBottom = view.getBottom();

        int childCount = getChildCount();
        final HashMap<View, int[]> oldCoordinates = new HashMap<View, int[]>();
        
        for (int i = 0; i < childCount; i++) {
            View v = getChildAt(i);
            ViewCompat.setHasTransientState(v, true);
            oldCoordinates.put(v, new int [] {v.getTop(), v.getBottom()});
        }

        /* Update the layout so the extra content becomes invisible.*/
        view.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT,
                viewObject.getCollapsedHeight()));

        /* Add an onPreDraw listener. */
        final ViewTreeObserver observer = getViewTreeObserver();
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {

            @Override
            public boolean onPreDraw() {

                if (!mShouldRemoveObserver) {
                    /*Same as for expandingView, the parameters for setSelectionFromTop must
                     * be determined such that the necessary cells of the ListView are rendered
                     * and added to it.*/
                    mShouldRemoveObserver = true;

                    int newTop = view.getTop();
                    int newBottom = view.getBottom();

                    int newHeight = newBottom - newTop;
                    int oldHeight = oldBottom - oldTop;
                    int deltaHeight = oldHeight - newHeight;

                    mTranslate = getCollapsingTopAndBottomTranslation(getPositionForView(view), oldTop, oldBottom, deltaHeight);

                    int currentTop = view.getTop();
                    int futureTop = oldTop + mTranslate[0];

                    int firstChildStartTop = getChildAt(0).getTop();
                    int firstVisiblePosition = getFirstVisiblePosition();
                    int deltaTop = currentTop - futureTop;

                    int i;
                    int childCount = getChildCount();
                    for (i = 0; i < childCount; i++) {
                        View v = getChildAt(i);
                        int height = v.getBottom() - Math.max(0, v.getTop());
                        if (deltaTop - height > 0) {
                            firstVisiblePosition++;
                            deltaTop -= height;
                        } else {
                            break;
                        }
                    }

                    if (i > 0) {
                        firstChildStartTop = 0;
                    }

                    setSelectionFromTop(firstVisiblePosition, firstChildStartTop - deltaTop);

                    requestLayout();

                    return false;
                }

                mShouldRemoveObserver = false;
                observer.removeOnPreDrawListener(this);

                int yTranslateTop = mTranslate[0];
                int yTranslateBottom = mTranslate[1];

                int index = indexOfChild(view);
                int childCount = getChildCount();
                
                for (int i = 0; i < childCount; i++) {
                    View v = getChildAt(i);
                    int [] old = oldCoordinates.get(v);
                    if (old != null) {
                        /* If the cell was present in the ListView before the collapse and
                         * after the collapse then the bounds are reset to their old values.*/
                        handleSetTopAndBottom(v, old[0], old[1]);

                        ViewCompat.setHasTransientState(v, false);
                    } else {
                        /* If the cell is present in the ListView after the collapse but
                         * not before the collapse then the bounds are calculated using
                         * the bottom and top translation of the collapsing cell.*/
                        int delta = i > index ? yTranslateBottom : -yTranslateTop;                        
                        handleSetTopAndBottom(v, v.getTop() + delta, v.getBottom() + delta);
                    }
                }

                
                /* Animates all the cells present on the screen after the collapse. */
                ArrayList <Animator> animations = new ArrayList<Animator>();
                for (int i = 0; i < childCount; i++) {
                    View v = getChildAt(i);
                    if(v!=view){
                        int old[] = oldCoordinates.get(v);
                        float diff = i > index ? yTranslateBottom : yTranslateTop;
                        if(old!=null){
                            animations.add(getAnimation(v, old[0], old[1], diff, diff));
                        }
                        else{
                            animations.add(getAnimation(v, v.getTop()-(int)diff, v.getBottom()-(int)diff, diff, diff));
                        }

                    }
                }
                
                final View expandingLayout = view.findViewById (R.id.expanding_layout);


                /* Adds animation for collapsing the cell that was clicked. */
                int old[] = oldCoordinates.get(view);
                animations.add(getAnimation(view, old[0], old[1], yTranslateTop, (float)yTranslateBottom));

                /* Adds an animation for fading out the extra content. */
                animations.add(ObjectAnimator.ofFloat(expandingLayout, "alpha", 1, 0));

                /* Disabled the ListView for the duration of the animation.*/
                setEnabled(false);
                setClickable(false);

                /* Play all the animations created above together at the same time. */
                AnimatorSet s = new AnimatorSet();
                s.playTogether(animations);
                s.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        expandingLayout.setVisibility(View.GONE);
                        view.setLayoutParams(new AbsListView.LayoutParams(AbsListView
                                .LayoutParams.MATCH_PARENT, AbsListView.LayoutParams.WRAP_CONTENT));
                        viewObject.setExpanded(false);
                        setEnabled(true);
                        setClickable(true);
                        /* Note that alpha must be set back to 1 in case this view is reused
                         * by a cell that was expanded, but not yet collapsed, so its state
                         * should persist in an expanded state with the extra content visible.*/
                        ViewHelper.setAlpha(expandingLayout, 1);
                        
                        if(mOnExpandingListener!=null){
                            mOnExpandingListener.onCollasped(view);
                        }
                    }
                });
                s.start();

                return true;
            }
        });
    }

    /**
     * Translation animator object applied to the view
     * @param view view to set the animation
     * @param top current view top
     * @param bottom curret view bottom
     * @param translateTop amount of top translation to the view
     * @param translateBottom amount of bottom translation to the view
     * @return animator object
     */
    private Animator getAnimation(final View view, int top, int bottom, float translateTop, float translateBottom) {
        int endTop = (int)(top + translateTop);
        int endBottom = (int)(bottom + translateBottom);

        PropertyValuesHolder translationTop = PropertyValuesHolder.ofInt("top", top, endTop);
        PropertyValuesHolder translationBottom = PropertyValuesHolder.ofInt("bottom", bottom,
                endBottom);
        

        return ObjectAnimator.ofPropertyValuesHolder(view, translationTop, translationBottom);
    }
    
    /**
     * Sets the top and bottom of the view
     * @param v
     * @param top
     * @param bottom
     */
    private void handleSetTopAndBottom(View v, int top, int bottom){
        ViewGroup.LayoutParams p = this.getLayoutParams();
        if(p instanceof AbsListView.LayoutParams){
            AbsListView.LayoutParams lp = (AbsListView.LayoutParams)p;
            lp.height = bottom - top;
            this.setLayoutParams(lp);
            v.requestLayout();
        }
    }

    public OnExpandingListener getOnExpandingListener() {
        return mOnExpandingListener;
    }

    public void setOnExpandingListener(OnExpandingListener listener) {
        this.mOnExpandingListener = listener;
    }
}
