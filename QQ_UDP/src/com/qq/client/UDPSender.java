package com.qq.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.qq.model.Jkuser;
import com.qq.model.ToolsCreateMsg;
import com.qq.model.ToolsParseMsg;
import com.qq.msg.IMsgConstance;
import com.qq.msg.MsgChangePwd;
import com.qq.msg.MsgChangePwdResp;
import com.qq.msg.MsgChatFile;
import com.qq.msg.MsgCommuChatText;
import com.qq.msg.MsgFind;
import com.qq.msg.MsgFindResp;
import com.qq.msg.MsgForgetResp;
import com.qq.msg.MsgHead;
import com.qq.msg.MsgLogin;
import com.qq.msg.MsgLoginResp;
import com.qq.msg.MsgReg;
import com.qq.msg.MsgRegResp;
import com.qq.util.LogTools;
import com.qq.util.MD5Util;

/**
 * UDP Client������
 * 
 * @author yy
 * 
 */
public class UDPSender extends Thread {
	private SocketAddress destAdd = new InetSocketAddress("localhost",
			IMsgConstance.serverPort);
	private DatagramSocket socket = null;
	private IClientMsgListener clientMsgListener = null;
	private byte[] buffer = new byte[1024 * 1024 * 10];
	private int port = 0;
	private static Map<Integer, InetSocketAddress> addrMap = new HashMap<Integer, InetSocketAddress>();
	private static Map<Integer, Integer> portMap = new HashMap<Integer, Integer>(); // Ⱥ����Ͷ˿ں����ӳ��
	private CommunityTree communityTree = null;
	private MulticastSocket receiveSocket[] = null; // ��������ʱ��Ҫָ�������Ķ˿ں�
	private MulticastSocket sendSocket = null;
	private InetAddress address = null;
	private byte[] buf = new byte[1024 * 1024];
	private Selector selector = null; // ѡ��������
	private DatagramChannel channel = null; // ͨ��

