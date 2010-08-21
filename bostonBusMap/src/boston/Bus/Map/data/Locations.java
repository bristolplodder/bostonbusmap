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
package boston.Bus.Map.data;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;
import boston.Bus.Map.R;
import boston.Bus.Map.database.DatabaseHelper;
import boston.Bus.Map.main.Main;
import boston.Bus.Map.main.UpdateAsyncTask;
import boston.Bus.Map.parser.BusPredictionsFeedParser;
import boston.Bus.Map.parser.RouteConfigFeedParser;
import boston.Bus.Map.parser.VehicleLocationsFeedParser;
import boston.Bus.Map.transit.TransitSystem;
import boston.Bus.Map.util.DownloadHelper;
import boston.Bus.Map.util.FeedException;
import boston.Bus.Map.util.StreamCounter;

public final class Locations
{
	/**
	 * No change to mode we're in. 0 is vehicle locations, and others are indexes to route numbers for bus predictions, as index into
	 * routesSupported. (In this class, routesSupported is defined such that 0 is null, 1 is route 1, 2 is route 4, etc, so -1
	 * should be the only special case to deal with)
	 */
	public static final int NO_CHANGE = -1;

	/**
	 * A mapping of the bus number to bus location
	 */
	private HashMap<Integer, BusLocation> busMapping = new HashMap<Integer, BusLocation>();
	
	/**
	 * A mapping of a route id to a RouteConfig object.
	 */
	private final HashMap<String, RouteConfig> routeMapping = new HashMap<String, RouteConfig>();
	

	
	private final HashMap<Integer, String> vehiclesToRouteNames = new HashMap<Integer, String>();

	private double lastInferBusRoutesTime = 0;
	
	private double lastUpdateTime = 0;
	
	/**
	 * This should let us know if the user checked or unchecked the Infer bus routes checkbox. If inferBusRoutes in Refresh()
	 * is true and this is false, we should do a refresh, and if inferBusRoutes is false and this is true, we should
	 * clear the bus information 
	 */
	private boolean lastInferBusRoutes;

	/**
	 * in millis
	 */
	private final double tenMinutes = 10 * 60 * 1000;
	
	
	private final Drawable bus;
	private final Drawable arrow;
	private final Drawable locationDrawable;
	private final Drawable busStop;
	
	private final String[] supportedRoutes;
	
	private String selectedRoute;
	private int selectedBusPredictions;
	
	private final HashMap<String, StopLocation> favoriteStops = new HashMap<String, StopLocation>();
	
	public Locations(Drawable bus, Drawable arrow, Drawable locationDrawable,
			Drawable busStop, String[] supportedRoutes)
	{
		this.bus = bus;
		this.arrow = arrow;
		this.locationDrawable = locationDrawable;
		this.busStop = busStop;
		this.supportedRoutes = supportedRoutes;
		
		
	}
	
