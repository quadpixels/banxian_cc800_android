package com.simcc800;

import com.simcc800.*;

public class EmulatorThread extends Thread {
	int deadlockCounter = 0;
	private static final int tommy_batch = (1<<19)-1;
	MainActivity host;
	byte lcdbuffer[] = new byte[1600];
	private boolean is_running = true;
	
	// Must construct CPU and Driver first then construct this thread.
	public EmulatorThread(MainActivity _host) {
		host = _host;
	}
	
	public void setIsRunning(boolean _is_running) {
		is_running = _is_running;
	}
	
	@Override
	public	void run() {
		// 11-16 Still under construction.
		{
			int batch_size = 150000;
			int curr_batch_todo = batch_size;
			try {
				while(true) {
					if(is_running==false) {
						Thread.sleep(1000);
						continue;
					}
					
					// Execute a BATCH of insts, then update LCD!
					while(curr_batch_todo > 0) {
					
						// ################################
						// FOR DEVELOPMENT! Uncomment to
						// enable trace comparison.
						// ################################
						
						// 0.5: Replay
						// (REMOVED IN ANDROID)
						
						// 1. We need to set the NMI flag -- heartbeat signal
						if((CPU.total_inst_count & tommy_batch) == tommy_batch ) {
							FleurDeLisDriver.threadFlags |= 0x08; // NMI flag
						}
						
						// 2. Process NMI and IRQ
						if((FleurDeLisDriver.threadFlags & 0x08)!=0) {
							FleurDeLisDriver.threadFlags &= 0xFFF7; // Removed!
							CPU.nmi = false;
							deadlockCounter--;
						} else if(((CPU.regs.ps & 0x4)==0) && 
								((FleurDeLisDriver.threadFlags & 0x10)!=0)) {
							FleurDeLisDriver.threadFlags &= 0xFFEF;
							CPU.irq = false;
							deadlockCounter--;
						}
						
						deadlockCounter++;
						
						
						// 3. Dead lock handler
						//  (Is this called a Watchdog ?)
						if(deadlockCounter==3000) {
							deadlockCounter = 0;
							if((FleurDeLisDriver.threadFlags&0x80)==0) {
								FleurDeLisDriver.checkTimebaseAndEnableIRQnEXIE1();
								if(FleurDeLisDriver.timer0started) {
									FleurDeLisDriver.prevtimer0value += 3;
									if(FleurDeLisDriver.prevtimer0value >= 0xFF) {
										FleurDeLisDriver.prevtimer0value = 0;
										FleurDeLisDriver.turnOff2HzNMIMaskAddIRQFlag();
									}
								} 
							} else { // RESET.
								FleurDeLisDriver.resetCPU();
							}
						} else {
							if(FleurDeLisDriver.timer0started) {
								FleurDeLisDriver.prevtimer0value += 3;
								if(FleurDeLisDriver.prevtimer0value >= 0xFF) {
									FleurDeLisDriver.prevtimer0value = 0;
									FleurDeLisDriver.turnOff2HzNMIMaskAddIRQFlag();
								}
							}
						}
						
						int cycles = CPU.oneInstruction();
						curr_batch_todo -= cycles;
					}
					// End batch, re-charge this batch!!!!
					
					curr_batch_todo = batch_size;
					
					// Update LCD. How to do that?
					for(int i=0; i<1600; i++) {
						lcdbuffer[i] = FleurDeLisDriver.getByte(0x9C0+i);
					}
					host.updateLCD(lcdbuffer, (int) CPU.total_inst_count);
				}
			} catch (Exception e) {
				e.printStackTrace();
				this.stop();
			} finally {
			}
		}
	}
}
