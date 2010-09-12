/*
    BostonBusMap
 
    Copyright (C) 2009  George Schneeloch

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
    */
package boston.Bus.Map.main;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.client.CircularRedirectException;
import org.xml.sax.SAXException;



import boston.Bus.Map.data.Location;
import boston.Bus.Map.data.Locations;
import boston.Bus.Map.data.Path;
import boston.Bus.Map.data.RouteConfig;
import boston.Bus.Map.data.StopLocation;
import boston.Bus.Map.database.DatabaseHelper;
import boston.Bus.Map.ui.BusOverlay;
import boston.Bus.Map.ui.LocationOverlay;
import boston.Bus.Map.ui.RouteOverlay;
import boston.Bus.Map.util.Constants;
import boston.Bus.Map.util.FeedException;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.OverlayItem;
import com.google.android.maps.Projection;

import android.content.Context;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;

import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

/**
 * Handles the heavy work of downloading and parsing the XML in a separate thread from the UI.
 *
 */
public class UpdateAsyncTask extends AsyncTask<Object, String, Locations>
{
	private final MapView mapView;
	private final String finalMessage;
	private final boolean doShowUnpredictable;
	private final boolean doRefresh;
	/**
	 * For now this is always false. I need to figure out how to download a 1 megabyte file gracefully
	 */
	private final boolean doInit;
	private final int maxOverlays;
	private final boolean drawCircle;
	private final DatabaseHelper helper;
	private TextView textView;
	
	private boolean silenceUpdates;
	
	private final boolean inferBusRoutes;
	
	private BusOverlay busOverlay;
	private RouteOverlay routeOverlay;
	private LocationOverlay locationOverlay;
	
	private final int selectedRouteIndex;
	private final int selectedBusPredictions;
	private final boolean showRouteLine;
	private final boolean showCoarseRouteLine;
	
	public UpdateAsyncTask(TextView textView, MapView mapView, LocationOverlay locationOverlay, String finalMessage,
			boolean doShowUnpredictable, boolean doRefresh, int maxOverlays,
			boolean drawCircle, boolean inferBusRoutes, BusOverlay busOverlay, RouteOverlay routeOverlay, 
			DatabaseHelper helper, int selectedRouteIndex,
			int selectedBusPredictions, boolean doInit, boolean showRouteLine, boolean showCoarseRouteLine)
	{
		super();
		
		//NOTE: these should only be used in one of the UI threads
		this.mapView = mapView;
		this.finalMessage = finalMessage;
		this.doShowUnpredictable = doShowUnpredictable;
		this.doRefresh = doRefresh;
		this.maxOverlays = maxOverlays;
		this.drawCircle = drawCircle;
		this.inferBusRoutes = inferBusRoutes;
		this.busOverlay = busOverlay;
		this.routeOverlay = routeOverlay;
		this.locationOverlay = locationOverlay;
		this.helper = helper;
		this.textView = textView;
		this.selectedRouteIndex = selectedRouteIndex;
		this.selectedBusPredictions = selectedBusPredictions;
		this.doInit = doInit;
		this.showRouteLine = showRouteLine;
		this.showCoarseRouteLine = showCoarseRouteLine;
		//this.uiHandler = new Handler();
	}
	
	/**
	 * A type safe wrapper around execute
	 * @param busLocations
	 */
	public void runUpdate(Locations locations, double centerLatitude, double centerLongitude, Context context)
	{
		execute(locations, centerLatitude, centerLongitude, context);
	}

	@Override
	protected Locations doInBackground(Object... args) {
		//number of bus pictures to draw. Too many will make things slow
		return updateBusLocations((Locations)args[0], (Double)args[1], (Double)args[2], (Context)args[3]);
	}

	@Override
	protected void onProgressUpdate(String... strings)
	{
		if (silenceUpdates == false)
		{
			textView.setText(strings[0]);
		}
		
	}

