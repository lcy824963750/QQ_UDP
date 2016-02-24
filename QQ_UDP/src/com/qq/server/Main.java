package com.qq.server;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import com.qq.model.ToolsParseMsg;
import com.qq.msg.IMsgConstance;
import com.qq.msg.MsgHead;
import com.qq.util.H2Server;
import com.qq.util.LogTools;

/**
 * QQ UDP服务器端开启 每当有客户端请求 就处理并给予回应
 * @author yy
 *
 */
public class Main extends Thread {
	
	private int port;	//服务器端口
	private JFrame frame = null;
	private DatagramSocket recvSocket = null;
	/**
	 * 创建服务器对象 并且传入端口号码
	 * @param port
	 */
	public Main(int port) {
		this.port = port;
	}
	
	public void run() {
		frame = new JFrame("服务器管理");
		frame.setLayout(null);
		frame.setBounds(0,0,400,200);
		
		JButton open = new JButton("开启服务器");
		JButton close = new JButton("关闭服务器");
		open.setBounds(60,30,100,40);
		close.setBounds(240,30,100,40);
		
		frame.add(open);
		frame.add(close);
		frame.setLocationRelativeTo(null);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);
		
		
		open.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				if(recvSocket != null) {
					JOptionPane.showMessageDialog(null, "服务器已开启,勿重复开启");
				}else {
					
					new Thread(new Runnable() {
						
						@Override
						public void run() {
							try {
								setupServer();
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}).start();
				}
			}
		});
		
		
		
		close.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				if(recvSocket!=null) {
					recvSocket.close();
				}
				JOptionPane.showMessageDialog(null, "服务器已关闭");
			}
		});
		
	}
	
	
	
	
	/**
	 * @throws Exception 
	 * 
	 */
	private synchronized void setupServer() throws Exception {
		try {
			recvSocket = new DatagramSocket(port);
			LogTools.INFO(this.getClass(), "服务器创建成功:" + port);
			JOptionPane.showMessageDialog(null, "服务器开启成功");
			
			
			File imgFile = new File("F:/QQimg"); 
			//检查默认头像图片是否存在
			if(!imgFile.exists()) {
				imgFile.mkdirs();
				File img = new File("./images/default_header.jpg");
				FileInputStream fins = new FileInputStream(img);
				FileOutputStream fos = new FileOutputStream("F:/QQimg/default_header.jpg");
				byte[] data = new byte[1024];
				int len = 0;
				while((len = fins.read(data)) != -1) {
					fos.write(data, 0, len);
				}
				fos.close();
			}else {
				if(!new File("F:/QQimg/default_header.jpg").exists()) {
					File img = new File("./images/default_header.jpg");
					FileInputStream fins = new FileInputStream(img);
					FileOutputStream fos = new FileOutputStream("F:/QQimg/default_header.jpg");
					byte[] data = new byte[1024];
					int len = 0;
					while((len = fins.read(data)) != -1) {
						fos.write(data, 0, len);
					}
					fos.close();
					
					
				}
			}
			
			File file = new File("E:/h2/qq.mv.db");
			if(!file.exists()) {
				new H2Server().start();
			}
			
			/**
			 * 对于接受到的每一个客户端数据包 都开启一个线程去处理
			 */
			System.out.println("UDP服务器等待接受数据："+recvSocket.getLocalSocketAddress());
			byte[] buffer = new byte[1024*1024*100];	//缓冲区
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
			
			while(true){
				recvSocket.receive(packet);
				SocketAddress address = packet.getSocketAddress();
				byte[] data = packet.getData();
				MsgHead head = ToolsParseMsg.parseMsg(data);
				Object obj = null;
				if(head.getType() == IMsgConstance.command_headerupload) {
					recvSocket.receive(packet);
					data = packet.getData();
					ByteArrayInputStream bins = new ByteArrayInputStream(data);
					ObjectInputStream oins = new ObjectInputStream(bins);
					obj = oins.readObject();
				}else if(head.getType() == IMsgConstance.command_msgUpdate) {
					recvSocket.receive(packet);
					data = packet.getData();
					ByteArrayInputStream bins = new ByteArrayInputStream(data);
					ObjectInputStream oins = new ObjectInputStream(bins);
					obj = oins.readObject();
				}
				ServerThread ss = new ServerThread(head, address, obj);
				ss.start();
			}
		} catch (IOException e) {
			LogTools.ERROR(this.getClass(), "服务器创建失败:" + e);
		}
	}
	
	
	public static void main(String[] args) {
		Main main = new Main(IMsgConstance.serverPort);
		main.start();
	}
}
