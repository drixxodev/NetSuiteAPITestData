package nsloader.datatransfer;

import nsloader.wsproxy.platform.core.BooleanCustomFieldRef;
import nsloader.wsproxy.platform.core.CustomFieldList;
import nsloader.wsproxy.platform.core.CustomFieldRef;
import nsloader.wsproxy.platform.core.DateCustomFieldRef;
import nsloader.wsproxy.platform.core.DoubleCustomFieldRef;
import nsloader.wsproxy.platform.core.ListOrRecordRef;
import nsloader.wsproxy.platform.core.LongCustomFieldRef;
import nsloader.wsproxy.platform.core.MultiSelectCustomFieldRef;
import nsloader.wsproxy.platform.core.SelectCustomFieldRef;
import nsloader.wsproxy.platform.core.StringCustomFieldRef;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Calendar;

/**
 * Description: Handles the setting of CustomFieldList on body or sublist fields
 *
 * <p>Copyright © 2015, NetSuite, Inc.</p>
 */
public class CustomFieldHandler
{

    private enum CustomDataType
    {
        BOOLEAN(boolean.class, BooleanCustomFieldRef.class),
        DATE(Calendar.class, DateCustomFieldRef.class),
        DOUBLE(double.class, DoubleCustomFieldRef.class),
        LONG(long.class, LongCustomFieldRef.class),
        SELECT(ListOrRecordRef.class, SelectCustomFieldRef.class),
        MULTISELECT(ListOrRecordRef[].class, MultiSelectCustomFieldRef.class),
        STRING(String.class, StringCustomFieldRef.class);

        public static CustomDataType fromString(String value)
        {
            for (CustomDataType d : values())
            {
                if (d.name().equals(value))
                    return d;
            }

            throw new RuntimeException("Can not find Custom Data Type for : " + value);
        }

        private Class dataType;
//        private Method setter;
        private Constructor fieldRefConstructor;

        CustomDataType(Class dataType, Class fieldRefClass)
        {
            try
            {
                this.dataType = dataType;
                this.fieldRefConstructor = fieldRefClass.getConstructor(String.class, String.class, dataType);
            }
            catch (NoSuchMethodException e)
            {
                throw new Error("NoSuchMethod Exception in statically defined types", e);
            }
        }


        public Class getDataType() { return dataType; }
        public Constructor getFieldRefConstructor() { return fieldRefConstructor; }
    }


    final Method cflGetter, cflSetter;
    final Constructor<CustomFieldList> cflConstructor;
    final CustomDataType dataType;
    final String fieldName;
    final int lineNumber;

    public CustomFieldHandler(Class owner, String fieldName) throws NoSuchMethodException
    {
        this(owner, fieldName, -1);
    }

    @SuppressWarnings("unchecked")
    public CustomFieldHandler(Class owner, String fieldName, int lineNumber) throws NoSuchMethodException
    {
        this.cflGetter = RecordMapper.findMethodByNameAndParamCount(owner, "getCustomFieldList", 0);
        this.cflSetter = RecordMapper.findMethodByNameAndParamCount(owner, "setCustomFieldList", 1);
        this.cflConstructor = (Constructor<CustomFieldList>)this.cflGetter.getReturnType().getConstructor();

        String[] customFieldInfo = fieldName.split(":");
        this.fieldName = customFieldInfo[0];
        this.dataType = CustomDataType.fromString(customFieldInfo[1]);
        this.lineNumber = lineNumber;
    }

    /** Convention - CustomFields should be declared as custentity_mytestfield:STRING */
    public static boolean isCustomField(String fieldName)
    {
        return fieldName.startsWith("cust") && fieldName.contains(":");
    }

    public Class getFieldType()
    {
        return dataType.getDataType();
    }

    public void setFieldData(Object cflOwner, Object data) throws InvocationTargetException, IllegalAccessException, InstantiationException
    {
        // Another Axis specific convention
        CustomFieldRef fieldRef = (CustomFieldRef)dataType.getFieldRefConstructor().newInstance(null, fieldName, data);

        CustomFieldList cfl = (CustomFieldList)cflGetter.invoke(cflOwner);
        if (cfl == null)
            cfl = cflConstructor.newInstance();

        CustomFieldRef[] newArray = RecordMapper.extendArray(CustomFieldRef.class, cfl.getCustomField(), fieldRef);

        cfl.setCustomField(newArray);
        cflSetter.invoke(cflOwner, cfl);
    }

}