	public Locations updateBusLocations(Locations busLocations, double centerLatitude, double centerLongitude, Context context)
	{
		Log.v("BostonBusMap", "in updateBusLocations, centerLatitude is " + centerLatitude);
		if (doRefresh == false)
		{
			//if doRefresh is false, we just want to resort the overlays for a new center. Don't bother updating the text
			silenceUpdates = true;
		}
		
		busLocations.select(selectedRouteIndex, selectedBusPredictions);

		if (doRefresh)
		{
			try
			{
				if (doInit)
				{
					//publishProgress("Did not find route info in database, checking if there's free space to download it...");
				}
				if (busLocations.checkFreeSpace(helper) == false)
				{
					publishProgress("There is not enough free space to download the route info. About 2MB free is required");
					return null;
				}
				
				if (doInit)
				{
					//publishProgress("Did not find route info in database, downloading it now...");
				}
				busLocations.initializeAllRoutes(this, context);
				
				
				publishProgress("Fetching data...");

				busLocations.Refresh(inferBusRoutes, selectedRouteIndex, selectedBusPredictions,
						centerLatitude, centerLongitude, this, showRouteLine);
			}
			catch (IOException e)
			{
				//this probably means that there is no Internet available, or there's something wrong with the feed
				publishProgress("Feed is inaccessible; try again later");

				StringWriter writer = new StringWriter();
				e.printStackTrace(new PrintWriter(writer));
				Log.e("BostonBusMap", writer.toString());
				
				return null;

			} catch (SAXException e) {
				publishProgress("XML parsing exception; cannot update. Maybe there was a hiccup in the feed?");

				StringWriter writer = new StringWriter();
				e.printStackTrace(new PrintWriter(writer));
				Log.e("BostonBusMap", writer.toString());
				
				return null;
			} catch (NumberFormatException e) {
				publishProgress("XML parsing exception; cannot update. Maybe there was a hiccup in the feed?");

				StringWriter writer = new StringWriter();
				e.printStackTrace(new PrintWriter(writer));
				Log.e("BostonBusMap", writer.toString());
				
				return null;
			} catch (ParserConfigurationException e) {
				publishProgress("XML parser configuration exception; cannot update");

				StringWriter writer = new StringWriter();
				e.printStackTrace(new PrintWriter(writer));
				Log.e("BostonBusMap", writer.toString());
				
				return null;
			} catch (FactoryConfigurationError e) {
				publishProgress("XML parser factory configuration exception; cannot update");

				StringWriter writer = new StringWriter();
				e.printStackTrace(new PrintWriter(writer));
				Log.e("BostonBusMap", writer.toString());
				
				return null;
			}
			catch (RuntimeException e)
			{
				if (e.getCause() instanceof FeedException)
				{
					publishProgress("The feed is reporting an error");

					StringWriter writer = new StringWriter();
					e.printStackTrace(new PrintWriter(writer));
					Log.e("BostonBusMap", writer.toString());
					
					return null;
				}
				else
				{
					throw e;
				}
			}
			catch (Exception e)
			{
				publishProgress("Unknown exception occurred");

				StringWriter writer = new StringWriter();
				e.printStackTrace(new PrintWriter(writer));
				Log.e("BostonBusMap", writer.toString());
				
				return null;
			}
		}
		publishProgress("Preparing to draw overlays...");
		
		publishProgress("Adding overlays to map...");
		
    	return busLocations;
    }
	
	@Override
	protected void onPostExecute(final Locations busLocationsObject)
	{
		if (busLocationsObject == null)
		{
			//we probably posted an error message already; just return
			return;
		}
		
		GeoPoint center = mapView.getMapCenter();
		final double e6 = Constants.E6;
		final double latitude = center.getLatitudeE6() / e6;
		final double longitude = center.getLongitudeE6() / e6;
		
		
		final Handler uiHandler = new Handler();
		
		sortBuses(busLocationsObject, latitude, longitude, uiHandler);
	}

