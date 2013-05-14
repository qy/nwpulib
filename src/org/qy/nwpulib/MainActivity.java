package org.qy.nwpulib;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.google.analytics.tracking.android.EasyTracker;
import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;
import com.handmark.pulltorefresh.library.PullToRefreshListView;

public class MainActivity extends Activity {
	private List<Book> mBooks = new ArrayList<Book>();
	private ArrayAdapter<String> lvAdapter;
	private EditText editText1;
	private Button button1;
	private ProgressDialog progressDialog;
	private PullToRefreshListView listView1;
	
	private Handler handler;
	private enum State {IDLE, FIRST_QUERING, FIRST_QUERIED, ALL_LOADED} ;
	private State mState;
	private final String baseurl = "http://lib.nwpu.info:8000";
	private String mEncKeyword;
	private String mKeyword;
	private int mPage;
	
	protected void fetchQueryResult()
	{
		List<Book> books = new ArrayList<Book>();

		if (mState == State.FIRST_QUERING) {
			mPage = 1;
			mKeyword = editText1.getText().toString();
			String[] words = mKeyword.split("\\s");
			mEncKeyword = "";
			for (int i = 0; i < words.length; i++) {
				try {
					if (i > 0) {
						mEncKeyword += "+";
					}
					mEncKeyword += URLEncoder.encode(words[i], "UTF-8");
				} catch (UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			mBooks.clear();
		} else if (mState == State.FIRST_QUERIED) {
			mPage++;
		} else {
			return;
		}
		try {
			URL url = new URL(baseurl + "/s/" + mEncKeyword + "/" + String.valueOf(mPage));
			BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));

			String inputLine;
			String json = "";
			while ((inputLine = in.readLine()) != null)
				json += inputLine;
			in.close();
			
			JSONObject jsonObject = new JSONObject(json);
			JSONArray jaBooks = jsonObject.getJSONArray("books");

			for (int i = 0; i < jaBooks.length(); i++) {
				Book book = new Book(jaBooks.getJSONObject(i), mKeyword, getApplication());
				books.add(book);
			}
			
			int current_page = jsonObject.getInt("current_page");
			int total_pages = jsonObject.getInt("total_pages");
			if (current_page >= total_pages) {
				mState = State.ALL_LOADED;
			} else if (mState == State.FIRST_QUERING) {
				mState = State.FIRST_QUERIED;
			}
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		mBooks.addAll(books);
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mState = State.IDLE;
		
		setContentView(R.layout.activity_main);
		editText1 = (EditText) findViewById(R.id.editText1);
		button1 = (Button) findViewById(R.id.button1);
		listView1 = (PullToRefreshListView) findViewById(R.id.listView1);
		progressDialog = new ProgressDialog(MainActivity.this);
		
		listView1.setVisibility(View.INVISIBLE);

		lvAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1) {
			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				View row;

				if (null == convertView) {
					row = getLayoutInflater().inflate( android.R.layout.simple_list_item_1, null);
				} else {
					row = convertView;
				}
				TextView tv = (TextView) row.findViewById(android.R.id.text1);
				Book book =  mBooks.get(position);
				Spanned span =book.toHtml();
				tv.setText(span);
				//tv.setText(Html.fromHtml("<b>Title" + String.valueOf(position) + "</b>"));
				return row;
			}
			@Override
			public int getCount()
			{
				return mBooks.size();
			}

		};
		listView1.setAdapter(lvAdapter);
        
        
		 handler = new Handler() {
				@Override
				public void handleMessage(Message msg) {
			    	if (progressDialog.isShowing()) {
			    		progressDialog.dismiss();
			    	} else {
			    		if (mState == State.ALL_LOADED) {
			                Toast.makeText(MainActivity.this, R.string.all_loaded, Toast.LENGTH_SHORT).show();
			    		}
			    		listView1.onRefreshComplete();
			    	}
				   lvAdapter.notifyDataSetChanged();
				}
			};

		button1.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
            progressDialog.setTitle(getString(R.string.pleasewait));
            progressDialog.setMessage(getString(R.string.searching) + "\"" + editText1.getText().toString() + "\" ..");
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);
            progressDialog.show();
            
    		   listView1.setVisibility(View.VISIBLE);
            mState = State.FIRST_QUERING;
            new GetDataTask().execute();
			}
		});
		
		listView1.setOnRefreshListener(new OnRefreshListener<ListView>() {
		    @Override
		    public void onRefresh(PullToRefreshBase<ListView> refreshView) {
		    		new GetDataTask().execute();
		    }
		});
	}

	private class GetDataTask extends AsyncTask<Void, Void, String[]> {
		@Override
		protected String[] doInBackground(Void... params) {
			fetchQueryResult();
			return null;
		}
		
	    @Override
	    protected void onPostExecute(String[] result) {
	        // Call onRefreshComplete when the list has been refreshed.
	    	handler.sendEmptyMessage(0);
	        super.onPostExecute(result);
	    }
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);

		menu.add(Menu.NONE, Menu.FIRST + 3, 6, getString(R.string.about)).setIcon(
				android.R.drawable.ic_menu_help);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case Menu.FIRST + 3:
			Dialog dialog = new AlertDialog.Builder(MainActivity.this)
					.setTitle(getString(R.string.about) + getString(R.string.app_name) + " v1.0")
					.setMessage(getString(R.string.about_info))
					.setPositiveButton(getString(R.string.ok),
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int which) {
								}
							}).create();
			dialog.show();
			break;
		}
		return true;
	}

	@Override
	  public void onStart() {
	    super.onStart();
	    EasyTracker.getInstance().activityStart(this);
	  }

	@Override
	  public void onStop() {
	    super.onStop();
	    EasyTracker.getInstance().activityStop(this);
	  }

}
