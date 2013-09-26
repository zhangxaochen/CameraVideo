//http://blog.csdn.net/fuuckwtu/article/details/7070205
package com.example.cameravideo;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.stream.Format;

import com.example.mysensorlistener.Consts;
import com.example.mysensorlistener.MySensorListener;
import com.zhangxaochen.xmlParser.NewSessionNode;
import com.zhangxaochen.xmlParser.XmlRootNode;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.PictureCallback;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;

public class CameraVideo extends Activity {

	private static final String TAG = "CameravedioActivity";
	private Camera camera;
	private boolean preview = false;

	// --------------------------------UI
	private SeekBar mSeekBar;
	EditText editTextCaptureNum;
	EditText editTextInterval;
	EditText editTextProjName;
	EditText editTextDescription;
	Button buttonCapture;

	// --------------------------------data xml
	SensorManager _sm;
	MySensorListener _listener=new MySensorListener();
	
	File _dataXmlFile;
	Format _format = new Format("<?xml version=\"1.0\" encoding= \"UTF-8\" ?>");
	Persister _persister = new Persister(_format);

	XmlRootNode	_newSessionNode=new NewSessionNode();

	//-----UI
	private void initWidgets() {
		mSeekBar = (SeekBar) findViewById(R.id.seekbar);
		
		editTextCaptureNum = (EditText) findViewById(R.id.editTextCaptureNum);
		editTextInterval = (EditText) findViewById(R.id.editTextInterval);
		editTextProjName = (EditText) findViewById(R.id.editTextProjName);
		editTextDescription = (EditText) findViewById(R.id.editTextDescription);
		buttonCapture = (Button) findViewById(R.id.buttonCapture);
	}//initWidgets

