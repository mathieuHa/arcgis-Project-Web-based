package fr.hanotaux.mathieu.arcgis;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class GetRouteService extends IntentService {
    // TODO: Rename actions, choose action names that describe tasks that this
    private static final String TAG = "GetRouteService";
    private static final String ACTION_ROUTE = "fr.hanotaux.mathieu.arcgis.ROUTE";
    private static final String START_POINT = "fr.hanotaux.mathieu.arcgis.START_POINT";
    private static final String ARRIVE_POINT = "fr.hanotaux.mathieu.arcgis.ARRIVE_POINT";

    public GetRouteService() {
        super("GetRouteService");
    }

    public static void startActionRoute(Context context, String nameStart, String nameArrive) {
        Log.d(TAG,"start action route");
        Intent intent = new Intent(context, GetRouteService.class);
        intent.setAction(ACTION_ROUTE);
        intent.putExtra(START_POINT, nameStart);
        intent.putExtra(ARRIVE_POINT, nameArrive);
        context.startService(intent);
    }


    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_ROUTE.equals(action)) {
                Log.d(TAG,"onHandleIntent");
                final String start = intent.getStringExtra(START_POINT);
                final String arrive = intent.getStringExtra(ARRIVE_POINT);
                handleActionRoute(start, arrive);
            }
        }
    }

    private void handleActionRoute(String start, String arrive) {
        Log.d(TAG,"IT WORKED");
        URL url = null;
        try {
            Log.d(TAG,"https://maps.googleapis.com/maps/api/directions/json?origin="+start+"&destination="+arrive+"&sensor=false&mode=driving&language=fr");
            url = new URL ("https://maps.googleapis.com/maps/api/directions/json?origin="+start+"&destination="+arrive+"&sensor=false&mode=driving&language=fr");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            if (HttpURLConnection.HTTP_OK == connection.getResponseCode()){
                copyInputStreamToFile(connection.getInputStream(),
                        new File(getCacheDir(), "/routes.json"));
                Log.d(TAG,"DL complete");
            } else {
                Log.d(TAG,"Error DL");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(ShowMap.ROUTE_UPDATE));
    }

    private void copyInputStreamToFile (InputStream in, File file){
        try {
            OutputStream ou =  new FileOutputStream(file);
            byte[] buf = new byte[1024];
            int len;
            while ((len=in.read(buf))>0){
                ou.write(buf,0,len);
            }
            ou.close();
            in.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
