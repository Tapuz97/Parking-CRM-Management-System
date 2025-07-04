package server_core;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.io.Serializable;
import java.util.*;

/**
 * Represents a data packet exchanged between client and server.
 * Includes a command string, optional arguments, an optional table of data,
 * a response status code, and a description message.
 *
 * <p>Typical usage:
 * - Client sends a CommandPacket with command and arguments.
 * - Server responds with a CommandPacket containing a status code and optional data.</p>
 *
 * <p>Status codes:</p>
 * <ul>
 *   <li>200 - ✅ OK</li>
 *   <li>201 - ✅ Created</li>
 *   <li>204 - ✅ No Content</li>
 *   <li>400 - ❌ Bad Request</li>
 *   <li>401 - ❌ Unauthorized</li>
 *   <li>403 - ❌ Forbidden</li>
 *   <li>404 - ❌ Not Found</li>
 *   <li>409 - ❌ Conflict</li>
 *   <li>500 - ❌ Internal Server Error</li>
 *   <li>503 - ❌ Service Unavailable</li>
 * </ul>
 */
public class CommandPacket implements Serializable {

	private static final long serialVersionUID = 1L;

	private String command;
	private Map<String, String> args;
	private List<Map<String, String>> table;
	private int answer;
	private String description;

	/**
	 * Default constructor for CommandPacket.
	 */
	public CommandPacket() {}

	// ===================== Getters =====================

	/**
	 * @return The command string (e.g., LOGIN, CREATE, REPORT).
	 */
	public String getCommand() {
		return command;
	}

	/**
	 * @return A map of key-value arguments related to the command.
	 */
	public Map<String, String> getArgs() {
		return args;
	}

	/**
	 * @return A list of rows, where each row is a map representing a table of data.
	 */
	public List<Map<String, String>> getTable() {
		return table;
	}

	/**
	 * @return The response status code (e.g., 200, 400, 500).
	 */
	public int getAnswer() {
		return answer;
	}

	/**
	 * @return A textual description of the result or response.
	 */
	public String getDescription() {
		return description;
	}

	// ===================== Setters =====================

	/**
	 * Sets the command string.
	 *
	 * @param command The command to set.
	 */
	public void setCommand(String command) {
		this.command = command;
	}

	/**
	 * Sets the arguments map.
	 *
	 * @param args The argument map to set.
	 */
	public void setArgs(Map<String, String> args) {
		this.args = args;
	}

	/**
	 * Sets the server's response description.
	 *
	 * @param desc A human-readable response message.
	 */
	public void setDescription(String desc) {
		this.description = desc;
	}

	/**
	 * Sets the response status code.
	 *
	 * @param answer The numeric result code (HTTP-style).
	 */
	public void setAnswer(int answer) {
		this.answer = answer;
	}

	/**
	 * Sets the data table for this packet.
	 *
	 * @param tb The table (list of map rows) to attach.
	 */
	public void setTable(List<Map<String, String>> tb) {
		this.table = tb;
	}

	/**
	 * @return A formatted debug string representing the contents of the packet.
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("\n📦 CommandPacket Debug:\n");
		sb.append("├─ Command    : ").append(this.getCommand()).append("\n");
		sb.append("├─ Answer     : ").append(this.getAnswer()).append("\n");
		sb.append("├─ Description: ").append(this.getDescription()).append("\n");

		Map<String, String> args = this.getArgs();
		if (args != null && !args.isEmpty()) {
			sb.append("├─ Args:\n");
			args.forEach((k, v) -> sb.append("│   ├─ ").append(k).append(" = ").append(v).append("\n"));
		} else {
			sb.append("├─ Args       : none\n");
		}

		List<Map<String, String>> table = this.getTable();
		if (table != null && !table.isEmpty()) {
			sb.append("├─ Table:\n");
			for (int i = 0; i < table.size(); i++) {
				sb.append("│   ├─ Row ").append(i + 1).append(": ").append(table.get(i)).append("\n");
			}
		} else {
			sb.append("├─ Table      : none\n");
		}

		sb.append("└────────────────────────────────────────────");
		return sb.toString();
	}
}
