package com.qq.server;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import org.h2.engine.SysProperties;

import com.qq.dao.BaseJdbcDao;
import com.qq.dao.CommuApplyDapImpl;
import com.qq.dao.CommuApplyRespDaoImpl;
import com.qq.dao.CommuChatLogDaoImpl;
import com.qq.dao.CommunityDaoImpl;
import com.qq.dao.FriendApplyDaoImpl;
import com.qq.dao.FriendApplyRespDaoImpl;
import com.qq.dao.GroupDaoImpl;
import com.qq.dao.JkfileDaoImpl;
import com.qq.dao.JkuserDaoImpl;
import com.qq.model.ChatLog;
import com.qq.model.CommuApply;
import com.qq.model.CommuApplyResp;
import com.qq.model.Community;
import com.qq.model.Jkgroup;
import com.qq.model.Jkuser;
import com.qq.model.ToolsCreateMsg;
import com.qq.model.ToolsParseMsg;
import com.qq.msg.IMsgConstance;
import com.qq.msg.MsgAddCommunity;
import com.qq.msg.MsgAddCommunityResp;
import com.qq.msg.MsgAddFriendResp;
import com.qq.msg.MsgAddGroup;
import com.qq.msg.MsgAddGroupResp;
import com.qq.msg.MsgChatFile;
import com.qq.msg.MsgChatText;
import com.qq.msg.MsgCommuChatFile;
import com.qq.msg.MsgCommuChatText;
import com.qq.msg.MsgCreateCommunity;
import com.qq.msg.MsgCreateCommunityResp;
import com.qq.msg.MsgDeleteCommunity;
import com.qq.msg.MsgDeleteCommunityResp;
import com.qq.msg.MsgDeleteFriendResp;
import com.qq.msg.MsgDeleteGroup;
import com.qq.msg.MsgDeleteGroupResp;
import com.qq.msg.MsgFind;
import com.qq.msg.MsgFindResp;
import com.qq.msg.MsgHead;
import com.qq.msg.MsgHeaderUploadResp;
import com.qq.util.ImageUtil;
import com.qq.util.LogTools;

/**
 * ���������û��Ļ�����Ϣ�͵�½IP��ַ
 * 
 * @author yy
 * 
 */
public class ChatTools {
	// �����½�û�����Ϣ�Ϳͻ���IP��ַ
	static Map<Jkuser, InetSocketAddress> stList = new HashMap();
	private static DatagramSocket socket = null;
	private static Map<Integer, InetSocketAddress> addrMap = new HashMap<Integer, InetSocketAddress>();
	private static int startPort = 10000;	//Ⱥ�Ŀ�ʼ�˿�  ����++
	//Ⱥ�źͶ˿ں����ӳ��
	private static Map<Integer, Integer> portMap = new HashMap<Integer, Integer>();
	
	private ChatTools() {
	}// ����Ҫ�����������,��������˽��

	/**
	 * �û���¼�ɹ��� ��IP��ַ���û��Ļ�����Ϣ����Map�� ����ѷ�����������
	 * 
	 * @param user
	 * @param ip
	 */
	public synchronized static void addClient(Jkuser user, InetSocketAddress ip) {
		stList.put(user, ip);
		addrMap.put(user.getJknum(), ip);
		sendCastPortToClient(user, ip);
		// ���������ߵ���Ϣ
		if (user.getState() == 1) {
			sendOnOffLineMsg(user, true);
		}
		sendAddressToClient(user.getJknum() ,ip);
	}

	/**
	 * ����Ⱥ�Ķ˿ڵ�ָ���Ŀͻ���
	 * @param user
	 */
	private static void sendCastPortToClient(Jkuser user, InetSocketAddress ip) {
		List<Community> commuList = user.getCommuList();
		for (Community community : commuList) {
			int cid = community.getCid();
			if(portMap.get(cid) == null) {
				portMap.put(cid, startPort++);
			}
		}
		MsgHead head = new MsgHead();
		head.setSrc(IMsgConstance.serverPort);
		head.setDest(user.getJknum());
		head.setType(IMsgConstance.command_sendPort);
		sendMsgToOneClient(ip, head);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(portMap);
			oos.flush();
			sendMsgToOneClient(ip, baos.toByteArray());
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		
	}

