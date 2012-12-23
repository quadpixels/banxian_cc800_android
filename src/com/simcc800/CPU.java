/** Update log goes here!
 * Nov 22: Ah! I have some interrupts!
 *   10:40 PM Start to implement interrupts.
 * Dec 21: Looks like 80% functionalities are working OK.
 *   Now need to move on to perform optimization.
 *   The first thing I can think of is to reduce function invocation overhead,
 *   that is instead of using fancy function objects, use just method calls,
 *   for example, ef_to_af.foo() ------> ef_to_af()
 *   Debug features would have to be preserved, because this is intended to be 
 *     a project for learning.
 */

package com.simcc800;

import com.simcc800.FleurDeLisDriver.BasicAddress;
import com.simcc800.FleurDeLisDriver.IORead;
import com.simcc800.FleurDeLisDriver.IOWrite;

public class CPU {
	public long total_inst_count = 0;
	
	private static final int IO_RANGE = 0x40;
	public FleurDeLisDriver theFleurDeLisDriver;
	public class Regs {
		// Why, Java doesn't support unsigned data types?
		byte a; 
		private byte x;
		private byte y;
		byte ps;
		short pc; // Program Counter.
		private short sp; // Stack pointer.
	}
	
	public Regs regs = new Regs();
	public boolean irq, nmi, wai, stp;
	
	public void cpuInitialize() {
		regs.a = 0; regs.x = 0; regs.y = 0; regs.ps = 0x24;
		regs.pc = theFleurDeLisDriver.getWord(0xFFFC);
		regs.sp = 0x01FF;
		irq = true; nmi = true; wai = false; stp = false;
	}
	
	//   N V 1 B D I Z C   <-- 6502 registers
	//  |7|6|5|4|3|2|1|0   
	private final static byte AF_SIGN = (byte)0x80;
	private final static byte AF_OVERFLOW = (byte)0x40;
	private final static byte AF_RESERVED = (byte)0x20;
	private final static byte AF_BREAK = (byte)0x10;
	private final static byte AF_DECIMAL = (byte)0x08;
	private final static byte AF_INTERRUPT = (byte)0x04;
	private final static byte AF_ZERO = (byte)0x02;
	private final static byte AF_CARRY = (byte)0x01;
	
	// For temporary use!
	private boolean flag_c, flag_n, flag_v, flag_z;
	private int pc,   // Address of instruction
				addr,   // Address of source operand 
				cycles,
				val,
				temp; //
	
