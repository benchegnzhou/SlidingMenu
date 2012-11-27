package com.slidingmenu.lib;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.view.KeyEventCompat;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.VelocityTrackerCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewConfigurationCompat;
import android.util.AttributeSet;
import android.util.FloatMath;
import android.util.Log;
import android.util.TypedValue;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.Scroller;

import com.slidingmenu.lib.SlidingMenu.OnClosedListener;
import com.slidingmenu.lib.SlidingMenu.OnOpenedListener;
//import com.slidingmenu.lib.SlidingMenu.OnCloseListener;
//import com.slidingmenu.lib.SlidingMenu.OnOpenListener;

public class CustomViewAbove extends ViewGroup {

	private static final String TAG = "CustomViewAbove";
	private static final boolean DEBUG = false;

	private static final boolean USE_CACHE = false;

	private static final int MAX_SETTLE_DURATION = 600; // ms
	private static final int MIN_DISTANCE_FOR_FLING = 25; // dips
	private static final int MARGIN_THRESHOLD = 20; // dips

	private static final Interpolator sInterpolator = new Interpolator() {
		public float getInterpolation(float t) {
			t -= 1.0f;
			return t * t * t * t * t + 1.0f;
		}
	};

	private View mContent;

	private int mCurItem;
	private Scroller mScroller;

	private int mShadowWidth;
	private Drawable mShadowDrawable;

	private boolean mScrollingCacheEnabled;

	private boolean mScrolling;

	private boolean mIsBeingDragged;
	private boolean mIsUnableToDrag;
	private int mTouchSlop;
	private float mInitialMotionX;
	/**
	 * Position of the last motion event.
	 */
	private float mLastMotionX;
	private float mLastMotionY;
	/**
	 * ID of the active pointer. This is used to retain consistency during
	 * drags/flings if multiple pointers are used.
	 */
	protected int mActivePointerId = INVALID_POINTER;
	/**
	 * Sentinel value for no current active pointer.
	 * Used by {@link #mActivePointerId}.
	 */
	private static final int INVALID_POINTER = -1;

	/**
	 * Determines speed during touch scrolling
	 */
	protected VelocityTracker mVelocityTracker;
	private int mMinimumVelocity;
	protected int mMaximumVelocity;
	private int mFlingDistance;
	private int mMarginThreshold;
	
	private CustomViewBehind mCustomViewBehind;
	private int mMode;
	private boolean mEnabled = true;

	private OnPageChangeListener mOnPageChangeListener;
	private OnPageChangeListener mInternalPageChangeListener;

	//	private OnCloseListener mCloseListener;
	//	private OnOpenListener mOpenListener;
	private OnClosedListener mClosedListener;
	private OnOpenedListener mOpenedListener;

	//	private int mScrollState = SCROLL_STATE_IDLE;

	/**
	 * Callback interface for responding to changing state of the selected page.
	 */
	public interface OnPageChangeListener {

		/**
		 * This method will be invoked when the current page is scrolled, either as part
		 * of a programmatically initiated smooth scroll or a user initiated touch scroll.
		 *
		 * @param position Position index of the first page currently being displayed.
		 *                 Page position+1 will be visible if positionOffset is nonzero.
		 * @param positionOffset Value from [0, 1) indicating the offset from the page at position.
		 * @param positionOffsetPixels Value in pixels indicating the offset from position.
		 */
		public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels);

