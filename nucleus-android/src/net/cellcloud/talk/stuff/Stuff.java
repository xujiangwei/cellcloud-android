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

package net.cellcloud.talk.stuff;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import net.cellcloud.util.ByteUtils;

import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/** 原语语素。
 * 
 * @author Jiangwei Xu
 */
public abstract class Stuff {

//	private static final DecimalFormat DF = new DecimalFormat("#0.0000");

	private StuffType type;

	protected byte[] value;
	protected LiteralBase literalBase;

	/** 构造函数。 */
	public Stuff(StuffType type) {
		this.type = type;
	}

	/** 构造函数。 */
	public Stuff(StuffType type, String value) {
		this.type = type;
		this.value = value.getBytes(Charset.forName("UTF-8"));
		this.literalBase = LiteralBase.STRING;
	}

	/** 构造函数。 */
	public Stuff(StuffType type, int value) {
		this.type = type;
		this.value = ByteUtils.toBytes(value);
		this.literalBase = LiteralBase.INT;
	}

	/** 构造函数。 */
	public Stuff(StuffType type, long value) {
		this.type = type;
		this.value = ByteUtils.toBytes(value);
		this.literalBase = LiteralBase.LONG;
	}

	/** 构造函数。 */
	public Stuff(StuffType type, float value) {
		this.type = type;
		this.value = ByteUtils.toBytes(value);
		this.literalBase = LiteralBase.FLOAT;
	}

	/** 构造函数。 */
	public Stuff(StuffType type, double value) {
		this.type = type;
		this.value = ByteUtils.toBytes(value);
		this.literalBase = LiteralBase.DOUBLE;
	}

	/** 构造函数。 */
	public Stuff(StuffType type, boolean value) {
		this.type = type;
		this.value = ByteUtils.toBytes(value);
		this.literalBase = LiteralBase.BOOL;
	}

	/** 构造函数。 */
	public Stuff(StuffType type, JSONObject json) {
		this.type = type;
		this.value = json.toString().getBytes(Charset.forName("UTF-8"));
		this.literalBase = LiteralBase.JSON;
	}

	/** 构造函数。 */
	public Stuff(StuffType type, byte[] value) {
		this.type = type;
		this.value = value;
		this.literalBase = LiteralBase.BIN;
	}

	/** 构造函数。 */
	public Stuff(StuffType type, Document doc)
			throws TransformerException {
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer t = tf.newTransformer();
		t.setOutputProperty("encoding", "UTF-8");
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		t.transform(new DOMSource(doc), new StreamResult(bos));

		this.value = bos.toString().getBytes(Charset.forName("UTF-8"));
		this.type = type;
		this.literalBase = LiteralBase.XML;

		try {
			bos.close();
		} catch (IOException e) {
			// Nothing
		}
	}

	/** 将自身语素数据复制给目标语素。 */
	abstract public void clone(Stuff target);

	/** 返回语素类型。
	 */
	public StuffType getType() {
		return this.type;
	}

	/** 按照二进制形式返回值。
	 */
	public byte[] getValue() {
		return this.value;
	}

	/** 按照字符串形式返回值。
	*/
	public String getValueAsString() {
		return new String(this.value, Charset.forName("UTF-8"));
	}

	/** 按照整数形式返回值。
	*/
	public int getValueAsInt() {
		return ByteUtils.toInt(this.value);
	}

	/** 按照长整数形式返回值。
	*/
	public long getValueAsLong() {
		return ByteUtils.toLong(this.value);
	}

	/** 按照浮点数形式返回值。
	 */
	public float getValueAsFloat() {
		return ByteUtils.toFloat(this.value);
	}

	/** 按照双精浮点数形式返回值。
	 */
	public double getValueAsDouble() {
		return ByteUtils.toDouble(this.value);
	}

	/** 按照布尔值形式返回值。
	*/
	public boolean getValueAsBool() {
		return ByteUtils.toBoolean(this.value);
	}

	/** 按照 JSON 格式返回值。
	 * @throws JSONException 
	 */
	public JSONObject getValueAsJSON() throws JSONException {
		return new JSONObject(new String(this.value, Charset.forName("UTF-8")));
	}

	/** 按照 XML 格式返回值。
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws IOException
	 */
	public Document getValueAsXML()
			throws ParserConfigurationException, SAXException, IOException {
		String xmlStr = new String(this.value, Charset.forName("UTF-8"));
		StringReader sr = new StringReader(xmlStr);
		InputSource is = new InputSource(sr);
		DocumentBuilderFactory factory =  DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document doc = builder.parse(is);
		sr.close();
	    return doc;
	}

	/** 返回数值字面义。
	*/
	public LiteralBase getLiteralBase() {
		return this.literalBase;
	}

	/** @private
	 */
	protected void setValue(byte[] value) {
		this.value = value;
	}
	/** @private
	 */
	protected void setValue(String value) {
		this.value = value.getBytes(Charset.forName("UTF-8"));
	}
	/** @private
	 */
	protected void setValue(int value) {
		this.value = ByteUtils.toBytes(value);
	}
	/** @private
	 */
	protected void setValue(long value) {
		this.value = ByteUtils.toBytes(value);
	}
	/** @private
	 */
	protected void setValue(boolean value) {
		this.value = ByteUtils.toBytes(value);
	}
	/** @private
	 */
	protected void setValue(float value) {
		this.value = ByteUtils.toBytes(value);
	}
	/** @private
	 */
	protected void setValue(double value) {
		this.value = ByteUtils.toBytes(value);
	}
	/** @private
	 */
	protected void setValue(JSONObject json) {
		this.value = json.toString().getBytes(Charset.forName("UTF-8"));
	}

	/** @private
	 */
	protected void setLiteralBase(LiteralBase literalBase) {
		this.literalBase = literalBase;
	}
}
