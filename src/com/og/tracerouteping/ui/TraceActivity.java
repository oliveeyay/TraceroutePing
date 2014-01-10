/*
This file is part of the project TraceroutePing, which is an Android library
implementing Traceroute with ping under GPL license v3.
Copyright (C) 2013  Olivier Goutay

TraceroutePing is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

TraceroutePing is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with TraceroutePing.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.og.tracerouteping.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.og.tracerouteping.R;
import com.og.tracerouteping.network.TracerouteContainer;
import com.og.tracerouteping.network.TracerouteWithPing;

import java.util.ArrayList;
import java.util.List;

/**
 * The main activity
 * 
 * @author Olivier Goutay
 * 
 */
public class TraceActivity extends Activity {

	public static final String tag = "TraceroutePing";
	public static final String INTENT_TRACE = "INTENT_TRACE";

	private Button buttonLaunch;
	private EditText editTextPing;
	private ProgressBar progressBarPing;
	private ListView listViewTraceroute;
	private TraceListAdapter traceListAdapter;

	private TracerouteWithPing tracerouteWithPing;
	private final int maxTtl = 40;

	private List<TracerouteContainer> traces;

	/**
	 * onCreate, init main components from view
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_trace);

		this.tracerouteWithPing = new TracerouteWithPing(this);
		this.traces = new ArrayList<TracerouteContainer>();

		this.buttonLaunch = (Button) this.findViewById(R.id.buttonLaunch);
		this.editTextPing = (EditText) this.findViewById(R.id.editTextPing);
		this.listViewTraceroute = (ListView) this.findViewById(R.id.listViewTraceroute);
		this.progressBarPing = (ProgressBar) this.findViewById(R.id.progressBarPing);

		initView();
	}

	/**
	 * initView, init the main view components (action, adapter...)
	 */
	private void initView() {
		buttonLaunch.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (editTextPing.getText().length() == 0) {
					Toast.makeText(TraceActivity.this, getString(R.string.no_text), Toast.LENGTH_SHORT).show();
				} else {
					startProgressBar();
					hideSoftwareKeyboard(editTextPing);
					tracerouteWithPing.executeTraceroute(editTextPing.getText().toString(), maxTtl);
				}
			}
		});

		traceListAdapter = new TraceListAdapter(getApplicationContext());
		listViewTraceroute.setAdapter(traceListAdapter);
	}

	/**
	 * Allows to refresh the listview of traces
	 * 
	 * @param traces
	 *            The list of traces to refresh
	 */
	public void refreshList(List<TracerouteContainer> traces) {
		this.traces = traces;
		traceListAdapter.notifyDataSetChanged();
	}

	/**
	 * The adapter of the listview (build the views)
	 */
	public class TraceListAdapter extends BaseAdapter {

		private Context context;

		public TraceListAdapter(Context c) {
			context = c;
		}

		public int getCount() {
			return traces.size();
		}

		public TracerouteContainer getItem(int position) {
			return traces.get(position);
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;

			// first init
			if (convertView == null) {
				LayoutInflater vi = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				convertView = vi.inflate(R.layout.item_list_trace, null);

				TextView textViewNumber = (TextView) convertView.findViewById(R.id.textViewNumber);
				TextView textViewIp = (TextView) convertView.findViewById(R.id.textViewIp);
				TextView textViewTime = (TextView) convertView.findViewById(R.id.textViewTime);
				ImageView imageViewStatusPing = (ImageView) convertView.findViewById(R.id.imageViewStatusPing);

				// Set up the ViewHolder.
				holder = new ViewHolder();
				holder.textViewNumber = textViewNumber;
				holder.textViewIp = textViewIp;
				holder.textViewTime = textViewTime;
				holder.imageViewStatusPing = imageViewStatusPing;

				// Store the holder with the view.
				convertView.setTag(holder);
			} else {
				holder = (ViewHolder) convertView.getTag();
			}

			TracerouteContainer currentTrace = getItem(position);

			if (position % 2 == 1) {
				convertView.setBackgroundResource(R.drawable.table_odd_lines);
			} else {
				convertView.setBackgroundResource(R.drawable.table_pair_lines);
			}

			if (currentTrace.isSuccessful()) {
				holder.imageViewStatusPing.setImageResource(R.drawable.check);
			} else {
				holder.imageViewStatusPing.setImageResource(R.drawable.cross);
			}

			holder.textViewNumber.setText(position + "");
			holder.textViewIp.setText(currentTrace.getHostname() + " (" + currentTrace.getIp() + ")");
			holder.textViewTime.setText(currentTrace.getMs() + "ms");

			return convertView;
		}

		// ViewHolder pattern
		class ViewHolder {
			TextView textViewNumber;
			TextView textViewIp;
			TextView textViewTime;
			ImageView imageViewStatusPing;
		}
	}

	/**
	 * Hides the keyboard
	 * 
	 * @param currentEditText
	 *            The current selected edittext
	 */
	public void hideSoftwareKeyboard(EditText currentEditText) {
		InputMethodManager imm = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
		if (imm.isActive()) {
			imm.hideSoftInputFromWindow(currentEditText.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
		}
	}

	public void startProgressBar() {
		progressBarPing.setVisibility(View.VISIBLE);
	}

	public void stopProgressBar() {
		progressBarPing.setVisibility(View.GONE);
	}

}
