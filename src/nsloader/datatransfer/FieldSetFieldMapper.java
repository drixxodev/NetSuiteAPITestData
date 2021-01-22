package nsloader.datatransfer;

import nsloader.wsproxy.platform.core.Record;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Description: Mapper for "Field Set" type fields, eg ShipAddress type on SalesOrder. There is a one-to-one cardinality
 * but it's in its own group.
 *
 * <p>Copyright © 2015, NetSuite, Inc.</p>
 */
public class FieldSetFieldMapper extends FieldMapper
{
	final Method fieldSetGetter, fieldSetSetter, fieldSetter;
	final Constructor fieldSetConstructor;
	final Class dataType;

	public FieldSetFieldMapper(Class recordType, String[] fieldSetInfo, boolean useExternalId, String dateFormat) throws NoSuchMethodException
	{
		super(useExternalId, dateFormat);

		String fieldSet = fieldSetInfo[0];
		String fieldName = fieldSetInfo[1];

		String fieldSetGetterName = "get" + RecordMapper.toInitCaps(fieldSet);
		this.fieldSetGetter = RecordMapper.findMethodByNameAndParamCount(recordType, fieldSetGetterName, 0);

		String fieldSetSetterName = "set" + RecordMapper.toInitCaps(fieldSet);
		this.fieldSetSetter = RecordMapper.findMethodByNameAndParamCount(recordType, fieldSetSetterName, 1);

		Class fieldSetClass = this.fieldSetGetter.getReturnType();
		this.fieldSetConstructor = fieldSetClass.getConstructor();


		String suffix = "class".equals(fieldName) ? "_class" : RecordMapper.toInitCaps(fieldName);
		String fieldSetterName = "set" + suffix;
		this.fieldSetter = RecordMapper.findMethodByNameAndParamCount(fieldSetClass, fieldSetterName, 1);

		String fieldGetterName = "get" + suffix;
		this.dataType = RecordMapper.findMethodByNameAndParamCount(fieldSetClass, fieldGetterName, 0).getReturnType();
	}

	@Override
	protected Class getFieldType()
	{
		return dataType;
	}

	@Override
	public void setFieldData(Record target, String data) throws ConversionException, InvocationTargetException, IllegalAccessException, InstantiationException
	{
		Object fieldSetObject = fieldSetGetter.invoke(target);

		// Instantiate the owning field set object
		if (fieldSetObject == null)
		{
			fieldSetObject = fieldSetConstructor.newInstance();
			fieldSetSetter.invoke(target, fieldSetObject);
		}

		// do the actual set
		Object convertedData = convertData(data);
		fieldSetter.invoke(fieldSetObject, convertedData);
	}
}
