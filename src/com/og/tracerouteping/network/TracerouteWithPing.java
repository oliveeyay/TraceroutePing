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

package com.og.tracerouteping.network;

import android.annotation.SuppressLint;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.og.tracerouteping.R;
import com.og.tracerouteping.ui.TraceActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * This class contain everything needed to launch a traceroute using the ping command
 * 
 * @author Olivier Goutay
 * 
 */
public class TracerouteWithPing {

	private static final String PING = "PING";
	private static final String FROM_PING = "From";
	private static final String SMALL_FROM_PING = "from";
	private static final String PARENTHESE_OPEN_PING = "(";
	private static final String PARENTHESE_CLOSE_PING = ")";
	private static final String TIME_PING = "time=";
	private static final String EXCEED_PING = "exceed";
	private static final String UNREACHABLE_PING = "100%";

	private List<TracerouteContainer> traces;
	private int ttl;
	private int finishedTasks;
	private String urlToPing;
	private String ipToPing;
	private float elapsedTime;
	private TraceActivity context;

	// timeout handling
	private static final int TIMEOUT = 30000;
	private Handler handlerTimeout;
	private static Runnable runnableTimeout;

	public TracerouteWithPing(TraceActivity context) {
		this.context = context;
	}

	/**
	 * Launches the Traceroute
	 * 
	 * @param url
	 *            The url to trace
	 * @param maxTtl
	 *            The max time to live to set (ping param)
	 */
	public void executeTraceroute(String url, int maxTtl) {
		this.ttl = 1;
		this.finishedTasks = 0;
		this.urlToPing = url;
		this.traces = new ArrayList<TracerouteContainer>();

		new ExecutePingAsyncTask(maxTtl).execute();
	}

	/**
	 * Allows to timeout the ping if TIMEOUT exceeds. (-w and -W are not always supported on Android)
	 */
	private class TimeOutAsyncTask extends AsyncTask<Void, Void, Void> {

		private ExecutePingAsyncTask task;
		private int ttlTask;

		public TimeOutAsyncTask(ExecutePingAsyncTask task, int ttlTask) {
			this.task = task;
			this.ttlTask = ttlTask;
		}

		@Override
		protected Void doInBackground(Void... arg0) {
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			if (handlerTimeout == null) {
				handlerTimeout = new Handler();
			}

			// stop old timeout
			if (runnableTimeout != null) {
				handlerTimeout.removeCallbacks(runnableTimeout);
			}
			// define timeout
			runnableTimeout = new Runnable() {
				@Override
				public void run() {
					if (task != null) {
						Log.e(TraceActivity.tag, ttlTask + " task.isFinished()" + finishedTasks + " " + (ttlTask == finishedTasks));
						if (ttlTask == finishedTasks) {
							Toast.makeText(context, context.getString(R.string.timeout), Toast.LENGTH_SHORT).show();
							task.setCancelled(true);
							task.cancel(true);
							context.stopProgressBar();
						}
					}
				}
			};
			// launch timeout after a delay
			handlerTimeout.postDelayed(runnableTimeout, TIMEOUT);

			super.onPostExecute(result);
		}
	}
	
	/**
	 * The task that ping an ip, with increasing time to live (ttl) value
	 */
	private class ExecutePingAsyncTask extends AsyncTask<Void, Void, String> {

		private boolean isCancelled;
		private int maxTtl;

		public ExecutePingAsyncTask(int maxTtl) {
			this.maxTtl = maxTtl;
		}