	/**
	 * Download all stop locations
	 * 
	 * @throws ParserConfigurationException
	 * @throws FactoryConfigurationError
	 * @throws SAXException
	 * @throws IOException
	 */
	public void initializeAllRoutes(DatabaseHelper helper, UpdateAsyncTask task, Context context)
		throws ParserConfigurationException, FactoryConfigurationError, SAXException, IOException
	{
		boolean hasMissingData = routeInfoNeedsUpdating();
		
		if (hasMissingData)
		{
			final String prepend = "Downloading route info (this may take a short while): ";
			
			//download everything at once
			//NOTE: for now, I'm including a copy of the routeConfig with the app
			/*final String urlString = TransitSystem.getRouteConfigUrl();
			URL url = new URL(urlString);
			
			InputStream stream = downloadStream(url, task, prepend, "of approx " + TransitSystem.getSizeOfRouteConfigUrl());
			*/
			task.publish("Decompressing route data. This may take a minute...");
			
			final int contentLength = 950308;
			
			InputStream in = new StreamCounter(context.getResources().openRawResource(boston.Bus.Map.R.raw.routeconfig),
					task, contentLength, null, "Decompressing route data, may take 1 or 2 min: "); 
			
			GZIPInputStream stream = new GZIPInputStream(in); 
			
			RouteConfigFeedParser parser = new RouteConfigFeedParser(busStop);
			
			parser.runParse(stream);
			
			task.publish("Parsing route data...");

			parser.fillMapping(routeMapping);
			
			task.publish("Saving route data to database...");
			helper.saveMapping(routeMapping, true);
			
			//TODO: fill routeMapping somehow
			
			task.publish("Done!");
		}
		else
		{
			for (String route : supportedRoutes)
			{
				if (routeMapping.containsKey(route) == false || routeMapping.get(route) == null || 
						routeMapping.get(route).getStops().size() == 0)
				{
					final String prepend = "Downloading route info for " + route + " (this may take a short while): ";

					//populate stops
					final String urlString = TransitSystem.getRouteConfigUrl(route);
					URL url = new URL(urlString);

					//just initialize the route and then end for this round
					InputStream stream = downloadStream(url, task, prepend, null);
					RouteConfigFeedParser parser = new RouteConfigFeedParser(busStop);
					
					parser.runParse(stream);
					
					parser.fillMapping(routeMapping);
					
					helper.saveMapping(routeMapping, false);
				}


			}
		}
	}
	
	private InputStream downloadStream(URL url, UpdateAsyncTask task, String prepend, String ifContentLengthMissing) throws IOException {
		URLConnection connection = url.openConnection();
		int totalDownloadSize = connection.getContentLength();
		InputStream inputStream = connection.getInputStream();

		return new StreamCounter(inputStream, task, totalDownloadSize, ifContentLengthMissing, prepend);
	}

	/**
	 * Update the bus locations based on data from the XML feed 
	 * 
	 * @param centerLat
	 * @param centerLon
	 * @throws SAXException
	 * @throws IOException
	 * @throws ParserConfigurationException
	 * @throws FactoryConfigurationError
	 * @throws FeedException 
	 */
	public void Refresh(DatabaseHelper helper, boolean inferBusRoutes, int routeIndexToUpdate,
			int selectedBusPredictions, double centerLatitude, double centerLongitude,
			UpdateAsyncTask updateAsyncTask) throws SAXException, IOException,
			ParserConfigurationException, FactoryConfigurationError 
	{
		String routeToUpdate = supportedRoutes[routeIndexToUpdate];

		try
		{
			updateInferRoutes(inferBusRoutes);
		}
		catch (IOException e)
		{
			//don't let a problem with the mit website stop everything from working
			Log.e("BostonBusMap", e.toString());
		}

		
		final int maxStops = 15;

		//read data from the URL
		DownloadHelper downloadHelper;
		switch (selectedBusPredictions)
		{
		case  Main.BUS_PREDICTIONS_ONE:
		{
			RouteConfig routeConfig = routeMapping.get(routeToUpdate);
			
			if (routeConfig != null)
			{
				if (routeConfig.getStops().size() != 0)
				{
					List<Location> locations = getLocations(maxStops, centerLatitude, centerLongitude, false);

					//ok, do predictions now
					String url = TransitSystem.getPredictionsUrl(locations, maxStops, routeConfig);

					downloadHelper = new DownloadHelper(url);
				}
				else
				{
					//populate stops (just in case we didn't already)
					updateAsyncTask.publish("Downloading data for route " + routeToUpdate + "...");
					populateStops(routeToUpdate, helper);
					updateAsyncTask.publish("Finished download");
					
					return;
				}
			}
			else
			{
				//populate stops (just in case we didn't already)
				updateAsyncTask.publish("Downloading data for route " + routeToUpdate + "...");
				populateStops(routeToUpdate, helper);
				updateAsyncTask.publish("Finished download");
				return;
			}
		}
		break;
		case Main.BUS_PREDICTIONS_ALL:
		case Main.BUS_PREDICTIONS_STAR:
		{
			List<Location> locations = getLocations(maxStops, centerLatitude, centerLongitude, false);
			
			String url = TransitSystem.getPredictionsUrl(locations, maxStops, null);

			downloadHelper = new DownloadHelper(url);
		}
		break;

		case Main.VEHICLE_LOCATIONS_ALL:
		case Main.VEHICLE_LOCATIONS_ONE:
		default:
		{
			//for now, we download and update all buses, whether the user chooses one route or all routes
			//we only make the distinction when we display the icons
			final String urlString = TransitSystem.getVehicleLocationsUrl((long)lastUpdateTime);
			downloadHelper = new DownloadHelper(urlString);
		}
		break;
		}

		downloadHelper.connect();

		InputStream data = downloadHelper.getResponseData();

		if (selectedBusPredictions == Main.BUS_PREDICTIONS_ONE || 
				selectedBusPredictions == Main.BUS_PREDICTIONS_ALL ||
				selectedBusPredictions == Main.BUS_PREDICTIONS_STAR)
		{
			//bus prediction

			BusPredictionsFeedParser parser = new BusPredictionsFeedParser(routeMapping);

			parser.runParse(data);
		}
		else 
		{
			//vehicle locations
			//VehicleLocationsFeedParser parser = new VehicleLocationsFeedParser(stream);

			//lastUpdateTime = parser.getLastUpdateTime();

			VehicleLocationsFeedParser parser = new VehicleLocationsFeedParser(vehiclesToRouteNames, routeMapping, bus, arrow);
			parser.runParse(data);

			//get the time that this information is valid until
			lastUpdateTime = parser.getLastUpdateTime();

			synchronized (busMapping)
			{
				parser.fillMapping(busMapping);

				//delete old buses
				List<Integer> busesToBeDeleted = new ArrayList<Integer>();
				for (Integer id : busMapping.keySet())
				{
					BusLocation busLocation = busMapping.get(id);
					if (busLocation.lastUpdateInMillis + 180000 < System.currentTimeMillis())
					{
						//put this old dog to sleep
						busesToBeDeleted.add(id);
					}
				}

				for (Integer id : busesToBeDeleted)
				{
					busMapping.remove(id);
				}
			}
		}
	}

