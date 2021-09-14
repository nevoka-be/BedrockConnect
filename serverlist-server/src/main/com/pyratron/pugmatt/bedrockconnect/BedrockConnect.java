package main.com.pyratron.pugmatt.bedrockconnect;

import main.com.pyratron.pugmatt.bedrockconnect.sql.Data;
import main.com.pyratron.pugmatt.bedrockconnect.sql.MySQL;
import main.com.pyratron.pugmatt.bedrockconnect.utils.PaletteManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class BedrockConnect {
    private static final Logger logger = LogManager.getLogger();

    public static PaletteManager paletteManager;

    public static MySQL MySQL;
    public static Connection connection;
    public static Data data;

    public static Server server;

    public static boolean noDB = false;
    public static boolean whitelist = false;
    public static String customServers = null;
    public static boolean kickInactive = true;
    public static boolean userServers = true;
    public static boolean featuredServers = true;
    public static File whitelistfile;

    public static void main(String[] args) {

        logger.info("-= BedrockConnect =-");
        paletteManager =  new PaletteManager();

        try {
            String hostname = "localhost";
            String database = "bedrock-connect";
            String username = "root";
            String password = "";
            String port = "19132";

            String serverLimit = "100";

            for(String str : args) {
                if(str.startsWith("mysql_host="))
                    hostname = getArgValue(str, "mysql_host");
                if(str.startsWith("mysql_db="))
                    database = getArgValue(str, "mysql_db");
                if(str.startsWith("mysql_user="))
                    username = getArgValue(str, "mysql_user");
                if(str.startsWith("mysql_pass="))
                    password = getArgValue(str, "mysql_pass");
                if(str.startsWith("server_limit="))
                    serverLimit = getArgValue(str, "server_limit");
                if(str.startsWith("port="))
                    port = getArgValue(str, "port");
                if(str.startsWith("nodb="))
                    noDB = getArgValue(str, "nodb").toLowerCase().equals("true");
                if(str.startsWith("custom_servers="))
                    customServers = getArgValue(str, "custom_servers");
                if(str.startsWith("generatedns=")) {
                    String ip;
                    try {
                        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

                        logger.info("Local IPv4 IPs:");
                        while (interfaces.hasMoreElements()) {
                            NetworkInterface iface = interfaces.nextElement();

                            if (iface.isLoopback() || !iface.isUp() || iface.isVirtual() || iface.isPointToPoint())
                                continue;

                            Enumeration<InetAddress> addresses = iface.getInetAddresses();
                            while(addresses.hasMoreElements()) {
                                InetAddress addr = addresses.nextElement();

                                if(!(addr instanceof Inet4Address)) continue;

                                ip = addr.getHostAddress();
                                logger.info(iface.getDisplayName() + ": " + ip);
                            }
                        }

                        Scanner reader = new Scanner(System.in);  // Reading from System.in
                        logger.info("Which IP should be used for the DNS records: ");
                        String selectedIP = reader.next().replaceAll("\\s+","");
                        reader.close();

                        BufferedWriter br = new BufferedWriter(new FileWriter(new File("bc_dns.conf")));
                        br.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                "<DNSMasqConfig xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                                "  <DNSMasqEntries>\n" +
                                "    <DNSMasqHost name=\"hivebedrock.network\" a=\"" + selectedIP + "\" />\n" +
                                "    <DNSMasqHost name=\"mco.mineplex.com\" a=\"" + selectedIP + "\" />\n" +
                                "    <DNSMasqHost name=\"play.mineplex.com\" a=\"" + selectedIP + "\" />\n" +
                                "    <DNSMasqHost name=\"play.inpvp.net\" a=\"" + selectedIP + "\" />\n" +
                                "    <DNSMasqHost name=\"mco.lbsg.net\" a=\"" + selectedIP + "\" />\n" +
                                "    <DNSMasqHost name=\"play.lbsg.net\" a=\"" + selectedIP + "\" />\n" +
                                "    <DNSMasqHost name=\"mco.cubecraft.net\" a=\"" + selectedIP + "\" />\n" +
                                "    <DNSMasqHost name=\"play.galaxite.net\" a=\"" + selectedIP + "\" />\n" +
                                "  </DNSMasqEntries>\n" +
                                "</DNSMasqConfig>");
                        br.close();
                    } catch (SocketException e) {
                        throw new RuntimeException(e);
                    }
                }
                if(str.startsWith("kick_inactive=")) {
                    kickInactive = getArgValue(str, "kick_inactive").toLowerCase().equals("true");
                }
                if(str.startsWith("user_servers=")) {
                    userServers = getArgValue(str, "user_servers").toLowerCase().equals("true");
                }
                if (str.startsWith("featured_servers=")) {
                    featuredServers = getArgValue(str, "featured_servers").toLowerCase().equals("true");
                }
                if (str.startsWith("whitelist=")) {
                	try {
                		whitelistfile = new File(getArgValue(str, "whitelist"));
                		Whitelist.loadWhitelist(whitelistfile);
                	}
                	catch(Exception e) {
                		System.out.println("Unable to load whitelist file: " + whitelistfile.getName());
                		e.printStackTrace();
                	}
                }
            }

            if(!noDB)
            System.out.println("MySQL Host: " + hostname + "\n" +
            "MySQL Database: " + database + "\n" +
            "MySQL User: " + username);

            logger.info("Server Limit: " + serverLimit + ", " + "Port: " + port);

            CustomServerHandler.initialize();
            logger.info(String.format("Loaded %d custom servers", CustomServerHandler.getServers().length));
            
            if (Whitelist.hasWhitelist())
                logger.info(String.format("There are %d whitelisted players\n", Whitelist.getWhitelist().size()));

            
            if(!noDB) {
                MySQL = new MySQL(hostname, database, username, password);

                connection = null;

                connection = MySQL.openConnection();

                data = new Data(serverLimit);

                // Keep MySQL connection alive
                Timer timer = new Timer();
                TimerTask task = new TimerTask() {
                    int sec;

                    public void run() {
                        try {
                            if (connection == null || connection.isClosed()) {
                                connection = MySQL.openConnection();
                            } else {
                                if (sec == 600) {
                                    try {
                                        ResultSet rs = connection
                                                .createStatement()
                                                .executeQuery(
                                                        "SELECT 1");
                                        rs.next();
                                    } catch (SQLException e) {
                                        // TODO Auto-generated
                                        // catch block
                                        e.printStackTrace();
                                    }
                                    sec = 0;
                                }
                            }
                        } catch (SQLException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                        sec++;
                    }
                };
                timer.scheduleAtFixedRate(task, 0L, 60 * 1000);
            } else {
                data = new Data(serverLimit);
                Timer timer = new Timer();
                TimerTask task = new TimerTask() {
                    public void run() { }
                };
                timer.scheduleAtFixedRate(task, 0L, 1200L);
            }

            server = new Server(port);
        } catch(Exception e) {
            e.printStackTrace();
        }

    }

    public static String getArgValue(String str, String name) {
        String target = name + "=";
        int index = str.indexOf(target);
        int subIndex = index + target.length();
        return str.substring(subIndex);
    }
}
