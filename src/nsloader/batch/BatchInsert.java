package nsloader.batch;

import nsloader.BatchJob;
import nsloader.Controller;
import nsloader.WsClient;
import nsloader.wsproxy.platform.core.Record;
import nsloader.wsproxy.platform.messages.WriteResponseList;

import java.rmi.RemoteException;
import java.util.Arrays;

/**
 * Description: Build and execute an addList call
 *
 * <p>Copyright © 2015, NetSuite, Inc.</p>
 */
public class BatchInsert extends BatchJob
{
    public BatchInsert(Controller controller, String[][] data, long globalCSVLineNumber) throws Exception
    {
		super(controller, globalCSVLineNumber, data);
    }

	@Override
	protected Object processRow(String[] row) throws Exception
	{
		return map(row);
	}

    @Override
    protected WriteResponseList doBatch(WsClient l) throws RemoteException
    {
		return l.addRecords(Arrays.copyOf(recordsInBatch.toArray(), recordsInBatch.size(), Record[].class));
    }
}
