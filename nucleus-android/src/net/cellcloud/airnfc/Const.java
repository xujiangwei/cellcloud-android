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

public class Const {

	public static final int DEFAULT_SAMPLE_RATE = 44100;

	protected static final byte[] CODE_BOOK_INDEX = new byte[] {
		'*',
		'#',
		'/',
		'0',
		'1',
		'2',
		'3',
		'4',
		'5',
		'6',
		'7',
		'8',
		'9',
		'A',
		'B',
		'C',
		'D',
		'E',
		'F',
		':'
	};

	protected static final int[] CODE_BOOK = new int[] {
		19900,	// *
		20000,	// #
		20100,	// /
		20200,	// 0
		20300,	// 1
		20400,	// 2
		20500,	// 3
		20600,	// 4
		20700,	// 5
		20800,	// 6
		20900,	// 7
		21000,	// 8
		21100,	// 9
		21200,	// A
		21300,	// B
		21400,	// C
		21500,	// D
		21600,	// E
		21700,	// F
		21800	// :
	};

	protected static final byte[] PACKET_BOOK_INDEX = new byte[] {
		0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
		0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F,
		' ',  '!',  '"',  '#',  '$',  '%',  '&',  '\'', '(',  ')',  '*',  '+',  ',',  '-',  '.',  '/',
		'0',  '1',  '2',  '3',  '4',  '5',  '6',  '7',  '8',  '9',  ':',  ';',  '<',  '=',  '>',  '?',
		'@',  'A',  'B',  'C',  'D',  'E',  'F',  'G',  'H',  'I',  'J',  'K',  'L',  'M',  'N',  'O',
		'P',  'Q',  'R',  'S',  'T',  'U',  'V',  'W',  'X',  'Y',  'Z',  '[',  '\\', ']',  '^',  '_',
		'`',  'a',  'b',  'c',  'd',  'e',  'f',  'g',  'h',  'i',  'j',  'k',  'l',  'm',  'n',  'o',
		'p',  'q',  'r',  's',  't',  'u',  'v',  'w',  'x',  'y',  'z',  '{',  '|',  '}',  '~',  0x7F
	};

	protected static final byte[][] PACKET_BOOK = new byte[][] {
		new byte[]{'0', '0'}, new byte[]{'0', '1'}, new byte[]{'0', '2'}, new byte[]{'0', '3'},
		new byte[]{'0', '4'}, new byte[]{'0', '5'}, new byte[]{'0', '6'}, new byte[]{'0', '7'},
		new byte[]{'0', '8'}, new byte[]{'0', '9'}, new byte[]{'0', 'A'}, new byte[]{'0', 'B'},
		new byte[]{'0', 'C'}, new byte[]{'0', 'D'}, new byte[]{'0', 'E'}, new byte[]{'0', 'F'},
		new byte[]{'1', '0'}, new byte[]{'1', '1'}, new byte[]{'1', '2'}, new byte[]{'1', '3'},
		new byte[]{'1', '4'}, new byte[]{'1', '5'}, new byte[]{'1', '6'}, new byte[]{'1', '7'},
		new byte[]{'1', '8'}, new byte[]{'1', '9'}, new byte[]{'1', 'A'}, new byte[]{'1', 'B'},
		new byte[]{'1', 'C'}, new byte[]{'1', 'D'}, new byte[]{'1', 'E'}, new byte[]{'1', 'F'},
		new byte[]{'2', '0'}, new byte[]{'2', '1'}, new byte[]{'2', '2'}, new byte[]{'2', '3'},
		new byte[]{'2', '4'}, new byte[]{'2', '5'}, new byte[]{'2', '6'}, new byte[]{'2', '7'},
		new byte[]{'2', '8'}, new byte[]{'2', '9'}, new byte[]{'2', 'A'}, new byte[]{'2', 'B'},
		new byte[]{'2', 'C'}, new byte[]{'2', 'D'}, new byte[]{'2', 'E'}, new byte[]{'2', 'F'},
		new byte[]{'3', '0'}, new byte[]{'3', '1'}, new byte[]{'3', '2'}, new byte[]{'3', '3'},
		new byte[]{'3', '4'}, new byte[]{'3', '5'}, new byte[]{'3', '6'}, new byte[]{'3', '7'},
		new byte[]{'3', '8'}, new byte[]{'3', '9'}, new byte[]{'3', 'A'}, new byte[]{'3', 'B'},
		new byte[]{'3', 'C'}, new byte[]{'3', 'D'}, new byte[]{'3', 'E'}, new byte[]{'3', 'F'},
		new byte[]{'4', '0'}, new byte[]{'4', '1'}, new byte[]{'4', '2'}, new byte[]{'4', '3'},
		new byte[]{'4', '4'}, new byte[]{'4', '5'}, new byte[]{'4', '6'}, new byte[]{'4', '7'},
		new byte[]{'4', '8'}, new byte[]{'4', '9'}, new byte[]{'4', 'A'}, new byte[]{'4', 'B'},
		new byte[]{'4', 'C'}, new byte[]{'4', 'D'}, new byte[]{'4', 'E'}, new byte[]{'4', 'F'},
		new byte[]{'5', '0'}, new byte[]{'5', '1'}, new byte[]{'5', '2'}, new byte[]{'5', '3'},
		new byte[]{'5', '4'}, new byte[]{'5', '5'}, new byte[]{'5', '6'}, new byte[]{'5', '7'},
		new byte[]{'5', '8'}, new byte[]{'5', '9'}, new byte[]{'5', 'A'}, new byte[]{'5', 'B'},
		new byte[]{'5', 'C'}, new byte[]{'5', 'D'}, new byte[]{'5', 'E'}, new byte[]{'5', 'F'},
		new byte[]{'6', '0'}, new byte[]{'6', '1'}, new byte[]{'6', '2'}, new byte[]{'6', '3'},
		new byte[]{'6', '4'}, new byte[]{'6', '5'}, new byte[]{'6', '6'}, new byte[]{'6', '7'},
		new byte[]{'6', '8'}, new byte[]{'6', '9'}, new byte[]{'6', 'A'}, new byte[]{'6', 'B'},
		new byte[]{'6', 'C'}, new byte[]{'6', 'D'}, new byte[]{'6', 'E'}, new byte[]{'6', 'F'},
		new byte[]{'7', '0'}, new byte[]{'7', '1'}, new byte[]{'7', '2'}, new byte[]{'7', '3'},
		new byte[]{'7', '4'}, new byte[]{'7', '5'}, new byte[]{'7', '6'}, new byte[]{'7', '7'},
		new byte[]{'7', '8'}, new byte[]{'7', '9'}, new byte[]{'7', 'A'}, new byte[]{'7', 'B'},
		new byte[]{'7', 'C'}, new byte[]{'7', 'D'}, new byte[]{'7', 'E'}, new byte[]{'7', 'F'}
	};

	protected static final byte[] CODE_DICTIONARY = new byte[] {
		' ', '!'
	};
}
