//http://blog.csdn.net/fuuckwtu/article/details/7070205
package com.example.cameravideo;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.stream.Format;

import android.R.bool;
import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.hardware.Camera.Size;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
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
    boolean savePicFinished = false;
    boolean saveXmlFinished = false;
    
    private static final String TAG = "CameravedioActivity";
    private Camera camera;
    private boolean preview = false;
    
    final String _dataFolderName = "CameraVideo-data";
    File _dataFolder;
    File _projFolder;
    
    final String picNamePrefix = "pic";
    
    final String picExt = ".jpg";
    final String projXmlName = "collection-proj.xml";
    
    final String dataXmlPrefix = "sensor";
    final String dataXmlExt = ".xml";
    String dataXmlName = "";

    
    // --------------------------------UI
    private SeekBar mSeekBar;
    EditText editTextCaptureNum;
    EditText editTextInterval;
    EditText editTextProjName;
    EditText editTextDescription;
    Button buttonCapture;
    
    // --------------------------------data xml
    SensorManager _sm;
    MySensorListener _listener = new MySensorListener();
    
    File _dataXmlFile;
    
    /* 用来save xml文件对象 */
    Persister _persister = new Persister(new Format("<?xml version=\"1.0\" encoding= \"UTF-8\" ?>"));
    
    /* 保存传感器数据对象 */
    NewSessionNode _newSessionNode = new NewSessionNode();
    
    /* 保存采集工程对象 */
    CollectionProjXml _projConfigXmlNode = new CollectionProjXml();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        /*
         * 设置窗口属性：一定要在 setContentView(R.layout.main) 之前
         */
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                             WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        _sm = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        
        initWidgets();
        respondEvents();
        
        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        // surfaceView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        surfaceView.getHolder().setFixedSize(200, 200);
        surfaceView.getHolder().addCallback(new SurfaceViewCallback());
        
        // 设置存储目录
        _dataFolder = Environment.getExternalStoragePublicDirectory(_dataFolderName);
        if(!_dataFolder.exists())
            _dataFolder.mkdirs();
    }// onCreate
    
    @Override
    protected void onResume() {
        super.onResume();
        
        /* 启动就注册采集数据 */
        _listener.reset();
        _listener.registerWithSensorManager(_sm, Consts.aMillion / 30);
    }//onResume
    
    @Override
    protected void onPause() {
        super.onPause();
        
        _listener.unregisterWithSensorManager(_sm);
    }//onPause

    //-----UI
    private void initWidgets() {
        mSeekBar = (SeekBar) findViewById(R.id.seekbar);
        
        editTextCaptureNum = (EditText) findViewById(R.id.editTextCaptureNum);
        editTextCaptureNum.setEnabled(false);
        
        editTextInterval = (EditText) findViewById(R.id.editTextInterval);
        editTextProjName = (EditText) findViewById(R.id.editTextProjName);
        editTextDescription = (EditText) findViewById(R.id.editTextDescription);
        buttonCapture = (Button) findViewById(R.id.buttonCapture);
    }//initWidgets
    
    /**
     * 加入seekbar的zoom功能
     */
    private void seekbar_event_impl() {
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                /* 修改了一下调节的逻辑，使用简单的方式，将seekbar的最大调节范围设置成paramters.getMaxZoom(),
                 * 然后直接根据当前seekbar的progress来调节摄像头的zoom大小
                 */
                Camera.Parameters parameters = camera.getParameters();
                if(parameters.isZoomSupported()) {
                    Log.e("progress", String.valueOf(progress));
                    parameters.setZoom(progress);
                    camera.setParameters(parameters);
                }
            }//onProgressChanged
            
            @Override
            public void onStartTrackingTouch(SeekBar arg0) {
            }
            
            @Override
            public void onStopTrackingTouch(SeekBar arg0) {
            }
        });
    }
    
    /**
     * 得到当前工程文件的节点数据结构
     */
    private void prepare_project_config_xml_node() {
        /* 获取当前工程名称和描述信息 */
        String projName = editTextProjName.getText().toString();
        String projDescription = editTextDescription.getText().toString();
        
        /* 检查 proj文件夹是否存在，如果不存在则新建 */
        _projFolder = new File(_dataFolder, projName);
        if(!_projFolder.exists()) {
            _projFolder.mkdirs();
        }
        
        /* 创建采集工程xml文件 */
        try {
            File configFile = new File(_projFolder, projXmlName);
            if(configFile.exists())
                _projConfigXmlNode = _persister.read(CollectionProjXml.class, configFile);
            else
                _projConfigXmlNode = new CollectionProjXml();
        } catch(Exception e) {
            e.printStackTrace();
        }
        
        _projConfigXmlNode.setProjName(projName);
        _projConfigXmlNode.setProjDescription(projDescription);
        _projConfigXmlNode.collectionCntPlusOne();
    }

    /**
     * 压缩当前proj目录
     */
    private void zip_current_proj_directory() {
    	String zipName=_projFolder.getName()+".zip";

    	try {
    		File zipFile=new File(_projFolder.getParent(), zipName);
    		if(zipFile.exists())
    			zipFile.delete();
    		ZipUtility.zipDirectory(_projFolder, zipFile);
    	} catch (IOException e) {
    		e.printStackTrace();
    	}
    }

    /**
     * 采集传感器数据，并且在相隔delay_time之后采集照片数据
     */
    private class capture_sensor_and_picture_t extends CountDownTimer {
        /**
         * 存在bug在sumsung手机上，如果设置时间为(2*interval, interval)，那么
         * 第一下的onTick将在Timer启动时候调用，而不是在interval时候调用。
         *
         * 用了比较naive的方法来解决，设置总时间为3个时间间隔，在第二个onTick调用时候，拍摄照片
         */
    	public capture_sensor_and_picture_t(long delay_time) {
    		super(3*delay_time, delay_time);
    	}

    	private int cnt = 0;

    	/* 为了和之前设计兼容，使用List来存放其实只有一张照片的数据信息 */
    	private List<String> picNames = new ArrayList<String>();
    	private List<Double> picTimestamps = new ArrayList<Double>();

    	@Override
    	public void onTick(long millisUntilFinished) {
    		System.out.println("onTick, millisUntilFinished, cnt: " + millisUntilFinished + ", " + cnt);

    		/* 第二次tick的时候拍照 */
    		if(cnt == 1) {
    			// 照片名称
    			int picCnt = getPicCnt();
    			String picName = picNamePrefix + "_" + picCnt + picExt;

    			// 记录拍照epoch 时间
    			Double epochTime = System.currentTimeMillis() * Consts.MS2S;
    			picTimestamps.add(epochTime);
    			picNames.add(picName);

    			// 获取传感器名称
    			dataXmlName = dataXmlPrefix + "_" + picCnt + dataXmlExt;

    			/* 加入一次新的collection节点 */
    			CollectionNode cNode = new CollectionNode();
    			cNode.setSensorName(dataXmlName);
    			cNode.addPicNodes(picNames, picTimestamps);
    			_projConfigXmlNode.getCollectionsNode().collectionList.add(cNode);

    			/* 写入新的collection到xml文件中 */
    			try {
    				_persister.write(_projConfigXmlNode, new File(_projFolder, projXmlName));
    			} catch(Exception e) {
    				e.printStackTrace();
    			}

    			/**
    			 * 注册了两个回调函数
    			 * MyShutterCallback    在照片采集下来之后调用，给出拍照声音
    			 * TakePictureCallback  在压缩好的jpeg图像采集下来之后调用，用来异步保存图像数据
    			 */
    			camera.takePicture(new MyShutterCallback(), null, null, new TakePictureCallback());
    		}

    		cnt++;
    	}//onTick

    	@Override
    	public void onFinish() {
    		/* 结束sensor数据采集 */
    		_newSessionNode.setEndTime(System.currentTimeMillis() * Consts.MS2S);
    		_newSessionNode.addNode(_listener.getSensorData());

    		_dataXmlFile = new File(_projFolder, dataXmlName);

    		WriteXmlTask task = new WriteXmlTask() {
    			@Override
    			protected void onPostExecute(Void result) {
    				super.onPostExecute(result);
    				saveXmlFinished = true;
    				enableCaptureButton();
    			}//onPostExecute
    		};

    		/* 异步写入传感器数据 */
    		task.setXmlRootNode(_newSessionNode)
    		.setFile(_dataXmlFile)
    		.setPersister(_persister)
    		.execute();
    	} // onFinish
    }

    /**
     * 拍摄按钮按下的相应实现
     */
    private void capture_btn_event_impl() {
    
        buttonCapture.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
            	prepare_project_config_xml_node();

                buttonCapture.setEnabled(false);

                /* 去掉目前采集的数据，也就意味着从点击了button之后的数据才被保存
                 * 使用这种方式的原因是提高采集数据的稳定性，缺点是会耗电一些
                 */
                _listener.clearAllBuf();
                
                /* 传感器数据开始采集时 */
                double beginTime=System.currentTimeMillis() * Consts.MS2S;
                _newSessionNode.setBeginTime(beginTime);
                _listener.set_baseTimestamp(beginTime);
                System.out.println("setBeginTime: " + beginTime);
                
                /* 传感器采集和照片采集开始时间的间隔，单位ms */
                long delay_time = 500;
                
                /* 启动采集任务 */
                new capture_sensor_and_picture_t(delay_time).start();

            }//onClick
        });
    }
    
    /**
     * 设置消息响应的方法
     */
    private void respondEvents() {
        seekbar_event_impl();
        capture_btn_event_impl();
    }//respondEvents
   
    /**
     * 拍照btn是否可以显示的逻辑
     */
    void enableCaptureButton() {
        if(savePicFinished && saveXmlFinished) {
            buttonCapture.setEnabled(true);
            savePicFinished = false;
            saveXmlFinished = false;
        }
    }//enableCaptureButton
    
    
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
            } catch(IOException e) {
                e.printStackTrace();
            }
            
            Camera.Parameters parameters = camera.getParameters();
            
            /* 每秒从摄像头捕获5帧画面， */
