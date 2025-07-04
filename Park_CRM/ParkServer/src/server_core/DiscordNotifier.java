package server_core;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Utility class responsible for sending structured notifications to Discord webhooks.
 * Used to inform about system events such as late pickups, cancellations, and user recoveries.
 */
public class DiscordNotifier {

    private String RecoveryHandlerURL;
    private String OrdersMonitorURL;
    private final Gson gson = new GsonBuilder().create();
    private boolean Monitoring = false; // Default to true, can be set via constructor

    /**
     * Constructs the notifier with specific webhook URLs.
     *
     * @param recoveryAPI       The Discord webhook for user recovery messages.
     * @param ordersMonitorAPI  The Discord webhook for late/cancellation messages.
     */
    
    public DiscordNotifier(String recoveryAPI, String ordersMonitorAPI) {
        if (recoveryAPI != null && !recoveryAPI.isEmpty()) {
            this.RecoveryHandlerURL = recoveryAPI;
        }
        else {
        	RecoveryHandlerURL=null;
        }
        if (ordersMonitorAPI != null && !ordersMonitorAPI.isEmpty()) {
            this.OrdersMonitorURL = ordersMonitorAPI;
        }
        else {
			OrdersMonitorURL=null;
		}
    }
    
    public DiscordNotifier() {}

    /**
     * Sends a message of the specified type to Discord.
     *
     * @param type            Message type ("LatePickup", "CancelOrder", "UserRecovery").
     * @param orderNumber     Related order number (or confirmation code for recovery).
     * @param subscriberId    Subscriber ID.
     * @param subscriberName  Subscriber full name.
     * @param subscriberEmail Subscriber email.
     * @param subscriberPhone Subscriber phone number.
     */
    public final void DiscordMsg(String type, String orderNumber, String subscriberId, String subscriberName, String subscriberEmail, String subscriberPhone) {
        switch (type) {
            case "LatePickup":
                sendLatePickupMessage(orderNumber, subscriberId, subscriberName, subscriberEmail, subscriberPhone);
                break;
            case "CancelOrder":
                sendCancelOrderMessage(orderNumber, subscriberId, subscriberName, subscriberEmail, subscriberPhone);
                break;
            case "UserRecovery":
                sendUserRecoveryMessage(orderNumber, subscriberId, subscriberName, subscriberEmail, subscriberPhone);
                break;
            default:
                System.out.println("Unknown message type: " + type);
        }
    }

