//http://blog.csdn.net/fuuckwtu/article/details/7070205
package com.example.cameravideo;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.stream.Format;

import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.PictureCallback;
import android.hardware.SensorManager;
import android.os.AsyncTask;
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

import com.example.mysensorlistener.Consts;
import com.example.mysensorlistener.MySensorListener;
import com.zhangxaochen.sensordataxml.NewSessionNode;
import com.zhangxaochen.sensordataxml.XmlRootNode;

public class CameraVideo extends Activity {

	private static final String TAG = "CameravedioActivity";
	private Camera camera;
	private boolean preview = false;

	final String _dataFolderName="CameraVideo-data";
	File _dataFolder;
	File _projFolder;
	
	final String picNamePrefix="pic";
	final String picExt=".jpg";
	final String projXmlName="collection-proj.xml";
	
	final String dataXmlPrefix="sensor";
	final String dataXmlExt=".xml";
	
	//----------two arrays
//	String[] picNames;
//	float[] picTimestamps;
	List<String> picNames;
	List<Float> picTimestamps;

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
	CollectionProjXml _projConfigXmlNode=new CollectionProjXml();

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
		// --------------------seekbar ����ʵ�ֱ佹�߼�
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
				
				//----------------������������
				int capNum=Integer.parseInt(editTextCaptureNum.getText().toString());
				picNames=new ArrayList<String>();
				picTimestamps=new ArrayList<Float>();
				
				
				//--------------������Ż�ȡ�ؼ�ֵ
				String projName=editTextProjName.getText().toString();
				String projDescription=editTextDescription.getText().toString();
				
				_projConfigXmlNode.setProjName(projName);
				_projConfigXmlNode.setProjDescription(projDescription);
				_projConfigXmlNode.collectionCntPlusOne();
//				_projConfigXmlNode.getCollectionsNode().collectionList.add(object)
				
				
				//----------����������
				_listener.reset();
				_listener.registerWithSensorManager(_sm, Consts.aMillion/30);
				((NewSessionNode)_newSessionNode).setBeginTime(System.currentTimeMillis()*Consts.MS2S);
				System.out.println("setBeginTime: "+System.currentTimeMillis()*Consts.MS2S);

				
				//------------����
				long interval=(long) (Float.parseFloat(editTextInterval.getText().toString())*1000);
				long dt=2*capNum*interval;
				System.out.println("dt, interval: "+dt+", "+interval);
				