	public UDPSender(IClientMsgListener clientMsgListener,
			CommunityTree communityTree) throws SocketException {
		this.clientMsgListener = clientMsgListener;
		socket = new DatagramSocket();
		this.communityTree = communityTree;
		try {
			address = InetAddress.getByName("224.0.0.1");
			sendSocket = new MulticastSocket();
			channel = DatagramChannel.open();
			channel.configureBlocking(false);
			channel.connect(new InetSocketAddress("localhost",IMsgConstance.serverPort+1));
			selector = Selector.open();
			channel.register(selector, SelectionKey.OP_READ);
			new ClientThread().start();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private class ClientThread extends Thread {
		@Override
		public void run() {
			ByteBuffer byteBuffer = ByteBuffer.allocate(1024 * 1024);
			while (true) {
				try {
					int eventsCount = selector.select();
					if (eventsCount > 0) {
						Set selectedKeys = selector.selectedKeys();
						Iterator iterator = selectedKeys.iterator();
						while (iterator.hasNext()) {
							SelectionKey sk = (SelectionKey) iterator.next();
							iterator.remove();
							if (sk.isReadable()) {
								DatagramChannel datagramChannel = (DatagramChannel) sk
										.channel();
								datagramChannel.read(byteBuffer);
								byteBuffer.flip();
								//�����յ�������ת����Ϊͨ����Ϣ
								MsgHead head = ToolsParseMsg.parseMsg(byteBuffer.array());
								System.out.println("msg "+head);
								byteBuffer.clear();
							}
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

		}
	}
	
	
	/**
	 * ͨ��NIO�ܵ������ݴ��䵽��������
	 * @param msgHead
	 */
	public void sendMsgToServerByNio(MsgHead msgHead) {
		try {
			channel.write(ByteBuffer.wrap(ToolsCreateMsg.packMsg(msgHead)));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	

	/**
	 * ����ʵ��Ⱥ�� By UDP_�鲥
	 * 
	 * @author yy
	 * 
	 */
	class UDPCast extends Thread {

		private MulticastSocket castSocket = null;

		public UDPCast(MulticastSocket castSocket) {
			this.castSocket = castSocket;
		}

		@Override
		public void run() {
			while (true) {
				DatagramPacket datagramPacket = new DatagramPacket(buf,
						buf.length);
				try {
					castSocket.receive(datagramPacket);
					byte[] message = datagramPacket.getData(); // ��buffer�н�ȡ�յ�������
					MsgHead head = ToolsParseMsg.parseMsg(message);
					communityTree.onMsgRecive(head);
				} catch (Exception e) {
					e.printStackTrace();
				} // �������ݣ�ͬ�����������״̬

			}

		}
	}

	/**
	 * �����鲥��Ϣ��
	 * 
	 * @param msg
	 */
	public void sendCastMsg(MsgHead head) {

		MsgCommuChatText msg = (MsgCommuChatText) head;
		int cid = msg.getDestCid();
		System.out
				.println("destcid " + cid + " portMapsize: " + portMap.size());
		int port = portMap.get(cid);
		try {
			byte[] data = ToolsCreateMsg.packMsg(msg);
			DatagramPacket datagramPacket = new DatagramPacket(data,
					data.length);
			datagramPacket.setAddress(address); // ���յ�ַ��group�ı�ʶ��ͬ
			datagramPacket.setPort(port); // �������Ķ˿ں�
			sendSocket.send(datagramPacket);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * �����������˴�������Ϣ һ������Ϣ �ʹ���
	 */
	@Override
	public void run() {

		while (true) {
			try {
				// ����һ����Ϣ
				MsgHead m = readFromServer();
				if (m.getType() == IMsgConstance.command_find_resp) {
					MsgFindResp findResp = (MsgFindResp) m;
					if (findResp.getState() == 0) {
						clientMsgListener.fireMsg(m, null);
					} else {
						Object obj = readObjectFromServer();
						clientMsgListener.fireMsg(m, obj);
					}
				} else if (m.getType() == IMsgConstance.command_addCommunity
						|| m.getType() == IMsgConstance.command_addCommunity_resp) {
					Object obj = readObjectFromServer();
					clientMsgListener.fireMsg(m, obj);
				} else if (m.getType() == IMsgConstance.command_sendAddr) {
					addrMap = (Map<Integer, InetSocketAddress>) readObjectFromServer();
				} else if (m.getType() == IMsgConstance.command_sendPort) {
					portMap = (Map<Integer, Integer>) readObjectFromServer();
					System.out.println("portMapsize: " + portMap.size());
					receiveSocket = new MulticastSocket[portMap.size()];
					Set<Integer> set = portMap.keySet();
					int index = 0;
					for (Integer i : set) {
						receiveSocket[index] = new MulticastSocket(
								portMap.get(i));
						receiveSocket[index].joinGroup(address);
						new UDPCast(receiveSocket[index++]).start();
					}
				} else {
					// ����Ϣ�ַ���������ȥ����
					clientMsgListener.fireMsg(m, null);
				}
			} catch (Exception ef) {
				ef.printStackTrace();
				break; // �����ȡ����,���˳�
			}
		}
		LogTools.INFO(this.getClass(), "�ͻ��˽����̼߳��˳�!");
	}

	/**
	 * ���ܷ��������������ݰ�
	 * 
	 * @return:��ȡ������Ϣ����
	 * @throws Exception
	 */
	public MsgHead readFromServer() throws Exception {
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
		socket.receive(packet);
		byte[] data = packet.getData();
		MsgHead head = ToolsParseMsg.parseMsg(data);
		LogTools.INFO(head.getClass(), "�ͻ����յ�:" + head);
		return head; // ���Ϊ��Ϣ����
	}

	/**
	 * ���ܷ����������Ķ������ݰ� ��ת��Ϊ����
	 * 
	 * @return:��ȡ������Ϣ����
	 * @throws Exception
	 */
	public Object readObjectFromServer() throws Exception {
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
		socket.receive(packet);
		ByteArrayInputStream bins = new ByteArrayInputStream(packet.getData());
		ObjectInputStream oins = new ObjectInputStream(bins);
		return oins.readObject();
	}

	/**
	 * ����һ����Ϣ��������
	 * 
	 * @param msg
	 *            �����͵���Ϣ
	 * @throws IOException
	 */
	public void sendMsg(MsgHead msg) throws IOException {
		LogTools.INFO(this.getClass(), "�ͻ��˷�����Ϣ:" + msg);
		byte[] data = ToolsCreateMsg.packMsg(msg);// �������Ϊ���ݿ�
		DatagramPacket packet = new DatagramPacket(data, data.length, destAdd);
		socket.send(packet);
	}

	/**
	 * �����������������Ϣ
	 * 
	 * @param jknum
	 */
	public void sendLeaveMsg(int jknum) {
		MsgHead head = new MsgHead();
		head.setSrc(jknum);
		head.setDest(IMsgConstance.Server_JK_NUMBER);
		head.setType(IMsgConstance.command_leave);
		try {
			sendMsg(head);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * ͷ���ϴ�����
	 * 
	 * @param head
	 * @param file
	 */
	public void updateHeader(MsgHead head, File file) {
		try {
			sendMsg(head);
			// ���ļ�����ȥ
			sendObjctToServer(file);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * ����һ�����󵽷�������
	 * 
	 * @param obj
	 */
	private void sendObjctToServer(Object obj) {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(obj);
			DatagramPacket packet = new DatagramPacket(baos.toByteArray(),
					baos.toByteArray().length, destAdd);
			socket.send(packet);
			oos.flush();
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * ����һ����Ϣ��ָ���Ŀͻ���
	 * 
	 * @param msg
	 */
	private void sendMsgToOneClient(InetSocketAddress destAddr, MsgHead msg) {
		try {
			byte[] data = ToolsCreateMsg.packMsg(msg);
			DatagramPacket packet = new DatagramPacket(data, data.length,
					destAddr);
			socket.send(packet);
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * �޸Ļ�������Ϣ
	 * 
	 * @return
	 */
	public void updateBasicMsg(Jkuser user) {

		MsgHead head = new MsgHead();
		head.setSrc(user.getJknum());
		head.setDest(IMsgConstance.Server_JK_NUMBER);
		head.setType(IMsgConstance.command_msgUpdate);

		try {
			sendMsg(head);
			sendObjctToServer(user);

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/**
	 * ��ע����û���Ϣ�ύ�������� �ȴ�����������Ӧ ����jknum
	 * 
	 * @param jkuser
	 * @return
	 * @throws Exception
	 */
	public int regServer(Jkuser jkuser) throws Exception {
		int state = 0;
		MsgReg msgReg = new MsgReg();
		msgReg.setType(IMsgConstance.command_reg);
		msgReg.setDest(IMsgConstance.Server_JK_NUMBER);
		msgReg.setSrc(0);
		msgReg.setJkuser(jkuser);
		this.sendMsg(msgReg);
		// �����˵�½����֮��,�������һ��Ӧ�����Ϣ
		MsgHead regResp = readFromServer();
		MsgRegResp resp = (MsgRegResp) regResp;
		if (resp.getState() == 0) {
			return resp.getDest();
		}
		return -1;

	}

	/**
	 * ��������˷�����������������Ϣ ���õ��������˵Ļ�Ӧ
	 * 
	 * @param jknum
	 * @return
	 */
	public String[] forgetPwd(int jknum) {
		String[] str = new String[2];
		// ��������˷�������
		MsgHead forgetPwd = new MsgHead();
		forgetPwd.setSrc(jknum);
		forgetPwd.setDest(IMsgConstance.Server_JK_NUMBER);
		forgetPwd.setType(IMsgConstance.command_forgetPwd);
		try {
			this.sendMsg(forgetPwd);
		} catch (Exception e) {
			e.printStackTrace();
		}

		try {
			MsgHead head = readFromServer();
			MsgForgetResp forgetResp = (MsgForgetResp) head;
			if (forgetResp.getSrc() == 0)
				return null;
			str[0] = forgetResp.getQuestion().trim();
			str[1] = forgetResp.getAnswer().trim();

		} catch (Exception e) {
			e.printStackTrace();
		}

		return str;

	}

	/**
	 * �޸����� �ɹ�����1 ʧ�ܵĻ�����0
	 * 
	 * @param srcNum
	 * @param newPwd
	 */
	public byte changePwd(int srcNum, String newPwd) {
		MsgChangePwd changePwd = new MsgChangePwd();
		changePwd.setType(IMsgConstance.command_changePwd);
		changePwd.setSrc(srcNum);
		changePwd.setDest(IMsgConstance.Server_JK_NUMBER);
		changePwd.setNewPwd(MD5Util.MD5(newPwd));
		try {
			this.sendMsg(changePwd);
		} catch (Exception e) {
			e.printStackTrace();
		}

		MsgChangePwdResp changePwdResp = null;

		try {
			MsgHead head = readFromServer();
			changePwdResp = (MsgChangePwdResp) head;

		} catch (Exception e) {
			e.printStackTrace();
		}

		return changePwdResp.getState();
	}

	/**
	 * ����������͵�½����
	 * 
	 * @param jkNum
	 * @param pwd
	 * @return
	 */
	public Jkuser loginServer(int jkNum, String pwd, int state) {
		try {
			MsgLogin msgLogin = new MsgLogin();
			msgLogin.setType(IMsgConstance.command_login);
			msgLogin.setSrc(jkNum);
			msgLogin.setState(state);
			msgLogin.setDest(IMsgConstance.Server_JK_NUMBER);
			msgLogin.setPassword(pwd);
			this.sendMsg(msgLogin);
			// ����ȵ�һ��Ӧ����Ϣ
			MsgHead loginResp = readFromServer();
			MsgLoginResp mlr = (MsgLoginResp) loginResp;
			if (mlr.getState() == 1) {
				return null;
			} else {
				Jkuser jkuser = (Jkuser) readObjectFromServer();
				System.out.println("���յ�:" + jkuser);
				return jkuser;
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	/**
	 * ����������Ͳ�����Ϣ
	 * 
	 * @param find
	 */
	public void findMsgById(MsgFind find) {
		try {
			sendMsg(find);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * �ļ��ϴ�����ʵ�� ʹ��P2P��ʽ
	 * 
	 * @param chatFile
	 */
	public void fileUpload(MsgChatFile chatFile) {
		int destNum = chatFile.getDest();
		InetSocketAddress addr = addrMap.get(destNum);
		sendMsgToOneClient(addr, chatFile);
	}

}
