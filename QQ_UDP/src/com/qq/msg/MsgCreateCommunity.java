package com.qq.msg;

import java.util.Arrays;

/**
 * 群组创建消息类
 * @author yy
 *
 */
public class MsgCreateCommunity extends MsgHead {
	
	private String cName;	//群名称
	private String cDes;	//群简介
	private byte[] icon;	//头像
	private String fileName;	//文件名字
	private int len;	//文件长度
	
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
