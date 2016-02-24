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
 * 保存在线用户的基本信息和登陆IP地址
 * 
 * @author yy
 * 
 */
public class ChatTools {
	// 保存登陆用户的信息和客户端IP地址
	static Map<Jkuser, InetSocketAddress> stList = new HashMap();
	private static DatagramSocket socket = null;
	private static Map<Integer, InetSocketAddress> addrMap = new HashMap<Integer, InetSocketAddress>();
	private static int startPort = 10000;	//群聊开始端口  依次++
	//群号和端口号码的映射
	private static Map<Integer, Integer> portMap = new HashMap<Integer, Integer>();
	
	private ChatTools() {
	}// 不需要创建引类对象,构造器则私有

	/**
	 * 用户登录成功后 将IP地址和用户的基本信息存入Map中 向好友发送上线提醒
	 * 
	 * @param user
	 * @param ip
	 */
	public synchronized static void addClient(Jkuser user, InetSocketAddress ip) {
		stList.put(user, ip);
		addrMap.put(user.getJknum(), ip);
		sendCastPortToClient(user, ip);
		// 发送其上线的消息
		if (user.getState() == 1) {
			sendOnOffLineMsg(user, true);
		}
		sendAddressToClient(user.getJknum() ,ip);
	}

	/**
	 * 发送群聊端口到指定的客户端
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
	 * 用户退出登录之后 移除map中的基本信息和IP地址 向好友发送离线信息
	 * 
	 * @param user
	 */
	public static void removeClient(Jkuser user) {
		stList.remove(user);
		addrMap.remove(user.getJknum());
		// 同时向好友发送离线通知
		if (user.getState() == 1) {
			sendOnOffLineMsg(user, false);
		}
	}
	
	/**
	 * 把地址信息发送到客户端
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
			//发送InetSocketAddress对象
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
	 * 当这个用户的好友发送 上线/下线消息
	 * 
	 * @param user
	 *            :上线/下线的用户
	 */
	public static void sendOnOffLineMsg(Jkuser user, boolean onLine) {
		// 给这个用户的好友发送：我己上线的消息
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
	 * 根据jknum在stList集合中得到user对象
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
	 * 把某个消息发送到一个指定的客户端
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
	 * 把某个消息发送到一个指定的客户端
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
	 * 把字节流数据发送到一个指定的客户端
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
	 * 把字节流数据发送到一个指定的客户端
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
	 * 给队列中的某一个用户发送消息
	 * 
	 * @param srcUser
	 *            ：发送者
	 * @param msg
	 *            :消息内容
	 * @throws SQLException
	 */
	public static synchronized void sendMsg2One(Jkuser srcUser, MsgHead msg)
			throws SQLException {
		if (msg.getType() == IMsgConstance.command_chatText) {
			// 文本聊天消息
			// MsgChatText chatText = (MsgChatText) msg;
			// // 数据库中添加记录
			// ChatLog chatLog = new ChatLog();
			// chatLog.setContent(chatText.getCharTxt());
			// chatLog.setSrcid(chatText.getSrc());
			// chatLog.setDestid(chatText.getDest());
			// chatLog.setSendtime(chatText.getSendTime());
			// // 判断消息接受者是否在线 如果在线 状态设置为1表示已接收 否则0表示未接收
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
			// // 如果对方在线 就把消息直接发送到对方那里
			// if (flag) {
			// stList.get(dest).sendMsg2Me(chatText);
			// }
			return;
		} else if (msg.getType() == IMsgConstance.command_addFriend) {
			int destNum = msg.getDest();
			// 若目标用户在线 直接把消息转发给目标用户 否则把数据存储到数据库
			if (stList.get(getUserByNum(destNum)) == null) {
				FriendApplyDaoImpl applyDaoImpl = new FriendApplyDaoImpl();
				applyDaoImpl.addLog(msg.getSrc(), msg.getDest(), 0);
			} else {
				sendMsgToOneClient(destNum, msg);
			}
		} else if (msg.getType() == IMsgConstance.command_addFriend_resp) {
			MsgAddFriendResp addFriendResp = (MsgAddFriendResp) msg;
			int destNum = msg.getDest();
			// 若目标用户在线 那么直接转发给目标用户 否则存到数据库
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
				// 顺便把申请入群的用户序列化到客户端
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
				 * 添加群组-好友映射
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
					// 群的基本信息序列化到本地
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
			// // 添加群组-文件映射
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
			// // 文件添加完成后 添加映射
			// daoImpl.addCfMapping(chatFile.getDestCid(), fid);
			//
			// for (int i = 0; i < uList.size(); i++) {
			// if (uList.get(i).getJknum() == chatFile.getSrc())
			// continue;
			// // 在线直接转发 不在线把记录存到数据库中
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

			// 向客户端发送添加分组回应消息
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

			// 向客户端发送删除好友回应消息
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

			// 判断对方在不在线 在线的话同样要发送删除好友消息回应
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

			// 向客户端发送分组删除回应消息
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
			// 首先把头像保存到本地
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

			// 数据库中添加群记录
			CommunityDaoImpl communityDaoImpl = new CommunityDaoImpl();
			int cid = communityDaoImpl.addCommunity(name, owner, des, path);

			// 向客户端发送回应消息
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
