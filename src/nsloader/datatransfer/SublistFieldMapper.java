package nsloader.datatransfer;
import nsloader.wsproxy.platform.core.Record;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Description: Generically handle the setting of sublist data
 *
 * <p>Copyright © 2015, NetSuite, Inc.</p>
 */
public class SublistFieldMapper extends FieldMapper
{
    final Method listGetter, listSetter, lineGetter, lineSetter, fieldSetter, setReplaceAll;
    final Constructor listConstructor, lineConstructor;
    final Class dataType, lineClass;
    final int oneBasedLineToSet;
    final CustomFieldHandler cflHandler;

    @SuppressWarnings({"unchecked","EmptyCatchBlock"})
    public SublistFieldMapper(Class recordType, String[] sublistInfo, boolean useExternalId, String dateFormat) throws NoSuchMethodException
    {
        super(useExternalId, dateFormat);

        this.oneBasedLineToSet = Integer.parseInt(sublistInfo[1]);
        String sublistName = sublistInfo[0];
        String sublistField = sublistInfo[2];

        String listGetterName = "get" + RecordMapper.toInitCaps(sublistName) + "List";
        this.listGetter = RecordMapper.findMethodByNameAndParamCount(recordType, listGetterName, 0);

        String listSetterName = "set" + RecordMapper.toInitCaps(sublistName) + "List";
        this.listSetter = RecordMapper.findMethodByNameAndParamCount(recordType, listSetterName, 1);

        Class listClass = this.listGetter.getReturnType();
        this.listConstructor = listClass.getConstructor();

        Method sra = null;
        try
        {

            if ( "T".equals(System.getProperty("nsloader.useReplaceAll")))
                sra = listClass.getMethod("setReplaceAll", boolean.class);
        }
        catch (NoSuchMethodException e) {}
        this.setReplaceAll = sra;

        this.lineGetter = RecordMapper.findSublistSingleMemberGetterMethod(listClass, sublistName);

        this.lineClass = lineGetter.getReturnType().getComponentType();
        this.lineConstructor = lineClass.getConstructor();

        String lineSetterName = lineGetter.getName().replaceFirst("get", "set");
        this.lineSetter = RecordMapper.findMethodByNameAndParamCount(listClass, lineSetterName, 1);


        if (CustomFieldHandler.isCustomField(sublistField))
        {
            this.fieldSetter =null;
            this.cflHandler = new CustomFieldHandler(lineClass, sublistField, oneBasedLineToSet);
            this.dataType = cflHandler.getFieldType();
        }
        else
        {
			String suffix = "class".equals(sublistField) ? "_class" : RecordMapper.toInitCaps(sublistField);
            String fieldSetterName = "set" + suffix;
            this.fieldSetter = RecordMapper.findMethodByNameAndParamCount(lineClass, fieldSetterName, 1);

            String fieldGetterName = "get" + suffix;
            this.dataType = RecordMapper.findMethodByNameAndParamCount(lineClass, fieldGetterName, 0).getReturnType();
            this.cflHandler = null;
        }
    }

    /**
     * Big Caveats to this method 1) Lines must be in order in the csv, eg item.1.amount, item.2.amount, otherwise you
     * will get an error 2) This is specific to an Axis code generator, currently 1.4
     */
    @Override
    @SuppressWarnings("unchecked")
    public void setFieldData(Record target, String data) throws ConversionException, InvocationTargetException, IllegalAccessException, InstantiationException
    {
        Object listObject = listGetter.invoke(target);

        // Instantiate the wrapping list if need be
        Object[] lines = null;
        if (listObject != null)
            lines = (Object[])lineGetter.invoke(listObject);
        else
        {
            listObject = listConstructor.newInstance();
            listSetter.invoke(target, listObject);

            if (setReplaceAll != null)
                setReplaceAll.invoke(listObject, true);
        }

        // Now instantiate the line if need be
        Object lineObject;
        if (lines == null || lines.length < oneBasedLineToSet)
        {
            lineObject = lineConstructor.newInstance();

            lines = RecordMapper.extendArray(lineClass, lines, lineObject);
            lineSetter.invoke(listObject, (Object)lines);
        }

        // do the actual set
        Object convertedData = convertData(data);
        if (cflHandler ==  null)
            fieldSetter.invoke(lines[oneBasedLineToSet-1], convertedData);
        else
            cflHandler.setFieldData(lines[oneBasedLineToSet-1], convertedData);

    }

    @Override
    public Class getFieldType()
    {
        return dataType;
    }
}
