package nsloader;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import nsloader.datatransfer.RecordMapper;
import nsloader.datatransfer.RefMapper;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.Console;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Description: Orchestration code for multi-threaded load
 *
 * <p>Copyright © 2015, NetSuite, Inc.</p>
 */
public class Controller
{
    public static final int ROW_REPORTING_INTERVAL = 100;
	private static final SimpleDateFormat FILE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HHmmssSSS");
	public static final int IN_PROCESS_JOBS_TIMEOUT_MIN = 60;

	static Logger log = Logger.getLogger(Controller.class.getName());

	final String recordType;
    final int threadCount, batchSize;
	final long startTime, rampTime;
    final RecordMapper mapper;
    final RefMapper refMapper;
    final BatchJob.CrudMode crudMode;

    final CSVReader reader;
    final CSVWriter errorWriter, rejectWriter;
    final Properties _properties;
    final AtomicLong recordsComplete = new AtomicLong(0);
	final AtomicBoolean keepRunning = new AtomicBoolean(true);
	boolean appFinished = false;
	final Object shutdownLock = new Object();
	final ShutdownThread shutdownHandler = new ShutdownThread(shutdownLock); // if the user hits ^C, new jobs won't start, started jobs finish correctly, files are closed
	long currentFileLine;

	private static Map<String, String> passwords = new HashMap<String, String>();

    public Controller (String recordType, int threadCount, String crudOption, String fileName, int startFrom) throws Exception
    {
        this.recordType = recordType;
        this.threadCount = threadCount;

		crudMode = BatchJob.CrudMode.fromOption(crudOption);

        this._properties = new Properties();
        this._properties.load(new FileInputStream("config/nsloader.properties"));
        this.batchSize = Integer.parseInt(_properties.getProperty("client.batchSize"));
        if ("true".equals(_properties.getProperty("useReplaceAll")))
            System.setProperty("nsloader.useReplaceAll", "T"); // pass the param to SublistFieldMapper
        this.rampTime = _properties.getProperty("rampTime") != null ? Integer.parseInt(_properties.getProperty("nsloader.rampTime")) : 1000; // millis between thread starts
		if ("true".equals(_properties.getProperty("log.console.verbose")))
			LogManager.getLogger("nsloader").setLevel(Level.INFO);
		log.setLevel(Level.INFO);
		if (_properties.getProperty("extendedDateFormat") != null)
			System.setProperty("nsloader.extendedDateFormat", _properties.getProperty("extendedDateFormat"));

		readPasswordsFromUser();

        this.reader = new CSVReader(new FileReader(fileName));

        String[] headers = reader.readNext();
        this.mapper = crudMode == BatchJob.CrudMode.DELETE ? null : new RecordMapper(recordType, headers);
        this.refMapper = crudMode == BatchJob.CrudMode.DELETE ? new RefMapper(recordType, headers) : null;

        // StartFrom is defined such that 1 is the first data row- but this corresponds to currentFileLine of 2
        for (currentFileLine=2;currentFileLine<startFrom;currentFileLine++)
            reader.readNext();

        Date logTime = Calendar.getInstance().getTime();

        String logDir = _properties.getProperty("log.dir");
        String errorFileName = _properties.getProperty("log.prefix.error") + FILE_DATE_FORMAT.format(logTime) + ".csv";
        errorWriter = new CSVWriter(new FileWriter(logDir + "/" + errorFileName));

        String rejectFileName =  _properties.getProperty("log.prefix.reject") + FILE_DATE_FORMAT.format(logTime) + ".csv";
        rejectWriter = new CSVWriter(new FileWriter(logDir + "/" + rejectFileName));

		Runtime.getRuntime().addShutdownHook(shutdownHandler);
		startTime = System.nanoTime();
    }

    /**
     * Main execution loop.
     * Spawns a configurable number of threads into a fixed size thread pool.
     * Each pool is assigned a ws client that lives between batches (thus the overridden thread factory)
     * When a thread completes it asks for more work using nextBatch()
     * When the file has been consumed, the wating master thread is notified and the pool is shutdown.
     * @throws Exception
     */
    public void execute() throws Exception
    {
        ExecutorService importExecutor = getImportExecutor();

        log.info("Beginning Job Submission");
        submitInitialJobs(importExecutor);

		log.info("Initial Jobs Submitted. Processing File");
        waitForFileProcessing();

		log.info("Awaiting in Process Job Completion");
        importExecutor.shutdown();
        importExecutor.awaitTermination(IN_PROCESS_JOBS_TIMEOUT_MIN, TimeUnit.MINUTES);

		log.info("ThreadPool Terminated");
        long elapsed = TimeUnit.SECONDS.convert(System.nanoTime()-startTime, TimeUnit.NANOSECONDS);
		log.info(String.format("Overall timings: %d records in %d seconds = %.1f records/second", currentFileLine - 2, elapsed, 1.0 * currentFileLine / elapsed));

        rejectWriter.close();
        errorWriter.close();
		synchronized (shutdownLock)
		{
			appFinished = true;
			shutdownHandler.notifyComplete();
		}
    }

