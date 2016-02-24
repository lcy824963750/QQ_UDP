package com.qq.msg;

public class MsgUpdateResp extends MsgHead {
	
	private byte state;

	public byte getState() {
		return state;
	}

	public void setState(byte state) {
		this.state = state;
	}

	@Override
	public String toString() {
		return "MsgUpdateResp [state=" + state + "]";
	}

	

}
