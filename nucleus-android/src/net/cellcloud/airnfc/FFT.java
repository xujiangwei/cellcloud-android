/*
-----------------------------------------------------------------------------
This source file is part of Cell Cloud.

Copyright (c) 2009-2016 Cell Cloud Team (www.cellcloud.net)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
-----------------------------------------------------------------------------
*/

package net.cellcloud.airnfc;

public class FFT {

	// 快速傅里叶变换
	public static void fft(Complex[] xin, int N) {
		int f, m, N2, nm, i, k, j, L;// L:运算级数
		float p;
		int e2, B, ip;
//		int le;
		Complex w = new Complex();
		Complex t = new Complex();
		N2 = N / 2;	// 每一级中蝶形的个数,同时也代表m位二进制数最高位的十进制权
		f = N;		// f是为了求流程的级数而设立的
		for (m = 1; (f = f / 2) != 1; m++)
			; // 得到流程图的共几级
		nm = N - 2;
		j = N2;
		// 倒序运算 — 雷德算法
		for (i = 1; i <= nm; i++) {
			// 防止重复交换
			if (i < j) {
				t = xin[j];
				xin[j] = xin[i];
				xin[i] = t;
			}
			k = N2;
			while (j >= k) {
				j = j - k;
				k = k / 2;
			}
			j = j + k;
		}
		// 蝶形图计算部分
		// 从第1级到第m级
		for (L = 1; L <= m; L++) {
			e2 = (int) Math.pow(2, L);
			// e2=(int)2.pow(L);
//			le = e2 + 1;
			B = e2 / 2;
			// j从0到2^(L-1)-1
			for (j = 0; j < B; j++) {
				p = (int) (2.0f * Math.PI / e2);
				w.real = Math.cos(p * j);
				// w.real = Math.cos((double)p*j); //系数W
				w.image = Math.sin(p * j) * -1;
				// w.imag = -sin(p*j);
				// 计算具有相同系数的数据
				for (i = j; i < N; i = i + e2) {
					ip = i + B; // 对应蝶形的数据间隔为2^(L-1)
					t = xin[ip].cc(w);
					xin[ip] = xin[i].cut(t);
					xin[i] = xin[i].sum(t);
				}
			}
		}
	}
}
