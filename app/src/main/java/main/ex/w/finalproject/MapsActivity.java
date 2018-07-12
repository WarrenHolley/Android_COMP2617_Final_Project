package main.ex.w.finalproject;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.Location;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;

import static java.lang.Math.pow;
import static java.lang.Math.sqrt;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    //CONFIG:
    int resolution = 10; //monodirectional pixels in the overlay.
    //Note that this is bound in at LEAST n^2.
    // A value of 10 is pretty good for low-end phones.

    LatLngBounds newWestBounds = new LatLngBounds(
            new LatLng(49.199515,-122.940879),       // South west corner
            new LatLng(49.223310,-122.905474));      // North east corner
    //Configure as needed. This range gives pretty good coverage.


    //Vars.
    private GoogleMap mMap;
    private SharedPreferences preferences;

    double minScore;
    double maxScore;

    double[][] fiberCoords;
    double[][] busCoords;
    double[][] govCoords;
    double[][] schoolCoords;
    double[][] playgroundCoords;

    final String TAG = MapsActivity.class.getName();

    LatLng locationGrid[][]; //Stores each lat-lng grid coords.
    double gridScore[][];  //Total Score. {-15,15}

    Bitmap bitmap;
    GroundOverlayOptions goo;
    GroundOverlay imageOverlay;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        //Set up Map.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        download(); //Download files.

        //Expected score range in the {-15,15} range.
        minScore = 100;
        maxScore = -100;

        gridScore = new double[resolution][resolution];

        //Set up comms between settings and maps activity.
        preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        LatLng newWest = new LatLng(49.2069,-122.911);

        bitmap = Bitmap.createBitmap(resolution,resolution,Bitmap.Config.ARGB_8888);
        for (int i = 0; i < resolution; i++)
            for (int j = 0; j < resolution; j++)
                bitmap.setPixel(i,j,Color.RED);

        //processScore();
        //updateMap();

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newWest,13));
        locationGrid = seperateLatLng(newWestBounds);

    }

    public void updateMap() {
        //Reset, regenerate overlay.
        if (imageOverlay != null)
            imageOverlay.remove();

        goo = new GroundOverlayOptions()
                .image(BitmapDescriptorFactory.fromBitmap(bitmap))
                .positionFromBounds(newWestBounds)
                .transparency((float)0.4);
        imageOverlay = mMap.addGroundOverlay(goo);
    }

    public void configButton(final View view) {
        Intent intent = new Intent(this, Settings.class);
        startActivity(intent);
    }

    public void printFirst(String name, double[][] printSet) {
        Log.i(TAG,"BUNNIES "+ name+" "+printSet[0][0] + " "+ printSet[0][1]+" "+printSet.length);
    }

    @Override
    protected void onResume() { //As to avoid frontloading bitmap config on the button press.
        super.onResume();
        if (fiberCoords != null) {
            processScore();
            updateMap();
        }
    }


    //Fetch order is bus,fiber,government.
    //Hate = -1, Meh = 1, Love = 3
    public void processScore() {
        //Reset Grid.
        for (int i = 0; i < resolution; i++) {
            for (int j = 0; j < resolution; j++) {
                gridScore[i][j] = 0;
            }
        }

        if (govCoords != null)
            scoreServices();
        if (busCoords != null)
            scoreBusses();
        if (fiberCoords != null)
            scoreFiber();
        if (schoolCoords != null)
            scoreSchools();
        if (playgroundCoords != null)
            scorePlaygrounds();

        //Reset min/max values. {-15,15}
        minScore = 300;
        maxScore = -300;
        for (int i = 0; i < resolution; i++) {
            for (int j = 0; j < resolution; j++) {
                if (gridScore[i][j] > maxScore)
                    maxScore = gridScore[i][j];
                else if (gridScore[i][j] < minScore)
                    minScore = gridScore[i][j];
            }
        }
        for (int i = 0; i < resolution; i++) {
            for (int j = 0; j < resolution; j++) {
                bitmap.setPixel(i,j,getScoreColor(gridScore[i][j]));
                //Log.i(TAG,"BUNNIES"+getScoreColor(gridScore[i][j]));
                //Log.i(TAG,"BUNNIES"+gridScore[i][j]);
            }
        }
        Log.i(TAG,"BUNNIES "+minScore+ " "+maxScore);
    }

    //Score as inverse of distance to nearest 3 schools
    public void scoreSchools() {
        double scoreMultiplier = (double) preferences.getInt("Radio3",1)-1;
        if (scoreMultiplier == 0)
            return;
        if (scoreMultiplier == 2)
            scoreMultiplier++; //2->3

        double[][] localGridScore = new double[resolution][resolution];
        double[] distanceTo = new double[schoolCoords.length];
        float[] tempDistance = new float[3];

        for (int i = 0; i < resolution; i++) {
            for (int j = 0; j < resolution; j++) {
                for (int k = 0; k < schoolCoords.length; k++) {
                    Location.distanceBetween(locationGrid[i][j].latitude, locationGrid[i][j].longitude,
                            schoolCoords[k][1], schoolCoords[k][0], tempDistance);
                    distanceTo[k] = tempDistance[0];
                }

                Arrays.sort(distanceTo);

                double totalDist = 0;
                for (int k = 0; k < 3; k++) {
                    if (distanceTo[k] <= 10)
                        distanceTo[k] = 10; // Div by zero catch. Also massive overscore catch.
                    totalDist += distanceTo[k];
                }
                localGridScore[i][j] = 1.0 / totalDist;
            }
        }

        double minScore = 1;
        double maxScore = 0;

        for (int i = 0; i < resolution; i++) {
            for (int j = 0; j < resolution; j++) {
                if (localGridScore[i][j] > maxScore)
                    maxScore = localGridScore[i][j];
                if (localGridScore[i][j] < minScore)
                    minScore = localGridScore[i][j];
            }
        }
        //Normalize to {0,1}
        for (int i = 0; i < resolution; i++) {
            for (int j = 0; j < resolution; j++) {
                double temp = scoreMultiplier * (localGridScore[i][j] - minScore) / (maxScore-minScore);
                gridScore[i][j] += temp;
            }
        }
    }

    //Score as inverse of total distance to nearest 5 playgrounds stops.
    public void scorePlaygrounds() {
        double scoreMultiplier = (double) preferences.getInt("Radio4",1)-1;

        double[][] localGridScore = new double[resolution][resolution];
        double[] distanceTo = new double[playgroundCoords.length];
        float[] tempDistance = new float[3];

        for (int i = 0; i < resolution; i++) {
            for (int j = 0; j < resolution; j++) {
                for (int k = 0; k < playgroundCoords.length; k++) {
                    Location.distanceBetween(locationGrid[i][j].latitude, locationGrid[i][j].longitude,
                            playgroundCoords[k][1], playgroundCoords[k][0], tempDistance);
                    distanceTo[k] = tempDistance[0];
                }

                Arrays.sort(distanceTo);

                double totalDist = 0;
                for (int k = 0; k < 5; k++) {
                    if (distanceTo[k] <= 10)
                        distanceTo[k] = 10; // Div by zero catch. Also massive overscore catch.
                    totalDist += distanceTo[k];
                }
                localGridScore[i][j] = 1.0 / totalDist;

            }
        }

        double minScore = 1;
        double maxScore = 0;

        for (int i = 0; i < resolution; i++) {
            for (int j = 0; j < resolution; j++) {
                if (localGridScore[i][j] > maxScore)
                    maxScore = localGridScore[i][j];
                if (localGridScore[i][j] < minScore)
                    minScore = localGridScore[i][j];
            }
        }
        //Normalize
        for (int i = 0; i < resolution; i++) {
            for (int j = 0; j < resolution; j++) {
                double temp = scoreMultiplier * (localGridScore[i][j] - minScore) / (maxScore-minScore);
                Log.i(TAG,"BUNNIES " + temp);
                gridScore[i][j] += temp;
            }
        }

    }

    //Score as inverse of total distance to nearest 20 bus stops.
    public void scoreBusses() {
        double scoreMultiplier = (double) preferences.getInt("Radio0",1)-1;
        if (scoreMultiplier == 0) //if 'Ignore'
            return;
        else if (scoreMultiplier == 2)
            scoreMultiplier++;


        double minDistance = 0;
        double maxDistance = 10000;

        double totalDist[][] = new double[resolution][resolution];
        double distanceTo[] = new double[busCoords.length];
        float singleDist[] = new float[3];
        for (int i = 0; i < resolution; i++) {
            for (int j = 0; j < resolution; j++) {
                for (int k = 0; k < busCoords.length; k++) {
                    Location.distanceBetween(locationGrid[i][j].latitude, locationGrid[i][j].longitude,
                            busCoords[k][1], busCoords[k][0], singleDist);
                    distanceTo[k] = singleDist[0];
                }
                Arrays.sort(distanceTo);

                totalDist[i][j] = 0;
                for (int k = 0; k < 20;k++)
                    totalDist[i][j] += distanceTo[k];
                totalDist[i][j] = 1.0/totalDist[i][j];
            }
        }

        double minDistScore = 10000;
        double maxDistScore = 0;
        for (int i = 0; i < resolution; i++) {
            for (int j = 0; j < resolution; j++) {
                if (totalDist[i][j] > maxDistScore)
                    maxDistScore = totalDist[i][j];
                else if (totalDist[i][j] < minDistScore)
                    minDistScore = totalDist[i][j];
            }
        }


        //Normalize:
        for (int i = 0; i < resolution; i++) {
            for (int j = 0; j < resolution; j++) {
                totalDist[i][j] = scoreMultiplier*(totalDist[i][j] - minDistScore)/(maxDistScore-minDistScore);
                //Log.i(TAG,"BUNNIES "+totalDist[i][j]);
                gridScore[i][j] += totalDist[i][j];
            }
        }
    }

    //Score as inverse to nearest 5 fiber locations.
    //Would have been 1, and probably should be at higher resolutions, but at the moment there is
    // a MASSIVE bias towards very few grid-locs.
    public void scoreFiber() {
        double scoreMultiplier = (double) preferences.getInt("Radio1",1)-1;
        if (scoreMultiplier == 0) //if 'Ignore'
            return;
        else if (scoreMultiplier == 2)
            scoreMultiplier++;


        double minDistance = 0;
        double maxDistance = 10000;

        double totalDist[][] = new double[resolution][resolution];
        double distanceTo[] = new double[fiberCoords.length];
        float singleDist[] = new float[3];

        for (int i = 0; i < resolution; i++) {
            for (int j = 0; j < resolution; j++) {
                for (int k = 0; k < fiberCoords.length; k++) {
                    Location.distanceBetween(locationGrid[i][j].latitude, locationGrid[i][j].longitude,
                            fiberCoords[k][1], fiberCoords[k][0], singleDist);
                    if (singleDist[0] < 10)
                        singleDist[0] = 10;
                    distanceTo[k] = singleDist[0];

                }
                Arrays.sort(distanceTo);
                totalDist[i][j] = 0;
                for (int k = 0; k < 5;k++)
                    totalDist[i][j] += distanceTo[k];
                //Log.i(TAG,"BUNNIES"+totalDist[i][j]);
                totalDist[i][j] = 1.0/totalDist[i][j];

            }
        }

        double minDistScore = 10000;
        double maxDistScore = 0;
        for (int i = 0; i < resolution; i++) {
            for (int j = 0; j < resolution; j++) {
                if (totalDist[i][j] > maxDistScore)
                    maxDistScore = totalDist[i][j];
                else if (totalDist[i][j] < minDistScore)
                    minDistScore = totalDist[i][j];
            }
        }


        //Normalize:
        for (int i = 0; i < resolution; i++) {
            for (int j = 0; j < resolution; j++) {
                totalDist[i][j] = scoreMultiplier*(totalDist[i][j] - minDistScore)/(maxDistScore-minDistScore);
                //Log.i(TAG,"BUNNIES "+totalDist[i][j]);
                gridScore[i][j] += totalDist[i][j];
            }
        }
    }


    // Score as count to all services within a kilometer. (Reasonable walking distance)
    public void scoreServices() {

        double scoreMultiplier = (double) preferences.getInt("Radio2",1)-1;
        if (scoreMultiplier == 0) //if 'Ignore'
            return;
        else if (scoreMultiplier == 2)
            scoreMultiplier++; //2->3

        int serviceCount[][] = new int[resolution][resolution];
        for (int i = 0; i < resolution; i++) {
            for (int j = 0; j < resolution; j++) {
                float serviceDistance[] = new float[3];
                for (int k = 0; k < govCoords.length; k++) {
                    Location.distanceBetween(locationGrid[i][j].latitude, locationGrid[i][j].longitude,
                            govCoords[k][1], govCoords[k][0], serviceDistance);
                    if (serviceDistance[0] <= 1000.0)
                        serviceCount[i][j]++;
                }
            }
        }

        double maxVal = 0;
        double minVal = govCoords.length;
        for (int i = 0; i < resolution; i++) {
            for (int j = 0; j < resolution; j++) {
                if (serviceCount[i][j] > maxVal)
                    maxVal = serviceCount[i][j];
                if (serviceCount[i][j] < minVal)
                    minVal = serviceCount[i][j];
            }

        }
        //Map to {0,1},
        //Multiply by score
        for (int i = 0; i < resolution; i++) {
            for (int j = 0; j < resolution; j++) {
                double totalScore = scoreMultiplier*(serviceCount[i][j] - minVal)/(maxVal-minVal);
                gridScore[resolution-i-1][j] += totalScore;
            }
        }
    }

    //Returns a colour value as bound in the red->yellow->green color shift.
    public int getScoreColor(double score) {
        if (maxScore == minScore || maxScore < minScore)
            return Color.TRANSPARENT; //If no score, no overlay.

        double realScore = (score-minScore) / (maxScore - minScore);
        //Red = ff0000, Yellow=ffff00,Green=00ff00;
        //Thus, ff->ffff->00ff
        //All the power and sqrt bullshit is just to increase the range of yellow.
        //Its harder to distiguish yellow->green than yellow->red.
        int green;
        int red;

        if (realScore > 0.5) {
            realScore = pow(realScore*2,4)/30.0+14/30.0;
            green=0xff;
            red = (int) (0xff* (2-realScore*2.0));
        }
        else {
            realScore=sqrt(realScore*2)/2;
            red = 0xff;
            green = (int)(0xff*2*realScore);
        }
        return 0xff*0x1000000+red*0x10000+green*0x100;
    }

    //From a latlngBound, return an array of latlngs that map to the sub gridpoints of the map.
    //Currently point to the corners of the pixels. TODO: Update to center of pixel. Easier to see.
    public LatLng[][] seperateLatLng(LatLngBounds bounds){
        LatLng ne = bounds.northeast;
        LatLng sw = bounds.southwest;

        double seperatorLat[] = new double[resolution];
        double seperatorLng[] = new double[resolution];
        double latRes = (ne.latitude - sw.latitude)/resolution;
        double lngRes = (ne.longitude - sw.longitude)/resolution;

        //For now, just leave on corner. Maybe shift to center.
        for (int i = 0; i < resolution; i++) {
            seperatorLat[i] = sw.latitude + latRes * i;
            seperatorLng[i] = sw.longitude+ lngRes * i;
        }
        LatLng returnArray[][] = new LatLng[resolution][resolution];
        for (int i = 0; i < resolution; i++)
            for (int j = 0; j < resolution; j++)
                returnArray[i][j] = new LatLng(seperatorLat[i],seperatorLng[j]);
        return returnArray;
    }





    public void download() {
        DownloadTask task1 = new DownloadTask();
        DownloadTask task2 = new DownloadTask();
        DownloadTask task3 = new DownloadTask();
        DownloadTask task4 = new DownloadTask();
        DownloadTask task5 = new DownloadTask();

        task1.execute("http://opendata.newwestcity.ca/downloads/fiber-network-locations/FIBER_NETWORK_LOCATIONS.json");
        task2.execute("http://opendata.newwestcity.ca/downloads/bus-stops/BUS_STOPS.json");
        task3.execute("http://opendata.newwestcity.ca/downloads/government/GOVERNMENT_AND_JUSTICE_SERVICES.json");
        task4.execute("http://opendata.newwestcity.ca/downloads/significant-buildings-schools/SIGNIFICANT_BLDG_SCHOOLS.json");
        task5.execute("http://opendata.newwestcity.ca/downloads/playgrounds/PLAYGROUNDS.json");
    }
    private class DownloadTask extends AsyncTask<String,Integer,String> {
        @Override
        protected String doInBackground (final String... strings) {
            try {
                final URL url;
                final URLConnection connection;
                final InputStream inputStream;

                url = new URL(strings[0]);
                connection = url.openConnection();
                inputStream = connection.getInputStream();

                BufferedReader buffReader = new BufferedReader(new InputStreamReader( inputStream));
                String inputLine;
                final StringBuilder builder = new StringBuilder();

                while ((inputLine = buffReader.readLine()) != null) {
                    builder.append(inputLine);
                }
                buffReader.close();
                inputStream.close();

                return builder.toString();
            }
            catch (final MalformedURLException ex) {
                Log.e(TAG,"BUNNIES badURL",ex);
            }
            catch (final IOException ex) {
                Log.e(TAG,"BUNNIES IO Error",ex);
            }
            return null;
        }
        @Override
        protected void onPostExecute(final String json) {
            final Gson gson = new Gson();

            final Generic generic = gson.fromJson(json,Generic.class);
            //Log.i(TAG,"BUNNIES"+generic.getName());

            if (generic.getName().equals("FIBER_NETWORK_LOCATIONS")) {
                final Fiber fiber = gson.fromJson(json,Fiber.class);
                fiberCoords = fiber.getCoords();
                printFirst(generic.getName(),fiberCoords);
            }
            else if (generic.getName().equals("BUS_STOPS")) {
                final BusStop busStop = gson.fromJson(json,BusStop.class);
                busCoords = busStop.getCoods();
                printFirst(generic.getName(),busCoords);
            }
            else if (generic.getName().equals("GOVERNMENT_AND_JUSTICE_SERVICES")) {
                final GovernmentServices govServices = gson.fromJson(json, GovernmentServices.class);
                govCoords = govServices.getCoods();
                printFirst(generic.getName(),govCoords);
            }
            else if (generic.getName().equals("SIGNIFICANT_BLDG_SCHOOLS")) {
                final Schools schools = gson.fromJson(json, Schools.class);
                schoolCoords = schools.getCoords();
                printFirst(generic.getName(),schoolCoords);
            }
            else if (generic.getName().equals("PLAYGROUNDS")) {
                final Playgrounds playgrounds = gson.fromJson(json, Playgrounds.class);
                playgroundCoords = playgrounds.getCoords();
                printFirst(generic.getName(),playgroundCoords);
            }
            else {
                Log.i(TAG,"BUNNIES: ERROR IN PARSING");
            }
        }
    }
}




