/**
 * Wake-On-LAN service for WebOS.
 * Written by Farhan Ahmad (http://www.thebitguru.com) based on WOL code from
 * http://www.jibble.org/wake-on-lan/
 *
 * @author Farhan Ahmad
 * @version 0.5.0
 */
package com.thebitguru.wolservice;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.net.SocketException;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import com.palm.luna.LSException;
import com.palm.luna.service.LunaServiceThread;
import com.palm.luna.service.ServiceMessage;
import com.palm.luna.message.ErrorMessage;

import org.json.JSONException;
import org.json.JSONObject;

public class WOLService extends LunaServiceThread {
  private String version = "0.5.0";

	/**
	 * Returns the version and the instance that this response was generated at. During development
	 * the instance can be used to verify that the service was updated.
	 */
  @LunaServiceThread.PublicMethod
	public void version(ServiceMessage message) throws JSONException, LSException {
		JSONObject reply = new JSONObject();
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		reply.put("success", true);
		reply.put("version", version);
		reply.put("instance", dateFormat.format(new Date()));
		message.respond(reply.toString());
	}

	/**
	 * Sends the WOL packet to the given MAC address on the specified interface.
	 * 
	 * Service parameters are:
	 *  - macAddress:
	 *  - broadcastIP:
	 * 
	 * Returns:
	 *  - success
	 *  - error: If there was an error then this will contain a description of the error.
	 */
  @LunaServiceThread.PublicMethod
	public void sendWOL(ServiceMessage message) throws JSONException, LSException {
		JSONObject reply = new JSONObject();
		boolean success = false;
		JSONObject msg = message.getJSONPayload();
		if (!msg.has("broadcastIP")) {
			reply.put("error", "broadcastIP is required.");
		} else if (!msg.has("macAddress")) {
			reply.put("error", "macAddress is required.");
		} else {
			try {
				WakeOnLan.sendWOL(msg.getString("broadcastIP"), msg.getString("macAddress"));
				success = true;
			} catch (Exception e) {
				reply.put("error", e.getMessage());
			}
		}
		reply.put("success", success);
		message.respond(reply.toString());
	}

	/**
	 * Validates the specified MAC address is in the correct format.
	 * 
	 * Service parameters are:
	 *  - macAddress:
	 *
	 * Returns:
	 *  - valid: Boolean indicating whether the MAC address was in the correct format.
	 *  - error: Only if invalid format then this further explains the issue.
	 */
  @LunaServiceThread.PublicMethod
	public void validateMACFormat(ServiceMessage message) throws JSONException, LSException {
		JSONObject reply = new JSONObject();
		boolean valid = false;
		JSONObject json = message.getJSONPayload();
		if (!json.has("macAddress")) {
			reply.put("error", "macAddress is required.");
		} else {
			try {
				byte[] discard = WakeOnLan.getMacBytes(json.getString("macAddress"));
				valid = true;
			} catch (IllegalArgumentException e) {
				reply.put("error", e.getMessage());
			}
		}
		reply.put("valid", valid);
		message.respond(reply.toString());
	}

	/**
	 * Returns the Broadcast IP address for the given IP address.
	 * 
	 * Service parameters are:
	 *  - ipAddress: IP address of the interface.
	 * 
	 * Returns:
	 *  - success
	 *  - broadcastIP: If a valid broadcast IP was found then this will have complete address.
	 *  - error: If an error occurred then this will contain the error description.
	 * 
	 * NOTE: WakeOnLan.getBroadcastIP isn't working on java 1.5 yet!
	 */
	//@LunaServiceThread.PublicMethod
	//public void getBroadcastIP(ServiceMessage message) throws JSONException,
	//		LSException {
	//	JSONObject reply = new JSONObject();
	//	boolean success = false;
	//	JSONObject msg = message.getJSONPayload();
	//	if (!msg.has("ipAddress")) {
	//		reply.put("error", "ipAddress is required.");
	//	} else {
	//		try {
	//			InetAddress broadcast = WakeOnLan.getBroadcastAddress(msg.getString("ipAddress"));
	//			if (broadcast == null) {
	//				reply.put("error", "Broadcast IP was not found.");
	//			} else {
	//				reply.put("broadcastIP", broadcast.toString());
	//				success = true;
	//			}
	//		} catch (Exception e) {
	//			reply.put("error", e.getMessage());
	//		}
	//	}
	//	reply.put("success", success);
	//	message.respond(reply.toString());
	//}
}

/**
 * This class sends the actual WOL packet.
 */
class WakeOnLan {
	public static final int PORT = 9;    

	/**
	 * @param broadcastIP IP address of the network interface to broadcast the magic packet on.
	 * @param macAddress MAC address of the computer that will be getting the magic packet.
	 */
	public static void sendWOL(String broadcastIP, String macAddress)
		throws UnknownHostException, SocketException, IOException {
		byte[] macBytes = getMacBytes(macAddress);
		
		// Magic packet = (6 * 0xFF) + (16 * MAC)
		byte[] bytes = new byte[6 + 16 * macBytes.length];
		for (int i = 0; i < 6; i++) {
			bytes[i] = (byte) 0xff;
		}
		for (int i = 6; i < bytes.length; i += macBytes.length) {
			System.arraycopy(macBytes, 0, bytes, i, macBytes.length);
		}

		InetAddress address = InetAddress.getByName(broadcastIP);
		DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address, PORT);

		DatagramSocket socket = new DatagramSocket();
		socket.send(packet);
		socket.close();
	}

	// Need ot get this to work on java 1.5
	///**
	// * Returns the InetAddress that represents the broadcast address for the given IP address.
	// * 
	// * @param ipAddress IP Address of the interface for which you need the broadcast address.
	// * 
	// * @return InetAddress if one is found, null otherwise.
	// */
	//public static InetAddress getBroadcastAddress(String ipAddress)
	//throws UnknownHostException, SocketException
	//{
	//	InetAddress address = InetAddress.getByName(ipAddress);
	//	NetworkInterface iface = NetworkInterface.getByInetAddress(address);
	//	InetAddress broadcast = null;
	//	List<InterfaceAddress> addresses = iface.getInterfaceAddresses();
	//	for (int index = 0; index < addresses.size(); index++) {
	//		broadcast = addresses.get(index).getBroadcast();
	//		if (broadcast != null) {
	//			return broadcast;
	//		}
	//	}
	//	return null;
	//}

	/**
	 * Converts the specified MAC address string into a HEX byte array.
	 */
	public static byte[] getMacBytes(String macAddress) throws IllegalArgumentException {
		byte[] bytes = new byte[6];
		String[] hex = macAddress.split("(\\:|\\-)");
		if (hex.length != 6) {
			throw new IllegalArgumentException("Invalid MAC address.");
		}
		try {
			for (int i = 0; i < 6; i++) {
				if (hex[i].length() > 2) {
					throw new IllegalArgumentException("Invalid octet at position " + i);
				}
				bytes[i] = (byte) Integer.parseInt(hex[i], 16);
			}
		}
		catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid hex digit in MAC address.");
		}
		return bytes;
	}
}
