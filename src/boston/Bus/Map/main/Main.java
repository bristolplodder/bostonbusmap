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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import java.util.List;
import java.util.concurrent.ConcurrentMap;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.impl.conn.tsccm.RouteSpecificPool;
import org.xml.sax.SAXException;

import boston.Bus.Map.R;
import boston.Bus.Map.algorithms.GetDirections;
import boston.Bus.Map.data.Direction;
import boston.Bus.Map.data.Locations;

import boston.Bus.Map.data.BusLocation;
import boston.Bus.Map.data.IntersectionLocation;
import boston.Bus.Map.data.RouteConfig;
import boston.Bus.Map.data.RouteTitles;
import boston.Bus.Map.data.Selection;
import boston.Bus.Map.data.StopLocation;
import boston.Bus.Map.data.TransitDrawables;
import boston.Bus.Map.data.UpdateArguments;
import boston.Bus.Map.provider.TransitContentProvider;
import boston.Bus.Map.transit.TransitSystem;
import boston.Bus.Map.tutorials.IntroTutorial;
import boston.Bus.Map.tutorials.Tutorial;
import boston.Bus.Map.tutorials.TutorialStep;
import boston.Bus.Map.ui.BusOverlay;

import boston.Bus.Map.ui.LocationOverlay;
import boston.Bus.Map.ui.ModeAdapter;
import boston.Bus.Map.ui.OverlayGroup;
import boston.Bus.Map.ui.RouteOverlay;
import boston.Bus.Map.ui.ViewingMode;
import boston.Bus.Map.util.Constants;
import boston.Bus.Map.util.SearchHelper;
import boston.Bus.Map.util.StringUtil;


import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.Overlay;
import com.google.android.maps.OverlayItem;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Lists;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import android.os.SystemClock;
import android.os.Handler.Callback;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.provider.SearchRecentSuggestions;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.View.OnTouchListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Gallery;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;
import android.widget.ZoomControls;
import android.widget.AdapterView.OnItemSelectedListener;

/**
 * The main activity
 *
 */
public class Main extends MapActivity
{
	private static final String selectedRouteIndexKey = "selectedRouteIndex";
	private static final String selectedBusPredictionsKey = "selectedBusPredictions";
	private static final String centerLatKey = "centerLat";
	private static final String centerLonKey = "centerLon";
	private static final String zoomLevelKey = "zoomLevel";
	private static final String selectedIntersectionKey = "selectedIntersection";
	//private static final String gpsAlwaysOn = "gpsAlwaysOn";
	private static final String markUpdatedStops = "markUpdatedStops";
	
	private static final String introScreenKey = "introScreen";
	
	public static final String tutorialStepKey = "tutorialStep";
	
	private EditText searchView;
	
	/**
	 * Used to make updateBuses run every 10 seconds or so
	 */
	private UpdateHandler handler;
	
	/**
	 * This is used to indicate to the mode spinner to ignore the first time we set it, so we don't update every time the screen changes
	 */
	private boolean firstRunMode;
	
	/**
	 * Is location overlay supposed to be enabled? Used mostly for onResume()
	 */
	private boolean locationEnabled; 
	
	private Spinner toggleButton;
	
	private Button chooseAPlaceButton;
	private Button chooseAFavoriteButton;
	
	/**
	 * The list of routes that's selectable in the routes dropdown list
	 */
	private RouteTitles dropdownRouteKeysToTitles;
	private AlertDialog routeChooserDialog;

	private ImageButton searchButton;

	private UpdateArguments arguments;
	private ImageButton myLocationButton;
	private Button skipTutorialButton;
	private RelativeLayout tutorialLayout;
	private Button nextTutorialButton;
	
	
	public static final int UPDATE_INTERVAL_INVALID = 9999;
	public static final int UPDATE_INTERVAL_SHORT = 15;
	public static final int UPDATE_INTERVAL_MEDIUM = 50;
	public static final int UPDATE_INTERVAL_LONG = 100;
	public static final int UPDATE_INTERVAL_NONE = 0;
	
	
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        firstRunMode = true;
        
        TransitSystem.setDefaultTimeFormat(this);
        
        //get widgets
        final MapView mapView = (MapView)findViewById(R.id.mapview);
        toggleButton = (Spinner)findViewById(R.id.predictionsOrLocations);
        chooseAPlaceButton = (Button)findViewById(R.id.chooseAPlaceButton);
        chooseAFavoriteButton = (Button)findViewById(R.id.chooseFavoriteButton);
        searchView = (EditText)findViewById(R.id.searchTextView);
        final ProgressBar progress = (ProgressBar)findViewById(R.id.progress);
        searchButton = (ImageButton)findViewById(R.id.searchButton);
        