class Generic {
    private String name;
    public String getName() { return name;}
}

class Schools {
    private Feature[] features;

    //Realized that coords could have been pulled from the 'Properties' tag.
    // Ain't broke.
    public double[][] getCoords() {
        double retArray[][] = new double[features.length][2];
        for (int i = 0; i < features.length; i++) {
            retArray[i][0] = features[i].geometry.coordinates[0][0][0];
            retArray[i][1] = features[i].geometry.coordinates[0][0][1];
        }
        return retArray;
    }


    public static class Feature {
        private Geometry geometry;
        public static class Geometry {
            private double[][][] coordinates;
        }
    }
}

class Playgrounds {
    private Feature[] features;

    public double[][] getCoords() {
        double retArray[][] = new double[features.length][2];
        for (int i = 0; i < features.length; i++) {
            retArray[i][0] = features[i].geometry.coordinates[0];
            retArray[i][1] = features[i].geometry.coordinates[1];
        }
        return retArray;
    }

    public static class Feature {
        private Geometry geometry;
        public static class Geometry {
            private double[] coordinates;
        }
    }
}

class GovernmentServices {
    private Feature[] features;

    public double[][] getCoods() {
        double retArray[][] = new double[features.length][2];
        for (int i = 0; i < features.length; i++) {
            retArray[i][0] = features[i].geometry.coordinates[0];
            retArray[i][1] = features[i].geometry.coordinates[1];
        }
        return retArray;
    }
    public static class Feature {
        private Geometry geometry;
        public static class Geometry {
            private double[] coordinates;
        }
    }
}

class Fiber {
    private String name;
    private String type;
    private Feature[] features;

    public double[][] getCoords() {
        double retArr[][] = new double[features.length][2];
        for (int i = 0; i < features.length; i++) {
            retArr[i][0] = features[i].geometry.coordinates[0];
            retArr[i][1] = features[i].geometry.coordinates[1];
        }
        return retArr;
    }

    public static class Feature {
        private Geometry geometry;
        public static class Geometry {
            private String type;
            private double[] coordinates;
        }
    }
}
class BusStop {
    private Feature[] features;

    public double[][] getCoods() {
        double retArray[][] = new double[features.length][2];
        for (int i = 0; i < features.length; i++) {
            retArray[i][0] = features[i].geometry.coordinates[0];
            retArray[i][1] = features[i].geometry.coordinates[1];
        }
        return retArray;
    }
    public static class Feature {
        private Geometry geometry;
        public static class Geometry {
            private double[] coordinates;
        }
    }
}


