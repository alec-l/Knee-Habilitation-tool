/*
 * Copyright 2015 MbientLab Inc. All rights reserved.
 *
 * IMPORTANT: Your use of this Software is limited to those specific rights
 * granted under the terms of a software license agreement between the user who
 * downloaded the software, his/her employer (which must be your employer) and
 * MbientLab Inc, (the "License").  You may not use this Software unless you
 * agree to abide by the terms of the License which can be found at
 * www.mbientlab.com/terms . The License limits your use, and you acknowledge,
 * that the  Software may not be modified, copied or distributed and can be used
 * solely and exclusively in conjunction with a MbientLab Inc, product.  Other
 * than for the foregoing purpose, you may not use, reproduce, copy, prepare
 * derivative works of, modify, distribute, perform, display or sell this
 * Software and/or its documentation for any purpose.
 *
 * YOU FURTHER ACKNOWLEDGE AND AGREE THAT THE SOFTWARE AND DOCUMENTATION ARE
 * PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESS OR IMPLIED,
 * INCLUDING WITHOUT LIMITATION, ANY WARRANTY OF MERCHANTABILITY, TITLE,
 * NON-INFRINGEMENT AND FITNESS FOR A PARTICULAR PURPOSE. IN NO EVENT SHALL
 * MBIENTLAB OR ITS LICENSORS BE LIABLE OR OBLIGATED UNDER CONTRACT, NEGLIGENCE,
 * STRICT LIABILITY, CONTRIBUTION, BREACH OF WARRANTY, OR OTHER LEGAL EQUITABLE
 * THEORY ANY DIRECT OR INDIRECT DAMAGES OR EXPENSES INCLUDING BUT NOT LIMITED
 * TO ANY INCIDENTAL, SPECIAL, INDIRECT, PUNITIVE OR CONSEQUENTIAL DAMAGES, LOST
 * PROFITS OR LOST DATA, COST OF PROCUREMENT OF SUBSTITUTE GOODS, TECHNOLOGY,
 * SERVICES, OR ANY CLAIMS BY THIRD PARTIES (INCLUDING BUT NOT LIMITED TO ANY
 * DEFENSE THEREOF), OR OTHER SIMILAR COSTS.
 *
 * Should you have any questions regarding your right to use this Software,
 * contact MbientLab Inc, at www.mbientlab.com.
 */

package com.mbientlab.metawear.app;

import android.content.Context;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.app.help.HelpOption;
import com.mbientlab.metawear.app.help.HelpOptionAdapter;
import com.mbientlab.metawear.data.AngularVelocity;
import com.mbientlab.metawear.data.EulerAngles;
import com.mbientlab.metawear.data.Quaternion;
import com.mbientlab.metawear.module.GyroBmi160;
import com.mbientlab.metawear.module.SensorFusionBosch;
import com.mbientlab.metawear.module.SensorFusionBosch.*;
import com.mbientlab.metawear.module.Logging;
import com.mbientlab.metawear.module.Settings;
import com.mbientlab.metawear.module.Debug;
import com.mbientlab.metawear.module.GyroBmi160;
import com.mbientlab.metawear.module.GyroBmi160.Range;
import com.mbientlab.metawear.module.GyroBmi160.OutputDataRate;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

import bolts.Continuation;
import bolts.Task;

import static com.mbientlab.metawear.module.SensorFusionBosch.CalibrationAccuracy.HIGH_ACCURACY;
import static java.lang.Math.abs;

/**
  Modified by Alec on 1/8/2018 */

public class SensorFusionFragment extends SensorFragment implements ServiceConnection {
    private static final float SAMPLING_PERIOD = 1 / 15f;

    private final ArrayList<Entry> x0 = new ArrayList<>(), x1 = new ArrayList<>(), x2 = new ArrayList<>(), x3 = new ArrayList<>();

    // Used Variables
    private int srcIndex = 0;
    double count = 0; //for rep count loop
    long lastBeep = 0;
    long lastCal= 0;
    public MetaWearBoard mwBoard2 = ScannerActivity2.mwBoard2;
    public MetaWearBoard mwBoard = ScannerActivity.mwBoard;
    public SensorFusionBosch sensorFusion2 = mwBoard2.getModule(SensorFusionBosch.class);
    public SensorFusionBosch sensorFusion = mwBoard.getModule(SensorFusionBosch.class);
    final Logging logging =mwBoard.getModule(Logging.class);
    final Logging logging2 =mwBoard.getModule(Logging.class);
    public MediaPlayer beep;
    float Xi1,Yi1,Zi1,Wi1,Xi2,Yi2,Zi2,Wi2;
    float temp=-1;
    float temphold=0;
    float dummy =0;