	// #######################################
	// Private methods for logging disassembly
	private String regs_log, inst_log, operand_log, machcode_log;
	private short prev_pc;
	private void logRegisters() {
		String cpu_state = Integer.toBinaryString(regs.ps & 0x000000FF);
		StringBuilder sb = new StringBuilder(cpu_state);
		for(int i=cpu_state.length(); i<8; i++) { sb.insert(0, "0"); }
		regs_log = (String.format("%02X %02X %02X %04X %s",
				regs.a,
				regs.x,
				regs.y,
				regs.sp,
				sb.toString()));
	}
	// This method is for debugging purpose only.
	private void logOperandAndMachineCode() {
		StringBuilder sb_operand = new StringBuilder();
		StringBuilder sb_machcode = new StringBuilder();
		/*
		 *  3-byte inst in memory:
		 *  +--------+----+----+
		 *  | OPCode | Hi | Lo |
		 *  +--------+----+----+
		 *  p         p+1  p+2
		 */
		// 1. Log the operand.
		{
			int p = prev_pc + 1;
			if(this_addr_mode.equals("")) operand_log="";
			else {
				if(this_addr_mode.equals("ABS") ||  // The beauty and ugliness of
					this_addr_mode.equals("ZPG")) { // O.O.P. !!!
					while((short)(p&0xFFFF) != regs.pc) {
						int addr = ((int)(p)) & 0xFFFF;
						sb_operand.insert(0, String.format("%02X", theFleurDeLisDriver.getByte(addr)));
						p += 1;
					}
					sb_operand.insert(0, "$");
				} 
				else if(this_addr_mode.equals("IMM")) {
					sb_operand.append("#$");
					int addr = ((int)(p)) & 0xFFFF;
					sb_operand.append(String.format("%02X", theFleurDeLisDriver.getByte(addr)));
				}
				else if(this_addr_mode.equals("REL")) {
					sb_operand.append("$");
					// PC has NOT been incremented by calling ) yet,
					//    that being said, logOperand() must precede )!!!\
					int pc = ((int)p) & 0x0000FFFF;
					int addr = (int)(char)theFleurDeLisDriver.getByte(pc);
					sb_operand.append(String.format("%04X", (regs.pc + addr)&0x0000FFFF));
				}
				else if(this_addr_mode.equals("IABS")) { // JMP $(00BA)
					sb_operand.append("$(");
					int pc = ((int)p) & 0x0000FFFF;
					int addr = (int)(theFleurDeLisDriver.getWord(pc&0xFFFF)&0xFFFF);
					sb_operand.append(String.format("$04X)", addr));
				}
				else if(this_addr_mode.equals("INDY")) { // STA ($08),Y
					sb_operand.append("($");
					int pc = ((int)p) & 0x0000FFFF;
					int addr = (int)(char)theFleurDeLisDriver.getByte(pc) & 0xFF;
					sb_operand.append(String.format("%02X),Y", addr));
				}
				else if(this_addr_mode.equals("ABSX")) { // STA $9999, X
					sb_operand.append("$");
					int addr = (int)(short)theFleurDeLisDriver.getWord(regs.pc&0xFFFF) & 0xFFFF;
					sb_operand.append(String.format("%04X,X", addr));
				}
				else if(this_addr_mode.equals("ABSY")) { // STA $9999, X
					sb_operand.append("$");
					int addr = (int)(short)theFleurDeLisDriver.getWord(p&0xFFFF) & 0xFFFF;
					sb_operand.append(String.format("%04X,Y", addr));
				}
				else if(this_addr_mode.equals("ZPGX")) { // STA $70,X
					sb_operand.append("$");
					int addr = (int)(char)theFleurDeLisDriver.getByte(p&0xFFFF)&0xFF;
					sb_operand.append(String.format("%02X,X", addr));
				}
				operand_log = sb_operand.toString();
			}
		}
		// 2. Log the machine codes
		{
			int p = prev_pc;
			while((short)(p&0xFFFF) != regs.pc) {
				sb_machcode.append(String.format("%02X", theFleurDeLisDriver.getByte(p&0xFFFF)));
				p=p+1;
			}
			machcode_log = sb_machcode.toString();
		}
	}
	private void logInst() {
		StringBuilder sb = new StringBuilder();
		String inst_name = this_inst;
		int idx = inst_name.lastIndexOf('$');
		inst_name = inst_name.substring(idx+1);
		sb.append(inst_name + " ");
		inst_log = sb.toString();
	}
	
	// Caution: dependencies of those macros.
	
	// ###################
	// Read, write, incrementing PC, etc
	// ###################
	void cyc(int _cycles) { cycles += _cycles;}
	
	byte read() {
		if((addr&0x0000FFFF) < IO_RANGE) {
			try {
				IORead ior = (IORead) theFleurDeLisDriver.ioread[addr];
				return ior.foo(addr);
			} catch (NullPointerException e) {
				System.err.println(String.format("IO Read ADDR=%08X", addr));
				throw e;
			}
		} else {
			return theFleurDeLisDriver.getByte(addr&0x0000FFFF);
		}
	}
	
	void write(byte data) {
		if((addr&0x0000FFFF) < IO_RANGE) {
			IOWrite iow = (IOWrite) theFleurDeLisDriver.iowrite[addr&0x0000FFFF];
			iow.foo(addr&0x0000FFFF, data);
		} else {
			theFleurDeLisDriver.writeByte(addr&0x0000FFFF, data);
		}
	}
	
	void push(byte data) {
		int addr = regs.sp & 0x000001FF;
		theFleurDeLisDriver.writeByte(addr, data);
		addr = addr - 1;
		if(addr < 0x0100) addr = 0x01FF;
		regs.sp = (short)addr;
	}
	
	private byte pop() {
		int addr = ((int)regs.sp) & 0x000001FF /* + 1 */; // Pay attention to opr priority.
		addr = addr + 1;
		if(addr > 0x01FF) addr = 0x0100;
		byte ret = theFleurDeLisDriver.getByte(addr);
		regs.sp = (short)addr;
		return ret;
	}
	
	// ###################################
	// Flags
	// ###################################
	void af_to_ef() {
		flag_c = ((regs.ps & AF_CARRY)==AF_CARRY);
		flag_n = ((regs.ps & AF_SIGN)==AF_SIGN);
		flag_v = ((regs.ps & AF_OVERFLOW)==AF_OVERFLOW);
		flag_z = ((regs.ps & AF_ZERO) == AF_ZERO);
	}
	