	public String getRecordType() { return recordType;}

	@SuppressWarnings("EmptyCatchBlock")
    private void waitForFileProcessing()
    {
        synchronized (this)
        {
            try
            {
                this.wait();
            }
            catch (Exception ie) {}
		}
    }

    private void submitInitialJobs(ExecutorService importExecutor) throws InterruptedException
    {
        for (int i =0;i<threadCount;i++)
        {
            Thread.sleep(rampTime);
            BatchJob toProcess = nextBatch();
            if (toProcess != null)
                importExecutor.submit(toProcess);
            else
                break; // EOF
        }
    }

    private ExecutorService getImportExecutor()
    {
        BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<Runnable>(threadCount);

        final Controller master = this;
        return new ThreadPoolExecutor(threadCount, threadCount, 10L, TimeUnit.MINUTES, workQueue, WsThreadFactory.getInstance())
        {
            @Override
            protected void afterExecute(Runnable runnable, Throwable throwable)
            {
                super.afterExecute(runnable, throwable);
                BatchJob toProcess = nextBatch();
                if (toProcess != null)
                    submit(toProcess);
                else
                {
                    synchronized (master)
                    {
						keepRunning.set(false);
						master.notifyAll();
                    }
                }
            }
        };
    }

    /**
     * Reads the next N rows from the file. Synchronized across threads.
     */
    private synchronized BatchJob nextBatch()
    {
        try
        {
			if (!keepRunning.get())
				return null;

            ArrayList<String[]> data = new ArrayList<String[]>(batchSize);
            String[] nextLine;
			int i=0;
            for (;i<batchSize && ((nextLine = reader.readNext()) != null) ;i++)
                data.add(nextLine);

            if (data.size() == 0)
                return null;
            currentFileLine+=i;
            return BatchJob.newJob(this, data.toArray(new String[data.size()][]), currentFileLine - i /* start of batch*/, crudMode);
        }
        catch (Exception e)
        {
            log.error(e);
            return null;
        }
    }

    public void reportResults(BatchJob.Results results)
    {
        long nowComplete = recordsComplete.addAndGet(batchSize);
        if (nowComplete / ROW_REPORTING_INTERVAL > (nowComplete-batchSize) / ROW_REPORTING_INTERVAL)
            log.info(String.format("%d lines complete. Last batch %.2f records/second. Current Avg Combined Throughput %.2f records/second"
					, nowComplete, results.getRate(), 1.0*TimeUnit.NANOSECONDS.convert(1, TimeUnit.SECONDS)*nowComplete/(System.nanoTime()-startTime)));

        for (BatchJob.Reject error : results.getRejects())
        {
            logReject(error);
        }
    }

    public void logReject(BatchJob.Reject error)
    {
        synchronized (rejectWriter)
        {
			String[] toWrite  = new String[2+error.rowData.length];
			toWrite[0] = Long.toString(error.lineNumber);
			toWrite[1] =  error.message;
			System.arraycopy(error.rowData, 0, toWrite, 2, error.rowData.length);
            rejectWriter.writeNext(toWrite);
        }
    }

    void logError(Exception e, int whichTry)
    {
        synchronized (errorWriter)
        {
            StringWriter w = new StringWriter();
            e.printStackTrace(new PrintWriter(w));
            errorWriter.writeNext(new String[]{new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(Calendar.getInstance().getTime()), Integer.toString(whichTry),  e.getMessage(), w.toString()});
        }
    }

	private void readPasswordsFromUser()
	{
		for (String property : _properties.stringPropertyNames())
		{
			if (property.startsWith("login.email"))
			{
				String email = _properties.getProperty(property);
				passwords.put(email, readPasswordForEmail(email));
			}
		}
	}

	private static String readPasswordForEmail(String email)
	{
		Console console = System.console();
		if (console == null)
		{
			throw new IllegalStateException("The console cannot be initialized. Make sure you are not running NetSuite Data Loader from Cygwin or an IDE. " +
											"Use the native console for your operating system.");
		}
		return new String(console.readPassword("Enter password for email " + email + ": "));
	}

	static String getPasswordForEmail(String email)
	{
		String password = passwords.get(email);
		if (password == null)
		{
			password = readPasswordForEmail(email);
			passwords.put(email, password);
		}
		return password;
	}

	private class ShutdownThread extends Thread
	{
		final Object done;
		public ShutdownThread(Object shutdownLock) {done = shutdownLock;}

		@SuppressWarnings("EmptyCatchBlock")
		public void run()
		{
			log.info("Exit signal received.");

			try
			{
				keepRunning.set(false);
				synchronized (done)
				{
					if(!appFinished)
						done.wait((IN_PROCESS_JOBS_TIMEOUT_MIN + 1) * 60 * 1000);
				}
			}
			catch (InterruptedException ie) {}
		}

		public void notifyComplete()
		{
			synchronized (done)
			{
				done.notifyAll();
			}
		}
	}
}