	private void populateStops(String routeToUpdate, DatabaseHelper databaseHelper) 
		throws IOException, ParserConfigurationException, SAXException
	{
		final String urlString = TransitSystem.getRouteConfigUrl(routeToUpdate);

		DownloadHelper downloadHelper = new DownloadHelper(urlString);
		
		downloadHelper.connect();
		//just initialize the route and then end for this round
		
		RouteConfigFeedParser parser = new RouteConfigFeedParser(busStop);

		parser.runParse(downloadHelper.getResponseData()); 

		parser.fillMapping(routeMapping);
		
		databaseHelper.saveMapping(routeMapping, false);
	}

	/**
	 * This is a special feed which should hopefully become irrelevant soon. It guesses the route numbers of unpredictable buses based on
	 * where they go
	 * 
	 * @param inferBusRoutes
	 * @throws MalformedURLException
	 * @throws ParserConfigurationException
	 * @throws FactoryConfigurationError
	 * @throws IOException
	 * @throws SAXException
	 */
	private void updateInferRoutes(boolean inferBusRoutes)
			throws MalformedURLException, ParserConfigurationException,
			FactoryConfigurationError, IOException, SAXException {
		//if Infer bus routes is checked and either:
		//(a) 10 minutes have passed
		//(b) the checkbox wasn't checked before, which means we should refresh anyway
		if (inferBusRoutes && ((System.currentTimeMillis() - lastInferBusRoutesTime > tenMinutes) || (lastInferBusRoutes == false)))
		{
			//if we can't read from this feed, it'll throw an exception
			//set last time we read from site to 5 minutes ago, so it won't try to read for another 5 minutes
			//(currently it will check inferred route info every 10 minutes)
			lastInferBusRoutesTime = System.currentTimeMillis() - tenMinutes / 2;
			
			
			synchronized (vehiclesToRouteNames)
			{
				vehiclesToRouteNames.clear();

				//thanks Nickolai Zeldovich! http://people.csail.mit.edu/nickolai/
				final String vehicleToRouteNameUrl = "http://kk.csail.mit.edu/~nickolai/bus-infer/vehicle-to-routename.xml";
				URL url = new URL(vehicleToRouteNameUrl);

				DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

				InputStream stream = url.openStream();

				//parse the data into an XML document
				Document document = builder.parse(stream);

				NodeList nodeList = document.getElementsByTagName("vehicle");

				for (int i = 0; i < nodeList.getLength(); i++)
				{
					Element node = (Element)nodeList.item(i);
					vehiclesToRouteNames.put(Integer.parseInt(node.getAttribute("id")), node.getAttribute("routeTag"));
				}
			}
			lastInferBusRoutesTime = System.currentTimeMillis();
		}
		else if (inferBusRoutes == false && lastInferBusRoutes == true)
		{
			//clear vehicle mapping if checkbox is false
			synchronized (vehiclesToRouteNames)
			{
				vehiclesToRouteNames.clear();
			}
		}
		
		lastInferBusRoutes = inferBusRoutes;
	}