		/**
		 * This method will be invoked when a new page becomes selected. Animation is not
		 * necessarily complete.
		 *
		 * @param position Position index of the new selected page.
		 */
		public void onPageSelected(int position);

	}

	/**
	 * Simple implementation of the {@link OnPageChangeListener} interface with stub
	 * implementations of each method. Extend this if you do not intend to override
	 * every method of {@link OnPageChangeListener}.
	 */
	public static class SimpleOnPageChangeListener implements OnPageChangeListener {

		public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
			// This space for rent
		}

		public void onPageSelected(int position) {
			// This space for rent
		}

		public void onPageScrollStateChanged(int state) {
			// This space for rent
		}

	}

	public CustomViewAbove(Context context) {
		this(context, null);
	}

	public CustomViewAbove(Context context, AttributeSet attrs) {
		this(context, attrs, true);
	}

	public CustomViewAbove(Context context, AttributeSet attrs, boolean isAbove) {
		super(context, attrs);
		initCustomViewAbove();
	}

	void initCustomViewAbove() {
		setWillNotDraw(false);
		setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
		setFocusable(true);
		final Context context = getContext();
		mScroller = new Scroller(context, sInterpolator);
		final ViewConfiguration configuration = ViewConfiguration.get(context);
		mTouchSlop = ViewConfigurationCompat.getScaledPagingTouchSlop(configuration);
		mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
		mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
		setInternalPageChangeListener(new SimpleOnPageChangeListener() {
			public void onPageSelected(int position) {
				if (mCustomViewBehind != null) {
					switch (position) {
					case 0:
					case 2:
						mCustomViewBehind.setChildrenEnabled(true);
						break;
					case 1:
						mCustomViewBehind.setChildrenEnabled(false);
						break;
					}
				}
			}
		});

		final float density = context.getResources().getDisplayMetrics().density;
		mFlingDistance = (int) (MIN_DISTANCE_FOR_FLING * density);
		mMarginThreshold = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 
				MARGIN_THRESHOLD, getResources().getDisplayMetrics());

	}

	/**
	 * Set the currently selected page. If the CustomViewPager has already been through its first
	 * layout there will be a smooth animated transition between the current item and the
	 * specified item.
	 *
	 * @param item Item index to select
	 */
	public void setCurrentItem(int item) {
		setCurrentItemInternal(item, true, false);
	}

	/**
	 * Set the currently selected page.
	 *
	 * @param item Item index to select
	 * @param smoothScroll True to smoothly scroll to the new item, false to transition immediately
	 */
	public void setCurrentItem(int item, boolean smoothScroll) {
		setCurrentItemInternal(item, smoothScroll, false);
	}

	public int getCurrentItem() {
		return mCurItem;
	}

	void setCurrentItemInternal(int item, boolean smoothScroll, boolean always) {
		setCurrentItemInternal(item, smoothScroll, always, 0);
	}

	void setCurrentItemInternal(int item, boolean smoothScroll, boolean always, int velocity) {
		//		if (!always && mCurItem == item && mMenu != null && mContent != null) {
		if (!always && mCurItem == item && mContent != null) {
			setScrollingCacheEnabled(false);
			return;
		}

		item = (item < 1) ? 0 : ((item > 1) ? 2 : 1);
		if (mMode == SlidingMenu.LEFT && item > 1)
			item = 0;
		if (mMode == SlidingMenu.RIGHT && item < 1)
			item = 2;

		final boolean dispatchSelected = mCurItem != item;
		mCurItem = item;
		final int destX = getDestScrollX(mCurItem);
		if (dispatchSelected && mOnPageChangeListener != null) {
			mOnPageChangeListener.onPageSelected(item);
		}
		if (dispatchSelected && mInternalPageChangeListener != null) {
			mInternalPageChangeListener.onPageSelected(item);
		}
		if (smoothScroll) {
			smoothScrollTo(destX, 0, velocity);
		} else {
			completeScroll();
			scrollTo(destX, 0);
		}
	}

	/**
	 * Set a listener that will be invoked whenever the page changes or is incrementally
	 * scrolled. See {@link OnPageChangeListener}.
	 *
	 * @param listener Listener to set
	 */
	public void setOnPageChangeListener(OnPageChangeListener listener) {
		mOnPageChangeListener = listener;
	}
	/*
	public void setOnOpenListener(OnOpenListener l) {
		mOpenListener = l;
	}

	public void setOnCloseListener(OnCloseListener l) {
		mCloseListener = l;
	}
	 */
	public void setOnOpenedListener(OnOpenedListener l) {
		mOpenedListener = l;
	}

	public void setOnClosedListener(OnClosedListener l) {
		mClosedListener = l;
	}

	/**
	 * Set a separate OnPageChangeListener for internal use by the support library.
	 *
	 * @param listener Listener to set
	 * @return The old listener that was set, if any.
	 */
	OnPageChangeListener setInternalPageChangeListener(OnPageChangeListener listener) {
		OnPageChangeListener oldListener = mInternalPageChangeListener;
		mInternalPageChangeListener = listener;
		return oldListener;
	}

	/**
	 * Set the margin between pages.
	 *
	 * @param shadowWidth Distance between adjacent pages in pixels
	 * @see #getShadowWidth()
	 * @see #setShadowDrawable(Drawable)
	 * @see #setShadowDrawable(int)
	 */
	public void setShadowWidth(int shadowWidth) {
		mShadowWidth = shadowWidth;
		invalidate();
	}

	/**
	 * Return the margin between pages.
	 *
	 * @return The size of the margin in pixels
	 */
	public int getShadowWidth() {
		return mShadowWidth;
	}

	/**
	 * Set a drawable that will be used to fill the margin between pages.
	 *
	 * @param d Drawable to display between pages
	 */
	public void setShadowDrawable(Drawable d) {
		mShadowDrawable = d;
		refreshDrawableState();
		setWillNotDraw(false);
		invalidate();
	}

	/**
	 * Set a drawable that will be used to fill the margin between pages.
	 *
	 * @param resId Resource ID of a drawable to display between pages
	 */
	public void setShadowDrawable(int resId) {
		setShadowDrawable(getContext().getResources().getDrawable(resId));
	}


	@Override
	protected boolean verifyDrawable(Drawable who) {
		return super.verifyDrawable(who) || who == mShadowDrawable;
	}


	@Override
	protected void drawableStateChanged() {
		super.drawableStateChanged();
		final Drawable d = mShadowDrawable;
		if (d != null && d.isStateful()) {
			d.setState(getDrawableState());
		}
	}

	// We want the duration of the page snap animation to be influenced by the distance that
	// the screen has to travel, however, we don't want this duration to be effected in a
	// purely linear fashion. Instead, we use this method to moderate the effect that the distance
	// of travel has on the overall snap duration.
	float distanceInfluenceForSnapDuration(float f) {
		f -= 0.5f; // center the values about 0.
		f *= 0.3f * Math.PI / 2.0f;
		return (float) FloatMath.sin(f);
	}

	public int getDestScrollX(int page) {
		switch (page) {
		case 0:
		case 2:
			if (mMode == SlidingMenu.LEFT) {
				return mContent.getLeft() - getBehindWidth();
			} else if (mMode == SlidingMenu.RIGHT) {
				return mContent.getLeft() + getBehindWidth();
			}
		case 1:
			return mContent.getLeft();
		}
		return 0;
	}

	private int getLeftBound() {
		if (mMode == SlidingMenu.LEFT) {
			return mContent.getLeft() - getBehindWidth();
		} else if (mMode == SlidingMenu.RIGHT) {
			return mContent.getLeft();
		}
		return 0;
	}

	private int getRightBound() {
		if (mMode == SlidingMenu.LEFT) {
			return mContent.getLeft();
		} else if (mMode == SlidingMenu.RIGHT) {
			return mContent.getLeft() + getBehindWidth();
		}
		return 0;
	}

	public int getContentLeft() {
		return mContent.getLeft() + mContent.getPaddingLeft();
	}

	public boolean isMenuOpen() {
		return mCurItem == 0 || mCurItem == 2;
	}

	public int getBehindWidth() {
		if (mCustomViewBehind == null) {
			return 0;
		} else {
			return mCustomViewBehind.getBehindWidth();
		}
	}

	public int getChildWidth(int i) {
		switch (i) {
		case 0:
			return getBehindWidth();
		case 1:
			return mContent.getWidth();
		default:
			return 0;
		}
	}

	public boolean isSlidingEnabled() {
		return mEnabled;
	}

	public void setSlidingEnabled(boolean b) {
		mEnabled = b;
	}

	/**
	 * Like {@link View#scrollBy}, but scroll smoothly instead of immediately.
	 *
	 * @param x the number of pixels to scroll by on the X axis
	 * @param y the number of pixels to scroll by on the Y axis
	 */
	void smoothScrollTo(int x, int y) {
		smoothScrollTo(x, y, 0);
	}

	/**
	 * Like {@link View#scrollBy}, but scroll smoothly instead of immediately.
	 *
	 * @param x the number of pixels to scroll by on the X axis
	 * @param y the number of pixels to scroll by on the Y axis
	 * @param velocity the velocity associated with a fling, if applicable. (0 otherwise)
	 */
	void smoothScrollTo(int x, int y, int velocity) {
		if (getChildCount() == 0) {
			// Nothing to do.
			setScrollingCacheEnabled(false);
			return;
		}
		int sx = getScrollX();
		int sy = getScrollY();
		int dx = x - sx;
		int dy = y - sy;
		if (dx == 0 && dy == 0) {
			completeScroll();
			if (isMenuOpen()) {
				if (mOpenedListener != null)
					mOpenedListener.onOpened();
			} else {
				if (mClosedListener != null)
					mClosedListener.onClosed();
			}
			return;
		}

		setScrollingCacheEnabled(true);
		mScrolling = true;

		final int width = getBehindWidth();
		final int halfWidth = width / 2;
		final float distanceRatio = Math.min(1f, 1.0f * Math.abs(dx) / width);
		final float distance = halfWidth + halfWidth *
				distanceInfluenceForSnapDuration(distanceRatio);

		int duration = 0;
		velocity = Math.abs(velocity);
		if (velocity > 0) {
			duration = 4 * Math.round(1000 * Math.abs(distance / velocity));
		} else {
			final float pageDelta = (float) Math.abs(dx) / (width + mShadowWidth);
			duration = (int) ((pageDelta + 1) * 100);
			duration = MAX_SETTLE_DURATION;
		}
		duration = Math.min(duration, MAX_SETTLE_DURATION);

		mScroller.startScroll(sx, sy, dx, dy, duration);
		invalidate();
	}

	public void setContent(View v) {
		if (mContent == null) 
			this.removeView(mContent);
		mContent = v;
		addView(mContent);
	}
	
	public View getContent() {
		return mContent;
	}

	public void setCustomViewBehind(CustomViewBehind cvb) {
		mCustomViewBehind = cvb;
	}

	public void setMode(int mode) {
		mMode = mode;
		if (mMode == SlidingMenu.RIGHT && mCurItem == 0)
			mCurItem = 2;
		if (mMode == SlidingMenu.LEFT && mCurItem == 2)
			mCurItem = 0;
	}
	
	public int getMode() {
		return mMode;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

		int width = getDefaultSize(0, widthMeasureSpec);
		int height = getDefaultSize(0, heightMeasureSpec);
		setMeasuredDimension(width, height);

		final int contentWidth = getChildMeasureSpec(widthMeasureSpec, 0, width);
		final int contentHeight = getChildMeasureSpec(heightMeasureSpec, 0, height);
		mContent.measure(contentWidth, contentHeight);
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		// Make sure scroll position is set correctly.
		if (w != oldw) {
			// [ChrisJ] - This fixes the onConfiguration change for orientation issue..
			// maybe worth having a look why the recomputeScroll pos is screwing
			// up?
			completeScroll();
			scrollTo(getDestScrollX(mCurItem), getScrollY());
		}
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		final int width = r - l;
		final int height = b - t;		
		mContent.layout(0, 0, width, height);
	}

	public void setAboveOffset(int i) {
		//		RelativeLayout.LayoutParams params = ((RelativeLayout.LayoutParams)mContent.getLayoutParams());
		//		params.setMargins(i, params.topMargin, params.rightMargin, params.bottomMargin);
		mContent.setPadding(i, mContent.getPaddingTop(), 
				mContent.getPaddingRight(), mContent.getPaddingBottom());
	}


	@Override
	public void computeScroll() {
		if (!mScroller.isFinished()) {
			if (mScroller.computeScrollOffset()) {
				if (DEBUG) Log.i(TAG, "computeScroll: still scrolling");
				int oldX = getScrollX();
				int oldY = getScrollY();
				int x = mScroller.getCurrX();
				int y = mScroller.getCurrY();

				if (oldX != x || oldY != y) {
					scrollTo(x, y);
					pageScrolled(x);
				}

				// Keep on drawing until the animation has finished.
				invalidate();
				return;
			}
		}

		// Done with scroll, clean up state.
		completeScroll();
	}

	private void pageScrolled(int xpos) {
		final int widthWithMargin = getWidth();
		final int position = xpos / widthWithMargin;
		final int offsetPixels = xpos % widthWithMargin;
		final float offset = (float) offsetPixels / widthWithMargin;

		onPageScrolled(position, offset, offsetPixels);
	}

	/**
	 * This method will be invoked when the current page is scrolled, either as part
	 * of a programmatically initiated smooth scroll or a user initiated touch scroll.
	 * If you override this method you must call through to the superclass implementation
	 * (e.g. super.onPageScrolled(position, offset, offsetPixels)) before onPageScrolled
	 * returns.
	 *
	 * @param position Position index of the first page currently being displayed.
	 *                 Page position+1 will be visible if positionOffset is nonzero.
	 * @param offset Value from [0, 1) indicating the offset from the page at position.
	 * @param offsetPixels Value in pixels indicating the offset from position.
	 */
	protected void onPageScrolled(int position, float offset, int offsetPixels) {
		if (mOnPageChangeListener != null) {
			mOnPageChangeListener.onPageScrolled(position, offset, offsetPixels);
		}
		if (mInternalPageChangeListener != null) {
			mInternalPageChangeListener.onPageScrolled(position, offset, offsetPixels);
		}
	}

	private void completeScroll() {
		boolean needPopulate = mScrolling;
		if (needPopulate) {
			// Done with scroll, no longer want to cache view drawing.
			setScrollingCacheEnabled(false);
			mScroller.abortAnimation();
			int oldX = getScrollX();
			int oldY = getScrollY();
			int x = mScroller.getCurrX();
			int y = mScroller.getCurrY();
			if (oldX != x || oldY != y) {
				scrollTo(x, y);
			}
			if (isMenuOpen()) {
				if (mOpenedListener != null)
					mOpenedListener.onOpened();
			} else {
				if (mClosedListener != null)
					mClosedListener.onClosed();
			}
		}
		mScrolling = false;
	}

	protected int mTouchMode = SlidingMenu.TOUCHMODE_MARGIN;

	public void setTouchMode(int i) {
		mTouchMode = i;
	}

	public int getTouchMode() {
		return mTouchMode;
	}

	private boolean thisTouchAllowed(MotionEvent ev) {
		int x = (int) (ev.getX() + mScrollX);
		if (isMenuOpen()) {
			if (mMode == SlidingMenu.LEFT) {
				return x >= getContentLeft();
			} else if (mMode == SlidingMenu.RIGHT) {
				return x <= mContent.getRight();
			}
		} else {
			switch (mTouchMode) {
			case SlidingMenu.TOUCHMODE_FULLSCREEN:
				return true;
			case SlidingMenu.TOUCHMODE_NONE:
				return false;
			case SlidingMenu.TOUCHMODE_MARGIN:
				if (mMode == SlidingMenu.LEFT) {
					int left = getContentLeft();
					return (x >= left && x <= mMarginThreshold + left);
				} else if (mMode == SlidingMenu.RIGHT) {
					int right = mContent.getRight();
					return (x <= right && x >= right - mMarginThreshold);
				}
			}
		}
		return false;
	}

	private boolean thisSlideAllowed(float dx) {
		boolean allowed = false;
		if (isMenuOpen()) {
			if (mMode == SlidingMenu.LEFT) {
				allowed = dx < 0;
			} else if (mMode == SlidingMenu.RIGHT) {
				allowed = dx > 0;
			}
		} else if (mCustomViewBehind != null) {
			if (mMode == SlidingMenu.LEFT) {
				allowed = dx > 0;
			} else if (mMode == SlidingMenu.RIGHT) {
				allowed = dx < 0;
			}
		}
		if (DEBUG)
			Log.v(TAG, "this slide allowed " + allowed);
		return allowed;
	}

	private int getPointerIndex(MotionEvent ev, int id) {
		int activePointerIndex = MotionEventCompat.findPointerIndex(ev, id);
		if (activePointerIndex == -1)
			mActivePointerId = INVALID_POINTER;
		return activePointerIndex;
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {

		if (!mEnabled)
			return false;

		final int action = ev.getAction() & MotionEventCompat.ACTION_MASK;

		if (action == MotionEvent.ACTION_DOWN && DEBUG)
			Log.v(TAG, "Received ACTION_DOWN");

		if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP
				|| (action != MotionEvent.ACTION_DOWN && mIsUnableToDrag)) {
			endDrag();
			return false;
		}

		switch (action) {
		case MotionEvent.ACTION_MOVE:
			final int activePointerId = mActivePointerId;
			if (activePointerId == INVALID_POINTER)
				break;

			final int pointerIndex = this.getPointerIndex(ev, activePointerId);
			if (mActivePointerId == INVALID_POINTER)
				break;
			final float x = MotionEventCompat.getX(ev, pointerIndex);
			final float dx = x - mLastMotionX;
			final float xDiff = Math.abs(dx);
			final float y = MotionEventCompat.getY(ev, pointerIndex);
			final float yDiff = Math.abs(y - mLastMotionY);
			if (!mIsUnableToDrag && xDiff > mTouchSlop && xDiff > yDiff && thisSlideAllowed(dx)) {
				if (DEBUG) Log.v(TAG, "Starting drag! from onInterceptTouch");
				mIsBeingDragged = true;
				mLastMotionX = x;
				setScrollingCacheEnabled(true);
			} else if (yDiff > mTouchSlop) {
				mIsUnableToDrag = true;
			}
			break;

		case MotionEvent.ACTION_DOWN:
			mActivePointerId = ev.getAction() & ((Build.VERSION.SDK_INT >= 8) ? MotionEvent.ACTION_POINTER_INDEX_MASK : 
				MotionEvent.ACTION_POINTER_INDEX_MASK);
			mLastMotionX = mInitialMotionX = MotionEventCompat.getX(ev, mActivePointerId);
			mLastMotionY = MotionEventCompat.getY(ev, mActivePointerId);
			if (thisTouchAllowed(ev)) {
				mIsBeingDragged = false;
				mIsUnableToDrag = false;
				if (isMenuOpen())
					return true;
			} else {
				mIsUnableToDrag = true;
			}
			break;
		case MotionEventCompat.ACTION_POINTER_UP:
			onSecondaryPointerUp(ev);
			break;
		}

		if (!mIsBeingDragged) {
			if (mVelocityTracker == null) {
				mVelocityTracker = VelocityTracker.obtain();
			}
			mVelocityTracker.addMovement(ev);
		}

		return mIsBeingDragged;
	}


	@Override
	public boolean onTouchEvent(MotionEvent ev) {

		if (!mEnabled)
			return false;

		if (!mIsBeingDragged && !thisTouchAllowed(ev))
			return false;

		final int action = ev.getAction();

		if (mVelocityTracker == null) {
			mVelocityTracker = VelocityTracker.obtain();
		}
		mVelocityTracker.addMovement(ev);

		switch (action & MotionEventCompat.ACTION_MASK) {
		case MotionEvent.ACTION_DOWN:
			/*
			 * If being flinged and user touches, stop the fling. isFinished
			 * will be false if being flinged.
			 */
			completeScroll();

			// Remember where the motion event started
			mLastMotionX = mInitialMotionX = ev.getX();
			mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
			break;
		case MotionEvent.ACTION_MOVE:
			if (!mIsBeingDragged) {
				final int pointerIndex = getPointerIndex(ev, mActivePointerId);
				if (mActivePointerId == INVALID_POINTER) {
					break;
				}
				final float x = MotionEventCompat.getX(ev, pointerIndex);
				final float xDiff = Math.abs(x - mLastMotionX);
				final float y = MotionEventCompat.getY(ev, pointerIndex);
				final float yDiff = Math.abs(y - mLastMotionY);
				if (DEBUG) Log.v(TAG, "Moved x to " + x + "," + y + " diff=" + xDiff + "," + yDiff);
				if (xDiff > mTouchSlop && xDiff > yDiff) {
					if (DEBUG) Log.v(TAG, "Starting drag! from onTouch");
					mIsBeingDragged = true;
					mLastMotionX = x;
					setScrollingCacheEnabled(true);
				}
			}
			if (mIsBeingDragged) {
				// Scroll to follow the motion event
				final int activePointerIndex = getPointerIndex(ev, mActivePointerId);
				if (mActivePointerId == INVALID_POINTER) {
					break;
				}
				final float x = MotionEventCompat.getX(ev, activePointerIndex);
				final float deltaX = mLastMotionX - x;
				mLastMotionX = x;
				float oldScrollX = getScrollX();
				float scrollX = oldScrollX + deltaX;
				final float leftBound = getLeftBound();
				final float rightBound = getRightBound();
				if (scrollX < leftBound) {
					scrollX = leftBound;
				} else if (scrollX > rightBound) {
					scrollX = rightBound;
				}
				// Don't lose the rounded component
				mLastMotionX += scrollX - (int) scrollX;
				scrollTo((int) scrollX, getScrollY());
				pageScrolled((int) scrollX);
			}
			break;
		case MotionEvent.ACTION_UP:
			if (mIsBeingDragged) {
				final VelocityTracker velocityTracker = mVelocityTracker;
				velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
				int initialVelocity = (int) VelocityTrackerCompat.getXVelocity(
						velocityTracker, mActivePointerId);
				final int widthWithMargin = getWidth();
				final int scrollX = getScrollX();
				final float pageOffset = (float) (scrollX % widthWithMargin) / widthWithMargin;
				final int activePointerIndex = getPointerIndex(ev, mActivePointerId);
				if (mActivePointerId != INVALID_POINTER) {
					final float x = MotionEventCompat.getX(ev, activePointerIndex);
					final int totalDelta = (int) (x - mInitialMotionX);
					int nextPage = determineTargetPage(pageOffset, initialVelocity, totalDelta);
					setCurrentItemInternal(nextPage, true, true, initialVelocity);
				} else {	
					setCurrentItemInternal(mCurItem, true, true, initialVelocity);
				}
				mActivePointerId = INVALID_POINTER;
				endDrag();
			} else if (isMenuOpen()) {
				// close the menu
				setCurrentItem(1);
			}
			break;
		case MotionEvent.ACTION_CANCEL:
			if (mIsBeingDragged) {
				setCurrentItemInternal(mCurItem, true, true);
				mActivePointerId = INVALID_POINTER;
				endDrag();
			}
			break;
		case MotionEventCompat.ACTION_POINTER_DOWN: {
			final int index = MotionEventCompat.getActionIndex(ev);
			final float x = MotionEventCompat.getX(ev, index);
			mLastMotionX = x;
			mActivePointerId = MotionEventCompat.getPointerId(ev, index);
			break;
		}
		case MotionEventCompat.ACTION_POINTER_UP:
			onSecondaryPointerUp(ev);
			int pointerIndex = this.getPointerIndex(ev, mActivePointerId);
			if (mActivePointerId == INVALID_POINTER)
				break;
			mLastMotionX = MotionEventCompat.getX(ev, pointerIndex);
			break;
		}
		return true;
	}

	private float mScrollScale;

	public float getScrollScale() {
		return mScrollScale;
	}

	public void setScrollScale(float f) {
		if (f < 0 && f > 1)
			throw new IllegalStateException("ScrollScale must be between 0 and 1");
		mScrollScale = f;
	}

	@Override
	public void scrollTo(int x, int y) {
		super.scrollTo(x, y);
		mScrollX = x;
		if (mCustomViewBehind != null && mEnabled) {
			int vis = View.VISIBLE;
			if (mMode == SlidingMenu.LEFT) {
				if (x >= getContentLeft()) vis = View.GONE;
				mCustomViewBehind.scrollTo((int)((x + getBehindWidth())*mScrollScale), y);
			} else if (mMode == SlidingMenu.RIGHT) {
				if (x <= getContentLeft()) vis = View.GONE;
				mCustomViewBehind.scrollTo((int)(getBehindWidth() - getWidth() + 
						(x-getBehindWidth())*mScrollScale), y);
			}
			mCustomViewBehind.setVisibility(vis);
		}	
		if (mShadowDrawable != null || mSelectorDrawable != null)
			invalidate();
	}

	private int determineTargetPage(float pageOffset, int velocity, int deltaX) {
		int targetPage = mCurItem;
		if (Math.abs(deltaX) > mFlingDistance && Math.abs(velocity) > mMinimumVelocity) {
			//			if (mMode == SlidingMenu.LEFT) {
			targetPage += velocity > 0 ? -1: 1;
			//			} else if (mMode == SlidingMenu.RIGHT) {
			//				targetPage += velocity > 0 ? 0: 1;
			//			}
		} else {
			targetPage = (int) Math.round(mCurItem + pageOffset);
		}
		return targetPage;
	}

	protected float getPercentOpen() {
		return Math.abs(mScrollX) / getBehindWidth();
	}

	@Override
	protected void dispatchDraw(Canvas canvas) {
		super.dispatchDraw(canvas);
		// Draw the margin drawable if needed.
		if (mShadowWidth > 0 && mShadowDrawable != null) {
			onDrawShadow(canvas);
		}

		if (mFadeEnabled)
			onDrawBehindFade(canvas, getPercentOpen());

		if (mSelectorEnabled)
			onDrawMenuSelector(canvas, getPercentOpen());
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
	}

	// variables for drawing
	private float mScrollX = 0.0f;
	// for the fade
	private boolean mFadeEnabled;
	private float mFadeDegree = 1.0f;
	private final Paint mBehindFadePaint = new Paint();
	// for the indicator
	private boolean mSelectorEnabled = true;
	private Bitmap mSelectorDrawable;
	private View mSelectedView;

	private void onDrawShadow(Canvas canvas) {
		if (mShadowDrawable == null || mShadowWidth <= 0)
			return;
		int left = 0;
		if (mMode == SlidingMenu.LEFT) {
			left = getContentLeft() - mShadowWidth;
		} else if (mMode == SlidingMenu.RIGHT) {
			left = mContent.getRight();
		}
		mShadowDrawable.setBounds(left, 0, left + mShadowWidth, getHeight());
		mShadowDrawable.draw(canvas);
	}


	private void onDrawBehindFade(Canvas canvas, float openPercent) {
		final int alpha = (int) (mFadeDegree * 255 * Math.abs(1-openPercent));
		if (alpha > 0) {
			mBehindFadePaint.setColor(Color.argb(alpha, 0, 0, 0));
			if (mMode == SlidingMenu.LEFT) {
				canvas.drawRect(getDestScrollX(0), 0, getContentLeft(), getHeight(), mBehindFadePaint);
			} else if (mMode == SlidingMenu.RIGHT) {
				canvas.drawRect(mContent.getRight(), 0, mContent.getRight() + getBehindWidth(), getHeight(), mBehindFadePaint);				
			}
		}
	}

	private void onDrawMenuSelector(Canvas canvas, float openPercent) {
		if (mSelectorDrawable != null && mSelectedView != null) {
			String tag = (String) mSelectedView.getTag(R.id.selected_view);
			if (tag.equals(TAG+"SelectedView")) {
				int right = getContentLeft();
				int left = (int) (right - mSelectorDrawable.getWidth() * openPercent);

				canvas.save();
				canvas.clipRect(left, 0, right, getHeight());
				canvas.drawBitmap(mSelectorDrawable, left, getSelectedTop(), null);
				canvas.restore();
			}
		}
	}

	public void setBehindFadeEnabled(boolean b) {
		mFadeEnabled = b;
	}

	public void setBehindFadeDegree(float f) {
		if (f > 1.0f || f < 0.0f)
			throw new IllegalStateException("The BehindFadeDegree must be between 0.0f and 1.0f");
		mFadeDegree = f;
	}

	public void setSelectorEnabled(boolean b) {
		mSelectorEnabled = b;
	}

	public void setSelectedView(View v) {
		if (mSelectedView != null) {
			mSelectedView.setTag(R.id.selected_view, null);
			mSelectedView = null;
		}
		if (v.getParent() != null) {
			mSelectedView = v;
			mSelectedView.setTag(R.id.selected_view, TAG+"SelectedView");
			invalidate();
		}
	}

	private int getSelectedTop() {
		int y = mSelectedView.getTop();
		y += (mSelectedView.getHeight() - mSelectorDrawable.getHeight()) / 2;
		return y;
	}

	public void setSelectorBitmap(Bitmap b) {
		mSelectorDrawable = b;
		refreshDrawableState();
	}

	private void onSecondaryPointerUp(MotionEvent ev) {
		if (DEBUG) Log.v(TAG, "onSecondaryPointerUp called");
		final int pointerIndex = MotionEventCompat.getActionIndex(ev);
		final int pointerId = MotionEventCompat.getPointerId(ev, pointerIndex);
		if (pointerId == mActivePointerId) {
			// This was our active pointer going up. Choose a new
			// active pointer and adjust accordingly.
			final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
			mLastMotionX = MotionEventCompat.getX(ev, newPointerIndex);
			mActivePointerId = MotionEventCompat.getPointerId(ev, newPointerIndex);
			if (mVelocityTracker != null) {
				mVelocityTracker.clear();
			}
		}
	}

	private void endDrag() {
		mIsBeingDragged = false;
		mIsUnableToDrag = false;
		mActivePointerId = INVALID_POINTER;

		if (mVelocityTracker != null) {
			mVelocityTracker.recycle();
			mVelocityTracker = null;
		}
	}

	private void setScrollingCacheEnabled(boolean enabled) {
		if (mScrollingCacheEnabled != enabled) {
			mScrollingCacheEnabled = enabled;
			if (USE_CACHE) {
				final int size = getChildCount();
				for (int i = 0; i < size; ++i) {
					final View child = getChildAt(i);
					if (child.getVisibility() != GONE) {
						child.setDrawingCacheEnabled(enabled);
					}
				}
			}
		}
	}

	/**
	 * Tests scrollability within child views of v given a delta of dx.
	 *
	 * @param v View to test for horizontal scrollability
	 * @param checkV Whether the view v passed should itself be checked for scrollability (true),
	 *               or just its children (false).
	 * @param dx Delta scrolled in pixels
	 * @param x X coordinate of the active touch point
	 * @param y Y coordinate of the active touch point
	 * @return true if child views of v can be scrolled by delta of dx.
	 */
	protected boolean canScroll(View v, boolean checkV, int dx, int x, int y) {
		if (v instanceof ViewGroup) {
			final ViewGroup group = (ViewGroup) v;
			final int scrollX = v.getScrollX();
			final int scrollY = v.getScrollY();
			final int count = group.getChildCount();
			// Count backwards - let topmost views consume scroll distance first.
			for (int i = count - 1; i >= 0; i--) {
				final View child = group.getChildAt(i);
				if (x + scrollX >= child.getLeft() && x + scrollX < child.getRight() &&
						y + scrollY >= child.getTop() && y + scrollY < child.getBottom() &&
						canScroll(child, true, dx, x + scrollX - child.getLeft(),
								y + scrollY - child.getTop())) {
					return true;
				}
			}
		}

		return checkV && ViewCompat.canScrollHorizontally(v, -dx);
	}


	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		// Let the focused view and/or our descendants get the key first
		return super.dispatchKeyEvent(event) || executeKeyEvent(event);
	}

	/**
	 * You can call this function yourself to have the scroll view perform
	 * scrolling from a key event, just as if the event had been dispatched to
	 * it by the view hierarchy.
	 *
	 * @param event The key event to execute.
	 * @return Return true if the event was handled, else false.
	 */
	public boolean executeKeyEvent(KeyEvent event) {
		boolean handled = false;
		if (event.getAction() == KeyEvent.ACTION_DOWN) {
			switch (event.getKeyCode()) {
			case KeyEvent.KEYCODE_DPAD_LEFT:
				handled = arrowScroll(FOCUS_LEFT);
				break;
			case KeyEvent.KEYCODE_DPAD_RIGHT:
				handled = arrowScroll(FOCUS_RIGHT);
				break;
			case KeyEvent.KEYCODE_TAB:
				if (Build.VERSION.SDK_INT >= 11) {
					// The focus finder had a bug handling FOCUS_FORWARD and FOCUS_BACKWARD
					// before Android 3.0. Ignore the tab key on those devices.
					if (KeyEventCompat.hasNoModifiers(event)) {
						handled = arrowScroll(FOCUS_FORWARD);
					} else if (KeyEventCompat.hasModifiers(event, KeyEvent.META_SHIFT_ON)) {
						handled = arrowScroll(FOCUS_BACKWARD);
					}
				}
				break;
			}
		}
		return handled;
	}

	public boolean arrowScroll(int direction) {
		View currentFocused = findFocus();
		if (currentFocused == this) currentFocused = null;

		boolean handled = false;

		View nextFocused = FocusFinder.getInstance().findNextFocus(this, currentFocused,
				direction);
		if (nextFocused != null && nextFocused != currentFocused) {
			if (direction == View.FOCUS_LEFT) {
				// If there is nothing to the left, or this is causing us to
				// jump to the right, then what we really want to do is page left.
				if (currentFocused != null && nextFocused.getLeft() >= currentFocused.getLeft()) {
					handled = pageLeft();
				} else {
					handled = nextFocused.requestFocus();
				}
			} else if (direction == View.FOCUS_RIGHT) {
				// If there is nothing to the right, or this is causing us to
				// jump to the left, then what we really want to do is page right.
				if (currentFocused != null && nextFocused.getLeft() <= currentFocused.getLeft()) {
					handled = pageRight();
				} else {
					handled = nextFocused.requestFocus();
				}
			}
		} else if (direction == FOCUS_LEFT || direction == FOCUS_BACKWARD) {
			// Trying to move left and nothing there; try to page.
			handled = pageLeft();
		} else if (direction == FOCUS_RIGHT || direction == FOCUS_FORWARD) {
			// Trying to move right and nothing there; try to page.
			handled = pageRight();
		}
		if (handled) {
			playSoundEffect(SoundEffectConstants.getContantForFocusDirection(direction));
		}
		return handled;
	}

	boolean pageLeft() {
		if (mCurItem > 0) {
			setCurrentItem(mCurItem-1, true);
			return true;
		}
		return false;
	}

	boolean pageRight() {
		if (mCurItem < 1) {
			setCurrentItem(mCurItem+1, true);
			return true;
		}
		return false;
	}

}
