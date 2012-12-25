package com.simcc800;

public class UpdateLCDThread extends Thread {
	private MainActivity host;
	static final public int FRAME_DELAY = 50; // Milliseconds
	static public boolean is_running = true;
	long last_millis = 0;
	byte[] buf = new byte[1600];
	
	public UpdateLCDThread(MainActivity _host) {
		host = _host;
	}
	
	@Override
	public void run() {
		last_millis = System.currentTimeMillis();
		try {
			while(true){
				if(is_running==false || host==null || host.myview==null) {
					Thread.sleep(1000);
				}
				long curr_millis = System.currentTimeMillis();
				long delta_millis = curr_millis - last_millis;
				if(delta_millis < FRAME_DELAY) {
					Thread.sleep(FRAME_DELAY - delta_millis);
				}
				for(int i=0; i<1600; i++) {
					buf[i] = FleurDeLisDriver.fixedram0000[0x9C0+i];
				}
				host.myview.update(buf);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