	/**
	 * �û��˳���¼֮�� �Ƴ�map�еĻ�����Ϣ��IP��ַ ����ѷ���������Ϣ
	 * 
	 * @param user
	 */
	public static void removeClient(Jkuser user) {
		stList.remove(user);
		addrMap.remove(user.getJknum());
		// ͬʱ����ѷ�������֪ͨ
		if (user.getState() == 1) {
			sendOnOffLineMsg(user, false);
		}
	}
	
	/**
	 * �ѵ�ַ��Ϣ���͵��ͻ���
	 */
	private static void sendAddressToClient(int jknum, InetSocketAddress addr) {
		Set<Jkuser> uSet = stList.keySet();
		for (Jkuser user : uSet) {
			InetSocketAddress add = stList.get(user);
			MsgHead head = new MsgHead();
			head.setSrc(IMsgConstance.Server_JK_NUMBER);
			head.setDest(jknum);
			head.setType(IMsgConstance.command_sendAddr);
			sendMsgToOneClient(add, head);
			//����InetSocketAddress����
			ByteArrayOutputStream baos =  new ByteArrayOutputStream();
			try {
				ObjectOutputStream oos = new ObjectOutputStream(baos);
				oos.writeObject(addrMap);
				oos.flush();
				sendMsgToOneClient(add, baos.toByteArray());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
	}

	/**
	 * ������û��ĺ��ѷ��� ����/������Ϣ
	 * 
	 * @param user
	 *            :����/���ߵ��û�
	 */
	public static void sendOnOffLineMsg(Jkuser user, boolean onLine) {
		// ������û��ĺ��ѷ��ͣ��Ҽ����ߵ���Ϣ
		ArrayList<Jkgroup> gList = (ArrayList<Jkgroup>) user.getGroupList();
		for (int i = 0; i < gList.size(); i++) {
			Jkgroup jkgroup = gList.get(i);
			ArrayList<Jkuser> uList = (ArrayList<Jkuser>) jkgroup.getUserList();
			for (int j = 0; j < uList.size(); j++) {
				Jkuser jkuser = uList.get(j);
				if (stList.get(getUserByNum(jkuser.getJknum())) != null) {
					MsgHead head = new MsgHead();
					head.setSrc(user.getJknum());
					head.setDest(jkuser.getJknum());
					if (onLine) {
						head.setType(IMsgConstance.command_onLine);
					} else {
						head.setType(IMsgConstance.command_offLine);
					}
					sendMsgToOneClient(jkuser.getJknum(), head);
				}
			}

		}

		JkuserDaoImpl daoImpl = new JkuserDaoImpl();
		List<Integer> cidList = daoImpl.getAllCids(user.getJknum());
		if (cidList == null || cidList.size() == 0)
			return;
		for (int i = 0; i < cidList.size(); i++) {
			int cid = cidList.get(i);
			CommunityDaoImpl communityDaoImpl = new CommunityDaoImpl();
			List<Integer> uList = communityDaoImpl.getAllOnLineUsers(cid);
			for (int j = 0; j < uList.size(); j++) {
				int uid = uList.get(j);
				if (uid == user.getJknum())
					continue;
				MsgHead msgHead = new MsgHead();
				msgHead.setDest(cid);
				msgHead.setSrc(user.getJknum());
				if (onLine) {
					msgHead.setType(IMsgConstance.command_commu_onLine);
				} else {
					msgHead.setType(IMsgConstance.command_commu_offLine);
				}
				sendMsgToOneClient(uid, msgHead);
			}
		}
	}

	/**
	 * ����jknum��stList�����еõ�user����
	 * 
	 * @param jknum
	 * @return
	 */
	public static Jkuser getUserByNum(int jknum) {
		Jkuser jkuser = null;
		Set<Jkuser> set = stList.keySet();
		Iterator<Jkuser> iterator = set.iterator();
		while (iterator.hasNext()) {
			Jkuser jkuser2 = iterator.next();
			if (jkuser2.getJknum() == jknum) {
				jkuser = jkuser2;
				break;
			}
		}

		return jkuser;
	}
	
	/**
	 * ��ĳ����Ϣ���͵�һ��ָ���Ŀͻ���
	 * 
	 * @param destNum
	 */
	private static void sendMsgToOneClient(InetSocketAddress addr, MsgHead msg) {
		if (socket == null) {
			try {
				socket = new DatagramSocket();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		try {
			byte[] buffer = ToolsCreateMsg.packMsg(msg);
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length,
					addr);
			socket.send(packet);
			System.out.println("address2 " + addr);
		} catch (Exception e) {
			e.printStackTrace();
		}
		LogTools.INFO(msg.getClass(), msg);
	}

	/**
	 * ��ĳ����Ϣ���͵�һ��ָ���Ŀͻ���
	 * 
	 * @param destNum
	 */
	private static void sendMsgToOneClient(int destNum, MsgHead msg) {
		Jkuser destUser = getUserByNum(destNum);
		InetSocketAddress addr = stList.get(destUser);
		if (socket == null) {
			try {
				socket = new DatagramSocket();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		try {
			byte[] buffer = ToolsCreateMsg.packMsg(msg);
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length,
					addr);
			socket.send(packet);
			System.out.println("address2 " + addr);
		} catch (Exception e) {
			e.printStackTrace();
		}
		LogTools.INFO(msg.getClass(), msg);
	}
	
	/**
	 * ���ֽ������ݷ��͵�һ��ָ���Ŀͻ���
	 * 
	 * @param destNum
	 */
	private static void sendMsgToOneClient(int destNum, byte[] buffer) {
		Jkuser destUser = getUserByNum(destNum);
		InetSocketAddress addr = stList.get(destUser);
		if (socket == null) {
			try {
				socket = new DatagramSocket();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		try {
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length,
					addr);
			socket.send(packet);
			System.out.println("address2 " + addr);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * ���ֽ������ݷ��͵�һ��ָ���Ŀͻ���
	 * 
	 * @param destNum
	 */
	private static void sendMsgToOneClient(InetSocketAddress addr, byte[] buffer) {
		if (socket == null) {
			try {
				socket = new DatagramSocket();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		try {
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length,
					addr);
			socket.send(packet);
			System.out.println("address2 " + addr);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * �������е�ĳһ���û�������Ϣ
	 * 
	 * @param srcUser
	 *            ��������
	 * @param msg
	 *            :��Ϣ����
	 * @throws SQLException
	 */
	public static synchronized void sendMsg2One(Jkuser srcUser, MsgHead msg)
			throws SQLException {
		if (msg.getType() == IMsgConstance.command_chatText) {
			// �ı�������Ϣ
			// MsgChatText chatText = (MsgChatText) msg;
			// // ���ݿ�����Ӽ�¼
			// ChatLog chatLog = new ChatLog();
			// chatLog.setContent(chatText.getCharTxt());
			// chatLog.setSrcid(chatText.getSrc());
			// chatLog.setDestid(chatText.getDest());
			// chatLog.setSendtime(chatText.getSendTime());
			// // �ж���Ϣ�������Ƿ����� ������� ״̬����Ϊ1��ʾ�ѽ��� ����0��ʾδ����
			// boolean flag = false;
			// Set<Jkuser> uSet = stList.keySet();
			// Iterator<Jkuser> iterator = uSet.iterator();
			// Jkuser dest = null;
			// while (iterator.hasNext()) {
			// Jkuser user = iterator.next();
			// if (user.getJknum() == chatText.getDest()) {
			// flag = true;
			// dest = user;
			// break;
			// }
			// }
			//
			// if (flag) {
			// chatLog.setState(1);
			// } else {
			// chatLog.setState(0);
			// }
			// ChatLogDaoImpl chatLogDaoImpl = new ChatLogDaoImpl();
			// int state = chatLogDaoImpl.save(chatLog);
			// // ����Է����� �Ͱ���Ϣֱ�ӷ��͵��Է�����
			// if (flag) {
			// stList.get(dest).sendMsg2Me(chatText);
			// }
			return;
		} else if (msg.getType() == IMsgConstance.command_addFriend) {
			int destNum = msg.getDest();
			// ��Ŀ���û����� ֱ�Ӱ���Ϣת����Ŀ���û� ��������ݴ洢�����ݿ�
			if (stList.get(getUserByNum(destNum)) == null) {
				FriendApplyDaoImpl applyDaoImpl = new FriendApplyDaoImpl();
				applyDaoImpl.addLog(msg.getSrc(), msg.getDest(), 0);
			} else {
				sendMsgToOneClient(destNum, msg);
			}
		} else if (msg.getType() == IMsgConstance.command_addFriend_resp) {
			MsgAddFriendResp addFriendResp = (MsgAddFriendResp) msg;
			int destNum = msg.getDest();
			// ��Ŀ���û����� ��ôֱ��ת����Ŀ���û� ����浽���ݿ�
			if (stList.get(getUserByNum(destNum)) == null) {
				FriendApplyRespDaoImpl applyRespDaoImpl = new FriendApplyRespDaoImpl();
				applyRespDaoImpl.add(msg.getSrc(), destNum, 0,
						addFriendResp.getRes());
			} else {
				sendMsgToOneClient(destNum, msg);
			}
		} else if (msg.getType() == IMsgConstance.command_addCommunity) {
			MsgAddCommunity addCommunity = (MsgAddCommunity) msg;
			int cid = addCommunity.getDestCid();
			CommunityDaoImpl communityDaoImpl = new CommunityDaoImpl();
			int owner = communityDaoImpl.getOwnerByCid(cid);
			addCommunity.setDest(owner);
			if (stList.get(getUserByNum(owner)) == null) {
				CommuApply apply = new CommuApply();
				apply.setCid(addCommunity.getDestCid());
				apply.setDestid(owner);
				apply.setSrcid(addCommunity.getSrc());
				apply.setState(0);
				CommuApplyDapImpl applyDapImpl = new CommuApplyDapImpl();
				applyDapImpl.save(apply);
			} else {
				sendMsgToOneClient(owner, addCommunity);
				// ˳���������Ⱥ���û����л����ͻ���
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				try {
					ObjectOutputStream oos = new ObjectOutputStream(baos);
					JkuserDaoImpl daoImpl = new JkuserDaoImpl();
					oos.writeObject(daoImpl.getBasicInfo(addCommunity.getSrc()));
				} catch (IOException e) {
					e.printStackTrace();
				}
				sendMsgToOneClient(owner, baos.toByteArray());
			}
		} else if (msg.getType() == IMsgConstance.command_addCommunity_resp) {
			MsgAddCommunityResp addCommunityResp = (MsgAddCommunityResp) msg;
			CommunityDaoImpl communityDaoImpl = new CommunityDaoImpl();
			if (addCommunityResp.getRes() == 1) {
				/**
				 * ���Ⱥ��-����ӳ��
				 */
				communityDaoImpl.insertLog(addCommunityResp.getDest(),
						addCommunityResp.getDestcid());
			}
			if (stList.get(getUserByNum(addCommunityResp.getDest())) == null) {
				CommuApplyResp applyResp = new CommuApplyResp();
				applyResp.setCid(addCommunityResp.getDestcid());
				applyResp.setSrcid(addCommunityResp.getSrc());
				applyResp.setDestid(addCommunityResp.getDest());
				applyResp.setState(0);
				applyResp.setRes(addCommunityResp.getRes());
				CommuApplyRespDaoImpl applyRespDaoImpl = new CommuApplyRespDaoImpl();
				applyRespDaoImpl.save(applyResp);
			} else {
				sendMsgToOneClient(addCommunityResp.getDest(), addCommunityResp);
				if (addCommunityResp.getRes() == 1) {
					// Ⱥ�Ļ�����Ϣ���л�������
					try {
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						ObjectOutputStream oos = new ObjectOutputStream(baos);
						oos.writeObject(communityDaoImpl
								.getBasicInfo(addCommunityResp.getDestcid()));
						oos.flush();
						sendMsgToOneClient(addCommunityResp.getDest(), baos.toByteArray());
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		} else if (msg.getType() == IMsgConstance.command_commuChatFile) {
			// MsgCommuChatFile chatFile = (MsgCommuChatFile) msg;
			// int cid = chatFile.getDestCid();
			// CommunityDaoImpl communityDaoImpl = new CommunityDaoImpl();
			// Community community = communityDaoImpl.getBasicInfo(cid);
			// List<Jkuser> uList = community.getUserList();
			// // ���Ⱥ��-�ļ�ӳ��
			// String path = "F:/QQServer/" + chatFile.getSrc()
			// + chatFile.getFileName();
			// try {
			// BufferedOutputStream bos = new BufferedOutputStream(
			// new FileOutputStream(path));
			// bos.write(chatFile.getFileData());
			// bos.flush();
			// } catch (FileNotFoundException e) {
			// e.printStackTrace();
			// } catch (IOException e) {
			// e.printStackTrace();
			// }
			//
			// JkfileDaoImpl daoImpl = new JkfileDaoImpl();
			// int fid = daoImpl.addFile(path, chatFile.getFileName(),
			// chatFile.getSrc(), chatFile.getSendTime());
			//
			// // �ļ������ɺ� ���ӳ��
			// daoImpl.addCfMapping(chatFile.getDestCid(), fid);
			//
			// for (int i = 0; i < uList.size(); i++) {
			// if (uList.get(i).getJknum() == chatFile.getSrc())
			// continue;
			// // ����ֱ��ת�� �����߰Ѽ�¼�浽���ݿ���
			// if (stList.get(getUserByNum(uList.get(i).getJknum())) == null) {
			//
			// daoImpl.addUcfMapping(uList.get(i).getJknum(), cid, fid);
			//
			// } else {
			// stList.get(getUserByNum(uList.get(i).getJknum()))
			// .sendMsg2Me(chatFile);
			// }
			//
			// }

		} else if (msg.getType() == IMsgConstance.command_addGroup) {
			MsgAddGroup addGroup = (MsgAddGroup) msg;
			GroupDaoImpl daoImpl = new GroupDaoImpl();
			int uid = addGroup.getSrc();
			String groupName = addGroup.getGroupName();
			int gid = daoImpl.addGroup(groupName, uid);

			// ��ͻ��˷�����ӷ����Ӧ��Ϣ
			MsgAddGroupResp addGroupResp = new MsgAddGroupResp();
			addGroupResp.setType(IMsgConstance.command_addGroup_resp);
			addGroupResp.setSrc(gid);
			addGroupResp.setDest(addGroup.getSrc());

			addGroupResp.setState((byte) 1);

			sendMsgToOneClient(msg.getSrc(), addGroupResp);

		} else if (msg.getType() == IMsgConstance.command_deleteFriend) {
			int srcNum = msg.getSrc();
			int destNum = msg.getDest();
			GroupDaoImpl daoImpl = new GroupDaoImpl();
			int gid1 = daoImpl.getGidByJknum(srcNum, destNum);
			int gid2 = daoImpl.getGidByJknum(destNum, srcNum);
			int state1 = daoImpl.deleteFriends(srcNum, gid1);
			int state2 = daoImpl.deleteFriends(destNum, gid2);

			// ��ͻ��˷���ɾ�����ѻ�Ӧ��Ϣ
			MsgDeleteFriendResp deleteFriendResp = new MsgDeleteFriendResp();
			deleteFriendResp.setType(IMsgConstance.command_deleteFriend_resp);
			deleteFriendResp.setSrc(msg.getSrc());
			deleteFriendResp.setDest(msg.getDest());
			if (state1 == 1 && state2 == 1) {
				deleteFriendResp.setState((byte) 1);
			} else {
				deleteFriendResp.setState((byte) 0);
			}
			deleteFriendResp.setGid(gid2);
			sendMsgToOneClient(srcNum, deleteFriendResp);

			// �ж϶Է��ڲ����� ���ߵĻ�ͬ��Ҫ����ɾ��������Ϣ��Ӧ
			if (stList.get(getUserByNum(destNum)) != null) {
				if (state1 == 1 && state2 == 1) {
					deleteFriendResp.setSrc(destNum);
					deleteFriendResp.setDest(srcNum);
					deleteFriendResp.setGid(gid1);
					sendMsgToOneClient(destNum, deleteFriendResp);

				}
			}

		} else if (msg.getType() == IMsgConstance.command_deleteGroup) {
			MsgDeleteGroup deleteGroup = (MsgDeleteGroup) msg;
			int gid = deleteGroup.getGid();
			int srcNum = deleteGroup.getSrc();

			GroupDaoImpl daoImpl = new GroupDaoImpl();
			int state = daoImpl.deleteGroup(gid);

			// ��ͻ��˷��ͷ���ɾ����Ӧ��Ϣ
			MsgDeleteGroupResp deleteGroupResp = new MsgDeleteGroupResp();
			deleteGroupResp.setType(IMsgConstance.command_deleteGroup_resp);
			deleteGroupResp.setSrc(IMsgConstance.Server_JK_NUMBER);
			deleteGroupResp.setDest(srcNum);
			deleteGroupResp.setGid(gid);
			if (state == 1) {
				deleteGroupResp.setState((byte) 1);
			} else {
				deleteGroupResp.setState((byte) 0);
			}

			sendMsgToOneClient(srcNum, deleteGroupResp);

		} else if (msg.getType() == IMsgConstance.command_createCommunity) {
			MsgCreateCommunity community = (MsgCreateCommunity) msg;
			int owner = community.getSrc();
			String name = community.getcName();
			String des = community.getcDes();
			byte[] data = community.getIcon();
			// ���Ȱ�ͷ�񱣴浽����
			String path = "F:/QQimg/" + community.getFileName();
			System.out.println(community);
			try {
				System.out.println("path " + path);
				BufferedOutputStream bos = new BufferedOutputStream(
						new FileOutputStream(path));
				bos.write(data);
				bos.flush();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}

			// ���ݿ������Ⱥ��¼
			CommunityDaoImpl communityDaoImpl = new CommunityDaoImpl();
			int cid = communityDaoImpl.addCommunity(name, owner, des, path);

			// ��ͻ��˷��ͻ�Ӧ��Ϣ
			MsgCreateCommunityResp communityResp = new MsgCreateCommunityResp();
			communityResp.setType(IMsgConstance.command_createCommunity_resp);
			communityResp.setSrc(IMsgConstance.Server_JK_NUMBER);
			communityResp.setDest(owner);
			communityResp.setCid(cid);
			if (cid > 0) {
				communityResp.setState((byte) 1);
			} else {
				communityResp.setState((byte) 0);
			}

			sendMsgToOneClient(owner, communityResp);

		} else if (msg.getType() == IMsgConstance.command_deleteCommunity) {
			MsgDeleteCommunity community = (MsgDeleteCommunity) msg;
			int cid = community.getCid();
			CommunityDaoImpl communityDaoImpl = new CommunityDaoImpl();
			int state = communityDaoImpl.deleteCommunity(cid);

			MsgDeleteCommunityResp communityResp = new MsgDeleteCommunityResp();
			communityResp.setType(IMsgConstance.command_deleteCommunity_resp);
			communityResp.setSrc(IMsgConstance.Server_JK_NUMBER);
			communityResp.setDest(community.getSrc());
			communityResp.setCid(cid);
			if (state == 1) {
				communityResp.setState((byte) 1);
			} else {
				communityResp.setState((byte) 0);
			}

			sendMsgToOneClient(community.getSrc(), communityResp);

		}
	}
}
