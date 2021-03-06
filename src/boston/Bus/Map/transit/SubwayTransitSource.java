package boston.Bus.Map.transit;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.client.ClientProtocolException;
import org.xml.sax.SAXException;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;

import android.content.Context;
import android.content.OperationApplicationException;
import android.graphics.Color;
import android.os.RemoteException;
import boston.Bus.Map.data.AlertsMapping;
import boston.Bus.Map.data.BusLocation;
import boston.Bus.Map.data.Directions;
import boston.Bus.Map.data.Location;
import boston.Bus.Map.data.Locations;
import boston.Bus.Map.data.RouteConfig;
import boston.Bus.Map.data.RoutePool;
import boston.Bus.Map.data.RouteTitles;
import boston.Bus.Map.data.Selection;
import boston.Bus.Map.data.StopLocation;
import boston.Bus.Map.data.SubwayStopLocation;
import boston.Bus.Map.data.TransitDrawables;
import boston.Bus.Map.data.TransitSourceTitles;
import boston.Bus.Map.database.Schema;
import boston.Bus.Map.main.Main;
import boston.Bus.Map.main.UpdateAsyncTask;
import boston.Bus.Map.parser.AlertParser;
import boston.Bus.Map.parser.SubwayPredictionsFeedParser;
import boston.Bus.Map.parser.SubwayRouteConfigFeedParser;
import boston.Bus.Map.ui.ProgressMessage;
import boston.Bus.Map.util.DownloadHelper;
import boston.Bus.Map.util.SearchHelper;

public class SubwayTransitSource implements TransitSource {
	private static String predictionsUrlSuffix = ".txt";
	private final TransitDrawables drawables;
	
	
	public static final int RedColor = Color.RED;
	public static final int OrangeColor = 0xf88017; //orange isn't a built in color?
	public static final int BlueColor = Color.BLUE;
	
	public static final String RedLine = "Red";
	public static final String OrangeLine = "Orange";
	public static final String BlueLine = "Blue";
	
	private final TransitSourceTitles routeTitles;
	
	
	public SubwayTransitSource(TransitDrawables drawables, TransitSourceTitles routeTitles)
	{
		this.drawables = drawables;
		
		this.routeTitles = routeTitles;
	}
	
	
	@Override
	public void refreshData(RouteConfig routeConfig,
			Selection selection, int maxStops, double centerLatitude,
			double centerLongitude, ConcurrentHashMap<String, BusLocation> busMapping,
			RoutePool routePool, Directions directions,
			Locations locationsObj)
			throws IOException, ParserConfigurationException, SAXException {
		//read data from the URL
		int selectedBusPredictions = selection.getMode();
		if (selectedBusPredictions == Selection.VEHICLE_LOCATIONS_ALL)
		{
			//for now I'm only refreshing data for buses if this is checked
			return;
		}
		
		
		HashSet<String> outputRoutes = new HashSet<String>();
		switch (selectedBusPredictions)
		{
		case  Selection.BUS_PREDICTIONS_ONE:
		case Selection.VEHICLE_LOCATIONS_ONE:
		{

			List<Location> locations = locationsObj.getLocations(maxStops, centerLatitude, centerLongitude, false, selection);

			//ok, do predictions now
			getPredictionsRoutes(locations, maxStops, routeConfig.getRouteName(), outputRoutes, selectedBusPredictions);
			break;
		}
		case Selection.BUS_PREDICTIONS_ALL:
		case Selection.VEHICLE_LOCATIONS_ALL:
		case Selection.BUS_PREDICTIONS_STAR:
		{
			List<Location> locations = locationsObj.getLocations(maxStops, centerLatitude, centerLongitude, false, selection);
			
			getPredictionsRoutes(locations, maxStops, null, outputRoutes, selectedBusPredictions);

		}
		break;

		}

		for (String route : outputRoutes)
		{
			{
				String url = getPredictionsUrl(route);
				DownloadHelper downloadHelper = new DownloadHelper(url);

				downloadHelper.connect();

				InputStream data = downloadHelper.getResponseData();

				//bus prediction

				SubwayPredictionsFeedParser parser = new SubwayPredictionsFeedParser(route, routePool, directions, drawables, busMapping, routeTitles);

				parser.runParse(data);
			}
			
			//get alerts if necessary
			AlertsMapping alertKeys = locationsObj.getAlertsMapping();
			String alertUrl = alertKeys.getUrlForRoute(route);
			
			RouteConfig railRouteConfig = routePool.get(route);
			if (railRouteConfig.obtainedAlerts() == false)
			{
				DownloadHelper downloadHelper = new DownloadHelper(alertUrl);
				downloadHelper.connect();

				InputStream stream = downloadHelper.getResponseData();
				InputStreamReader data = new InputStreamReader(stream);

				AlertParser parser = new AlertParser();
				parser.runParse(data);
				railRouteConfig.setAlerts(parser.getAlerts());
				data.close();

			}
			
		}
	}

