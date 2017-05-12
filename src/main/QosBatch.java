
/**
 * 2014/12/15開始運作
 *
 */

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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;


public class QosBatch extends  TimerTask implements Runnable {

	Connection conn = null,conn2=null;
	//static int runInterval = 1000*60*10; //10min
	static int period_Time=10;//min
	static String number_section=null;
	static String initialTime ;
	
	static Properties props =new Properties();
	static Logger logger =null;
	
	static String IP="203.142.105.18"; 
	String VERSION="1";
	String MSISDN="66407851";
	String IMSI="454120260232504";
	String DATE_TIME=null;
	String VENDOR="S";
	String ACTION="A";
	String PLAN="1";
	
	Date preTime; //last excute time
	Date nowTime; //now 
	static Date lastTime=null;
	String preTimeS; //last excute time
	String nowTimeS; //now 
	String sql="";
	private String errorMsg;
	private static long waitTime=8;
	//private static ExecutorService execService;
	static int tCount=0;
	
	
	static List postdatas = new ArrayList();
	
	private static  void loadProperties(){
		System.out.println("initial Log4j, property !");
		String path=QosBatch.class.getResource("").toString().replaceAll("file:", "")+"Log4j.properties";
		System.out.println("path : "+path);
		try {
			props.load(new   FileInputStream(path));
			PropertyConfigurator.configure(props);
			logger =Logger.getLogger(QosBatch.class);
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
			
			number_section = props.getProperty("number_section");

		} catch (FileNotFoundException e) {
			logger.error("Got FileNotFoundException,File Path : "+path,e);
		} catch (IOException e) {
			e.printStackTrace();
			logger.info("Got IOException ",e);
		}
		
	}
	
	private void connectDB(){
		//conn=tool.connDB(logger, DriverClass, URL, UserName, PassWord);
		try {
			connect1();
			connect2();
		} catch (ClassNotFoundException e) {
			StringWriter s = new StringWriter();
			e.printStackTrace(new PrintWriter(s));
			errorMsg=s.toString();
			logger.error("Error at connDB",e);
			//sendMail
			sendMail("Error at connDB\n"+s);
		} catch (SQLException e) {
			StringWriter s = new StringWriter();
			e.printStackTrace(new PrintWriter(s));
			errorMsg=s.toString();
			logger.error("Error at connDB",e);
			//sendMail
			sendMail("Error at connDB\n"+s);
		}
	}
	
