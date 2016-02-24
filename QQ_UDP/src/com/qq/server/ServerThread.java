package com.qq.server;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.Charset;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Set;

import javax.imageio.ImageIO;

import com.qq.dao.BaseJdbcDao;
import com.qq.dao.JkuserDaoImpl;
import com.qq.model.Jkuser;
import com.qq.model.ToolsCreateMsg;
import com.qq.model.ToolsParseMsg;
import com.qq.msg.IMsgConstance;
import com.qq.msg.MsgChangePwd;
import com.qq.msg.MsgChangePwdResp;
import com.qq.msg.MsgFind;
import com.qq.msg.MsgFindResp;
import com.qq.msg.MsgForgetResp;
import com.qq.msg.MsgHead;
import com.qq.msg.MsgHeaderUploadResp;
import com.qq.msg.MsgLogin;
import com.qq.msg.MsgLoginResp;
import com.qq.msg.MsgReg;
import com.qq.msg.MsgRegResp;
import com.qq.msg.MsgUpdateResp;
import com.qq.util.ImageUtil;
import com.qq.util.LogTools;
import com.qq.util.MD5Util;

/**
 * 对于每个接受到的数据包消息都开启一个线程去处理
 * 
 * @author yy
 * 
 */
public class ServerThread extends Thread {

	private MsgHead msg = null;
	private InetSocketAddress address = null;
	private DatagramSocket socket = null;
	private Object obj;
	private Selector selector = null;
	private DatagramChannel channel = null;

