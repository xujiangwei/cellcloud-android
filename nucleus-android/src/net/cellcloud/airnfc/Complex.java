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

public class Complex {

	public double real;
	public double image;

	public Complex() {
		this.real = 0;
		this.image = 0;
	}

	public Complex(double real, double image) {
		this.real = real;
		this.image = image;
	}

	public Complex(int real, int image) {
		Integer integer = real;
		this.real = integer.floatValue();
		integer = image;
		this.image = integer.floatValue();
	}

	public Complex(double real) {
		this.real = real;
		this.image = 0;
	}

	public Complex cc(Complex complex) {
		Complex tmpComplex = new Complex();
		tmpComplex.real = this.real * complex.real - this.image * complex.image;
		tmpComplex.image = this.real * complex.image + this.image * complex.real;
		return tmpComplex;
	}

	public Complex sum(Complex complex) {
		Complex tmpComplex = new Complex();
		tmpComplex.real = this.real + complex.real;
		tmpComplex.image = this.image + complex.image;
		return tmpComplex;
	}

	public Complex cut(Complex complex) {
		Complex tmpComplex = new Complex();
		tmpComplex.real = this.real - complex.real;
		tmpComplex.image = this.image - complex.image;
		return tmpComplex;
	}

	public int getIntValue() {
		int ret = 0;
		ret = (int) Math.round(Math.sqrt(this.real * this.real - this.image * this.image));
		return ret;
	}
}
