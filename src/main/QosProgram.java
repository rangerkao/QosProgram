package main;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.channels.ClosedByInterruptException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;


public class QosProgram {

	static Connection conn = null;
	static Connection conn2=null;
	static String IP="203.142.105.18";
	static int waitTime=8000;
	static int period_Time=10;//min
	static Properties props =new Properties();
	static Logger logger =null;
	
	private static String errorMsg;
	
	static String resultCode;	
	
	private static  void loadProperties(){
		System.out.println("initial Log4j, property !");
		String path=QosProgram.class.getResource("").toString().replaceAll("file:", "")+"Log4j.properties";
		System.out.println("path : "+path);
		try {
			props.load(new   FileInputStream(path));
			PropertyConfigurator.configure(props);
			logger =Logger.getLogger(QosProgram.class);
			logger.info("Logger Load Success!");
			
			String cip=props.getProperty("program.QosIP");
			
			if(cip!=null && !"".equals(cip))
				IP=cip;
			logger.info("Set external IP = "+IP);
			
			String p=props.getProperty("program.QosPeriod");
			if(p!=null && !"".equals(p) && p.matches("^\\d+$"))
				period_Time=Integer.parseInt(p);
			logger.info("Set period time "+p);
			
			String w=props.getProperty("program.waitTime");
			if(w!=null && !"".equals(w) && w.matches("^\\d+$"))
				waitTime=Integer.parseInt(w);
			logger.info("Set wait time "+w);

		} catch (FileNotFoundException e) {
			logger.error("Got FileNotFoundException,File Path : "+path,e);
		} catch (IOException e) {
			logger.info("Got IOException ",e);
		}
		
	}
	
	private static void connectDB(){
		//conn=tool.connDB(logger, DriverClass, URL, UserName, PassWord);
		try {
			connect1();
			connect2();
		} catch (ClassNotFoundException e) {
			
			logger.error("Error at connDB",e);
			//sendMail
			errorMsg=e.getMessage();
			sendMail("Error at connDB");
		} catch (SQLException e) {
			logger.error("Error at connDB",e);
			//sendMail
			errorMsg=e.getMessage();
			sendMail("Error at connDB");
		}
	}
	
	private static void connect2() throws ClassNotFoundException, SQLException{
		String url=props.getProperty("mBOSS.URL")
				.replaceAll("\\{\\{Host\\}\\}", props.getProperty("mBOSS.Host"))
				.replaceAll("\\{\\{Port\\}\\}", props.getProperty("mBOSS.Port"))
				.replaceAll("\\{\\{ServiceName\\}\\}", (props.getProperty("mBOSS.ServiceName")!=null?props.getProperty("mBOSS.ServiceName"):""))
				.replaceAll("\\{\\{SID\\}\\}", (props.getProperty("mBOSS.SID")!=null?props.getProperty("mBOSS.SID"):""));
		logger.info("Connrct to "+url);
		conn2=connDB(logger, props.getProperty("mBOSS.DriverClass"), url, 
				props.getProperty("mBOSS.UserName"), 
				props.getProperty("mBOSS.PassWord")
				);
		
	}
	private static void connect1() throws ClassNotFoundException, SQLException{
		String url=props.getProperty("Oracle.URL")
				.replaceAll("\\{\\{Host\\}\\}", props.getProperty("Oracle.Host"))
				.replaceAll("\\{\\{Port\\}\\}", props.getProperty("Oracle.Port"))
				.replaceAll("\\{\\{ServiceName\\}\\}", (props.getProperty("Oracle.ServiceName")!=null?props.getProperty("Oracle.ServiceName"):""))
				.replaceAll("\\{\\{SID\\}\\}", (props.getProperty("Oracle.SID")!=null?props.getProperty("Oracle.SID"):""));
		logger.info("Connrct to "+url);
		conn=connDB(logger, props.getProperty("Oracle.DriverClass"), url, 
				props.getProperty("Oracle.UserName"), 
				props.getProperty("Oracle.PassWord")
				);
	}
	
