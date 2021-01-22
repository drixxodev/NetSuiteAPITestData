import nsloader.Controller;
import nsloader.WsClient;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.log4j.PropertyConfigurator;

/**
 * Description: Parse params and options and run the controller
 *
 * <p>Copyright © 2015, NetSuite, Inc.</p>
 */
public class Main
{
    public static void main(String[] args) throws Exception
    {
		Options o = new Options();

		o.addOption(new Option("h","help",false,"Show this Help and Exit"));
		o.addOption(new Option("t","test-connectivity",false,"Test Connectivity and Exit"));

		o.addOption("m", "record-action", true, "(c|r|u|d) Create, upseRt, Update, Delete. Default Create.");
		o.addOption("c", "thread-count", true, "Thread Count (Concurrent Clients). Default 1.");
		o.addOption("s", "start-at", true, "Start at row : Row in the file you would like to start from.\n" +
										   "2 represents the first row after the header.\n" +
										   "If 600 lines completed in the previous run. Enter 602\n" +
										   "Default is 2");

		CommandLineParser parser = new BasicParser();
		CommandLine cl = parser.parse(o, args);

		System.setProperty("org.apache.commons.logging.Log","org.apache.commons.logging.impl.NoOpLog");
		PropertyConfigurator.configure("config/log4j.properties");

		if (cl.hasOption("t"))
		{
			testConnectivity();
			return;
		}
		if (cl.getArgs() == null || cl.getArgs().length !=2 || cl.hasOption("h"))
		{
			HelpFormatter f = new HelpFormatter();
			f.setWidth(120);
			f.printHelp("NetSuite Data Loader (-t | [-c <threads>] [-s <start from>] -m <action (c|r|u|d)> RECORDTYPE FILE)" +
						"\nRECORDTYPE is the WSDL name of the Record Type - eg SalesOrder" +
						"\nFILE is the path to the CSV file" +
						"\nPlease see the README file for more information\n", o);
			return;
		}

        Controller app = new Controller(cl.getArgs()[0], Integer.parseInt(cl.getOptionValue("c","1")),
										cl.getOptionValue("m","c"), cl.getArgs()[1], Integer.parseInt(cl.getOptionValue("s","2")));

        app.execute();
    }

	private static void testConnectivity()
    {
        try
        {
			WsClient client = WsClient.getInstance(0);
            client.getServerTime();
        }
        catch (Exception e)
        {
            System.err.println(e.getMessage());
            e.printStackTrace(System.err);
        }
    }

}
