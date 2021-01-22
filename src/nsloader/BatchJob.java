package nsloader;

import nsloader.batch.BatchDelete;
import nsloader.batch.BatchInsert;
import nsloader.batch.BatchUpdate;
import nsloader.batch.BatchUpsert;
import nsloader.wsproxy.platform.core.BaseRef;
import nsloader.wsproxy.platform.core.Record;
import nsloader.wsproxy.platform.messages.WriteResponse;
import nsloader.wsproxy.platform.messages.WriteResponseList;
import org.apache.axis.AxisFault;
import org.apache.log4j.Logger;

import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Description: Constructs and executes a batch for AddList()
 *
 * <p>Copyright © 2015, NetSuite, Inc.</p>
 */
public abstract class BatchJob implements Runnable
{
    public static final int RETRYCOUNT = 10;

	static Logger log = Logger.getLogger(BatchJob.class.getName());

    public enum CrudMode
	{
		CREATE("c"), UPSERT("r"), UPDATE("u"), DELETE("d");
		String option;

		CrudMode(String option) {this.option=option;}

		public static CrudMode fromOption(String option)
		{
			for (CrudMode m : CrudMode.values())
			{
				if (m.option.equals(option))
					return m;
			}
			return CREATE;
		}
	}

	protected final ArrayList<Object> recordsInBatch;


    public static BatchJob newJob(Controller controller, String[][] data, long globalCSVLineNumber, CrudMode crudMode) throws Exception
    {
        switch (crudMode)
        {
            case DELETE : return new BatchDelete(controller, data, globalCSVLineNumber);
            case UPDATE : return new BatchUpdate(controller, data, globalCSVLineNumber);
            case CREATE: return new BatchInsert(controller, data, globalCSVLineNumber);
            case UPSERT: return new BatchUpsert(controller, data, globalCSVLineNumber);
            default : return null;
        }
    }

    protected final Controller controller;
    protected final long globalCSVLineNumber;
	protected final String[][] rawData;
	protected final Map<Integer, Integer> requestToRawDataPosition = new HashMap<Integer, Integer>();
	protected int positionInRequest = 0;

    protected BatchJob(Controller controller, long globalCSVLineNumber, String[][] rawData)
    {
		log.info(String.format("Preparing new batch with %d lines", rawData.length));
        this.controller = controller;
        this.globalCSVLineNumber = globalCSVLineNumber;
		this.rawData = rawData;

		// convert raw data into Records or RecordRefs
		this.recordsInBatch = new ArrayList<Object>(rawData.length);
		for (int i = 0;i<rawData.length;i++)
		{
			processRowAndAddToRecordsInBatch(rawData[i], i);
		}
		log.info(String.format("New batch with %d lines successfully prepared", recordsInBatch.size()));
    }

    protected abstract WriteResponseList doBatch(WsClient l) throws RemoteException;
	protected abstract Object processRow(String[] row) throws Exception;

    @Override
    public final void run()
    {
        try
        {
			if (recordsInBatch.size() == 0)
			{
				log.info("0 processed lines in batch - not sending WS request");
				return;
			}

            WsClient l = ((WsThreadFactory.WSThread)Thread.currentThread()).getLoader(); // Assumes a specific thread factory
            long start = System.nanoTime();
            WriteResponseList response=null;
            for (int i=0; response == null && i< RETRYCOUNT;i++)
            {
                try
                {
					log.info(String.format("Sending WS request"));
                    response = doBatch(l);
                }
                catch (RemoteException rmi)
                {
                    controller.logError(rmi, i);
                    log.error(String.format("Remote Exception: %s", rmi instanceof AxisFault ? ((AxisFault) rmi).getFaultString() : rmi.getMessage()));
					if (rmi.getMessage().matches("(?i)(?=.*invalid)(?=.*email)(?=.*password).*"))
					{
						break; // We cannot retry with invalid password because we can suspend the account
					}
					Thread.sleep(3000*(i+1));
                    l = WsClient.getInstance(l.instance); // copy instance, possible bad internal state
					log.info("Retrying");
                }
            }

			log.info(String.format("WS request completed"));
            controller.reportResults(new Results(response, System.nanoTime()-start, globalCSVLineNumber, rawData, requestToRawDataPosition));
        }
        catch (Exception e)
        {
			log.error(String.format("WS request error", e));
            controller.logError(e, -1);
        }
    }

	private void processRowAndAddToRecordsInBatch(String[] dataRow, int positionInBatch)
	{
		try
		{
			Object r = processRow(dataRow);
			recordsInBatch.add(r);
			requestToRawDataPosition.put(positionInRequest, positionInBatch);
			positionInRequest++;
		}
		catch (Exception e)
		{
			controller.logReject(new Reject(globalCSVLineNumber + positionInBatch, e.getMessage(), dataRow));
		}
	}

    protected Record map(String[] dataRow) throws Exception
    {
		return controller.mapper.map(dataRow);
    }

	protected BaseRef mapRef(String[] dataRow) throws Exception
	{
		return controller.refMapper.map(dataRow);
	}

    public static class Results
    {
        final long elapsedNanos, globalCSVLineNumber;
        final WriteResponse[] responses;
		final String[][] rawData;
		final Map<Integer, Integer> requestToRawDataPosition;

        Results(WriteResponseList responseList, long elapsedNanos, long globalCSVLineNumber, String[][] rawData, Map<Integer, Integer> requestToRawDataPosition)
        {
            this.elapsedNanos = elapsedNanos;
            this.responses = responseList.getWriteResponse();
            this.globalCSVLineNumber = globalCSVLineNumber;
			this.rawData = rawData;
			this.requestToRawDataPosition = requestToRawDataPosition;
        }

        public double getRate() { return TimeUnit.NANOSECONDS.convert(responses.length, TimeUnit.SECONDS)*1.0/elapsedNanos; }

        public Iterable<Reject> getRejects()
        {
            return new Iterable<Reject>()
            {
                @Override
                public Iterator<Reject> iterator()
                {
                    return new Iterator<Reject>()
                    {
                        int responsesPosition =0;
                        @Override
                        public boolean hasNext()
                        {
                            while (responsesPosition < responses.length)
                            {
                                if (!responses[responsesPosition].getStatus().isIsSuccess())
                                    return true;
                                responsesPosition++;
                            }
                            return false;
                        }

                        @Override
                        public Reject next()
                        {
							int batchPosition = requestToRawDataPosition.get(responsesPosition);
                            Reject toRet = new Reject(globalCSVLineNumber + batchPosition, responses[responsesPosition].getStatus().getStatusDetail()[0].getMessage(), rawData[batchPosition]);
                            responsesPosition+=1;
                            return toRet;
                        }

                        @Override
                        public void remove()
                        {
                            throw new UnsupportedOperationException();
                        }
                    };
                }
            };
        }

    }

    public static class Error
    {
        public final long lineNumber;
        public final String message;

        public Error(long lineNumber, String message)
        {
            this.lineNumber = lineNumber;
            this.message = message;
        }
    }

	public static class Reject extends Error
	{
		public final String[] rowData;

		public Reject(long lineNumber, String message, String[] rowData)
		{
			super(lineNumber, message);
			this.rowData = rowData;
		}
	}

}
