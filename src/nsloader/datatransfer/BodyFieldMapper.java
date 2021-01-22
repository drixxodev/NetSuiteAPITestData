package nsloader.datatransfer;

import nsloader.wsproxy.platform.core.Record;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Description: Set Data on a body field
 *
 * <p>Copyright © 2015, NetSuite, Inc.</p>
 */
public class BodyFieldMapper extends FieldMapper
{
    final Method setter;
    final Class dataType;
    final CustomFieldHandler cflHandler;

    public BodyFieldMapper(Class recordType, String header, boolean useExternalId, String dateFormat) throws NoSuchMethodException
    {
        super(useExternalId, dateFormat);
        if (CustomFieldHandler.isCustomField(header) )
        {
            this.setter = null;
            this.cflHandler = new CustomFieldHandler(recordType, header);
            this.dataType = cflHandler.getFieldType();
		}
        else
        {
			String suffix = "class".equals(header) ? "_class" : RecordMapper.toInitCaps(header);
            String fieldSetterName = "set" + suffix;
            String fieldGetterName = "get" + suffix;

            this.setter = RecordMapper.findMethodByNameAndParamCount(recordType, fieldSetterName, 1);
            this.dataType = RecordMapper.findMethodByNameAndParamCount(recordType, fieldGetterName, 0).getReturnType();

            this.cflHandler = null;
        }
    }

    @Override
    public void setFieldData(Record target, String data) throws ConversionException, InvocationTargetException, IllegalAccessException, InstantiationException
    {
        Object convertedData = convertData(data);
        if (cflHandler == null)
            setter.invoke(target, convertedData);
        else
            cflHandler.setFieldData(target, convertedData);
    }

    @Override
    public Class getFieldType()
    {
        return dataType;
    }
}
