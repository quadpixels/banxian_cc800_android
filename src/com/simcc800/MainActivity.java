package com.simcc800;

// Needs > 16MB of memory space.

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;


import android.os.Bundle;
import android.os.Environment;
import android.app.Activity;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {
	FleurDeLisDriver fleurDeLisDriver = null;
	EmulatorThread thdEmulator;
	UpdateLCDThread thdUIUpdate;
	WQXView myview;
	Button magic_button;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		myview = (WQXView)findViewById(R.id.view1);
		myview.host = this;
		/*
		magic_button = (Button)findViewById(R.id.button1);
		magic_button.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				FleurDeLisDriver.syncMachineTime();
				Toast.makeText(getApplicationContext(), "Sync Machine Type", Toast.LENGTH_SHORT).show();
			}
		});
		*/
		WQXCC800();
	}

	@Override
	protected void onPause() {
		super.onPause();
		thdEmulator.setIsRunning(false);
		UpdateLCDThread.is_running=false;
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		CPU.total_inst_count = 0;
		EmulatorThread.needs_sync_machine_type=true;
		thdEmulator.setIsRunning(true);
		UpdateLCDThread.is_running=true;
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		thdEmulator.setIsRunning(false);
		UpdateLCDThread.is_running=false;
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	private void WQXCC800() {
		// First, increase heap size
		
		
		byte[] brom_buf = null;
		{
			final File brom_file = new File(Environment.getExternalStorageDirectory()
                    .getAbsolutePath()+"/cc800/", "obj.bin");
			try {
				FileInputStream is = new FileInputStream(brom_file);
				int brom_available = is.available();
				brom_buf = new byte[brom_available];
				int idx = 0;
				while(is.available() > 0) {
					is.read(brom_buf, idx+0x4000, 0x4000);
					is.read(brom_buf, idx,        0x4000);
					idx+=0x8000;
				}
				is.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		byte[] norflash_buf = null;
		{
			final File norflash_file = new File(Environment.getExternalStorageDirectory()
                    .getAbsolutePath()+"/cc800/", "cc800.fls");
			try {
				FileInputStream is = new FileInputStream(norflash_file);
				int norflash_available = is.available();
				norflash_buf = new byte[norflash_available];
				int idx = 0;
				while(is.available()>0) {
					is.read(norflash_buf, idx+0x4000, 0x4000);
					is.read(norflash_buf, idx, 0x4000);
					idx+=0x8000;
				}
				is.close();
			} catch(IOException e) {
				e.printStackTrace();
			}
		}
		fleurDeLisDriver = new FleurDeLisDriver(brom_buf, norflash_buf);
		CPU.cpuInitialize();
		Toast.makeText(getApplicationContext(), "Init'ed FleurDeLisDriver", Toast.LENGTH_LONG).show();
		thdEmulator = new EmulatorThread(this);
		thdEmulator.setPriority(Thread.MAX_PRIORITY);
		thdEmulator.start();
		thdUIUpdate = new UpdateLCDThread(this);
		thdUIUpdate.start();
	}
}