    // Sets the view of the activity
    public SensorFusionFragment() {
        super(R.string.navigation_fragment_sensor_fusion, R.layout.fragment_sensor_config_spinner, -1f, 1f);
    }

    @Override
    // Controls What happens with the SPINNER. Defaulted this value to "1" as we only need the one option.
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ((TextView) view.findViewById(R.id.config_option_title)).setText(R.string.config_name_sensor_fusion_data);

        Spinner fusionModeSelection = (Spinner) view.findViewById(R.id.config_option_spinner);
        fusionModeSelection.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                srcIndex = 1;

                final YAxis leftAxis = chart.getAxisLeft();
                if (srcIndex == 0) {
                    leftAxis.setAxisMaxValue(1.f);
                    leftAxis.setAxisMinValue(-1.f);
                } else {
                    leftAxis.setAxisMaxValue(360f);
                    leftAxis.setAxisMinValue(-360f);
                }

                refreshChart(false);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(getContext(), R.array.values_fusion_data, android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fusionModeSelection.setAdapter(spinnerAdapter);
    }

    @Override
    //Configures the Sensors and updates the Chart with sensor data IMUPlus and M4G can be alternate modes
    protected void setup() {
        sensorFusion.configure()
                .mode(Mode.NDOF)
                .accRange(AccRange.AR_16G)
                .gyroRange(GyroRange.GR_2000DPS)
                .commit();





        sensorFusion2.configure()
                .mode(Mode.NDOF)
                .accRange(AccRange.AR_16G)
                .gyroRange(GyroRange.GR_2000DPS)
                .commit();


    }
    // ATOMIC REFRENCES MUST BE USED TO ALLOW CONTINUOUS REFERENCE TO SENSOR VALUES BETWEEN MULTIPLE THREADS. CRUCIAL FOR COMPUTATIONS BETWEEN SENSORS
    AtomicReference<Quaternion> angle1 = new AtomicReference<>();
    AtomicReference<Quaternion> angle3 = new AtomicReference<>();
    AtomicReference<AngularVelocity> gyroVal1 = new AtomicReference<>();
    AtomicReference<AngularVelocity> gyroVal2 = new AtomicReference<>();



    public void StartMath() {



    }

    protected void logstart(){



    }