	public static Connection connDB(Logger logger, String DriverClass, String URL,
			String UserName, String PassWord) throws ClassNotFoundException, SQLException {

		Connection conn = null;
		Class.forName(DriverClass);
		conn = DriverManager.getConnection(URL, UserName, PassWord);
		return conn;
	}
	

	private static void closeConnect() {
		logger.info("Close connection...");
		if (conn != null) {

			try {
				conn.close();
			} catch (SQLException e) {
				StringWriter s = new StringWriter();
				e.printStackTrace(new PrintWriter(s));
				errorMsg=s.toString();
				logger.debug("close Connect Error : ",e);
				//sendMail
				sendMail("close Connect Error \n"+s);
			}
		}		
	}
	
	
	private static void excuteByMsisdn(String msisdn,String action) {
		logger.info("excuteByMsisdn..."+msisdn);
		
		Statement st = null;
		ResultSet rs = null;
				
		String plan = "2";
		//String action = "A";
		String IMSI = null;
		try {
			st = conn.createStatement();
			
			String sql = "select IMSI from imsi A,service B where A.serviceid = B.serviceid and B.servicecode like '%"+msisdn+"'";
			
			logger.info("Execute sql:"+sql);
			rs = st.executeQuery(sql);
			
			while(rs.next()){
				IMSI = rs.getString("IMSI");
			}
			
			if(IMSI==null){
				logger.error("Can't find IMSI!");
				return;
			}
			
			rs.close();
			
			String sql2 = 
					"SELECT CASE a.SERVICECODE WHEN 'SX001' THEN '3' WHEN 'SX002' THEN '4' ELSE '2' END PLAN "
					+ "FROM ADDONSERVICE_N A "
					+ "WHERE A.STARTDATE < SYSDATE AND (SYSDATE < A.ENDDATE OR A.ENDDATE IS NULL) "
					+ "AND  A.S2TIMSI = '"+IMSI+"' AND A.S2TMSISDN like '%"+msisdn+"'";
			
			logger.debug("Excute Sql : "+sql2);
			rs = st.executeQuery(sql2);

			while(rs.next()){
				plan = rs.getString("PLAN");
			}

		} catch (SQLException e) {
			logger.error("Got SQLException",e);
		}finally{
			try {
				if(st != null)
					st.close();
				if(rs != null)
					rs.close();
			} catch (SQLException e) {
			}
		}
		
		excutePost("1", msisdn, IMSI, setDayTime(), "S", action, plan);
	}

	//set run time
		private static String setDayTime(){ 
			SimpleDateFormat sdf = new SimpleDateFormat("dd---yyyy.HH:mm:ss");
			String dString=sdf.format(new Date());
			int dm=Calendar.getInstance().get(Calendar.MONTH)+1;
			switch(dm){
				case 1:
					dString=dString.replaceAll("---", "-JAN-");//Jan, Feb, Mar, Apr, May, Jun, Jul, Aug, Sep, Oct, Nov, Dec
					break;
				case 2:
					dString=dString.replaceAll("---", "-FEB-");
					break;
				case 3:
					dString=dString.replaceAll("---", "-MAR-");
					break;
				case 4:
					dString=dString.replaceAll("---", "-APR-");
					break;
				case 5:
					dString=dString.replaceAll("---", "-MAY-");
					break;
				case 6:
					dString=dString.replaceAll("---", "-JUN-");
					break;
				case 7:
					dString=dString.replaceAll("---", "-JUL-");
					break;
				case 8:
					dString=dString.replaceAll("---", "-AUG-");
					break;
				case 9:
					dString=dString.replaceAll("---", "-SEP-");
					break;
				case 10:
					dString=dString.replaceAll("---", "-OCT-");
					break;
				case 11:
					dString=dString.replaceAll("---", "-NOV-");
					break;
				case 12:
					dString=dString.replaceAll("---", "-DEC-");
					break;
				default:
			}
			return dString;
		}
	
