package com.simcc800;

public class EmulatorThread extends Thread {
	int deadlockCounter = 0;
	private static final int min_cycles_per_nmi = 3686400/2;
	private static final int batches_per_nmi = 12;
	private static final int SPEEDUP = 1;
	public static final int BATCH_SIZE = min_cycles_per_nmi / batches_per_nmi / SPEEDUP; // = 76800
	private long last_millis = 0, last_nmi_millis = 0;
	MainActivity host;
	private boolean is_running = true;
	long curr_millis, delta_millis=0;
	private boolean is_too_slow = false;
	public boolean isTooSlow() { return is_too_slow;}
	public static boolean needs_sync_machine_type = true;
	
	// Performance Counters
	// Group means frame group, the average of a window of a certain width.
	long host_group_millis=0, wqx_group_millis=0, total_cycles;
	int percent; String text_instcnt = "Hello world!";
	
	// Called per batch, so number of cycles is the batch size
	private void updatePerformanceCounters(/*int num_cycles_delta,*/ long host_millis_delta) {
		total_cycles += BATCH_SIZE;
		wqx_group_millis += (1000*BATCH_SIZE)/3686400; // Cycles == Milliseconds
		host_group_millis += host_millis_delta;
		if(host_group_millis>0) {
			percent = (int) (100 * wqx_group_millis / host_group_millis);
			if(host_group_millis > 10000) {
				host_group_millis /= 10;wqx_group_millis /= 10; // THe window width is 10 seconds.
			}
		}
		text_instcnt = String.format("Insts/Cycles/CPI: %d/%d/%.2f, speed=%d%%", 
				CPU.total_inst_count, total_cycles, 1.0*total_cycles/CPU.total_inst_count, percent);
	}
	
	public String getPerformanceString() {
		return text_instcnt;
	}
	
	// Must construct CPU and Driver first then construct this thread.
	public EmulatorThread(MainActivity _host) {
		host = _host;
	}
	
	public void setIsRunning(boolean _is_running) {
		is_running = _is_running;
	}
	
	public int num_batches = 0;
	
	@Override
	public	void run() {
		// 11-16 Still under construction.
		{
			int curr_batch_todo = BATCH_SIZE;
			int next_nmi_batches_todo = batches_per_nmi;
			last_millis = System.currentTimeMillis();
			try {
				while(true) {
					if(is_running==false) {
						Thread.sleep(1000);
						continue;
					}
					curr_millis = System.currentTimeMillis();
					delta_millis = curr_millis - last_millis;
					last_millis = curr_millis;
					
					next_nmi_batches_todo--;
					if(curr_millis - last_nmi_millis > 500) {
						last_nmi_millis = curr_millis;
						if(next_nmi_batches_todo>0) { // This is a rather subjective criterion!!
							is_too_slow = true;
						} else {
							is_too_slow = false;
						}
						FleurDeLisDriver.threadFlags |= 0x08; // NMI flag;
						if(needs_sync_machine_type && CPU.total_inst_count>200000) { // Ugly method!
							FleurDeLisDriver.syncMachineTime();
							needs_sync_machine_type=false;
						}
					}
					/*
					if(curr_millis - last_millis > 500) {
						if(next_nmi_cycles_todo > 0) {
							is_too_slow=true;
						} else {
							is_too_slow = false;
							FleurDeLisDriver.threadFlags |= 0x08; // NMI flag
							next_nmi_cycles_todo = min_cycles_per_nmi;
						}
					}
					*/
					// Execute a BATCH of insts, then update LCD!
					while(curr_batch_todo > 0) {
					
						// ################################
						// FOR DEVELOPMENT! Uncomment to
						// enable trace comparison.
						// ################################
						
						// 0.5: Replay
						// (REMOVED IN ANDROID)
						
						// 1. We need to set the NMI flag -- heartbeat signal
//						if((CPU.total_inst_count&262143)==262143) {
	//						FleurDeLisDriver.threadFlags |= 0x08; // NMI flag;
		//				}
						
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
					
					curr_batch_todo = BATCH_SIZE;
					num_batches++;
					
					// Update LCD. How to do that?
					// Removed on 12-24; updating LCD should not take up the resources of the emulation thread
					// should be handled by another thread
//					for(int i=0; i<1600; i++) {
//						lcdbuffer[i] = FleurDeLisDriver.fixedram0000[0x9C0+i];
//					}
					updatePerformanceCounters(delta_millis);
				}
			} catch (Exception e) {
				e.printStackTrace();
				this.stop();
			} finally {
			}
		}
	}
}
