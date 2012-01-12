package com.morphoss.acal.weekview;

import java.util.ArrayList;
import java.util.Collections;

import android.content.ContentValues;
import android.content.Context;
import android.os.Handler;
import android.os.Message;

import com.morphoss.acal.acaltime.AcalDateRange;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.database.DataChangeEvent;
import com.morphoss.acal.database.cachemanager.CRObjectsInWindow;
import com.morphoss.acal.database.cachemanager.CacheChangedEvent;
import com.morphoss.acal.database.cachemanager.CacheChangedListener;
import com.morphoss.acal.database.cachemanager.CacheManager;
import com.morphoss.acal.database.cachemanager.CacheObject;
import com.morphoss.acal.database.cachemanager.CacheResponse;
import com.morphoss.acal.database.cachemanager.CacheResponseListener;
import com.morphoss.acal.database.cachemanager.CacheWindow;
import com.morphoss.acal.weekview.WeekViewDays.WVCacheObject;

/**
 * This class provides an in memory cache of event data for weekview to prevent unnecessarily making cache requests.
 * @author Chris Noldus
 *
 */
public class WeekViewCache implements CacheChangedListener, CacheResponseListener<ArrayList<CacheObject>> {

	private WeekViewDays callback;
	private CacheManager cm;
	private ArrayList<WVCacheObject> partDayEventsInRange = new ArrayList<WVCacheObject>();
	private ArrayList<WVCacheObject> fullDayEventsInRange = new ArrayList<WVCacheObject>();
	private CacheWindow window;
	private int lastMaxX;
	
	
	private ArrayList<WVCacheObject> HSimpleList;  	//The last list of (simple) events for the header
	private WVCacheObject[][] HTimetable;  			//The last timetable used for the header

	
	
	private static final int HANDLE_SAVE_NEW_DATA = 1;
	private static final int HANDLE_RESET = 2;
	
