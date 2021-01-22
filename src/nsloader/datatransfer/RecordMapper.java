package nsloader.datatransfer;

import nsloader.wsproxy.platform.core.Record;
import org.apache.log4j.Logger;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.CodeSource;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Description: Responsible for transferring data from the CSV file to the Axis Serializable Object. Attempt to do all
 * reflection in the constructor. TODO: There are a couple exceptions, but these could be fixed with some work.
 * Note, this process is not at all clientCPU bound
 *
 * <p>Copyright © 2015, NetSuite, Inc.</p>
 */
public class RecordMapper
{
	static Logger log = Logger.getLogger(RecordMapper.class.getName());

    final Class recordType;
    final FieldMapper[] mappings;

    public RecordMapper(String recordType, String[] headers)
    {
        this.recordType = recordTypeMap.get(recordType);
		if (this.recordType == null)
		{
			throw new RuntimeException(String.format("Record type '%s' not found in the schema.", recordType));
		}
        this.mappings = new FieldMapper[headers.length];

        for(int i =0;i<headers.length;i++)
            mappings[i] = FieldMapper.newMapping(this.recordType, headers[i].trim());
    }

    public Record map(String[] data) throws FieldMapper.ConversionException
    {
        RuntimeException x;
        try
        {
            Record toRet = (Record)recordType.newInstance();
            for (int i=0;i<data.length;i++)
            {
                if (data[i] != null && data[i].length() > 0)
                    mappings[i].setFieldData(toRet, data[i]);
            }
            return toRet;
        }
        catch (IllegalAccessException e)
        {
            x = new RuntimeException(e);
        }
        catch (InvocationTargetException e)
        {
            x = new RuntimeException(e);
        }
        catch (InstantiationException e)
        {
            x = new RuntimeException(e);
        }
        throw x;
    }


    /** Naive array extender */
    @SuppressWarnings("unchecked")
    public static <T> T[] extendArray(Class<T> elementClass, T[] currentArray, T toAdd)
    {
        int newLength = currentArray == null ? 1 : currentArray.length + 1;
        T[] newArray = (T []) Array.newInstance(elementClass, newLength);

        if (newLength > 1)
            System.arraycopy(currentArray, 0, newArray, 0, newLength-1);
        newArray[newLength-1] = toAdd;

        return  newArray;
    }

    public static String toInitCaps(String raw)
    {
        return raw.substring(0,1).toUpperCase() + raw.substring(1);
    }

    public static String toInitLower(String raw)
    {
        return raw.substring(0,1).toLowerCase() + raw.substring(1);
    }

    /** @return - the first method matching methodName on owner
     * @throws NoSuchMethodException - does not return null */
    public static Method findMethodByNameAndParamCount(Class owner, String methodName, int parameterCount ) throws NoSuchMethodException
    {
        for (Method m : owner.getDeclaredMethods())
        {
            if (m.getName().equals(methodName) && m.getParameterTypes().length == parameterCount)
                return m;
        }
        throw new NoSuchMethodException(methodName + "," + parameterCount);
    }

	/** @return - Find the single sublist member getter
	 * @throws NoSuchMethodException - does not return null */
	public static Method findSublistSingleMemberGetterMethod(Class owner, String sublistName) throws NoSuchMethodException
	{
		for (Method m : owner.getDeclaredMethods())
		{
			if (m.getName().startsWith("get") && m.getParameterTypes().length == 0 && m.getReturnType().isArray())
				return m;
		}
		throw new NoSuchMethodException(String.format("sublist '%s' single member getter", sublistName));
	}

    public static final Map<String, Class> recordTypeMap = new HashMap<String,Class>();
    static
    {
        try
        {
            CodeSource codeSource = Record.class.getProtectionDomain().getCodeSource();
            File jarFile = new File(codeSource.getLocation().toURI().getPath());
            String jarDir = jarFile.getParentFile().getPath();

            java.util.jar.JarFile j = new JarFile(jarDir+"/nsws.jar");
            for ( Enumeration<JarEntry> jes = j.entries();jes.hasMoreElements(); )
            {
                String jeName = jes.nextElement().getName();

                if (jeName.endsWith(".class"))
                {
                    jeName = jeName.replace('/','.').substring(0,jeName.length()-6);
                    Class x = Class.forName(jeName);
                    if (Record.class.isAssignableFrom(x))
                        recordTypeMap.put(x.getSimpleName(), x);
                }
            }
        }
        catch (Exception e)
        {
            log.error("Could not determine Record Classes", e);
        }
    }

}