	private void sortBuses(Locations busLocationsObject, final double latitude,
			final double longitude, Handler uiHandler) {
		final ArrayList<Location> busLocations = new ArrayList<Location>();
		
		try
		{
			busLocations.addAll(busLocationsObject.getLocations(maxOverlays, latitude, longitude, doShowUnpredictable));
		}
		catch (IOException e)
		{
			publishProgress("Error getting route data from database");
			return;
		}

		//if doRefresh is false, we should skip this, it prevents the icons from updating locations
		if (busLocations.size() == 0 && doRefresh)
		{
			//no data? oh well
			//sometimes the feed provides an empty XML message; completely valid but without any vehicle elements
			publishProgress("Finished update, no data provided");

			//an error probably occurred; keep buses where they were before, and don't overwrite message in textbox
			return;
		}
		
		final int selectedBusId;
		if (busOverlay != null)
		{
			selectedBusId = busOverlay.getSelectedBusId();
		}
		else
		{
			selectedBusId = -1;
		}
		
		Log.v("BostonBusMap", "selectedBusId is " + selectedBusId);
		
		busOverlay.setDrawHighlightCircle(drawCircle);
		
		routeOverlay.setDrawLine(showRouteLine);
		//routeOverlay.setDrawCoarseLine(showCoarseRouteLine);
		
        ArrayList<Path> paths;
		try {
			paths = busLocationsObject.getSelectedPaths();
		} catch (IOException e) {
			Log.e("BostonBusMap", "Exception thrown from getSelectedPaths: " + e.getMessage());
			paths = new ArrayList<Path>();
		}
		
		RouteConfig selectedRouteConfig;
		if (selectedBusPredictions == Main.BUS_PREDICTIONS_STAR)
		{
			selectedRouteConfig = null;
		}
		else
		{
			try {
				selectedRouteConfig = busLocationsObject.getSelectedRoute();
			} catch (IOException e) {
				Log.e("BostonBusMap", "Exception thrown from getSelectedRoute: " + e.getMessage());
				selectedRouteConfig = null;
			}
		}
		
		displayIcons(busOverlay, routeOverlay, locationOverlay, paths, latitude, longitude, busLocations, selectedBusId, selectedRouteConfig);
	}
	
	private void displayIcons(BusOverlay busOverlay, RouteOverlay routeOverlay, LocationOverlay locationOverlay, ArrayList<Path> paths,
			double latitude, double longitude, ArrayList<Location> busLocations, int selectedBusId, RouteConfig selectedRoute)
	{
		routeOverlay.setPaths(paths);
		
    	//we need to run populate even if there are 0 busLocations. See this link:
    	//http://groups.google.com/group/android-beginners/browse_thread/thread/6d75c084681f943e?pli=1
    	busOverlay.clear();

    	busOverlay.doPopulate();
    	
    	//point hash to index in busLocations
    	HashMap<Integer, Integer> points = new HashMap<Integer, Integer>();
    	
    	//draw the buses on the map
        for (Location busLocation : busLocations)
        {
        	int latInt = (int)(busLocation.getLatitudeAsDegrees() * Constants.E6);
        	int lonInt = (int)(busLocation.getLongitudeAsDegrees() * Constants.E6);
        	GeoPoint point = new GeoPoint(latInt, lonInt);
        			
        	//make a hash to easily compare this location's position against others
        	int hash = latInt ^ lonInt;
        	Integer index = points.get(hash);
        	if (null != index)
        	{
        		//two stops in one space. Just use the one overlay, and combine textboxes in an elegant manner
        		busLocation.addToSnippetAndTitle(selectedRoute, busLocations.get(points.get(hash)));
        	}
        	else
        	{
        		busLocation.makeSnippetAndTitle(selectedRoute);
        	
        	
        		points.put(hash, busOverlay.size());

        		//the title is displayed when someone taps on the icon
        		OverlayItem overlay = new OverlayItem(point, null, null);
        		busOverlay.addOverlay(overlay);
        		busOverlay.addLocation(busLocation);
        	}
        }

        busOverlay.setSelectedBusId(selectedBusId);
        busOverlay.refreshBalloons();

        mapView.getOverlays().clear();
		mapView.getOverlays().add(routeOverlay);
		mapView.getOverlays().add(locationOverlay);
		mapView.getOverlays().add(busOverlay);
		

        //make sure we redraw map
        mapView.invalidate();
        
        if (finalMessage != null)
        {
        	publishProgress(finalMessage);
        }
	}
	
	/**
	 * public method exposing protected publishProgress()
	 * @param msg
	 */
	public void publish(String msg)
	{
		publishProgress(msg);
	}
	
	/**
	 * This should be run in the UI thread so that we don't change the textView object while it's being used
	 * @param textView
	 */
	public void setTextView(TextView textView)
	{
		this.textView = textView;
	}
}