	class ServerNioThread extends Thread {
		@Override
		public void run() {
			ByteBuffer byteBuffer = ByteBuffer.allocate(65536);
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
								SocketAddress sa = datagramChannel
										.receive(byteBuffer);
								byteBuffer.flip();

								// 测试：通过将收到的ByteBuffer首先通过缺省的编码解码成CharBuffer 再输出
								MsgHead head = ToolsParseMsg.parseMsg(byteBuffer.array());
								System.out.println("已接受:"+head);

								int destNum = head.getDest();
								InetSocketAddress add = ChatTools.stList.get(ChatTools.getUserByNum(destNum));
								DatagramChannel chan = DatagramChannel.open();
								chan.configureBlocking(false);
								chan.connect(add);
								chan.write(byteBuffer);
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

	public ServerThread(MsgHead head, SocketAddress address, Object obj)
			throws SocketException {
		this.msg = head;
		this.obj = obj;
		this.address = (InetSocketAddress) address;
		this.socket = new DatagramSocket();
		try {
			channel = DatagramChannel.open();
			DatagramSocket socket = channel.socket();
			channel.configureBlocking(false);
			socket.bind(new InetSocketAddress(IMsgConstance.serverPort+1));

			selector = Selector.open();
			channel.register(selector, SelectionKey.OP_READ);
			new ServerNioThread().start();
		} catch (ClosedChannelException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		if (msg.getType() == IMsgConstance.command_reg) {
			MsgReg reg = (MsgReg) msg;
			JkuserDaoImpl daoImpl = new JkuserDaoImpl();
			int jknum = daoImpl.regUser(reg.getJkuser());
			MsgRegResp msgRegResp = new MsgRegResp((byte) 0, jknum);
			msgRegResp.setType(IMsgConstance.command_reg_resp);
			msgRegResp.setSrc(IMsgConstance.Server_JK_NUMBER);
			msgRegResp.setDest(jknum);

			// 发送消息到客户端
			try {
				this.sendMsg2Me(msgRegResp);
			} catch (IOException e) {
				e.printStackTrace();
			}

		} else if (msg.getType() == IMsgConstance.command_login) {
			MsgLogin ml = (MsgLogin) msg;
			Jkuser jkuser = checkLogin(ml);
			MsgLoginResp loginResp = new MsgLoginResp();
			loginResp.setSrc(IMsgConstance.Server_JK_NUMBER);
			loginResp.setType(IMsgConstance.command_login_resp);
			if (jkuser == null) {
				loginResp.setDest(0);
				loginResp.setState((byte) 1);
				// send to client
				try {
					this.sendMsg2Me(loginResp);
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else {
				loginResp.setState((byte) 0);
				loginResp.setDest(jkuser.getJknum());
				try {
					this.sendMsg2Me(loginResp);
				} catch (IOException e) {
					e.printStackTrace();
				}
				// write object to client
				writeObjectToClient(jkuser);
				ChatTools.addClient(jkuser, address);
			}
		} else if (msg.getType() == IMsgConstance.command_forgetPwd) {
			JkuserDaoImpl daoImpl = new JkuserDaoImpl();
			Jkuser user = daoImpl.getBasicInfo(msg.getSrc());
			MsgForgetResp forgetResp = new MsgForgetResp();
			forgetResp.setType(IMsgConstance.command_forgetPwd_resp);
			if (user == null) {
				forgetResp.setSrc(0);
				forgetResp.setQuestion("");
				forgetResp.setAnswer("");
			} else {
				forgetResp.setSrc(IMsgConstance.Server_JK_NUMBER);
				String question = user.getQuestion().trim();
				String answer = user.getAnswer().trim();
				forgetResp.setQuestion(question);
				forgetResp.setAnswer(answer);
			}
			forgetResp.setDest(msg.getSrc());
			try {
				this.sendMsg2Me(forgetResp);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else if (msg.getType() == IMsgConstance.command_changePwd) {
			int srcNum = msg.getSrc();
			MsgChangePwd changePwd = (MsgChangePwd) msg;
			String newPwd = changePwd.getNewPwd();
			JkuserDaoImpl daoImpl = new JkuserDaoImpl();
			int status = daoImpl.changePwd(srcNum, newPwd);

			MsgChangePwdResp changePwdResp = new MsgChangePwdResp();
			changePwdResp.setType(IMsgConstance.command_changePwd_resp);
			changePwdResp.setSrc(IMsgConstance.Server_JK_NUMBER);
			changePwdResp.setDest(srcNum);
			if (status == 1) {
				changePwdResp.setState((byte) 1);
			} else {
				changePwdResp.setState((byte) 0);
			}
			try {
				this.sendMsg2Me(changePwdResp);
			} catch (IOException e) {
				e.printStackTrace();
			}

		} else if (msg.getType() == IMsgConstance.command_leave) {
			int jknum = msg.getSrc();
			// 更新数据库的用户状态为不在线
			JkuserDaoImpl userImpl = new JkuserDaoImpl();
			userImpl.offOnline(jknum);
			ChatTools.removeClient(ChatTools.getUserByNum(jknum));
		} else if (msg.getType() == IMsgConstance.command_headerupload) {
			try {
				int jknum = msg.getSrc();
				File file = (File) obj;
				// 首先把文件保存到本地
				// 文件缩放至60*60大小
				String path = "F:/QQimg/u" + jknum + System.currentTimeMillis()
						+ ".jpg";
				BufferedImage bi = ImageUtil.compressImage(file, 60, 60);
				ImageIO.write(bi, "jpg", new FileOutputStream(path));
				// 修改数据库记录
				JkuserDaoImpl userImpl = new JkuserDaoImpl();
				int state = userImpl.updateIcon(jknum, path);

				// 发送消息给客户端
				MsgHeaderUploadResp headerUploadResp = new MsgHeaderUploadResp();
				headerUploadResp.setSrc(IMsgConstance.Server_JK_NUMBER);
				headerUploadResp.setDest(jknum);
				headerUploadResp
						.setType(IMsgConstance.command_headerupload_resp);
				headerUploadResp.setState((byte) state);
				sendMsg2Me(headerUploadResp);
			} catch (Exception e) {
				e.printStackTrace();
			}

		} else if (msg.getType() == IMsgConstance.command_msgUpdate) {
			Jkuser user = (Jkuser) obj;
			JkuserDaoImpl daoImpl = new JkuserDaoImpl();
			int state = daoImpl.updateUserInfo(user);
			MsgUpdateResp msgUpdateResp = new MsgUpdateResp();
			msgUpdateResp.setSrc(IMsgConstance.Server_JK_NUMBER);
			msgUpdateResp.setDest(user.getJknum());
			msgUpdateResp.setType(IMsgConstance.command_msgUpdate_resp);
			msgUpdateResp.setState((byte) state);
			try {
				sendMsg2Me(msgUpdateResp);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else if (msg.getType() == IMsgConstance.command_confirmPort) {
			Jkuser user = ChatTools.getUserByNum(msg.getSrc());
			ChatTools.addClient(user, address);
		} else if (msg.getType() == IMsgConstance.command_find) {

			try {
				// 查找请求
				MsgFind find = (MsgFind) msg;
				int findId = find.getFindId();
				byte classify = find.getClassify();
				// 到数据库中查询数据
				Object object = BaseJdbcDao.findById(classify, findId);
				MsgFindResp findResp = new MsgFindResp();
				findResp.setSrc(IMsgConstance.Server_JK_NUMBER);
				findResp.setDest(msg.getSrc());
				findResp.setType(IMsgConstance.command_find_resp);
				if (object == null) {
					findResp.setState((byte) 0);
					sendMsg2Me(findResp);
				} else {
					findResp.setState((byte) 1);
					sendMsg2Me(findResp);
					// 直接把对象信息序列化到客户端
					writeObjectToClient(object);
				}
				return;
			} catch (Exception e) {
				e.printStackTrace();
			}

		} else {
			try {
				ChatTools
						.sendMsg2One(ChatTools.getUserByNum(msg.getSrc()), msg);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * 检查登陆是否成功
	 * 
	 * @param ml
	 *            接受的MsgLogin对象
	 * @return
	 */
	private Jkuser checkLogin(MsgLogin ml) {
		JkuserDaoImpl impl = new JkuserDaoImpl();
		Jkuser user = impl.checkLogin(ml.getSrc(),
				MD5Util.MD5(ml.getPassword()), ml.getState());
		return user;
	}

	/**
	 * 将对象转化成字节流然后传送到客户端
	 * 
	 * @param object
	 */
	private void writeObjectToClient(Object object) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(object);
			oos.flush();
			DatagramPacket packet = new DatagramPacket(baos.toByteArray(),
					baos.toByteArray().length, address);
			socket.send(packet);
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/**
	 * 发送回应消息给指定的
	 * 
	 * @param msg
	 * @return
	 * @throws IOException
	 */
	public void sendMsg2Me(MsgHead msg) throws IOException {
		byte[] data = ToolsCreateMsg.packMsg(msg);
		DatagramPacket packet = new DatagramPacket(data, data.length, address);
		System.out.println("address:" + address);
		socket.send(packet);
		LogTools.INFO(MsgHead.class, msg);
	}

}
