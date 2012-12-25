package com.simcc800;


import java.nio.IntBuffer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.view.*;

public class WQXView extends View {
	MainActivity host;
	private int zoom;
	private Bitmap mBitmap;
	private Matrix mScaleMatrix, mTranslateMatrix;
	private int[] mScreenPixelData;
	
	public WQXView(Context context, AttributeSet attrs) {
		super(context, attrs);
		mBitmap = Bitmap.createBitmap(160, 80, Bitmap.Config.ARGB_8888);
		mScreenPixelData = new int[160*80/8*8];
		mScaleMatrix = new Matrix();
		mScaleMatrix.setScale(4, 4);
		mTranslateMatrix = new Matrix();
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

	byte[] buf;
	final Rect bounds = new Rect();
	Paint px_paint = new Paint();
	Paint text_paint = new Paint();
	int keyboard_padding_top;
	int mtx_deltax, mtx_deltay;
	String text_instcnt;
	int percent; // Emulation speed benchmark
	
	final static String keys_sz[] = {
	/* 0-7 */	" ", " ", "ON", " ", " ", " ", " ", " ", 
	/* 8-15 */	"英汉", "名片", "计算", "提醒", "资料", "时间", "网络", " ",
	/* 16-23 */	"求助", "Shift", "Caps", "跳出", "0", ".", "空格|=", "←",
	/* 24-31 */	"Z", "X", "C", "V", "B|1", "N|2", "M|3", "PgUp",
	/* 32-39 */	"A", "S", "D", "F", "G|4", "H|5", "J|6", "K",
	/* 40-47 */	"Q", "W", "E", "R", "T|7", "Y|8", "U|9", "I",
	/* 48-55 */	"O", "L", "↑", "↓", "P", "输入", "PgDn", "→",
	/* 56-63 */	" ", " ", "F1", "F2", "F3", "F4", " ", " "
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
	
	final static int keys_width = 10;
	final static int keys_height = 6;
	final static int key_layout[] = {
		-1, -1, 8,  9, 10, 11, 12, 13, -1, -1, // 功能键。
		-1, 14, -1, 58, 59, 60, 61, -1, 2, -1, // 网络，F1-F4，开关
		40, 41, 42, 43, 44, 45, 46, 47, 48, 52, // QWERTYUIOP
		32, 33, 34, 35, 36, 37, 38, 39, 49, 53, // ASDFGHJKL 输入
		24, 25, 26, 27, 28, 29, 30, 31, 50, 54, // ZXCVBNM 上翻页 上 下翻页
		16, 17, 18, 19, 20, 21, 22, 23, 51, 55  // 求助  中英数 输入法 跳出 0 . 空格 左 下 右
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
		
		mtx_deltax = W/keys_width;
		mtx_deltay = (H-keyboard_padding_top)/keys_height;
		if(mtx_deltay > mtx_deltax) mtx_deltay = mtx_deltax;
		for(int row=0; row<keys_height; row++) {
			for(int col=0; col<keys_width; col++) {
				int mtxidx = row*keys_width+col;
				int kidx = key_layout[mtxidx];
				if(kidx==-1) continue;
				char ks = keys_state[kidx];
				if(ks==2) continue;
				if(ks==0) 
					p_mtx.setColor(Color.GRAY);
				else p_mtx.setColor(Color.BLUE);
				String s = keys_sz[kidx];
				float dx = mtx_deltax * col, dy = mtx_deltay * row + keyboard_padding_top;
				c.drawRect(dx, dy, dx+mtx_deltax-2, dy+mtx_deltay-2, p_mtx);
				c.drawText(s, dx+mtx_deltax/2, dy+mtx_deltay/2, p_mtx_txt);
			}
		}
	}

	int last_key_col=0;
	int last_key_row=0;
	void on_touch(float x, float y, boolean is_down) {
		if(mtx_deltax==0||mtx_deltay==0) return;
		if(x==-1 && y==-1 && is_down==false) {
			keys_state[last_key_row*8+last_key_col]=0;
			FleurDeLisDriver.keymatrixChange(last_key_row, last_key_col, false);
			return;
		}
		int mtx_col = (int) (x/mtx_deltax);
		int mtx_row = (int) ((y-keyboard_padding_top)/mtx_deltay);
		if(mtx_row < 0 || mtx_row >= keys_height) return;
		if(mtx_col < 0 || mtx_col >= keys_width)  return;
		int mtxidx = mtx_row*keys_width + mtx_col;
		if(mtxidx > keys_width*keys_height || mtxidx<0) return;
		int keyidx = key_layout[mtxidx];
		if(keyidx==-1) return;
		if(keys_state[keyidx]==2) return;
		if(is_down) {
			keys_state[keyidx] = 1;
			int row = keyidx >> 3, col = keyidx & 0x7;
			FleurDeLisDriver.keymatrixChange(row, col, true);
			last_key_row = row; last_key_col = col;
		}
	}
	
	public void update(byte[] buf) {
		this.buf = buf;
		text_instcnt = host.thdEmulator.getPerformanceString();
		postInvalidate();
	}
	
	
	@Override
	protected void onDraw(Canvas canvas) {
		final int W = this.getWidth();
		final int H = this.getHeight();
		final int zoom = this.getWidth()/160;
		final int padding_left = (W-160*zoom)/2;
		mTranslateMatrix.setTranslate(padding_left, 0);
		text_paint.setTextAlign(Align.LEFT);
		
		if(buf==null) {
			text_paint.setTextAlign(Align.CENTER);
			canvas.drawText("Please put obj.bin and cc800.fls under\nsdcard/cc800", W/2, H/2, text_paint);
			return;
		}
		
		keyboard_padding_top = (int) (80*zoom + text_paint.getTextSize());
		{
			// Inst Count
			
			text_paint.getTextBounds(text_instcnt, 0, text_instcnt.length(), bounds);
			if(host.thdEmulator.isTooSlow()==true) { text_paint.setColor(Color.RED);}
			else { text_paint.setColor(Color.DKGRAY); }
			final int y = (int) (80*zoom + bounds.height());
			
			canvas.drawText(text_instcnt, 0, y, text_paint);
			
		}
		
		px_paint.setStyle(Style.FILL);
		int idx_buf = 0, idx_pixel=0, i = 0, j = 0;
		boolean curr_black = false;
		while(idx_buf < 1600) {
			for(int bid=0; bid<8; bid++) { // bid = bit id
				byte mask =	 (byte)(1 << (7-bid));
				if((buf[idx_buf] & mask)!=0) {
					curr_black = true;
				} else curr_black = false;
				
				if(curr_black==true) {
					mScreenPixelData[idx_pixel] = 0xFF000000;
				} else {
					mScreenPixelData[idx_pixel] = 0xFFCCCCCC;
				}
				idx_pixel++;
				i=i+1;
				if(i==160) { i=0; j+=1; }
			}
			idx_buf++;
		}
		mBitmap.copyPixelsFromBuffer(IntBuffer.wrap(mScreenPixelData));
		mScaleMatrix.setScale(zoom, zoom);
		mScaleMatrix.setConcat(mTranslateMatrix, mScaleMatrix);
		canvas.drawBitmap(mBitmap, mScaleMatrix, null);
		
		paintKeyMatrix(canvas, zoom);
	}
}