				CountDownTimer timer=new CountDownTimer(dt+100, interval) {
					int cnt=0;
					@Override
					public void onTick(long millisUntilFinished) {
						System.out.println("onTick, millisUntilFinished, cnt: "+millisUntilFinished+", "+cnt);
						if(cnt%2==1){
							System.out.println("cnt%2==1");
							//TODO: ����
							System.out.println("cnt: "+cnt);
							camera.takePicture(null, null,
									new TakePictureCallback());
						}
						cnt++;
					}//onTick
					
					@Override
					public void onFinish() {
						System.out.println("onFinish--------");
						
						//----------------------ֹͣ����
						_listener.unregisterWithSensorManager(_sm);
						
						((NewSessionNode)_newSessionNode).setEndTime(System.currentTimeMillis()*Consts.MS2S);
						_newSessionNode.addNode(_listener.getSensorData());
						
						//---------------------����xml�����ļ�
						System.out.println("_projFolder: "+_projFolder+", "+_projFolder.isDirectory());
						int dataXmlCnt = _projFolder.list(new FilenameFilter() {
							@Override
							public boolean accept(File dir, String filename) {
								return filename.contains(dataXmlPrefix)
										&& filename.endsWith(dataXmlExt);
							}
						}).length;
						String dataXmlName=dataXmlPrefix+"_"+dataXmlCnt+dataXmlExt;
						_dataXmlFile=new File(_projFolder, dataXmlName);
						

						
						WriteXmlTask task=new WriteXmlTask(){

							@Override
							protected void onPostExecute(Void result) {
								super.onPostExecute(result);
								
								buttonCapture.setEnabled(true);
							}
							
						};
						task.setXmlRootNode(_newSessionNode)
						.setFile(_dataXmlFile)
						.setPersister(_persister)
						.execute();
						
						//-------------------д�����ļ�
						CollectionNode cNode=new CollectionNode();
						cNode.setSensorName(dataXmlName);
						cNode.addPicNodes(picNames, picTimestamps);
						System.out.println("_projConfigXmlNode: "+_projConfigXmlNode);
						_projConfigXmlNode.getCollectionsNode().collectionList.add(cNode);
						try {
							_persister.write(_projConfigXmlNode, new File(_projFolder, projXmlName));
						} catch (Exception e) {
							e.printStackTrace();
						}

						
					}
				};
				timer.start();
				
			}//onClick
		});
		
		
	}//respondEvents
	@Override
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		/*
		 * ���ô������ԣ�һ��Ҫ�� setContentView(R.layout.main) ֮ǰ
		 */
		// ���ڱ���,��ʵ������manifes�ļ�����ע��
		// requestWindowFeature(Window.FEATURE_NO_TITLE);
		// ȫ��
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(R.layout.activity_main);
		_sm=(SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
		
		initWidgets();
		respondEvents();
		
		SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
		// surfaceView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		surfaceView.getHolder().setFixedSize(200, 200);
		surfaceView.getHolder().addCallback(new SurfaceViewCallback());

		_dataFolder=Environment.getExternalStoragePublicDirectory(_dataFolderName);
		if(!_dataFolder.exists())
			_dataFolder.mkdirs();
		_projFolder =new File(_dataFolder, editTextProjName.getText().toString());
		
		System.out.println("!_projFolder.exists(): "+!_projFolder.exists());
		if(!_projFolder.exists()){
			System.out.println("!_projFolder.exists()");
			_projFolder.mkdirs();
		}
		
		try {
			File configFile=new File(_projFolder, projXmlName);
			if(configFile.exists())
				_projConfigXmlNode=_persister.read(CollectionProjXml.class, configFile);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}// onCreate

	private final class SurfaceViewCallback implements Callback {
		/**
		 * surfaceView �������ɹ�����ô˷���
		 */
		@Override
		public void surfaceCreated(SurfaceHolder holder) {
			Log.d(TAG, "surfaceCreated");
			// ��SurfaceView������֮�� ������ͷ ע���� android.hardware.Camera
			camera = Camera.open();
			camera.setDisplayOrientation(90);

			/*
			 * This method must be called before startPreview(). otherwise
			 * surfaceviewû��ͼ��
			 */
			try {
				camera.setPreviewDisplay(holder);
			} catch (IOException e) {
				e.printStackTrace();
			}

			Camera.Parameters parameters = camera.getParameters();
			/* ����Ԥ����Ƭ�Ĵ�С���˴�����Ϊȫ�� */
			// WindowManager wm = (WindowManager)
			// getSystemService(Context.WINDOW_SERVICE); // ��ȡ��ǰ��Ļ����������
			// Display display = wm.getDefaultDisplay(); // ��ȡ��Ļ��Ϣ��������
			// parameters.setPreviewSize(display.getWidth(),
			// display.getHeight()); // ����

			// parameters.setPreviewSize(200, 200);

			/* ÿ�������ͷ����5֡���棬 */
			parameters.setPreviewFrameRate(2);
			/* ������Ƭ�������ʽ:jpg */
			// parameters.setPictureFormat(PixelFormat.JPEG);
			parameters.setPictureFormat(ImageFormat.RGB_565);
			/* ��Ƭ���� */
			parameters.set("jpeg-quality", 85);
			/* ������Ƭ�Ĵ�С���˴���Ƭ��С������Ļ��С */
			// parameters.setPictureSize(display.getWidth(),
			// display.getHeight());
			parameters.setPictureSize(200, 200);

			List<String> focusModes = parameters.getSupportedFocusModes();
			if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
				parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
			}
			/* �����������赽 camera ������ */
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
					// ����Ƶ�����У����ﴫ�ͱ���frame���ݸ�remote��
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
		 * SurfaceView ������ʱ�ͷŵ� ����ͷ
		 */
		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			if (camera != null) {
				/* ������ͷ���ڹ�������ֹͣ�� */
				if (preview) {
					camera.stopPreview();
					preview = false;
				}
				// ���ע���˴˻ص�����release֮ǰ���ã�����release֮�󻹻ص���crash
				camera.setPreviewCallback(null);
				camera.release();
			}
		}

	}
	
	int getPicCnt(){
//			String projFolderName=_dataFolderName+File.separator+editTextProjName.getText().toString();
//			File projFolder = Environment.getExternalStoragePublicDirectory(projFolderName);
//		File projFolder =new File(_dataFolder, editTextProjName.getText().toString());
		System.out.println("in getPicCnt(), _projFolder: "+_projFolder+", "+_projFolder.isDirectory());
		int picCnt=_projFolder.list(new FilenameFilter() {

			@Override
			public boolean accept(File dir, String filename) {
				return filename.contains(picNamePrefix)&&filename.endsWith(picExt);
			}
		}).length;

		return picCnt;
	}

	/**
	 * ������Ƭ������֮����¼�
	 */
	private final class TakePictureCallback implements PictureCallback {
		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
			System.out.println("onPictureTaken~~~~~~~~~~~~~~~~~~~~~~~~");
			//--------������Ƭ
			int picCnt=getPicCnt();
			
			String picName=picNamePrefix+"_"+picCnt+picExt;
			File picFile=new File(_projFolder, picName);
			System.out.println("picFile: "+picFile.getAbsolutePath());
			
			FileOutputStream fout;
			try {
				fout = new FileOutputStream(picFile);
//				fout.write(data);
//				fout.close();
				//--------------------------�첽����Ƭ
				WriteFoutTask foutTask=new WriteFoutTask();
				foutTask.setFout(fout)
				.setData(data)
				.execute();				
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			camera.startPreview();
			
			//------------��¼����epoch ʱ��
			Float epochTime=(float) (System.currentTimeMillis()*Consts.MS2S);
			picTimestamps.add(epochTime);
			picNames.add(picName);

		}//onPictureTaken
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

//�� UI �߳�д�ļ���
class WriteXmlTask extends AsyncTask<Void, Void, Void> {
	XmlRootNode _xmlRootNode;
	File _file;
	Persister _persister;
	
	public WriteXmlTask() {
	}
	
	public WriteXmlTask setXmlRootNode(XmlRootNode rootNode){
		_xmlRootNode=rootNode;
		return this;
	}
	
	public WriteXmlTask setFile(File file){
		_file=file;
		return this;
	}
	
	public WriteXmlTask setPersister(Persister persister){
		_persister=persister;
		return this;
	}
	
	@Override
	protected Void doInBackground(Void... params) {
		System.out.println("doInBackground()");
		if (_xmlRootNode == null || _file == null
				|| _persister == null) {
			System.out
					.println("_xmlRootNode==null || _file==null || _persister == null");
			return null;
		}

		try {
//			_persister.write(_captureSessionNode, _file);
			_persister.write(_xmlRootNode, _file);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	protected void onPostExecute(Void result) {
		System.out.println("onPostExecute");
		super.onPostExecute(result);

//		_captureSessionNode.clearAllNodes();
		_xmlRootNode.clear();

	}

}// WriteXmlTask

class WriteFoutTask extends AsyncTask<Void, Void, Void>{
	FileOutputStream fout;
	byte[] data;
	
	@Override
	protected Void doInBackground(Void... params) {
		// TODO Auto-generated method stub
		try {
			fout.write(data);
			fout.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public FileOutputStream getFout() {
		return fout;
	}

	public WriteFoutTask setFout(FileOutputStream fout) {
		this.fout = fout;
		return this;
	}

	public byte[] getData() {
		return data;
	}

	public WriteFoutTask setData(byte[] data) {
		this.data = data;
		return this;
	}


	
}