//            parameters.setPreviewFrameRate(2);
            
            /* 设置照片的输出格式:jpg */
            parameters.setPictureFormat(PixelFormat.JPEG);
            
            /* 照片质量 */
            parameters.set("jpeg-quality", 100);
            
            /* 设置照片大小为最接近 1920*1080 */
            List<Size> sizes = parameters.getSupportedPictureSizes();
            int min_size = 1000000;
            int index = 0;
            if(sizes != null) {
                for(int i = 0; i < sizes.size(); i++) {
                    Size size = sizes.get(i);
                    if(Math.abs(size.width - 1920) < min_size) {
                        min_size = Math.abs(size.width - 1920);
                        index = i;
                    }
                }
            }
//            parameters.setPictureSize(sizes.get(index).width, sizes.get(index).height);
            
            /* 设置当前seekbar的最大大小 */
            if(parameters.isZoomSupported()) {
                mSeekBar.setMax(parameters.getMaxZoom());
            }
            
            /* 使用定焦模式 */
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
            
            /* 将参数对象赋予到 camera 对象上 */
            camera.setParameters(parameters);
            
            /* 开始捕获数据 */
            camera.startPreview();
            
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
            if(camera != null) {
                /* 若摄像头正在工作，先停止它 */
                if(preview) {
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
     * 返回当前过程目录中图片的数目
     */
    int getPicCnt() {
        return _projFolder.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                return filename.contains(picNamePrefix) && filename.endsWith(picExt);
            }
        }).length;
    }
    
    /**
     * 拍摄照片shutter时候的回调函数
     */
    private class MyShutterCallback implements ShutterCallback {
        private ToneGenerator tone;
        
        @Override
        public void onShutter() {
            /* 拍照声音 */
            if(tone == null)
                tone = new ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2);
        }
    };
    
    /**
     * 处理照片被拍摄之后的事件
     */
    private final class TakePictureCallback implements PictureCallback {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            /* 设置存储照片文件 */
            String picName = picNamePrefix + "_" + getPicCnt() + picExt;
            File picFile = new File(_projFolder, picName);
            
            FileOutputStream fout;
            try {
                fout = new FileOutputStream(picFile);
                
                //--------------------------异步存照片
                WriteFoutTask foutTask = new WriteFoutTask() {
                    @Override
                    protected void onPostExecute(Void result) {
                        super.onPostExecute(result);
                        savePicFinished = true;
                        enableCaptureButton();
                    }
                };
                
                foutTask.setFout(fout)
                .setData(data)
                .execute();
                
            } catch(FileNotFoundException e) {
                e.printStackTrace();
            } catch(IOException e) {
                e.printStackTrace();
            }
            
            camera.startPreview();
        }//onPictureTaken
    }
}// CameraVideo

