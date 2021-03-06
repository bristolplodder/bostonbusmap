package boston.Bus.Map.data;

import android.graphics.drawable.Drawable;

public class CommuterTrainLocation extends BusLocation {

	public CommuterTrainLocation(float latitude, float longitude, String id,
			long lastFeedUpdateInMillis, long lastUpdateInMillis,
			String heading, boolean predictable, String dirTag,
			String inferBusRoute,
			String routeName, Directions directions, String routeTitle,
			boolean disappearAfterRefresh) {
		super(latitude, longitude, id, lastFeedUpdateInMillis, lastUpdateInMillis,
				heading, predictable, dirTag, inferBusRoute, routeName,
				directions, routeTitle, disappearAfterRefresh);
		// TODO Auto-generated constructor stub
	}

	private static final String experimentalString = "<font color='red' size='1'>Commuter rail predictions are experimental</font>";
	
	@Override
	protected String getBetaWarning() {
		return experimentalString + "<br />";
	}
	
	@Override
	protected String getBusNumberMessage() {
		return "Train number: " + busId + "<br />\n";
	}
}