	private static String getPredictionsUrl(String route)
	{
		final String dataUrlPrefix = "http://developer.mbta.com/Data/";

		return dataUrlPrefix + route + predictionsUrlSuffix ;
	}
	
	private void getPredictionsRoutes(List<Location> locations, int maxStops,
			String routeName, HashSet<String> outputRoutes, int mode) {
		
		//BUS_PREDICTIONS_ONE or VEHICLE_LOCATIONS_ONE
		if (routeName != null)
		{
			//we know we're updating only one route
			if (isSubway(routeName))
			{
				outputRoutes.add(routeName);
				return;
			}
		}
		else
		{
			if (mode == Selection.BUS_PREDICTIONS_STAR)
			{
				//ok, let's look at the locations and see what we can get
				for (Location location : locations)
				{
					if (location instanceof StopLocation)
					{
						StopLocation stopLocation = (StopLocation)location;


						for (String route : stopLocation.getRoutes())
						{
							if (isSubway(route))
							{
								outputRoutes.add(route);
							}
						}
					}
					else if (location instanceof BusLocation)
					{
						//bus location
						BusLocation busLocation = (BusLocation)location;
						String route = busLocation.getRouteId();

						if (isSubway(route))
						{
							outputRoutes.add(route);
						}
					}
				}
			}
			else
			{
				//add all three
				for (String route : routeTitles.routeTags())
				{
					outputRoutes.add(route);
				}
			}
		}
	}


	@Override
	public boolean hasPaths() {
		return false;
	}

	

	public static String getRouteConfigUrl() {
		return "http://developer.mbta.com/RT_Archive/RealTimeHeavyRailKeys.csv";
	}

	private boolean isSubway(String route) {
		return routeTitles.hasRoute(route);
	}

	public static int getSubwayColor(String subwayRoute)
	{
		return BlueColor;
	}



	@Override
	public TransitDrawables getDrawables() {
		return drawables;
	}

	@Override
	public String searchForRoute(String indexingQuery, String lowercaseQuery)
	{
		return SearchHelper.naiveSearch(indexingQuery, lowercaseQuery, routeTitles);
	}


	@Override
	public SubwayStopLocation createStop(float latitude, float longitude,
			String stopTag, String stopTitle, int platformOrder, String branch,
			String route) {
		SubwayStopLocation stop = new SubwayStopLocation.SubwayBuilder(
				latitude, longitude, stopTag, stopTitle, platformOrder, branch).build();
		stop.addRoute(route);
		return stop;
	}


	@Override
	public int getLoadOrder() {
		return 2;
	}


	@Override
	public int getTransitSourceId() {
		return Schema.Routes.enumagencyidSubway;
	}


	@Override
	public TransitSourceTitles getRouteTitles() {
		return routeTitles;
	}
	
	@Override
	public boolean requiresSubwayTable() {
		return true;
	}
}
