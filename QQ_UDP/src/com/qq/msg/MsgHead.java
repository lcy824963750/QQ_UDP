package com.qq.msg;

/**
 * ������Ϣ���͵ĸ���
 * @author yy
 *
 */
public class MsgHead {
	
	private byte type;	//��Ϣ������
	private int dest;	//��Ϣ���շ���jk����
	private int src;	//��Ϣ���ͷ���jk����
	
	@Override
	public String toString() {
		return "MsgHead [type=" + type + ", dest=" + dest + ", src=" + src
				+ "]";
	}
	public byte getType() {
		return type;
	}
	public void setType(byte type) {
		this.type = type;
	}
	public int getDest() {
		return dest;
	}
	public void setDest(int dest) {
		this.dest = dest;
	}
	public int getSrc() {
		return src;
	}
	public void setSrc(int src) {
		this.src = src;
	}
	
	
}
