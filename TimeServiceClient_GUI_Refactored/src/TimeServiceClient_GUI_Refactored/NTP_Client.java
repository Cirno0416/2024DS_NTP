/*	File			NTP_Client.java
	Purpose			Network Time Protocol (NTP) Client - refactored
	Author			Richard Anthony	(ar26@gre.ac.uk)
	Date			February 2015
*/

package TimeServiceClient_GUI_Refactored;

import java.net.*;

@SuppressWarnings("SpellCheckingInspection")
public class NTP_Client {

    private static final int NTP_Port = 123;
    private static final int NTP_PACKET_SIZE = 48; // NTP time stamp is in the first 48 bytes of the message
    private static final long SeventyYears = 2208988800L; // Number of seconds in 70 years.
    // The raw timestamp is the number of seconds Since 1900
    // Unix time starts on Jan 1 1970 (70 years = 2208988800 seconds)
    private static long t1;// Originate Timestamp(T1)
    private static long t4;// Reference Timestamp(T4)
    DatagramSocket m_TimeService_Socket; // Used for UDP communication with NTP servers
    InetAddress m_TimeService_IPAddress; // Used for UDP communication with NTP servers
    Boolean m_bNTP_Client_Started; // The startup flag of the client

    public enum NTP_Client_ResultCode {NTP_Success, NTP_ServerAddressNotSet, NTP_SendFailed, NTP_ReceiveFailed}

    static final class NTP_Timestamp_Data {
        NTP_Client_ResultCode eResultCode;
        long lUnixTime;    // Seconds since 1970 (secsSince1900 - seventyYears)
        long lHour;
        long lMinute;
        long lSecond;
        long lMilliSecond;
        long lUninTimeMIlli;

        NTP_Timestamp_Data() {
            eResultCode = NTP_Client_ResultCode.NTP_ServerAddressNotSet; // Use as default for construction (gets overwritten)
            lHour = 0;
            lMinute = 0;
            lSecond = 0;
            lMilliSecond = 0;
            lUnixTime = 0;
            lUninTimeMIlli = 0;
        }
    }

    private Boolean m_bTimeServiceAddressSet;

    public NTP_Client() {
        m_bTimeServiceAddressSet = false;
        m_bNTP_Client_Started = false;
    }

    /**
     * Create a DatagramSocket for sending and receiving UDP packets.
     * Set the timeout to 500ms to prevent the client from hanging up due to waiting for the server's response.
     * If the creation fails, return false.
     */
    public Boolean CreateSocket() {
        try {
            m_TimeService_Socket = new DatagramSocket();
            m_TimeService_Socket.setSoTimeout(500); // Timeout = 500ms (i.e. non-blocking IO behaviour)
            // The timeout period was chosen to prevent the application freezing if the time service does not respond
            // but that it waits long enough for the reply RTT , so that it mostly avoids missing an actual reply
            // Tested with the following values: 100ms(can work but unreliable) 
            // 200ms(generally ok but highly dependent on network RTT) 400ms(generally reliable) 500ms(adds margin of safety)
        } catch (SocketException Ex) {
            return false;
        }
        return true;
    }

    /**
     * Set the address of the NTP server.
     * By providing a URL, parse it into InetAddress and save it to m_TimeService_SPAddress.
     * If parsing fails, return null.
     */
    public InetAddress SetUp_TimeService_AddressStruct(String sURL) {
        String sFullURL = "https://" + sURL;
        try {
            m_TimeService_IPAddress = InetAddress.getByName(new URI(sFullURL).getHost());
            m_bTimeServiceAddressSet = true;
        } catch (Exception Ex) {
            return null;
        }
        return m_TimeService_IPAddress;
    }

    public int GetPort() {
        return NTP_Port;
    }

