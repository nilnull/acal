package com.morphoss.acal.database.cachemanager;

import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.acaltime.AcalDateTime;

public class CacheWindow {

	private AcalDateTime windowStart = null;
	private AcalDateTime windowEnd = null;
	private AcalDateRange requestedWindow = null;

	/**
	 * Creeate a window object for maintaining cache window state. initial window can be null.
	 * @param initialWindow initial size of the window, can be null.
	 */
	public CacheWindow(AcalDateRange initialWindow) {
		if (initialWindow != null) {
			windowStart = initialWindow.start.clone();
			windowEnd = initialWindow.end.clone();
		}
	}
	
	/**
	 * Returns true if the requested range is within the window. otherwise false
	 */
	public boolean isWithinWindow(AcalDateRange range) {
		if (windowStart == null || windowEnd == null ) return false;
		if ( windowStart.after(range.start) ) return false;
		if ( windowEnd.before(range.end) ) return false;
		return true;
	}
	
	/**
	 * expand the requested range to incorporate the provided range
	 * @param range
	 */
	public void addToRequestedRange(AcalDateRange range) {
		if (requestedWindow == null) {
			this.requestedWindow = range.clone();
		}
		else if ( requestedWindow.start.before(range.start) && requestedWindow.end.after(range.end) )
			return;
		else {
			this.requestedWindow = new AcalDateRange(
					(requestedWindow.start.before(range.start) ? requestedWindow.start : range.start.clone()),
					(requestedWindow.end.after(range.end) ? requestedWindow.end : range.end.clone())
				);
		}
		
		//check requested ranges validity
		if (isWithinWindow(requestedWindow)) requestedWindow = null;
	}
	
	/**
	 * Returns the currently requested window range. can be null. 
	 * @return
	 */
	public AcalDateRange getRequestedWindow() {
		if (requestedWindow == null) return null;
		return this.requestedWindow.clone();
	}
	
	/**
	 * Arbitrarily set the window size. Will reset requested range if new window covers the current requested range
	 * @param range
	 */
	public void setWindowSize(AcalDateRange range) {
		if (range == null) { windowStart = null; return; }
		windowStart = range.start.clone();
		windowEnd = range.end.clone();

		//check requested ranges validity
		if (requestedWindow != null && isWithinWindow(requestedWindow)) requestedWindow = null;
	}
	
	/**
	 * Grow window size. the new window will be the union of the current window and the given range.
	 * Will reset requested range if new window covers the current requested range
	 * @param range
	 */

	public void expandWindow(AcalDateRange range) {
		if (windowStart == null) {
			windowStart = range.start.clone();
			windowEnd = range.end.clone();
			//check requested ranges validity
			if (requestedWindow != null && isWithinWindow(requestedWindow)) requestedWindow = null;
			return;
		}
		if (range.start.before(windowStart)) windowStart = range.start.clone();
		if (range.end.after(windowEnd)) windowEnd = range.end.clone();
		//check requested ranges validity
		if (requestedWindow != null && isWithinWindow(requestedWindow)) requestedWindow = null;
	}
	
	/**
	 * Reduce window size to the intersection of the current window and the given range.
	 * @param range
	 */
	public void reduceWindow(AcalDateRange range) {
		if (windowStart == null) {
			windowStart = range.start.clone();
			windowEnd = range.end.clone();
			//check requested ranges validity
			if (requestedWindow != null && isWithinWindow(requestedWindow)) requestedWindow = null;
			return;
		}
		
		if (range.start.after(windowStart)) windowStart = range.start.clone();
		if (range.end.before(windowEnd)) windowEnd = range.end.clone();
		//check requested ranges validity
		if (requestedWindow != null && isWithinWindow(requestedWindow)) requestedWindow = null;
	}

	/**
	 * returns a clone of the current window range or null if there is none.
	 * @return
	 */
	public AcalDateRange getCurrentWindow() {
		if (windowStart == null) return null;
		return new AcalDateRange(windowStart.clone(), windowEnd.clone());
	}
	
	@Override
	public String toString() {
		return "CacheWindow is ("+(windowStart==null?"<null<":windowStart.fmtIcal())+","+(windowEnd==null?">null>":windowEnd.fmtIcal())+") " +
				(requestedWindow == null ? "" : " requested "+requestedWindow);
	}
}