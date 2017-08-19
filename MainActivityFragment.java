package edu.umich.eyesnap;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.view.MotionEventCompat;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.parse.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

import static android.R.attr.bitmap;
import static android.R.attr.start;
import static android.view.View.*;


/**
 * Fragment which contains main logic for this application. Includes on click loop which changes on screen elements with clicks registered on various parts of the screen.
 */
public class MainActivityFragment extends Fragment implements
        OnClickListener, OnTouchListener, SurfaceHolder.Callback{

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        //initalizes connection to parse with specified keys like appID, clientKey, server and build
        Parse.initialize(new Parse.Configuration.Builder(getActivity().getApplicationContext())
                .applicationId(getString(R.string.parse_app_id))
                .clientKey(getString(R.string.parse_client_key))
                .server("https://parseapi.back4app.com")
                .build()
        );

        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    //initializes log tag by getting this class' name
    private final String LOG_TAG = this.getClass().getName();


    // declaring or initalizing variables for showing images
    private static enum State {CAMERA_MODE, SAVING_LEFT_PUPIL, SAVING_RIGHT_PUPIL};

    public TouchImageView display;
    Drawable[] layers;
    PointF[] centerCoordinates;
    boolean canUseCamera = false;
    boolean hasTouchedPupil = false;
    boolean hasLeft = false;
    boolean hasRight = false;
    boolean leftDisabled = true;
    boolean rightDisabled = true;
    boolean textEnabled = true;
    SurfaceView cameraPreview;
    SurfaceHolder surfaceHolder;
    Camera camera;
    Camera.PictureCallback imageTakenCallback;
    int pupilSize = 36;
    boolean smallPupil;
    boolean hasPaused = false;
    EditText mEdit;
    String diagnosis;
    boolean hasText = false;
    boolean savePicOpen = true;
    private String picture_id = "";

    private State state = State.CAMERA_MODE;

    public MainActivityFragment() {
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize views and sets the listener for the clicks as this
        view.findViewById(R.id.es_capture).setOnClickListener(this);
        view.findViewById(R.id.es_save).setOnClickListener(this);
        view.findViewById(R.id.es_back).setOnClickListener(this);
        view.findViewById(R.id.es_plus).setOnClickListener(this);
        view.findViewById(R.id.es_minus).setOnClickListener(this);
        view.findViewById(R.id.es_clear).setOnClickListener(this);
        view.findViewById(R.id.es_left).setOnClickListener(this);
        view.findViewById(R.id.es_right).setOnClickListener(this);
        view.findViewById(R.id.es_savePic).setOnClickListener(this);
        //view.findViewById(R.id.es_exit).setOnClickListener(this);
        view.findViewById(R.id.text).setOnClickListener(this);
        //view.findViewById(R.id.es_exit).setVisibility(VISIBLE);
        view.findViewById(R.id.es_diagnosis).setOnClickListener(this);
        //view.findViewById(R.id.es_import).setOnClickListener(this);
        //view.findViewById(R.id.es_disable).setOnClickListener(this);
        view.findViewById(R.id.es_saveLeft).setOnClickListener(this);
        view.findViewById(R.id.es_saveRight).setOnClickListener(this);
        //view.findViewById(R.id.es_pause).setOnClickListener(this);

        //initalizes camera preview
        cameraPreview = (SurfaceView) view.findViewById(R.id.es_camera_preview);
        surfaceHolder = cameraPreview.getHolder();
        surfaceHolder.addCallback((SurfaceHolder.Callback) this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        //sets area to show picture
        display = (TouchImageView) view.findViewById(R.id.es_show_picture);
        display.setOnTouchListener(this);
        centerCoordinates = new PointF[2];
        start_camera_mode();

        Toast toast= Toast.makeText((Context)getActivity().getApplicationContext(),
                "Please touch the camera button!", Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER_HORIZONTAL, 0, 0);
        toast.show();

        //should store raw image on sd card of device, initalizes bitmap layers to be drawn
        imageTakenCallback = new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                BitmapFactory.Options scalingOptions = new BitmapFactory.Options();
                scalingOptions.inSampleSize = camera.getParameters().getPictureSize().width / display.getMeasuredWidth();
                Bitmap raw = BitmapFactory.decodeByteArray(data, 0, data.length, scalingOptions);
                Matrix mat = new Matrix();
                mat.postRotate((float)getRotationAngle(getActivity(), 0));
                Bitmap bmp = Bitmap.createBitmap(raw, 0, 0, raw.getWidth(), raw.getHeight(), mat, true);
                try {
                    FileOutputStream outputStream = new FileOutputStream(String.format("/sdcard/raw_%d.jpg", System.currentTimeMillis()));
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                    outputStream.close();
                    Log.d(LOG_TAG, "onPictureTaken - wrote bytes: " + data.length);
                } catch (FileNotFoundException fnfe ) {
                    fnfe.printStackTrace();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
                Log.d(LOG_TAG, "onPictureTaken - png");
                layers[0] = new BitmapDrawable(getResources(), bmp);
                layers[1] = new BitmapDrawable(getResources(), bmp);
                display.setImageBitmap(bmp);
                stop_camera();
            }
        };
    }

    //main on click loop which registers clicks from the user on particular buttons
    @Override
    public void onClick(View view) {
        switch(view.getId()) {
            case R.id.es_capture: {
                capture_image();
                start_pupil_saving_mode();
                break;
            }
            case R.id.es_savePic: // when the "save picture" button is pressed
            {
                savePicOpen = false;

                //saves raw and processed pics when save pic button is pressed and disables further pressing of button
                getView().findViewById(R.id.es_savePic).setEnabled(false);
                Toast toast= Toast.makeText((Context)getActivity().getApplicationContext(),
                        "Picture saved", Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.CENTER_HORIZONTAL, 0, 0);
                toast.show();

                Log.d(LOG_TAG, "Save got here before.");

                //saves picture by converting drawable to bitmap and storing raw image along with processed image
                Log.d(LOG_TAG, "Save got here.");
                Bitmap raw = drawableToBitmap(layers[0]);
                Bitmap processed = drawableToBitmap(new LayerDrawable(layers));

                AsyncTask<Bitmap, Void, Void> asyncLoad = new AsyncTask<Bitmap, Void, Void>()
                {
                    @Override
                    protected Void doInBackground(Bitmap... params)
                    {
                        ParseFile store_raw = new ParseFile("raw.png", BitmapToByteArray(params[0]));
                        try {
                            store_raw.save();
                        } catch (ParseException pe) {
                            Log.e(LOG_TAG, pe.toString());
                        }
                        ParseFile store_marked = new ParseFile("marked.png", BitmapToByteArray(params[1]));
                        try {
                            store_marked.save();
                        } catch (ParseException pe) {
                            Log.e(LOG_TAG, pe.toString());
                        }
                        // send object complete with pictures to database
                        final ParseObject eyeSnapStorage = new ParseObject("EyeSnap_2016");
                        eyeSnapStorage.put("pic_raw", store_raw);
                        eyeSnapStorage.put("pic_marked", store_marked);
                        if(hasLeft) eyeSnapStorage.put("user_left_center_x", centerCoordinates[0].x);
                        if(hasLeft) eyeSnapStorage.put("user_left_center_y", centerCoordinates[0].y);
                        if(hasRight) eyeSnapStorage.put("user_right_center_x", centerCoordinates[1].x);
                        if(hasRight) eyeSnapStorage.put("user_right_center_y", centerCoordinates[1].y);
                        if(hasText) eyeSnapStorage.put("diagnosis",diagnosis);

                        Log.d(LOG_TAG,"test message for uploading");
                        eyeSnapStorage.saveInBackground(new SaveCallback() {
                            @Override
                            public void done(ParseException e) {
                                Log.d(LOG_TAG, "return fn");
                                Log.d(LOG_TAG, "name = " + eyeSnapStorage.getString("name") + " " + eyeSnapStorage.getObjectId());
                            }
                        });



                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void result)
                    {
                        super.onPostExecute(result);
                    }
                };

                asyncLoad.execute(raw, processed);

                try {
                    FileOutputStream outputStream = new FileOutputStream(String.format("/sdcard/marked_%d.jpg", System.currentTimeMillis()));
                    processed.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                    outputStream.close();
                    Log.d(LOG_TAG, "saved processed image");
                } catch (FileNotFoundException fnfe ) {
                    fnfe.printStackTrace();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }

                Log.i(LOG_TAG, "Sending image and data to server.");
                if(hasLeft) Log.i(LOG_TAG, "Left_pupil: " + centerCoordinates[0].toString());
                if(hasRight) Log.i(LOG_TAG, "Right_pupil: " + centerCoordinates[1].toString());

                break;
            }
            case R.id.es_diagnosis: { // when the diagnosis button is pressed
                //recognizes click on diagnosis button which instructs opening of empty text box
                Log.d(LOG_TAG, "got into diagnosis section of loop");

                display.setVisibility(VISIBLE);
                getView().findViewById(R.id.text).setVisibility(VISIBLE);
                //getView().findViewById(R.id.es_disable).setVisibility(GONE);
                cameraPreview.setVisibility(INVISIBLE);
                Log.d(LOG_TAG, "got into diagnosis section of loop");
                //if((textEnabled)==(false)) view.findViewById(R.id.text).setEnabled(true);
//                String s1 = getView().findViewById(R.id.text).toString();
//                Log.d(LOG_TAG,"got after s1");

                break;
            }
            case R.id.text:{ // when the text box is clicked
                //registers clicks from the user on the text box, supposed to get executed when user
                //hits enter/send on the android keyboard
                EditText text1 = (EditText)getView().findViewById(R.id.text);
                diagnosis = text1.getText().toString();
                hasText = true;
                Log.d(LOG_TAG,diagnosis);

                final ParseObject eyeSnapStorage = new ParseObject("EyeSnap_2016");

                if(hasLeft) eyeSnapStorage.put("user_left_center_x", centerCoordinates[0].x);
                if(hasLeft) eyeSnapStorage.put("user_left_center_y", centerCoordinates[0].y);
                if(hasRight) eyeSnapStorage.put("user_right_center_x", centerCoordinates[1].x);
                if(hasRight) eyeSnapStorage.put("user_right_center_y", centerCoordinates[1].y);
                if(hasText) eyeSnapStorage.put("diagnosis",diagnosis);

                Log.d(LOG_TAG,"test message for uploading");
                eyeSnapStorage.saveInBackground(new SaveCallback() {
                    @Override
                    public void done(ParseException e) {
                        Log.d(LOG_TAG, "return fn");
                        Log.d(LOG_TAG, "name = " + eyeSnapStorage.getString("name") + " " + eyeSnapStorage.getObjectId());
                    }
                });

                break;
            }
            case R.id.es_left:  { // when save left pupil button is pressed
                leftDisabled = false;
                //getView().findViewById(R.id.es_save2).setEnabled(true);
                getView().findViewById(R.id.es_plus).setEnabled(true);
                getView().findViewById(R.id.es_minus).setEnabled(true);
                getView().findViewById(R.id.es_clear).setEnabled(true);
                getView().findViewById(R.id.es_left).setEnabled(false);
                getView().findViewById(R.id.es_saveLeft).setEnabled(true);

                //disable right to prevent crashing
                getView().findViewById(R.id.es_right).setEnabled(false);
                getView().findViewById(R.id.es_savePic).setEnabled(false);

                Toast toast= Toast.makeText((Context)getActivity().getApplicationContext(),
                        "Please touch screen!", Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.CENTER_HORIZONTAL, 0, 0);
                toast.show();

                hasLeft = true;

                //if(hasRight)getView().findViewById(R.id.es_save2).setEnabled(false);

                state = State.SAVING_LEFT_PUPIL;
                break;
            }
            case R.id.es_right: { // when save right pupil button is pressed
                leftDisabled = false;
                //getView().findViewById(R.id.es_save2).setEnabled(true);
                getView().findViewById(R.id.es_plus).setEnabled(true);
                getView().findViewById(R.id.es_minus).setEnabled(true);
                getView().findViewById(R.id.es_clear).setEnabled(true);
                getView().findViewById(R.id.es_right).setEnabled(false);
                getView().findViewById(R.id.es_saveRight).setEnabled(true);
                getView().findViewById(R.id.es_left).setEnabled(false);
                getView().findViewById(R.id.es_savePic).setEnabled(false);

                Toast toast= Toast.makeText((Context)getActivity().getApplicationContext(),
                        "Please touch screen!", Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.CENTER_HORIZONTAL, 0, 0);
                toast.show();

                hasRight = true;

                //if(hasLeft)getView().findViewById(R.id.es_save2).setEnabled(false);

                state = State.SAVING_RIGHT_PUPIL;
                break;

            }
            case R.id.es_back: { // when the back button is pressed - go to picture mode
                Log.i(LOG_TAG, "State: " + state);
                savePicOpen = true;

                EditText text1 = (EditText)getView().findViewById(R.id.text);
                text1.setText("");

                //view.findViewById(R.id.es_savePic).setEnabled(true);
                //back = 1;

                display.resetZoom();

                switch (state) {
                    case SAVING_LEFT_PUPIL: {
                        start_camera_mode();
                        hasLeft = false;
                        hasRight = false;
                        hasTouchedPupil = false;
                        break;
                    }
                    case SAVING_RIGHT_PUPIL: {

                        start_camera_mode();
                        hasLeft = false;
                        hasRight = false;
                        hasTouchedPupil = false;
                        break;

                    }
                    default: {
                        Log.e(LOG_TAG, "Back button has reached uncharted territory.");
                        break;
                    }
                }
                break;
            }
            case R.id.es_plus: { //changes pupil radius
                //increments the pupil radius when plus button is clicked
                Toast toast= Toast.makeText((Context)getActivity().getApplicationContext(),
                        "Pupil radius increased by 3 pixels!", Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.CENTER_HORIZONTAL, 0, 0);
                toast.show();

                pupilSize = pupilSize+3;

                layers[1] = layers[0];
                LayerDrawable ldr1 = new LayerDrawable(layers);
                display.setImageDrawable(ldr1);
                Drawable image = display.getDrawable();
                int imageWidth = image.getIntrinsicWidth(), imageHeight = image.getIntrinsicHeight();
                Bitmap mark = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(mark);
                Paint paint = new Paint();
                paint.setStyle(Paint.Style.STROKE);
                paint.setAntiAlias(true);
                paint.setStrokeWidth((float) 3);
                paint.setARGB(251, 251, 200, 20);
                // Translate the touch coordinates to image coordinates:
                if(hasRight) canvas.drawCircle(centerCoordinates[1].x, centerCoordinates[1].y, pupilSize, paint);
                if(hasLeft) canvas.drawCircle(centerCoordinates[0].x, centerCoordinates[0].y, pupilSize, paint);
                BitmapDrawable bitmapDrawable = new BitmapDrawable(getResources(), mark);
                layers[1] = bitmapDrawable;
                LayerDrawable ldr = new LayerDrawable(layers);
                display.setImageDrawable(ldr);

                return;
            }
            case R.id.es_minus: { // changes pupil radius

                Toast toast= Toast.makeText((Context)getActivity().getApplicationContext(),
                        "Pupil radius decreased by 3 pixels!", Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.CENTER_HORIZONTAL, 0, 0);
                toast.show();

                pupilSize = pupilSize-3;

                layers[1] = layers[0];
                LayerDrawable ldr1 = new LayerDrawable(layers);
                display.setImageDrawable(ldr1);
                Drawable image = display.getDrawable();
                int imageWidth = image.getIntrinsicWidth(), imageHeight = image.getIntrinsicHeight();
                Bitmap mark = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(mark);
                Paint paint = new Paint();
                paint.setStyle(Paint.Style.STROKE);
                paint.setAntiAlias(true);
                paint.setStrokeWidth((float) 3);
                paint.setARGB(251, 251, 200, 20);
                // Translate the touch coordinates to image coordinates:
                if(hasRight) canvas.drawCircle(centerCoordinates[1].x, centerCoordinates[1].y, pupilSize, paint);
                if(hasLeft) canvas.drawCircle(centerCoordinates[0].x, centerCoordinates[0].y, pupilSize, paint);
                BitmapDrawable bitmapDrawable = new BitmapDrawable(getResources(), mark);
                layers[1] = bitmapDrawable;
                LayerDrawable ldr = new LayerDrawable(layers);
                display.setImageDrawable(ldr);

                return;
            }
//            case R.id.es_exit: {
//                System.exit(0);
//                return;
//            }
            case R.id.es_saveLeft: {
                    //saves left pupil when button is pressed

                if(!hasTouchedPupil) return;

                if(savePicOpen) getView().findViewById(R.id.es_savePic).setEnabled(true);
                getView().findViewById(R.id.es_saveLeft).setEnabled(false);
                getView().findViewById(R.id.es_plus).setEnabled(false);
                getView().findViewById(R.id.es_minus).setEnabled(false);

                getView().findViewById(R.id.es_left).setEnabled(true);
                getView().findViewById(R.id.es_right).setEnabled(true);
                getView().findViewById(R.id.es_plus).setEnabled(false);
                getView().findViewById(R.id.es_minus).setEnabled(false);

                //if(!hasTouchedPupil) return;
                leftDisabled = true;
                hasLeft = true;

                if(hasRight) {
                    //getView().findViewById(R.id.es_save2).setEnabled(true);
                }

                if(hasLeft && hasTouchedPupil) {
                    Toast toast = Toast.makeText((Context) getActivity().getApplicationContext(),
                            "Left pupil saved", Toast.LENGTH_SHORT);
                    toast.setGravity(Gravity.CENTER_HORIZONTAL, 0, 0);
                    toast.show();
                }




                Log.d(LOG_TAG, "Save got here before.");

                        Log.d(LOG_TAG, "Save got here.");
                        Bitmap raw = drawableToBitmap(layers[0]);
                        Bitmap processed = drawableToBitmap(new LayerDrawable(layers));

                        AsyncTask<Bitmap, Void, Void> asyncLoad = new AsyncTask<Bitmap, Void, Void>()
                        {
                            @Override
                            protected Void doInBackground(Bitmap... params)
                            {
                                ParseFile store_raw = new ParseFile("raw.png", BitmapToByteArray(params[0]));
                                try {
                                    store_raw.save();
                                } catch (ParseException pe) {
                                    Log.e(LOG_TAG, pe.toString());
                                }
                                ParseFile store_marked = new ParseFile("marked.png", BitmapToByteArray(params[1]));
                                try {
                                    store_marked.save();
                                } catch (ParseException pe) {
                                    Log.e(LOG_TAG, pe.toString());
                                }
                                // send object complete with pictures to database
                                final ParseObject eyeSnapStorage = new ParseObject("EyeSnap_2016");
                                eyeSnapStorage.put("pic_raw", store_raw);
                                eyeSnapStorage.put("pic_marked", store_marked);
                                if(hasLeft) eyeSnapStorage.put("user_left_center_x", centerCoordinates[0].x);
                                if(hasLeft) eyeSnapStorage.put("user_left_center_y", centerCoordinates[0].y);
                                if(hasRight) eyeSnapStorage.put("user_right_center_x", centerCoordinates[1].x);
                                if(hasRight) eyeSnapStorage.put("user_right_center_y", centerCoordinates[1].y);
                                if(hasText) eyeSnapStorage.put("diagnosis",diagnosis);

                                Log.d(LOG_TAG,"test message for uploading");
                                eyeSnapStorage.saveInBackground(new SaveCallback() {
                                    @Override
                                    public void done(ParseException e) {
                                        Log.d(LOG_TAG, "return fn");
                                        Log.d(LOG_TAG, "name = " + eyeSnapStorage.getString("name") + " " + eyeSnapStorage.getObjectId());
                                    }
                                });



                                return null;
                            }

                            @Override
                            protected void onPostExecute(Void result)
                            {
                                super.onPostExecute(result);
                            }
                        };

                        asyncLoad.execute(raw, processed);

                        try {
                            FileOutputStream outputStream = new FileOutputStream(String.format("/sdcard/marked_%d.jpg", System.currentTimeMillis()));
                            processed.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                            outputStream.close();
                            Log.d(LOG_TAG, "saved processed image");
                        } catch (FileNotFoundException fnfe ) {
                            fnfe.printStackTrace();
                        } catch (IOException ioe) {
                            ioe.printStackTrace();
                        }

                        Log.i(LOG_TAG, "Sending image and data to server.");
                        if(hasLeft) Log.i(LOG_TAG, "Left_pupil: " + centerCoordinates[0].toString());
                        if(hasRight) Log.i(LOG_TAG, "Right_pupil: " + centerCoordinates[1].toString());
                        //start_camera_mode();
                        //save_button.setText("Save first pupil");
                        break;


            }
            case R.id.es_saveRight: {
                //saves right pupil when pressed
                if(!hasTouchedPupil) return;

                if(savePicOpen) getView().findViewById(R.id.es_savePic).setEnabled(true);
                getView().findViewById(R.id.es_saveRight).setEnabled(false);
                getView().findViewById(R.id.es_plus).setEnabled(false);
                getView().findViewById(R.id.es_minus).setEnabled(false);




                leftDisabled = true;
                hasRight = true;

                if(hasLeft) {
                    //getView().findViewById(R.id.es_save2).setEnabled(true);
                }

                if(hasRight && hasTouchedPupil) {
                    Toast toast = Toast.makeText((Context) getActivity().getApplicationContext(),
                            "Right pupil saved", Toast.LENGTH_SHORT);
                    toast.setGravity(Gravity.CENTER_HORIZONTAL, 0, 0);
                    toast.show();
                }

                    getView().findViewById(R.id.es_left).setEnabled(true);
                    getView().findViewById(R.id.es_right).setEnabled(true);
                    getView().findViewById(R.id.es_plus).setEnabled(false);
                    getView().findViewById(R.id.es_minus).setEnabled(false);

                    //return;

                //Button save_button = (Button) getView().findViewById(R.id.es_save);


                        Bitmap raw = drawableToBitmap(layers[0]);
                        Bitmap processed = drawableToBitmap(new LayerDrawable(layers));

                        AsyncTask<Bitmap, Void, Void> asyncLoad = new AsyncTask<Bitmap, Void, Void>()
                        {
                            @Override
                            protected Void doInBackground(Bitmap... params)
                            {
                                ParseFile store_raw = new ParseFile("raw.png", BitmapToByteArray(params[0]));
                                try {
                                    store_raw.save();
                                } catch (ParseException pe) {
                                    Log.e(LOG_TAG, pe.toString());
                                }
                                ParseFile store_marked = new ParseFile("marked.png", BitmapToByteArray(params[1]));
                                try {
                                    store_marked.save();
                                } catch (ParseException pe) {
                                    Log.e(LOG_TAG, pe.toString());
                                }
                                // send object complete with pictures to database
                                final ParseObject eyeSnapStorage = new ParseObject("EyeSnap_2016");
                                eyeSnapStorage.put("pic_raw", store_raw);
                                eyeSnapStorage.put("pic_marked", store_marked);
                                if(hasLeft) eyeSnapStorage.put("user_left_center_x", centerCoordinates[0].x);
                                if(hasLeft) eyeSnapStorage.put("user_left_center_y", centerCoordinates[0].y);
                                if(hasRight) eyeSnapStorage.put("user_right_center_x", centerCoordinates[1].x);
                                if(hasRight) eyeSnapStorage.put("user_right_center_y", centerCoordinates[1].y);
                                if(hasText) eyeSnapStorage.put("diagnosis",diagnosis);

                                Log.d(LOG_TAG,"test message for uploading");
                                eyeSnapStorage.saveInBackground(new SaveCallback() {
                                    @Override
                                    public void done(ParseException e) {
                                        Log.d(LOG_TAG, "return fn");
                                        Log.d(LOG_TAG, "name = " + eyeSnapStorage.getString("name") + " " + eyeSnapStorage.getObjectId());
                                    }
                                });



                                return null;
                            }

                            @Override
                            protected void onPostExecute(Void result)
                            {
                                super.onPostExecute(result);
                            }
                        };

                        asyncLoad.execute(raw, processed);

                        try {
                            FileOutputStream outputStream = new FileOutputStream(String.format("/sdcard/marked_%d.jpg", System.currentTimeMillis()));
                            processed.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                            outputStream.close();
                            Log.d(LOG_TAG, "saved processed image");
                        } catch (FileNotFoundException fnfe ) {
                            fnfe.printStackTrace();
                        } catch (IOException ioe) {
                            ioe.printStackTrace();
                        }

                        Log.i(LOG_TAG, "Sending image and data to server.");
                        if(hasLeft) Log.i(LOG_TAG, "Left_pupil: " + centerCoordinates[0].toString());
                        if(hasRight) Log.i(LOG_TAG, "Right_pupil: " + centerCoordinates[1].toString());
                        //start_camera_mode();
                        //save_button.setText("Save first pupil");
                        break;


            }

//
            case R.id.es_clear: {
                //clears both pupiis when pressed
//                EditText text1 = (EditText)getView().findViewById(R.id.text);
//                text1.setText("");

                switch (state) {
                    default: {
                        layers[1] = layers[0];
                        LayerDrawable ldr = new LayerDrawable(layers);
                        display.setImageDrawable(ldr);
                        start_pupil_saving_mode();
                        hasLeft = false;
                        hasRight = false;
                        leftDisabled = true;
                        rightDisabled = true;
                        hasTouchedPupil =false;
                        break;
                    }
                }
                
            }
        }
        hasTouchedPupil = false;
    }
    //declares values for x coordinate on which started pushing down
    private float mDownX;
    private float mDownY;
    //initializes value for threshold of pixels beyond which click is considered movement
    private final float SCROLL_THRESHOLD = 100;
    private boolean isOnClick;

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if(state != State.CAMERA_MODE
                && state != State.SAVING_LEFT_PUPIL
                && state != State.SAVING_RIGHT_PUPIL) {
            Log.e(LOG_TAG, "Saving state has reached uncharted territory.");
            return false;
        }
        if(hasPaused) return true;
        if(!(hasLeft || hasRight)) return true;
        if(leftDisabled && rightDisabled) return false;
//        if(!hasTouchedPupil && (hasLeft||hasRight)) return false;

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                //records x and y values from when pressure is applied to android keyboard
                mDownX = event.getX();
                mDownY = event.getY();
                isOnClick = true;
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (isOnClick) {
                    Log.i(LOG_TAG, "onClick ");
                    layers[1] = layers[0];
                    LayerDrawable ldr1 = new LayerDrawable(layers);
                    display.setImageDrawable(ldr1);

                    // Prepare necessary variables for drawing the mark
                    Drawable image = display.getDrawable();
                    int imageWidth = image.getIntrinsicWidth(), imageHeight = image.getIntrinsicHeight();
                    Bitmap mark = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(mark);
                    Paint paint = new Paint();
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setAntiAlias(true);
                    paint.setStrokeWidth((float) 3);
                    paint.setARGB(251, 251, 200, 20);

                    // Translate the touch coordinates to image coordinates:
                    PointF bitmapPoint = display.transformCoordTouchToBitmap(event.getX(), event.getY(), true);

                    switch (state) {
                        case SAVING_LEFT_PUPIL: {
                            centerCoordinates[0] = bitmapPoint;
                            if(hasRight) canvas.drawCircle(centerCoordinates[1].x, centerCoordinates[1].y, pupilSize, paint);
                            break;
                        }
                        case SAVING_RIGHT_PUPIL: {
                            centerCoordinates[1] = bitmapPoint;
                            if(hasLeft) canvas.drawCircle(centerCoordinates[0].x, centerCoordinates[0].y, pupilSize, paint);
                        }
                    }
                    canvas.drawCircle(bitmapPoint.x, bitmapPoint.y, pupilSize, paint);

                    BitmapDrawable bitmapDrawable = new BitmapDrawable(getResources(), mark);
                    layers[1] = bitmapDrawable;
                    LayerDrawable ldr = new LayerDrawable(layers);
                    display.setImageDrawable(ldr);
                    hasTouchedPupil = true;


                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (isOnClick && (Math.abs(mDownX - event.getX()) > SCROLL_THRESHOLD || Math.abs(mDownY - event.getY()) > SCROLL_THRESHOLD)) {
                    Log.i(LOG_TAG, "movement detected");
                    isOnClick = false;
                    return false;
                }
                break;
            default:
                break;
        }



        return true;
    }

    public static int getRotationAngle(Activity activity, int cameraId) {
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        Camera.getCameraInfo((int) cameraId, (Camera.CameraInfo) cameraInfo);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: {
                degrees = 0;
                break;
            }
            case Surface.ROTATION_90: {
                degrees = 90;
                break;
            }
            case Surface.ROTATION_180: {
                degrees = 180;
                break;
            }
            case Surface.ROTATION_270: {
                degrees = 270;
            }
        }
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (360 - (cameraInfo.orientation + degrees) % 360) % 360;
        }
        return (cameraInfo.orientation - degrees + 360) % 360;
    }

    public static Bitmap rotate(Bitmap bitmap, int degrees)
    {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        Matrix matrix = new Matrix();
        matrix.postRotate((float)degrees);
        return Bitmap.createBitmap((Bitmap)bitmap, (int)0, (int)0, (int)width, (int)height, (Matrix)matrix, (boolean)true);
    }

    public void capture_image() {
//        if (!canUseCamera) {
//            return;
//        }
        Camera.Parameters parameters = this.camera.getParameters();
        parameters.setFlashMode("torch");
        this.camera.setParameters(parameters);
        this.camera.takePicture(null, null, this.imageTakenCallback);
        parameters.setFlashMode("off");
        this.camera.setParameters(parameters);
    }

    public void start_camera_mode() {
        layers = new Drawable[2];
        layers[0] = getResources().getDrawable(R.drawable.processing);
        layers[1] = getResources().getDrawable(R.drawable.processing);
        LayerDrawable layerDrawable = new LayerDrawable(layers);
        display.setImageDrawable((Drawable) layerDrawable);
        state = State.CAMERA_MODE;
        cameraPreview.setVisibility(VISIBLE);
        getView().findViewById(R.id.es_save).setVisibility(INVISIBLE);
        getView().findViewById(R.id.es_back).setVisibility(INVISIBLE);
        getView().findViewById(R.id.es_capture).setVisibility(VISIBLE);
        getView().findViewById(R.id.es_clear).setVisibility(INVISIBLE);
        getView().findViewById(R.id.es_plus).setVisibility(INVISIBLE);
        getView().findViewById(R.id.es_minus).setVisibility(INVISIBLE);
        getView().findViewById(R.id.es_left).setVisibility(INVISIBLE);
        getView().findViewById(R.id.es_right).setVisibility(INVISIBLE);
        getView().findViewById(R.id.es_savePic).setVisibility(INVISIBLE);
        //getView().findViewById(R.id.es_save2).setVisibility(INVISIBLE);
        getView().findViewById(R.id.text).setVisibility(GONE);
        getView().findViewById(R.id.es_diagnosis).setVisibility(INVISIBLE);
        display.setVisibility(INVISIBLE);
       // getView().findViewById(R.id.es_disable).setVisibility(INVISIBLE);
        //getView().findViewById(R.id.es_import).setVisibility(INVISIBLE);
        getView().findViewById(R.id.es_saveLeft).setVisibility(INVISIBLE);
        getView().findViewById(R.id.es_saveRight).setVisibility(INVISIBLE);
        getView().findViewById(R.id.es_pause).setVisibility(INVISIBLE);

        Toast toast= Toast.makeText((Context)getActivity().getApplicationContext(),
                "Please touch the camera button!", Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER_HORIZONTAL, 0, 0);
        toast.show();
    }

    public void start_pupil_saving_mode() {
        hasTouchedPupil = false;
        state = State.SAVING_LEFT_PUPIL;
        cameraPreview.setVisibility(INVISIBLE);
        getView().findViewById(R.id.es_save).setVisibility(INVISIBLE);
        getView().findViewById(R.id.es_back).setVisibility(VISIBLE);
        getView().findViewById(R.id.es_clear).setVisibility(VISIBLE);
        getView().findViewById(R.id.es_plus).setVisibility(VISIBLE);
        getView().findViewById(R.id.es_minus).setVisibility(VISIBLE);
        getView().findViewById(R.id.es_capture).setVisibility(INVISIBLE);
        getView().findViewById(R.id.es_left).setVisibility(VISIBLE);
        getView().findViewById(R.id.es_right).setVisibility(VISIBLE);
        getView().findViewById(R.id.es_savePic).setVisibility(VISIBLE);
        getView().findViewById(R.id.es_diagnosis).setVisibility(VISIBLE);//change to visible to recomment
        getView().findViewById(R.id.text).setVisibility(GONE);
        //getView().findViewById(R.id.es_import).setVisibility(GONE);
        //getView().findViewById(R.id.es_save2).setEnabled(false);
        getView().findViewById(R.id.es_plus).setEnabled(false);
        getView().findViewById(R.id.es_minus).setEnabled(false);
        getView().findViewById(R.id.es_clear).setEnabled(false);
        getView().findViewById(R.id.es_left).setEnabled(true);
        getView().findViewById(R.id.es_right).setEnabled(true);
        getView().findViewById(R.id.es_saveLeft).setVisibility(VISIBLE);
        getView().findViewById(R.id.es_saveRight).setVisibility(VISIBLE);
        getView().findViewById(R.id.es_saveLeft).setEnabled(false);
        getView().findViewById(R.id.es_saveRight).setEnabled(false);
        getView().findViewById(R.id.es_pause).setVisibility(INVISIBLE);
        getView().findViewById(R.id.es_savePic).setEnabled(true);

        //.setVisibility(VISIBLE);


        //getView().findViewById(R.id.es_back).setEnabled(false);

        display.setVisibility(VISIBLE);
    }

    public void start_camera() {
        try {
            camera = Camera.open();
        } catch (RuntimeException e) {
            Log.e(LOG_TAG, "camera initialization error: " + e);
            canUseCamera = false;
            return;
        }

        Camera.Parameters parameters = camera.getParameters();
        camera.setDisplayOrientation(getRotationAngle(this.getActivity(), 0));
        parameters.setFlashMode("off");
        camera.setParameters(parameters);

        try {
            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();
            canUseCamera = true;
        } catch (Exception e) {
            Log.e(LOG_TAG, "camera start preview error: " + e);
        }
    }

    public void stop_camera() {
        if (!canUseCamera) {//if camera is not working, do not try to stop it
            return;
        }
        canUseCamera = false;
        camera.stopPreview();
        camera.release();
    }

    public static Bitmap drawableToBitmap (Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable)drawable).getBitmap();
        }

        int width = drawable.getIntrinsicWidth();
        width = width > 0 ? width : 1;
        int height = drawable.getIntrinsicHeight();
        height = height > 0 ? height : 1;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }

    public static byte[] BitmapToByteArray (Bitmap bmp) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, stream);
        return stream.toByteArray();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        start_camera();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }
}