	private Handler mHandler = new Handler() {
		@SuppressWarnings("unchecked")
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case HANDLE_SAVE_NEW_DATA:
					partDayEventsInRange.addAll((ArrayList<WVCacheObject>)((Object[])msg.obj)[0]);
					fullDayEventsInRange.addAll((ArrayList<WVCacheObject>)((Object[])msg.obj)[1]);
					callback.refreshDrawableState();
					break;
				case HANDLE_RESET: {
					window = new CacheWindow(null);
					partDayEventsInRange.clear();
					fullDayEventsInRange.clear();
					callback.refreshDrawableState();
				}
			}
		}
	};
	
	public WeekViewCache(Context context, WeekViewDays callback) {
		this.callback = callback;
		cm = CacheManager.getInstance(context, this);
		window = new CacheWindow(null);
	}
	
	public void close() {
		cm.removeListener(this);
	}

	private void loadDataForRange(AcalDateRange range) {
		window.addToRequestedRange(range);
		cm.sendRequest(new CRObjectsInWindow(this));
	}
	
	@Override
	public void cacheChanged(CacheChangedEvent event) {
	
		//1 Check if any of the changes affect the current range
		boolean affected = false;
		for (DataChangeEvent e : event.getChanges()) {
			ContentValues cv = e.getData();
			CacheObject co = CacheObject.fromContentValues(cv);
			AcalDateRange range = new AcalDateRange(co.getStartDateTime(), co.getEndDateTime());
			if (range.overlaps(window.getCurrentWindow())) {
				affected = true;
				break;
			}
		}
		//2 if so, wipe existing data
		if (affected) {
			mHandler.sendMessage(mHandler.obtainMessage(HANDLE_RESET));
		}
	}
	
	@Override
	public void cacheResponse(CacheResponse<ArrayList<CacheObject>> response) {
		ArrayList<WVCacheObject> partDay = new ArrayList<WVCacheObject>();
		ArrayList<WVCacheObject> fullDay = new ArrayList<WVCacheObject>();
		for (CacheObject co: response.result()) {
			if (co.isAllDay()) fullDay.add(callback.new WVCacheObject(co));
			else partDay.add(callback.new WVCacheObject(co));
		}
		Object[] obj = new Object[]{partDay,fullDay};
		
		mHandler.sendMessage(mHandler.obtainMessage(HANDLE_SAVE_NEW_DATA, obj));
		
	}
	
	public CacheWindow getWindow() {
		return this.window;
	}

	
	/**
	 * Calculates a timetable of rows to place horizontal (multi-day) events in order
	 * that the events do not overlap one other.
	 * @param range A one-dimensional array of events
	 * @return A two-dimensional array of events
	 */
	public WVCacheObject[][] getMultiDayTimeTable(AcalDateRange range, WeekViewDays view, long HST, long HET, long HDepth) {
		//first check to see if we even have the requested range
		if (!window.isWithinWindow(range)) {
			this.loadDataForRange(range);
			return new WVCacheObject[0][0];
		}
		
		//first we need to construct a list of WVChacheObjects for the requested range
		ArrayList<WVCacheObject> eventsForMultiDays = new ArrayList<WVCacheObject>();
		for (WVCacheObject wvo : this.fullDayEventsInRange) if (wvo.getRange().overlaps(range)) eventsForMultiDays.add(wvo);
		
		//List<EventInstance> events = context.getEventsForDays(range, WeekViewActivity.INCLUDE_ALL_DAY_EVENTS );
		if (HTimetable != null && HSimpleList != null) {
			if (HSimpleList.containsAll(eventsForMultiDays) && eventsForMultiDays.size() == HSimpleList.size()) return HTimetable;
		}
		HSimpleList = eventsForMultiDays;
		Collections.sort(HSimpleList);

		WVCacheObject[][] timetable = new WVCacheObject[HSimpleList.size()][HSimpleList.size()]; //maximum possible
		int depth = 0;
		for (int x = 0; x < HSimpleList.size(); x++) {
			WVCacheObject co = HSimpleList.get(x);
			if ( co.getStart() >= HET || co.getEnd() <= HST ) continue;  // Discard any events out of range.
			int i = 0;
			boolean go = true;
			while(go) {
				WVCacheObject[] row = timetable[i];
				int j=0;
				while(true) {
					if (row[j] == null) { row[j] = co; go=false; break; }
					else if (!(row[j].getEnd() > (co.getStart()))) {j++; continue; }
					else break;
				}
				i++;
			}
			depth = Math.max(depth,i);
		}
		HDepth = depth;
		HTimetable = timetable;
		return timetable; 
	}

	/**
	 * Calculates a timetable for the layout of the events within a day.
	 * @param day to do the timetable for
	 * @return An array of lists, one per column
	 */
	public WVCacheObject[][] getInDayTimeTable(AcalDateTime currentDay, WeekViewDays weekViewDays) {
		
		AcalDateTime dayStart = currentDay.clone().setDaySecond(0);
		AcalDateTime dayEnd = dayStart.clone().addDays(1);
		AcalDateRange range = new AcalDateRange(dayStart,dayEnd);
		
		//first check to see if we even have the requested range
		if (!window.isWithinWindow(range)) {
			this.loadDataForRange(range);
			return new WVCacheObject[0][0];
		}
		
		//first we need to construct a list of WVChacheObjects for the requested range
		ArrayList<WVCacheObject> eventsForDays = new ArrayList<WVCacheObject>();
		for (WVCacheObject wvo : this.partDayEventsInRange) if (wvo.getRange().overlaps(range)) eventsForDays.add(wvo);
		
		Collections.sort(eventsForDays);
		WVCacheObject[][] timetable = new WVCacheObject[eventsForDays.size()][eventsForDays.size()]; //maximum possible
		int maxX = 0;
		for (WVCacheObject co :eventsForDays){
			int x = 0; int y = 0;
			while (timetable[y][x] != null) {
				if (co.overlaps(timetable[y][x])) {

					//if our end time is before [y][x]'s, we need to extend[y][x]'s range
					if (co.getEnd() < (timetable[y][x].getEnd())) timetable[y+1][x] = timetable[y][x];
					x++;
				} else {
					y++;
				}
			}
			timetable[y][x] = co;
			if (x > maxX) maxX = x;
		}
		lastMaxX = maxX;
		return timetable;
	}
	
	public int getLastMaxX() {
		return this.lastMaxX;
	}
}