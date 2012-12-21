package com.simcc800;

import org.quadpixels.FleurDeLisDriver;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.*;

public class WQXView extends View {
	
	public WQXView(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if(event.getAction() == MotionEvent.ACTION_UP) {
					on_touch(-1,-1,false);
				}else if (event.getAction()==MotionEvent.ACTION_DOWN){
					on_touch(event.getX(), event.getY(), true);
				}
				return true;
			}
		});
	}
	public FleurDeLisDriver fleurDeLisDriver;

	byte[] buf;
	int num_insts = 0;
	final Rect bounds = new Rect();
	Paint px_paint = new Paint();
	Paint text_paint = new Paint();
	int keyboard_padding_top;
	int mtx_deltax, mtx_deltay;
	
	final static String keys_sz[] = {
		" ", " ", "ON", " ", " ", " ", " ", " ",
		"F5", "F6", "F7", "F8", "F9", "F10", "F11", " ",
		"ÇóÖú", "Shift", "Caps", "Ìø³ö", "0", ".", "=", "¡û",
		"Z", "X", "C", "V", "B", "N", "M", "PgUp",
		"A", "S", "D", "F", "G", "H", "J", "K",
		"Q", "W", "E", "R", "T", "Y", "U", "I",
		"O", "L", "¡ü", "¡ý", "P", "ÊäÈë", "PgDn", "¡ú",
		" ", " ", "F1", "F2", "F3", "F4", " ", " "
	};
	final char keys_state[] = {
			2,2,0,2,2,2,2,2,
			0,0,0,0,0,0,0,2,
			0,0,0,0,0,0,0,0,
			0,0,0,0,0,0,0,0,
			0,0,0,0,0,0,0,0,
			0,0,0,0,0,0,0,0,
			0,0,0,0,0,0,0,0,
			2,2,0,0,0,0,2,2
	};
	
	final Paint p_mtx = new Paint();
	final Paint p_mtx_txt = new Paint();
	private void paintKeyMatrix(Canvas c, float zoom) {
		final int W = this.getWidth();
		final int H = this.getHeight();
		
		p_mtx.setStyle(Style.FILL);
		p_mtx_txt.setColor(Color.WHITE);
		p_mtx_txt.setFlags(Paint.ANTI_ALIAS_FLAG);
		p_mtx_txt.setTextAlign(Align.CENTER);
		p_mtx_txt.setTextSize(zoom/2*12);
		
		mtx_deltax = W/8;
		mtx_deltay = (H-keyboard_padding_top)/8;
		for(int row=0; row<8; row++) {
			for(int col=0; col<8; col++) {
				int keyidx = row*8+col;
				char ks = keys_state[keyidx];
				if(ks==2) continue;
				if(ks==0) 
					p_mtx.setColor(Color.GRAY);
				else p_mtx.setColor(Color.BLUE);
				String s = keys_sz[keyidx];
				float dx = mtx_deltax * col, dy = mtx_deltay * row + keyboard_padding_top;
				c.drawRect(dx, dy, dx+mtx_deltax-2, dy+mtx_deltay-2, p_mtx);
				c.drawText(s, dx+mtx_deltax/2, dy+mtx_deltay/2, p_mtx_txt);
			}
		}
	}

	int last_key_col;

	int last_key_row;
	void on_touch(float x, float y, boolean is_down) {
		if(mtx_deltax==0||mtx_deltay==0) return;
		if(x==-1 && y==-1 && is_down==false) {
			keys_state[last_key_row*8+last_key_col]=0;
			fleurDeLisDriver.keymatrixChange(last_key_row, last_key_col, false);
			return;
		}
		int col = (int) (x/mtx_deltax);
		int row = (int) ((y-keyboard_padding_top)/mtx_deltay);
		int keyidx = row*8+col;
		if(keys_state[keyidx]==2) return;
		if(is_down) {
			keys_state[keyidx] = 1;
			fleurDeLisDriver.keymatrixChange(row, col, true);
			last_key_row = row; last_key_col = col;
		}
	}
	
	public void update(byte[] buf, int num_insts) {
		this.num_insts = num_insts;
		this.buf = buf;
		postInvalidate();
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		final int W = this.getWidth();
		final int H = this.getHeight();
		final float zoom = W/160;
		
		text_paint.setTextAlign(Align.LEFT);
		
		if(buf==null) {
			text_paint.setTextAlign(Align.CENTER);
			canvas.drawText("Ah! Perhaps you are in preview mode. Buf is null.", W/2, H/2, text_paint);
			return;
		}
		
		String text_instcnt = "Inst count: "+num_insts;
		{
			// Inst Count
			text_paint.getTextBounds(text_instcnt, 0, text_instcnt.length(), bounds);
			text_paint.setColor(Color.DKGRAY);
			final int y = (int) (80*zoom + bounds.height());
			keyboard_padding_top = (int) (80*zoom + text_paint.getTextSize());
			canvas.drawText(text_instcnt, 0, y, text_paint);
		}
		
		px_paint.setStyle(Style.FILL);
		int idx = 0, i = 0, j = 0;
		boolean curr_black = false;
		while(idx < 1600) {
			for(int bid=0; bid<8; bid++) { // bid = bit id
				byte mask =	 (byte)(1 << (7-bid));
				if((buf[idx] & mask)!=0) {
					curr_black = true;
				} else curr_black = false;
				
				if(curr_black==true) {
					px_paint.setColor(Color.BLACK);
				} else px_paint.setColor(Color.LTGRAY);
				canvas.drawRect(i*zoom, j*zoom, i*zoom+zoom, j*zoom+zoom, px_paint);
				i=i+1;
				if(i==160) { i=0; j+=1; }
			}
			idx++;
		}
		
		paintKeyMatrix(canvas, zoom);
	}
}
