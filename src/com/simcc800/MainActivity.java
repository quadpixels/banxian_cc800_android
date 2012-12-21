package com.simcc800;

// Needs > 16MB of memory space.

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.quadpixels.CPU;
import org.quadpixels.EmulatorThread;
import org.quadpixels.FleurDeLisDriver;

import android.os.Bundle;
import android.os.Environment;
import android.app.Activity;
import android.view.Menu;
import android.view.View;
import android.widget.Toast;

public class MainActivity extends Activity {
	FleurDeLisDriver fleurDeLisDriver = null;
	WQXView myview;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		myview = (WQXView)findViewById(R.id.view1);
		WQXCC800();
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
		CPU cpu = new CPU();
		fleurDeLisDriver = new FleurDeLisDriver(brom_buf, norflash_buf, cpu);
		myview.fleurDeLisDriver = fleurDeLisDriver;
		cpu.cpuInitialize();
		Toast.makeText(getApplicationContext(), "Init'ed FleurDeLisDriver", 5000).show();
		EmulatorThread thd = new EmulatorThread(cpu, fleurDeLisDriver, this);
		thd.start();
	}
	
	public void updateLCD(byte[] lcdbuffer, int instcnt) {
		myview.update(lcdbuffer, instcnt);
	}
}