    /**
     * Get the current NTP timestamp.
     * This method sequentially checks whether the NTP server address has been set and sends a request to the server.
     * If the transmission is successful, call the Receive() method and wait for the timestamp to be received.
     * If valid data is received, return the correct timestamp data.
     * If any step fails, an error code will be set and returned accordingly.
     */
    public NTP_Timestamp_Data Get_NTP_Timestamp() {
        NTP_Timestamp_Data NTP_Timestamp = new NTP_Timestamp_Data();
        if (m_bTimeServiceAddressSet) {
            if (Send_TimeService_Request()) {
                // Send operation succeeded
                NTP_Timestamp = Receive(NTP_Timestamp);
                if (0 != NTP_Timestamp.lUnixTime) {
                    // Signal that the NTP_Timestamp has been updated with valid content
                    NTP_Timestamp.eResultCode = NTP_Client_ResultCode.NTP_Success;
                    return NTP_Timestamp;
                }
                // Signal that the received operation failed (Time server did not reply)
                NTP_Timestamp.eResultCode = NTP_Client_ResultCode.NTP_ReceiveFailed;
                return NTP_Timestamp;
            }
            // Signal that the send operation failed (Time server was not contacted)
            NTP_Timestamp.eResultCode = NTP_Client_ResultCode.NTP_SendFailed;
            return NTP_Timestamp;
        }
        // Signal that Time server address has not been set, cannot get NTP timestamp
        NTP_Timestamp.eResultCode = NTP_Client_ResultCode.NTP_ServerAddressNotSet;
        return NTP_Timestamp;
    }

    /**
     * Create an NTP request packet and send it to the NTP server via UDP.
     * The first byte of the NTP request packet is set to 0xE3:
     * LI bits 7,6 = 3 (Clock not synchronised),
     * Version bits 5,4,3 = 4 (The current version of NTP)
     * Mode bits 2,1,0 = 3 (Sent by client)
     */
    Boolean Send_TimeService_Request() {
        byte[] bSendBuf = new byte[NTP_PACKET_SIZE]; // Zero-out entire 48-byte buffer to hold incoming packets (UTC time value)
        bSendBuf[0] = (byte) 0xE3;  // 0b11100011;
        // Originate Timestamp(T1)
        t1 = System.currentTimeMillis();
        try {
            DatagramPacket SendPacket = new DatagramPacket(bSendBuf, bSendBuf.length, m_TimeService_IPAddress, NTP_Port);
            m_TimeService_Socket.send(SendPacket);
        } catch (SocketTimeoutException Ex) {
            return false;
        } catch (Exception Ex) {
            System.out.printf("Send failed: %s\n", Ex.toString());
            return false;
        }
        return true;
    }

//    Boolean Send_TimeService_Request()
//    {
//        byte[] bSendBuf = new byte [NTP_PACKET_SIZE]; // Zero-out entire 48-byte buffer to hold incoming packets (UTC time value)
//        // Initialize values needed to form NTP request
//        bSendBuf[0] = (byte) 0xE3;// 0b11100011;
//        // LI bits 7,6		= 3 (Clock not synchronised),
//        // Version bits 5,4,3	= 4 (The current version of NTP)
//        // Mode bits 2,1,0	= 3 (Sent by client)
//
//        int attempts = 100; // Send request multiple times for better accuracy
//        long bestRTT = Long.MAX_VALUE;
//
//        for (int i = 0; i < attempts; i++) {
//            try {
//                DatagramPacket sendPacket = new DatagramPacket(bSendBuf, bSendBuf.length, m_TimeService_IPAddress, NTP_Port);
//                long startTime = System.currentTimeMillis();
//                m_TimeService_Socket.send(sendPacket);
//
//                NTP_Timestamp_Data response = Receive(new NTP_Timestamp_Data());
//                long RTT = System.currentTimeMillis() - startTime;
//                if (response.eResultCode == NTP_Client_ResultCode.NTP_Success && RTT < bestRTT) {
//                    bestRTT = RTT;
//                }
//            } catch (SocketTimeoutException Ex) {
//                return false;
//            } catch(Exception Ex) {
//                System.out.printf("Send failed: %s\n", Ex.toString());
//                return false;
//            }
//        }
//        return bestRTT < Long.MAX_VALUE;
//    }