	private void ef_to_af() {
		regs.ps = (byte) (regs.ps & ~(AF_CARRY | AF_SIGN | AF_OVERFLOW 
				| AF_ZERO));
		if(flag_c == true) regs.ps |= AF_CARRY;
		if(flag_n == true) regs.ps |= AF_SIGN;
		if(flag_v == true) regs.ps |= AF_OVERFLOW;
		if(flag_z == true) regs.ps |= AF_ZERO;
	}
	
	byte to_bin(byte b) {
		return (byte)(((b) >> 4)*10 + ((b) & 0x0F));
	}
	
	final class TOBCD {
		byte foo(byte b) {
			return (byte)(((((b)/10) % 10) << 4) | ((b) % 10));
		}
	} final TOBCD to_bcd = new TOBCD();
	
	void setnz(byte a) {
		flag_n = ((a & (byte)0x80) == (byte)0x80);
		flag_z = (a == 0x00);
	}
	
	
	// #################
	// Addressing modes
	// #################
	private String this_addr_mode;
	
	private void am_abs() {
		pc = ((int)regs.pc) & 0x0000FFFF;
		addr = theFleurDeLisDriver.getWord(pc);
		regs.pc = (short)(pc+2);
	}
	
	
	private void am_absx() {
		addr = theFleurDeLisDriver.getWord(regs.pc & 0xFFFF);
		addr += (short)(regs.x & 0xFF);
		regs.pc += 2;
	}
	
	private void am_absy() {
		addr = theFleurDeLisDriver.getWord(regs.pc & 0xFFFF);
		addr += (short)(regs.y & 0xFF);
		regs.pc += 2;
	}
	
	private void am_iabs() {
		int star_pc = theFleurDeLisDriver.getWord(regs.pc & 0xFFFF);
		addr = theFleurDeLisDriver.getWord(star_pc & 0xFFFF);
		regs.pc += 2;
	}
	
	private void am_indx() {
		int k = (int)(theFleurDeLisDriver.getByte(regs.pc&0xFFFF)&0xFF
				+ (int)(regs.x&0xFF))&0xFFFF;
		addr = theFleurDeLisDriver.getWord(k);
		regs.pc++;
	}
	
	private void am_indy() {
		int k = ((int)(regs.pc)) & 0x0000FFFF;
		addr = theFleurDeLisDriver.getWord(
				theFleurDeLisDriver.getByte(k)&0x000000FF)
				+ (short)(regs.y & 0xFF);
		addr = addr & 0x0000FFFF;
		regs.pc++;
	}
	
	private void am_zpg() {
		int pc = ((int)regs.pc) & 0x0000FFFF;
		addr = theFleurDeLisDriver.getByte(pc);
		addr &= 0x000000FF; // Zero page, addr should be 00 to FF /* &=0000FFFF */
		regs.pc += 1;
	}
	
	private void am_zpgx() {
		int reg_plus_x = (regs.pc&0xFFFF);
		addr = theFleurDeLisDriver.getByte(reg_plus_x&0xFFFF) + (regs.x&0xFF);
		addr &= 0xFF;
		regs.pc+=1;
	}	
	
	private void am_zpgy() {
		int reg_plus_y = regs.pc&0xFFFF;
		addr = theFleurDeLisDriver.getByte(reg_plus_y) + (regs.y&0xFF);
		addr &= 0xFF;
		regs.pc+=1;
	}
	
	private void am_rel() {
		pc = ((int)regs.pc) & 0x0000FFFF;
		addr = (int)(char)theFleurDeLisDriver.getByte(pc);
		addr &= 0x0000FFFF;
		regs.pc = (short)(pc+1);
	}
	
	private void am_imm() {
		addr = ((int)regs.pc++) & 0x0000FFFF;
	}
	
	private void am_null() {
	}
	
	// #################
	// Instructions
	// #################
	private String this_inst;
	
	private void adc() {
		temp = (read()) & 0x000000FF; 
		if((regs.ps & AF_DECIMAL)!=0) {
			val = to_bin(regs.a) + to_bin((byte)temp)
					+ (flag_c != false ? 1 : 0);
			flag_c = (val > 99);
			regs.a = to_bcd.foo((byte)val); cyc(1);
			setnz(regs.a);
		} else {
			val = (regs.a&0xFF) + temp + (flag_c != false?1:0);
			flag_c = (val>0xFF);
			flag_v = (((regs.a & 0x80) == (temp &0x80)) &&
					((regs.a & 0x80)!=(val&0x80)));
			regs.a = (byte)(val & 0xFF);
			setnz(regs.a);
		}
	}
	