	private void respondEvents() {
		// --------------------seekbar 用来实现变焦逻辑
		mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				Log.d(TAG, "progress:" + progress);
				Camera.Parameters parameters = camera.getParameters();
				int maxZoom = parameters.getMaxZoom();

				int zoom = (int) (maxZoom * progress * 1.f / seekBar.getMax());
				parameters.setZoom(zoom);
				camera.setParameters(parameters);
			}//onProgressChanged

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				Log.d(TAG, "onStartTrackingTouch");
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				Log.d(TAG, "onStopTrackingTouch");
				camera.autoFocus(new AutoFocusCallback() {
					@Override
					public void onAutoFocus(boolean success, Camera camera) {
						if (success)
							camera.takePicture(null, null,
									new JpegPictureCallback());
					}
				});

			}

		});

		//--------------------------buttonCapture
		buttonCapture.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				buttonCapture.setEnabled(false);
				
				//----------传感器采样
				_listener.reset();
				_listener.registerWithSensorManager(_sm, Consts.aMillion/30);
				((NewSessionNode)_newSessionNode).setBeginTime(System.currentTimeMillis()*Consts.MS2S);
				System.out.println("setBeginTime: "+System.currentTimeMillis()*Consts.MS2S);

				
				//------------连拍
				long interval=(long) (Float.parseFloat(editTextInterval.getText().toString())*1000);
				int capNum=Integer.parseInt(editTextCaptureNum.getText().toString());
				long dt=2*capNum*interval;
				
				CountDownTimer timer=new CountDownTimer(dt+30, interval) {
					int cnt=0;
					@Override
					public void onTick(long millisUntilFinished) {
						if(cnt%2==1){
							//TODO: 拍照
							System.out.println("cnt: "+cnt);
						}
					}
					
					@Override
					public void onFinish() {
						
						buttonCapture.setEnabled(true);
					}
				};
			}//onClick
		});
		
		
	}//respondEvents

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		/*
		 * 设置窗口属性：一定要在 setContentView(R.layout.main) 之前
		 */
		// 窗口标题,其实可以在manifes文件里面注册
		// requestWindowFeature(Window.FEATURE_NO_TITLE);
		// 全屏
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(R.layout.activity_main);
		initWidgets();
		respondEvents();
		
		SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
		// surfaceView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		surfaceView.getHolder().setFixedSize(200, 200);
		surfaceView.getHolder().addCallback(new SurfaceViewCallback());


	}// onCreate

	private final class SurfaceViewCallback implements Callback {
		/**
		 * surfaceView 被创建成功后调用此方法
		 */
		@Override
		public void surfaceCreated(SurfaceHolder holder) {
			Log.d(TAG, "surfaceCreated");
			// 在SurfaceView创建好之后 打开摄像头 注意是 android.hardware.Camera
			camera = Camera.open();
			camera.setDisplayOrientation(90);

			/*
			 * This method must be called before startPreview(). otherwise
			 * surfaceview没有图像
			 */
			try {
				camera.setPreviewDisplay(holder);
			} catch (IOException e) {
				e.printStackTrace();
			}

			Camera.Parameters parameters = camera.getParameters();
			/* 设置预览照片的大小，此处设置为全屏 */
			// WindowManager wm = (WindowManager)
			// getSystemService(Context.WINDOW_SERVICE); // 获取当前屏幕管理器对象
			// Display display = wm.getDefaultDisplay(); // 获取屏幕信息的描述类
			// parameters.setPreviewSize(display.getWidth(),
			// display.getHeight()); // 设置

			// parameters.setPreviewSize(200, 200);

			/* 每秒从摄像头捕获5帧画面， */
			parameters.setPreviewFrameRate(2);
			/* 设置照片的输出格式:jpg */
			// parameters.setPictureFormat(PixelFormat.JPEG);
			parameters.setPictureFormat(ImageFormat.RGB_565);
			/* 照片质量 */
			parameters.set("jpeg-quality", 85);
			/* 设置照片的大小：此处照片大小等于屏幕大小 */
			// parameters.setPictureSize(display.getWidth(),
			// display.getHeight());
			parameters.setPictureSize(200, 200);

			List<String> focusModes = parameters.getSupportedFocusModes();
			if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
				parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
			}
			/* 将参数对象赋予到 camera 对象上 */
			// camera.setParameters(parameters);
			mSeekBar.setMax(100);
			camera.startPreview();

			camera.autoFocus(new AutoFocusCallback() {

				@Override
				public void onAutoFocus(boolean success, Camera camera) {
				}
			});

			/**
			 * Installs a callback to be invoked for every preview frame in
			 * addition to displaying them on the screen. The callback will be
			 * repeatedly called for as long as preview is active. This method
			 * can be called at any time, even while preview is live. Any other
			 * preview callbacks are overridden. a callback object that receives
			 * a copy of each preview frame, or null to stop receiving
			 */
			camera.setPreviewCallback(new Camera.PreviewCallback() {

				@Override
				public void onPreviewFrame(byte[] data, Camera camera) {
					// TODO Auto-generated method stub
					// 在视频聊天中，这里传送本地frame数据给remote端
					// Log.d(TAG, "camera:" + camera);
					// Log.d(TAG, "byte:" + data);
				}

			});
			preview = true;
		}//surfaceCreated

		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width,
				int height) {
			Log.d(TAG, "surfaceChanged");
		}

		/**
		 * SurfaceView 被销毁时释放掉 摄像头
		 */
		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			if (camera != null) {
				/* 若摄像头正在工作，先停止它 */
				if (preview) {
					camera.stopPreview();
					preview = false;
				}
				// 如果注册了此回调，在release之前调用，否则release之后还回调，crash
				camera.setPreviewCallback(null);
				camera.release();
			}
		}

	}

	/**
	 * 处理照片被拍摄之后的事件
	 */
	private final class TakePictureCallback implements PictureCallback {
		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
		}
	}
}// CameraVideo

class JpegPictureCallback implements PictureCallback {

	@Override
	public void onPictureTaken(byte[] data, Camera camera) {
		String fname = "shit.jpg";
		File path = Environment.getExternalStorageDirectory();
		File file = new File(path, fname);
		FileOutputStream fout;
		try {
			fout = new FileOutputStream(file);
			fout.write(data);
			// Bitmap bm=BitmapFactory.decodeByteArray(data, 0, data.length);
			// Matrix mat=new Matrix();
			// mat.setRotate(90);
			// bm=Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(),
			// mat, true);
			// bm.compress(Bitmap.CompressFormat.JPEG, 50, fout);
			fout.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		camera.startPreview();
	}
}// JpegPictureCallback
