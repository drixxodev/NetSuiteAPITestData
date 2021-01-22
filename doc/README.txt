=========================================================================
NetSuite Data Loader - Tool to Load Data into NetSuite Using SuiteTalk (Web Services)
=========================================================================
The main purposes of the NetSuite Data Loader tool are quick loading of data into NetSuite, SuiteTalk performance testing, and migration of data into new NetSuite accounts.

This tool takes CSV data that you provide and transforms it into a SuiteTalk request. When you enter the appropriate commands, the tool performs a web services call, bringing your data into NetSuite.

At a high level, using this tool involves the following steps:

A. Configuring the nsloader.properties file.
B. Building the NetSuite Data Loader application.
C. Preparing your CSV files.
D. Reviewing the 'Supported Operations' section of this document.
E. Using the loader to process your CSV files.

These steps are described in greater detail in the following sections.


Software Requirements
======================
The loader requires Java 6 and Gradle. It also requires several third-party libraries that are automatically downloaded from a public repository during the build process.


Configuration
=============
The file named nsloader.properties holds the configuration of the tool. It is located in the NetSuiteDataLoader\config directory, which was created when you extracted the NetSuite Data Loader archive. Edit this file to include your NetSuite account ID and your user details (your login email address and role ID). You will be prompted to enter your password each time you run the tool.

To enable processing in multiple threads, you can enter authentication details for multiple user IDs.

For help locating your NetSuite account ID, go to Setup > Integration > Web Services Preferences. Your account ID is listed at the top left corner of this page.

You can use the other properties in the file for performance tuning.


Building Application
====================
To build the NetSuite Data Loader application: Open a command prompt and navigate to the main NetSuite Data Loader directory. At the prompt, enter 'gradle deploy'. All needed libraries are automatically downloaded.


Formatting CSV Files
====================
For help understanding how to format your CSV files, please review the sample CSV files, which are stored in the NetSuiteDataLoader\doc\examples directory. Note that each line in the CSV input file represents a single record (or record reference) in the outgoing SuiteTalk request. The header (the first line in the file) functions as a series of column headings. Each heading represents a NetSuite field and determines how the data in the rest of the file should be interpreted. Headings are case sensitive.

To maximize performance, the loader uses SuiteTalk list operations, rather than single-record operations. 


Supported Operations
====================
The following operations are supported: Create (add), Update (update), Update or Insert (upsert), and Delete (delete).


General guidelines for the add, update, upsert, and delete operations
---------------------------------------------------------------------
For these operations, use application option '-m'. (For examples, see the 'Usage' section of this document.)

For a list of available fields and sublists for supported record types, refer to the SuiteTalk Schema Browser.

The following record properties can be set by using a specific heading format. Remember that all headings are case sensitive:

- body fields
-- Use the field name only. For example: 'companyName'

- sublist fields (on a specific sublist line)
-- Use the following format: 'sublistName.lineNumber.fieldName'. Note that the first lineNumber on a sublist is 1 (not 0). For example: 'item.1.quantity'

- custom fields, both on the body of the record and in sublists
-- To reference custom fields, use the following format: 'customFieldScriptId:(BOOLEAN|DATE|DOUBLE|LONG|SELECT|SELECT|MULTISELECT|STRING)'. For example:'custentity_datetimenormal:DATE', 'item.1.custcol_contact:SELECT'

- subrecord fields and files of types platformCommon:ShipAddress and platformCommon:BillAddress
-- Use the following format: 'bodyFieldContainingSubrecord;subrecordBodyField'. For example: 'shippingAddress;addressee'

- externalId (instead of internalId) for fields with values of types RecordRef, RecordRef[], ListOrRecordRef, and ListOrRecordRef[]
-- Use the '(E)' suffix. For example: 'entity(E)', 'custentity_listrecord:SELECT(E)', 'custentity_multiselect:MULTISELECT(E)', 'item.1.item(E)'


Guidelines for the delete, update, and upsert operations
--------------------------------------------------------
- When using the delete, update, or upsert operation, either 'internalId' or 'externalId' must be specified.

- For custom records, 'typeId' must also be specified.


Additional usage notes
----------------------
- custom records
-- Use 'CustomRecord' as the application command line parameter and 'recType' property to specify the custom record type.

- file records with content
-- To specify the content of the file, use the system path to the file ('content' header) - for example, 'C:\\NetSuiteDataLoader\\doc\\README.txt' (notice the escape characters)

If a request fails, the system tries again, until the request succeeds or the system reaches the maximum of 10 attempts.

-date format
-- To define a permitted date format, configure the extendedDateFormat property (in the nsloader.properties file). Refer to the java class SimpleDateFormat for supported formats. Regardless of your configuration, the standard date format of "MM/dd/yyyy" can always be used.


Usage
=====
After you have edited the nsloader.properties file and prepared your CSV files, you can use the loader tool. To start, open NSLoader.bat in a command prompt. Then use the following examples to take specific actions with your CSV files.

Test the connection to NetSuite
$ NSLoader.bat -t

Create customers
$ NSLoader.bat -m c Customer doc\examples\customers_create_customFields.csv

Update customers
$ NSLoader.bat -m u Customer doc\examples\customers_update_sublist.csv

Create inventory items in multiple threads
$ NSLoader.bat -m c -c 5 InventoryItem doc\examples\inventoryitems_create.csv

Upsert inventory items
$ NSLoader.bat -m r InventoryItem doc\examples\inventoryitems_upsert.csv

Delete custom records
$ NSLoader.bat -m d CustomRecord doc\examples\customRecords_delete.csv


Configuring a Different SuiteTalk Endpoint
==========================================
The loader is configured for use with the 2014.1 endpoint. Using an endpoint other than 2014.1 is discouraged.

It is possible to change to a different endpoint. However, because there could be significant differences between endpoints, making a change could require changing the NetSuite Data Loader core classes.

To change the endpoint:

1. Specify the desired endpoint version in the configuration file.
2. Run command 'gradle deploy' from the main NetSuite Data Loader directory.

 
Logging
=======
Logs are stored in the NetSuiteDataLoader\log directory.
