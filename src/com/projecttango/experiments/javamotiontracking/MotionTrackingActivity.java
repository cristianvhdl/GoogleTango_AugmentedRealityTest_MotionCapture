/*
 * Copyright 2014 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.projecttango.experiments.javamotiontracking;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.Tango.OnTangoUpdateListener;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;
import java.util.ArrayList;

/**
 * Main Activity class for the Motion Tracking API Sample. Handles the connection to the Tango
 * service and propagation of Tango pose data to OpenGL and Layout views. OpenGL rendering logic is
 * delegated to the {@link MTGLRenderer} class.
 */
public class MotionTrackingActivity extends Activity implements View.OnClickListener, SurfaceHolder.Callback  {

    private static final String TAG = MotionTrackingActivity.class.getSimpleName();
    private static final int SECS_TO_MILLISECS = 1000;
    private Tango mTango;
    private TangoConfig mConfig;
    private TextView mDeltaTextView;
    private TextView mPoseCountTextView;
    private TextView mPoseTextView;
    private TextView mQuatTextView;
    private TextView mPoseStatusTextView;
    private TextView mTangoServiceVersionTextView;
    private TextView mApplicationVersionTextView;
    private TextView mTangoEventTextView;
    private Button mMotionResetButton;
    private float mPreviousTimeStamp;
    private int mPreviousPoseStatus;
    private int count;
    private float mDeltaTime;
    private boolean mIsAutoRecovery;
    private GLClearRenderer mRenderer;
    private GLSurfaceView mGLView;
    private SurfaceHolder surfaceHolder;
    private SurfaceView surfaceView;
    
