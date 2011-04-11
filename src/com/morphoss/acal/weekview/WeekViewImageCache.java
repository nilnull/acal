package com.morphoss.acal.weekview;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.MeasureSpec;
import android.widget.TextView;

import com.morphoss.acal.R;
import com.morphoss.acal.acaltime.AcalDateTime;
import com.morphoss.acal.davacal.AcalEvent;

public class WeekViewImageCache {
	
	private float dayWidth;
	private float halfHeight;
	private Context c;
	
	private Bitmap sidebar;
	private int sidebarWidth = -1;
	
	private Bitmap daygrid;
	private float daygridWidth = -1;
	private int workDayStart = 9;
	private int workDayEnd = 17;
	private Bitmap daybox;
	private Bitmap workbox;
	
	private HashMap<Long,Bitmap> eventMap = new HashMap<Long, Bitmap>();
	private Queue<Long> eventQueue = new LinkedList<Long>();
	
	public WeekViewImageCache(Context c, float dayWidth, float halfHeight) {
		this.c=c;
		this.dayWidth=dayWidth;
		this.halfHeight=halfHeight;
	}
	public void cacheDayBoxes() {
		if (daybox != null) return;
		
		//First do regular box
		Bitmap returnedBitmap = Bitmap.createBitmap((int)dayWidth, (int)(halfHeight*2),Bitmap.Config.ARGB_4444);
		Canvas canvas = new Canvas(returnedBitmap);
		float hourHeight = halfHeight*2;
		Paint p = new Paint();
		p.setStyle(Paint.Style.STROKE);
		p.setColor(Color.parseColor("#AAAAAA"));
		canvas.drawRect(0,0,dayWidth,hourHeight,p);
		
		p.setStyle(Paint.Style.STROKE);
		p.setColor(Color.parseColor("#AAAAAA"));
		//draw dotted center line
		DashPathEffect dashes = new DashPathEffect(new float[]{5,5},0);
		p.setPathEffect(dashes);
		canvas.drawLine(0,halfHeight, dayWidth, halfHeight, p);
		this.daybox = returnedBitmap;
		
		//now do yellow box;
		returnedBitmap = Bitmap.createBitmap((int)dayWidth, (int)(halfHeight*2),Bitmap.Config.ARGB_8888);
		canvas = new Canvas(returnedBitmap);
		p.reset();
		p.setStyle(Paint.Style.FILL);
		p.setColor(Color.parseColor("#fff6f6d0"));
		canvas.drawRect(0,0,dayWidth,hourHeight,p);
		p.setStyle(Paint.Style.STROKE);
		p.setColor(Color.parseColor("#AAAAAA"));
		canvas.drawRect(0,0,dayWidth,hourHeight,p);
		p.reset();
		p.setStyle(Paint.Style.STROKE);
		p.setColor(Color.parseColor("#AAAAAA"));
		//draw dotted center line
		p.setPathEffect(dashes);
		canvas.drawLine(0,halfHeight, dayWidth, halfHeight, p);
		this.workbox = returnedBitmap;

	}
	public Bitmap getDayBox(int startHour, int endHour) {
		cacheDayBoxes();
		int numHours = endHour - startHour;
		Bitmap returnedBitmap = Bitmap.createBitmap((int)dayWidth, (int)((halfHeight*2)*numHours),Bitmap.Config.ARGB_4444);
		Canvas canvas = new Canvas(returnedBitmap);
		Paint p = new Paint();
		for (int curHour = startHour; curHour<endHour; curHour++) {
			float curY = (halfHeight*2)*(curHour-startHour);
			if (curHour < workDayStart || curHour >= workDayEnd)
				canvas.drawBitmap(daybox, 0, curY, p);
			else
				canvas.drawBitmap(workbox, 0, curY, p);
		}
		return returnedBitmap;
	}
	
	public Bitmap getSideBar(int width) {
		if (width == sidebarWidth) return sidebar;
		float y = 0;
		boolean half = false;
		int hour = 0;
		Bitmap master = Bitmap.createBitmap((int)width, (int)halfHeight*50,Bitmap.Config.ARGB_4444);
		Canvas masterCanvas = new Canvas(master);
		while(hour<=24) {
			LayoutInflater inflater = (LayoutInflater) c.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			View v = (View) inflater.inflate(R.layout.week_view_assets, null);
			TextView box;
			if (!half) box= ((TextView) v.findViewById(R.id.WV_side_box));
			else box= ((TextView) v.findViewById(R.id.WV_side_box_half));
			box.setVisibility(View.VISIBLE);
			String text = "";
			if (half) text="30";
			else if (hour == 0)text="12 a";
			else {
				int hd = hour;
				if (hour >= 13) hd-=12; 
				text=(int)hd+" "+(hour<12?"a":"p");
			}
			box.setText(text);
			box.measure(MeasureSpec.makeMeasureSpec((int) width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) halfHeight, MeasureSpec.EXACTLY));
			box.layout(0,0, (int)width, (int)halfHeight);
			Bitmap returnedBitmap = Bitmap.createBitmap((int)width, (int)halfHeight,Bitmap.Config.ARGB_4444);
			Canvas tempCanvas = new Canvas(returnedBitmap);
			box.draw(tempCanvas);
			half=!half;
			if (!half) hour++;
			y+=halfHeight;
			masterCanvas.drawBitmap(returnedBitmap,0, y, new Paint());
		}
		sidebar = master;
		sidebarWidth=width;
		return sidebar;
	}
	
	public Bitmap getEventBitmap(AcalEvent e, int width, int height) {
		long hash = getEventHash(e,width,height);
		if (eventMap.containsKey(hash)) {
			eventQueue.remove(hash);
			eventQueue.offer(hash);	//re prioritise
			return eventMap.get(hash);
		}
		if (eventMap.size() > 100) eventQueue.poll(); //make space
		//now construct the Bitmap
		LayoutInflater inflater = (LayoutInflater) c.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View v = (View) inflater.inflate(R.layout.week_view_assets, null);
		TextView title = ((TextView) v.findViewById(R.id.WV_event_box));
		title.setBackgroundColor(e.colour);
		title.setVisibility(View.VISIBLE);
		title.setText(e.summary);
		title.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
		title.layout(0, 0, width, height);
		Bitmap returnedBitmap = Bitmap.createBitmap(width, height,Bitmap.Config.ARGB_8888);
		Canvas tempCanvas = new Canvas(returnedBitmap);
		title.draw(tempCanvas);
		eventMap.put(hash, returnedBitmap);
		eventQueue.offer(hash);
		return returnedBitmap;
	}
	public long getEventHash(AcalEvent e, int width, int height) {
		return (long)width + (long)(dayWidth*height) + ((long)dayWidth*10000L * (long)e.resourceId);
	}
}