	private void connect2() throws ClassNotFoundException, SQLException{
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
	private void connect1() throws ClassNotFoundException, SQLException{
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
	
	public Connection connDB(Logger logger, String DriverClass, String URL,
			String UserName, String PassWord) throws ClassNotFoundException, SQLException {

		Connection conn = null;
		Class.forName(DriverClass);
		conn = DriverManager.getConnection(URL, UserName, PassWord);
		return conn;
	}
	

	private void closeConnect() {
		logger.info("Close connection...");
		if (conn != null) {

			try {
				conn.close();
			} catch (SQLException e) {
				StringWriter s = new StringWriter();
				e.printStackTrace(new PrintWriter(s));
				errorMsg=s.toString();
				logger.debug("close Connect Error!",e);
				//sendMail
				sendMail("close Connect Error "+s);
				
			}
		}		
	}
	
	
	static SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
	
	private boolean setTime(){
		
		boolean result = true;
		
		
		nowTimeS = sdf.format(nowTime);
		
		if("000".equals(nowTimeS.substring(8,11))){
			sendMail("Qos notification mail ("+new Date()+") \n","ranger.kao@sim2travel.com");
		}
		
		
		if(lastTime==null){
			try {
				preTime=sdf.parse(initialTime);
			} catch (ParseException e) {
				StringWriter s = new StringWriter();
				e.printStackTrace(new PrintWriter(s));
				logger.debug("ParseException Error!",e);
				//sendMail
				sendMail("cParseException Error "+s);
				result = false;
			}
		}else{
			preTime=lastTime;
		}
		
		preTimeS = sdf.format(preTime);
		
		//preTimeS = "20170210093000";
		//nowTimeS = "20170210103000";
		
		logger.info("Proccess from "+preTimeS+" to "+nowTimeS);
		return result;
	}
	
	//set run time
	private String setDayTime(){
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
	//20150526 mod
	//mail host server had ended
	//change send from local machine, solaris not use mail conmand, is use mailx,and final location end by dot. 
	private void sendMail(String msg,String recevier){
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
		cmd[2]= "/bin/echo \""+msg+"\" | /bin/mailx -s \"Qos System alert\" -r  Qos_Batch_ALERT_MAIL "+recevier+"." ; ;

		try{
			Process p = Runtime.getRuntime().exec (cmd);
			p.waitFor();
			System.out.println("send mail cmd:"+cmd);
		}catch (Exception e){
			System.out.println("send mail fail:"+msg);
		}
	}
	private void sendMail(String msg){
		sendMail(msg,props.getProperty("mail.Receiver"));
	}
	
	/*private void sendMail(String content){
		
		DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		mailReceiver=props.getProperty("mail.Receiver");
		mailSubject="Qos Warnning Mail";
		mailContent="Error :"+content+"<br>\n"
				+ "Error occurr time: "+df.format(new Date())+"<br>\n"
				+ "SQL : "+sql+"<br>\n"
				+ "Error Msg : "+errorMsg;

		try {
			if(mailReceiver==null ||"".equals(mailReceiver)){
				System.out.println("Can't send email without receiver!");
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
	
	public void sendMail(String sender,String receiver,String subject,String content) throws AddressException, MessagingException, IOException {

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
	
	
	private String excutePost(String msisdn,String imsi,String action,String plan){
		String url=	"http://"+IP+"/mvno_api/MVNO_UPDATE_QOS";
		String param="VERSION="+VERSION+"&MSISDN="+msisdn+"&IMSI="+imsi+"&DATE_TIME="+setDayTime()+"&VENDOR="+VENDOR+"&ACTION="+action+"&PLAN="+plan+"";
		String result=null;
		
		try {
			result = HttpPost(url,param,"");
			logger.info("Posted :"+url+"?"+param+"   \nresult:"+result);
			
			sql=
					"INSERT INTO QOS_PROVISION_LOG(PROVISIONID,IMSI,MSISDN,ACTION,PLAN,RESPONSE_CODE,RESULT_CODE,CERATE_TIME) "
					+ "VALUES(QOS_PROVISION_LOG_ID.NEXTVAL,'"+imsi+"','"+msisdn+"','"+action+"','"+plan+"','"+result+"','"+resultCode+"',SYSDATE)";

			logger.debug("Excute Sql : "+sql);
			
			Statement st = conn.createStatement();

			st.executeUpdate(sql);
			
			if(st!=null) st.close();
			
			Thread.sleep(waitTime*1000);
			
		} catch (IOException e) {
			StringWriter s = new StringWriter();
			e.printStackTrace(new PrintWriter(s));
			errorMsg=s.toString();
			logger.error("For "+url+"?"+param+"   \nresult:"+result+"  at post url occur exception : ",e);
			//sendMail
			sendMail("Please redo this data.\nFor "+url+"?"+param+"   \nresult:"+result+"  at post url occur exception\n"+s);
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
			sendMail("Got InterruptedException !"+s);
		}
		
		return result;
		
	}
	
	static String resultCode;
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
	
	/*private boolean addQos(){
		logger.error("Excute add Qos...");
		ACTION="A";
		sql=
				"SELECT A.SERVICEID, SUBSTR(SERVICECODE,4,8) MSISDN, IMSI ,A.PRICEPLANID "
				+ "FROM SERVICE A, IMSI B "
				+ "WHERE A.SERVICEID=B.SERVICEID AND A.STATUS IN (1,3) "
				+ "AND TO_CHAR(A.DATEACTIVATED,'YYYYMMDDHH24MISS')>='"+preTimeS+"' "
				+ "AND TO_CHAR(A.DATEACTIVATED,'YYYYMMDDHH24MISS')<'"+nowTimeS+"' "
				+ "AND (A.SERVICECODE like '8526640%' OR  A.SERVICECODE like '8525609%'  OR A.SERVICECODE like '8526947%'  OR A.SERVICECODE like '8525392%' ) ";
		try {
			Statement st = conn2.createStatement();
			logger.info("Search add : "+sql);
			ResultSet rs = st.executeQuery(sql);
			while(rs.next()){
				MSISDN=rs.getString("MSISDN");
				IMSI=rs.getString("IMSI");
				String pricePlanId = rs.getString("PRICEPLANID");
				
				//20150702 cancel
				//if("158".equals(pricePlanId)||"159".equals(pricePlanId)||"160".equals(pricePlanId)){
				//因NTT 香港 160 不需限速，故拿掉
				if("158".equals(pricePlanId)||"159".equals(pricePlanId)){
					//20150409 mod
					//PLAN="1";
					PLAN="3";
				}else{
					PLAN="2";
				}
				
				if(MSISDN!=null && !"".equals(MSISDN) && IMSI!=null && !"".equals(IMSI)){
					excutePost();
				}else{
					logger.error(" Because of MSISDN  or IMSI is null  , Can't add Qos .");
				}
				
			}
			st.close();
			rs.close();
		} catch (SQLException e) {
			StringWriter s = new StringWriter();
			e.printStackTrace(new PrintWriter(s));
			errorMsg=s.toString();
			logger.error("Got SQLException",e);
			//sendMail
			sendMail("At Add new occure Exception("+new Date()+") \n"+s);
			return false;
		}
		return true;
	}*/
	
	/*private boolean deleteQos(){
		logger.error("Excute delete Qos...");
		ACTION="D";
		
		sql=
				"SELECT A.SERVICEID, SUBSTR(SERVICECODE,4,8) MSISDN, IMSI ,A.PRICEPLANID "
				+ "FROM SERVICE A, IMSI B, TERMINATIONORDER C "
				+ "WHERE A.SERVICEID=B.SERVICEID AND A.SERVICEID=C.TERMOBJID(+) "
				+ "AND TO_CHAR(C.COMPLETEDATE, 'YYYYMMDDHH24MISS')>='"+preTimeS+"' "
				+ "AND TO_CHAR(C.COMPLETEDATE, 'YYYYMMDDHH24MISS')<'"+nowTimeS+"' "
				+ "AND (A.SERVICECODE like '8526640%' OR  A.SERVICECODE like '8525609%'  OR A.SERVICECODE like '8526947%' OR A.SERVICECODE like '8525392%'  ) ";
	
		try {
			Statement st = conn2.createStatement();
			logger.info("Search delete : "+sql);
			ResultSet rs = st.executeQuery(sql);
			while(rs.next()){
				MSISDN=rs.getString("MSISDN");
				IMSI=rs.getString("IMSI");
				String pricePlanId = rs.getString("PRICEPLANID");
				//20150702 cancel
				//if("158".equals(pricePlanId)||"159".equals(pricePlanId)||"160".equals(pricePlanId)){
				//因NTT 香港 160 不需限速，故拿掉
				if("158".equals(pricePlanId)||"159".equals(pricePlanId)){
					//20150409
					//PLAN="1";
					PLAN="3";
				}else{
					PLAN="2";
				}
				
				if(MSISDN!=null && !"".equals(MSISDN) && IMSI!=null && !"".equals(IMSI)){
					excutePost();
				}else{
					logger.error(" Because of MSISDN  or IMSI is null  , Can't delete Qos .");
				}
				
			}
			st.close();
			rs.close();
		} catch (SQLException e) {
			StringWriter s = new StringWriter();
			e.printStackTrace(new PrintWriter(s));
			errorMsg=s.toString();
			logger.error("Got SQLException",e);
			//sendMail
			sendMail("At Cancel occure Exception("+new Date()+") \n"+s);
			return false;
		}
		return true;
	}*/
	
	private boolean addAndDeleteQos(){
		logger.error("Excute addAndDeleteQos Qos...");
		
		String sectionCondition = "" ;
		if(number_section!=null){
			
			sectionCondition += "AND ( 1=1 ";
			
			String[] sections = number_section.trim().split(",");
			
			for(int i = 0;i<sections.length;i++){
				sectionCondition += "OR A.SERVICECODE like '"+sections[i]+"%' ";
			}
			sectionCondition += ") ";
		}
		
		sql=
				"SELECT SERVICEID,  MSISDN, IMSI,PRICEPLANID,ACTION,TIME "
				+ "from( "
				+ "        SELECT A.SERVICEID, SUBSTR(SERVICECODE,4,8) MSISDN, IMSI ,A.PRICEPLANID,TO_CHAR(C.COMPLETEDATE, 'YYYYMMDDHH24MISS') TIME,'D' ACTION "
				+ "		FROM SERVICE A, IMSI B, TERMINATIONORDER C "
				+ "		WHERE A.SERVICEID=B.SERVICEID AND A.SERVICEID=C.TERMOBJID(+) "
				+ "		AND TO_CHAR(C.COMPLETEDATE, 'YYYYMMDDHH24MISS')>='"+preTimeS+"' "
				+ "		AND TO_CHAR(C.COMPLETEDATE, 'YYYYMMDDHH24MISS')<'"+nowTimeS+"' "
				+ sectionCondition
				//+ "		AND (A.SERVICECODE like '8526640%' OR  A.SERVICECODE like '8525609%'  OR A.SERVICECODE like '8526947%' OR A.SERVICECODE like '8525392%'  ) "
				+ "        UNION"
				+ "        SELECT A.SERVICEID, SUBSTR(SERVICECODE,4,8) MSISDN, IMSI ,A.PRICEPLANID, TO_CHAR(A.DATEACTIVATED,'YYYYMMDDHH24MISS') TIME,'A' ACTION"
				+ "				FROM SERVICE A, IMSI B "
				+ "				WHERE A.SERVICEID=B.SERVICEID AND A.STATUS IN (1,3) "
				+ "				AND TO_CHAR(A.DATEACTIVATED,'YYYYMMDDHH24MISS')>='"+preTimeS+"' "
				+ "				AND TO_CHAR(A.DATEACTIVATED,'YYYYMMDDHH24MISS')<'"+nowTimeS+"' "
				+ sectionCondition
				//+ "				AND (A.SERVICECODE like '8526640%' OR  A.SERVICECODE like '8525609%'  OR A.SERVICECODE like '8526947%'  OR A.SERVICECODE like '8525392%' ) "
				+ "        )"
				+ "        ORDER BY TIME";
	
		try {
			Statement st = conn2.createStatement();
			logger.info("Search add and delete : "+sql);
			ResultSet rs = st.executeQuery(sql);
			while(rs.next()){
				MSISDN=rs.getString("MSISDN");
				IMSI=rs.getString("IMSI");
				ACTION = rs.getString("ACTION");
				
				String pricePlanId = rs.getString("PRICEPLANID");
				//20150702 cancel
				//if("158".equals(pricePlanId)||"159".equals(pricePlanId)||"160".equals(pricePlanId)){
				//因NTT 香港 160 不需限速，故拿掉
				if("158".equals(pricePlanId)||"159".equals(pricePlanId)){
					//20150409
					//PLAN="1";
					PLAN="3";
				}else{
					PLAN="2";
				}
				
				if(MSISDN!=null && !"".equals(MSISDN) && IMSI!=null && !"".equals(IMSI)){
					Map m = new HashMap();
					m.put("MSISDN", MSISDN);
					m.put("PLAN", PLAN);
					m.put("ACTION", ACTION);
					m.put("IMSI", IMSI);
					m.put("TIME", rs.getString("TIME"));
					postdatas.add(m);
					//excutePost();
				}else{
					logger.error(" Because of MSISDN  or IMSI is null  , Can't delete Qos .");
				}
				
				
				
			}
			st.close();
			rs.close();
		} catch (SQLException e) {
			StringWriter s = new StringWriter();
			e.printStackTrace(new PrintWriter(s));
			errorMsg=s.toString();
			logger.error("Got SQLException",e);
			//sendMail
			sendMail("At Cancel occure Exception("+new Date()+") \n"+s);
			return false;
		}
		return true;
	}
	
	private boolean changeQos(){
		logger.error("Excute change Qos...");
		
		String sectionCondition = "" ;
		String sectionCondition2 = "" ;
		if(number_section!=null){
			
			sectionCondition += "AND ( 1=1 ";
			sectionCondition2 += "AND ( 1=1 ";
			String[] sections = number_section.trim().split(",");
			
			for(int i = 0;i<sections.length;i++){
				sectionCondition += "OR A.PREVPHONENUMBER like '"+sections[i]+"%' ";
				sectionCondition2 += "OR A.NEWPHONENUMBER like '"+sections[i]+"%' ";
			}
			sectionCondition += ") ";
			sectionCondition2 += ") ";
		}
		
		sql=
				"SELECT B.SERVICEID, SUBSTR(PREVPHONENUMBER,4,8) OLD_MSISDN, SUBSTR(NEWPHONENUMBER,4,8) NEW_MSISDN, IMSI ,D.PRICEPLANID,TO_CHAR(B.COMPLETEDATE,'YYYYMMDDHH24MISS') TIME "
				+ "FROM PHONENUMBERCHANGEORDER A, SERVICEORDER B, IMSI C,SERVICE D "
				+ "WHERE A.PREVPHONENUMBER<>A.NEWPHONENUMBER "
				+ "AND A.ORDERID=B.ORDERID AND B.SERVICEID=C.SERVICEID AND C.SERVICEID =D.SERVICEID "
				+ "AND TO_CHAR(B.COMPLETEDATE,'YYYYMMDDHH24MISS')>='"+preTimeS+"' "
				+ "AND TO_CHAR(B.COMPLETEDATE,'YYYYMMDDHH24MISS')<'"+nowTimeS+"' "
				//+ "AND (A.PREVPHONENUMBER like '8526640%' OR  A.PREVPHONENUMBER like '8525609%'  OR A.PREVPHONENUMBER like '8526947%'   OR A.PREVPHONENUMBER like '8525392%' ) "
				//+ "AND (A.NEWPHONENUMBER like '8526640%' OR  A.NEWPHONENUMBER like '8525609%'  OR A.NEWPHONENUMBER like '8526947%'   OR A.NEWPHONENUMBER like '8525392%' ) "
				+ sectionCondition
				+ sectionCondition2
				
				;

		
		
		try {
			Statement st = conn2.createStatement();
			Statement st2 = conn.createStatement();
			logger.info("Search change : "+sql);
			ResultSet rs = st.executeQuery(sql);
			ResultSet rs2 = null;
			
			while(rs.next()){
				String oMSISDN=rs.getString("OLD_MSISDN");
				String nMSISDN=rs.getString("NEW_MSISDN");
				IMSI=rs.getString("IMSI");
				String pricePlanId = rs.getString("PRICEPLANID");
				
				String sql2 = "select case A.ServiceCode when 'SX001' then '3' when 'SX002' then '4' else '2' end PLAN "
						+ "from Addonservice_N A "
						+ "where A.S2TMSISDN like '%"+oMSISDN+"' "
						+ "and A.STATUS = 'A' "
						+ "and A.ENDDATE is null";
				
				
				logger.info("query PLAN : "+sql2);
				rs2 = st2.executeQuery(sql2);
				
				PLAN = "2";
				
				while(rs2.next()){
					PLAN = rs2.getString("PLAN");
				}
				
				/*if("158".equals(pricePlanId)||"159".equals(pricePlanId)){
					PLAN="3";
				}else{
					PLAN="2";
				}*/
				
				if(oMSISDN!=null && !"".equals(oMSISDN) && nMSISDN!=null && !"".equals(nMSISDN) && IMSI!=null && !"".equals(IMSI)){
					
					//Delete old
					MSISDN=oMSISDN;
					ACTION="D";
					
					Map m = new HashMap();
					m.put("MSISDN", MSISDN);
					m.put("PLAN", PLAN);
					m.put("ACTION", ACTION);
					m.put("IMSI", IMSI);
					m.put("TIME", rs.getString("TIME"));
					postdatas.add(m);
					
					//excutePost();
					
					//Add new
					MSISDN=nMSISDN;
					ACTION="A";
					
					m = new HashMap();
					m.put("MSISDN", MSISDN);
					m.put("PLAN", PLAN);
					m.put("ACTION", ACTION);
					m.put("IMSI", IMSI);
					m.put("TIME", rs.getString("TIME"));
					postdatas.add(m);
					
					//excutePost();
				}else{
					logger.error(" Because of new MSISDN,old MSISDN  or IMSI is null  , Can't change Qos .");
				}
				
				
			}
			st.close();
			st2.close();
			rs.close();
			if(rs2!=null){
				rs2.close();
			}
			
		} catch (SQLException e) {
			StringWriter s = new StringWriter();
			e.printStackTrace(new PrintWriter(s));
			errorMsg=s.toString();
			logger.error("Got SQLException",e);
			//sendMail
			sendMail("At Cahnge occure Exception("+new Date()+") \n"+s);
			return false;
		}
		return true;
	}
	private boolean added(){
		logger.error("Excute added Qos...");
		
		String sectionCondition = "" ;
		if(number_section!=null){
			
			sectionCondition += "AND ( 1=1 ";
			
			String[] sections = number_section.trim().split(",");
			
			for(int i = 0;i<sections.length;i++){
				sectionCondition += "OR A.S2TMSISDN like '"+sections[i]+"%' ";
			}
			sectionCondition += ") ";
		}
		sql = 
				"SELECT SUBSTR(A.S2TMSISDN,4,8) MSISDN, A.S2TIMSI IMSI,A.ADDONCODE,A.ADDONACTION,TO_CHAR(A.REQUESTDATETIME,'YYYYMMDDHH24MISS') TIME "
				+ "FROM ADDONSERVICE A "
				+ "WHERE A.ADDONCODE IN ('SX001','SX002') "
				+ "AND TO_CHAR(A.REQUESTDATETIME,'YYYYMMDDHH24MISS')>='"+preTimeS+"' "
				+ "AND TO_CHAR(A.REQUESTDATETIME,'YYYYMMDDHH24MISS')<'"+nowTimeS+"' "
				//+ "AND (A.S2TMSISDN like '8526640%' OR  A.S2TMSISDN like '8525609%'  OR A.S2TMSISDN like '8526947%'   OR A.S2TMSISDN like '8525392%'  ) "
				+ sectionCondition
				+ "ORDER BY A.REQUESTDATETIME ASC";

		try {
			Statement st = conn.createStatement();
			logger.info("Search added: "+sql);
			ResultSet rs = st.executeQuery(sql);
			while(rs.next()){
				MSISDN=rs.getString("MSISDN");
				IMSI=rs.getString("IMSI");
				String ADDONCODE = rs.getString("ADDONCODE");
				String ADDONACTION = rs.getString("ADDONACTION");
				//String pricePlanId = rs.getString("PRICEPLANID");
				
				if(MSISDN!=null && !"".equals(MSISDN) && IMSI!=null && !"".equals(IMSI)){
					
					if("A".equals(ADDONACTION)){
						//Delete old
						PLAN="2";
						ACTION="D";
						//excutePost();
						Map m = new HashMap();
						m.put("MSISDN", MSISDN);
						m.put("PLAN", PLAN);
						m.put("ACTION", ACTION);
						m.put("IMSI", IMSI);
						m.put("TIME", rs.getString("TIME"));
						postdatas.add(m);
						
						//Add new
						if("SX001".equals(ADDONCODE))PLAN="3";
						if("SX002".equals(ADDONCODE))PLAN="4";					
						ACTION="A";
						m = new HashMap();
						m.put("MSISDN", MSISDN);
						m.put("PLAN", PLAN);
						m.put("ACTION", ACTION);
						m.put("IMSI", IMSI);
						m.put("TIME", rs.getString("TIME"));
						postdatas.add(m);
						//excutePost();	
					}else if("D".equals(ADDONACTION)){
						//Delete old
						if("SX001".equals(ADDONCODE))PLAN="3";
						if("SX002".equals(ADDONCODE))PLAN="4";	
						ACTION="D";
						Map m = new HashMap();
						m.put("MSISDN", MSISDN);
						m.put("PLAN", PLAN);
						m.put("ACTION", ACTION);
						m.put("IMSI", IMSI);
						m.put("TIME", rs.getString("TIME"));
						postdatas.add(m);
						//excutePost();
						
						//Add new
						PLAN="2";
						ACTION="A";
						m = new HashMap();
						m.put("MSISDN", MSISDN);
						m.put("PLAN", PLAN);
						m.put("ACTION", ACTION);
						m.put("IMSI", IMSI);
						m.put("TIME", rs.getString("TIME"));
						postdatas.add(m);
						//excutePost();
					}
				}else{
					logger.error(" Because of MSISDN  or IMSI is null  , Can't added  Qos .");
				}
				
			}
			st.close();
			rs.close();
		} catch (SQLException e) {
			StringWriter s = new StringWriter();
			e.printStackTrace(new PrintWriter(s));
			errorMsg=s.toString();
			logger.error("SQLException",e);
			//sendMail
			sendMail("At Add added occure Exception("+new Date()+") \n"+s);
			return false;
		}
		return true;
	}
	/*
	
	private boolean addedQosA(){
		logger.error("Excute added A Qos...");
		sql=
				"SELECT B.SERVICEID, SUBSTR(S2TMSISDN,4,8) MSISDN, S2TIMSI IMSI,B.PRICEPLANID,A.ADDONCODE "
				+ "FROM ADDONSERVICE A, SERVICE B, IMSI C "
				+ "WHERE A.ADDONCODE IN ('SX001','SX002') "
				+ "AND A.S2TMSISDN=B.SERVICECODE "
				+ "AND B.SERVICEID=C.SERVICEID AND A.S2TIMSI=C.IMSI "
				+ "AND A.ADDONACTION='A' "
				+ "AND TO_CHAR(A.REQUESTDATETIME,'YYYYMMDDHH24MISS')>='"+preTimeS+"' "
				+ "AND TO_CHAR(A.REQUESTDATETIME,'YYYYMMDDHH24MISS')<'"+nowTimeS+"' "
				+ "AND (A.S2TMSISDN like '8526640%' OR  A.S2TMSISDN like '8525609%'  OR A.S2TMSISDN like '8526947%'   OR A.SERVICECODE like '8525392%'  )  ";

		try {
			Statement st = conn.createStatement();
			logger.info("Search added A : "+sql);
			ResultSet rs = st.executeQuery(sql);
			while(rs.next()){
				MSISDN=rs.getString("MSISDN");
				IMSI=rs.getString("IMSI");
				String ADDONCODE=rs.getString("ADDONCODE");
				
				//String pricePlanId = rs.getString("PRICEPLANID");
				
				if(MSISDN!=null && !"".equals(MSISDN) && IMSI!=null && !"".equals(IMSI)){
					
					//Delete old
					PLAN="2";
					ACTION="D";
					
					excutePost();
					
					
					
					//Add new
					//PLAN="1";
					//ACTION="A";
					
					
					if("SX001".equals(ADDONCODE)){
						//20150409 mod
						PLAN="3";
						
					}else if("SX002".equals(ADDONCODE)){
						//20150702 add
						PLAN="4";
					}
					
					ACTION="A";

					excutePost();
				}else{
					logger.error(" Because of MSISDN  or IMSI is null  , Can't added  Qos .");
				}
				
			}
			st.close();
			rs.close();
		} catch (SQLException e) {
			StringWriter s = new StringWriter();
			e.printStackTrace(new PrintWriter(s));
			errorMsg=s.toString();
			logger.error("SQLException",e);
			//sendMail
			sendMail("At Add added occure Exception("+new Date()+") \n"+s);
			return false;
		}
		return true;
	}
	
	private boolean addedQosD(){
		logger.error("Excute added D Qos...");
		sql=
				"SELECT B.SERVICEID, SUBSTR(S2TMSISDN,4,8) MSISDN, S2TIMSI IMSI,B.PRICEPLANID,A.ADDONCODE "
				+ "FROM ADDONSERVICE A, SERVICE B, IMSI C "
				+ "WHERE A.ADDONCODE IN ('SX001','SX002') "
				+ "AND A.S2TMSISDN=B.SERVICECODE "
				+ "AND B.SERVICEID=C.SERVICEID AND A.S2TIMSI=C.IMSI "
				+ "AND A.ADDONACTION='D' "
				+ "AND TO_CHAR(A.REQUESTDATETIME,'YYYYMMDDHH24MISS')>='"+preTimeS+"' "
				+ "AND TO_CHAR(A.REQUESTDATETIME,'YYYYMMDDHH24MISS')<'"+nowTimeS+"' "
				+ "AND (A.S2TMSISDN like '8526640%' OR  A.S2TMSISDN like '8525609%'  OR A.S2TMSISDN like '8526947%'   OR A.SERVICECODE like '8525392%'  )  ";

		try {
			Statement st = conn.createStatement();
			logger.info("Search added D : "+sql);
			ResultSet rs = st.executeQuery(sql);
			while(rs.next()){
				MSISDN=rs.getString("MSISDN");
				IMSI=rs.getString("IMSI");
				String ADDONCODE=rs.getString("ADDONCODE");
				//String pricePlanId = rs.getString("PRICEPLANID");
				
				if(MSISDN!=null && !"".equals(MSISDN) && IMSI!=null && !"".equals(IMSI)){
					
					//Delete old
					//PLAN="1";
					//ACTION="D";

					if("SX001".equals(ADDONCODE)){
						//20150409 mod
						PLAN="3";
						
					}else if("SX002".equals(ADDONCODE)){
						//20150702 add
						PLAN="4";
					}
					ACTION="D";
					
					excutePost();
					
					//Add new
					PLAN="2";
					ACTION="A";
					excutePost();
				}else{
					logger.error(" Because of MSISDN  or IMSI is null  , Can't added  Qos .");
				}
				
			}
			st.close();
			rs.close();
		} catch (SQLException e) {
			StringWriter s = new StringWriter();
			e.printStackTrace(new PrintWriter(s));
			errorMsg=s.toString();
			logger.error("Got SQLException",e);
			//sendMail
			sendMail("At Delete added occure Exception("+new Date()+") \n"+s);
			return false;
		}
		return true;
	}*/
	
	private void proccess() throws ParseException{
		
		long startTime;
		long endTime;
		
		conn=null;
		
		connectDB();
		
		if(conn!=null){
			logger.info("connection success!");
			nowTime=new Date(new Date().getTime()-1*60*1000);
			logger.info("Start QosBatch..."+Thread.currentThread().getName()+"\t"+nowTime);
			startTime = nowTime.getTime();
			
			postdatas.clear();
			if(setTime()&&addAndDeleteQos()&&changeQos()&&added()){
				
				if(postdatas.size()!=0){
					logger.info("Sorting list with size "+postdatas.size());
					List result = mergerSortList(0,postdatas.size()-1);
					Iterator it = result.iterator();
					
					logger.info("Execute post... ");
					while(it.hasNext()){
						Map m = (Map) it.next();
						//System.out.println(m.get("TIME")+":"+m.get("MSISDN")+":"+m.get("ACTION")+":"+m.get("PLAN"));
						excutePost((String)m.get("MSISDN"),(String)m.get("IMSI"),(String)m.get("ACTION"),(String)m.get("PLAN"));
					}
				}
				
				lastTime=nowTime;
			}

			// run end
			endTime = System.currentTimeMillis();
			logger.info("Program execute time :" + (endTime - startTime));
		}else{
			logger.error("connction is null!");
			//sendMail
			sendMail("connction is null!");
		}
		
		closeConnect();
	}
	
	public static List  mergerSortList(int start,int end) throws ParseException{
		List result = new ArrayList();
		if(end ==start ){
			result.add(postdatas.get(start));
		}else{
			int mid = (end+start)/2;
			List left = mergerSortList(start,mid);
			List right = mergerSortList(mid+1,end);
			int l = 0,r = 0;
			while(true){
				if(l<left.size() && r<right.size()){
					Map lm = (Map) left.get(l);
					Map rm = (Map) right.get(r);
					if(sdf.parse((String) lm.get("TIME")).after(sdf.parse((String) rm.get("TIME")))){
						result.add(rm);
						r++;
					}else{
						result.add(lm);
						l++;
					}
				}
				else if(l<left.size()){
					result.add(left.get(l++));
				}else if(r<right.size()){
					result.add(right.get(r++));
				}else{
					break;
				}
			}
		}
		return result;
	}

	public static void main(String[] args){
		
		/*if(args.length>0 && args[0].matches("^\\d+$")){
			period_Time=Integer.parseInt(args[0]);
			System.out.println("Has insert parameter time "+period_Time);
		}*/
		
		loadProperties();
		
		if(args.length>0){
			initialTime = args[0];
		}else{
			//initialTime = sdf.format(new Date());
			initialTime = "20170221110000";
		}
		
		//regularTimeRun();		
		//20150420 測試新用法 timer 與 timerTask
		Timer timer =new Timer();
		timer.schedule(new QosBatch(),0, period_Time*60*1000);
	}

	public void run() {
		try {
			proccess();
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}
}