    // USED FOR DOWNLOADING THE LOGFILE/CSV
    protected void logstop(){
        logging.stop();
        logging2.stop();

        logging.downloadAsync(100, new Logging.LogDownloadUpdateHandler() {
            @Override
            public void receivedUpdate(long nEntriesLeft, long totalEntries) {
                Log.i("MainActivity", "Progress Update = " + nEntriesLeft + "/" + totalEntries);
            }
        }).continueWithTask(new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> task) throws Exception {
                Log.i("MainActivity", "Download completed");
                return null;
            }
        });

        logging2.downloadAsync(100, new Logging.LogDownloadUpdateHandler() {
            @Override
            public void receivedUpdate(long nEntriesLeft, long totalEntries) {
                Log.i("MainActivity", "Progress Update = " + nEntriesLeft + "/" + totalEntries);
            }
        }).continueWithTask(new Continuation<Void, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Void> task) throws Exception {
                Log.i("MainActivity", "Download completed");
                return null;
            }
        });
    }






    // SENSOR 2 ROUTE
    public float Sensor2() {
        sensorFusion2.quaternion().addRouteAsync(source -> source.limit(25).stream((data, env) -> {



            angle3.set(data.value(Quaternion.class));

        })).continueWith(task -> {
            streamRoute2 = task.getResult();
            sensorFusion2.quaternion().start();
            sensorFusion2.start();
            return 0;
        });

        sensorFusion2.correctedAngularVelocity().addRouteAsync(source -> source.limit(25).stream((data, env) -> {


            gyroVal2.set(data.value(CorrectedAngularVelocity.class));

        })).continueWith(task -> {
            streamRoute2 = task.getResult();
            sensorFusion2.quaternion().start();
            sensorFusion2.correctedAngularVelocity().start();
            sensorFusion2.start();
            return 0;
        });

        sensorFusion.correctedAngularVelocity().addRouteAsync(source -> source.limit(25).stream((data, env) -> {

            gyroVal1.set(data.value(CorrectedAngularVelocity.class));

        })).continueWith(task -> {
            streamRoute2 = task.getResult();
            sensorFusion2.quaternion().start();
            sensorFusion2.correctedAngularVelocity().start();
            sensorFusion2.start();
            sensorFusion.correctedAngularVelocity().start();



            return 0;
        });
        return 0;


    }


    float i=0;

    // SENSOR 1 ROUTE & MATHEMATICAL COMPUTATIONS BETWEEN SENSOR 2
    public float Sensor1() {
        sensorFusion.quaternion().addRouteAsync(source -> source.limit(25).stream((data, env) -> {
            // Put Yaw value form Euler Angles into A TextView


            sensorFusion.correctedAngularVelocity().start();
            sensorFusion2.correctedAngularVelocity().start();
            angle1.set(data.value(Quaternion.class));

            Quaternion Cal = data.value(Quaternion.class);
            float CalX = Cal.x();


            // Prevent Null pointer exception by requiring the angle values to be something other than NULL
            if (angle1.get() != null && angle3.get() != null) {


                // Line Below Ensures that the textviews can be updated on the UI thread
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {


                        long time = SystemClock.elapsedRealtime();



                        if (i < 2) {

                            // grabbing the intial position of sensor 1
                            float CalX = angle1.get().x();
                            Xi1 = CalX;
                            float CalY = angle1.get().y();
                            Yi1 = CalY;
                            float CalZ = angle1.get().z();
                            Zi1 = CalZ;
                            float CalW = angle1.get().w();
                            Wi1 = CalW;

                            // grabbing the intial position of sensor 2
                            float CalX2 = angle3.get().x();
                            Xi2 = CalX2;
                            float CalY2 = angle3.get().y();
                            Yi2 = CalY2;
                            float CalZ2 = angle3.get().z();
                            Zi2 = CalZ2;
                            float CalW2 = angle3.get().w();
                            Wi2 = CalW2;


                        }

                        float SensorFinalX;
                        float SensorFinalY;
                        float SensorFinalZ;
                        LineData chartData = chart.getData();

                        // Sensor 1 Displacement Vector components

                        float w = (angle1.get().w() * Wi1);
                        float x = (angle1.get().x() * Xi1);
                        float y = (angle1.get().y() * Yi1);
                        float z = (angle1.get().z() * Zi1);

                        //Sensor 2 Displacement vector components

                        float w2 = (angle3.get().w() * Wi2);
                        float x2 = (angle3.get().x() * Xi2);
                        float y2 = (angle3.get().y() * Yi2);
                        float z2 = (angle3.get().z() * Zi2);



                        // Sensor 1 Displacement Vector
                        float q1 = (w + x + y + z);
                        float theta = (float) Math.acos(2 * (Math.pow(q1, 2)) - 1);


                        if (Float.isNaN(theta)) {
                            theta = 0;
                        }


                        else {
                            theta = (float) Math.acos(2 * (Math.pow(q1, 2)) - 1);
                        }


                        double thetaDeg = (double) (theta * (180 / Math.PI));

                        if((angle1.get().y()-Yi1) < 0.01 ) {
                            thetaDeg=thetaDeg*(-1);
                        }
                        else {
                            thetaDeg = (double) (theta * (180 / Math.PI));
                        }

                        // Sensor 2 Displacement Vector
                        float q2 = (w2 + x2 + y2 + z2);
                        float theta2 = (float) Math.acos(2 * (Math.pow(q2, 2)) - 1);

                        if (Float.isNaN(theta2)){
                            theta2=0;
                        }

                        else {
                            theta2 = (float) Math.acos(2 * (Math.pow(q2, 2)) - 1);
                        }

                        double thetaDeg2 = (double) (theta2 * (180 / Math.PI));

                        if((angle3.get().y()-Yi2) < 0.01 ) {
                            thetaDeg2=thetaDeg2*(-1);
                        }
                        else {
                            thetaDeg2 = (double) (theta2 * (180 / Math.PI));
                        }

                        // Displacement Between Sensor 1 and 2
                        int thetaFloat = (int) abs(thetaDeg - thetaDeg2);




                        SensorFinalX = abs(angle1.get().x() - angle3.get().x());
                        SensorFinalY = abs(angle1.get().y() - angle3.get().y());
                        SensorFinalZ = abs(angle1.get().z() - angle3.get().z());


                        TextView kneeAngleDiff = (TextView) getActivity().findViewById(R.id.kneeAngleDiff);
                        String YawFinalX = String.format("%.2f", SensorFinalX);
                        String YawFinalY = String.format("%.2f", SensorFinalY);
                        String YawFinalZ = String.format("%.2f", SensorFinalZ);

                        kneeAngleDiff.setText("Knee Angle:"  + "  " +  thetaFloat+ (char) 0x00B0);


                        TextView kneeAngle1 = (TextView) getActivity().findViewById(R.id.kneeAngle);
                        String Yaw1X = String.format("%.2f", angle1.get().x());
                        String Yaw1Y = String.format("%.2f", angle1.get().y());
                        String Yaw1Z = String.format("%.2f", angle1.get().z());
                        kneeAngle1.setText("Yaw:  " + Yaw1X + "  " + Yaw1Y + "  " + Yaw1Z + (char) 0x00B0);

                        TextView kneeAngle2 = (TextView) getActivity().findViewById(R.id.kneeAngle2);
                        String Yaw2X = String.format("%.2f", angle3.get().x());
                        String Yaw2Y = String.format("%.2f", angle3.get().y());
                        String Yaw2Z = String.format("%.2f", angle3.get().z());
                        kneeAngle2.setText("Yaw:  " + Yaw2X + "  " + Yaw2Y + "  " + Yaw2Z + (char) 0x00B0);


                        TextView repCount = (TextView) getActivity().findViewById(R.id.repCount);
                        String reps = String.valueOf(count);
                        repCount.setText("Completed Reps " + count);

                        final Quaternion angles = data.value(Quaternion.class);
                        // Sample number in CSV file
                        chartData.addXValue(String.format(Locale.US, "%.2f", sampleCount * SAMPLING_PERIOD));

                        //Values that Appear as Columns in the CSV file
                        chartData.addEntry(new Entry(thetaFloat, sampleCount), 0);
                        chartData.addEntry(new Entry(abs(dummy), sampleCount), 1);
                        chartData.addEntry(new Entry(abs(dummy), sampleCount), 2);
                        chartData.addEntry(new Entry(abs(0), sampleCount), 3);


                        // Gyroscope Used to Detect Calibration State along with User requirement to return to Position close to starting point.
                        long interval2 = 1000;
                        int movTolerance = 2;
                        int calAngleTol = abs(15);
                        if ( thetaFloat< calAngleTol && (abs(gyroVal1.get().x()) <movTolerance) && (abs(gyroVal1.get().y()) <movTolerance) && (abs(gyroVal1.get().z()) <movTolerance) && (abs(gyroVal2.get().x()) <movTolerance) && (abs(gyroVal2.get().y()) <movTolerance) && (abs(gyroVal2.get().z()) <movTolerance)) {
                            // only calibrates after a rep by mkaing sure rep counter > and since we set temp=-1 and count starts at 0, first rep makes count=1, we check that temp== 2 less then count.
                            if ((lastCal + interval2 < time) && temp==count-2 && count>0) {

                                // grabbing the intial position of sensor 1
                                float CalX = angle1.get().x();
                                Xi1 = CalX;
                                float CalY = angle1.get().y();
                                Yi1 = CalY;
                                float CalZ = angle1.get().z();
                                Zi1 = CalZ;
                                float CalW = angle1.get().w();
                                Wi1 = CalW;

                                // grabbing the intial position of sensor 2
                                float CalX2 = angle3.get().x();
                                Xi2 = CalX2;
                                float CalY2 = angle3.get().y();
                                Yi2 = CalY2;
                                float CalZ2 = angle3.get().z();
                                Zi2 = CalZ2;
                                float CalW2 = angle3.get().w();
                                Wi2 = CalW2;

                                lastCal = time;
                                // dummy is used to watch for flexion / extension in the Desktop app
                                dummy++;
                                temp++;

                                // Beep at origin/Calibration (beginning of new rep)
                                beep = MediaPlayer.create(getActivity().getApplicationContext(), R.raw.repbeep);
                                beep.start();
                                lastBeep = time;
                                if (!beep.isPlaying()) {

                                    beep.reset();
                                    beep.stop();
                                    beep.release();
                                }

                            }
                        }

                        sampleCount++;
                        i++;
                        updateChart();



                        // Beep once the user has reached a position close enough to desired range. Count as a Rep. Prevent False Beeps
                        // By requiring the user to return to Origin (Calibration State) prior to allowing another Rep Count.
                        float a = 90;
                        double acceptedDiff = 5;
                        //long interval sets the timer value in milliseconds
                        // accepted diff means and angle within +- that value of float a triggers a rep and beep
                        long interval = 1500;

                        if ((lastBeep + interval < time && (count==temp+1))) {
                            if (abs(a - thetaFloat) < acceptedDiff) {

                                beep = MediaPlayer.create(getActivity().getApplicationContext(), R.raw.repbeep);
                                beep.start();
                                lastBeep = time;

                                count = count + 1;
                                dummy++;


                                if (!beep.isPlaying()) {

                                    beep.reset();
                                    beep.stop();
                                    beep.release();
                                }

                            }
                        }

                    }
                });



            }

        })).continueWith(task -> {
            streamRoute = task.getResult();
            sensorFusion.quaternion().start();
            sensorFusion.start();

            /*StartMath();*/

            return 0;
        });
        return 0;
    }

    // Startmath Function has now been implicitly added into Sensor1(); Sensor 1 controls all calculations between
    // the two sensors








    @Override
    // CLEAN FUNTION STOPS THE SENSOR FROM STREAMING.
    protected void clean() {
        sensorFusion.stop();
        sensorFusion2.stop();
    }

    // saves the CSV file
    @Override
    protected String saveData() {
        final String CSV_HEADER = (srcIndex == 0 ? String.format("time,Angle,x,y,z%n") : String.format("time,heading,pitch,roll,yaw%n"));
        String filename = String.format(Locale.US, "%s_%tY%<tm%<td-%<tH%<tM%<tS%<tL.csv", getContext().getString(sensorResId), Calendar.getInstance());

        try {
            FileOutputStream fos = getActivity().openFileOutput(filename, Context.MODE_PRIVATE);
            fos.write(CSV_HEADER.getBytes());

            LineData data = chart.getLineData();
            LineDataSet x0DataSet = data.getDataSetByIndex(0), x1DataSet = data.getDataSetByIndex(1),
                    x2DataSet = data.getDataSetByIndex(2), x3DataSet = data.getDataSetByIndex(3);
            for (int i = 0; i < data.getXValCount(); i++) {
                fos.write(String.format(Locale.US, "%.3f,%.3f,%.3f,%.3f,%.3f%n", (i * SAMPLING_PERIOD),
                        x0DataSet.getEntryForXIndex(i).getVal(), x1DataSet.getEntryForXIndex(i).getVal(),
                        x2DataSet.getEntryForXIndex(i).getVal(), x3DataSet.getEntryForXIndex(i).getVal()).getBytes());
            }
            fos.close();
            return filename;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    //Resets the Chart/ CSV data entry.
    protected void resetData(boolean clearData) {
        if (clearData) {
            sampleCount = 0;
            count=0;


            // no longer using chart

            // chartXValues.clear();
            // x0.clear();
            // x1.clear();
            // x2.clear();
            // x3.clear();
        }

        // CSV file Column Titles
        ArrayList<LineDataSet> spinAxisData= new ArrayList<>();
        spinAxisData.add(new LineDataSet(x0, srcIndex == 0 ? "w" : "heading"));
        spinAxisData.get(0).setColor(Color.BLACK);
        spinAxisData.get(0).setDrawCircles(false);

        spinAxisData.add(new LineDataSet(x1, srcIndex == 0 ? "x" : "pitch"));
        spinAxisData.get(1).setColor(Color.RED);
        spinAxisData.get(1).setDrawCircles(false);

        spinAxisData.add(new LineDataSet(x2, srcIndex == 0 ? "y" : "roll"));
        spinAxisData.get(2).setColor(Color.GREEN);
        spinAxisData.get(2).setDrawCircles(false);

        spinAxisData.add(new LineDataSet(x3, srcIndex == 0 ? "z" : "yaw"));
        spinAxisData.get(3).setColor(Color.BLUE);
        spinAxisData.get(3).setDrawCircles(false);

        LineData data= new LineData(chartXValues);
        for(LineDataSet set: spinAxisData) {
            data.addDataSet(set);
        }
        data.setDrawValues(false);
        chart.setData(data);
    }

    @Override
    //Ensures the board is ready
    protected void boardReady() throws UnsupportedModuleException {
        sensorFusion = mwBoard.getModuleOrThrow(SensorFusionBosch.class);
        sensorFusion2 = mwBoard2.getModuleOrThrow(SensorFusionBosch.class);
    }



    @Override
    protected void fillHelpOptionAdapter(HelpOptionAdapter adapter) {
        adapter.add(new HelpOption(R.string.config_name_sensor_fusion_data, R.string.config_desc_sensor_fusion_data));
    }
}