	private void bcc() {
		if(flag_c == false) { regs.pc += addr; cyc(1); }
	}
	
	private void and() {
		regs.a &= read(); setnz(regs.a);
	}
	
	private void asl() {
		val = (read() & 0x000000FF) << 1;
		flag_c = (val > 0x000000FF);
		setnz((byte)val);
		write((byte)val);
	}
	
	private void asla() {
		val = (regs.a & 0x000000FF) << 1;
		flag_c = (val > 0xFF); setnz((byte)val);
		regs.a = (byte)val;
	}
	
	private void bcs() {
		if(flag_c == true) {regs.pc += addr; cyc(1); }
	}
	
	private void beq() {
		if(flag_z==true) {regs.pc += addr; cyc(1);}
	}
	
	private void bit() {
		val = read() & 0x000000FF;
		flag_z = !((regs.a & val)!=0);
		flag_n = ((val & 0x80)!=0);
		flag_v = ((val & 0x40)!=0);
	}
	
	private void bmi() {
		if(flag_n==true) {regs.pc += addr; cyc(1); }
	}
	
	private void bpl() {
		if(flag_n == false) { regs.pc += addr; cyc(1); }
	}
	
	private void brk() {
		regs.pc += 1;
		push((byte)(regs.pc >> 8));
		push((byte)(regs.pc & 0xFF));
		ef_to_af();
		regs.ps |= AF_BREAK;
		push(regs.ps);
		regs.ps |= AF_INTERRUPT;
		regs.pc = theFleurDeLisDriver.getWord(0xFFFE);
	}
	
	private void bne() {
		if(flag_z == false) {regs.pc += addr; cyc(1); }
	}
	
	private void bvs() {
		if(flag_v==true) { regs.pc += addr; cyc(1); }
	}
	
	private void clc() {
		flag_c = false;
		regs.ps &= 0xFE;
	}
	
	private void cli() {
		regs.ps &= ~AF_INTERRUPT;
	}
	
	private void cmp() {
		val = read(); 
		int lhs = (regs.a & 0x000000FF); // regs.a is a BYTE
		int rhs = (val    & 0x000000FF); // val is a WORD.  Both are unsigned.
		flag_c = (lhs >= rhs); 
		val = lhs - rhs;
		setnz((byte)val);
	}
	
	private void cpx() {
		val = read() & 0xFF;
		flag_c = ((regs.x & 0xFF) >= val);
		val = ((regs.x & 0xFF) - (val & 0xFF));
		setnz((byte)val);
	}
	
	private void cpy() {
		val = read() & 0xFF;
		flag_c = ((regs.y & 0xFF) >= val);
		val = ((regs.y & 0xFF) - (val & 0xFF));
		setnz((byte)val);
	}
	
	private void dec() {
		val = read() - 1;
		setnz((byte)val);
		write((byte)val);
	}
	
	private void dex() {
		regs.x -= 1; setnz(regs.x);
	}
	
	private void dey() {
		regs.y -= 1; setnz(regs.y);
	}
	
	private void eor() {
		regs.a ^= ((byte)read());
		setnz(regs.a);
	}

	private void inc() {
		val = read() + 1;
		setnz((byte)val);
		write((byte)val); 
	}
	private void inx() {regs.x += 1; setnz(regs.x);}
	private void iny() {regs.y += 1; setnz(regs.y);}
	private void jmp() {regs.pc = (short)addr;}
	private void jsr() {
		regs.pc -= 1;
		push((byte) (regs.pc >> 8));
		push((byte) (regs.pc & 0xFF));
		regs.pc = (short)addr;
	}
	private void lda() {
		regs.a = read();
		setnz(regs.a);
		this_inst = "LDA";
	}
	private void bvc() {
		if(flag_v==false) regs.pc += addr; cyc(1);
		this_inst = "BVC";
	}
	
	private void clv() { flag_v=false; this_inst="CLV"; }
	
	private void ldx() {
		regs.x = read();
		setnz(regs.x); 
		this_inst="LDX";
	}

	private void ldy() {
		regs.y = read();
		setnz(regs.y);
	}
	
	private void lsr() {
		val = read()&0xFF;
		flag_c = ((val&1)!=0);
		flag_n = false;
		val >>= 1;
		flag_z = ((val&0xFF)==0);
		write((byte)val);
	}
	