	/**
	 * Return the 20 (or whatever maxLocations is) closest buses to the center
	 * 
	 * NOTE: this is run in the UI thread, so be speedy
	 * 
	 * @param maxLocations
	 * @return
	 */
	public List<Location> getLocations(int maxLocations, double centerLatitude, double centerLongitude, boolean doShowUnpredictable) {

		TreeSet<Location> newLocations = new TreeSet<Location>(new LocationComparator(centerLatitude, centerLongitude));
		
		HashSet<Integer> locationKeys = new HashSet<Integer>();

		if (selectedBusPredictions == Main.VEHICLE_LOCATIONS_ALL || selectedBusPredictions == Main.VEHICLE_LOCATIONS_ONE)
		{
			synchronized (busMapping)
			{
				if (doShowUnpredictable == false)
				{
					for (BusLocation busLocation : busMapping.values())
					{
						if (busLocation.predictable == true)
						{
							if (selectedBusPredictions == Main.VEHICLE_LOCATIONS_ONE)
							{
								if (busLocation.route != null && busLocation.route.getRouteName().equals(selectedRoute))
								{
									newLocations.add(busLocation);
								}
							}
							else
							{
								newLocations.add(busLocation);
							}
						}
					}
				}
				else
				{
					if (selectedBusPredictions == Main.VEHICLE_LOCATIONS_ALL)
					{
						newLocations.addAll(busMapping.values());
					}
					else
					{
						for (BusLocation location : busMapping.values())
						{
							if (location.route != null && location.route.getRouteName().equals(selectedRoute))
							{
								newLocations.add(location);
							}
						}
					}
				}
			}
		}
		else if (selectedBusPredictions == Main.BUS_PREDICTIONS_ONE)
		{
			RouteConfig routeConfig = routeMapping.get(selectedRoute);
			if (routeConfig != null)
			{
				newLocations.addAll(routeConfig.getStops());
			}
		}
		else if (selectedBusPredictions == Main.BUS_PREDICTIONS_ALL)
		{
			//Log.v("BostonBusMap", "allStops size is " + allStops.size());
			
			/*for (StopLocation location : allStops)
			{
				if (location.distanceFrom(centerLatitude * LocationComparator.degreesToRadians,
						centerLongitude * LocationComparator.degreesToRadians) < 1)
				{
					if (locationKeys.contains(location.getId()) == false)
					{
						newLocations.add(location);
						locationKeys.add(location.getId());
					}
				}
			}*/
		}
		else if (selectedBusPredictions == Main.BUS_PREDICTIONS_STAR)
		{
			for (String location : favoriteStops.keySet())
			{
				StopLocation stopLocation = favoriteStops.get(location);
				
				newLocations.add(stopLocation);
				locationKeys.add(stopLocation.getId());
			}
		}
		
		if (maxLocations > newLocations.size())
		{
			maxLocations = newLocations.size();
		}
		
		
		
		return new ArrayList<Location>(newLocations).subList(0, maxLocations);
	}

