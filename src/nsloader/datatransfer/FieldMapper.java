package nsloader.datatransfer;

import nsloader.wsproxy.platform.core.ListOrRecordRef;
import nsloader.wsproxy.platform.core.Record;
import nsloader.wsproxy.platform.core.RecordRef;
import nsloader.wsproxy.platform.core.RecordRefList;
import org.apache.commons.io.FileUtils;

import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.regex.Pattern;

/**
* Description: TODO: Enter a paragraph that summarizes what the class does and why someone might want to utilize it
*
* <p>Copyright © 2015, NetSuite, Inc.</p>
*/
public abstract class FieldMapper
{
	public static final String EXTERNALID_SIGNAL = "(E)";
	public static final Pattern SHORT_DATE_PATTERN = Pattern.compile("\\d+/\\d+/-?[1-9]\\d*");
	public static final String SHORT_DATE_FORMAT = "MM/dd/yyyy";
	public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";
	private String dateFormat;

	final private boolean useExternalId;

	public abstract void setFieldData(Record target, String data) throws ConversionException, InvocationTargetException, IllegalAccessException, InstantiationException;
	protected abstract Class getFieldType();

	FieldMapper(boolean useExternalId, String dateFormat)
	{
		this.useExternalId = useExternalId;
		this.dateFormat = dateFormat;
	}

	static FieldMapper newMapping(Class recordType, String header)
    {
        try
        {
            String processedHeader = header.replace(EXTERNALID_SIGNAL,"");
            boolean useExternalId = processedHeader.length() != header.length();
            String dateFormat = System.getProperty("nsloader.extendedDateFormat");

            if (processedHeader.contains("."))
                return new SublistFieldMapper(recordType, processedHeader.split("\\."), useExternalId, dateFormat);
			else if (processedHeader.contains(";"))
				return new FieldSetFieldMapper(recordType, processedHeader.split(";"), useExternalId, dateFormat);
			else
                return new BodyFieldMapper(recordType, processedHeader, useExternalId, dateFormat);
        }
        catch (Exception e)
        {
            throw new RuntimeException(String.format("Could not map field '%s'. Reflexion error '%s'.", header, e.getMessage()));
        }
    }

    protected Object convertData(String data) throws ConversionException
    {
        Class type = getFieldType();
        try
        {
            Object toRet;
            if (type == Long.class || type == long.class)
                toRet = Long.parseLong(data.trim());
            else if (type == Double.class || type == double.class)
                toRet = Double.parseDouble(data);
            else if (type == Calendar.class)
                toRet = parseDate(data);
            else if (type == Boolean.class || type == boolean.class)
                toRet = "T".equals(data);
            else if (type == RecordRef.class)
                toRet = createRecordRef(data);
            else if (type == RecordRefList.class)
            {
                String[] refs = data.split("\\|");
                toRet = new RecordRefList(new RecordRef[refs.length]);
                int i=0;
                for (String eachRef : refs)
                    ((RecordRefList)toRet).setRecordRef(i++, createRecordRef(eachRef));
            }
            else if (type == ListOrRecordRef.class)
                toRet = createListOrRecordRef(data);
            else if (type == ListOrRecordRef[].class)
            {
                String[] refs = data.split("\\|");
                toRet = new ListOrRecordRef[refs.length];
                int i=0;
                for (String eachRef : refs)
                    ((ListOrRecordRef[])toRet)[i++] = createListOrRecordRef(eachRef);
            }
            else if (type == String.class)
                toRet = data;
            else if (type == byte[].class)
				toRet = FileUtils.readFileToByteArray(new java.io.File(data)); // data is expected to be the file path/name
			else if (isEnum(type))
                toRet = translateEnum(type, data);
			else
                throw new Exception();

            return toRet;
        }
        catch (Exception e)
        {
            throw new ConversionException(data, type.getName());
        }
    }

    private ListOrRecordRef createListOrRecordRef(String data)
    {
        ListOrRecordRef recordRef = new ListOrRecordRef();
        if (useExternalId)
            recordRef.setExternalId(data);
        else
            recordRef.setInternalId(data);
        return recordRef;
    }

    private RecordRef createRecordRef(String data)
    {
        RecordRef recordRef = new RecordRef();
        if (useExternalId)
            recordRef.setExternalId(data);
        else
            recordRef.setInternalId(data);
        return recordRef;
    }

    private Calendar parseDate(String data)
    {
        Calendar toRet = Calendar.getInstance();
        try
        {
            if (SHORT_DATE_PATTERN.matcher(data).matches()) {
                toRet.setTime(new SimpleDateFormat(SHORT_DATE_FORMAT).parse(data));
            } else {
                String pattern = dateFormat != null ? dateFormat : DEFAULT_DATE_FORMAT;
                toRet.setTime(new SimpleDateFormat(pattern).parse(data));
            }
        }
        catch (ParseException ex)
        {
            throw  new RuntimeException("Could not parse date: " + data, ex);
        }
        return toRet;
    }

    private static Object translateEnum(Class type, String data)
    {
        try
        {
            return type.getMethod("fromString",String.class).invoke(type, data); // eg AccountType.fromValue("");
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }

    }

    private static boolean isEnum(Class type)
    {
        try
        {
            return RecordMapper.findMethodByNameAndParamCount(type, "fromString", 1) != null;
        }
        catch (NoSuchMethodException e)
        {
            return false;
        }
    }

    public static class ConversionException extends Exception
    {
       final String data, dataType;
        public ConversionException(String data, String dataType)
        {
            this.data = data;
            this.dataType = dataType;
        }

        @Override
        public String getMessage()
        {
            return String.format("Could not convert '%s' to '%s'", data, dataType);
        }
    }
}