	public static void readtxt(String filePath) throws InterruptedException {
			
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), "UTF-8")); 
			// 指定讀取文件的編碼格式，以免出現中文亂碼

			String str = null;

			while ((str = reader.readLine()) != null) {
				
				//System.out.println(str);
				String s=str.trim();

				String[] ims=s.split(",");
				
				//20150420 調整txt 欄位   IMSI MSISDN PLAN ACTION
				if(ims==null||"".equals(ims[0])||ims[0].startsWith("#")){
					logger.info(s);
					continue;
				}
				
				if(ims.length<4){
					logger.error("Parameter Error :"+s);
					continue;
				}
				
				excutePost("1", ims[1], ims[0], setDayTime(), "S", ims[3], ims[2]);
			}

		} catch (FileNotFoundException e) {
			logger.error("Got FileNotFoundException",e);
		} catch (IOException e) {
			logger.error("Got IOException",e);
		} finally {
			try {
				if(reader!=null){
					reader.close();
				}
			} catch (IOException e) {
			}
		}
	}
	
	
	public static String HttpPost(String url,String param,String charset) throws IOException{
		resultCode="";
		URL obj = new URL(url);
		
		if(charset!=null && !"".equals(charset))
			param=URLEncoder.encode(param, charset);
		
		
		HttpURLConnection con =  (HttpURLConnection) obj.openConnection();
 
		//add reuqest header
		/*con.setRequestMethod("POST");
		con.setRequestProperty("User-Agent", USER_AGENT);
		con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");*/
 
		// Send post request
		con.setDoOutput(true);
		DataOutputStream wr = new DataOutputStream(con.getOutputStream());
		wr.writeBytes(param);
		wr.flush();
		wr.close();
 
		int responseCode = con.getResponseCode();
		/*System.out.println("\nSending 'POST' request to URL : " + url);
		System.out.println("Post parameters : " + param);
		System.out.println("Response Code : " + responseCode);*/
 
		BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
		 String line;
		 String TAB = "\r";
	    StringBuffer response = new StringBuffer();
	      
	    while((line = reader.readLine()) != null) {  
	         response.append(line);
	         response.append(TAB);
	    }
	    reader.close();
	    resultCode=response.toString();
	
		return String.valueOf(responseCode);
	}
	
	//20150526 mod
		//mail host server had ended
		//change send from local machine, solaris not use mail conmand, is use mailx,and final location end by dot. 
		private static void sendMail(String msg){
			String ip ="";
			try {
				ip = InetAddress.getLocalHost().getHostAddress();
			} catch (UnknownHostException e) {
				StringWriter s = new StringWriter();
				e.printStackTrace(new PrintWriter(s));
				errorMsg=s.toString();
				logger.error(e);
			}
			
			msg=msg+" from location "+ip;			
			
			String [] cmd=new String[3];
			cmd[0]="/bin/bash";
			cmd[1]="-c";
			cmd[2]= "/bin/echo \""+msg+"\" | /bin/mailx -s \"Qos System alert\" -r Qos_PROGRAM_ALERT_MAIL "+props.getProperty("mail.Receiver")+"." ;

			try{
				Process p = Runtime.getRuntime().exec (cmd);
				p.waitFor();
				System.out.println("send mail cmd:"+cmd);
			}catch (Exception e){
				System.out.println("send mail fail:"+msg);
			}
		}
	
	/*private static void sendMail(String content){
		
		DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		mailReceiver=props.getProperty("mail.Receiver");
		mailSubject="Qos Warnning Mail";
		mailContent="Error :"+content+"<br>\n"
				+ "Error occurr time: "+df.format(new Date())+"<br>\n"
				+ "SQL : "+sql+"<br>\n"
				+ "Error Msg : "+errorMsg;

		try {
			if(mailReceiver==null ||"".equals(mailReceiver)){
				logger.error("Can't send email without receiver!");
			}else{
				sendMail(mailSender, mailReceiver, mailSubject, mailContent);
			}
			
		} catch (AddressException e) {
			e.printStackTrace();
			logger.error("Error at sendMail : "+e.getMessage());
			//sendMail
			//sendMail("At sendMail occur AddressException error!");
			//errorMsg=e.getMessage();
		} catch (MessagingException e) {
			e.printStackTrace();
			logger.error("Error at sendMail : "+e.getMessage());
			//sendMail
			//sendMail("At sendMail occur MessagingException error!");
			//errorMsg=e.getMessage();
		} catch (IOException e) {
			e.printStackTrace();
			logger.error("Error at sendMail : "+e.getMessage());
			//sendMail
			//sendMail("At sendMail occur IOException error!");
			//errorMsg=e.getMessage();
		}
	}
	
	public static void sendMail(String sender,String receiver,String subject,String content) throws AddressException, MessagingException, IOException {

		logger.info("get Properites!");			
		
		final String host=props.getProperty("mail.smtp.host");
		logger.info("Connect to Host : "+ host);
		
		String p=props.getProperty("mail.smtp.port");
		final Integer port=((p==null||"".equals(p))?null:Integer.valueOf(p));
		logger.info("port : "+port);
		
		final String username=props.getProperty("mail.username");
		final String passwd=props.getProperty("mail.password");		
		
		String auth = props.getProperty("mail.smtp.auth");
		boolean authFlag = true;
		if(auth==null||"".equals(auth)||"false".equals(auth)){
			authFlag=false;
		}
		logger.info("use authority : "+authFlag);
		
		boolean sessionDebug = false;
		boolean singleBody=true;
		
		if(sender==null || "".equals(sender)){
			if(username==null){
				logger.error("No sender and No UserName Set!");
				return;
			}
			sender=username;			
		}else{
			if(username!=null && !"".equals(username) &&!sender.equalsIgnoreCase(username)){
				logger.error("sender is not equals to UserName !");
				return;
			}
		}
		
		InternetAddress[] address = null; 
		String ccList="";
		
		
		StringBuffer messageText = new StringBuffer(); 
		messageText.append("<html><body>"); 
		messageText.append(content); 
		messageText.append("</body></html>"); 
		
		javax.mail.Session mailSession=null;
		logger.debug("Creat mail Session!");
		if(authFlag){
			// construct a mail session 
			mailSession = javax.mail.Session.getInstance(props,new javax.mail.Authenticator() {
			    protected PasswordAuthentication getPasswordAuthentication() {
			        return new PasswordAuthentication(username, passwd);
			    }
			}); 
		}else{
			mailSession = javax.mail.Session.getDefaultInstance(props);
		}
		
		mailSession.setDebug(sessionDebug); 
		
			Message msg = new MimeMessage(mailSession); 
			msg.setFrom(new InternetAddress(sender));			// mail sender 
			
			address = InternetAddress.parse(receiver, false); // mail recievers 
			msg.setRecipients(Message.RecipientType.TO, address); 
			msg.setRecipients(Message.RecipientType.CC, InternetAddress.parse(ccList)); // mail cc 
			
			msg.setSubject(subject); // mail's subject 
			msg.setSentDate(new Date());// mail's sending time 
			logger.debug("set mail content!");
			if(singleBody){
				//msg.setText(messageText.toString());
			    msg.setContent(messageText.toString(), "text/html;charset=UTF-8");
			}else{
				MimeBodyPart mbp = new MimeBodyPart();// mail's charset
				mbp.setContent(messageText.toString(), "text/html; charset=utf8"); 
				Multipart mp = new MimeMultipart(); 
				mp.addBodyPart(mbp); 
				msg.setContent(mp); 
			}

			if(receiver==null ||"".equals(receiver)){
				System.out.println("Can't send email without receiver!");
			}else{
				Transport.send(msg);
			}
			logger.info("sending mail from "+sender+" to "+receiver+"\n<br>"+
										"Subject : "+msg.getSubject()+"\n<br>"+
										"Content : "+msg.getContent()+"\n<br>"+
										"SendDate: "+msg.getSentDate());			
	}*/
	
	 private  static String excutePost(String VERSION,String MSISDN,String IMSI,String dayTime,String VENDOR,String ACTION,String PLAN){
		String url=	"http://"+IP+"/mvno_api/MVNO_UPDATE_QOS";
		String param="VERSION="+VERSION+"&MSISDN="+MSISDN+"&IMSI="+IMSI+"&DATE_TIME="+dayTime+"&VENDOR="+VENDOR+"&ACTION="+ACTION+"&PLAN="+PLAN+"";
		String result=null;
		
		Statement st = null;
		try {
			result = HttpPost(url,param,"");
			logger.info("Posted :"+url+"?"+param+"   \nresult:"+result);
			
			
			if(!"200".equals(result)){
				sendMail("Http connection status "+result+" is not correct at provinding data ("+param+") ");
			}
			
			resultCode = resultCode.trim().replaceAll("RETURN_CODE=", "").replaceAll("\n", "");
			
			if(!"0".equals(resultCode)){
				sendMail("The provision RETURN_CODE = "+resultCode+" of data ("+param+")  is not correct.");
			}
			
			String sql=
					"INSERT INTO QOS_PROVISION_LOG(PROVISIONID,IMSI,MSISDN,ACTION,PLAN,RESPONSE_CODE,RESULT_CODE,CERATE_TIME) "
					+ "VALUES(QOS_PROVISION_LOG_ID.NEXTVAL,'"+IMSI+"','"+MSISDN+"','"+ACTION+"','"+PLAN+"','"+result+"','"+resultCode+"',SYSDATE)";

			logger.debug("Excute Sql : "+sql);
			
			st = conn.createStatement();

			st.executeUpdate(sql);

			Thread.sleep(waitTime*1000);
			
		} catch (IOException e) {
			StringWriter s = new StringWriter();
			e.printStackTrace(new PrintWriter(s));
			errorMsg=s.toString();
			logger.error("For "+url+"?"+param+"   \nresult:"+result+"  at post url occur exception : ",e);
			//sendMail
			sendMail("For "+url+"?"+param+"   \nresult:"+result+"  at post url occur exception\n"+s);
		} catch (SQLException e) {
			StringWriter s = new StringWriter();
			e.printStackTrace(new PrintWriter(s));
			errorMsg=s.toString();
			logger.error("Write Log to DB occured error! : ",e);
			//sendMail
			sendMail("Write Log to DB occured error!\n"+s);
		} catch (InterruptedException e) {
			StringWriter s = new StringWriter();
			e.printStackTrace(new PrintWriter(s));
			errorMsg=s.toString();
			logger.error("Got InterruptedException ! : ",e);
			//sendMail
			sendMail("Got InterruptedException !\n"+s);
		}finally{
			try {
				if(st!=null) st.close();
			} catch (SQLException e) {
			}
		}
		
		return result;
		
	}
	 
	 public static void main(String args[]) throws InterruptedException{
			
			
			//args=new String[]{"85269477975"};
			
			
			
			if(args.length<1){
				System.out.println("lack parameter！");
				return;
			}
			
			loadProperties();
			connectDB();
			int msisdnLength = 8;
			int IMSILength = 15;
			/*if(args.length>1&&!"".equals(args[0])&&args[0].matches("^\\d+$")&&args[0].length()>=IMSILength &&
					!"".equals(args[1])&&args[1].matches("^\\d+$")&&args[1].length()>=msisdnLength){
				//純數字，Msisdn
				logger.info("execute by imsi,msisdn...");
				String msisdn = args[1].substring(args[1].length()-msisdnLength,args[1].length());
				excuteByMsisdn(args[0],msisdn);*/
			if(!"".equals(args[0])&&args[0].matches("^\\d+$")&&args[0].length()>=msisdnLength){
				//純數字，Msisdn
				logger.info("execute by imsi,msisdn...");
				String msisdn = args[0].substring(3,args[0].length());
				
				String action = "A";
				
				if(args.length>=2 && ("A".equalsIgnoreCase(args[1])||"D".equalsIgnoreCase(args[1])))
					action = args[1].toUpperCase();
				
				excuteByMsisdn(msisdn,action);
				
			}else if(!"".equals(args[0])&&args[0].matches("^\\w+\\.txt$")){
				//.txt 檔案
				logger.info("execute by file...");
				String filename;
				//filename ="C:/Users/ranger.kao/Dropbox/workspace/addonQos/src/Qosfile.txt";
				filename=args[0];
				readtxt(filename);
				
			}else{
				System.out.print("can't resolve parameter!");
				for(int i = 0 ;i<args.length;i++)
					System.out.print(","+args[i]);
				System.out.println();
			}
			closeConnect();
			
			
		}
}