	private int latitudeAsDegreesE6;
	private int longitudeAsDegreesE6;
	private boolean showCurrentLocation;

	public void setCurrentLocation(int latitudeAsDegreesE6, int longitudeAsDegreesE6) {
		this.latitudeAsDegreesE6 = latitudeAsDegreesE6;
		this.longitudeAsDegreesE6 = longitudeAsDegreesE6;
		showCurrentLocation = true;
	}
	
	public void clearCurrentLocation()
	{
		showCurrentLocation = false;
	}
	
	public void select(int position, int busPredictions) {
		if (position == Locations.NO_CHANGE)
		{
			//-1 means don't change this
		}
		else
		{
			selectedRoute = supportedRoutes[position];

			selectedBusPredictions = busPredictions;
		}
	}

	public ArrayList<Path> getSelectedPaths() {
		ArrayList<Path> ret = new ArrayList<Path>();

		RouteConfig routeConfig = routeMapping.get(selectedRoute);
		if (routeConfig != null)
		{
			ret.addAll(routeConfig.getPaths().values());
		}
		
		return ret;
	}

	/**
	 * Fills stopMapping with information from the database
	 * @param helper
	 * @param favorites
	 * @return
	 * @throws IOException 
	 */
	public void getRouteDataFromDatabase(DatabaseHelper helper) throws IOException {
		if (routeInfoNeedsUpdating() == false)
		{
			return;
		}
		
		final HashMap<String, RouteConfig> map = new HashMap<String, RouteConfig>();
		
		for (String route : supportedRoutes)
		{
			map.put(route, null);
		}
		
		try
		{
			HashSet<String> initialFavorites = new HashSet<String>();
			helper.populateMap(map, initialFavorites, supportedRoutes);
			//we can skip buses since they have no favorites, so everything will be in the map
			if (initialFavorites.size() != 0)
			{
				for (RouteConfig route : map.values())
				{
					if (route != null)
					{
						for (StopLocation location : route.getStops())
						{
							if (initialFavorites.contains(location.getStopTag()))
							{
								Log.v("BostonBusMap", "toggling favorite: " + location.getId());
								favoriteStops.put(location.getStopTag(), location);
								location.setFavorite(true);
							}
						}
					}
				}
			}
			initialFavorites.clear();
		}
		catch (IOException e)
		{
			Log.e("BostonBusMap", e.toString());
		}

		routeMapping.putAll(map);
	}
	
	private boolean routeInfoNeedsUpdating()
	{
		for (String route : supportedRoutes)
		{
			//TODO: remember to put this back when we go back to using the whole routeConfig
			if (routeMapping.get(route) == null || routeMapping.get(route).getStops().size() == 0)
			{
				return true;
			}
			/*if (routeMapping.get(route) != null && routeMapping.get(route).getStops().size() != 0)
			{
				return false;
			}*/
		}
		
		return false;
	}

	/**
	 * Is there enough space available, if we need any?
	 * @return
	 */
	public boolean checkFreeSpace(DatabaseHelper helper) {
		if (routeInfoNeedsUpdating() == false)
		{
			//everything is already in the database
			return true;
		}
		else
		{
			return helper.checkFreeSpace();
		}
	}

	public RouteConfig getSelectedRoute() {
		return routeMapping.get(selectedRoute);
	}
	
	public int toggleFavorite(DatabaseHelper helper, StopLocation location)
	{
		String stopTag = location.getStopTag();
		if (favoriteStops.containsKey(stopTag))
		{
			location.setFavorite(false);
			favoriteStops.remove(stopTag);
			helper.saveFavorite(stopTag, false);
			return R.drawable.empty_star;
		}
		else
		{
			location.setFavorite(true);
			favoriteStops.put(stopTag, location);
			helper.saveFavorite(stopTag, true);
			return R.drawable.full_star;
		}

	}
}
