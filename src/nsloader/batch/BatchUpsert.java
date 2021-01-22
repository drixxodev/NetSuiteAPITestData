package nsloader.batch;

import nsloader.Controller;
import nsloader.WsClient;
import nsloader.wsproxy.platform.core.Record;
import nsloader.wsproxy.platform.messages.WriteResponseList;

import java.rmi.RemoteException;
import java.util.Arrays;

/**
 * Description: Run batch upsert
 *
 * <p>Copyright © 2015, NetSuite, Inc.</p>
 */
public class BatchUpsert extends BatchInsert
{
	public BatchUpsert(Controller controller, String[][] data, long globalCSVLineNumber) throws Exception
	{
		super(controller, data, globalCSVLineNumber);
	}

	@Override
	protected WriteResponseList doBatch(WsClient l) throws RemoteException
	{
		return l.upsertRecords(Arrays.copyOf(recordsInBatch.toArray(), recordsInBatch.size(), Record[].class));
	}
}