/**
 * 异步写xml文件
 */
class WriteXmlTask extends AsyncTask<Void, Void, Void> {
    XmlRootNode _xmlRootNode;
    File _file;
    Persister _persister;
    
    public WriteXmlTask() {
    }
    
    public WriteXmlTask setXmlRootNode(XmlRootNode rootNode) {
        _xmlRootNode = rootNode;
        return this;
    }
    
    public WriteXmlTask setFile(File file) {
        _file = file;
        return this;
    }
    
    public WriteXmlTask setPersister(Persister persister) {
        _persister = persister;
        return this;
    }
    
    @Override
    protected Void doInBackground(Void... params) {
        System.out.println("doInBackground()");
        if(_xmlRootNode == null || _file == null || _persister == null) {
            System.out.println("_xmlRootNode==null || _file==null || _persister == null");
            return null;
        }
        
        try {
            //          _persister.write(_captureSessionNode, _file);
            _persister.write(_xmlRootNode, _file);
            
        } catch(Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    @Override
    protected void onPostExecute(Void result) {
        System.out.println("onPostExecute");
        super.onPostExecute(result);
        
        //      _captureSessionNode.clearAllNodes();
        _xmlRootNode.clear();
        
    }
    
}// WriteXmlTask

/**
 * 异步写普通数据文件
 */
class WriteFoutTask extends AsyncTask<Void, Void, Void> {
    FileOutputStream fout;
    byte[] data;
    
    @Override
    protected Void doInBackground(Void... params) {
        try {
            fout.write(data);
            fout.close();
        } catch(IOException e) {
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