    /**
     * Sends a "Late Pickup" notification to the orders Discord webhook.
     */
    private void sendLatePickupMessage(String orderNumber, String subscriberId, String subscriberName, String subscriberEmail, String subscriberPhone) {
        try {
            Map<String, Object> embed = buildEmbed(
                    "Late Pickup Notification",
                    "https://i.imgur.com/1VYH5ys.png", // clock icon
                    orderNumber, subscriberId, subscriberName, subscriberEmail, subscriberPhone
            );

            Map<String, Object> payload = new HashMap<>();
            payload.put("username", "Parking Orders Notifier");
            payload.put("avatar_url", "https://i.imgur.com/Qfct4A5.png"); // parking icon
            payload.put("content", null);
            payload.put("embeds", List.of(embed));

            postToDiscord(gson.toJson(payload), OrdersMonitorURL);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends an "Order Cancelled" notification to the orders Discord webhook.
     */
    private void sendCancelOrderMessage(String orderNumber, String subscriberId, String subscriberName, String subscriberEmail, String subscriberPhone) {
        try {
            Map<String, Object> embed = buildEmbed(
                    "Order Cancellation Notification",
                    "https://i.imgur.com/K8c7pD2.png", // cross icon
                    orderNumber, subscriberId, subscriberName, subscriberEmail, subscriberPhone
            );

            Map<String, Object> payload = new HashMap<>();
            payload.put("username", "Parking Orders Notifier");
            payload.put("avatar_url", "https://i.imgur.com/Qfct4A5.png");
            payload.put("content", null);
            payload.put("embeds", List.of(embed));

            postToDiscord(gson.toJson(payload), OrdersMonitorURL);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends a "User Recovery" notification to the recovery Discord webhook.
     */
    private void sendUserRecoveryMessage(String confirmationCode, String subscriberId, String subscriberName, String subscriberEmail, String subscriberPhone) {
        try {
            Map<String, Object> embed = buildEmbed(
                    "User Recovery Notification",
                    "https://i.imgur.com/vkJ7gob.png", // recovery icon
                    confirmationCode, subscriberId, subscriberName, subscriberEmail, subscriberPhone
            );

            Map<String, Object> payload = new HashMap<>();
            payload.put("username", "User Recovery Notifier");
            payload.put("avatar_url", "https://i.imgur.com/W7DmZzw.png"); // user icon
            payload.put("content", null);
            payload.put("embeds", List.of(embed));

            postToDiscord(gson.toJson(payload), RecoveryHandlerURL);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a single Discord embed object containing subscriber and order info.
     *
     * @param title        The embed title.
     * @param thumbnailUrl The thumbnail icon URL.
     * @param orderNumber  Order number or confirmation code.
     * @param id           Subscriber ID.
     * @param name         Subscriber name.
     * @param email        Subscriber email.
     * @param phone        Subscriber phone.
     * @return A Map representing the Discord embed.
     */
    private Map<String, Object> buildEmbed(String title, String thumbnailUrl, String orderNumber, String id, String name, String email, String phone) {
        Map<String, Object> embed = new HashMap<>();
        embed.put("title", title);
        embed.put("color", 16766765); // Yellow-orange shade
        embed.put("timestamp", Instant.now().toString());

        List<Map<String, String>> fields = new ArrayList<>();
        fields.add(createField("Order Number", orderNumber));
        fields.add(createField("Subscriber Name", name));
        fields.add(createField("Subscriber ID", id));
        fields.add(createField("Email", email));
        fields.add(createField("Phone number", phone));
        embed.put("fields", fields);

        embed.put("author", Map.of(
                "name", "B-PARK | Parking Management System",
                "url", "https://www.linkedin.com/in/galmitrani1/",
                "icon_url", "https://i.imgur.com/Htrco6D.png"
        ));

        embed.put("footer", Map.of(
                "text", "B-PARK | Parking Management System",
                "icon_url", "https://i.imgur.com/Htrco6D.png"
        ));

        embed.put("thumbnail", Map.of(
                "url", thumbnailUrl
        ));

        return embed;
    }

    /**
     * Creates a single field entry for a Discord embed.
     *
     * @param name  The field name.
     * @param value The field value.
     * @return A Map containing the field.
     */
    private Map<String, String> createField(String name, String value) {
        return Map.of("name", name, "value", value);
    }

    /**
     * Sends a POST request with JSON payload to a Discord webhook URL.
     *
     * @param jsonPayload The JSON-formatted message body.
     * @param webhookUrl  The Discord webhook URL to post to.
     * @throws Exception If any connection or IO errors occur.
     */
    private void postToDiscord(String jsonPayload, String webhookUrl) throws Exception {
    	if (Monitoring) {
    	
        URL url = new URL(webhookUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
        }

        connection.getInputStream().close();
    	}
    }
    
    
    public void enableMonitoring(boolean enable) {
		this.Monitoring = enable;
	}
    public boolean isMonitoringEnabled() {
    	return Monitoring;
    }

    
    /**
     * Verifies the validity of a Discord webhook URL by sending a test message.
     * This method constructs a payload with an embed containing verification details
     * and sends it to the specified webhook URL. The response code from the server
     * is returned to indicate the success or failure of the operation.
     *
     * @param webhookUrl The Discord webhook URL to verify.
     * @return The HTTP response code from the server. Returns -1 if an exception occurs.
     */
    public int verifyKey(String webhookUrl) {
        String keyIcon = "https://i.imgur.com/xf9Etn2.png"; // Key icon
        String checkmarkIcon = "https://i.imgur.com/Q3pHQaA.png"; // Checkmark icon
    	try {
            Map<String, Object> embed = new HashMap<>();
            embed.put("title", "API Key Validation");
            embed.put("color", 5785327);
            embed.put("timestamp", Instant.now().toString());
            
            List<Map<String, String>> fields = new ArrayList<>();
            
            fields.add(createField("Verified", ""));
            embed.put("fields", fields);
            
            embed.put("author", Map.of(
                "name", "Gal Mitrani | LinkedIn",
                "url", "https://www.linkedin.com/in/galmitrani1/",
                "icon_url", keyIcon
            ));

            embed.put("footer", Map.of(
                "text", "Tapuz97 | GitHub",
                "url", "https://www.github.com/Tapuz97/",
                "icon_url", keyIcon	
            ));

            embed.put("thumbnail", Map.of(
                "url", checkmarkIcon // 
            ));

            Map<String, Object> payload = new HashMap<>();
            payload.put("username", "API Key Verification");	
            payload.put("avatar_url", keyIcon);//user icon
            payload.put("content", null);
            payload.put("embeds", List.of(embed));
            postToDiscord(gson.toJson(payload), webhookUrl);
            URL url = new URL(webhookUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            String jsonPayload = gson.toJson(payload);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            }
            int responseCode = connection.getResponseCode();
            connection.getInputStream().close();
            return responseCode;
        } catch (Exception e) {
        	return -1;
        }
    }
}