	private void lsra() {
		flag_c = ((regs.a & 1)!=0);
		flag_n = false;
		regs.a = (byte)((regs.a&0xFF)>>1);
		flag_z = ((regs.a&0xFF)==0);
	}
	
	private void nop() {}
	private void ora() {
		regs.a |= read();
		setnz(regs.a);
	}
	
	private void pha() {
		push(regs.a);
	}
	private void pla() {
		regs.a = pop(); setnz(regs.a);
	}
	
	private void plp() {
		regs.ps = pop(); af_to_ef();
	}
	
	private void php() {
		ef_to_af();
		regs.ps |= AF_RESERVED;
		push(regs.ps);
	}

	private void rola() {
		val = regs.a << 1 | (flag_c!=false?1:0);
		val &= 0xFFFF;
		flag_c = (val > 0xFF);
		regs.a = (byte)(val&0xFF);
		setnz(regs.a);
	}
	
	private void rol() {
		temp = read() & 0xFF;
		val = (temp << 1) | (flag_c != false?1:0);
		flag_c = (val > 0xFF);
		setnz((byte)val);
		write((byte)val);
	}
	
	private void ror() {
		temp = (read()&0xFF);
		val = (temp >> 1) | (flag_c ? 0x80 : 0x00);
		flag_c = (temp & 1)==1;
		setnz((byte)val);
		write((byte)val);
	}
	
	private void rora() {
		val = (((int)(regs.a&0xFF)) >> 1) | (flag_c ? 0x80 : 0x00);
		flag_c = (regs.a & 1)==1;
		regs.a = (byte)(val & 0xFF);
		setnz(regs.a);	
	}
	
	private void rti() {
		regs.ps = pop(); 
		cli(); irq = true;
		af_to_ef();
		regs.pc = (short)(pop()&0x00FF);
		regs.pc |= (pop() << 8);
	}
	
	
	private void rts() {
		regs.pc = (short)(pop() & 0x00FF);
		regs.pc |= (short)((pop() << 8)&0x0000FF00); regs.pc += 1;
	}

	private void sbc() {
		temp = (read()) & 0x000000FF;
		if((regs.ps & AF_DECIMAL)!=0) {
			val = to_bin(regs.a) - to_bin((byte)temp) - (flag_c==true?0:1);
			val = val & 0x0000FFFF;
			flag_c = (val < 0x8000); // type of val is WORD.
			regs.a = to_bcd.foo((byte)val);
			setnz(regs.a);
			cyc(1);
		} else {
			temp = (read()) & 0x000000FF;
			val = (regs.a&0xFF) - (temp&0xFF) - (flag_c==true?0:1);
			val = val & 0x0000FFFF;
			flag_c = (val < 0x00008000);
			flag_v = (((regs.a & 0x80)!=(temp & 0x80)) &&
						((regs.a & 0x80)!=(val&0x80)));
			regs.a = (byte)(val&0xFF);
			setnz(regs.a);
		}
	}
	
	private void sec() {flag_c = true;}
	private void sed() {regs.ps |= AF_DECIMAL;}
	private void sta() {write(regs.a);}
	private void stx() {write(regs.x);}
	private void sty() {write(regs.y);}
	private void sei() {regs.ps |= (byte)AF_INTERRUPT;}
	private void tax() {regs.x = regs.a;
		setnz(regs.x);
	}
	private void tsx() {regs.x = (byte)(regs.sp & 0xFF);
		setnz(regs.x);
	}
	
	private void txa() {regs.a = regs.x; setnz(regs.a);}
	private void txs() {regs.sp = (short) ((short)0x0100 | ((short)regs.x&0xFF));}
	private void tya() {regs.a = regs.y; setnz(regs.a);}
	private void tay() {regs.y = regs.a; setnz(regs.y);}
	private void cld() {regs.ps &= (byte)~AF_DECIMAL;}
	
	
	// ###################################
	// Interrupts!
	// ###################################
	
	private void nmi() {
		if(wai==true) { regs.pc++; wai = false; }
		push((byte)(regs.pc >> 8));
		push((byte)(regs.pc & 0xFF));
		sei();
		ef_to_af();
		push(regs.ps);
		regs.pc = theFleurDeLisDriver.getWord(0xFFFA);
		nmi = true;
		cyc(7);
	}
	
	private void irq() {
		if(wai==true) { regs.pc++; wai = false; }
		if((regs.ps & AF_INTERRUPT)==0) {
			push((byte)(regs.pc >> 8));
			push((byte)(regs.pc & 0xFF));
			ef_to_af();
			regs.ps &= ~AF_BREAK;
			push(regs.ps);
			regs.pc = theFleurDeLisDriver.getWord(0xFFFE);
			cyc(7);
			sei();
		}
	}
	