    private CameraView mCameraView;

 

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_motion_tracking);
        
        Intent intent = getIntent();
        mIsAutoRecovery = intent.getBooleanExtra(StartActivity.KEY_MOTIONTRACKING_AUTORECOVER,
                false);
        // Text views for displaying translation and rotation data
        mPoseTextView = (TextView) findViewById(R.id.pose);
        mQuatTextView = (TextView) findViewById(R.id.quat);
        mPoseCountTextView = (TextView) findViewById(R.id.posecount);
        mDeltaTextView = (TextView) findViewById(R.id.deltatime);
        mTangoEventTextView = (TextView) findViewById(R.id.tangoevent);
     
        
        
        // Buttons for selecting camera view and Set up button click listeners
        findViewById(R.id.first_person_button).setOnClickListener(this);
        findViewById(R.id.third_person_button).setOnClickListener(this);
        findViewById(R.id.top_down_button).setOnClickListener(this);

        // Button to reset motion tracking
        mMotionResetButton = (Button) findViewById(R.id.resetmotion);

        // Text views for the status of the pose data and Tango library versions
        mPoseStatusTextView = (TextView) findViewById(R.id.status);
        mTangoServiceVersionTextView = (TextView) findViewById(R.id.version);
        mApplicationVersionTextView = (TextView) findViewById(R.id.appversion);

        // Set up button click listeners
        mMotionResetButton.setOnClickListener(this);
        
        // Instantiate the Tango service
        mTango = new Tango(this);
        // Create a new Tango Configuration and enable the MotionTrackingActivity API
        mConfig = new TangoConfig();
        mConfig = mTango.getConfig(TangoConfig.CONFIG_TYPE_CURRENT);
        mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_MOTIONTRACKING, true);
        
        
        try {
            setTangoListeners();
        } catch (TangoErrorException e) {
            Toast.makeText(getApplicationContext(), R.string.TangoError, Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(getApplicationContext(), R.string.motiontrackingpermission,
                    Toast.LENGTH_SHORT).show();
        }
        
       
        
        
        
        
        // OpenGL view where all of the graphics are drawn
        mGLView = (GLSurfaceView) findViewById(R.id.gl_surface_view);
       // mGLView = new GLSurfaceView(this);
        mGLView.setEGLConfigChooser(8,8,8,8,16,0);
        //ADDED these two
        surfaceHolder = mGLView.getHolder();
        surfaceHolder.setFormat(PixelFormat.TRANSLUCENT);

        // Configure OpenGL renderer
        mRenderer = new GLClearRenderer();
        //mGLView.setEGLContextClientVersion(2);
        mGLView.setRenderer(mRenderer);
        
        // mGLView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        // Now also create a view which contains the camera preview...
        //mCameraView = new TangoCameraView( this , mTango);
        //mCameraView = new CameraView( this);
        
        // ...and add it, wrapping the full screen size.
        //addContentView( mCameraView, new LayoutParams( LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT ) );
        surfaceView = new SurfaceView(this);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        addContentView( surfaceView, new LayoutParams( LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT ) );
      
     

        // The Auto-Recovery ToggleButton sets a boolean variable to determine
        // if the
        // Tango service should automatically attempt to recover when
        // / MotionTrackingActivity enters an invalid state.
        if (mIsAutoRecovery) {
            mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_AUTORECOVERY, true);
            Log.i(TAG, "Auto Reset On!!!");
        } else {
            mConfig.putBoolean(TangoConfig.KEY_BOOLEAN_AUTORECOVERY, false);
            Log.i(TAG, "Auto Reset Off!!!");
        }

        PackageInfo packageInfo;
        try {
            packageInfo = this.getPackageManager().getPackageInfo(this.getPackageName(), 0);
            mApplicationVersionTextView.setText(packageInfo.versionName);
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }

        // Display the library version for debug purposes
        mTangoServiceVersionTextView.setText(mConfig.getString("tango_service_library_version"));

    }

    

    private void motionReset() {
        mTango.resetMotionTracking();
    }

    @Override
    protected void onPause() {
        super.onPause();
        
    }

    protected void onResume() {
        super.onResume();
       
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.first_person_button:
            //mRenderer.setFirstPersonView();
            break;
        case R.id.top_down_button:
            //mRenderer.setTopDownView();
            break;
        case R.id.third_person_button:
            //mRenderer.setThirdPersonView();
            break;
        case R.id.resetmotion:
            motionReset();
            break;
        default:
            Log.w(TAG, "Unknown button click");
            return;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false; 
    }

    
    /**
     * Set up the TangoConfig and the listeners for the Tango service, then begin using the Motion
     * Tracking API. This is called in response to the user clicking the 'Start' Button.
     */
    private void setTangoListeners() {
        // Lock configuration and connect to Tango
        // Select coordinate frame pair
        final ArrayList<TangoCoordinateFramePair> framePairs = 
                new ArrayList<TangoCoordinateFramePair>();
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE));
        // Listen for new Tango data
        mTango.connectListener(framePairs, new OnTangoUpdateListener() {

            @Override
            public void onPoseAvailable(final TangoPoseData pose) {
                // Log whenever Motion Tracking enters a n invalid state
                if (!mIsAutoRecovery && (pose.statusCode == TangoPoseData.POSE_INVALID)) {
                    Log.w(TAG, "Invalid State");
                }
                if (mPreviousPoseStatus != pose.statusCode) {
                    count = 0;
                }
                count++;
                mPreviousPoseStatus = pose.statusCode;
                mDeltaTime = (float) (pose.timestamp - mPreviousTimeStamp) * SECS_TO_MILLISECS;
                mPreviousTimeStamp = (float) pose.timestamp;
                // Update the OpenGL renderable objects with the new Tango Pose
                // data
                float[] translation = pose.getTranslationAsFloats();
                
                //mGLView.requestRender();

                // Update the UI with TangoPose information
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        DecimalFormat threeDec = new DecimalFormat("0.000");
                        String translationString = "[" + threeDec.format(pose.translation[0])
                                + ", " + threeDec.format(pose.translation[1]) + ", "
                                + threeDec.format(pose.translation[2]) + "] ";
                        String quaternionString = "[" + threeDec.format(pose.rotation[0]) + ", "
                                + threeDec.format(pose.rotation[1]) + ", "
                                + threeDec.format(pose.rotation[2]) + ", "
                                + threeDec.format(pose.rotation[3]) + "] ";

                        // Display pose data on screen in TextViews
                        Log.i(TAG,translationString);
                        mPoseTextView.setText(translationString);
                        mQuatTextView.setText(quaternionString);
                        mPoseCountTextView.setText(Integer.toString(count));
                        mDeltaTextView.setText(threeDec.format(mDeltaTime));
                        if (pose.statusCode == TangoPoseData.POSE_VALID) {
                            mPoseStatusTextView.setText(R.string.pose_valid);
                        } else if (pose.statusCode == TangoPoseData.POSE_INVALID) {
                            mPoseStatusTextView.setText(R.string.pose_invalid);
                        } else if (pose.statusCode == TangoPoseData.POSE_INITIALIZING) {
                            mPoseStatusTextView.setText(R.string.pose_initializing);
                        } else if (pose.statusCode == TangoPoseData.POSE_UNKNOWN) {
                            mPoseStatusTextView.setText(R.string.pose_unknown);
                        }
                    }
                });
            }

            @Override
            public void onXyzIjAvailable(TangoXyzIjData arg0) {
                // We are not using TangoXyzIjData for this application
            }

            @Override
            public void onTangoEvent(final TangoEvent event) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTangoEventTextView.setText(event.eventKey + ": " + event.eventValue);
                    }
                });
            }
        });
    }

    
    
    private void setUpExtrinsics() {
        // Get device to imu matrix.
        TangoPoseData device2IMUPose = new TangoPoseData();
        TangoCoordinateFramePair framePair = new TangoCoordinateFramePair();
        framePair.baseFrame = TangoPoseData.COORDINATE_FRAME_IMU;
        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_DEVICE;
        device2IMUPose = mTango.getPoseAtTime(0.0, framePair);
       // mRenderer.getModelMatCalculator().SetDevice2IMUMatrix(
       //         device2IMUPose.getTranslationAsFloats(), device2IMUPose.getRotationAsFloats());

        // Get color camera to imu matrix.
        TangoPoseData color2IMUPose = new TangoPoseData();
        framePair.baseFrame = TangoPoseData.COORDINATE_FRAME_IMU;
        framePair.targetFrame = TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR;
        color2IMUPose = mTango.getPoseAtTime(0.0, framePair);

       // mRenderer.getModelMatCalculator().SetColorCamera2IMUMatrix(
        //        color2IMUPose.getTranslationAsFloats(), color2IMUPose.getRotationAsFloats());
    }

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		Surface surface = holder.getSurface();
        if (surface.isValid()) {
       	 TangoConfig config = new TangoConfig();
       	 config =  mTango.getConfig(TangoConfig.CONFIG_TYPE_CURRENT);
       	 mTango.connectSurface(0, surface);
       	 mTango.connect(config);
        }
		
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		mTango.disconnectSurface(0);
		
	}


	

}
