package nsloader.batch;

import nsloader.Controller;
import nsloader.WsClient;
import nsloader.wsproxy.platform.core.Record;
import nsloader.wsproxy.platform.messages.WriteResponseList;

import java.rmi.RemoteException;
import java.util.Arrays;

/**
 * Description: Run Batch Updates
 *
 * <p>Copyright © 2015, NetSuite, Inc.</p>
 */
public class BatchUpdate extends BatchInsert
{
    public BatchUpdate(Controller controller, String[][] data, long globalCSVLineNumber) throws Exception
    {
        super(controller, data, globalCSVLineNumber);
    }

    @Override
    protected WriteResponseList doBatch(WsClient l) throws RemoteException
    {
		return l.updateRecords(Arrays.copyOf(recordsInBatch.toArray(), recordsInBatch.size(), Record[].class));
    }

}
