package com.qq.msg;

import java.util.Arrays;

/**
 * Ⱥ�鴴����Ϣ��
 * @author yy
 *
 */
public class MsgCreateCommunity extends MsgHead {
	
	private String cName;	//Ⱥ����
	private String cDes;	//Ⱥ���
	private byte[] icon;	//ͷ��
	private String fileName;	//�ļ�����
	private int len;	//�ļ�����
	
	public int getLen() {
		return len;
	}
	public void setLen(int len) {
		this.len = len;
	}
	public String getFileName() {
		return fileName;
	}
	public void setFileName(String fileName) {
		this.fileName = fileName;
	}
	public String getcName() {
		return cName;
	}
	public void setcName(String cName) {
		this.cName = cName;
	}
	public String getcDes() {
		return cDes;
	}
	public void setcDes(String cDes) {
		this.cDes = cDes;
	}
	public byte[] getIcon() {
		return icon;
	}
	public void setIcon(byte[] icon) {
		this.icon = icon;
	}
	@Override
	public String toString() {
		return "MsgCreateCommunity [cName=" + cName + ", cDes=" + cDes
				+ ", icon=" + Arrays.toString(icon) + ", fileName=" + fileName
				+ "]";
	}
	
}