    /**
     * Waiting to receive the response packet sent from the NTP server. Extract timestamp information from the response packet.
     * The NTP timestamp is located in the 40th to 43rd bytes of the packet (big endian).
     * Convert NTP timestamps from 'seconds since 1900' to Unix timestamps (seconds since 1970).
     * Then convert the Unix timestamp into hours, minutes, and seconds.
     */
    private NTP_Timestamp_Data Receive(NTP_Timestamp_Data NTP_Timestamp) {
        byte[] bRecvBuf = new byte[NTP_PACKET_SIZE]; // buffer to hold incoming packets (UTC time value)
        DatagramPacket RecvPacket = new DatagramPacket(bRecvBuf, NTP_PACKET_SIZE);
        try {
            m_TimeService_Socket.receive(RecvPacket);
            //Reference Timestamp(T4)
            t4 = System.currentTimeMillis();
        } catch (Exception ex) {
            NTP_Timestamp.lUnixTime = 0; // Signal that an error occurred
            return NTP_Timestamp;
        }

        if (0 < RecvPacket.getLength()) {
            //Receive Timestamp(T2)
            long t2_second = (((long) bRecvBuf[32] & 0xFF) << 24) + (((long) bRecvBuf[33] & 0xFF) << 16) + (((long) bRecvBuf[34] & 0xFF) << 8) + ((long) bRecvBuf[35] & 0xFF);
            long t2_milli = ((((long) bRecvBuf[36] & 0xFFL) << 24) + (((long) bRecvBuf[37] & 0xFFL) << 16) + (((long) bRecvBuf[38] & 0xFFL) << 8) + ((long) bRecvBuf[39] & 0xFFL)) * 1000L / 0x100000000L;
            long t2 = t2_second * 1000 + t2_milli;
            //Transmit Timestamp(T3)
            long t3_second = (((long) bRecvBuf[40] & 0xFF) << 24) + (((long) bRecvBuf[41] & 0xFF) << 16) + (((long) bRecvBuf[42] & 0xFF) << 8) + ((long) bRecvBuf[43] & 0xFF);
            long t3_milli = ((((long) bRecvBuf[44] & 0xFFL) << 24) + (((long) bRecvBuf[45] & 0xFFL) << 16) + (((long) bRecvBuf[46] & 0xFFL) << 8) + ((long) bRecvBuf[47] & 0xFFL)) * 1000L / 0x100000000L;
            long t3 = t3_second * 1000 + t3_milli;
            System.out.println("t1:" + t1);
            System.out.println("t2:" + t2);
            System.out.println("t3:" + t3);
            System.out.println("t4:" + t4);

            long offset = ((t2 - t1) + (t3 - t4)) / 2;//毫秒为单位
            long now = System.currentTimeMillis();
            NTP_Timestamp.lUninTimeMIlli = (now + offset) - SeventyYears * 1000L;//毫秒版时间戳
            NTP_Timestamp.lUnixTime = (now + offset) / 1000L - SeventyYears;
            NTP_Timestamp.lHour = (long) (NTP_Timestamp.lUnixTime % 86400L) / 3600;
            NTP_Timestamp.lMinute = (long) (NTP_Timestamp.lUnixTime % 3600) / 60;
            NTP_Timestamp.lSecond = (long) NTP_Timestamp.lUnixTime % 60;
            NTP_Timestamp.lMilliSecond = (now + offset) % 1000L;

        } else {
            NTP_Timestamp.lUnixTime = 0; // Signal that an error occurred
        }
        return NTP_Timestamp;
    }

    public Boolean Get_ClientStarted_Flag() {
        return m_bNTP_Client_Started;
    }

    public void Set_ClientStarted_Flag(Boolean bClient_Started) {
        m_bNTP_Client_Started = bClient_Started;
    }

    public void CloseSocket() {
        try {
            m_TimeService_Socket.close();
        } catch (NullPointerException Ex) {
            // Generic approach to dealing with situations such as socket not created
            System.out.println("Socket was not created.\n");
        } catch (Exception Ex) {
            System.out.println("Error occurred while closing socket: " + Ex.getMessage() + "\n");
        }
    }
}