package ambient_invisible_intelligence.logic;

import java.util.List;

import ambient_invisible_intelligence.boundaries.CommandBoundary;

public interface CommandsService {
	// returns one or more JSON-friendly objects describing the command's result
	List<Object> invokeCommand(CommandBoundary command);
	// Unbounded multi-fetch: replaced by pagination. Throws GoneException (410).
	@Deprecated
	List<CommandBoundary> getAllCommandsHistory();
	List<CommandBoundary> getAllCommandsHistory(int size, int page);
	void deleteAllCommands();
}
