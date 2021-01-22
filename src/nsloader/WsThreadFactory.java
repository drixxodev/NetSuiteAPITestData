package nsloader;

import org.apache.log4j.Logger;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This is one way to give the BatchJob access to a WS client, the others are a threadlocal, or
 * just to instantiate a new client for every batch. The latter seemed wasteful but the thread local vs thread
 * attribute doesn't matter much.
 *
 * <p>Copyright © 2015, NetSuite, Inc.</p>
 */
class WsThreadFactory implements ThreadFactory
{
	static Logger log = Logger.getLogger(WsThreadFactory.class.getName());

	static WsThreadFactory instance = new WsThreadFactory();
	static AtomicInteger counter = new AtomicInteger(0);
	public static ThreadFactory getInstance() { return instance; }
	public Thread newThread(Runnable runnable)
	{

		try
		{
			int instanceNumber = counter.getAndIncrement();
			return new WSThread(runnable, WsClient.getInstance(instanceNumber), instanceNumber);
		}
		catch (Exception e)
		{
			log.error(e);
			System.exit(1);
			return null; // probably won't get here
		}
	}

	public static class WSThread extends Thread
	{
		WsClient loader;
		public WSThread(Runnable r, WsClient loader, long threadNum)
		{
			super(r);
			this.setName(String.format("WS-THREAD-%03d",threadNum));
			this.loader = loader;
		}
		public WsClient getLoader() { return loader; }
	}
}
