package org.qy.nwpulib;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Application;
import android.content.Context;
import android.text.Html;
import android.text.Spanned;

public class Book {
	String title;
	String author;
	String call_no;
	String publisher;
	String marc_no;
	String nr_copy;
	String nr_borrowable;
	private Application app;
	private String mKeyword;
	private Spanned html;
	
	class BorrowInfo {
		class BorrowInfoItem
		{
			String status;
			String where;
			public BorrowInfoItem(JSONObject jo) {
				try {
					status = jo.getString("status");
					where = jo.getString("where");
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
			public String toString()
			{
				return where + ": " + status;
			}
			
			public String toHtmlString()
			{
				String htmlStatus;
				if (status.equals(app.getString(R.string.borrowable))) {
					htmlStatus = "<font color='green'>" + status + "</font>";
				} else {
					htmlStatus = "<font color='#808080'>" + status + "</font>";
				}
				return where + ": " + htmlStatus;
			}
		}
		ArrayList<BorrowInfoItem> bii = new ArrayList<BorrowInfoItem>();
		public BorrowInfo(JSONArray ja)
		{
			
				for (int i = 0; i < ja.length(); i++) {
					try {
						bii.add(new BorrowInfoItem(ja.getJSONObject(i)));
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}
		}
		public String toString() {
			String result = "";
			for (int i = 0; i < bii.size(); i++) {
				result += bii.get(i).toString() + "\n";
			}
			return result;
		}
		public String toHtmlString() {
			String result = "";
			for (int i = 0; i < bii.size(); i++) {
				result += bii.get(i).toHtmlString() + "<br />";
			}
			return result;
		}
	}
	BorrowInfo borrow_info;

	public Book(JSONObject jsbook, String keyword, Application application)
	{
		app = application;
		mKeyword = keyword;
		try {
			title = jsbook.getString("title");
			author = jsbook.getString("author");
			call_no = jsbook.getString("call_no");
			publisher = jsbook.getString("publisher");
			marc_no = jsbook.getString("marc_no");
			nr_copy = jsbook.getString("nr_copy");
			nr_borrowable = jsbook.getString("nr_borrowable");
			borrow_info = new BorrowInfo(jsbook.getJSONArray("borrow_info"));
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}
	
	public String toString()
	{
		return title + "," + author + "," +call_no + "," +publisher + "," +marc_no + "," +nr_copy + "," + nr_borrowable + "," + borrow_info.toString();
	}
	
	public Spanned toHtml()
	{
		if (html == null) {
			Pattern p = Pattern.compile(Pattern.quote(mKeyword), Pattern.CASE_INSENSITIVE);
			Matcher m = p.matcher(title);
			StringBuffer sb = new StringBuffer();
			while (m.find()) {
				String replacement ="<font color='red'>" +  m.group() + "</font>";
				m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
			}
			m.appendTail(sb);
			String htmlTitle = new String(sb);
		
			html = Html.fromHtml(
				htmlTitle + "<br />" +
				"<small>" +
				author + "<br />"+
				"<small>" + 
				publisher + "<br />"+
				call_no + " " + app.getString(R.string.borrowable) + ":" + nr_borrowable + " " +
				app.getString(R.string.in_library) + ":" + nr_copy + "<br />"+
				"---<br />" + 
				borrow_info.toHtmlString() +
				"</small></small>");
		}
		return html;
	}
}