		/**
		 * Launches the ping, launches InetAddress to retrieve url if there is one, store trace
		 */
		@Override
		protected String doInBackground(Void... params) {
			if (hasConnectivity()) {
				try {
					String res = launchPing(urlToPing);

					TracerouteContainer trace;

					if (res.contains(UNREACHABLE_PING) && !res.contains(EXCEED_PING)) {
						// Create the TracerouteContainer object when ping
						// failed
						trace = new TracerouteContainer("", parseIpFromPing(res), elapsedTime, false);
					} else {
						// Create the TracerouteContainer object when succeed
						trace = new TracerouteContainer("", parseIpFromPing(res), ttl == maxTtl ? Float.parseFloat(parseTimeFromPing(res))
								: elapsedTime, true);
					}

					// Get the host name from ip (unix ping do not support
					// hostname resolving)
					InetAddress inetAddr = InetAddress.getByName(trace.getIp());
					String hostname = inetAddr.getHostName();
					String canonicalHostname = inetAddr.getCanonicalHostName();
					trace.setHostname(hostname);
					Log.d(TraceActivity.tag, "hostname : " + hostname);
					Log.d(TraceActivity.tag, "canonicalHostname : " + canonicalHostname);

					// Store the TracerouteContainer object
					Log.d(TraceActivity.tag, trace.toString());
					
					context.refreshList(trace);
					
					return res;
				} catch (final Exception e) {
					context.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							onException(e);
						}
					});
				}
			} else {
				return context.getString(R.string.no_connectivity);
			}
			return "";
		}

		/**
		 * Launches ping command
		 * 
		 * @param url
		 *            The url to ping
		 * @return The ping string
		 */
		@SuppressLint("NewApi")
		private String launchPing(String url) throws Exception {
			// Build ping command with parameters
			Process p;
			String command = "";

			String format = "ping -c 1 -t %d ";
			command = String.format(format, ttl);

			Log.d(TraceActivity.tag, "Will launch : " + command + url);

			long startTime = System.nanoTime();
			// timeout task
			new TimeOutAsyncTask(this, ttl).execute();
			// Launch command
			p = Runtime.getRuntime().exec(command + url);
			BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));

			// Construct the response from ping
			String s;
			String res = "";
			while ((s = stdInput.readLine()) != null) {
				res += s + "\n";
				if (s.contains(FROM_PING) || s.contains(SMALL_FROM_PING)) {
					// We store the elapsedTime when the line from ping comes
					elapsedTime = (System.nanoTime() - startTime) / 1000000.0f;
				}
			}

			p.destroy();

			if (res.equals("")) {
				throw new IllegalArgumentException();
			}

			// Store the wanted ip adress to compare with ping result
			if (ttl == 1) {
				ipToPing = parseIpToPingFromPing(res);
			}

			return res;
		}

		/**
		 * Treat the previous ping (launches a ttl+1 if it is not the final ip, refresh the list on view etc...)
		 */
		@Override
		protected void onPostExecute(String result) {
			if (!isCancelled) {
				try {
					if (!"".equals(result)) {
						if (context.getString(R.string.no_connectivity).equals(result)) {
							Toast.makeText(context, context.getString(R.string.no_connectivity), Toast.LENGTH_SHORT).show();
						} else {
							Log.d(TraceActivity.tag, result);

							if (traces.size() > 0 && traces.get(traces.size() - 1).getIp().equals(ipToPing)) {
								if (ttl < maxTtl) {
									ttl = maxTtl;
									traces.remove(traces.size() - 1);
									new ExecutePingAsyncTask(maxTtl).execute();
								} else {
									context.stopProgressBar();
								}
							} else {
								if (ttl < maxTtl) {
									ttl++;
									new ExecutePingAsyncTask(maxTtl).execute();
								}
							}
//							context.refreshList(traces);
						}
					}
					finishedTasks++;
				} catch (final Exception e) {
					context.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							onException(e);
						}
					});
				}
			}

			super.onPostExecute(result);
		}

		/**
		 * Handles exception on ping
		 * 
		 * @param e
		 *            The exception thrown
		 */
		private void onException(Exception e) {
			Log.e(TraceActivity.tag, e.toString());

			if (e instanceof IllegalArgumentException) {
				Toast.makeText(context, context.getString(R.string.no_ping), Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(context, context.getString(R.string.error), Toast.LENGTH_SHORT).show();
			}

			context.stopProgressBar();

			finishedTasks++;
		}

		public void setCancelled(boolean isCancelled) {
			this.isCancelled = isCancelled;
		}

	}

	/**
	 * Gets the ip from the string returned by a ping
	 * 
	 * @param ping
	 *            The string returned by a ping command
	 * @return The ip contained in the ping
	 */
	private String parseIpFromPing(String ping) {
		String ip = "";
		if (ping.contains(FROM_PING)) {
			// Get ip when ttl exceeded
			int index = ping.indexOf(FROM_PING);

			ip = ping.substring(index + 5);
			if (ip.contains(PARENTHESE_OPEN_PING)) {
				// Get ip when in parenthese
				int indexOpen = ip.indexOf(PARENTHESE_OPEN_PING);
				int indexClose = ip.indexOf(PARENTHESE_CLOSE_PING);

				ip = ip.substring(indexOpen + 1, indexClose);
			} else {
				// Get ip when after from
				ip = ip.substring(0, ip.indexOf("\n"));
				if (ip.contains(":")) {
					index = ip.indexOf(":");
				} else {
					index = ip.indexOf(" ");
				}

				ip = ip.substring(0, index);
			}
		} else {
			// Get ip when ping succeeded
			int indexOpen = ping.indexOf(PARENTHESE_OPEN_PING);
			int indexClose = ping.indexOf(PARENTHESE_CLOSE_PING);

			ip = ping.substring(indexOpen + 1, indexClose);
		}

		return ip;
	}

	/**
	 * Gets the final ip we want to ping (example: if user fullfilled google.fr, final ip could be 8.8.8.8)
	 * 
	 * @param ping
	 *            The string returned by a ping command
	 * @return The ip contained in the ping
	 */
	private String parseIpToPingFromPing(String ping) {
		String ip = "";
		if (ping.contains(PING)) {
			// Get ip when ping succeeded
			int indexOpen = ping.indexOf(PARENTHESE_OPEN_PING);
			int indexClose = ping.indexOf(PARENTHESE_CLOSE_PING);

			ip = ping.substring(indexOpen + 1, indexClose);
		}

		return ip;
	}

	/**
	 * Gets the time from ping command (if there is)
	 * 
	 * @param ping
	 *            The string returned by a ping command
	 * @return The time contained in the ping
	 */
	private String parseTimeFromPing(String ping) {
		String time = "";
		if (ping.contains(TIME_PING)) {
			int index = ping.indexOf(TIME_PING);

			time = ping.substring(index + 5);
			index = time.indexOf(" ");
			time = time.substring(0, index);
		}

		return time;
	}

	/**
	 * Check for connectivity (wifi and mobile)
	 * 
	 * @return true if there is a connectivity, false otherwise
	 */
	public boolean hasConnectivity() {
		ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
		return activeNetworkInfo != null && activeNetworkInfo.isConnected();
	}
}