	// Totally copied from BanXian's code.
	final public int oneInstruction() throws Exception {
		
		cycles = 0;
		
		af_to_ef();
		
		pc = ((int)regs.pc) & 0x0000FFFF; // Always keep it unsigned
		regs.pc += 1; // So that we don't mess up with signed/unsigned
		byte opcode = theFleurDeLisDriver.getByte(pc);
		switch(opcode) {
		case (byte)0x00: // BRK
			am_null(); brk();cyc(7);break;
		case (byte)0x01:
			am_indx(); ora(); cyc(6); break;
		case (byte)0x03: // INVALID1
			am_null(); cyc(1); break;
		case (byte)0x05: // ORA $12
			am_zpg(); ora(); cyc(3); break;
		case (byte)0x06: // Zpg ASL; ASL $56
			am_zpg(); asl(); cyc(5); break;
		case (byte)0x08: // PHP
			am_null(); php(); cyc(3); break;
		case (byte)0x09: // ORA #$12
			am_imm(); ora(); cyc(2); break;
		case (byte)0x0A:
			am_null(); asla();cyc(2);break;
		case (byte)0x0D:
			am_abs(); ora(); cyc(4); break;
		case (byte)0x0E:
			am_abs(); asl(); cyc(6); break;
		case (byte)0x10: // 
			am_rel(); bpl(); cyc(2); break;
		case (byte)0x11:
			am_indy(); ora(); cyc(4); break;
		case (byte)0x15:
			am_zpgx(); ora(); cyc(4); break;
		case (byte)0x18:
			am_null(); clc(); cyc(2); break;
		case (byte)0x19:
			am_absy(); ora(); cyc(4); break;
		case (byte)0x1D: 
			am_absx(); ora(); cyc(4); break;
		case (byte)0x1E:
			am_absx(); asl(); cyc(6); break;
		case (byte)0x20: // JSR $1234
			am_abs(); jsr(); cyc(6); break;
		case (byte)0x21:
			am_indx();and(); cyc(6); break;
		case (byte)0x24: // BIT $63
			am_zpg(); bit(); cyc(3); break;
		case (byte)0x25: // ZPG AND
			am_zpg(); and(); cyc(3); break;
		case (byte)0x26: // ROL $60
			am_zpg(); rol(); cyc(5); break;
		case (byte)0x28: // PLP
			am_null(); plp(); cyc(4); break;
		case (byte)0x29: // Imm AND
			am_imm(); and(); cyc(2); break;
		case (byte)0x2A:
			am_null(); rola(); cyc(2); break;
		case (byte)0x2C: // Abs BIT
			am_abs(); bit(); cyc(4); break;
		case (byte)0x2D:
			am_abs(); and(); cyc(4); break;
		case (byte)0x2E: // ROL
			am_abs(); rol(); cyc(6); break;
		case (byte)0x30: // BMI
			am_rel(); bmi(); cyc(2); break;
		case (byte)0x31:
			am_indy(); and(); cyc(5); break;
		case (byte)0x35:
			am_zpgx(); and(); cyc(4); break;
		case (byte)0x38:
			am_null(); sec(); cyc(2); break;
		case (byte)0x39:
			am_absy(); and(); cyc(4); break;
		case (byte)0x3D:
			am_absx(); and(); cyc(4); break;
		case (byte)0x3E:
			am_absx(); rol(); cyc(6); break;
		case (byte)0x40:
			am_null(); rti(); cyc(6); break;
		case (byte)0x45:
			am_zpg(); eor(); cyc(3); break;
		case (byte)0x46:
			am_zpg(); lsr(); cyc(5); break;
		case (byte)0x48:
			am_null(); pha(); cyc(3); break;
		case (byte)0x49:
			am_imm(); eor(); cyc(3); break;
		case (byte)0x4A:
			am_null(); lsra(); cyc(2); break;
		case (byte)0x4C: // Abs JMP; e.g. JMP $E77E
			am_abs(); jmp(); cyc(3); break;
		case (byte)0x4D:
			am_abs(); eor(); cyc(4); break;
		case (byte)0x4E:
			am_abs(); lsr(); cyc(6); break;
		case (byte)0x50:
			am_rel(); bvc(); cyc(1); break;
		case (byte)0x51:
			am_indy(); eor(); cyc(5); break;
		case (byte)0x55:
			am_zpgx(); eor(); cyc(4); break;
		case (byte)0x56:
			am_zpgx(); lsr(); cyc(6); break;
		case (byte)0x58:
			am_null(); cli(); cyc(2); break;
		case (byte)0x59:
			am_absy(); eor(); cyc(4); break;
		case (byte)0x60: // RTS
			am_null(); rts(); cyc(6); break;
		case (byte)0x61:
			am_indx(); adc(); cyc(6); break;
		case (byte)0x65: // ADC $5F
			am_zpg(); adc(); cyc(3); break;
		case (byte)0x66:
			am_zpg(); ror(); cyc(5); break;
		case (byte)0x68:
			am_null(); pla(); cyc(4); break;
		case (byte)0x69:
			am_imm(); adc(); cyc(2); break;
		case (byte)0x6A: // RORA
			am_null(); rora(); cyc(2); break;
		case (byte)0x6C:
			am_iabs();jmp(); cyc(6); break;
		case (byte)0x6D:
			am_abs(); adc(); cyc(4); break;
		case (byte)0x6E:
			am_abs(); ror(); cyc(6); break;
		case (byte)0x70:
			am_rel(); bvs(); cyc(2); break;
		case (byte)0x71:
			am_indy();adc(); cyc(5); break;
		case (byte)0x75:
			am_zpgx(); adc(); cyc(4); break;
		case (byte)0x76:
			am_zpgx(); ror(); cyc(6); break;
		case (byte)0x78: // SEI; sets interruption flag
			am_null(); sei(); cyc(2); break;
		case (byte)0x79:
			am_absy();adc();cyc(4); break;
		case (byte)0x7D:
			am_absx(); adc(); cyc(4); break;
		case (byte)0x7E:
			am_absx(); ror(); cyc(6); break;
		case (byte)0x81:
			am_indx(); sta(); cyc(6); break;
		case (byte)0x84: // ZPG STY
			am_zpg(); sty(); cyc(3); break;
		case (byte)0x85: // Zpg STA; e.g. STA $0A
			am_zpg(); sta(); cyc(3); break;
		case (byte)0x86: // ZPG STX
			am_zpg(); stx(); cyc(3); break;
		case (byte)0x88: // DEY
			am_null(); dey(); cyc(2); break;
		case (byte)0x8A: // TXA
			am_null(); txa(); cyc(2); break;
		case (byte)0x8C:
			am_abs(); sty(); cyc(4); break;
		case (byte)0x8D: // Abs STA; e.g. STA $0489
			am_abs(); sta(); cyc(4); break;
		case (byte)0x8E:
			am_abs(); stx(); cyc(4); break;
		case (byte)0x90: // BCC
			am_rel(); bcc(); cyc(2); break;
		case (byte)0x91: // INDY STA
			am_indy();sta(); cyc(6); break;
		case (byte)0x94: // ZPGX STY
			am_zpgx(); sty(); cyc(4); break;
		case (byte)0x95:
			am_zpgx(); sta(); cyc(4); break;
		case (byte)0x98:
			am_null(); tya(); cyc(2); break;
		case (byte)0x99:
			am_absy();sta(); cyc(5); break;
		case (byte)0x9A:
			am_null(); txs(); cyc(2); break;
		case (byte)0x9D:
			am_absx();sta(); cyc(5); break;
		case (byte)0xA0: // LDY #$08
			am_imm(); ldy(); cyc(2); break;
		case (byte)0xA1: // INDX LDA
			am_indx(); lda(); cyc(6); break;
		case (byte)0xA2: // Imm LDX
			am_imm(); ldx(); cyc(2); break;
		case (byte)0xA4:
			am_zpg(); ldy(); cyc(3); break;
		case (byte)0xA5:
			am_zpg(); lda(); ; cyc(3); break;
		case (byte)0xA6:
			am_zpg(); ldx(); cyc(3); break;
		case (byte)0xA8:
			am_null();  tay(); cyc(2); break;
		case (byte)0xA9: // Imm LDA; e.g. LDA #$00
			am_imm(); lda(); cyc(2); break;
		case (byte)0xAA:
			am_null(); tax(); cyc(2); break;
		case (byte)0xAC: // Abs LDY
			am_abs(); ldy(); cyc(4); break;
		case (byte)0xAD: // Abs LDA; e.g. LDA $04BB
			am_abs(); lda(); cyc(4); break;
		case (byte)0xAE: // Abs LDX
			am_abs(); ldx(); cyc(4); break;
		case (byte)0xB0: // BCS
			am_rel(); bcs(); cyc(2); break;
		case (byte)0xB1: // INDY LDA
			am_indy(); lda(); cyc(5); break;
		case (byte)0xB4:
			am_zpgx(); ldy(); cyc(4); break;
		case (byte)0xB5:
			am_zpgx(); lda(); cyc(4); break;
		case (byte)0xB6:
			am_zpgy(); ldx(); cyc(4); break;
		case (byte)0xB8:
			am_null(); clv(); cyc(1); break;
		case (byte)0xB9: // LDA $1000, Y
			am_absy(); lda(); cyc(4); break;
		case (byte)0xBA: // TSX
			am_null();  tsx(); cyc(4); break;
		case (byte)0xBC:
			am_absx(); ldy(); cyc(4); break;
		case (byte)0xBD: // ABSX LDA
			am_absx(); lda(); cyc(4); break;
		case (byte)0xBE:
			am_absy(); ldx(); cyc(4); break;
		case (byte)0xC0: // CPY #$08
			am_imm(); cpy(); cyc(2); break;
		case (byte)0xC1:
			am_indx(); cmp(); cyc(6); break;
		case (byte)0xC4: // CPY $62
			am_zpg(); cpy(); cyc(3); break;
		case (byte)0xC5: // CMP $61
			am_zpg(); cmp(); cyc(3); break;
		case (byte)0xC6: // Zpg DEC; e.g. DEC $10
			am_zpg(); dec(); cyc(5); break;
		case (byte)0xC8: // INY
			am_null(); iny(); cyc(2); break;
		case (byte)0xC9: // IMM CMP
			am_imm(); cmp(); cyc(2); break;
		case (byte)0xCA: // DEX
			am_null(); dex(); cyc(2); break;
		case (byte)0xCC:
			am_abs(); cpy(); cyc(4); break;
		case (byte)0xCD:
			am_abs(); cmp(); cyc(4); break;
		case (byte)0xCE:
			am_abs(); dec(); cyc(6); break;
		case (byte)0xD0:
			am_rel(); bne(); cyc(2); break;
		case (byte)0xD1:
			am_indy(); cmp(); cyc(5); break;
		case (byte)0xD5:
			am_zpgx(); cmp(); cyc(4); break;
		case (byte)0xD6:
			am_zpgx(); dec(); cyc(6); break;
		case (byte)0xD8: // CLD
			am_null(); cld(); cyc(2); break;
		case (byte)0xD9: //
			am_absy(); cmp(); cyc(4); break;
		case (byte)0xDD:
			am_absx(); cmp(); cyc(4); break;
		case (byte)0xDE:
			am_absx(); dec(); cyc(6); break;
		case (byte)0xE0:
			am_imm(); cpx(); cyc(2); break;
		case (byte)0xE1:
			am_indx();sbc(); cyc(6); break;
		case (byte)0xE4:
			am_zpg(); cpx(); cyc(3); break;
		case (byte)0xE5: // SBC $4E
			am_zpg(); sbc(); cyc(3); break;
		case (byte)0xE6:
			am_zpg(); inc(); cyc(5); break;
		case (byte)0xE8:
			am_null();  inx(); cyc(2); break;
		case (byte)0xE9:
			am_imm(); sbc(); cyc(2); break;
		case (byte)0xEA: // NOP
			am_null(); nop(); cyc(2); break;
		case (byte)0xEC:
			am_abs(); cpx(); cyc(4); break;
		case (byte)0xED:
			am_abs(); sbc(); cyc(4); break;
		case (byte)0xEE:
			am_abs(); inc(); cyc(5); break;
		case (byte)0xF0:
			am_rel(); beq(); cyc(2); break;
		case (byte)0xF1:
			am_indy(); sbc(); cyc(5); break;
		case (byte)0xF5:
			am_zpgx(); sbc(); cyc(4); break;
		case (byte)0xF6:
			am_zpgx(); inc(); cyc(6); break;
		case (byte)0xF9:
			am_absy();sbc(); cyc(4); break;
		case (byte)0xFD:
			am_absx(); sbc(); cyc(4); break;
		case (byte)0xFE:
			am_absx(); inc(); cyc(6); break;
		default:
			throw new Exception();
		}
		
		if(stp == false) {
			if(nmi == false) {
				nmi();
			}
			if(irq == false) { // TODO IRQ
				irq();
			}
		}
		
		ef_to_af();
		total_inst_count++;
		
		return cycles;
	}
}
