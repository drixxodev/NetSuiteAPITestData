package nsloader.batch;

import nsloader.BatchJob;
import nsloader.Controller;
import nsloader.WsClient;
import nsloader.wsproxy.platform.core.BaseRef;
import nsloader.wsproxy.platform.messages.WriteResponseList;

import java.rmi.RemoteException;
import java.util.Arrays;

/**
 * Description: Delete Records
 *
 * <p>Copyright © 2015, NetSuite, Inc.</p>
 */
public class BatchDelete extends BatchJob
{
    public BatchDelete(Controller controller, String[][] data, long globalCSVLineNumber) throws Exception
    {
        super(controller, globalCSVLineNumber, data);
    }

	@Override
	protected Object processRow(String[] row) throws Exception
	{
		return mapRef(row);
	}


    @Override
    protected WriteResponseList doBatch(WsClient l) throws RemoteException
    {
		return l.deleteRecords(Arrays.copyOf(recordsInBatch.toArray(), recordsInBatch.size(), BaseRef[].class));
	}
}