        myLocationButton = (ImageButton)findViewById(R.id.myLocationButton);
        myLocationButton.getBackground().setAlpha(0xbb);
        tutorialLayout = (RelativeLayout)findViewById(R.id.mapViewTutorial);
        skipTutorialButton = (Button)findViewById(R.id.mapViewTutorialSkipButton);
        nextTutorialButton = (Button)findViewById(R.id.mapViewTutorialNextButton);
        
    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

    	final ProgressDialog progressDialog = new ProgressDialog(this);
		progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		progressDialog.setCancelable(true);

        
        progress.setVisibility(View.INVISIBLE);
        
        searchView.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				onSearchRequested();
			}
		});
        
        searchButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				onSearchRequested();
			}
		});
        
        chooseAPlaceButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				showIntersectionsDialog();
			}
		});
        
        chooseAFavoriteButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				showChooseStopDialog();
			}
		});
        
        myLocationButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
	    		if (arguments != null)
	    		{
	    			final LocationOverlay myLocationOverlay = arguments.getOverlayGroup().getMyLocationOverlay();
	    			if (myLocationOverlay.isMyLocationEnabled() == false)
	    			{
	    				myLocationOverlay.enableMyLocation();
	    				
	    				locationEnabled = true;
	    				
	    				Toast.makeText(Main.this, getString(R.string.findingCurrentLocation), Toast.LENGTH_SHORT).show();
	    			}
	   				myLocationOverlay.updateMapViewPosition();
	    		}
				
			}
		});
        
        skipTutorialButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				
			}
		});
        
        Resources resources = getResources();

        // busPicture is used to initialize busOverlay, otherwise it would
        // joint the rest of the drawables in the brackets 
    	Drawable busPicture = resources.getDrawable(R.drawable.bus_statelist);
    	final TransitSystem transitSystem = new TransitSystem();
        {
        	Drawable busStopUpdated = resources.getDrawable(R.drawable.busstop_statelist_updated);
        	Drawable arrow = resources.getDrawable(R.drawable.arrow);
        	Drawable tooltip = resources.getDrawable(R.drawable.tooltip);
        	Drawable rail = resources.getDrawable(R.drawable.rail_statelist);
        	Drawable intersection = resources.getDrawable(R.drawable.busstop_intersect_statelist);
        
        	Drawable busStop = resources.getDrawable(R.drawable.busstop_statelist);
        
        	TransitDrawables busDrawables = new TransitDrawables(this, busStop,
        			busStopUpdated, busPicture,
        			arrow, busPicture.getIntrinsicHeight() / 5, intersection);
        	TransitDrawables subwayDrawables = new TransitDrawables(this, busStop,
        			busStopUpdated, rail,
        			arrow, rail.getIntrinsicHeight() / 5, intersection);
        	TransitDrawables commuterRailDrawables = new TransitDrawables(this, busStop,
        			busStopUpdated, rail, arrow, rail.getIntrinsicHeight() / 5, 
        			intersection);
        	
        	transitSystem.setDefaultTransitSource(busDrawables, subwayDrawables, commuterRailDrawables, this);
        }
        SpinnerAdapter modeSpinnerAdapter = makeModeSpinner(); 

        toggleButton.setOnItemSelectedListener(new OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view,
					int position, long id) {
				if (firstRunMode)
				{
					firstRunMode = false;
				}
				else if (arguments != null && handler != null)
				{
					if (position >= 0 && position < Selection.modesSupported.length)
					{
						setMode(Selection.modesSupported[position], false, true);
					}
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
				//do nothing
			}
			

		});

        toggleButton.setAdapter(modeSpinnerAdapter);

        
        dropdownRouteKeysToTitles = transitSystem.getRouteKeysToTitles();
        
        {
            String[] routeTitles = dropdownRouteKeysToTitles.titleArray();
            
        	AlertDialog.Builder builder = new AlertDialog.Builder(this);
        	builder.setTitle(getString(R.string.chooseRouteInBuilder));
        	builder.setItems(routeTitles, new DialogInterface.OnClickListener() {
        		public void onClick(DialogInterface dialog, int item) {
        			setNewRoute(item, true);
        		}
        	});
        	routeChooserDialog = builder.create();
        }
		
        //get the busLocations variable if it already exists. We need to do that step here since handler
        long lastUpdateTime = 0;
        int previousUpdateConstantlyInterval = UPDATE_INTERVAL_NONE;

        RefreshAsyncTask majorHandler = null;
        
        Selection selection;
        Object lastNonConfigurationInstance = getLastNonConfigurationInstance();
        final OverlayGroup overlayGroup;
        Locations busLocations = null;
        if (lastNonConfigurationInstance != null)
        {
        	CurrentState currentState = (CurrentState)lastNonConfigurationInstance;
        	currentState.restoreWidgets();
        	
        	overlayGroup = currentState.cloneOverlays(this, mapView, dropdownRouteKeysToTitles, handler);
        	overlayGroup.refreshMapView(mapView);
        	
        	if (currentState.getLocationEnabled())
        	{
        		locationEnabled = true;
        		overlayGroup.getMyLocationOverlay().enableMyLocation();
        	}
        	
        	overlayGroup.getBusOverlay().refreshBalloons();
        	
        	final UpdateArguments otherArguments = currentState.getUpdateArguments();
        	
        	if (otherArguments != null) {
        		busLocations = otherArguments.getBusLocations();
            	selection = busLocations.getSelection();
        	}
        	else
        	{
        		selection = new Selection(Selection.VEHICLE_LOCATIONS_ALL, null);
        	}

        	lastUpdateTime = currentState.getLastUpdateTime();
        	previousUpdateConstantlyInterval = currentState.getUpdateConstantlyInterval();
        	progress.setVisibility(currentState.getProgressState() ? View.VISIBLE : View.INVISIBLE);
        	
        	
        	if (otherArguments != null) {
            	majorHandler = otherArguments.getMajorHandler();
        	}
        	//continue posting status updates on new textView
        	if (majorHandler != null)
        	{
        		majorHandler.setProgress(progress, progressDialog);
        	}
        }
        else
        {
        	overlayGroup = new OverlayGroup(this, busPicture, mapView, dropdownRouteKeysToTitles, handler);
        	
        	locationEnabled = prefs.getBoolean(getString(R.string.alwaysShowLocationCheckbox), true);
            int selectedRouteIndex = prefs.getInt(selectedRouteIndexKey, 0);
            int mode = prefs.getInt(selectedBusPredictionsKey, Selection.BUS_PREDICTIONS_ONE);
        	String route = dropdownRouteKeysToTitles.getTagUsingIndex(selectedRouteIndex);
            selection = new Selection(mode, route);
        }

        //final boolean showIntroScreen = prefs.getBoolean(introScreenKey, true);
    	//only show this screen once
    	//prefs.edit().putBoolean(introScreenKey, false).commit();

        if (busLocations == null)
        {
        	busLocations = new Locations(this, transitSystem, selection);
        }

        arguments = new UpdateArguments(progress, progressDialog,
        		mapView, this, overlayGroup,
        		majorHandler, busLocations, transitSystem);
        handler = new UpdateHandler(arguments);
        overlayGroup.getBusOverlay().setUpdateable(handler);
        
        populateHandlerSettings();
        
        if (lastNonConfigurationInstance != null)
        {
        	updateSearchText(selection);
        	setMode(selection.getMode(), true, false);
        }
        else
        {
            int centerLat = prefs.getInt(centerLatKey, Integer.MAX_VALUE);
            int centerLon = prefs.getInt(centerLonKey, Integer.MAX_VALUE);
            int zoomLevel = prefs.getInt(zoomLevelKey, Integer.MAX_VALUE);
            setMode(selection.getMode(), true, false);
            
        	updateSearchText(selection);

            if (centerLat != Integer.MAX_VALUE && centerLon != Integer.MAX_VALUE && zoomLevel != Integer.MAX_VALUE)
            {

            	GeoPoint point = new GeoPoint(centerLat, centerLon);
            	MapController controller = mapView.getController();
            	controller.setCenter(point);
            	controller.setZoom(zoomLevel);
            }
            else
            {
            	//move maps widget to center of transit network
            	MapController controller = mapView.getController();
            	GeoPoint location = new GeoPoint(TransitSystem.getCenterLatAsInt(), TransitSystem.getCenterLonAsInt());
            	controller.setCenter(location);

            	//set zoom depth
            	controller.setZoom(14);
            }
        	//make the textView blank
        }
        
        handler.setLastUpdateTime(lastUpdateTime);

        //show all icons if there are any
    	handler.triggerUpdate();
        if (handler.getUpdateConstantlyInterval() != UPDATE_INTERVAL_NONE &&
        		previousUpdateConstantlyInterval == UPDATE_INTERVAL_NONE)
        {
        	handler.instantRefresh();
        }

        
    	//enable plus/minus zoom buttons in map
        mapView.setBuiltInZoomControls(true);
        
        /*handler.post(new Runnable() {
        	public void run() {
                if (showIntroScreen || true)
                {
                	displayInstructions(Main.this);
                }
        		
        	}
        });*/
        
    }
		
	/**
	 * Updates search text depending on current mode
	 */
	private void updateSearchText(Selection selection) {
		if (searchView != null)
		{
			String route = selection.getRoute();
			String routeTitle = dropdownRouteKeysToTitles.getTitle(route);
			searchView.setText("Route " + routeTitle);
		}
		else
		{
			Log.i("BostonBusMap", "ERROR: search view is null");
		}
	}
	
	/**
	 * This should be called only by SearchHelper 
	 * 
	 * @param position
	 * @param saveNewQuery save a search term in the search history as if user typed it in
	 */
	public void setNewRoute(int position, boolean saveNewQuery)
    {
		if (arguments != null && handler != null)
		{
			String route = dropdownRouteKeysToTitles.getTagUsingIndex(position);
			Locations locations = arguments.getBusLocations();
			Selection selection = locations.getSelection();
			locations.setSelection(selection.withDifferentRoute(route));

			handler.immediateRefresh();
			handler.triggerUpdate();

			String routeTitle = dropdownRouteKeysToTitles.getTitle(route);
			if (routeTitle == null)
			{
				routeTitle = route;
			}

			updateSearchText(locations.getSelection());

			if (saveNewQuery)
			{
				final SearchRecentSuggestions suggestions = new SearchRecentSuggestions(Main.this, TransitContentProvider.AUTHORITY,
						TransitContentProvider.MODE);
				suggestions.saveRecentQuery("route " + routeTitle, null);
			}
		}
    }


	private SpinnerAdapter makeModeSpinner() {
    	final ArrayList<ViewingMode> modes = new ArrayList<ViewingMode>();
        
        for (int i = 0; i < Selection.modesSupported.length; i++)
        {
        	ViewingMode mode = new ViewingMode(Selection.modeIconsSupported[i], Selection.modeTextSupported[i]);
        	modes.add(mode);
        }
        
        ModeAdapter adapter = new ModeAdapter(this, modes);
        
        return adapter;
	}

	@Override
    protected void onPause() {
    	if (arguments != null)
    	{
    		final MapView mapView = arguments.getMapView();
    		GeoPoint point = mapView.getMapCenter();
    		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    		SharedPreferences.Editor editor = prefs.edit();

    		Selection selection = arguments.getBusLocations().getSelection();
    		editor.putInt(selectedBusPredictionsKey, selection.getMode());
    		editor.putInt(selectedRouteIndexKey, arguments.getBusLocations().getRouteAsIndex(selection.getRoute()));
    		editor.putInt(centerLatKey, point.getLatitudeE6());
    		editor.putInt(centerLonKey, point.getLongitudeE6());
    		editor.putInt(zoomLevelKey, mapView.getZoomLevel());
    		editor.commit();
    	}
    	
		
		if (handler != null)
		{
			handler.removeAllMessages();
			handler.nullifyProgress();
		}
		
		if (arguments != null) {
			arguments.getOverlayGroup().getMyLocationOverlay().disableMyLocation();
			if (arguments.getProgressDialog() != null) {
				arguments.getProgressDialog().dismiss();
			}
		}
		
		super.onPause();
    }

	
	@Override
	protected void onDestroy() {
		handler = null;
		if (arguments != null) {
			arguments.getOverlayGroup().getBusOverlay().setUpdateable(null);
			arguments.getOverlayGroup().getBusOverlay().clear();
			arguments.getMapView().getOverlays().clear();
		}
		arguments = null;
		
		
		searchView = null;
		
		toggleButton = null;
		
		
		super.onDestroy();
	}
	
	@Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
    	//when the menu button is clicked, a menu comes up
    	switch (item.getItemId())
    	{
    	case R.id.refreshItem:
    		boolean b = handler.instantRefresh();
    		if (b == false)
    		{
    			Toast.makeText(this, "Please wait 10 seconds before clicking Refresh again", Toast.LENGTH_LONG).show();
    		}
    		break;
    	case R.id.settingsMenuItem:
    		startActivity(new Intent(this, Preferences.class));
    		break;
    	case R.id.centerOnBostonMenuItem:
    	
    		if (arguments != null)
    		{
    			GeoPoint point = new GeoPoint(TransitSystem.getCenterLatAsInt(), TransitSystem.getCenterLonAsInt());
    			arguments.getMapView().getController().animateTo(point);
    			handler.triggerUpdate(1500);
    		}
    		break;
    	
    	
    	
    	case R.id.chooseRoute:
    		routeChooserDialog.show();
    		
    		break;
    		
    	case R.id.intersectionsMenuItem:
    		showIntersectionsDialog();
    		break;
    	
    		
    	/*case R.id.getDirectionsMenuItem:
    		{
    			// this activity starts with an Intent with an empty Bundle, which indicates
    			// all fields are blank
    			startActivityForResult(new Intent(this, GetDirectionsDialog.class), GetDirectionsDialog.GETDIRECTIONS_REQUEST_CODE);
    		}
    		
    		break;
    		*/
    	case R.id.chooseStop:
    		showChooseStopDialog();
    		break;
    	}
    	return true;
    }

    
    private void showChooseStopDialog() {
		if (arguments != null)
		{
			StopLocation[] favoriteStops = arguments.getBusLocations().getCurrentFavorites();
			
			final StopLocation[] stops = StopLocation.consolidateStops(favoriteStops);

			String[] titles = new String[stops.length];
			for (int i = 0; i < stops.length; i++)
			{
				StopLocation stop = stops[i];
				String routes = stop.getFirstRoute();
				String title = stop.getTitle() + " (route " + routes + ")";
				titles[i] = title;
			}
			
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(getString(R.string.chooseStopInBuilder));
			builder.setItems(titles, new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					if (which >= 0 && which < stops.length)
					{
						StopLocation stop = stops[which];
						
						String route = stop.getFirstRoute();
						setNewStop(route, stop.getStopTag());
						setMode(Selection.BUS_PREDICTIONS_STAR, true, true);
					}
				}
			});
			AlertDialog stopChooserDialog = builder.create();
			stopChooserDialog.show();
		}
	}

	private void showIntersectionsDialog() {
    	
		if (arguments != null) {
			Collection<String> unsortedTitles = arguments.getBusLocations().getIntersectionNames();
			
			List<String> titles = Lists.newArrayList(unsortedTitles);
			Collections.sort(titles);
			final String[] titlesArray = new String[titles.size() + 1];
			titlesArray[0] = "Add new place...";
			for (int i = 0; i < titles.size(); i++) {
				titlesArray[i+1] = titles.get(i);
			}

			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(getString(R.string.places));
			builder.setItems(titlesArray, new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					if (which == 0) {
						Toast.makeText(Main.this, "Tap a spot on the map to create a place", Toast.LENGTH_LONG).show();
						arguments.getOverlayGroup().getBusOverlay().captureNextTap(new BusOverlay.OnClickListener() {
							
							@Override
							public boolean onClick(final GeoPoint point) {
								AlertDialog.Builder builder = new AlertDialog.Builder(Main.this);
								builder.setTitle("New place name");

								final EditText textView = new EditText(Main.this);
								textView.setHint("Place name (ie, Home or Work)");
								builder.setView(textView);
								builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
									
									@Override
									public void onClick(DialogInterface dialog, int which) {
										String newName = textView.getText().toString();
										if (newName.length() == 0) {
											Toast.makeText(Main.this, "Place name cannot be empty", Toast.LENGTH_LONG).show();
										}
										else
										{
											float latitudeAsDegrees = (float) (point.getLatitudeE6() * Constants.InvE6); 
											float longitudeAsDegrees = (float) (point.getLongitudeE6() * Constants.InvE6); 
											IntersectionLocation.Builder builder = new IntersectionLocation.Builder(newName, latitudeAsDegrees, longitudeAsDegrees);
											Locations locations = arguments.getBusLocations();
											
											locations.addIntersection(builder);
											Toast.makeText(Main.this, "New place created!", Toast.LENGTH_LONG).show();
											setNewIntersection(newName);
										}
										dialog.dismiss();
									}
								});
								
								builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
									
									@Override
									public void onClick(DialogInterface dialog, int which) {
										dialog.dismiss();
									}
								});
								
								builder.create().show();
								return true;
							}
							
							@Override
							public boolean onClick(boston.Bus.Map.data.Location location) {
								return onClick(BusOverlay.toGeoPoint(location));
							}
						});
					}
					else if (which >= 1 && which < titlesArray.length) {
						setNewIntersection(titlesArray[which]);
					}
				}
			});
			AlertDialog stopChooserDialog = builder.create();
			stopChooserDialog.show();

		}
	}

	@Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
    	MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);

        return true;
    }
    
	@Override
	protected boolean isRouteDisplayed() {
		//TODO: what exactly should we return here? 
		if (arguments != null && arguments.getMapView().getOverlays().size() != 0)
		{
			return true;
		}
		else
		{
			return false;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (locationEnabled && arguments != null)
		{
			arguments.getOverlayGroup().getMyLocationOverlay().enableMyLocation();
		}
		
		//check the result
		populateHandlerSettings();
		handler.resume();
		
    	Tutorial tutorial = new Tutorial(IntroTutorial.populate());
    	tutorial.start(this);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK)
		{
			return super.onKeyDown(keyCode, event);
		}
		else if (arguments != null)
		{
			final MapView mapView = arguments.getMapView();
			if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
			{
				float centerX = mapView.getWidth() / 2;
				float centerY = mapView.getHeight() / 2;
				
				//make it a tap to the center of the screen
					
				MotionEvent downEvent = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
						MotionEvent.ACTION_DOWN, centerX, centerY, 0);
				
				
				return mapView.onTouchEvent(downEvent);
				
				
			}
			else
			{
				return mapView.onKeyDown(keyCode, event);
			}
		}
		else
		{
			return super.onKeyDown(keyCode, event);
		}
	}

	
    private void populateHandlerSettings() {
    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    	
    	int updateInterval = getUpdateInterval(prefs);
    	handler.setUpdateConstantlyInterval(updateInterval);
    	handler.setShowUnpredictable(prefs.getBoolean(getString(R.string.showUnpredictableBusesCheckbox), false));
    	handler.setHideHighlightCircle(prefs.getBoolean(getString(R.string.hideCircleCheckbox), false));
    	boolean allRoutesBlue = prefs.getBoolean(getString(R.string.allRoutesBlue), TransitSystem.isDefaultAllRoutesBlue());
    	handler.setAllRoutesBlue(allRoutesBlue);
    	arguments.getOverlayGroup().getRouteOverlay().setDrawLine(prefs.getBoolean(getString(R.string.showRouteLineCheckbox), false));
    	boolean showCoarseRouteLineCheckboxValue = prefs.getBoolean(getString(R.string.showCoarseRouteLineCheckbox), true); 
    	//handler.setInitAllRouteInfo(prefs.getBoolean(getString(R.string.initAllRouteInfoCheckbox2), true));
    	handler.setInitAllRouteInfo(true);
    	
    	boolean alwaysUpdateLocationValue = prefs.getBoolean(getString(R.string.alwaysShowLocationCheckbox), true);
    	
    	String intervalString = Integer.valueOf(updateInterval).toString();
    	//since the default value for this flag is true, make sure we let the preferences know of this
    	prefs.edit().
    		putBoolean(getString(R.string.alwaysShowLocationCheckbox), alwaysUpdateLocationValue).
    		putString(getString(R.string.updateContinuouslyInterval), intervalString).
    		putBoolean(getString(R.string.showCoarseRouteLineCheckbox), showCoarseRouteLineCheckboxValue).
    		putBoolean(getString(R.string.allRoutesBlue), allRoutesBlue)
    		.commit();
    }

	@Override
	public Object onRetainNonConfigurationInstance() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		int updateConstantlyInterval = getUpdateInterval(prefs);
		
		boolean progressVisibility = false;
		if (arguments != null && arguments.getProgress() != null) {
			progressVisibility = arguments.getProgress().getVisibility() == View.VISIBLE;
		}
		return new CurrentState(arguments, handler.getLastUpdateTime(), updateConstantlyInterval,
				progressVisibility, locationEnabled);
	}

	
	private int getUpdateInterval(SharedPreferences prefs) {
		String intervalString = prefs.getString(getString(R.string.updateContinuouslyInterval), "");
		int interval;
		if (intervalString.length() == 0) {
			interval = prefs.getBoolean(getString(R.string.runInBackgroundCheckbox), true) ? UPDATE_INTERVAL_SHORT : UPDATE_INTERVAL_NONE;
		}
		else
		{
			interval = Integer.parseInt(intervalString);
		}
		return interval;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK)
		{
			return super.onKeyUp(keyCode, event);
		}
		else if (arguments != null)
		{
			final MapView mapView = arguments.getMapView();
			if (keyCode == KeyEvent.KEYCODE_MENU)
			{
				return super.onKeyUp(keyCode, event);
			}
			handler.triggerUpdate(250);
			
			if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
			{
				float centerX = mapView.getWidth() / 2;
				float centerY = mapView.getHeight() / 2;
				
				//make it a tap to the center of the screen
					
				MotionEvent upEvent = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
						MotionEvent.ACTION_UP, centerX, centerY, 0);
				
				
				return mapView.onTouchEvent(upEvent);
				
				
			}
			else
			{
			
				return mapView.onKeyUp(keyCode, event);
			}
		}
		else
		{
			return super.onKeyUp(keyCode, event);
		}
	}

	@Override
	public boolean onTrackballEvent(MotionEvent event) {
		// TODO Auto-generated method stub
		if (arguments != null)
		{
			handler.triggerUpdate(250);
			return arguments.getMapView().onTrackballEvent(event);
		}
		else
		{
			return false;
		}
	}

	@Override
	public void onNewIntent(Intent newIntent) {
		//since Main is marked singletop, it only uses one activity and onCreate won't get called. Use this to handle search requests 
		if (Intent.ACTION_SEARCH.equals(newIntent.getAction()))
		{
			String query = newIntent.getStringExtra(SearchManager.QUERY);

			if (query == null)
			{
				return;
			}

			
			final SearchHelper helper = new SearchHelper(this, dropdownRouteKeysToTitles, arguments, query);
			helper.runSearch(new Runnable()
			{
				@Override
				public void run() {
					//search is finished
					
					String suggestionsQuery = helper.getSuggestionsQuery();
					if (suggestionsQuery != null)
					{
						SearchRecentSuggestions suggestions = new SearchRecentSuggestions(Main.this, TransitContentProvider.AUTHORITY,
								TransitContentProvider.MODE);
						suggestions.saveRecentQuery(suggestionsQuery, null);
						
						if (handler != null)
						{
							handler.triggerUpdate();
						}
					}
				}
			});

			
		}
	}

	public void setMode(int mode, boolean updateIcon, boolean triggerRefresh)
	{
		int setTo = Selection.VEHICLE_LOCATIONS_ALL; 
		for (int i = 0; i < Selection.modesSupported.length; i++)
		{
			if (Selection.modesSupported[i] == mode)
			{
				setTo = mode;
				break;
			}
		}
		
		if (updateIcon)
		{
	    	for (int i = 0; i < Selection.modesSupported.length; i++)
	    	{
	    		if (Selection.modesSupported[i] == mode)
	    		{
	    			toggleButton.setSelection(i);
	    			break;
	    		}
	    	}
		}
		
		Locations locations = arguments.getBusLocations();
		Selection oldSelection = locations.getSelection();
		Selection newSelection = oldSelection.withDifferentMode(setTo);
		locations.setSelection(newSelection);

		if (triggerRefresh) {
			handler.triggerUpdate();
			handler.immediateRefresh();
		}
		
		updateSearchText(newSelection);
		updateButtonVisibility(newSelection);
	}
	
	private void updateButtonVisibility(Selection selection) {
		int mode = selection.getMode();
		if (mode == Selection.BUS_PREDICTIONS_STAR) {
			chooseAFavoriteButton.setVisibility(View.VISIBLE);
			chooseAPlaceButton.setVisibility(View.GONE);
		}
		else
		{
			chooseAFavoriteButton.setVisibility(View.GONE);
			chooseAPlaceButton.setVisibility(View.GONE);
		}
	}

	public void setNewIntersection(String name) {
		if (arguments != null) {
			Locations locations = arguments.getBusLocations();
			
			setMode(Selection.BUS_PREDICTIONS_ALL, true, false);
			
			IntersectionLocation newLocation = locations.getIntersection(name);
			if (newLocation != null) {

				MapController controller = arguments.getMapView().getController();

				int latE6 = (int)(newLocation.getLatitudeAsDegrees() * Constants.E6);
				int lonE6 = (int)(newLocation.getLongitudeAsDegrees() * Constants.E6);

				GeoPoint geoPoint = new GeoPoint(latE6, lonE6);
				controller.setCenter(geoPoint);
				controller.scrollBy(0, -100);

				handler.triggerUpdateThenSelect(newLocation.getId());
			}
			
		}
	}
	
	/**
	 * Sets the current selected stop to stopTag, moves map over it, sets route to route, sets mode to stops for one route
	 * @param route
	 * @param stopTag
	 */
	public void setNewStop(String route, String stopTag)
	{
		StopLocation stopLocation = arguments.getBusLocations().setSelectedStop(route, stopTag);

		if (stopLocation == null)
		{
			Log.e("BostonBusMap", "Error: stopLocation was null");
			return;
		}
		
		int routePosition = dropdownRouteKeysToTitles.getIndexForTag(route);
		
		
		final int id = stopLocation.getId();
		handler.triggerUpdateThenSelect(id);

		
		
		if (routePosition != -1)
		{
			//should always happen, but we just ignore this if something went wrong
			String currentRoute = dropdownRouteKeysToTitles.getTagUsingIndex(routePosition);
			if (stopLocation.getRoutes().contains(currentRoute) == false)
			{
				//only set it if some route which contains this stop isn't already set
				setNewRoute(routePosition, false);
			}
		}
		
		setMode(Selection.BUS_PREDICTIONS_ONE, true, true);
		
		MapController controller = arguments.getMapView().getController();
		
		int latE6 = (int)(stopLocation.getLatitudeAsDegrees() * Constants.E6);
		int lonE6 = (int)(stopLocation.getLongitudeAsDegrees() * Constants.E6);
		
		GeoPoint geoPoint = new GeoPoint(latE6, lonE6);
		controller.setCenter(geoPoint);
		controller.scrollBy(0, -100);
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, Intent data) {
		if (requestCode == GetDirectionsDialog.GETDIRECTIONS_REQUEST_CODE) {
			
			final String startTag = data != null ? data.getStringExtra(GetDirectionsDialog.START_TAG_KEY) : null;
			final String stopTag = data != null ? data.getStringExtra(GetDirectionsDialog.STOP_TAG_KEY) : null;
			final String startDisplay = data != null ? data.getStringExtra(GetDirectionsDialog.START_DISPLAY_KEY) : null;
			final String stopDisplay = data != null ? data.getStringExtra(GetDirectionsDialog.STOP_DISPLAY_KEY) : null;
			
			switch (resultCode) {
			case GetDirectionsDialog.EVERYTHING_OK:
			{
				if (startTag == null) {
					Toast.makeText(this, "Starting location is not set", Toast.LENGTH_LONG).show();
					break;
				}
				if (stopTag == null) {
					Toast.makeText(this, "Ending location is not set", Toast.LENGTH_LONG).show();
					break;
				}
				
				LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
				Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
				
				arguments.getBusLocations().startGetDirectionsTask(arguments, startTag, stopTag,
						location != null ? location.getLatitude() : 0,
								location != null ? location.getLongitude() : 0);
				break;
			}
			case GetDirectionsDialog.NEEDS_INPUT_FROM:
			case GetDirectionsDialog.NEEDS_INPUT_TO:
				setMode(Selection.BUS_PREDICTIONS_ALL, true, true);
				arguments.getOverlayGroup().getBusOverlay().captureNextTap(new BusOverlay.OnClickListener() {
					@Override
					public boolean onClick(GeoPoint point) {
						return false;
					}

					@Override
					public boolean onClick(boston.Bus.Map.data.Location location) {
						if (location instanceof StopLocation) {
							StopLocation stopLocation = (StopLocation)location;
							if (resultCode == GetDirectionsDialog.NEEDS_INPUT_FROM) {
								String newStartTag = stopLocation.getStopTag();
								Intent intent = new Intent(Main.this, GetDirectionsDialog.class);
								intent.putExtra(GetDirectionsDialog.START_TAG_KEY, newStartTag);
								intent.putExtra(GetDirectionsDialog.STOP_TAG_KEY, stopTag);
								intent.putExtra(GetDirectionsDialog.START_DISPLAY_KEY, stopLocation.getTitle());
								intent.putExtra(GetDirectionsDialog.STOP_DISPLAY_KEY, stopDisplay);
								startActivityForResult(intent, GetDirectionsDialog.GETDIRECTIONS_REQUEST_CODE);
							}
							else
							{
								String newStopTag = stopLocation.getStopTag();
								Intent intent = new Intent(Main.this, GetDirectionsDialog.class);
								intent.putExtra(GetDirectionsDialog.START_TAG_KEY, startTag);
								intent.putExtra(GetDirectionsDialog.STOP_TAG_KEY, newStopTag);
								intent.putExtra(GetDirectionsDialog.START_DISPLAY_KEY, startDisplay);
								intent.putExtra(GetDirectionsDialog.STOP_DISPLAY_KEY, stopLocation.getTitle());
								startActivityForResult(intent, GetDirectionsDialog.GETDIRECTIONS_REQUEST_CODE);
							}
						}
						else
						{
							Log.e("BostonBusMap", "weird... that should have selected a stop, not a vehicle");
						}
						return true;
					}
				});
				Toast.makeText(this, "Click on the stop you wish to select", Toast.LENGTH_LONG).show();
				break;
			}
			
		}
	}
}